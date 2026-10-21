package akyrion.thepredict.adapter.server.binding;

import akyrion.thepredict.domain.CooldownClass;
import akyrion.thepredict.domain.PredictValue;
import io.lumine.mythic.api.mobs.MythicMob;
import io.lumine.mythic.api.skills.Skill;
import io.lumine.mythic.api.skills.placeholders.PlaceholderDuration;
import io.lumine.mythic.core.skills.AbstractSkill;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reads the MythicMobs metadata the binder checks for each skill choice (M03.4, M15.3).
 *
 * <p>Evidence (javap on MythicMobs Premium 5.13.1): {@code AbstractSkill.getCooldown()} returns a
 * {@code PlaceholderDuration}, an {@code IGenericPlaceholder<Duration>} with {@code isStatic()} and {@code isPresent()}
 * of {@code IPlaceholder} and {@code get()}; {@code PlaceholderDuration} has no {@code getSeconds()}, which
 * {@code io.lumine.mythic.bukkit.utils.numbers.Duration} has. {@code MetaSkill} extends {@code AbstractSkill}.
 */
final class SkillMetadata {
    /** Largest cooldown, in seconds, of the class {@code SHORT}. */
    static final double SHORT_SECONDS = 3.0;
    /** Largest cooldown, in seconds, of the class {@code MEDIUM}. */
    static final double MEDIUM_SECONDS = 10.0;
    /** Compatibility {@code CD} in ticks from which a vanilla melee choice counts as {@code MEDIUM}. */
    static final int VANILLA_MEDIUM_CD_TICKS = 60;

    private SkillMetadata() {
    }

    /**
     * Reads {@code PredictValues} as history: since M19 they never give a purpose (user decision 5), so invalid labels
     * are not an error any more and give an empty set.
     */
    static Set<PredictValue> predictValuesOrEmpty(Skill skill) {
        var entries = lines(skill, "PredictValues");
        if (entries.isEmpty()) {
            return Set.of();
        }
        try {
            return PredictValue.parse(entries);
        } catch (IllegalArgumentException exception) {
            return Set.of();
        }
    }

    /**
     * A problem in the historical {@code PredictValues} list: absent when the key is missing or every label is known.
     * An empty list and an unknown label stay warnings; they never become a purpose (decision 5).
     */
    static Optional<String> predictValuesProblem(Skill skill) {
        if (!hasPredictValues(skill)) {
            return Optional.empty();
        }
        var entries = lines(skill, "PredictValues");
        if (entries.isEmpty()) {
            return Optional.of("PredictValues requires at least one entry");
        }
        try {
            PredictValue.parse(entries);
            return Optional.empty();
        } catch (IllegalArgumentException exception) {
            return Optional.of(exception.getMessage());
        }
    }

    /** Tells whether the skill YAML has a {@code PredictValues} key. */
    static boolean hasPredictValues(Skill skill) {
        return skill.getConfig().isSet("PredictValues");
    }

    /**
     * Reads the {@code Intention} line as {@code PredictValues} are read (M19.2): a YAML list or a comma-separated text;
     * empty when the key is absent.
     */
    static List<String> intention(Skill skill) {
        return lines(skill, "Intention");
    }

    /**
     * Path of the {@code Intention} line of a skill for a warning: {@code Skills/<file>:<skill>.Intention}, with
     * {@code [index]} for one primitive.
     *
     * @param index position of the primitive, or a negative value for the whole line
     */
    static String intentionPath(Skill skill, int index) {
        return skillPath(skill, "Intention") + (index >= 0 ? "[" + index + "]" : "");
    }

    /** Path of a key of a skill: {@code Skills/<file>:<skill>.<key>}. */
    static String skillPath(Skill skill, String key) {
        var file = skill.getConfig().getFileName();
        return "Skills/" + (file == null || file.isBlank() ? "?" : file) + ":" + skill.getInternalName() + "." + key;
    }

    private static List<String> lines(Skill skill, String key) {
        var config = skill.getConfig();
        if (!config.isSet(key)) {
            return List.of();
        }
        List<String> raw = config.isList(key) ? config.getStringList(key)
                : Arrays.asList(Objects.toString(config.getString(key, ""), "").split(","));
        return raw.stream().filter(Objects::nonNull).filter(value -> !value.isBlank()).toList();
    }

    /**
     * Cooldown class of a skill (M15.3): a skill that is not an {@code AbstractSkill} is {@code UNKNOWN}; without the
     * {@code Cooldown} key its cooldown is 0 s; a {@code Cooldown} without a fixed readable value ({@code null},
     * {@code !isPresent()} or {@code !isStatic()}) is {@code UNKNOWN}; otherwise {@code getCooldown().get().getSeconds()}.
     * The compatibility {@code CD} adds {@code extraCooldownTicks / 20} seconds.
     */
    static CooldownClass cooldownClass(Skill skill, int extraCooldownTicks) {
        if (!(skill instanceof AbstractSkill regular)) {
            return CooldownClass.UNKNOWN;
        }
        return cooldownClass(hasNativeCooldown(skill), regular.getCooldown(), extraCooldownTicks);
    }

    /** Cooldown class from whether the YAML has {@code Cooldown}, the parsed native value, which may be null, and the {@code CD}. */
    static CooldownClass cooldownClass(boolean declared, PlaceholderDuration cooldown, int extraCooldownTicks) {
        var seconds = 0.0;
        if (declared) {
            if (cooldown == null || !cooldown.isPresent() || !cooldown.isStatic()) {
                return CooldownClass.UNKNOWN;
            }
            var duration = cooldown.get();
            if (duration == null) {
                return CooldownClass.UNKNOWN;
            }
            seconds = duration.getSeconds();
        }
        return classOf(seconds + extraCooldownTicks / 20.0);
    }

    /** Class of a cooldown in seconds: 0 or less {@code NONE}, up to 3 {@code SHORT}, up to 10 {@code MEDIUM}, more {@code LONG}. */
    static CooldownClass classOf(double seconds) {
        if (seconds <= 0.0) {
            return CooldownClass.NONE;
        }
        if (seconds <= SHORT_SECONDS) {
            return CooldownClass.SHORT;
        }
        return seconds <= MEDIUM_SECONDS ? CooldownClass.MEDIUM : CooldownClass.LONG;
    }

    /**
     * Cooldown class of a vanilla melee choice: {@code SHORT}, because its pace comes from the 10 ticks of vanilla
     * invulnerability and the {@code CD} (M10.9), or {@code MEDIUM} with a {@code CD} of at least 60 ticks.
     */
    static CooldownClass vanillaCooldownClass(int extraCooldownTicks) {
        return extraCooldownTicks >= VANILLA_MEDIUM_CD_TICKS ? CooldownClass.MEDIUM : CooldownClass.SHORT;
    }

    /** Tells whether the skill declares its native {@code Cooldown}. */
    static boolean hasNativeCooldown(Skill skill) {
        return skill.getConfig().isSet("Cooldown");
    }

    /**
     * Tells whether {@code CastMode.ANY_READY} can pace the skill from a fixed {@code Cooldown}. A real
     * {@code AbstractSkill} is paced only when its class is neither {@code NONE} nor {@code UNKNOWN}. A test double
     * that is not an {@code AbstractSkill} is paced when the {@code Cooldown} key is present: its duration cannot be
     * classified, and a present key is not the absent-cooldown case the warning names.
     */
    static boolean hasStaticCooldown(Skill skill) {
        if (!(skill instanceof AbstractSkill)) {
            return hasNativeCooldown(skill);
        }
        var pace = cooldownClass(skill, 0);
        return pace != CooldownClass.NONE && pace != CooldownClass.UNKNOWN;
    }

    /** Tells whether the mob also casts the skill from its own {@code Skills} list with a normal trigger. */
    static boolean triggeredByMob(MythicMob mob, String skillId) {
        var reference = Pattern.compile("(?<![A-Za-z0-9_-])" + Pattern.quote(skillId) + "(?![A-Za-z0-9_-])");
        var lines = mob.getConfig().getStringList("Skills");
        return lines != null && lines.stream().anyMatch(line -> line.contains("~on") && reference.matcher(line).find());
    }
}
