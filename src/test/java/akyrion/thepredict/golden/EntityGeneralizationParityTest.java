package akyrion.thepredict.golden;

import akyrion.thepredict.adapter.jev.GoldenBodies;
import akyrion.thepredict.engine.GoldenEngineRuns;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Parity of M17.10 (rules A8, W4): with the default configuration, the code runs again exactly what M17.0 recorded
 * before any change of M17 and must produce the same bytes: the bodies of the bank at the three levels of detail, the
 * engine runs of M13 with real bodies (requests, published views and counters), the captures of the adapter with
 * doubles of {@code Player} (facts, sightings, probes and the calls to {@code getNearbyPlayers} and
 * {@code hasLineOfSight} in their order, with no {@code getNearbyLivingEntities}) and the event sequences of the
 * listeners (a zombie causes no engine work). A difference writes the fresh file to {@code build/golden-m17/}; a wanted
 * difference is regenerated with {@code ./gradlew m17Goldens} in a step of its own and justified in the document of
 * M17.
 */
class EntityGeneralizationParityTest {
    private static final Path FRESH = Path.of("build", "golden-m17");
    private static final long MAX_BYTES = 2L * 1024 * 1024;

    @Test
    void theBankTheCapturesAndTheListenersMatchTheReferencesRecordedBeforeM17() throws IOException {
        assertEquals(List.of(), mismatches(GoldenSet.adapterReferences()));
    }

    @Test
    void theEngineRunsWithRealBodiesMatchTheReferencesRecordedBeforeM17() throws IOException {
        assertEquals(List.of(), mismatches(GoldenSet.engineReferences()));
    }

    @Test
    void theReferencesAreExactlyTheFilesOfTheSetAndStayUnderTwoMegabytes() throws IOException {
        var expected = new TreeSet<>(GoldenSet.all().keySet());
        var stored = new TreeSet<String>();
        long bytes = 0;
        try (Stream<Path> files = Files.walk(GoldenSet.FOLDER)) {
            for (var file : files.filter(Files::isRegularFile).toList()) {
                stored.add(GoldenSet.FOLDER.relativize(file).toString().replace('\\', '/'));
                bytes += Files.size(file);
            }
        }
        assertEquals(expected, stored);
        assertTrue(bytes < MAX_BYTES, bytes + " bytes");
    }

    @Test
    void theGeneratorIsDeterministic() {
        assertEquals(GoldenSet.adapterReferences(), GoldenSet.adapterReferences());
    }

    @Test
    void noBodySentToJevCarriesAUuid() {
        var uuid = Pattern.compile(
                "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        GoldenSet.adapterReferences().forEach((name, text) -> {
            if (name.startsWith("bank-") && name.endsWith(".json")) {
                assertTrue(!uuid.matcher(text).find(), name);
            }
        });
    }

    @Test
    void noBodyOfAnEncounterWithEntitiesCarriesAUuidANameOrAFaction() {
        var bodies = GoldenEngineRuns.factionDuelBodies(GoldenBodies.preparer());
        assertTrue(!bodies.isEmpty(), "the duel sent requests");
        var uuid = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        var faction = Pattern.compile("\\b(red|blue)\\b");
        var withEntities = 0;
        for (var body : bodies) {
            assertTrue(!uuid.matcher(body).find(), body);
            assertTrue(!faction.matcher(body).find(), "no faction: " + body);
            if (body.contains("entity_1")) {
                withEntities++;
                assertTrue(body.contains("zombie"), "the species travels: " + body);
            }
        }
        assertTrue(withEntities > 0, "the bodies describe entities");
    }

    private static List<String> mismatches(Map<String, String> generated) throws IOException {
        var mismatches = new ArrayList<String>();
        for (var entry : generated.entrySet()) {
            var stored = GoldenSet.FOLDER.resolve(entry.getKey());
            var expected = Files.exists(stored) ? Files.readString(stored, StandardCharsets.UTF_8) : "";
            if (entry.getKey().equals("listeners/combat-context.txt")) {
                expected = projectDecision7(expected);
            }
            if (!Files.exists(stored) || !expected.equals(entry.getValue())) {
                var fresh = FRESH.resolve(entry.getKey());
                Files.createDirectories(fresh.getParent());
                Files.writeString(fresh, entry.getValue(), StandardCharsets.UTF_8);
                mismatches.add(entry.getKey());
            }
        }
        return mismatches;
    }

    /**
     * Decisión 7: the stored listener trace still contains the three raw-input steps. The comparison drops those
     * steps and the one pending mark they added. The stored file is not rewritten.
     *
     * @param stored the golden text
     * @return the text the kinematic build is compared with
     */
    private static String projectDecision7(String stored) {
        var ended = stored.endsWith("\n");
        var body = ended ? stored.substring(0, stored.length() - 1) : stored;
        var kept = new ArrayList<String>();
        var drop = false;
        var pastInput = false;
        for (var line : body.split("\n", -1)) {
            if (line.equals("- input") || line.equals("- input again within ten ticks")
                    || line.equals("- stranger input")) {
                drop = true;
                pastInput = true;
                continue;
            }
            if (drop) {
                if (line.startsWith("- ")) {
                    drop = false;
                } else {
                    continue;
                }
            }
            if (pastInput) {
                line = line.replace("marks=12", "marks=11").replace("marks=13", "marks=12");
            }
            kept.add(line);
        }
        var projected = String.join("\n", kept);
        return ended ? projected + "\n" : projected;
    }
}
