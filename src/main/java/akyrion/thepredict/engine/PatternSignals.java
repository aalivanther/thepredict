package akyrion.thepredict.engine;

import akyrion.thepredict.domain.EnemyAction;
import akyrion.thepredict.domain.EnemyKinematics;
import akyrion.thepredict.domain.PatternSettings;
import akyrion.thepredict.domain.StimulusKind;
import akyrion.thepredict.domain.TickSignals;

import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Kinematic sample, strafe and step tokens, and the previous tick's signals (M26.3). The opaque stamp is read only
 * through {@link EnemyKinematics}. A missing reader does not overwrite the book. The movement and shield tokens go to
 * the ring of the participant, which remembers what it already offered, so a participant of two encounters is not
 * offered twice; the low-health window opens on the model of the encounter whose member saw it.
 */
final class PatternSignals {
    PatternSignals() {
    }

    /**
     * Whether a sample of the enemy is due: there is a reader and the stored tick is older than {@code sampleTicks}.
     *
     * @param reader capture reader, or {@code null}
     * @param book kinematic book
     * @param settings pattern limits
     * @param target the enemy
     * @param tick the server tick
     * @return {@code true} when a read would be made
     */
    boolean due(CombatReader reader, KinematicBook book, PatternSettings settings, UUID target, long tick) {
        if (reader == null) {
            return false;
        }
        var stored = book.tick(target);
        return stored < 0 || tick - stored >= settings.sampleTicks();
    }

    /**
     * Samples when a sample is due and the step still has time.
     *
     * @param reader capture reader, or {@code null}
     * @param book kinematic book
     * @param settings pattern limits
     * @param target the enemy
     * @param witness the member, or the nil id
     * @param tick the server tick
     * @param deadline monotonic deadline
     * @param clock monotonic clock
     * @return {@code true} when the reader was asked
     */
    boolean sample(CombatReader reader, KinematicBook book, PatternSettings settings, UUID target, UUID witness,
                   long tick, long deadline, LongSupplier clock) {
        if (!due(reader, book, settings, target, tick) || clock.getAsLong() >= deadline) {
            return false;
        }
        var word = reader.enemyKinematics(target, book.word(target), witness);
        book.store(target, word, (int) tick);
        return true;
    }

    /**
     * Offers strafe and step once per new book tick of the participant. An invisible enemy offers neither (A14).
     *
     * @param book kinematic book
     * @param inbox token ring
     * @param target the enemy
     * @param tick the server tick
     */
    void movement(KinematicBook book, PatternInbox inbox, UUID target, int tick) {
        var word = book.word(target);
        var at = book.tick(target);
        if (!EnemyKinematics.valid(word) || EnemyKinematics.invisible(word) || at == inbox.sampledTick(target)) {
            return;
        }
        inbox.sampledTick(target, at);
        offerLateral(inbox, target, tick, EnemyKinematics.lateral(word));
        offerRadial(inbox, target, tick, EnemyKinematics.radial(word));
    }

    /**
     * Shield edges and a low-health window from the signals of the previous tick. The caller asks only with a witness
     * (SP-04). An invisible enemy, whose word the model just copied, opens nothing, and its shield state becomes
     * unknown, so the change it made while nobody could see it raises no edge when it is seen again.
     *
     * @param session the session
     * @param inbox token ring
     * @param learned the model of the session's encounter
     * @param target the enemy
     * @param tick the server tick
     */
    void edges(MobSession session, PatternInbox inbox, EnemyModel learned, UUID target, int tick) {
        if (session.cadence() != akyrion.thepredict.domain.TickTier.HOT) {
            return;
        }
        if (EnemyKinematics.invisible(learned.kinematics())) {
            inbox.shield(target, -1);
            return;
        }
        var signals = session.tickSignals();
        if (!TickSignals.bit(signals, TickSignals.VALID)) {
            return;
        }
        var blocking = TickSignals.bit(signals, TickSignals.TARGET_BLOCKING) ? 1 : 0;
        var before = inbox.shield(target);
        if (before >= 0 && blocking != before) {
            var action = blocking == 1 ? EnemyAction.SHIELD_UP : EnemyAction.SHIELD_DOWN;
            inbox.offer(target, action.ordinal(), tick);
        }
        inbox.shield(target, blocking);
        var critical = session.profile().health().criticalRatio() * 100.0;
        var health = TickSignals.targetHealthPercent(signals);
        if (health > 0 && health <= critical) {
            learned.windows().open(StimulusKind.LOW_HEALTH, tick, 60);
        }
    }

    /**
     * Opens the strike window for a strike token.
     *
     * @param learned the model
     * @param action the token
     * @param tick the server tick
     */
    static void strike(EnemyModel learned, EnemyAction action, int tick) {
        if (action == EnemyAction.STRIKE || action == EnemyAction.STRIKE_EARLY || action == EnemyAction.CRIT_STRIKE
                || action == EnemyAction.SPRINT_STRIKE) {
            learned.windows().open(StimulusKind.AFTER_OWN_STRIKE, tick, 20);
        }
    }

    private static void offerLateral(PatternInbox inbox, UUID target, int tick, int lateral) {
        if (lateral == EnemyKinematics.LEFT_OR_CLOSING) {
            inbox.offer(target, EnemyAction.STRAFE_LEFT.ordinal(), tick);
        } else if (lateral == EnemyKinematics.RIGHT_OR_AWAY) {
            inbox.offer(target, EnemyAction.STRAFE_RIGHT.ordinal(), tick);
        }
    }

    private static void offerRadial(PatternInbox inbox, UUID target, int tick, int radial) {
        if (radial == EnemyKinematics.LEFT_OR_CLOSING) {
            inbox.offer(target, EnemyAction.STEP_IN.ordinal(), tick);
        } else if (radial == EnemyKinematics.RIGHT_OR_AWAY) {
            inbox.offer(target, EnemyAction.STEP_OUT.ordinal(), tick);
        }
    }
}
