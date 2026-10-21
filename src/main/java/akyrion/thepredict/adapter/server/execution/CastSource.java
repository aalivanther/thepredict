package akyrion.thepredict.adapter.server.execution;

import io.lumine.mythic.api.skills.Skill;
import org.bukkit.entity.LivingEntity;

import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * What {@link CombatActionsAdapter} does through MythicMobs on a mob, kept apart so the adapter is tested with
 * doubles (rule P4). The live implementation is {@link MythicCastSource}. Server thread only; every lookup is O(1) by
 * UUID.
 */
public interface CastSource {
    /**
     * Resolves the caster.
     *
     * @param mobId UUID of the mob
     * @return the living entity of an active, alive MythicMobs mob
     */
    Optional<LivingEntity> caster(UUID mobId);

    /**
     * Finds the target as the capture does: the top threat holder, otherwise {@code Mob#getTarget()}, a valid, living
     * player in the mob's world or any other living entity the participant policy of its profile admits (M17.7).
     *
     * @param mobId UUID of the mob
     * @return the target, if any
     */
    Optional<LivingEntity> target(UUID mobId);

    /**
     * Tells whether a skill is on its native cooldown for the mob.
     *
     * @param mobId UUID of the mob
     * @param skill loaded skill
     * @return {@code true} while on cooldown, or when the mob is not active
     */
    boolean onCooldown(UUID mobId, Skill skill);

    /**
     * Remaining native cooldown in milliseconds (M19.4). {@code 0} when a charge remains, {@link Long#MAX_VALUE} when
     * nothing is recharging, empty when the skill is not an {@code AbstractSkill} or the mob is inactive. The default
     * is empty, so a double that only models {@link #onCooldown} keeps compiling (W5).
     *
     * @param mobId UUID of the mob
     * @param skill loaded skill
     * @return milliseconds, or empty
     */
    default OptionalLong remainingMillis(UUID mobId, Skill skill) {
        return OptionalLong.empty();
    }

    /**
     * The target of the mob's own AI ({@code Mob.getTarget()}), distinct from {@link #target} (M19.4). Empty when the
     * mob has none. The default is empty.
     *
     * @param mobId UUID of the mob
     * @return UUID of the AI target
     */
    default Optional<UUID> aiTarget(UUID mobId) {
        return Optional.empty();
    }

    /**
     * Evaluates, without side effects, the {@code REQUIRED} and {@code CANCEL} conditions of a skill with no target.
     *
     * @param mobId UUID of the mob
     * @param skill loaded skill
     * @return {@code true} when they all pass; {@code false} when the mob is not active
     */
    boolean conditionsHold(UUID mobId, Skill skill);

    /**
     * Evaluates, without side effects, the {@code REQUIRED} and {@code CANCEL} conditions of a skill at a target.
     *
     * @param mobId UUID of the mob
     * @param skill loaded skill
     * @param target target, a player or any other living entity
     * @return {@code true} when they all pass; {@code false} when the mob is not active
     */
    boolean conditionsHoldAt(UUID mobId, Skill skill, LivingEntity target);

    /**
     * Executes a skill without a target through {@code executeIfUsable}.
     *
     * @param mobId UUID of the mob
     * @param skill loaded skill
     * @return whether MythicMobs executed it; {@code false} when the mob is not active
     */
    boolean execute(UUID mobId, Skill skill);

    /**
     * Executes a skill at a target through {@code executeIfUsable}.
     *
     * @param mobId UUID of the mob
     * @param skill loaded skill
     * @param target target, a player or any other living entity
     * @return whether MythicMobs executed it; {@code false} when the mob is not active
     */
    boolean executeAt(UUID mobId, Skill skill, LivingEntity target);

    /**
     * Executes a skill at a target and stores the tell's termination flag (M23.7). The default ignores the flag, so a
     * double that only implements {@link #executeAt} still casts (W5).
     *
     * @param mobId UUID of the mob
     * @param skill loaded skill
     * @param target target
     * @param terminated flag MythicMobs reads after the next delay
     * @return whether MythicMobs executed it
     */
    default boolean executeAt(UUID mobId, Skill skill, LivingEntity target,
                              java.util.concurrent.atomic.AtomicBoolean terminated) {
        java.util.Objects.requireNonNull(terminated, "terminated");
        return executeAt(mobId, skill, target);
    }

    /**
     * Whether the mob has a skill that listens for this signal (M23.5). The default is false, so a cue is not sent
     * until a source knows the handler.
     *
     * @param mobId UUID of the mob
     * @param signal exact signal name
     * @return whether a handler is bound
     */
    default boolean handlesSignal(UUID mobId, String signal) {
        java.util.Objects.requireNonNull(signal, "signal");
        return false;
    }

    /**
     * Writes a string variable on the mob. The default does nothing.
     *
     * @param mobId UUID of the mob
     * @param key variable name
     * @param value variable value
     */
    default void writeVariable(UUID mobId, String key, String value) {
        java.util.Objects.requireNonNull(key, "key");
        java.util.Objects.requireNonNull(value, "value");
    }

    /**
     * Calls {@code setStance}. The default does nothing.
     *
     * @param mobId UUID of the mob
     * @param stance stance name
     */
    default void setStance(UUID mobId, String stance) {
        java.util.Objects.requireNonNull(stance, "stance");
    }

    /**
     * Sends a MythicMobs signal with the mob as trigger ({@code ActiveMob.signalMob}).
     *
     * @param mobId UUID of the mob
     * @param signal exact signal name
     * @return whether the mob was active and received it
     */
    boolean signal(UUID mobId, String signal);

    /**
     * Adds ({@code threatGain}) or removes ({@code threatLoss}) threat of a participant in the mob's threat table.
     *
     * @param mobId UUID of the mob
     * @param participant participant in the same world, a player or any other living entity
     * @param amount positive to add, negative to remove
     * @return whether the mob had a threat table and the change was applied
     */
    boolean changeThreat(UUID mobId, LivingEntity participant, double amount);
}
