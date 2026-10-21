package akyrion.thepredict.adapter.server.binding;

import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * What {@link NativeSkillReader} reads of one MythicMobs metaskill (M19.3): its cooldown, charges and mode, its
 * condition lines and its top-level mechanic lines, each as MythicMobs parsed it. The live implementation reads the
 * public accessors of {@code MetaSkill} ({@link LiveNativeSkill}); tests build it from YAML lines, because the
 * MythicMobs classes cannot be doubled (rule P4). Server thread only for the live one.
 */
public interface NativeSkillView {
    /**
     * Tells whether the skill YAML declares {@code Cooldown}.
     *
     * @return whether {@code Cooldown} is set
     */
    boolean cooldownDeclared();

    /**
     * The native cooldown with a fixed readable value.
     *
     * @return seconds, empty without one (a placeholder or a random range)
     */
    OptionalDouble staticCooldownSeconds();

    /**
     * Charges as MythicMobs keeps them.
     *
     * @return {@code max(1, Charges)}
     */
    int charges();

    /**
     * Name of the {@code CooldownMode}.
     *
     * @return for example {@code SEQUENTIAL}
     */
    String cooldownMode();

    /**
     * The caster conditions ({@code Conditions}).
     *
     * @return the lines in order
     */
    List<Line> conditions();

    /**
     * The target conditions ({@code TargetConditions}).
     *
     * @return the lines in order
     */
    List<Line> targetConditions();

    /**
     * The top-level mechanics ({@code Skills}), without following {@code skill{s=…}} nor nested metaskills; such a
     * line is marked {@link Line#nested()}.
     *
     * @return the lines in order
     */
    List<Line> mechanics();

    /**
     * Converts a static duration of MythicMobs to ticks, as {@code delay} reads it: a number without suffix is in ticks,
     * and {@code 1s} or {@code 20t} carry their unit (5.12.1).
     *
     * @param text the duration text
     * @return the ticks, empty for a placeholder, a random range or a text MythicMobs does not read as static
     */
    OptionalInt ticks(String text);

    /** Kind of targeter of a mechanic, as far as the reader needs it. */
    enum Targeter {
        /** No targeter, or one whose radius the reader does not read. */
        OTHER,
        /** A radius targeter: {@code @PlayersInRadius}, {@code @MobsInRadius} or {@code @EntitiesInRadius}. */
        RADIUS,
        /** {@code @EntitiesInCone}. */
        CONE
    }

    /**
     * One condition or mechanic line.
     *
     * @param typeName the type name MythicMobs gives it ({@code getTypeName()}), compared without case
     * @param line the whole line as MythicMobs keeps it ({@code getLine()} or {@code getConfigLine()})
     * @param damaging whether the mechanic is a {@code DamagingMechanic}; {@code false} for a condition
     * @param targeter kind of targeter of a mechanic; {@code OTHER} for a condition
     * @param required whether a condition has the action {@code REQUIRED}; {@code false} for a mechanic
     * @param nested whether the mechanic runs skills the reader does not follow: {@code skill}/{@code metaskill},
     *               {@code randomskill}, a projectile or an aura (A12); {@code false} for a condition
     */
    record Line(String typeName, String line, boolean damaging, Targeter targeter, boolean required, boolean nested) {
        /**
         * Validates the line.
         *
         * @throws NullPointerException if a component is {@code null}
         */
        public Line {
            Objects.requireNonNull(typeName, "typeName");
            Objects.requireNonNull(line, "line");
            Objects.requireNonNull(targeter, "targeter");
        }

        /**
         * A line that nests nothing.
         *
         * @param typeName the type name
         * @param line the whole line
         * @param damaging whether the mechanic is a {@code DamagingMechanic}
         * @param targeter kind of targeter
         * @param required whether a condition is {@code REQUIRED}
         */
        public Line(String typeName, String line, boolean damaging, Targeter targeter, boolean required) {
            this(typeName, line, damaging, targeter, required, false);
        }
    }
}
