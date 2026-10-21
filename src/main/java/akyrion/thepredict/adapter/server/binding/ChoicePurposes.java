package akyrion.thepredict.adapter.server.binding;

import akyrion.thepredict.domain.ChoiceTelegraph;
import akyrion.thepredict.domain.IntentionParse;
import akyrion.thepredict.domain.IntentionVocabulary;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.SkillPurpose;
import akyrion.thepredict.domain.TacticalProfile;
import io.lumine.mythic.api.skills.Skill;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves the purpose of each bound choice (M15.3, M19.2), so no bound profile keeps a provisional purpose. Server
 * thread only, like the binder.
 *
 * <p>Precedence, by whole purpose: the {@code Purpose} of the profile ({@code DECLARED}, internal data and an override
 * of the administrator) over the {@code Intention} line of the skill ({@code SKILL}, completed with the native facts of
 * M19.3); without either the choice is untyped. Nothing is inferred from {@code PredictValues} (user decision 5): they
 * stay on the choice as history and only produce a warning when the choice ends untyped. The native facts never
 * change a declared purpose; they only feed the coherence warnings. Every typed choice gets the cooldown class read
 * from the native {@code Cooldown} plus the {@code CD}; a vanilla melee choice gets {@code SHORT} or {@code MEDIUM}.
 * Warnings about a skill are given once per binding, however many profiles share it.
 *
 * <p>The {@code ANSWER_} primitive of a line that types the choice becomes the answer of its tell, unless the profile
 * declares {@code Telegraph.Answer} for the choice (M23.2, DOC-05); a {@code Purpose} of the profile overrides the whole
 * line, its answer included.
 */
final class ChoicePurposes {
    private ChoicePurposes() {
    }

    /** The vanilla melee choice with its cooldown class. */
    static SkillChoice vanilla(SkillChoice choice) {
        return choice.withPurpose(choice.typedPurpose().withCooldown(
                SkillMetadata.vanillaCooldownClass(choice.extraCooldownTicks())));
    }

    /**
     * The skill choice with its labels, its native facts and its resolved purpose, if any.
     *
     * @param reading native facts of the skill ({@link NativeSkillReader.Reading#UNREAD} when it is not a metaskill)
     * @param noted keys of the skill warnings already given in this binding
     */
    static SkillChoice skill(TacticalProfile profile, SkillChoice choice, Skill skill,
                             NativeSkillReader.Reading reading, Set<String> noted, List<BindingProblem> problems) {
        var skillId = choice.mythicSkillId().orElseThrow();
        SkillMetadata.predictValuesProblem(skill).ifPresent(problem -> {
            if (noted.add(skillId + "#values")) {
                problems.add(BindingProblem.of(BindingProblem.Severity.WARNING, profile.id(),
                        "Choices." + choice.id() + ".MythicID", "skill '" + skillId + "' PredictValues: " + problem));
            }
        });
        var parse = IntentionVocabulary.parse(SkillMetadata.intention(skill));
        if (noted.add(skillId + "#read")) {
            IntentionWarnings.intention(skill, parse, problems);
            reading.notes().forEach(note -> problems.add(IntentionWarnings.skillWarning(skill, "Skills", note)));
        }
        Optional<SkillPurpose> purpose;
        if (choice.authored()) {
            purpose = choice.purpose();
            if (!(parse instanceof IntentionParse.Absent)) {
                IntentionWarnings.overridden(profile, choice, skillId, problems);
            }
        } else {
            purpose = fromIntention(choice, skill, parse, reading, profile, noted, problems);
        }
        if (reading.read() && purpose.isPresent()) {
            IntentionWarnings.nativeCoherence(profile, choice, skill, purpose.get(), reading.facts(), noted, problems);
        }
        var cooldown = SkillMetadata.cooldownClass(skill, choice.extraCooldownTicks());
        var resolved = purpose.map(found -> choice.withPurpose(found.withCooldown(cooldown)))
                .orElseGet(choice::withoutPurpose);
        return withLineAnswer(resolved, choice.authored() ? new IntentionParse.Absent() : parse)
                .withNativeFacts(reading.facts()).withValues(SkillMetadata.predictValuesOrEmpty(skill));
    }

    /**
     * The answer the line asks for, when the line typed the choice and the profile declares no answer for it. An
     * untyped choice and a declared {@code Purpose} keep their tell as it is.
     */
    private static SkillChoice withLineAnswer(SkillChoice choice, IntentionParse parse) {
        if (!choice.isTyped() || !(parse instanceof IntentionParse.Parsed parsed) || parsed.answer().isEmpty()
                || choice.telegraph().answer().isPresent()) {
            return choice;
        }
        var declared = choice.telegraph();
        return choice.withTelegraph(new ChoiceTelegraph(declared.danger(), parsed.answer(), declared.recoveryTicks()));
    }

    private static Optional<SkillPurpose> fromIntention(SkillChoice choice, Skill skill, IntentionParse parse,
                                                        NativeSkillReader.Reading reading, TacticalProfile profile,
                                                        Set<String> noted, List<BindingProblem> problems) {
        var skillId = choice.mythicSkillId().orElseThrow();
        if (parse instanceof IntentionParse.Parsed parsed) {
            var completion = SkillIntentions.complete(parsed, reading.facts(), profile.movement());
            if (completion.purpose().isPresent()) {
                return completion.purpose();
            }
            if (noted.add(skillId + "#incomplete")) {
                IntentionWarnings.unusable(skill, completion.problem().orElseThrow(), problems);
            }
        } else if (parse instanceof IntentionParse.Invalid invalid) {
            if (noted.add(skillId + "#invalid")) {
                IntentionWarnings.unusable(skill, IntentionWarnings.causes(invalid), problems);
            }
        } else if (noted.add(skillId + "#untyped")) {
            IntentionWarnings.untyped(skill, problems);
        }
        return Optional.empty();
    }
}
