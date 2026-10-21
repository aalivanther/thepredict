package akyrion.thepredict.adapter.jev;

import akyrion.thepredict.adapter.jev.CriterionRenderer.RenderedCriterion;
import akyrion.thepredict.adapter.jev.OptionTextDedup.Discriminator;
import akyrion.thepredict.domain.ArmorBand;
import akyrion.thepredict.domain.ChoiceId;
import akyrion.thepredict.domain.ChoiceKind;
import akyrion.thepredict.domain.CombatFacts;
import akyrion.thepredict.domain.CooldownClass;
import akyrion.thepredict.domain.DamageTier;
import akyrion.thepredict.domain.DecisionRequest;
import akyrion.thepredict.domain.Delivery;
import akyrion.thepredict.domain.DistanceBand;
import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.HealthBand;
import akyrion.thepredict.domain.MobFacts;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.MovementSettings;
import akyrion.thepredict.domain.ParticipantAlias;
import akyrion.thepredict.domain.ParticipantFacts;
import akyrion.thepredict.domain.PredictValue;
import akyrion.thepredict.domain.PurposeOrigin;
import akyrion.thepredict.domain.QuestionDetail;
import akyrion.thepredict.domain.QuestionKey;
import akyrion.thepredict.domain.QuestionSpec;
import akyrion.thepredict.domain.Situation;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.SkillPurpose;
import akyrion.thepredict.domain.SkillRisk;
import akyrion.thepredict.domain.SkillRole;
import akyrion.thepredict.domain.Tactic;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.domain.TargetCue;
import akyrion.thepredict.domain.TargetRelation;
import akyrion.thepredict.domain.Trend;
import akyrion.thepredict.domain.UseCondition;
import akyrion.thepredict.engine.Preparation;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The deduplication by final text of M16.1: bounded passes, discriminators in order and never lost, merges that keep
 * the current tactic, and the one text invariant: no question ever carries two equal criteria, at any detail, over the
 * bank, the example and generated worst-case profiles.
 */
class OptionTextDedupTest {
    private static final TacticId A = new TacticId("alpha");
    private static final TacticId B = new TacticId("bravo");
    private static final TacticId C = new TacticId("charlie");
    private static final MobKey MOB = new MobKey(new UUID(0xDEDL, 1), 0);
    private static final EncounterId ENCOUNTER = new EncounterId(1);

    private static RenderedCriterion text(String value) {
        return new RenderedCriterion(value);
    }

    @Test
    void distinctTextsAreSentAsTheyAreInOnePass() {
        var renders = new AtomicInteger();
        var result = OptionTextDedup.apply(List.of(A, B), Optional.empty(), (option, discriminators) -> {
            renders.incrementAndGet();
            return text(option.value());
        });
        assertEquals(List.of(A, B), result.sent());
        assertEquals(Map.of(), result.mergedInto());
        assertEquals(2, renders.get());
    }

    @Test
    void eachCollidingOptionGetsTheNextDiscriminatorThatAppliesAndSkipsTheOthersWithoutSpendingAPass() {
        var seen = new HashMap<TacticId, List<Set<Discriminator>>>();
        var result = OptionTextDedup.apply(List.of(A, B), Optional.empty(), (option, discriminators) -> {
            seen.computeIfAbsent(option, key -> new ArrayList<>()).add(Set.copyOf(discriminators));
            var suffix = new StringBuilder();
            if (discriminators.contains(Discriminator.NOTE_EXCERPT) && option.equals(A)) {
                suffix.append("; note: a");
            }
            if (discriminators.contains(Discriminator.REACH_BAND)) {
                suffix.append(option.equals(A) ? "; reach: melee" : "; reach: ranged");
            }
            return text("close in" + suffix);
        });
        assertEquals(Map.of(A, "close in; note: a", B, "close in; reach: ranged"), texts(result));
        assertEquals(List.of(Set.of(), Set.of(Discriminator.NOTE_EXCERPT)), seen.get(A));
        assertEquals(List.of(Set.of(), Set.of(Discriminator.NOTE_EXCERPT), Set.of(Discriminator.REACH_BAND)),
                seen.get(B), "B has no note, so it gets REACH_BAND in the same pass");
    }

    @Test
    void optionsThatStayIdenticalAreMergedIntoTheFirstOrIntoTheTacticToKeep() {
        var result = OptionTextDedup.apply(List.of(A, B, C), Optional.empty(), (option, discriminators) ->
                text(option.equals(C) ? "retreat" : "close in"));
        assertEquals(List.of(A, C), result.sent());
        assertEquals(Map.of(B, A), result.mergedInto());
        var kept = OptionTextDedup.apply(List.of(A, B, C), Optional.of(B), (option, discriminators) ->
                text(option.equals(C) ? "retreat" : "close in"));
        assertEquals(List.of(B, C), kept.sent());
        assertEquals(Map.of(A, B), kept.mergedInto());
    }

    @Test
    void anOptionIsRenderedAtMostFourTimes() {
        var renders = new HashMap<TacticId, Integer>();
        OptionTextDedup.apply(List.of(A, B, C), Optional.empty(), (option, discriminators) -> {
            renders.merge(option, 1, Integer::sum);
            return text("same " + discriminators.size());
        });
        renders.values().forEach(count -> assertTrue(count <= OptionTextDedup.MAX_PASSES, renders.toString()));
        assertEquals(4, OptionTextDedup.MAX_PASSES);
    }

    @Test
    void theExampleNeverSendsTwoEqualCriteriaAtAnyDetail() {
        var example = ScenarioFacts.example();
        for (var detail : QuestionDetail.values()) {
            var request = ScenarioFacts.production(JevFixtures.detailed(example, detail));
            assertNoEqualCriteria(request, detail);
        }
    }

    @Test
    void theBankNeverSendsTwoEqualCriteriaAtAnyDetail() {
        for (var scenario : ScenarioBank.scenarios()) {
            for (var detail : QuestionDetail.values()) {
                assertNoEqualCriteria(ScenarioBank.request(scenario, detail, false), detail);
            }
        }
    }

    @Test
    void worstCaseProfilesNeverSendTwoEqualCriteriaAtAnyDetailAndKeepTheCaps() {
        for (var profile : worstCases()) {
            for (var detail : QuestionDetail.values()) {
                var detailed = JevFixtures.detailed(profile, detail);
                var options = List.copyOf(detailed.tactics().keySet()).subList(0, Math.min(255,
                        detailed.tactics().size()));
                assertNoEqualCriteria(request(detailed, options), detail);
            }
        }
    }

    @Test
    void theCurrentTacticSurvivesAMergeOfTwoTacticsThatOnlyDifferInTheirCooldown() {
        var profile = worstCases().get(1);
        var twins = profile.tactics().keySet().stream().filter(id -> id.value().startsWith("cooldown_")).toList();
        assertTrue(twins.size() >= 2);
        var keep = twins.get(1);
        var spec = new QuestionSpec(QuestionKey.of(0), MOB, Situation.NOW, twins, Optional.of(keep));
        var prepared = new RequestEncoder("jev-1.13.0", new SemanticEncoder(12_000)).prepare(new DecisionRequest(
                ENCOUNTER, 1, facts(), List.of(spec, new QuestionSpec(QuestionKey.of(1), MOB, Situation.NOW,
                List.of(new TacticId("hold_ground"), new TacticId("press_attack")))),
                Map.of(MOB, JevFixtures.detailed(profile, QuestionDetail.COMPACT))));
        var ready = assertInstanceOf(Preparation.Ready.class, prepared).prepared();
        assertEquals(Set.of(QuestionKey.of(0)), ready.droppedQuestions(), "one distinct text left: dropped");
        var standard = assertInstanceOf(Preparation.Ready.class, new RequestEncoder("jev-1.13.0",
                new SemanticEncoder(12_000)).prepare(new DecisionRequest(ENCOUNTER, 1, facts(), List.of(spec),
                Map.of(MOB, JevFixtures.detailed(profile, QuestionDetail.STANDARD))))).prepared();
        var sent = standard.sent().questions().getFirst().options();
        assertEquals(4, sent.size(), "the cooldown tells them apart, except NONE and SHORT, which read the same");
        assertFalse(sent.contains(new TacticId("cooldown_short")), sent.toString());
        var compactKeep = OptionTextDedup.apply(twins, Optional.of(keep), (option, discriminators) -> text("same"));
        assertEquals(List.of(keep), compactKeep.sent());
    }

    private static void assertNoEqualCriteria(DecisionRequest request, QuestionDetail detail) {
        var preparation = new RequestEncoder("jev-1.13.0", new SemanticEncoder(12_000)).prepare(request);
        if (!(preparation instanceof Preparation.Ready ready)) {
            assertInstanceOf(Preparation.NothingToAsk.class, preparation, detail + " " + preparation);
            return;
        }
        var body = JsonParser.parseString(ready.prepared().body()).getAsJsonObject();
        for (var entry : body.getAsJsonObject("questions").entrySet()) {
            var question = entry.getValue().getAsJsonObject();
            if (!question.get("type").getAsString().equals("choice")) {
                continue;
            }
            var seen = new HashSet<String>();
            for (var criterion : question.getAsJsonObject("criteria").entrySet()) {
                var value = criterion.getValue();
                var rendered = value.isJsonPrimitive() ? value.getAsString() : value.toString();
                assertTrue(seen.add(rendered), detail + " repeats a criterion in " + entry.getKey() + ": " + rendered);
                if (detail == QuestionDetail.COMPACT) {
                    var collided = rendered.contains("; note: ") || rendered.contains("; reach: ")
                            || rendered.contains("; best when ");
                    assertTrue(rendered.length() <= (collided ? CriterionRenderer.COLLISION_CHARS
                            : CriterionRenderer.COMPACT_CHARS), rendered.length() + ": " + rendered);
                    assertTrue(rendered.split("poor fit now:", -1).length <= 2, rendered);
                }
            }
        }
    }

    private static CombatFacts facts() {
        var mob = new MobFacts(MOB, HealthBand.CRITICAL, Optional.of(new TargetRelation(new ParticipantAlias(1),
                DistanceBand.RANGED, Trend.BACKING_AWAY, false, false, false, true, true)), Set.of(), Set.of(),
                Set.of(), Set.of());
        return new CombatFacts(ENCOUNTER, 1, 0, List.of(mob), List.of(new ParticipantFacts(new ParticipantAlias(1),
                HealthBand.CRITICAL, ArmorBand.HEAVY, Set.of(TargetCue.BLOCKING), 1)), List.of(), 10);
    }

    private static DecisionRequest request(TacticalProfile profile, List<TacticId> options) {
        return new DecisionRequest(ENCOUNTER, 1, facts(), List.of(new QuestionSpec(QuestionKey.of(0), MOB,
                Situation.NOW, options), new QuestionSpec(QuestionKey.of(1), MOB, Situation.IF_TARGET_IN_MELEE,
                options)), Map.of(MOB, profile));
    }

    /**
     * Worst cases of M16.1: three long roles, {@code CONE} and {@code FLANK}, every phrase present, deliveries without
     * reach, purposes written by the author and inferred, every cooldown class, descriptions equal in their first 40
     * characters, and tactics that differ only in their cooldown or their wind-up.
     */
    private static List<TacticalProfile> worstCases() {
        var tactics = new LinkedHashMap<TacticId, Tactic>();
        var choices = new LinkedHashMap<ChoiceId, SkillChoice>();
        var shared = "Hold the altar room and keep the doorway";
        var index = 0;
        for (var delivery : Delivery.values()) {
            for (var movement : Movement.values()) {
                var purpose = purpose(delivery, SkillRole.ESCAPE, EnumSet.of(SkillRole.ESCAPE, SkillRole.ZONE,
                        SkillRole.INTERRUPT), CooldownClass.MEDIUM, 20, PurposeOrigin.DECLARED);
                var choice = choice("choice_" + index, purpose);
                choices.put(choice.id(), choice);
                var description = index % 3 == 0 ? Optional.<String>empty()
                        : Optional.of(shared + (index % 2 == 0 ? " clear of enemies" : " clear of their allies"));
                add(tactics, "worst_" + index, movement, choice.id(), description);
                index++;
            }
        }
        for (var values : List.of(Set.of(PredictValue.MELEE_ATTACK), Set.of(PredictValue.RANGED_ATTACK,
                PredictValue.HIGH_DAMAGE), Set.of(PredictValue.HEALS_CASTER), Set.of(PredictValue.AREA_TARGET,
                PredictValue.APPLIES_POISON))) {
            var choice = choice("inferred_" + index, purpose(Delivery.MELEE, SkillRole.DAMAGE,
                    EnumSet.of(SkillRole.DAMAGE), CooldownClass.SHORT, index % 50, PurposeOrigin.SKILL));
            choices.put(choice.id(), choice);
            add(tactics, "inferred_" + index, Movement.APPROACH, choice.id(), Optional.empty());
            index++;
        }
        var first = worst(tactics, choices);
        var twins = new LinkedHashMap<TacticId, Tactic>();
        var twinChoices = new LinkedHashMap<ChoiceId, SkillChoice>();
        for (var cooldown : CooldownClass.values()) {
            var choice = choice("cd_" + cooldown.name().toLowerCase(Locale.ROOT), purpose(Delivery.MELEE,
                    SkillRole.DAMAGE, EnumSet.of(SkillRole.DAMAGE), cooldown, 5, PurposeOrigin.DECLARED));
            twinChoices.put(choice.id(), choice);
            add(twins, "cooldown_" + cooldown.name().toLowerCase(Locale.ROOT), Movement.APPROACH, choice.id(),
                    Optional.empty());
        }
        for (var windUp : new int[] {0, 5, 20}) {
            var choice = choice("wind_" + windUp, purpose(Delivery.CONE, SkillRole.CONTROL, EnumSet.of(SkillRole.CONTROL),
                    CooldownClass.SHORT, windUp, PurposeOrigin.DECLARED));
            twinChoices.put(choice.id(), choice);
            add(twins, "wind_up_" + windUp, Movement.FLANK, choice.id(), Optional.empty());
        }
        return List.of(first, worst(twins, twinChoices));
    }

    private static TacticalProfile worst(Map<TacticId, Tactic> extra, Map<ChoiceId, SkillChoice> extraChoices) {
        var base = JevFixtures.guardian();
        var tactics = new LinkedHashMap<>(base.tactics());
        tactics.putAll(extra);
        var choices = new LinkedHashMap<>(base.choices());
        choices.putAll(extraChoices);
        return new TacticalProfile(base.id(), base.mythicMobId(), base.objective(), tactics, choices, base.contexts(),
                base.movement(), base.health(), base.threat(), base.baseAi(), base.timing());
    }

    private static void add(Map<TacticId, Tactic> tactics, String id, Movement movement, ChoiceId choice,
                            Optional<String> description) {
        var tactic = new TacticId(id);
        tactics.put(tactic, new Tactic(tactic, movement, Optional.of(choice), description, 50));
    }

    private static SkillChoice choice(String id, SkillPurpose purpose) {
        return new SkillChoice(new ChoiceId(id), ChoiceKind.MYTHIC_SKILL, Optional.of("Skill_" + id), Set.of(), true, 0,
                purpose);
    }

    private static SkillPurpose purpose(Delivery delivery, SkillRole primary, Set<SkillRole> roles,
                                        CooldownClass cooldown, int windUp, PurposeOrigin origin) {
        var reach = delivery == Delivery.MELEE || delivery == Delivery.CONE ? OptionalDouble.of(2.5)
                : delivery == Delivery.AREA_AT_TARGET ? OptionalDouble.of(9) : OptionalDouble.empty();
        var radius = delivery == Delivery.AREA_AROUND_SELF || delivery == Delivery.AREA_AT_TARGET
                ? OptionalDouble.of(6) : OptionalDouble.empty();
        var damage = roles.contains(SkillRole.DAMAGE) ? DamageTier.HIGH : DamageTier.MEDIUM;
        return new SkillPurpose(primary, roles, delivery, reach, radius, windUp, cooldown, damage,
                EnumSet.of(UseCondition.TARGET_SHIELDING, UseCondition.ENEMIES_GROUPED, UseCondition.TARGET_AT_RANGE,
                        UseCondition.TARGET_LOW_HEALTH), EnumSet.of(SkillRisk.LEAVES_CASTER_EXPOSED,
                SkillRisk.NEEDS_LINE_OF_SIGHT, SkillRisk.SLOW_PROJECTILE, SkillRisk.HITS_ALLIES), false, origin);
    }

    private static Map<TacticId, String> texts(OptionTextDedup.Result result) {
        var texts = new HashMap<TacticId, String>();
        result.texts().forEach((option, text) -> texts.put(option, text.text()));
        return texts;
    }
}
