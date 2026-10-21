package akyrion.thepredict.adapter.jev;

import akyrion.thepredict.adapter.jev.BankScenario.Family;
import akyrion.thepredict.domain.QuestionDetail;
import akyrion.thepredict.domain.Situation;
import akyrion.thepredict.domain.TacticId;
import com.google.gson.JsonObject;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The labelled bank of M16.9: sixty scenarios in twelve families, the named sets, documents equal to what the bank
 * generates, only aliases in the bodies, and the rules of the labels. A mismatch writes the fresh document to
 * {@code build/scenario-bank/} so it can be reviewed and copied.
 */
class ScenarioBankTest {
    private static final Pattern UUID = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    @Test
    void theBankHasFortyEightNowAndTwelveContingencyScenariosInTwelveFamilies() {
        var scenarios = ScenarioBank.scenarios();
        assertEquals(60, scenarios.size());
        assertEquals(48, scenarios.stream().filter(scenario -> scenario.situation() == Situation.NOW).count());
        assertEquals(12, scenarios.stream().filter(scenario -> scenario.situation() != Situation.NOW).count());
        assertEquals(Set.of(Family.values()), scenarios.stream().map(BankScenario::family).collect(Collectors.toSet()));
        assertEquals(60, scenarios.stream().map(BankScenario::id).distinct().count());
        for (var scenario : scenarios) {
            assertTrue(scenario.id().matches("bank-" + scenario.family().key() + "-0[1-6]"), scenario.id());
        }
    }

    @Test
    void theNamedSetsHoldExactlyTheFirstAndSecondOfEachFamily() {
        assertEquals(List.of("bank-melee-01", "bank-shooter-01", "bank-shield-01", "bank-lowhp-01", "bank-pillar-01",
                "bank-grouped-01", "bank-finisher-01", "bank-drinking-01", "bank-nolos-01", "bank-fleeing-01",
                "bank-cont-melee-01", "bank-cont-critical-01"), ScenarioBank.set("core12"));
        var core24 = ScenarioBank.set("core24");
        assertEquals(24, core24.size());
        assertTrue(core24.containsAll(ScenarioBank.set("core12")));
        assertTrue(core24.stream().allMatch(id -> id.endsWith("-01") || id.endsWith("-02")));
        assertEquals(60, ScenarioBank.set("all").size());
        assertEquals(List.of("bank-melee-03", "bank-pillar-02"), ScenarioBank.set("bank-melee-03, bank-pillar-02"));
        assertThrows(IllegalArgumentException.class, () -> ScenarioBank.set("bank-unknown-01"));
    }

    @Test
    void everyDocumentIsWhatTheBankGeneratesWithAliasesOnly() throws IOException {
        var mismatches = new ArrayList<String>();
        for (var scenario : ScenarioBank.scenarios()) {
            var expected = ScenarioBank.document(scenario);
            var file = ScenarioBank.FOLDER.resolve(ScenarioBank.fileName(scenario));
            if (!Files.exists(file) || !Files.readString(file).equals(expected)) {
                var fresh = Path.of("build", "scenario-bank", ScenarioBank.fileName(scenario));
                Files.createDirectories(fresh.getParent());
                Files.writeString(fresh, expected);
                mismatches.add(scenario.id());
            }
            assertFalse(UUID.matcher(expected).find(), "no UUID in " + scenario.id());
        }
        assertEquals(List.of(), mismatches);
        try (Stream<Path> files = Files.list(ScenarioBank.FOLDER)) {
            assertEquals(60, files.filter(file -> file.toString().endsWith(".json")).count());
        }
    }

    @Test
    void everyLabelFollowsTheRulesOfTheBank() throws IOException {
        for (var scenario : ScenarioBank.scenarios()) {
            var document = ScenarioDocument.read(scenario.id(),
                    Files.readString(ScenarioBank.FOLDER.resolve(ScenarioBank.fileName(scenario))));
            var options = Set.copyOf(document.questions().getFirst().options());
            var label = document.labels().values().iterator().next();
            var what = scenario.id();
            assertTrue(label.acceptable().containsAll(label.best()), what + ": best inside acceptable");
            assertTrue(options.containsAll(label.acceptable()), what + ": acceptable inside the options");
            assertTrue(options.containsAll(label.forbidden()), what + ": forbidden inside the options");
            var overlap = new HashSet<>(label.acceptable());
            overlap.retainAll(label.forbidden());
            assertTrue(overlap.isEmpty(), what + ": nothing both acceptable and forbidden");
            assertTrue(LabelReviewApply.STATUSES.contains(label.status()), what);
            assertEquals(label.acceptable().containsAll(options), label.uninformative(), what + ": uninformative mark");
            assertTrue(label.uninformative() || !label.forbidden().isEmpty(), what + ": at least one forbidden option");
            var raw = raw(scenario);
            if (!label.status().equals("PROPOSED")) {
                assertFalse(raw.get("reviewedBy").getAsString().isBlank(), what + ": reviewedBy");
                LocalDate.parse(raw.get("reviewedOn").getAsString());
            }
        }
    }

    @Test
    void theUninformativeScenariosAreExactlyTheContingenciesWithTwoAcceptableOptions() {
        var uninformative = ScenarioBank.scenarios().stream().filter(scenario -> ScenarioBank.uninformative(
                scenario.acceptable(), ScenarioBank.options(scenario))).map(BankScenario::id).toList();
        assertEquals(List.of("bank-cont-critical-04", "bank-cont-critical-05", "bank-cont-critical-06"), uninformative);
        uninformative.forEach(id -> assertEquals(2, ScenarioBank.options(ScenarioBank.scenario(id)).size()));
        assertTrue(ScenarioBank.set("core24").stream().noneMatch(uninformative::contains));
    }

    @Test
    void theLowHealthFamilyReplacesTheSeedWhoseTwoOptionsWereBothAcceptable() {
        var lowHealth = ScenarioBank.scenarios().stream().filter(scenario -> scenario.family() == Family.LOWHP).toList();
        assertEquals(6, lowHealth.size());
        for (var scenario : lowHealth) {
            assertTrue(ScenarioBank.options(scenario).size() >= 6, scenario.id());
            assertFalse(scenario.forbidden().isEmpty(), scenario.id());
        }
    }

    @Test
    void theCriticalContingencyWithTheContextOffersOnlyItsTwoTacticsAndTheMeleeOneTheSlam() {
        assertEquals(List.of(new TacticId("retreat_and_defend"), new TacticId("keep_distance_and_shoot")),
                ScenarioBank.options(ScenarioBank.scenario("bank-cont-critical-04")));
        assertTrue(ScenarioBank.options(ScenarioBank.scenario("bank-cont-melee-01"))
                .contains(new TacticId("close_in_and_slam")));
    }

    @Test
    void theReviewFileListsEveryScenarioOnceWithAKnownStatus() throws Exception {
        var yaml = new YamlConfiguration();
        yaml.load(ScenarioBank.REVIEW.toFile());
        var entries = yaml.getConfigurationSection("scenarios");
        assertEquals(ScenarioBank.scenarios().stream().map(BankScenario::id).collect(Collectors.toSet()),
                entries.getKeys(false));
        for (var id : entries.getKeys(false)) {
            assertTrue(LabelReviewApply.STATUSES.contains(entries.getString(id + ".status")), id);
        }
    }

    @Test
    void theReviewIsAppliedWithTheReviewerAndTheDateAndRejectsAnInvalidEntry(@TempDir Path folder) throws Exception {
        var review = folder.resolve("label-review.yml");
        Files.writeString(review, """
                reviewer: Adrian
                scenarios:
                  bank-melee-01:
                    status: REVIEWED
                  bank-shield-01:
                    status: REVIEWED
                    acceptable: [break_guard, hold]
                    note: holding is fine while the shield is up
                  bank-pillar-01:
                    status: PROPOSED
                """);
        var changed = LabelReviewApply.apply(folder, review, LocalDate.of(2026, 9, 30));
        assertEquals(List.of("bank-melee-01", "bank-shield-01", "bank-pillar-01"), changed);
        var shield = ScenarioDocument.read("shield", Files.readString(folder.resolve("bank-shield-01.json")));
        var label = shield.labels().values().iterator().next();
        assertTrue(label.reviewed());
        assertEquals(Set.of(new TacticId("break_guard"), new TacticId("hold")), label.acceptable());
        var raw = StrictJson.parseObject(Files.readString(folder.resolve("bank-shield-01.json")), "shield")
                .getAsJsonObject("labels").getAsJsonObject("q0");
        assertEquals("Adrian", raw.get("reviewedBy").getAsString());
        assertEquals("2026-09-30", raw.get("reviewedOn").getAsString());
        assertEquals("holding is fine while the shield is up", raw.get("note").getAsString());
        assertEquals(List.of(), LabelReviewApply.apply(folder, review, LocalDate.of(2026, 10, 1)),
                "a label that did not change keeps its review date");
        Files.writeString(review, "reviewer: Adrian\nscenarios:\n  bank-shield-01:\n    status: REVIEWED\n"
                + "    forbidden: [break_guard]\n");
        assertThrows(IllegalArgumentException.class, () -> LabelReviewApply.apply(folder, review, LocalDate.now()));
        Files.writeString(review, "reviewer: ''\nscenarios:\n  bank-shield-01:\n    status: REVIEWED\n");
        assertThrows(IllegalArgumentException.class, () -> LabelReviewApply.apply(folder, review, LocalDate.now()));
        Files.writeString(review, "reviewer: A\nscenarios:\n  bank-shield-01:\n    status: REVIEWED\n"
                + "    best: [summon]\n");
        assertThrows(IllegalArgumentException.class, () -> LabelReviewApply.apply(folder, review, LocalDate.now()));        var before = Files.readString(folder.resolve("bank-melee-01.json"));
        Files.writeString(review, "reviewer: B\nscenarios:\n  bank-melee-01:\n    status: DISPUTED\n"
                + "  bank-shield-01:\n    status: REVIEWED\n    best: [summon]\n");
        assertThrows(IllegalArgumentException.class, () -> LabelReviewApply.apply(folder, review, LocalDate.now()));
        assertEquals(before, Files.readString(folder.resolve("bank-melee-01.json")),
                "an invalid entry anywhere leaves every document as it was");
        try (var files = Files.list(folder)) {
            assertTrue(files.noneMatch(file -> file.toString().endsWith(".tmp")));
        }
    }

    @Test
    void theReviewSheetShowsEveryScenarioWithoutWhatFitsNow() {
        var sheet = LabelReviewSheet.sheet();
        for (var scenario : ScenarioBank.scenarios()) {
            assertTrue(sheet.contains("### " + scenario.id()), scenario.id());
        }
        assertFalse(sheet.contains("fits now:"), "the sheet never shows what the code thinks fits now");
        assertFalse(sheet.contains("poor fit now:"));
        assertTrue(sheet.indexOf("### bank-cont-critical-02") < sheet.indexOf("### bank-melee-03"),
                "core24 first");
    }

    @Test
    void theBankCanBeEncodedAtEveryDetailAndWithFitChecks() {
        for (var scenario : ScenarioBank.scenarios()) {
            for (var detail : QuestionDetail.values()) {
                var request = ScenarioBank.request(scenario, detail, detail == QuestionDetail.FULL);
                assertEquals(1, request.questions().size());
                var checks = scenario.situation() == Situation.NOW && detail == QuestionDetail.FULL
                        ? request.questions().getFirst().options().size() : 0;
                assertEquals(checks, request.fitChecks().size(), scenario.id());
            }
        }
    }

    private static JsonObject raw(BankScenario scenario) throws IOException {
        return StrictJson.parseObject(Files.readString(ScenarioBank.FOLDER.resolve(ScenarioBank.fileName(scenario))),
                scenario.id()).getAsJsonObject("labels").getAsJsonObject("q0");
    }
}
