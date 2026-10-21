package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ActionStance;
import akyrion.thepredict.domain.ChoiceId;
import akyrion.thepredict.domain.ChoiceTelegraph;
import akyrion.thepredict.domain.CombatStance;
import akyrion.thepredict.domain.CooldownClass;
import akyrion.thepredict.domain.DamageTier;
import akyrion.thepredict.domain.DangerClass;
import akyrion.thepredict.domain.Delivery;
import akyrion.thepredict.domain.ExecutionOutcome;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.PurposeOrigin;
import akyrion.thepredict.domain.ReleaseOutcome;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.SkillPurpose;
import akyrion.thepredict.domain.SkillRole;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.domain.TellAnswer;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

/**
 * One duel session driven by {@link TellOpening}, {@link TelegraphRelease} and {@link StanceClock} (M23.11).
 * The sword cooldown is 12 ticks. A rival in range and off cooldown is punished during recovery.
 */
public final class FairnessDrive {
    private static final UUID TARGET = new UUID(0x23L, 0x11L);
    private static final CombatActions RELEASED = actions(ReleaseOutcome.RELEASED);
    private static final CombatActions WHIFFED = actions(ReleaseOutcome.WHIFFED);

    private final MobSession session;
    private final EnumMap<DangerClass, EnumMap<TellAnswer, SkillChoice>> grid;
    private final long[] step = new long[5];
    private long attackReady;

    /** A session whose choices declare every measured class and answer. */
    public FairnessDrive() {
        grid = new EnumMap<>(DangerClass.class);
        var listed = new LinkedHashMap<ChoiceId, SkillChoice>();
        for (var danger : new DangerClass[] {DangerClass.MEDIUM, DangerClass.HEAVY, DangerClass.SEVERE}) {
            var row = new EnumMap<TellAnswer, SkillChoice>(TellAnswer.class);
            for (var answer : new TellAnswer[] {TellAnswer.BLOCK, TellAnswer.DODGE, TellAnswer.BACK_OFF}) {
                var choice = choice(danger, answer);
                row.put(answer, choice);
                listed.put(choice.id(), choice);
            }
            grid.put(danger, row);
        }
        var base = EngineFixtures.profile("Guardian");
        var profile = new TacticalProfile(base.id(), base.mythicMobId(), base.objective(), base.tactics(), listed,
                base.contexts(), base.movement(), base.health(), base.threat(), base.baseAi(), base.timing());
        session = new MobSession(new MobKey(new UUID(0x23L, 0x10L), 0), profile);
        session.playerTarget(true);
    }

    /**
     * Opens one tell and releases it.
     *
     * @param danger class the choice declares
     * @param answer answer the choice declares
     * @param tick opening tick
     * @param inRange whether the rival can reach the caster
     * @return what the engine did
     */
    public Step tell(DangerClass danger, TellAnswer answer, long tick, boolean inRange) {
        var choice = grid.get(danger).get(answer);
        session.recordTarget(TARGET, inRange ? 2.0 : 30.0);
        var changes = 0;
        var before = visible();
        var held = TellOpening.hold(session, choice, Movement.NONE, tick, inRange ? RELEASED : WHIFFED, step);
        changes += changed(before);
        if (!held || !session.tellOpen()) {
            return new Step(0, false, false, false, changes);
        }
        var shown = session.telegraphTicks();
        var releaseAt = session.releaseTick();
        before = visible();
        var outcome = TelegraphRelease.release(session, releaseAt, inRange ? RELEASED : WHIFFED, step);
        changes += changed(before);
        var abort = outcome == ReleaseOutcome.REJECTED || outcome == ReleaseOutcome.SESSION_INVALID;
        var punished = false;
        if (outcome == ReleaseOutcome.RELEASED) {
            before = visible();
            StanceClock.advance(session, releaseAt, RELEASED, step);
            changes += changed(before);
            if (inRange && session.actionStance() == ActionStance.RECOVERY && releaseAt >= attackReady) {
                session.tellResults().notePunish(TARGET, 0.05, releaseAt);
                punished = true;
                attackReady = releaseAt + 12;
            }
        }
        var guard = 0;
        while (session.actionStance() != ActionStance.NONE && guard++ < 4) {
            var until = session.actionUntilTick();
            if (until == Long.MAX_VALUE) {
                break;
            }
            before = visible();
            StanceClock.advance(session, Math.max(until, releaseAt), RELEASED, step);
            changes += changed(before);
        }
        if (session.tellOpen()) {
            session.closeTell();
        }
        return new Step(shown, true, abort, punished, changes);
    }

    private CombatStance visible() {
        return CombatStance.visible(session.baseStance().ordinal(), session.actionStance().ordinal());
    }

    private int changed(CombatStance before) {
        return visible() == before ? 0 : 1;
    }

    private static SkillChoice choice(DangerClass danger, TellAnswer answer) {
        var name = danger.name().toLowerCase() + "_" + answer.name().toLowerCase();
        var id = new ChoiceId(name);
        var purpose = new SkillPurpose(SkillRole.DAMAGE, Set.of(SkillRole.DAMAGE), Delivery.MELEE, OptionalDouble.of(3),
                OptionalDouble.empty(), 0, CooldownClass.SHORT, DamageTier.LOW, Set.of(), Set.of(), false,
                PurposeOrigin.DECLARED);
        return SkillChoice.typed(id, name, Set.of(), true, 0, purpose).withTelegraph(new ChoiceTelegraph(
                Optional.of(danger), Optional.of(answer), OptionalInt.of(20)));
    }

    private static CombatActions actions(ReleaseOutcome outcome) {
        return new CombatActions() {
            @Override
            public ExecutionOutcome cast(MobKey mob, SkillChoice choice, Movement movement) {
                return ExecutionOutcome.CAST;
            }

            @Override
            public boolean ready(MobKey mob, SkillChoice choice) {
                return true;
            }

            @Override
            public void signalTactic(akyrion.thepredict.domain.MobKey mob, akyrion.thepredict.domain.TacticId tactic) {
            }

            @Override
            public void addThreat(MobKey mob, UUID participantId, double amount) {
            }

            @Override
            public ReleaseOutcome release(MobKey mob, SkillChoice choice, Movement movement, UUID expectedTarget,
                                          CastInterrupt interrupt) {
                return outcome;
            }
        };
    }

    /**
     * @param shown ticks the engine showed
     * @param opened whether a tell opened
     * @param selfAbort whether the release was rejected or the session was invalid
     * @param punished whether the rival hit during recovery
     * @param stanceChanges visible stance changes of this tell
     */
    public record Step(int shown, boolean opened, boolean selfAbort, boolean punished, int stanceChanges) {
    }
}
