package akyrion.thepredict.adapter.server.duel;

import akyrion.thepredict.domain.Answer;
import akyrion.thepredict.domain.ChoiceId;
import akyrion.thepredict.domain.CombatFacts;
import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.DecisionRequest;
import akyrion.thepredict.domain.DecisionResult;
import akyrion.thepredict.domain.DiscardReason;
import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.ExecutionOutcome;
import akyrion.thepredict.domain.FailureKind;
import akyrion.thepredict.domain.FallbackReason;
import akyrion.thepredict.domain.HealthBand;
import akyrion.thepredict.domain.MobFacts;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.QuestionKey;
import akyrion.thepredict.domain.QuestionSpec;
import akyrion.thepredict.domain.Situation;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.engine.DecisionDispatcher;
import akyrion.thepredict.engine.DecisionTap;
import akyrion.thepredict.engine.DuelRecording;
import akyrion.thepredict.engine.IntentBoard;
import akyrion.thepredict.engine.Preparation;
import akyrion.thepredict.engine.PreparedRequest;
import akyrion.thepredict.engine.SwitchableDecisionTap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DuelRecorderTest {
    private static final UUID RED = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID BLUE = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final Pattern UUID_TEXT = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    @TempDir
    Path root;

    private final IntentBoard board = new IntentBoard();
    private final SwitchableDecisionTap tap = new SwitchableDecisionTap();

    @Test
    void aRoundOfArmAIsRecordedFromItsSamplesToTheDeathOfBlue() throws IOException {
        var kit = arena();
        var status = kit.recorder.start(request("arm-a"));
        assertTrue(status.recording(), status.stoppedBy().orElse(""));
        assertTrue(tap.recording(), "the recorder attaches itself to the engine tap");
        run(kit, 10);
        kit.recorder.castAttempted(new MobKey(RED, 0), new TacticId("press_and_strike"), new ChoiceId("strike"),
                ExecutionOutcome.CAST);
        kit.recorder.damaged(RED, BLUE, "ENTITY_ATTACK", 5, 5.0 / 60, false);
        kit.recorder.skillEvent(BLUE, "DuelBolt");
        run(kit, 30);
        kit.recorder.damaged(BLUE, RED, "ENTITY_ATTACK", 3, 3.0 / 60, false);
        kit.recorder.skillEvent(BLUE, "DuelRuleStrike");
        kit.recorder.gone(BLUE, true);
        var stopped = kit.recorder.stop();
        assertFalse(stopped.recording());
        assertEquals(Optional.of(DuelRecorder.STOPPED_BY_COMMAND), stopped.stoppedBy());
        assertFalse(tap.recording());
        var folder = finished(kit);
        var rounds = Files.readAllLines(folder.resolve("rounds.csv"));
        assertEquals(2, rounds.size());
        assertTrue(rounds.get(1).startsWith("1,A,false," + RED + ",tp," + BLUE + ",classic,0,40,red,death,"),
                rounds.get(1));
        var casts = Files.readAllLines(folder.resolve("casts.csv"));
        assertEquals(3, casts.size(), "the rule wrapper is not a counted skill: " + casts);
        assertTrue(casts.get(1).endsWith(",THEPREDICT,DuelStrike,press_and_strike,strike,CAST"), casts.get(1));
        assertTrue(casts.get(2).endsWith(",MYTHIC_EVENT,DuelBolt,,,ATTEMPT"), casts.get(2));
        var damage = Files.readAllLines(folder.resolve("damage.csv"));
        assertTrue(damage.get(1).endsWith(",DuelStrike,true"), "a hit of the cast within 20 ticks: " + damage.get(1));
        assertTrue(damage.get(2).endsWith(",,false"), "the bolt of tick 10 is a miss at tick 40: " + damage.get(2));
        assertEquals(1 + 2 * 8, Files.readAllLines(folder.resolve("samples.csv")).size());
        var summary = Files.readString(folder.resolve("summary.md"));
        assertTrue(summary.contains("- stopped_by: command"), summary);
        assertTrue(summary.contains("| A | 1 | 0 | 1 | 0 | 0 | 1 |"), summary);
        assertTrue(summary.contains("| A | DuelBolt | 1 | 0 | 1 |"), summary);
        assertTrue(summary.contains("| A | DuelStrike | 1 | 1 | 0 |"), summary);
    }

    @Test
    void aRemovalEndsTheRoundAsADrawAndSwappedSidesAreRead() throws IOException {
        var kit = arena();
        kit.scene.put(DuelTestKit.at(RED, "DUEL_RED", 0.5, 10.5));
        kit.scene.put(DuelTestKit.at(BLUE, "duel_blue", 0.5, -9.5));
        kit.recorder.start(request("draw"));
        run(kit, 5);
        kit.recorder.gone(RED, false);
        kit.recorder.stop();
        var rounds = Files.readAllLines(finished(kit).resolve("rounds.csv"));
        assertTrue(rounds.get(1).startsWith("1,A,true,"), rounds.get(1));
        assertTrue(rounds.get(1).contains(",draw,removed,"), rounds.get(1));
    }

    @Test
    void decisionsJsonlJoinsEveryRequestByItsKeyWhateverTheOrderOfTheLines() throws IOException {
        var kit = arena();
        kit.recorder.start(request("decisions"));
        run(kit, 5);
        var recorder = kit.recorder;
        var red = new MobKey(RED, 0);
        var blue = new MobKey(BLUE, 0);
        sent(recorder, 1, 1, red);
        sent(recorder, 2, 1, blue);
        recorder.resultReceived(new EncounterId(1), 1, answered(), 120_000_000L);
        adopted(recorder, red);
        recorder.resultReceived(new EncounterId(2), 1, new DecisionResult.Failed(FailureKind.TIMEOUT,
                Optional.empty(), "no answer before the deadline"), 800_000_000L);
        sent(recorder, 1, 2, red);
        recorder.resultReceived(new EncounterId(1), 2, answered(), 900_000_000L);
        recorder.answerDiscarded(new EncounterId(1), 2, DiscardReason.STALE_SEQUENCE);
        planned(recorder, 2, 2, blue, new Preparation.NothingToAsk(Set.of(blue)),
                DecisionDispatcher.Outcome.NOTHING_TO_ASK);
        planned(recorder, 2, 3, blue, ready(2, 3, blue), DecisionDispatcher.Outcome.NO_BUDGET);
        planned(recorder, 1, 3, red, new Preparation.Invalid("the body is larger than the guard"),
                DecisionDispatcher.Outcome.PREPARE_FAILED);
        recorder.intentEvent(blue, new DecisionTap.IntentEvent(DecisionTap.IntentEventKind.LOCAL_PLANNED,
                new TacticId("approach"), Movement.APPROACH, 0.0, ControlLevel.BASE, ControlLevel.LOCAL,
                Optional.empty(), Optional.of(FallbackReason.NO_BUDGET)));
        recorder.stop();
        var lines = Files.readAllLines(finished(kit).resolve("decisions.jsonl"));
        var truncated = new java.util.ArrayList<>(lines);
        truncated.add("{\"type\":\"planned\",\"encounter\":7,\"seq");
        assertEquals(DuelJoin.join(lines).size(), DuelJoin.join(truncated).size(), "a cut last line is skipped");
        var requests = DuelJoin.join(lines);
        assertEquals(6, requests.size());
        assertEquals(List.of("planned", "body", "result", "acceptance"), request(requests, 1, 1).types());
        assertEquals("ADOPTED", request(requests, 1, 1).lines().get(3).get("decision").getAsString());
        assertEquals("q0", request(requests, 1, 1).lines().get(3).get("question").getAsString());
        assertEquals(List.of("planned", "body", "result"), request(requests, 2, 1).types());
        assertEquals("TIMEOUT", request(requests, 2, 1).lines().get(2).get("failure").getAsString());
        assertEquals(List.of("planned", "body", "result", "acceptance"), request(requests, 1, 2).types());
        assertEquals("STALE_SEQUENCE", request(requests, 1, 2).lines().get(3).get("reason").getAsString());
        assertEquals(List.of("planned"), request(requests, 2, 2).types());
        assertEquals("NOTHING_TO_ASK", request(requests, 2, 2).lines().getFirst().get("dispatch").getAsString());
        assertEquals(List.of("planned"), request(requests, 2, 3).types());
        assertEquals("NO_BUDGET", request(requests, 2, 3).lines().getFirst().get("dispatch").getAsString());
        assertEquals(List.of("planned"), request(requests, 1, 3).types());
        assertEquals("the body is larger than the guard", request(requests, 1, 3).lines().getFirst()
                .get("dispatch_detail").getAsString());
        var local = lines.stream().filter(line -> line.startsWith("{\"type\":\"local\"")).toList();
        assertEquals(1, local.size());
        assertTrue(local.getFirst().contains("\"encounter\":null,\"sequence\":-1,"), local.getFirst());
        lines.stream().filter(line -> line.startsWith("{\"type\":\"body\"")).forEach(line -> assertFalse(
                UUID_TEXT.matcher(line).find(), "a body sent to Jev only holds aliases: " + line));
        assertTrue(lines.stream().allMatch(line -> line.contains("\"t_nanos\":")));
    }

    @Test
    void eachLimitStopsTheRecordingWithItsReasonAndTellsTheLog() throws IOException {
        var kit = arena();
        kit.recorder.start(request("hours"));
        kit.clock.nanos += DuelRecorder.MAX_HOURS * 3_600_000_000_000L;
        run(kit, 5);
        assertFalse(kit.recorder.recording());
        assertEquals(List.of("Duel recording 'hours' stopped: 4 h reached"), kit.notices);
        assertTrue(Files.readString(finished(kit).resolve("summary.md")).contains("- stopped_by: 4 h"));
        var rounds = arena();
        rounds.scene.alive.remove(BLUE);
        rounds.recorder.start(request("rounds"));
        for (var round = 0; round < DuelRecorder.MAX_ROUNDS && rounds.recorder.recording(); round++) {
            var blue = new UUID(7, round);
            rounds.scene.put(DuelTestKit.at(blue, "duel_blue", 0.5, 10.5));
            run(rounds, 5);
            rounds.scene.alive.remove(blue);
            rounds.recorder.gone(blue, true);
        }
        run(rounds, 5);
        assertEquals(Optional.of("500 rounds"), rounds.recorder.status().stoppedBy());
        finished(rounds);
    }

    @Test
    void aFullFileOrAFailedWriteStopsTheRecording() {
        for (var failing : List.of(false, true)) {
            var kit = arena();
            var writers = new java.util.ArrayList<DuelWriter>();
            var limited = new DuelRecorder(new DuelRecorder.Parts(root.resolve("limited-" + failing), kit.clock,
                    java.time.Clock.systemUTC(), kit.scene, board, tap, kit.repeater, new DuelTestKit.Notices(kit.notices), folder -> {
                        var writer = DuelWriter.start(folder, 64, 100, 4_000, file -> {
                            if (failing) {
                                throw new IOException("disk full");
                            }
                            return new java.io.StringWriter();
                        });
                        writers.add(writer);
                        return writer;
                    }));
            limited.start(request("limited"));
            kit.repeater.tick(0);
            var deadline = System.nanoTime() + 10_000_000_000L;
            while (writers.getFirst().limitReached().isEmpty() && writers.getFirst().failure().isEmpty()
                    && System.nanoTime() < deadline) {
                Thread.onSpinWait();
            }
            kit.clock.tick = 5;
            kit.repeater.tick(5);
            assertFalse(limited.recording());
            var reason = failing ? "a write failure (IOException)" : DuelWriter.FILE_LIMIT;
            assertEquals(Optional.of(reason), limited.status().stoppedBy());
            assertEquals(List.of("Duel recording 'limited' stopped: " + reason + " reached"), kit.notices);
            limited.stop();
            assertTrue(limited.awaitWriter(60_000));
        }
    }

    @Test
    void labelsThatCouldLeaveTheFolderAndAStartWhileRecordingAreRefused() {
        for (var label : List.of("a/b", "..", "Upper", "", "x".repeat(41), "a.b")) {
            assertThrows(IllegalArgumentException.class, () -> request(label), label);
        }
        var kit = arena();
        kit.recorder.start(request("first"));
        var refused = kit.recorder.start(request("second"));
        assertFalse(refused.recording());
        assertEquals(Optional.of("recording 'first' is running; stop it first"), refused.stoppedBy());
        kit.recorder.stop();
        assertTrue(kit.recorder.awaitWriter(60_000));
        kit.scene.loaded = false;
        assertEquals(Optional.of("the world of the arena is not loaded"),
                kit.recorder.start(request("third")).stoppedBy());
    }

    @Test
    void withoutARecordingNothingIsWrittenAndTheTapStaysSilent() throws IOException {
        var kit = arena();
        assertFalse(kit.recorder.recording());
        assertFalse(tap.recording());
        kit.recorder.damaged(RED, BLUE, "ENTITY_ATTACK", 1, 0.1, false);
        kit.recorder.gone(BLUE, true);
        kit.recorder.skillEvent(RED, "DuelStrike");
        assertEquals(DuelRecording.DuelStatus.IDLE, kit.recorder.status());
        assertEquals(DuelRecording.DuelStatus.IDLE, kit.recorder.stop());
        assertFalse(kit.repeater.scheduled());
        assertTrue(kit.recorder.shutdown(), "nothing to wait for");
        try (var files = Files.walk(root)) {
            assertEquals(1, files.count(), "nothing is created without a recording");
        }
    }

    @Test
    void theDefaultCountedSkillsAreTheSkillsOfTheProfilesThatAdmitEitherFaction() {
        var profiles = DuelTestKit.packProfiles().values();
        assertEquals(Set.of("DuelStrike", "DuelBolt", "DuelLeap", "DuelGuard", "DuelHeal"),
                DuelSession.defaultSkills(profiles, request("skills")));
        assertEquals(Set.of(), DuelSession.defaultSkills(profiles, new DuelRecording.DuelRequest("other", "a", "b",
                DuelTestKit.WORLD, 0, 65, 0, 24, Set.of(), Optional.empty())));
    }

    @Test
    void aBodyToldFromAnotherThreadIsOnlyQueuedAndNeverWrittenByTheCaller() throws Exception {
        var kit = arena();
        var release = new java.util.concurrent.CountDownLatch(1);
        var blocked = new java.util.concurrent.CountDownLatch(1);
        var disk = new java.io.StringWriter();
        var recorder = new DuelRecorder(new DuelRecorder.Parts(root.resolve("threads"), kit.clock,
                java.time.Clock.systemUTC(), kit.scene, board, tap, kit.repeater, new DuelTestKit.Notices(new java.util.ArrayList<>()), folder -> DuelWriter.start(folder, 64, DuelWriter.MAX_FILE_BYTES, DuelWriter.MAX_RECORDING_BYTES,
                        file -> new java.io.FilterWriter(file.getFileName().toString().equals("decisions.jsonl") ? disk
                                : new java.io.StringWriter()) {
                            @Override
                            public void write(String text, int offset, int length) throws IOException {
                                blocked.countDown();
                                try {
                                    release.await(10, java.util.concurrent.TimeUnit.SECONDS);
                                } catch (InterruptedException exception) {
                                    Thread.currentThread().interrupt();
                                }
                                super.write(text, offset, length);
                            }
                        })));
        recorder.start(request("threads"));
        kit.repeater.tick(0);
        var red = new MobKey(RED, 0);
        planned(recorder, 9, 1, red, ready(9, 1, red), DecisionDispatcher.Outcome.SENT);
        assertTrue(blocked.await(10, java.util.concurrent.TimeUnit.SECONDS), "the writer thread holds the disk");
        var caller = new Thread(() -> recorder.requestBody(new EncounterId(9), 1, "{\"state\":\"late\"}"));
        caller.start();
        caller.join(10_000);
        assertFalse(caller.isAlive(), "the caller returned while the disk was still blocked");
        release.countDown();
        recorder.stop();
        assertTrue(recorder.awaitWriter(60_000));
        assertTrue(disk.toString().contains("{\"type\":\"body\",\"encounter\":9,\"sequence\":1,"), disk.toString());
    }

    @Test
    void aRecorderWhoseTapCallThrowsStopsWithTapFailureAndTheEngineGoesOn() {
        var kit = arena();
        kit.recorder.start(request("broken"));
        kit.repeater.tick(0);
        var broken = new DuelTestKit.Scene() {
            @Override
            public Optional<String> faction(UUID entityId) {
                throw new IllegalStateException("broken scene");
            }
        };
        kit.scene.factions.clear();
        var red = new MobKey(RED, 0);
        var request = decisionRequest(3, 1, red);
        var failing = new DuelRecorder(new DuelRecorder.Parts(root.resolve("broken"), kit.clock,
                java.time.Clock.systemUTC(), broken, board, tap, kit.repeater, new DuelTestKit.Notices(kit.notices),
                DuelWriter::start));
        kit.recorder.stop();
        failing.start(request("broken-scene"));
        tap.questionsPlanned(request.encounter(), 1, request.questions(), ready(3, 1, red),
                DecisionDispatcher.Outcome.SENT, request.facts());
        assertFalse(failing.recording());
        assertFalse(tap.recording());
        assertEquals(Optional.of(DuelRecorder.STOPPED_BY_TAP_FAILURE), failing.status().stoppedBy());
        assertEquals("Duel recording 'broken-scene' stopped: tap failure reached", kit.notices.getLast());
        assertTrue(failing.awaitWriter(60_000));
        assertTrue(kit.recorder.awaitWriter(60_000));
    }

    @Test
    void anExistingFolderIsRefusedUpFrontAndAFailedStartLeavesNoThreadBehind() throws IOException {
        var kit = arena();
        var folder = root.resolve("taken");
        Files.createDirectories(folder.resolve("duels/busy-20260929-120000"));
        var taken = new DuelRecorder(new DuelRecorder.Parts(folder.resolve("duels"), kit.clock,
                java.time.Clock.fixed(java.time.Instant.parse("2026-09-29T12:00:00Z"), java.time.ZoneOffset.UTC),
                kit.scene, board, tap, kit.repeater, new DuelTestKit.Notices(kit.notices), DuelWriter::create));
        assertEquals(Optional.of("the folder busy-20260929-120000 already exists; use another label or wait a second"),
                taken.start(request("busy")).stoppedBy());
        var writers = new java.util.ArrayList<DuelWriter>();
        var failing = new DuelRecorder(new DuelRecorder.Parts(root.resolve("failing"), kit.clock,
                java.time.Clock.systemUTC(), kit.scene, board, tap, (period, task) -> {
                    throw new IllegalStateException("scheduler down");
                }, new DuelTestKit.Notices(kit.notices), folder2 -> {
                    var writer = DuelWriter.create(folder2);
                    writers.add(writer);
                    return writer;
                }));
        assertThrows(IllegalStateException.class, () -> failing.start(request("first")));
        assertFalse(writers.getFirst().running(), "the writer thread starts last and never started");
        assertFalse(failing.recording());
        assertFalse(tap.recording());
        assertTrue(kit.recorder.start(request("second")).recording(), "a failed start never blocks the next one");
        kit.recorder.stop();
        assertTrue(kit.recorder.awaitWriter(60_000));
    }

    @Test
    void aRoundCutByTheStopIsWrittenAsUnfinishedInBothFiles() throws IOException {
        var kit = arena();
        kit.recorder.start(request("cut"));
        run(kit, 10);
        var folder = finished(kit);
        var rounds = Files.readAllLines(folder.resolve("rounds.csv"));
        assertEquals(2, rounds.size());
        assertTrue(rounds.get(1).contains(",unfinished,stopped,"), rounds.get(1));
        var summary = Files.readString(folder.resolve("summary.md"));
        assertTrue(summary.contains("| A | 0 | 1 | 0 | 0 | 0 | - |"), summary);
    }

    private DuelTestKit arena() {
        var kit = new DuelTestKit(root.resolve(UUID.randomUUID().toString()), board, tap);
        var profiles = DuelTestKit.packProfiles();
        kit.scene.catalog.addAll(profiles.values());
        kit.scene.bound.put(RED, profiles.get("DuelRed"));
        kit.scene.put(DuelTestKit.at(RED, "duel_red", 0.5, -9.5));
        kit.scene.put(DuelTestKit.at(BLUE, "duel_blue", 0.5, 10.5));
        return kit;
    }

    private static DuelRecording.DuelRequest request(String label) {
        return new DuelRecording.DuelRequest(label, "duel_red", "duel_blue", DuelTestKit.WORLD, 0, 65, 0, 24, Set.of(),
                Optional.empty());
    }

    private static void run(DuelTestKit kit, int ticks) {
        for (var index = 0; index < ticks; index++) {
            kit.repeater.tick(kit.clock.tick);
            kit.clock.tick++;
            kit.clock.nanos += 50_000_000L;
        }
    }

    private static Path finished(DuelTestKit kit) {
        kit.recorder.stop();
        assertTrue(kit.recorder.awaitWriter(60_000), "the writer ends");
        return Path.of(kit.recorder.status().directory());
    }

    private static DuelJoin.Request request(List<DuelJoin.Request> requests, long encounter, long sequence) {
        return requests.stream().filter(request -> request.encounter() == encounter && request.sequence() == sequence)
                .findFirst().orElseThrow();
    }

    private static void sent(DuelRecorder recorder, long encounter, long sequence, MobKey mob) {
        var ready = ready(encounter, sequence, mob);
        planned(recorder, encounter, sequence, mob, ready, DecisionDispatcher.Outcome.SENT);
        recorder.requestBody(new EncounterId(encounter), sequence, ready.prepared().body());
    }

    private static void planned(DuelRecorder recorder, long encounter, long sequence, MobKey mob,
                                Preparation preparation, DecisionDispatcher.Outcome dispatch) {
        var request = decisionRequest(encounter, sequence, mob);
        recorder.questionsPlanned(request.encounter(), sequence, request.questions(), preparation, dispatch,
                request.facts());
    }

    private static Preparation.Ready ready(long encounter, long sequence, MobKey mob) {
        var body = "{\"state\":\"entity_1 is a zombie\",\"sequence\":" + sequence + "}";
        return new Preparation.Ready(new PreparedRequest(decisionRequest(encounter, sequence, mob), body, Set.of(),
                Set.of(), body.length()));
    }

    private static DecisionRequest decisionRequest(long encounter, long sequence, MobKey mob) {
        var profile = profile(mob);
        var options = List.of(new TacticId("press_and_strike"), new TacticId("approach"));
        var facts = new CombatFacts(new EncounterId(encounter), 1, 1, List.of(new MobFacts(mob, HealthBand.HEALTHY,
                Optional.empty(), Set.of(), Set.copyOf(options), Set.of(), Set.of())), List.of(), List.of(), 0);
        return new DecisionRequest(new EncounterId(encounter), sequence, facts, List.of(new QuestionSpec(
                new QuestionKey("q0"), mob, Situation.NOW, options)), Map.of(mob, profile), List.of());
    }

    private static TacticalProfile profile(MobKey mob) {
        return DuelTestKit.packProfiles().get(mob.entityId().equals(RED) ? "DuelRed" : "DuelBlue");
    }

    private static DecisionResult.Answered answered() {
        return new DecisionResult.Answered(Map.of(new QuestionKey("q0"), new Answer(Map.of(
                new TacticId("press_and_strike"), 0.8, new TacticId("approach"), 0.2), 0.7)), "jev-1.13.0", 950);
    }

    private static void adopted(DuelRecorder recorder, MobKey mob) {
        recorder.intentEvent(mob, new DecisionTap.IntentEvent(DecisionTap.IntentEventKind.ADOPTED,
                new TacticId("press_and_strike"), Movement.APPROACH, 0.7, ControlLevel.LOCAL, ControlLevel.JEV,
                Optional.of(new QuestionKey("q0")), Optional.empty()));
    }
}