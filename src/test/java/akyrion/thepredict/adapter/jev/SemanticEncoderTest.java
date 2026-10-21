package akyrion.thepredict.adapter.jev;

import akyrion.thepredict.domain.ArmorBand;
import akyrion.thepredict.domain.CombatFacts;
import akyrion.thepredict.domain.DistanceBand;
import akyrion.thepredict.domain.EncounterEvent;
import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.EventType;
import akyrion.thepredict.domain.HealthBand;
import akyrion.thepredict.domain.MobCue;
import akyrion.thepredict.domain.MobFacts;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.ParticipantAlias;
import akyrion.thepredict.domain.ParticipantFacts;
import akyrion.thepredict.domain.ParticipantKind;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.domain.TargetCue;
import akyrion.thepredict.domain.TargetRelation;
import akyrion.thepredict.domain.Trend;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import static akyrion.thepredict.adapter.jev.JevFixtures.ENCOUNTER;
import static akyrion.thepredict.adapter.jev.JevFixtures.MOB_A;
import static akyrion.thepredict.adapter.jev.JevFixtures.MOB_B;
import static akyrion.thepredict.adapter.jev.JevFixtures.PLAYER_1;
import static akyrion.thepredict.adapter.jev.JevFixtures.PLAYER_3;
import static akyrion.thepredict.adapter.jev.JevFixtures.guardian;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SemanticEncoderTest {
    private static final Pattern UUID_TEXT = Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-");
    private static final Pattern REFERENCE = Pattern.compile("(mob|player)_[0-9]+");
    private static final String[] GOLDEN = {"encounter-melee.json", "encounter-shield.json",
            "encounter-three-mobs.json", "encounter-truncated.json"};

    @Test
    void meleeStateMatchesItsGoldenByteForByte() {
        assertGolden("encounter-melee.json", encodeAll(new SemanticEncoder(12_000), JevFixtures.meleeFacts(), Map.of(MOB_A, guardian())));
    }

    @Test
    void shieldStateMatchesItsGoldenByteForByte() {
        assertGolden("encounter-shield.json", encodeAll(new SemanticEncoder(12_000), JevFixtures.shieldFacts(), Map.of(MOB_A, guardian())));
    }

    @Test
    void threeMobStateMatchesItsGoldenByteForByte() {
        assertGolden("encounter-three-mobs.json", encodeAll(new SemanticEncoder(12_000), JevFixtures.threeMobFacts(), Map.of(MOB_A, guardian(), MOB_B, guardian())));
    }

    @Test
    void truncatedStateMatchesItsGoldenByteForByte() {
        assertGolden("encounter-truncated.json", encodeAll(new SemanticEncoder(1000), JevFixtures.crowdedFacts(), Map.of(MOB_A, guardian())));
    }

    @Test
    void goldensCarryNoUuidNameOrPositionNumber() {
        for (var name : GOLDEN) {
            var text = SystemOneContractServer.fixture("golden/" + name);
            assertFalse(UUID_TEXT.matcher(text).find(), name);
            assertFalse(REFERENCE.matcher(text).replaceAll("ref").matches("(?s).*[0-9].*"), name);
            assertFalse(text.contains(MOB_A.entityId().toString()), name);
            assertFalse(text.contains("Guardian"), name);
        }
    }

    @Test
    void encodingIsDeterministicAndIgnoresSetIterationOrder() {
        var encoder = new SemanticEncoder(12_000);
        var first = encodeAll(encoder, JevFixtures.shieldFacts(), Map.of(MOB_A, guardian())).toString();
        assertEquals(first, encodeAll(encoder, JevFixtures.shieldFacts(), Map.of(MOB_A, guardian())).toString());
        var reordered = JevFixtures.shieldFacts();
        var player = reordered.participants().getFirst();
        var swapped = new CombatFacts(reordered.encounter(), reordered.captureTick(), reordered.captureNanos(),
                reordered.mobs(), List.of(new ParticipantFacts(player.alias(), player.health(),
                player.armor(), Set.of(TargetCue.HOLDS_TOTEM, TargetCue.SHIELD_DISABLED,
                TargetCue.RAISING_SHIELD), player.threatRank())), reordered.recentEvents(), reordered.combatSeconds());
        assertEquals(first, encodeAll(encoder, swapped, Map.of(MOB_A, guardian())).toString());
    }

    @Test
    void mobsAreReferencedInCaptureOrderAndEventsNewestFirst() {
        var state = encodeAll(new SemanticEncoder(12_000), JevFixtures.threeMobFacts(), Map.of(MOB_A, guardian()));
        var mobs = state.getAsJsonArray("mobs");
        assertEquals("mob_1", mobs.get(0).getAsJsonObject().get("ref").getAsString());
        assertEquals("mob_3", mobs.get(2).getAsJsonObject().get("ref").getAsString());
        assertFalse(mobs.get(1).getAsJsonObject().has("objective"), "only asked mobs carry the objective");
        assertEquals("player_3 threw an ender pearl", state.getAsJsonArray("recent_events").get(0).getAsString());
    }

    @Test
    void anEventAliasThatIsNotAParticipantIsWrittenWithItsAlias() {
        var facts = JevFixtures.threeMobFacts();
        assertTrue(facts.participants().stream().noneMatch(participant -> participant.alias().equals(PLAYER_3)));
        var state = encodeAll(new SemanticEncoder(12_000), facts, Map.of(MOB_A, guardian()));
        assertTrue(state.getAsJsonArray("recent_events").toString().contains("player_3 threw an ender pearl"));
    }

    @Test
    void aGroupedTargetIsSaidLastAmongItsFactsAndAnUngroupedOneNever() {
        var grouped = new MobFacts(MOB_A, HealthBand.HEALTHY, Optional.of(new TargetRelation(PLAYER_1,
                DistanceBand.MELEE, Trend.CLOSING, true, true, false, true, true)), Set.of(), Set.of(), Set.of(),
                Set.of());
        var facts = new CombatFacts(ENCOUNTER, 10, 0, List.of(grouped), List.of(JevFixtures.player(PLAYER_1, 1)),
                List.of(), 0);
        var target = encodeAll(new SemanticEncoder(12_000), facts, Map.of(MOB_A, guardian())).getAsJsonArray("mobs")
                .get(0).getAsJsonObject().getAsJsonObject("target").getAsJsonArray("facts");
        assertEquals("target can be flanked", target.get(target.size() - 2).getAsString());
        assertEquals("target stands close to other enemies", target.get(target.size() - 1).getAsString());
        assertFalse(encodeAll(new SemanticEncoder(12_000), JevFixtures.meleeFacts(), Map.of(MOB_A, guardian()))
                .toString().contains("stands close to other enemies"));
    }

    @Test
    void eventsWithoutParticipantOrWithAMobOutsideTheCaptureUseNeutralWords() {
        var outsider = new MobKey(UUID.fromString("00000000-0000-0000-0000-0000000000ff"), 9);
        var facts = new CombatFacts(ENCOUNTER, 10, 0, List.of(JevFixtures.meleeMob(MOB_A, PLAYER_1)),
                List.of(JevFixtures.player(PLAYER_1, 1)), List.of(
                new EncounterEvent(1, EventType.MELEE_HIT, Optional.empty(), Optional.of(outsider)),
                new EncounterEvent(2, EventType.MELEE_HIT, Optional.of(PLAYER_1), Optional.empty())), 0);
        var events = encodeAll(new SemanticEncoder(12_000), facts, Map.of(MOB_A, guardian()))
                .getAsJsonArray("recent_events");
        assertEquals("a mob hit player_1 in melee", events.get(0).getAsString());
        assertEquals("another mob hit a player in melee", events.get(1).getAsString());
        assertFalse(events.toString().contains(outsider.entityId().toString()));
    }

    @Test
    void aStateExactlyAtTheLimitIsKeptAndOneCharacterMoreTrimsTheOldestEvent() {
        var facts = JevFixtures.meleeFacts();
        var profiles = Map.of(MOB_A, guardian());
        var full = encodeAll(new SemanticEncoder(100_000), facts, profiles).toString();
        assertEquals(full, encodeAll(new SemanticEncoder(full.length()), facts, profiles).toString());
        var trimmed = encodeAll(new SemanticEncoder(full.length() - 1), facts, profiles);
        var events = trimmed.getAsJsonArray("recent_events");
        assertEquals(1, events.size());
        assertEquals("player_1 missed a projectile shot", events.get(0).getAsString());
        assertTrue(trimmed.toString().length() <= full.length() - 1);
    }

    @Test
    void trimmingFollowsIrrelevantThenRelevantEventsThenCuesThenLeastThreateningParticipantsThenUnaskedMobs() {
        var facts = JevFixtures.crowdedFacts();
        var profiles = Map.<MobKey, TacticalProfile>of(MOB_A, guardian());
        var full = encodeAll(new SemanticEncoder(100_000), facts, profiles).toString().length();
        var steps = new ArrayList<JsonObject>();
        String previous = null;
        for (var limit = full; limit > 0; limit--) {
            JsonObject state;
            try {
                state = encodeAll(new SemanticEncoder(limit), facts, profiles);
            } catch (SystemOneRequestException exception) {
                break;
            }
            if (!state.toString().equals(previous)) {
                steps.add(state);
                previous = state.toString();
            }
        }
        assertEquals(13, steps.size(), "6 events, 3 cue lists, 2 participants, 1 mob and the untrimmed state");
        var relevant = steps.get(3).getAsJsonArray("recent_events");
        assertEquals(3, relevant.size());
        relevant.forEach(event -> assertEquals("mob_1 hit player_1 in melee", event.getAsString()));
        assertEquals(0, steps.get(6).getAsJsonArray("recent_events").size());
        assertEquals("[]", player(steps.get(7), "player_3").get("cues").toString());
        assertEquals("[]", player(steps.get(8), "player_2").get("cues").toString());
        assertEquals("[]", player(steps.get(9), "player_1").get("cues").toString());
        assertEquals(2, steps.get(10).getAsJsonArray("participants").size());
        assertEquals(1, steps.get(11).getAsJsonArray("participants").size());
        var last = steps.getLast();
        assertEquals(1, last.getAsJsonArray("mobs").size());
        var mob = last.getAsJsonArray("mobs").get(0).getAsJsonObject();
        assertEquals("mob_1", mob.get("ref").getAsString());
        assertTrue(mob.getAsJsonObject("target").getAsJsonArray("facts").toString().contains("target is blocking"),
                "the cues of the target of an asked mob stay in its target facts");
    }

    @Test
    void onlyMobsWithAQuestionCarryTheirObjectiveAndProtectTheirTarget() {
        var state = new SemanticEncoder(12_000).encodeState(JevFixtures.threeMobFacts(),
                Map.of(MOB_A, guardian(), MOB_B, guardian()), Set.of(MOB_A));
        var mobs = state.getAsJsonArray("mobs");
        assertTrue(mobs.get(0).getAsJsonObject().has("objective"));
        assertFalse(mobs.get(1).getAsJsonObject().has("objective"), "a profile without a question leaks nothing");
        assertEquals(1, state.toString().split("\"objective\"", -1).length - 1);
    }

    @Test
    void aHiddenTargetIsSaidOnceNotTwice() {
        var state = encodeAll(new SemanticEncoder(12_000), JevFixtures.shieldFacts(), Map.of(MOB_A, guardian()));
        assertFalse(state.toString().contains("has no line of sight to its target"));
        assertTrue(state.toString().contains("\"no line of sight to target\""));
        var facts = new CombatFacts(ENCOUNTER, 1, 0, List.of(new MobFacts(MOB_A,
                HealthBand.HEALTHY, Optional.empty(), Set.of(), Set.of(),
                Set.of(MobCue.NO_LINE_OF_SIGHT), Set.of())), List.of(), List.of(), 0);
        assertTrue(encodeAll(new SemanticEncoder(12_000), facts, Map.of(MOB_A, guardian())).toString()
                .contains("mob_1 has no line of sight to its target"), "without a target the cue stays");
    }

    @Test
    void aStateThatCannotFitEvenAfterTrimmingIsRejectedWithoutQuotingIt() {
        var exception = assertThrows(SystemOneRequestException.class,
                () -> encodeAll(new SemanticEncoder(100), JevFixtures.meleeFacts(), Map.of(MOB_A, guardian())));
        assertEquals("state exceeds maxStateChars even after trimming", exception.getMessage());
    }

    @Test
    void durationPhrasesFollowCombatSecondsIncludingTheLastFightOutsideCombat() {
        assertEquals("the fight has just started", SemanticPhrases.duration(0));
        assertEquals("the fight has just started", SemanticPhrases.duration(9));
        assertEquals("the fight has lasted about half a minute", SemanticPhrases.duration(10));
        assertEquals("the fight has lasted about one minute", SemanticPhrases.duration(45));
        assertEquals("the fight has lasted about two minutes", SemanticPhrases.duration(90));
        assertEquals("the fight has lasted several minutes", SemanticPhrases.duration(180));
        assertNotEquals(SemanticPhrases.duration(0), SemanticPhrases.duration(Integer.MAX_VALUE));
    }

    private static JsonObject encodeAll(SemanticEncoder encoder, CombatFacts facts, Map<MobKey, TacticalProfile> profiles) {
        return encoder.encodeState(facts, profiles, profiles.keySet());
    }

    private static JsonObject player(JsonObject state, String ref) {
        for (var element : state.getAsJsonArray("participants")) {
            if (element.getAsJsonObject().get("ref").getAsString().equals(ref)) {
                return element.getAsJsonObject();
            }
        }
        throw new AssertionError(ref + " is not in the state");
    }

    private static void assertGolden(String name, JsonObject state) {
        var pretty = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(state) + "\n";
        JevFixtures.keepFresh("golden/" + name, pretty);
        assertEquals(SystemOneContractServer.fixture("golden/" + name), pretty);
    }

    @Test
    void theCuesOfTheTargetAreRepeatedInsideTheAskedMobWithTheSubjectTarget() {
        var state = new SemanticEncoder(12_000).encodeState(JevFixtures.shieldFacts(), Map.of(MOB_A, guardian()),
                Set.of(MOB_A));
        var target = state.getAsJsonArray("mobs").get(0).getAsJsonObject().getAsJsonObject("target")
                .getAsJsonArray("facts").toString();
        for (var cue : List.of("target is raising a shield but not blocking yet", "target's shield is disabled",
                "target holds a totem of undying")) {
            assertTrue(target.contains(cue), cue + " in " + target);
        }
        assertTrue(player(state, "player_1").get("cues").toString().contains("player_1 is raising a shield"));
        assertFalse(state.has("players"), "the array is called participants since M16.4");
    }

    @Test
    void theMobSaysWhetherItIsInBetterOrWorseShapeThanItsTargetOnlyWhenTheBandsDiffer() {
        var better = new SemanticEncoder(12_000).encodeState(JevFixtures.meleeFacts(), Map.of(MOB_A, guardian()),
                Set.of(MOB_A)).getAsJsonArray("mobs").get(0).getAsJsonObject().getAsJsonArray("facts").toString();
        assertTrue(better.contains("mob_1 is in better shape than its target"), better);
        var worse = new SemanticEncoder(12_000).encodeState(JevFixtures.shieldFacts(), Map.of(MOB_A, guardian()),
                Set.of(MOB_A)).getAsJsonArray("mobs").get(0).getAsJsonObject().getAsJsonArray("facts").toString();
        assertTrue(worse.contains("mob_1 is in better shape than its target"), "wounded mob, critical target: " + worse);
        var even = new CombatFacts(ENCOUNTER, 1, 0, List.of(JevFixtures.meleeMob(MOB_A, PLAYER_1)),
                List.of(new ParticipantFacts(PLAYER_1, HealthBand.HEALTHY, ArmorBand.LIGHT, Set.of(), 1)), List.of(), 1);
        var same = new SemanticEncoder(12_000).encodeState(even, Map.of(MOB_A, guardian()), Set.of(MOB_A)).toString();
        assertFalse(same.contains("shape than its target"), same);
        var critical = new CombatFacts(ENCOUNTER, 1, 0, List.of(new MobFacts(MOB_A, HealthBand.CRITICAL,
                JevFixtures.meleeMob(MOB_A, PLAYER_1).target(), Set.of(), Set.of(), Set.of(), Set.of())),
                List.of(new ParticipantFacts(PLAYER_1, HealthBand.HEALTHY, ArmorBand.LIGHT, Set.of(), 1)), List.of(), 1);
        assertTrue(new SemanticEncoder(12_000).encodeState(critical, Map.of(MOB_A, guardian()), Set.of(MOB_A))
                .toString().contains("mob_1 is in worse shape than its target"));
    }

    @Test
    void theLargestAdmittedStateStillFitsUnderTwelveThousandCharacters() {
        var request = ScenarioFacts.encounter(ScenarioFacts.example(), 16, 16, 7);
        var asked = new HashSet<MobKey>();
        request.questions().forEach(question -> asked.add(question.mob()));
        var untrimmed = new SemanticEncoder(1_000_000).encodeState(request.facts(), request.profiles(), asked).toString();
        assertTrue(untrimmed.length() < 12_000, untrimmed.length() + " characters");
    }

    /** A mob of the melee fixture whose target is a zombie, with a player nearby (M17.9). */
    private static CombatFacts entityFacts() {
        var zombie = new ParticipantAlias(ParticipantKind.ENTITY, 1);
        var mob = new MobFacts(MOB_A, HealthBand.HEALTHY, Optional.of(new TargetRelation(zombie, DistanceBand.MELEE,
                Trend.CLOSING, true, true, false, false, false)), Set.of(), Set.of(), Set.of(), Set.of());
        return new CombatFacts(new EncounterId(1), 1200, 9_000_000L, List.of(mob), List.of(
                new ParticipantFacts(zombie, HealthBand.WOUNDED, ArmorBand.LIGHT, Set.of(), 1,
                        Optional.of("wither_skeleton")),
                new ParticipantFacts(new ParticipantAlias(1), HealthBand.HEALTHY, ArmorBand.MEDIUM, Set.of(), 2)),
                List.of(new EncounterEvent(1100, EventType.ALLY_ASSISTED, Optional.of(zombie), Optional.empty())), 12);
    }

    @Test
    void anEntityIsIntroducedByItsSpeciesAndCountedAsAnotherCreature() {
        var state = encodeAll(new SemanticEncoder(12_000), entityFacts(), Map.of(MOB_A, guardian()));
        assertEquals("one player, one other creature and one mob are involved",
                state.getAsJsonObject("encounter").get("headcount").getAsString());
        var entity = player(state, "entity_1");
        assertEquals("entity_1 is a wither skeleton", entity.getAsJsonArray("facts").get(0).getAsString());
        assertEquals("player_1", player(state, "player_1").get("ref").getAsString());
        assertFalse(player(state, "player_1").getAsJsonArray("facts").get(0).getAsString().contains(" is a "),
                "a player has no species");
        var target = state.getAsJsonArray("mobs").get(0).getAsJsonObject().getAsJsonObject("target");
        assertTrue(target.getAsJsonArray("facts").toString().contains("target is a wither skeleton"));
        assertTrue(state.toString().contains("entity_1 helped an ally"));
    }

    @Test
    void theStateOfAnEncounterWithEntitiesCarriesNoUuidNameMythicIdOrFaction() {
        var text = encodeAll(new SemanticEncoder(12_000), entityFacts(), Map.of(MOB_A, guardian())).toString();
        assertFalse(UUID_TEXT.matcher(text).find());
        assertFalse(text.contains(MOB_A.entityId().toString()));
        for (var forbidden : List.of("minecraft:", "faction", "DuelBlue", "Display")) {
            assertFalse(text.contains(forbidden), forbidden);
        }
    }
}
