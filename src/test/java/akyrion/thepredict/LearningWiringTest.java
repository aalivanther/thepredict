package akyrion.thepredict;

import akyrion.thepredict.config.StorageSettings;
import akyrion.thepredict.domain.QuestionDetail;
import akyrion.thepredict.domain.RecordLevel;
import akyrion.thepredict.domain.distill.DistillArtifactFormat;
import akyrion.thepredict.domain.distill.DistillMode;
import akyrion.thepredict.domain.learn.ArtifactFormat;
import akyrion.thepredict.domain.learn.CalibratedMinimum;
import akyrion.thepredict.engine.GateVerdicts;
import akyrion.thepredict.engine.StorageReply;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** G of learn-distill, CAR-10 and CAR-20: one learn folder, verdicts by fingerprint, and no broken file stops a start. */
class LearningWiringTest {
    @TempDir
    Path directory;

    private final List<String> warnings = new ArrayList<>();

    @Test
    void aNestedRecordsDirectoryKeepsTheLearnFolderNextToItAsTheWriterDoes() {
        var storage = settings("data/records");
        var learn = LearningWiring.folder(directory, storage);
        var records = directory.resolve(storage.directory());
        assertEquals(records.resolveSibling("learn"), learn);
        assertEquals(directory.resolve("data").resolve("learn"), learn);
        assertEquals(directory.resolve("learn"), LearningWiring.folder(directory, StorageSettings.standard()));
    }

    @Test
    void theDistillCeilingIsTheVerdictOfTheArtifactInForce() throws IOException {
        var learn = directory.resolve("learn");
        var bytes = "tpld".getBytes(StandardCharsets.UTF_8);
        var sha = DistillArtifactFormat.sha256(bytes);
        var distill = Files.createDirectories(learn.resolve("distill").resolve("artifacts")).getParent();
        Files.write(distill.resolve("artifacts").resolve("a.tpld"), bytes);
        Files.writeString(distill.resolve("verdict.json"), "{\"sha256\":\"" + sha + "\",\"status\":\"PASS_SIM_ONLY\"}");
        var wiring = new LearningWiring(learn, warnings::add);
        assertEquals(DistillMode.OFF, wiring.distillCeiling(1024), "no active.txt names an artifact");
        Files.writeString(distill.resolve("active.txt"), "a.tpld\n");
        assertEquals(DistillMode.ASSIST, wiring.distillCeiling(1024));
        assertEquals(DistillMode.OFF, wiring.distillCeiling(2));
        Files.writeString(distill.resolve("verdict.json"), "{\"sha256\":\"" + "0".repeat(64) + "\",\"status\":\"PASS\"}");
        assertEquals(DistillMode.OFF, wiring.distillCeiling(1024));
        Files.writeString(distill.resolve("active.txt"), "../a.tpld");
        assertEquals(DistillMode.OFF, wiring.distillCeiling(1024));
        assertEquals(List.of("Distill verdict was not loaded: the artifact named by active.txt is missing or larger"
                + " than distill.maxArtifactBytes", "Distill verdict was not loaded: IllegalArgumentException"),
                warnings);
    }

    @Test
    void aPackagedJevSourceLoadsNoneInsteadOfStoppingTheStart() throws IOException {
        var packaged = Files.createDirectories(directory.resolve("learn").resolve("packaged").resolve("one"));
        Files.writeString(packaged.resolve("verdict.json"), "{\"source\":\"jev-argmax\"}");
        var loader = new ClassLoader(null) {
            @Override
            public URL getResource(String name) {
                try {
                    return "learn/packaged".equals(name) ? packaged.getParent().toUri().toURL() : null;
                } catch (IOException exception) {
                    throw new IllegalStateException(exception);
                }
            }
        };
        assertTrue(new LearningWiring(directory, warnings::add).packaged(loader).isEmpty());
        assertEquals(List.of("Packaged distill was not loaded: IllegalArgumentException"), warnings);
    }

    @Test
    void theCalibratorActsOnlyWithAPassForTheSha256OfItsFile() throws IOException {
        var learn = Files.createDirectories(directory.resolve("learn"));
        var bytes = ArtifactFormat.calibrationArtifact(1, "jev-1.13.0", 1_500_000, -200_000);
        Files.write(learn.resolve("active.tplc"), bytes);
        var wiring = new LearningWiring(learn, warnings::add);
        var key = LearningWiring.calibrationKey("jev-1.13.0", QuestionDetail.COMPACT);
        assertEquals(QuestionDetail.COMPACT, key.detail());
        var gates = new GateVerdicts();
        var pending = wiring.calibrator("jev-1.13.0", key, gates).calibrator();
        assertEquals(0.40, pending.threshold(key, "jev-1.13.0", 0.40, false, 0.70).rawThreshold(), 1e-12);
        gates.load(List.of(new StorageReply.GateVerdictRow(ArtifactFormat.sha256(bytes), "G1", "PASS", "run", 1)));
        var verified = wiring.calibrator("jev-1.13.0", key, gates).calibrator();
        assertNotEquals(0.40, verified.threshold(key, "jev-1.13.0", 0.40, false, 0.70).rawThreshold(), 1e-6);
        Files.writeString(learn.resolve("active.tplc"), "broken");
        var broken = wiring.calibrator("jev-1.13.0", key, gates).calibrator();
        assertEquals(0.40, broken.threshold(key, "jev-1.13.0", 0.40, false, 0.70).rawThreshold(), 1e-12);
        assertEquals(1, warnings.size(), warnings.toString());
        assertTrue(Double.isNaN(LearningWiring.calibrationTarget(CalibratedMinimum.PARITY)));
        assertEquals(0.7, LearningWiring.calibrationTarget(new CalibratedMinimum(false, 0.7)));
    }

    private static StorageSettings settings(String directory) {
        var d = StorageSettings.standard();
        return new StorageSettings(RecordLevel.DECISIONS, d.bodies(), directory, d.queueCapacity(),
                d.segmentMegabytes(), d.segmentMinutes(), d.compressClosedSegments(), d.retentionDays(),
                d.maxTotalMegabytes(), d.aggregates(), d.file(), d.synchronous(), d.busyTimeoutMillis(),
                d.flushIntervalTicks(), d.maxPendingKeys(), d.backupEveryHours(), d.backupsKept(), d.answerCache(),
                d.answerCacheTtlTicks(), d.answerCacheEntries(), d.answerCacheRevalidateRatio(),
                d.patternSummaryBinary());
    }
}
