package akyrion.thepredict.adapter.server.binding;

import akyrion.thepredict.adapter.server.binding.BindingProblem.Severity;
import akyrion.thepredict.domain.CastMode;
import akyrion.thepredict.domain.CooldownClass;
import akyrion.thepredict.domain.CueChannels;
import akyrion.thepredict.domain.DangerRules;
import akyrion.thepredict.domain.Delivery;
import akyrion.thepredict.domain.HitShape;
import akyrion.thepredict.domain.OptionSet;
import akyrion.thepredict.domain.PurposeRules;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.SkillRisk;
import akyrion.thepredict.domain.StanceNativeMode;
import akyrion.thepredict.domain.Tactic;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.domain.TellAnswer;
import akyrion.thepredict.domain.ThreatWeights;
import io.lumine.mythic.api.mobs.MythicMob;
import io.lumine.mythic.api.skills.Skill;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Warnings of the binder: they never exclude a profile (M03.4, M15.3, M15.4, M16.1). */
final class BindingWarnings {
    private BindingWarnings() {
    }

    /**
     * Warns about each tactic whose movement keeps the target beyond the reach of its skill (M15.4). Only tactics whose
     * choice has a purpose written by the author are checked, so a profile without {@code Purpose} gains nothing.
     */
    static void tacticFit(TacticalProfile profile, List<BindingProblem> problems) {
        for (var tactic : profile.tactics().values()) {
            var choice = tactic.choice().map(profile.choices()::get).orElse(null);
            if (choice == null || !choice.authored()) {
                continue;
            }
            var fit = PurposeRules.fit(tactic.movement(), choice.typedPurpose(), profile.movement());
            if (fit != PurposeRules.PurposeFit.FITS) {
                problems.add(BindingProblem.of(Severity.WARNING, profile.id(), "Tactics." + tactic.id(),
                        fitMessage(tactic, choice, profile, fit)));
            }
        }
    }

    /**
     * Warns about each tactic identical in everything to one of higher priority (M16.1): Jev only sees the first, in
     * the order of the questions (descending priority, then ID).
     */
    static void indistinguishable(TacticalProfile profile, List<BindingProblem> problems) {
        var ordered = profile.tactics().values().stream()
                .sorted(Comparator.comparingInt((Tactic tactic) -> -tactic.priority())
                        .thenComparing(tactic -> tactic.id().value()))
                .map(Tactic::id).toList();
        for (var later = 1; later < ordered.size(); later++) {
            for (var earlier = 0; earlier < later; earlier++) {
                if (OptionSet.identical(profile, ordered.get(earlier), ordered.get(later))) {
                    var seen = ordered.get(earlier).value();
                    problems.add(BindingProblem.of(Severity.WARNING, profile.id(), "Tactics." + ordered.get(later),
                            "Tactics." + seen + " and Tactics." + ordered.get(later) + " describe the same option to"
                                    + " Jev; Jev only sees '" + seen + "'"));
                    break;
                }
            }
        }
    }

    /**
     * Warns about a skill without a static cooldown or also cast by the mob with a normal trigger. With
     * {@code CastMode.ANY_READY} a skill without a static {@code Cooldown} (absent, a placeholder or a random range) and
     * without {@code CD} fires whenever it can, because ThePredict adds no floor of its own (user decision 8 of M19).
     */
    static void skill(TacticalProfile profile, SkillChoice choice, Skill skill, MythicMob mob, Set<String> noted,
                      List<BindingProblem> problems) {
        var skillId = choice.mythicSkillId().orElseThrow();
        var anyReady = profile.timing().castMode() == CastMode.ANY_READY;
        var unpaced = anyReady ? !SkillMetadata.hasStaticCooldown(skill) : !SkillMetadata.hasNativeCooldown(skill);
        if (unpaced && choice.extraCooldownTicks() == 0 && noted.add(skillId + "#cooldown")) {
            problems.add(BindingProblem.of(Severity.WARNING, profile.id(), "Choices." + choice.id(), anyReady
                    ? "skill '" + skillId + "' has no static Cooldown; with CastMode ANY_READY it fires whenever it can"
                    : "skill '" + skillId + "' has no native Cooldown and the choice has no CD; it may be cast every"
                            + " decision"));
        }
        if (SkillMetadata.triggeredByMob(mob, skillId)) {
            problems.add(BindingProblem.of(Severity.WARNING, profile.id(), "Choices." + choice.id() + ".MythicID",
                    "mob '" + mob.getInternalName() + "' also casts '" + skillId + "' from its Skills with a trigger;"
                            + " it may add damage while ThePredict controls the mob"));
        }
    }

    /** Warns about author text that may not be English. */
    static void language(TacticalProfile profile, List<BindingProblem> problems) {
        EnglishTextHint.reason(profile.objective()).ifPresent(reason -> problems.add(BindingProblem.of(
                Severity.WARNING, profile.id(), "Objective", "may not be in English (" + reason + "); Jev reads English")));
        profile.tactics().values().forEach(tactic -> tactic.description().flatMap(EnglishTextHint::reason)
                .ifPresent(reason -> problems.add(BindingProblem.of(Severity.WARNING, profile.id(),
                        "Tactics." + tactic.id() + ".Description",
                        "may not be in English (" + reason + "); Jev reads English"))));
    }

    /**
     * Warns when a profile that writes {@code Group} also uses native targeting that can override the assignment.
     * The role-without-capacity case is the constructor error of {@code GroupSettings}, not a warning.
     *
     * @param profile the profile
     * @param skills mob skill lines
     * @param selectors {@code AITargetSelectors} lines
     * @param problems destination
     */
    static void group(TacticalProfile profile, List<String> skills, List<String> selectors,
                      List<BindingProblem> problems) {
        if (profile.group() == null || !profile.group().authored()) {
            return;
        }
        var override = false;
        var shared = false;
        var forced = "sudoskill";
        for (var line : skills == null ? List.<String>of() : skills) {
            if (line == null) {
                continue;
            }
            var lower = line.toLowerCase(Locale.ROOT);
            if (lower.contains("rally") || lower.contains("settarget") || lower.contains(forced)
                    || threatMode(lower)) {
                override = true;
            }
            if (lower.contains("inheritthreattable=true") || lower.contains("itt=true")) {
                shared = true;
            }
        }
        if (override) {
            problems.add(BindingProblem.of(Severity.WARNING, profile.id(), "Skills",
                    "native targeting may override the group assignment"));
        }
        if (shared) {
            problems.add(BindingProblem.of(Severity.WARNING, profile.id(), "Skills",
                    "shared threat table; ThePredict applies one change per table"));
        }
        var hurt = false;
        for (var line : selectors == null ? List.<String>of() : selectors) {
            if (line != null && line.toLowerCase(Locale.ROOT).contains("hurtbytarget")) {
                hurt = true;
                break;
            }
        }
        if (hurt) {
            problems.add(BindingProblem.of(Severity.WARNING, profile.id(), "AITargetSelectors",
                    "MythicMobs hurtByTarget does not alert allies; group perception is shared by ThePredict for"
                            + " controlled members"));
        }
    }

    private static boolean threatMode(String lower) {
        return lower.contains("threat")
                && (lower.contains("mode=set") || lower.contains("mode=taunt") || lower.contains("mode=forcetop"));
    }

    /** Ignores the unreachable decay when MythicMobs already decays unreachable targets (M11.1). */
    static ThreatWeights threat(TacticalProfile profile, MythicMob mob, List<BindingProblem> problems) {
        var threat = profile.threat();
        if (threat.unreachableDecayPerSecond() == 0.0 || !mob.getConfig().getBoolean("ThreatTable.DecayUnreachable", false)) {
            return threat;
        }
        problems.add(BindingProblem.of(Severity.WARNING, profile.id(), "Threat.UnreachableDecayPerSecond",
                "is ignored because mob '" + mob.getInternalName() + "' enables ThreatTable.DecayUnreachable"));
        return new ThreatWeights(threat.assistsOthers(), threat.rangedHarass(), threat.blockedHits(), 0.0,
                threat.maxPerWindow());
    }

    /**
     * Warns when a tell of class {@code MEDIUM} or harder cannot be seen or heard, shares a cue, or cannot be dodged.
     * Texts are the plan's literals (M23.3). {@code SELF} with damage is reported as an area.
     *
     * @param profile the profile
     * @param mobName MythicMobs mob id
     * @param mobSetsStance whether a skill of the mob calls {@code setStance}
     * @param channelsBySkill effective cue bits per skill id
     * @param signatureBySkill cue signature per skill id
     * @param problems destination
     */
    static void telegraphCoherence(TacticalProfile profile, String mobName, boolean mobSetsStance,
                                   java.util.Map<String, Integer> channelsBySkill,
                                   java.util.Map<String, Integer> signatureBySkill, List<BindingProblem> problems) {
        if (profile.stances().nativeMode() == StanceNativeMode.WRITE && mobSetsStance) {
            problems.add(BindingProblem.of(Severity.WARNING, profile.id(), "Stances.Native",
                    "mob '" + mobName + "' sets its own stance; Stances.Native write would overwrite it every change"));
        }
        var seen = new java.util.LinkedHashMap<Integer, SkillChoice>();
        for (var choice : profile.choices().values()) {
            if (!choice.authored()) {
                continue;
            }
            var danger = DangerRules.classify(choice);
            if (!danger.enforced() || !profile.telegraph().againstPlayers()) {
                continue;
            }
            var skill = choice.mythicSkillId().orElse(choice.id().value());
            var answer = DangerRules.answer(choice);
            var bits = channelsBySkill.getOrDefault(skill, 0);
            if (!CueChannels.visual(bits) || !CueChannels.audio(bits)) {
                problems.add(BindingProblem.of(Severity.WARNING, profile.id(), "Choices." + choice.id(),
                        "skill '" + skill + "' telegraph has no visual (or audio) cue; ThePredict adds none: add a"
                                + " ~onSignal:PREDICTOR_TELL_" + answer.name() + " handler"));
            }
            var signature = signatureBySkill.getOrDefault(skill, 0);
            if (signature != 0) {
                var other = seen.putIfAbsent(signature, choice);
                if (other != null && DangerRules.answer(other) != answer) {
                    problems.add(BindingProblem.of(Severity.WARNING, profile.id(), "Choices." + choice.id(),
                            "skills '" + other.mythicSkillId().orElse(other.id().value()) + "' and '" + skill
                                    + "' ask for different answers (" + DangerRules.answer(other).name() + ", "
                                    + answer.name() + ") but start with the same effects"));
                }
            }
            var facts = choice.nativeFacts();
            if ((answer == TellAnswer.DODGE || answer == TellAnswer.BACK_OFF)
                    && facts.hitShape() == HitShape.SINGLE_TARGET && facts.windUpOrZero() > 0
                    && choice.busyTicks() == facts.windUpOrZero()) {
                problems.add(BindingProblem.of(Severity.WARNING, profile.id(), "Choices." + choice.id(),
                        "skill '" + skill + "' hits @target after a delay: moving away cannot avoid it; use a cone,"
                                + " radius or projectile targeter"));
            }
            var purpose = choice.purpose().orElse(null);
            if (purpose != null && purpose.risks().contains(SkillRisk.LEAVES_CASTER_EXPOSED)
                    && facts.recoveryTicks().isEmpty() && choice.telegraph().recoveryTicks().isEmpty()) {
                problems.add(BindingProblem.of(Severity.WARNING, profile.id(), "Choices." + choice.id(),
                        "skill '" + skill + "' leaves the caster exposed; ThePredict uses a "
                                + profile.telegraph().defaultRecoveryTicks() + "-tick recovery"));
            }
            if (purpose != null && purpose.delivery() == Delivery.SELF && purpose.damaging()) {
                problems.add(BindingProblem.of(Severity.WARNING, profile.id(), "Choices." + choice.id(),
                        "skill '" + skill + "' deals damage on SELF; ThePredict treats it as an area around the caster"));
            }
        }
    }

    private static String fitMessage(Tactic tactic, SkillChoice choice, TacticalProfile profile,
                                     PurposeRules.PurposeFit fit) {
        var movement = profile.movement();
        var reach = number(choice.typedPurpose().effectiveReachBlocks().orElseThrow());
        var skill = "skill '" + choice.id() + "' reaches " + reach;
        var limit = switch (tactic.movement()) {
            case KEEP_DISTANCE -> fit == PurposeRules.PurposeFit.NEVER_CASTS
                    ? "keeps the target beyond Band.Inner " + number(movement.bandInnerBlocks())
                    : "keeps the target up to Band.Outer " + number(movement.bandOuterBlocks());
            case APPROACH, FLANK -> "stops at MeleeReach " + number(movement.meleeReachBlocks());
            case RETREAT, HOLD, NONE -> "does not close in to RangedReach " + number(movement.rangedReachBlocks());
        };
        var outcome = fit == PurposeRules.PurposeFit.NEVER_CASTS ? "this tactic never casts"
                : "this tactic casts only if the target comes within reach";
        return "Movement " + tactic.movement() + " " + limit + " but " + skill + "; " + outcome;
    }

    private static String number(double blocks) {
        return blocks == Math.rint(blocks) ? Long.toString((long) blocks) : Double.toString(blocks);
    }
}
