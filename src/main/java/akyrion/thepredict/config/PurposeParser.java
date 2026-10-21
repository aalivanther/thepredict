package akyrion.thepredict.config;

import akyrion.thepredict.domain.ChoiceKind;
import akyrion.thepredict.domain.CooldownClass;
import akyrion.thepredict.domain.DamageTier;
import akyrion.thepredict.domain.Delivery;
import akyrion.thepredict.domain.MovementSettings;
import akyrion.thepredict.domain.PurposeRules;
import akyrion.thepredict.domain.PurposeOrigin;
import akyrion.thepredict.domain.SkillPurpose;
import akyrion.thepredict.domain.SkillRisk;
import akyrion.thepredict.domain.SkillRole;
import akyrion.thepredict.domain.UseCondition;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reads the optional {@code Choices.<id>.Purpose} section of a schema 2 profile into a {@link SkillPurpose} with origin
 * {@code DECLARED} and cooldown {@code UNKNOWN}, which the binder completes (M15.2). Key names are exact; enum values
 * ignore case. The limits are the constants of {@link SkillPurpose}. A vanilla melee choice without {@code Purpose}
 * gets its fixed purpose ({@link PurposeRules#vanillaMelee}); a MythicMobs skill choice without it gets none, and the
 * binder reads the {@code Intention} line of its skill or leaves it untyped (M19.2: nothing is inferred from
 * {@code PredictValues}). {@code Purpose} is internal data and an override of the administrator (user decision 7).
 */
final class PurposeParser {
    private static final List<DamageTier> DECLARABLE_DAMAGE = List.of(DamageTier.NONE, DamageTier.LOW,
            DamageTier.MEDIUM, DamageTier.HIGH);

    private PurposeParser() {
    }

    /**
     * Reads the purpose of one choice.
     *
     * @return the declared purpose; without {@code Purpose}, the fixed purpose of a vanilla melee choice or nothing for
     *         a skill choice
     * @throws ConfigurationException with the full path of the first invalid value
     */
    static Optional<SkillPurpose> purpose(ConfigurationSection choice, ChoiceKind kind, MovementSettings movement,
                                          String file, List<ConfigProblem> warnings) {
        var section = ConfigurationValues.section(choice, "Purpose");
        if (section.isEmpty()) {
            return kind == ChoiceKind.VANILLA_MELEE ? Optional.of(PurposeRules.vanillaMelee(movement))
                    : Optional.empty();
        }
        return Optional.of(declared(section.orElseThrow(), kind, movement, file, warnings));
    }

    private static SkillPurpose declared(ConfigurationSection purpose, ChoiceKind kind, MovementSettings movement,
                                         String file, List<ConfigProblem> warnings) {
        ProfileSchema.requireKnown(purpose, ProfileSchema.PURPOSE);
        var roles = roles(purpose);
        var delivery = delivery(purpose);
        requireVanillaHit(purpose, kind, delivery, roles);
        var reach = reach(purpose, kind, delivery, movement);
        var radius = radius(purpose, delivery);
        var bestWhen = names(purpose, "BestWhen", UseCondition.class, 0, SkillPurpose.MAX_BEST_WHEN, "conditions",
                "condition");
        var risks = names(purpose, "Risks", SkillRisk.class, 0, SkillPurpose.MAX_RISKS, "risks", "risk");
        var ignoresShield = ignoresShield(purpose, risks);
        var windUp = windUp(purpose);
        var damage = damage(purpose, roles);
        warnBeyondRangedReach(purpose, reach, movement, file, warnings);
        try {
            return new SkillPurpose(roles.getFirst(), Set.copyOf(roles), delivery, reach, radius, windUp,
                    CooldownClass.UNKNOWN, damage, Set.copyOf(bestWhen), Set.copyOf(risks), ignoresShield,
                    PurposeOrigin.DECLARED);
        } catch (IllegalArgumentException exception) {
            throw new ConfigurationException(purpose.getCurrentPath(), ProfileSchema.yamlNames(exception.getMessage()));
        }
    }

    private static List<SkillRole> roles(ConfigurationSection purpose) {
        if (!purpose.contains("Roles")) {
            throw new ConfigurationException(ConfigurationValues.path(purpose, "Roles"), "is required");
        }
        return names(purpose, "Roles", SkillRole.class, 1, SkillPurpose.MAX_ROLES, "roles", "role");
    }

    /** {@code Reach}; a vanilla melee choice defaults to {@code Movement.MeleeReach}. */
    private static OptionalDouble reach(ConfigurationSection purpose, ChoiceKind kind, Delivery delivery,
                                        MovementSettings movement) {
        var fallback = kind == ChoiceKind.VANILLA_MELEE && !purpose.contains("Reach")
                ? OptionalDouble.of(movement.meleeReachBlocks()) : OptionalDouble.empty();
        return distance(purpose, "Reach", SkillPurpose.MAX_REACH_BLOCKS, reachRequired(delivery),
                reachForbidden(delivery), delivery, fallback);
    }

    /** {@code Radius}: the {@code r} of the MythicMobs radius targeter the skill uses, required exactly for the areas. */
    private static OptionalDouble radius(ConfigurationSection purpose, Delivery delivery) {
        var area = delivery == Delivery.AREA_AROUND_SELF || delivery == Delivery.AREA_AT_TARGET;
        return distance(purpose, "Radius", SkillPurpose.MAX_RADIUS_BLOCKS, area, !area, delivery,
                OptionalDouble.empty());
    }

    private static void requireVanillaHit(ConfigurationSection purpose, ChoiceKind kind, Delivery delivery,
                                          List<SkillRole> roles) {
        if (kind != ChoiceKind.VANILLA_MELEE) {
            return;
        }
        if (delivery != Delivery.MELEE) {
            throw new ConfigurationException(ConfigurationValues.path(purpose, "Delivery"),
                    "must be MELEE for Kind VANILLA_MELEE");
        }
        if (!roles.contains(SkillRole.DAMAGE)) {
            throw new ConfigurationException(ConfigurationValues.path(purpose, "Roles"),
                    "must include DAMAGE for Kind VANILLA_MELEE");
        }
    }

    private static int windUp(ConfigurationSection purpose) {
        var windUp = ConfigurationValues.intValue(purpose, "WindUp", 0);
        if (windUp < 0 || windUp > SkillPurpose.MAX_WIND_UP_TICKS) {
            throw new ConfigurationException(ConfigurationValues.path(purpose, "WindUp"), "must be between 0 and "
                    + SkillPurpose.MAX_WIND_UP_TICKS + " ticks, was " + windUp);
        }
        return windUp;
    }

    private static boolean ignoresShield(ConfigurationSection purpose, List<SkillRisk> risks) {
        var ignoresShield = ConfigurationValues.booleanValue(purpose, "IgnoresShield", false);
        if (ignoresShield && risks.contains(SkillRisk.BLOCKED_BY_SHIELD)) {
            throw new ConfigurationException(ConfigurationValues.path(purpose, "IgnoresShield"),
                    "cannot be true while Risks has BLOCKED_BY_SHIELD");
        }
        return ignoresShield;
    }

    private static Delivery delivery(ConfigurationSection purpose) {
        var text = ConfigurationValues.requiredText(purpose, "Delivery");
        for (var delivery : Delivery.values()) {
            if (delivery.name().equals(normalized(text))) {
                return delivery;
            }
        }
        throw new ConfigurationException(ConfigurationValues.path(purpose, "Delivery"), "unknown delivery '" + text
                + "'; known: " + known(Arrays.asList(Delivery.values())));
    }

    private static OptionalDouble distance(ConfigurationSection purpose, String key, double maximum, boolean required,
                                           boolean forbidden, Delivery delivery, OptionalDouble fallback) {
        var path = ConfigurationValues.path(purpose, key);
        if (!purpose.contains(key)) {
            if (required && fallback.isEmpty()) {
                throw new ConfigurationException(path, "is required for Delivery " + delivery);
            }
            return fallback;
        }
        if (forbidden) {
            throw new ConfigurationException(path, "must be absent for Delivery " + delivery);
        }
        var blocks = ConfigurationValues.decimal(purpose, key, 0.0);
        if (!(blocks > 0.0 && blocks <= maximum)) {
            throw new ConfigurationException(path, "must be greater than 0 and at most " + number(maximum)
                    + " blocks, was " + number(blocks));
        }
        return OptionalDouble.of(blocks);
    }

    private static DamageTier damage(ConfigurationSection purpose, List<SkillRole> roles) {
        var damaging = roles.contains(SkillRole.DAMAGE) || roles.contains(SkillRole.FINISHER);
        var path = ConfigurationValues.path(purpose, "Damage");
        if (!purpose.contains("Damage")) {
            if (damaging) {
                throw new ConfigurationException(path, "is required when Roles has DAMAGE or FINISHER");
            }
            return DamageTier.NONE;
        }
        var text = ConfigurationValues.requiredText(purpose, "Damage");
        var tier = DECLARABLE_DAMAGE.stream().filter(value -> value.name().equals(normalized(text))).findFirst()
                .orElseThrow(() -> new ConfigurationException(path, "unknown damage '" + text + "'; known: "
                        + known(DECLARABLE_DAMAGE)));
        if (damaging && tier == DamageTier.NONE) {
            throw new ConfigurationException(path, "must not be NONE when Roles has DAMAGE or FINISHER");
        }
        return tier;
    }

    private static <E extends Enum<E>> List<E> names(ConfigurationSection purpose, String key, Class<E> type,
                                                     int minimum, int maximum, String plural, String singular) {
        var path = ConfigurationValues.path(purpose, key);
        var texts = ConfigurationValues.textList(purpose, key);
        if (texts.size() < minimum || texts.size() > maximum) {
            throw new ConfigurationException(path, "must list " + minimum + " to " + maximum + " " + plural
                    + ", had " + texts.size());
        }
        var values = new ArrayList<E>(texts.size());
        var seen = EnumSet.noneOf(type);
        for (var text : texts) {
            var value = Arrays.stream(type.getEnumConstants()).filter(constant -> constant.name().equals(normalized(text)))
                    .findFirst().orElseThrow(() -> new ConfigurationException(path, "unknown " + singular + " '" + text
                            + "'; known: " + known(Arrays.asList(type.getEnumConstants()))));
            if (!seen.add(value)) {
                throw new ConfigurationException(path, "lists '" + text + "' twice");
            }
            values.add(value);
        }
        return values;
    }

    private static void warnBeyondRangedReach(ConfigurationSection purpose, OptionalDouble reach,
                                              MovementSettings movement, String file, List<ConfigProblem> warnings) {
        if (purpose.contains("Reach") && reach.isPresent() && reach.getAsDouble() > movement.rangedReachBlocks()) {
            warnings.add(ConfigProblem.warning(file, ConfigurationValues.path(purpose, "Reach"),
                    "is beyond RangedReach; Jev reads it as beyond ranged reach"));
        }
    }

    private static boolean reachRequired(Delivery delivery) {
        return delivery == Delivery.MELEE || delivery == Delivery.CONE;
    }

    private static boolean reachForbidden(Delivery delivery) {
        return delivery == Delivery.SELF || delivery == Delivery.AREA_AROUND_SELF;
    }

    private static String normalized(String text) {
        return text.trim().toUpperCase(Locale.ROOT);
    }

    private static String known(List<? extends Enum<?>> values) {
        return values.stream().map(Enum::name).collect(Collectors.joining(", "));
    }

    private static String number(double value) {
        return value == Math.rint(value) && Math.abs(value) < 1e9 ? Long.toString((long) value) : Double.toString(value);
    }
}
