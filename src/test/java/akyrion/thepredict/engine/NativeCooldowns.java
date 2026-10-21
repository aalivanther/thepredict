package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ChoiceId;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.SkillChoice;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * The native {@code Cooldown} of MythicMobs skills for the combat action doubles: a skill cast by a mob is not ready
 * again until its cooldown in ticks has passed, per caster and choice, as {@code CombatActionsAdapter} reads it with
 * {@code onCooldown}. A choice without its own value uses the default; a vanilla melee choice is always ready.
 */
final class NativeCooldowns {
    private final LongSupplier tick;
    private final Map<ChoiceId, Integer> ticks = new HashMap<>();
    private final Map<UUID, Map<ChoiceId, Long>> lastCast = new HashMap<>();
    private int defaultTicks;

    NativeCooldowns(LongSupplier tick, int defaultTicks) {
        this.tick = tick;
        this.defaultTicks = defaultTicks;
    }

    /** Sets the cooldown of one choice, in ticks. */
    void set(ChoiceId choice, int cooldownTicks) {
        ticks.put(choice, cooldownTicks);
    }

    /** Sets the cooldown of every choice without its own value, in ticks. */
    void setDefault(int cooldownTicks) {
        defaultTicks = cooldownTicks;
    }

    /** Tells whether the native cooldown of a choice has passed for a mob now. */
    boolean ready(MobKey mob, SkillChoice choice) {
        if (choice.mythicSkillId().isEmpty()) {
            return true;
        }
        var last = lastCast.getOrDefault(mob.entityId(), Map.of()).get(choice.id());
        return last == null || tick.getAsLong() - last >= ticks.getOrDefault(choice.id(), defaultTicks);
    }

    /** Starts the cooldown of a choice a mob has just cast. */
    void cast(MobKey mob, SkillChoice choice) {
        lastCast.computeIfAbsent(mob.entityId(), id -> new HashMap<>()).put(choice.id(), tick.getAsLong());
    }
}
