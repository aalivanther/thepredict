package akyrion.thepredict.golden;

import akyrion.thepredict.adapter.jev.GoldenBodies;
import akyrion.thepredict.adapter.server.context.CaptureGoldens;
import akyrion.thepredict.adapter.server.events.ListenerGoldens;
import akyrion.thepredict.domain.QuestionDetail;
import akyrion.thepredict.engine.GoldenEngineRuns;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;

/**
 * The golden references of M17.0, generated from the code as it runs now, by path under
 * {@code src/test/resources/golden/m17/}: the bodies of the bank of M16.9 at the three levels of detail (the SHA-256 of
 * all 60 per level in one index, and the exact text of the first {@value #FULL_BANK_BODIES} of each level, so the
 * references stay under 2 MB), the engine runs of M13 with real bodies, the captures of the adapter with doubles of
 * {@code Player}, and the event sequences of the listeners. {@link GoldenRecorder} writes them;
 * {@code EntityGeneralizationParityTest} compares a fresh generation with them byte for byte.
 */
final class GoldenSet {
    /** Bank bodies kept as exact text per level of detail; every body of the bank is in the SHA-256 index. */
    static final int FULL_BANK_BODIES = 20;
    /** Folder of the references, relative to the project. */
    static final Path FOLDER = Path.of("src", "test", "resources", "golden", "m17");

    private GoldenSet() {
    }

    /** The cheap references: bank, captures and listeners. */
    static Map<String, String> adapterReferences() {
        var files = new LinkedHashMap<String, String>();
        var index = new StringJoiner("\n", "", "\n");
        for (var detail : QuestionDetail.values()) {
            var folder = "bank-" + detail.name().toLowerCase(Locale.ROOT) + "/";
            var kept = new int[1];
            GoldenBodies.bank(detail).forEach((name, body) -> {
                if (kept[0]++ < FULL_BANK_BODIES) {
                    files.put(folder + name, body);
                }
                index.add(folder + name + " " + GoldenEngineRuns.sha256(body));
            });
        }
        files.put("bank-sha256.txt", index.toString());
        CaptureGoldens.all().forEach((name, text) -> files.put("capture/" + name, text));
        ListenerGoldens.all().forEach((name, text) -> files.put("listeners/" + name, text));
        return files;
    }

    /** The engine references: load steps and the local A/B. */
    static Map<String, String> engineReferences() {
        var files = new LinkedHashMap<String, String>();
        GoldenEngineRuns.all(GoldenBodies.preparer()).forEach((name, text) -> files.put("engine/" + name, text));
        return files;
    }

    /** Every reference. */
    static Map<String, String> all() {
        var files = new LinkedHashMap<>(adapterReferences());
        files.putAll(engineReferences());
        return files;
    }
}
