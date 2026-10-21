package akyrion.thepredict.adapter.server.binding;

import io.lumine.mythic.api.skills.placeholders.PlaceholderDuration;
import io.lumine.mythic.bukkit.utils.numbers.Duration;
import io.lumine.mythic.bukkit.utils.numbers.DurationUnit;
import io.lumine.mythic.core.skills.MetaSkill;
import io.lumine.mythic.core.skills.SkillCondition;
import io.lumine.mythic.core.skills.SkillMechanic;
import io.lumine.mythic.core.skills.SkillTargeter;
import io.lumine.mythic.core.skills.auras.Aura;
import io.lumine.mythic.core.skills.conditions.ConditionAction;
import io.lumine.mythic.core.skills.damage.DamagingMechanic;
import io.lumine.mythic.core.skills.mechanics.MetaSkillMechanic;
import io.lumine.mythic.core.skills.mechanics.RandomSkillMechanic;
import io.lumine.mythic.core.skills.projectiles.Projectile;
import io.lumine.mythic.core.skills.targeters.EntitiesInConeTargeter;
import io.lumine.mythic.core.skills.targeters.EntitiesNearbyTargeter;
import io.lumine.mythic.core.skills.targeters.MobsInRadiusTargeter;
import io.lumine.mythic.core.skills.targeters.PlayersInRadiusTargeter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Queue;

/**
 * {@link NativeSkillView} over a loaded MythicMobs metaskill, through its public accessors only (rules A1, C11). Server
 * thread only, while the binder runs.
 *
 * <p>Evidence (javap, MythicMobs Premium 5.13.1, R5 to R8 of M19): {@code AbstractSkill} has {@code getCooldown()},
 * {@code getCharges()}, {@code getCooldownMode()}, {@code getConditions()} and {@code getConditionsTarget()};
 * {@code MetaSkill.getBaseSkill().getMechanics()} is a {@code Queue} of {@code SkillMechanic}, each with
 * {@code getTypeName()}, {@code getConfigLine()} and {@code getTargeter()}; {@code SkillCondition} has
 * {@code getTypeName()}, {@code getLine()} and {@code getACTION()}; {@code DamagingMechanic} is the base of the damage
 * mechanics; {@code PlaceholderDuration.of(text, DurationUnit.TICKS)} is how {@code DelaySkill} reads its ticks. Whether
 * {@code getLine()}, {@code getConfigLine()} and {@code getTypeName()} return the expected texts at run time is checked
 * on the server (session 19.1, DT-45 and DT-46).
 */
final class LiveNativeSkill implements NativeSkillView {
    private final MetaSkill skill;

    LiveNativeSkill(MetaSkill skill) {
        this.skill = Objects.requireNonNull(skill, "skill");
    }

    @Override
    public boolean cooldownDeclared() {
        return skill.getConfig().isSet("Cooldown");
    }

    @Override
    public OptionalDouble staticCooldownSeconds() {
        var cooldown = skill.getCooldown();
        if (cooldown == null || !cooldown.isPresent() || !cooldown.isStatic() || cooldown.get() == null) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(cooldown.get().getSeconds());
    }

    @Override
    public int charges() {
        return Math.max(1, skill.getCharges());
    }

    @Override
    public String cooldownMode() {
        var mode = skill.getCooldownMode();
        return mode == null ? "SEQUENTIAL" : mode.name();
    }

    @Override
    public List<Line> conditions() {
        return lines(skill.getConditions());
    }

    @Override
    public List<Line> targetConditions() {
        return lines(skill.getConditionsTarget());
    }

    @Override
    public List<Line> mechanics() {
        var base = skill.getBaseSkill();
        if (base == null || base.getMechanics() == null) {
            return List.of();
        }
        Queue<SkillMechanic> queue = base.getMechanics();
        var lines = new ArrayList<Line>();
        for (var mechanic : queue) {
            lines.add(new Line(Objects.toString(mechanic.getTypeName(), ""), Objects.toString(mechanic.getConfigLine(),
                    ""), mechanic instanceof DamagingMechanic, targeter(mechanic.getTargeter().orElse(null)), false,
                    nested(mechanic)));
        }
        return List.copyOf(lines);
    }

    /**
     * Whether a mechanic runs skills the reader does not follow (A12). Evidence (javap): {@code MetaSkillMechanic}
     * ({@code skill}, {@code metaskill}) and {@code RandomSkillMechanic} ({@code randomskill}) extend
     * {@code SkillMechanic} and hold other skills; {@code Projectile} and {@code Aura} extend {@code SkillMechanic},
     * not {@code DamagingMechanic}, and deal their damage in {@code onHit}/{@code onTick} skills.
     */
    private static boolean nested(SkillMechanic mechanic) {
        return mechanic instanceof MetaSkillMechanic || mechanic instanceof RandomSkillMechanic
                || mechanic instanceof Projectile || mechanic instanceof Aura;
    }

    @Override
    public OptionalInt ticks(String text) {
        return staticTicks(text);
    }

    /**
     * Ticks of a static MythicMobs duration, as {@code DelaySkill} reads it. Empty for a placeholder, a range or a text
     * MythicMobs does not keep as a fixed duration.
     *
     * @param text the duration text
     * @return the ticks
     */
    static OptionalInt staticTicks(String text) {
        try {
            var duration = PlaceholderDuration.of(text, DurationUnit.TICKS);
            if (duration == null || !duration.isStatic() || duration.get() == null) {
                return OptionalInt.empty();
            }
            return OptionalInt.of(duration.get().getTicks());
        } catch (IllegalStateException unavailable) {
            return parsedTicks(text);
        } catch (RuntimeException exception) {
            return OptionalInt.empty();
        }
    }

    /**
     * Ticks of a static duration when MythicMobs has not started, which is the only way {@code PlaceholderDuration.of}
     * can run. A placeholder or a range is empty, as a non-static placeholder is.
     */
    private static OptionalInt parsedTicks(String text) {
        try {
            var duration = Duration.parse(text, DurationUnit.TICKS);
            return duration == null ? OptionalInt.empty() : OptionalInt.of(duration.getTicks());
        } catch (RuntimeException exception) {
            return OptionalInt.empty();
        }
    }

    private static List<Line> lines(List<SkillCondition> conditions) {
        if (conditions == null) {
            return List.of();
        }
        var lines = new ArrayList<Line>(conditions.size());
        for (var condition : conditions) {
            lines.add(new Line(Objects.toString(condition.getTypeName(), ""), Objects.toString(condition.getLine(), ""),
                    false, Targeter.OTHER, condition.getACTION() == ConditionAction.REQUIRED));
        }
        return List.copyOf(lines);
    }

    private static Targeter targeter(SkillTargeter targeter) {
        if (targeter instanceof PlayersInRadiusTargeter || targeter instanceof MobsInRadiusTargeter
                || targeter instanceof EntitiesNearbyTargeter) {
            return Targeter.RADIUS;
        }
        return targeter instanceof EntitiesInConeTargeter ? Targeter.CONE : Targeter.OTHER;
    }
}
