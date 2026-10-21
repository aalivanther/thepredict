package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ChoiceKind;
import akyrion.thepredict.domain.CueRequest;
import akyrion.thepredict.domain.ExecutionOutcome;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.ReadyIn;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.ReleaseOutcome;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.TacticId;

import java.util.OptionalInt;
import java.util.UUID;

/**
 * Port through which {@code engine} acts on the world (00 §4, M10): casts the choice of an intent, tells whether it is
 * off its native cooldown, telegraphs a new tactic and changes the native threat. Implemented by {@code adapter.server.execution.CombatActionsAdapter}; tests
 * use doubles (rule P2). Server thread only (rule T1): implementations reject other threads, and {@code engine} only
 * calls it from the coordinator tick, never from a callback (rule T5).
 */
public interface CombatActions {
    /**
     * Tries to cast a choice now, with the checks of M10.3: target, native cooldown, invulnerability, shield (or the
     * blocking angle for {@code FLANK}, M10.8) and, for a vanilla melee choice, reach (M10.9). Never throws for a
     * live-game reason.
     *
     * @param mob session of the caster; {@code engine} passes only the key of the current session
     * @param choice choice of the intent
     * @param movement movement of the intent, which selects the shield rule
     * @return {@code CAST} or {@code REJECTED_BY_MYTHICMOBS} (final), {@code SKIPPED_SHIELD} (final for this tactic),
     *         or {@code NOT_ELIGIBLE} and {@code DEFERRED_INVULNERABLE} (retried in a later tick); never {@code EXPIRED}
     */
    ExecutionOutcome cast(MobKey mob, SkillChoice choice, Movement movement);

    /**
     * Casts a choice only when the live target is still the one captured (M19.4). The default delegates to
     * {@link #cast(MobKey, SkillChoice, Movement)} so a double that models no expected target keeps compiling (W5).
     * The production adapter compares {@code expectedTarget} with {@code CastSource.target} and, when the skill has a
     * native {@code targetwithin}, with {@code Mob.getTarget()}.
     *
     * @param mob session of the caster
     * @param choice choice to cast
     * @param movement movement of the intent
     * @param expectedTarget target captured for this cast; the engine calls this overload only when one was captured
     * @return the same outcomes as {@link #cast(MobKey, SkillChoice, Movement)}
     * @throws NullPointerException if {@code expectedTarget} is {@code null}
     */
    default ExecutionOutcome cast(MobKey mob, SkillChoice choice, Movement movement, UUID expectedTarget) {
        java.util.Objects.requireNonNull(expectedTarget, "expectedTarget");
        return cast(mob, choice, movement);
    }

    /**
     * Whether the live target is still the captured one, without casting (M19.4). The default is {@code true}, so a
     * double that does not model a target still casts. The production adapter compares {@code expectedTarget} with
     * {@code CastSource.target}.
     *
     * @param mob session of the caster
     * @param expectedTarget target captured for this cast
     * @return whether the live target is that one
     * @throws NullPointerException if {@code expectedTarget} is {@code null}
     */
    default boolean liveTargetMatches(MobKey mob, UUID expectedTarget) {
        java.util.Objects.requireNonNull(expectedTarget, "expectedTarget");
        return true;
    }

    /**
     * Whether the native AI target is still the captured one, without casting (M19.4). Used when the choice has a
     * native {@code targetwithin}. The default is {@code true}. The production adapter compares {@code expectedTarget}
     * with {@code Mob.getTarget()}.
     *
     * @param mob session of the caster
     * @param expectedTarget target captured for this cast
     * @return whether the AI target is that one
     * @throws NullPointerException if {@code expectedTarget} is {@code null}
     */
    default boolean aiTargetMatches(MobKey mob, UUID expectedTarget) {
        java.util.Objects.requireNonNull(expectedTarget, "expectedTarget");
        return true;
    }

    /**
     * Tells whether a choice is off its native cooldown now, without casting it and without side effects (M10,
     * real-time readiness): a MythicMobs skill answers whether its {@code Cooldown} has passed for this caster, and a
     * choice that is not a skill is always ready. A skill that is no longer loaded, or a mob that is not active, is not
     * ready. O(1); the other checks of {@link #cast} (reach, invulnerability, shield, conditions) still gate the cast.
     *
     * @param mob session of the caster; {@code engine} passes only the key of the current session
     * @param choice a choice of the profile of the session
     * @return whether the native cooldown lets the choice be cast now
     */
    boolean ready(MobKey mob, SkillChoice choice);

    /**
     * Ticks until the native cooldown lets the choice be cast (M19.4). Empty when MythicMobs has no pending recharge
     * ({@code Long.MAX_VALUE}) or the skill is not an {@code AbstractSkill}. A vanilla melee choice is empty: its rhythm
     * is {@code nextMeleeTick}. The default follows {@link #ready(MobKey, SkillChoice)} so a double that only models
     * readiness still casts: {@code 0} when ready, {@code 1} otherwise. {@link akyrion.thepredict.NoCombatActions}
     * returns empty.
     *
     * @param mob session of the caster
     * @param choice a choice of the profile
     * @return ticks, empty when unknown
     */
    default OptionalInt ticksUntilReady(MobKey mob, SkillChoice choice) {
        return ready(mob, choice) ? OptionalInt.of(0) : OptionalInt.of(1);
    }

    /**
     * Readiness for an old answer (M20.7). A vanilla melee is zero ticks and does not ask the port. The default
     * cannot see an exhausted skill, so it reports {@link ReadyIn#UNKNOWN} when {@link #ticksUntilReady} is empty
     * and {@link ReadyIn#in(int)} otherwise. The production adapter returns {@link ReadyIn#EXHAUSTED} for
     * {@code Long.MAX_VALUE}.
     *
     * @param mob session of the caster
     * @param choice choice of the tactic
     * @return the reading
     */
    default ReadyIn readyIn(MobKey mob, SkillChoice choice) {
        if (choice.kind() == ChoiceKind.VANILLA_MELEE) {
            return ReadyIn.in(0);
        }
        var ticks = ticksUntilReady(mob, choice);
        return ticks.isEmpty() ? ReadyIn.UNKNOWN : ReadyIn.in(ticks.getAsInt());
    }

    /**
     * Cheap admission before a candidate cast (M19.5): reach and a present target, without a line of sight. The
     * default admits every choice, so a double that does not model reach still casts. The production adapter checks
     * the reach and leaves the line of sight to {@link #seesTarget}.
     *
     * @param mob session of the caster
     * @param choice candidate choice
     * @param movement movement of the current intent
     * @return whether the choice may be attempted before a line-of-sight read
     */
    default boolean cheaplyAdmitted(MobKey mob, SkillChoice choice, Movement movement) {
        return true;
    }

    /**
     * Whether the caster sees its target (M19.5). Called at most once per mob per tick, and only for the first
     * candidate that still needs a line of sight. The default is {@code true}. The production adapter reads
     * {@code LivingEntity#hasLineOfSight}; {@link #cast} still refuses a choice whose native facts or purpose require
     * a line of sight the caster does not have.
     *
     * @param mob session of the caster
     * @return whether the target is in sight
     */
    default boolean seesTarget(MobKey mob) {
        return true;
    }

    /**
     * Sends the MythicMobs signal of a tactic that starts ({@code PREDICTOR_} plus the tactic in upper case, rule I9)
     * with the mob as trigger. Does nothing when the mob is not active.
     *
     * @param mob session of the mob
     * @param tactic tactic that starts
     */
    void signalTactic(MobKey mob, TacticId tactic);

    /**
     * Adds threat to a participant in the native threat table of the mob (M11.4, M17.7); a negative amount removes
     * threat. Does nothing without a threat table, for a participant offline, dead or in another world, or for a zero
     * or non-finite amount.
     *
     * @param mob session of the mob
     * @param participantId UUID of the participant entity, a player or any other living entity
     * @param amount threat to add
     */
    void addThreat(MobKey mob, UUID participantId, double amount);

    /**
     * Casts with a tell interrupt (M23.4). The default delegates to {@link #cast(MobKey, SkillChoice, Movement)} and
     * drops the interrupt, so a double that only implements that overload still casts. The engine calls it for a tell
     * opened with a lead of 0 on a choice that needs no target and none was captured (SA-02).
     *
     * @param mob session of the caster
     * @param choice choice to cast
     * @param movement movement of the intent
     * @param interrupt flag MythicMobs reads; unused by the default
     * @return the same outcomes as {@link #cast(MobKey, SkillChoice, Movement)}
     */
    default ExecutionOutcome cast(MobKey mob, SkillChoice choice, Movement movement, CastInterrupt interrupt) {
        java.util.Objects.requireNonNull(interrupt, "interrupt");
        return cast(mob, choice, movement);
    }

    /**
     * Casts at the captured target with a tell interrupt (M23.4, SA-02). The engine calls it for a tell opened with a
     * lead of 0, so a poise break during the skill's own wind-up cuts it like a released tell. The default delegates
     * to {@link #cast(MobKey, SkillChoice, Movement, UUID)} and drops the interrupt (W5).
     *
     * @param mob session of the caster
     * @param choice choice to cast
     * @param movement movement of the intent
     * @param expectedTarget target captured for this cast
     * @param interrupt flag MythicMobs reads; unused by the default
     * @return the same outcomes as {@link #cast(MobKey, SkillChoice, Movement, UUID)}
     */
    default ExecutionOutcome cast(MobKey mob, SkillChoice choice, Movement movement, UUID expectedTarget,
                                  CastInterrupt interrupt) {
        java.util.Objects.requireNonNull(interrupt, "interrupt");
        return cast(mob, choice, movement, expectedTarget);
    }

    /**
     * Releases a tell without a shield check (M23.4, errata E23-5). Invulnerability still defers. The default reports
     * the target lost, which is what {@link akyrion.thepredict.NoCombatActions} must do; other methods stay no-ops.
     *
     * @param mob session of the caster
     * @param choice choice opened with the tell
     * @param movement movement of the intent
     * @param expectedTarget target captured when the tell opened
     * @param interrupt flag of this tell
     * @return the release outcome
     */
    default ReleaseOutcome release(MobKey mob, SkillChoice choice, Movement movement, UUID expectedTarget,
                                   CastInterrupt interrupt) {
        java.util.Objects.requireNonNull(expectedTarget, "expectedTarget");
        java.util.Objects.requireNonNull(interrupt, "interrupt");
        return ReleaseOutcome.TARGET_LOST;
    }

    /**
     * Sends one predictor signal. The default does nothing.
     *
     * @param mob session of the caster
     * @param request the signal
     */
    default void cue(MobKey mob, CueRequest request) {
        java.util.Objects.requireNonNull(request, "request");
    }

    /**
     * Writes {@code predictor_stance} in hybrid mode. The default does nothing.
     *
     * @param mob session of the mob
     * @param stance lower-case visible stance, or {@code none}
     */
    default void writeStanceVariable(MobKey mob, String stance) {
        java.util.Objects.requireNonNull(stance, "stance");
    }

    /**
     * Calls {@code setStance} in write mode. The default does nothing.
     *
     * @param mob session of the mob
     * @param stance stance name
     */
    default void writeNativeStance(MobKey mob, String stance) {
        java.util.Objects.requireNonNull(stance, "stance");
    }
}
