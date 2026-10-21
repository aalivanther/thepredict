package akyrion.thepredict.adapter.jev;

import akyrion.thepredict.domain.Answer;
import akyrion.thepredict.domain.DecisionResult;
import akyrion.thepredict.domain.QuestionKey;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Scenario bank of M13.6: what one recorded decision holds, that recording never delays an answer, and the limits of
 * the recorder (queue, per-start cap, failed writes, names across restarts).
 */
class ScenarioRecorderTest {
    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-09-29T10:15:30Z"), ZoneOffset.UTC);
    /** Close wait of the tests: long enough that a close never cuts a write the test expects. */
    private static final long PATIENT_CLOSE_MILLIS = 30_000;

    private final SystemOneContractServer server = SystemOneContractServer.start();
    private final List<Runnable> closeables = new ArrayList<>();

    @AfterEach
    void stop() {
        closeables.forEach(Runnable::run);
        server.close();
    }

    @Test
    void anAnsweredDecisionIsWrittenAfterItsResultWithAliasesOnlyAndAnEmptyLabelPerQuestion(@TempDir Path folder)
            throws Exception {
        server.onDecide(SystemOneContractServer.json(200, SystemOneContractServer.fixture("response-valid.json")));
        var recorder = recorder(folder.resolve("scenarios"), 10, lines(), ScenarioRecorderTest::realWrite);
        var client = client(recorder);
        assertInstanceOf(DecisionResult.Answered.class, JevFixtures.send(client, JevFixtures.request()).get(10, TimeUnit.SECONDS));
        client.close();

        var files = files(folder.resolve("scenarios"));
        assertEquals(1, files.size());
        var name = files.getFirst().getFileName().toString();
        assertTrue(name.matches("scenario-20260929-101530-[0-9a-f]{8}-0001\\.json"), name);
        var text = Files.readString(files.getFirst());
        var document = JsonParser.parseString(text).getAsJsonObject();
        assertEquals(ScenarioSerializer.FORMAT, document.get("format").getAsInt());
        assertEquals(1, document.get("mobs").getAsInt());
        assertEquals(1, document.get("participants").getAsInt());
        var body = server.received().stream().filter(request -> request.method().equals("POST")).findFirst()
                .orElseThrow().body();
        assertEquals(body.length(), document.get("requestChars").getAsInt());
        assertEquals(JsonParser.parseString(body), document.get("request"));
        assertEquals("IF_OWN_HEALTH_CRITICAL", document.getAsJsonArray("questions").get(1).getAsJsonObject()
                .get("situation").getAsString());
        assertEquals(412, document.getAsJsonObject("answer").get("inputTokens").getAsInt());
        var label = document.getAsJsonObject("labels").getAsJsonObject("q1");
        assertEquals(0, label.getAsJsonArray("acceptable").size());
        assertEquals("", label.get("forbidden").getAsString());
        assertFalse(text.contains(JevFixtures.MOB_A.entityId().toString()), text);
        assertFalse(text.contains(JevFixtures.KEY), text);
    }

    @Test
    void aSlowDiskNeverDelaysTheAnswerAndTheScenarioIsWrittenOnceTheDiskCatchesUp(@TempDir Path folder)
            throws Exception {
        server.onDecide(SystemOneContractServer.json(200, SystemOneContractServer.fixture("response-valid.json")));
        var disk = new CountDownLatch(1);
        var recorder = recorder(folder, 10, lines(), (target, document) -> {
            await(disk);
            realWrite(target, document);
        });
        var client = client(recorder);
        var result = JevFixtures.send(client, JevFixtures.request()).get(10, TimeUnit.SECONDS);
        assertInstanceOf(DecisionResult.Answered.class, result, "the answer arrives while the disk is still blocked");
        assertEquals(0, recorder.written());
        disk.countDown();
        recorder.close();
        assertEquals(1, recorder.written());
        assertEquals(1, files(folder).size());
    }

    @Test
    void aFullQueueDropsTheDecisionCountsItAndSaysSoOnce(@TempDir Path folder) {
        var disk = new CountDownLatch(1);
        var lines = lines();
        var recorder = recorder(folder, 1000, lines, (target, document) -> {
            await(disk);
            realWrite(target, document);
        });
        for (var index = 0; index < ScenarioRecorder.QUEUE_CAPACITY + 3; index++) {
            recorder.record(body(), JevFixtures.request(), answered());
        }
        assertEquals(2, recorder.dropped(), "one running, a full queue and two more");
        disk.countDown();
        recorder.close();
        assertEquals(ScenarioRecorder.QUEUE_CAPACITY + 1, recorder.written());
        assertEquals(List.of("Scenario recording dropped a decision: its queue of 64 is full"), lines);
    }

    @Test
    void aQueuedDecisionWhoseAnswerLostItsFutureIsNotWritten(@TempDir Path folder) {
        var disk = new CountDownLatch(1);
        var started = new CountDownLatch(1);
        var recorder = recorder(folder, 10, lines(), (target, document) -> {
            started.countDown();
            await(disk);
            realWrite(target, document);
        });
        recorder.record(body(), JevFixtures.request(), answered());
        await(started);
        var late = recorder.record(body(), JevFixtures.request(), answered());
        late.run();
        disk.countDown();
        recorder.close();
        assertEquals(1, recorder.written(), "the answer that lost to the deadline is not in the bank");
    }

    @Test
    void decisionsStillQueuedWhenTheRecorderClosesAreDroppedAndCounted(@TempDir Path folder) {
        var disk = new CountDownLatch(1);
        var started = new CountDownLatch(1);
        var recorder = ScenarioRecorder.into(folder, 100, FIXED, lines()::add, (target, document) -> {
            started.countDown();
            await(disk);
        }, 0);
        for (var index = 0; index < 5; index++) {
            recorder.record(body(), JevFixtures.request(), answered());
        }
        await(started);
        recorder.close();
        disk.countDown();
        assertEquals(4, recorder.dropped(), "four were still queued behind the write in progress");
    }

    @Test
    void theLimitIsReportedEvenAfterAnEarlierWriteFailedAndNoBodyIsLogged(@TempDir Path folder) {
        var lines = lines();
        var recorder = recorder(folder, 2, lines, (target, document) -> {
            throw new IOException("disk full: " + document);
        });
        for (var index = 0; index < 5; index++) {
            recorder.record(body(), JevFixtures.request(), answered());
        }
        recorder.close();
        assertEquals(2, recorder.failures());
        assertEquals(3, recorder.skipped());
        assertEquals(2, lines.size(), lines.toString());
        assertTrue(lines.contains("Scenario recording failed: IOException"), lines.toString());
        assertTrue(lines.contains("Scenario recording reached its limit of 2 files; later decisions are not recorded"));
        assertFalse(String.join(" ", lines).contains("altar"));
    }

    @Test
    void twoStartsInTheSameSecondNeverOverwriteEachOtherAndLeaveNoTemporaryFile(@TempDir Path folder)
            throws IOException {
        for (var start = 0; start < 2; start++) {
            var recorder = ScenarioRecorder.into(folder, 10, FIXED, line -> {
            }, ScenarioRecorderTest::realWrite, PATIENT_CLOSE_MILLIS);
            recorder.record(body(), JevFixtures.request(), answered());
            recorder.close();
        }
        var files = files(folder);
        assertEquals(2, files.size(), files.toString());
        assertNotEquals(files.get(0).getFileName(), files.get(1).getFileName());
        assertTrue(files.stream().allMatch(file -> file.toString().endsWith(".json")), "no temporary file is left");
    }

    @Test
    void aFailedDecisionIsNotRecorded(@TempDir Path folder) throws Exception {
        server.onDecide(SystemOneContractServer.json(500, "{}"));
        var recorder = recorder(folder, 10, lines(), ScenarioRecorderTest::realWrite);
        var client = client(recorder);
        assertInstanceOf(DecisionResult.Failed.class, JevFixtures.send(client, JevFixtures.request()).get(10, TimeUnit.SECONDS));
        client.close();
        assertEquals(List.of(), files(folder));
        assertEquals(0, recorder.written());
    }

    @Test
    void theDisabledRecorderWritesNothingAndHasNoFolder() {
        var recorder = ScenarioRecorder.disabled();
        recorder.record(body(), JevFixtures.request(), answered());
        recorder.close();
        assertFalse(recorder.enabled());
        assertTrue(recorder.directory().isEmpty());
        assertEquals(0, recorder.written());
    }

    private ScenarioRecorder recorder(Path folder, int limit, List<String> lines, ScenarioRecorder.Writer writer) {
        var recorder = ScenarioRecorder.into(folder, limit, FIXED, lines::add, writer, PATIENT_CLOSE_MILLIS);
        closeables.add(recorder::close);
        return recorder;
    }

    private SystemOneClient client(ScenarioRecorder recorder) {
        var client = new SystemOneClient(JevFixtures.settings(server.baseUrl()), JevFixtures.CLOCK, line -> {
        }, recorder);
        closeables.add(client::close);
        return client;
    }

    private static List<String> lines() {
        return new CopyOnWriteArrayList<>();
    }

    private static void realWrite(Path target, String document) throws IOException {
        var temporary = Files.createTempFile(target.getParent(), "scenario-", ".tmp");
        Files.writeString(temporary, document);
        Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static String body() {
        return new RequestEncoder("jev-1.13.0", new SemanticEncoder(12_000)).encode(JevFixtures.request());
    }

    private static DecisionResult.Answered answered() {
        var now = new Answer(Map.of(JevFixtures.tactic("keep_range"), 0.6, JevFixtures.tactic("press_attack"), 0.3,
                JevFixtures.tactic("hold_ground"), 0.1), 0.6);
        return new DecisionResult.Answered(Map.of(QuestionKey.of(0), now), "jev-1.13.0", 400);
    }

    private static List<Path> files(Path folder) throws IOException {
        if (!Files.isDirectory(folder)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(folder)) {
            return stream.sorted().toList();
        }
    }
}
