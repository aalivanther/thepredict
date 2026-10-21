package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.DistanceBand;
import akyrion.thepredict.domain.EnemyPattern;
import akyrion.thepredict.domain.ExploitPolicy;
import akyrion.thepredict.domain.HealthBand;
import akyrion.thepredict.domain.HealthThresholds;
import akyrion.thepredict.domain.SkillRole;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TickSignals;
import akyrion.thepredict.domain.ZoneMode;
import akyrion.thepredict.domain.learn.ContextKey;
import akyrion.thepredict.domain.learn.CreditMath;
import akyrion.thepredict.domain.learn.GroupBand;
import akyrion.thepredict.domain.learn.HealthBand3;
import akyrion.thepredict.domain.learn.RewardSpec;
import akyrion.thepredict.domain.learn.TrainingSource;
import akyrion.thepredict.domain.learn.ZoneBand;

import java.util.Set;

/**
 * Registers a closed outcome window on the credit ledger (M28.2). The outcome events snapshot {@link #live} and the
 * damage rate of the last 60 ticks when the window opens. The context is the live one (I21): distance, own and target health, the kind of the target, the zone band of
 * {@code full}, the group and the pattern group a counter may exploit now. A window earns avoided damage when its
 * tactic retreats or its choice defends or sustains ({@link RewardSpec#defensive}). Each window carries its source
 * ({@link TrainingSource#of}) and the wall time of its close.
 */
final class WindowCredit {
    private static final EnemyPattern[] PATTERNS = EnemyPattern.values();

    private WindowCredit() {
    }

    /**
     * The fine context this window would photograph. Capture reads this text, not the armed cell.
     *
     * @param session the member
     * @return the live key
     */
    static ContextKey live(MobSession session) {
        return context(session);
    }

    /**
     * Writes tactic, context, effect, avoided damage, source and the agreement of Jev with the learned leader. Encounter
     * close distributes {@code z} later. A window that asked Jev agrees when its tactic is the leader of highest value
     * noted at the capture it answered ({@code jev_agree}, M28.8); without an active table there is no leader.
     *
     * @param session the member
     * @param summary the closed window
     * @param playersPresent whether a player is a participant of the encounter
     * @param epochMs wall time supplied by the adapter
     */
    static void record(MobSession session, OutcomeWindow.Summary summary, boolean playersPresent, long epochMs) {
        var ticks = (int) Math.max(1L, summary.endTick() - summary.startTick());
        var learning = session.learning();
        var defensive = RewardSpec.defensive(summary.movement(), roles(session, summary.tactic()));
        var avoided = CreditMath.avoided(learning.rateBefore(), summary.taken() / ticks, ticks, defensive);
        var source = TrainingSource.of(playersPresent, learning.training(), session.difficulty().play(),
                session.difficulty().rung());
        var askedJev = summary.level() == ControlLevel.JEV;
        var agreed = askedJev && learning.agreesWithLeader(summary.tactic().value());
        learning.credit().onWindowClosed(summary.tactic().value(), windowContext(session), summary.effect(), avoided,
                askedJev, agreed, source);
        learning.distillSession().emitWindow(session.profile().id(), session.key().entityId().toString(),
                summary.effect(), epochMs);
    }

    private static String windowContext(MobSession session) {
        var context = session.learning().windowContext();
        return context.isEmpty() ? context(session).text() : context;
    }

    /** Roles of the choice of a tactic, empty without a typed choice. */
    private static Set<SkillRole> roles(MobSession session, TacticId tacticId) {
        var profile = session.profile();
        var tactic = profile.tactics().get(tacticId);
        if (tactic == null || tactic.choice().isEmpty()) {
            return Set.of();
        }
        var choice = profile.choices().get(tactic.choice().get());
        if (choice == null || choice.purpose().isEmpty()) {
            return Set.of();
        }
        return choice.purpose().get().roles();
    }

    private static ContextKey context(MobSession session) {
        var band = session.probeBand();
        var distance = ContextKey.distanceCode(band.isPresent() ? band.get() : DistanceBand.MELEE);
        var full = session.zoneMode() == ZoneMode.FULL;
        var facts = session.lastMobFacts();
        var zone = ZoneBand.of(full, facts.isPresent() ? facts.get().zoneFacts() : Set.of());
        var clock = session.groupClock();
        var living = clock.isPresent() ? clock.get().members() : 1;
        return ContextKey.of(distance, HealthBand3.of(session.probeHealth()), targetHealth(session),
                session.playerTarget(), zone, GroupBand.of(living), patternCode(session));
    }

    /** Target health of the last signals, with the profile thresholds; a reading without a target says nothing. */
    private static HealthBand3 targetHealth(MobSession session) {
        var signals = session.tickSignals();
        if (!TickSignals.bit(signals, TickSignals.VALID) || !TickSignals.bit(signals, TickSignals.HAS_TARGET)) {
            return HealthBand3.HIGH;
        }
        var ratio = TickSignals.targetHealthPercent(signals) / 100.0;
        HealthThresholds thresholds = session.profile().health();
        if (ratio <= thresholds.criticalRatio()) {
            return HealthBand3.of(HealthBand.CRITICAL);
        }
        return HealthBand3.of(ratio <= thresholds.woundedRatio() ? HealthBand.WOUNDED : HealthBand.HEALTHY);
    }

    /**
     * Pattern group of the counter the session may exploit at its last pattern step, {@code 0} without one. With
     * {@code off} and {@code observe} it is always {@code 0} ({@link ExploitPolicy#allowsNow}).
     */
    private static int patternCode(MobSession session) {
        var focus = session.patternFocus();
        if (focus < 0 || focus >= PATTERNS.length || !session.patternCounterActive()) {
            return 0;
        }
        var tick = session.patternSeenTick();
        var quota = session.exploitBudget().allows((int) tick, session.patternWindow(), session.patternQuota());
        var allows = ExploitPolicy.allowsNow(session.patternMode(), session.patternResting(tick),
                session.learning().openPatterns(), quota);
        return ContextKey.patternCode(PATTERNS[focus].group(), allows);
    }
}
