package akyrion.thepredict.adapter.server.binding;

import akyrion.thepredict.adapter.server.binding.BindingProblem.Severity;
import akyrion.thepredict.domain.DamageTier;
import akyrion.thepredict.domain.IntentionIssue;
import akyrion.thepredict.domain.IntentionParse;
import akyrion.thepredict.domain.IntentionToken;
import akyrion.thepredict.domain.IntentionVocabulary;
import akyrion.thepredict.domain.NativeSkillFacts;
import akyrion.thepredict.domain.PurposeOrigin;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.SkillPurpose;
import akyrion.thepredict.domain.SkillRisk;
import akyrion.thepredict.domain.SkillRole;
import akyrion.thepredict.domain.TacticalProfile;
import io.lumine.mythic.api.skills.Skill;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;

/**
 * Warnings of the {@code Intention} line and of the native facts of a skill (M19.2, M19.3). They never exclude a
 * profile: ThePredict cannot make MythicMobs reject its own file, so an unknown primitive or an unusable line is a
 * {@code WARNING} with the file and path of the skill ({@code Skills/<file>:<skill>.Intention[<i>]}, rule C13 as
 * amended). Warnings about a skill are given once per binding, not once per profile that shares it.
 */
final class IntentionWarnings {
    /** Tolerance when a declared reach is compared with {@code targetwithin}, in blocks. */
    static final double REACH_TOLERANCE_BLOCKS = 0.05;

    private IntentionWarnings() {
    }

    /** Warns about each ignored primitive and each alias of the line of a skill. */
    static void intention(Skill skill, IntentionParse parse, List<BindingProblem> problems) {
        var warnings = switch (parse) {
            case IntentionParse.Parsed parsed -> parsed.warnings();
            case IntentionParse.Invalid invalid -> invalid.warnings();
            case IntentionParse.Absent absent -> List.<IntentionIssue>of();
        };
        for (var issue : warnings) {
            problems.add(new BindingProblem(Severity.WARNING, Optional.empty(),
                    SkillMetadata.intentionPath(skill, issue.index()), message(issue)));
        }
    }

    private static String message(IntentionIssue issue) {
        var token = issue.token();
        return switch (issue.kind()) {
            case REPEATED_TOKEN -> "lists '" + token + "' twice";
            case FUTURE_TOKEN -> "intention '" + token + "' is newer than vocabulary v" + IntentionVocabulary.VERSION
                    + "; update ThePredict or remove it";
            case ALIAS_USED -> "'" + token + "' is a retired name; write '" + IntentionVocabulary.ALIASES.get(token)
                    + "'";
            default -> "unknown intention '" + token + "' (vocabulary v" + IntentionVocabulary.VERSION + "); known: "
                    + known();
        };
    }

    private static String known() {
        var joiner = new StringJoiner(", ");
        for (var token : IntentionToken.values()) {
            joiner.add(token.name());
        }
        return joiner.toString();
    }

    /** The errors of an invalid line, for the warning that leaves the choice untyped. */
    static String causes(IntentionParse.Invalid invalid) {
        var joiner = new StringJoiner(", ");
        invalid.errors().forEach(error -> joiner.add(error.kind().name()));
        return joiner.toString();
    }

    /** Warns about a line that cannot be used: the choice stays untyped. */
    static void unusable(Skill skill, String cause, List<BindingProblem> problems) {
        problems.add(skillWarning(skill, "Intention", "is not usable (" + cause + "); the choice is untyped"));
    }

    /**
     * Warns about a skill choice left untyped without a line: {@code PredictValues} no longer give a purpose, and
     * without them the skill has no {@code Intention} at all.
     */
    static void untyped(Skill skill, List<BindingProblem> problems) {
        var skillId = skill.getInternalName();
        problems.add(SkillMetadata.hasPredictValues(skill)
                ? skillWarning(skill, "PredictValues", "PredictValues of skill '" + skillId
                        + "' are no longer read; write an Intention")
                : skillWarning(skill, "Intention", "skill '" + skillId + "' has no Intention; the choice is untyped"
                        + " and only casts with its own tactic"));
    }

    /** Informs that the {@code Purpose} of the profile wins over the line of the skill. */
    static void overridden(TacticalProfile profile, SkillChoice choice, String skillId, List<BindingProblem> problems) {
        var path = "Choices." + choice.id() + ".Purpose";
        problems.add(BindingProblem.of(Severity.WARNING, profile.id(), path, path + " overrides the Intention of skill '"
                + skillId + "'"));
    }

    /** A warning about a key of a skill, without profile. */
    static BindingProblem skillWarning(Skill skill, String key, String message) {
        return new BindingProblem(Severity.WARNING, Optional.empty(), SkillMetadata.skillPath(skill, key), message);
    }

    /**
     * Compares a purpose with what MythicMobs enforces for its skill (M19.3): line of sight, damage, the reach and the
     * wind-up of a declared purpose, and the shield. A declared purpose is reported under its choice; a purpose read
     * from the skill under the skill, once per binding.
     */
    static void nativeCoherence(TacticalProfile profile, SkillChoice choice, Skill skill, SkillPurpose purpose,
                                NativeSkillFacts facts, Set<String> noted, List<BindingProblem> problems) {
        var declared = purpose.origin() == PurposeOrigin.DECLARED;
        if (!declared && !noted.add(skill.getInternalName() + "#coherence")) {
            return;
        }
        var path = "Choices." + choice.id() + ".Purpose";
        for (var message : incoherences(skill.getInternalName(), purpose, facts, declared)) {
            problems.add(declared ? BindingProblem.of(Severity.WARNING, profile.id(), path, message)
                    : skillWarning(skill, "Intention", message));
        }
    }

    private static List<String> incoherences(String skillId, SkillPurpose purpose, NativeSkillFacts facts,
                                             boolean declared) {
        var messages = new ArrayList<String>();
        var sight = purpose.risks().contains(SkillRisk.NEEDS_LINE_OF_SIGHT);
        if (facts.requiresLineOfSight() != sight) {
            messages.add(sight ? "purpose has the NEEDS_LINE_OF_SIGHT risk but skill '" + skillId
                    + "' does not check lineofsight" : "skill '" + skillId
                    + "' checks lineofsight but its purpose has no NEEDS_LINE_OF_SIGHT risk");
        }
        var damagingRole = purpose.roles().contains(SkillRole.DAMAGE) || purpose.roles().contains(SkillRole.FINISHER);
        if (damagingRole && !facts.hasDamagingMechanic()) {
            messages.add("purpose deals damage but skill '" + skillId + "' has no damage mechanic");
        } else if (facts.hasDamagingMechanic() && purpose.damage() == DamageTier.NONE) {
            messages.add("skill '" + skillId + "' has a damage mechanic but the purpose deals no damage");
        }
        if (facts.damageIgnoresShield() && purpose.risks().contains(SkillRisk.BLOCKED_BY_SHIELD)) {
            messages.add("skill '" + skillId + "' ignores shields but the purpose says BLOCKED_BY_SHIELD");
        }
        if (declared) {
            declaredNumbers(skillId, purpose, facts, messages);
        }
        return messages;
    }

    private static void declaredNumbers(String skillId, SkillPurpose purpose, NativeSkillFacts facts,
                                        List<String> messages) {
        var within = facts.targetWithinBlocks();
        var reach = purpose.reachBlocks();
        if (within.isPresent() && reach.isPresent()
                && Math.abs(within.getAsDouble() - reach.getAsDouble()) > REACH_TOLERANCE_BLOCKS) {
            messages.add("Reach " + number(reach.getAsDouble()) + " differs from targetwithin{d="
                    + number(within.getAsDouble()) + "} of skill '" + skillId + "'; MythicMobs refuses beyond "
                    + number(within.getAsDouble()));
        }
        var windUp = facts.windUpTicks();
        if (windUp.isPresent() && windUp.getAsInt() != purpose.windUpTicks()) {
            messages.add("WindUp " + purpose.windUpTicks() + " differs from the delays of skill '" + skillId + "' ("
                    + windUp.getAsInt() + " ticks)");
        }
    }

    private static String number(double blocks) {
        return blocks == Math.rint(blocks) ? Long.toString((long) blocks) : Double.toString(blocks);
    }
}
