package akyrion.thepredict;

import akyrion.thepredict.adapter.server.events.MobDirectory;
import akyrion.thepredict.adapter.server.events.ServerThread;
import akyrion.thepredict.config.DecisionSettings;
import akyrion.thepredict.config.JevSettings;
import akyrion.thepredict.domain.CaptureContext;
import akyrion.thepredict.domain.ChoiceId;
import akyrion.thepredict.domain.ChoiceKind;
import akyrion.thepredict.domain.CombatFacts;
import akyrion.thepredict.domain.DecisionRequest;
import akyrion.thepredict.domain.DecisionResult;
import akyrion.thepredict.domain.DecisionTiming;
import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.ExecutionOutcome;
import akyrion.thepredict.domain.HealthBand;
import akyrion.thepredict.domain.HealthThresholds;
import akyrion.thepredict.domain.InstallResult;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.MobProbe;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.MovementSettings;
import akyrion.thepredict.domain.ServiceCheck;
import akyrion.thepredict.domain.ServiceState;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.Tactic;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.domain.ThreatWeights;
import akyrion.thepredict.domain.ThreatTableState;
import akyrion.thepredict.admin.CommandServices;
import akyrion.thepredict.domain.SessionState;
import akyrion.thepredict.engine.DecisionTap;
import akyrion.thepredict.engine.CombatReader;
import akyrion.thepredict.engine.DebugIntents;
import akyrion.thepredict.engine.ResultIntake;
import akyrion.thepredict.engine.StatusReport;
import akyrion.thepredict.engine.ContextSignals;
import akyrion.thepredict.engine.DecisionClient;
import akyrion.thepredict.engine.Preparation;
import akyrion.thepredict.engine.PreparedRequest;
import akyrion.thepredict.engine.EngineSettings;
import akyrion.thepredict.engine.ExecutionSettings;
import akyrion.thepredict.engine.IntentBoard;
import akyrion.thepredict.engine.ServerClock;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DecisionServicesTest {
    private static final UUID MOB = new UUID(0x51L, 1);
    private static final UUID WORLD = new UUID(0xA11L, 1);

    private final List<String> info = new ArrayList<>();
    private final IntentBoard board = new IntentBoard();
    private long tick;
    private int checks;
    private CompletableFuture<ServiceCheck> nextCheck = CompletableFuture.completedFuture(
            new ServiceCheck(ServiceState.UNCONFIGURED, "no key"));

    private static TacticalProfile profile() {
        var hold = new TacticId("hold_ground");
        return new TacticalProfile("guardian_ai", "Guardian", "Defeat the target",
                Map.of(hold, new Tactic(hold, Movement.HOLD, Optional.empty(), Optional.empty(), 50)), Map.of(),
                List.of(), new MovementSettings(2.5, 5, 9, 16, 10, 1.2, 1.15, OptionalDouble.empty()),
                new HealthThresholds(0.3, 0.6), new ThreatWeights(1, 1, 1, 0.5, 10), List.of(),
                new DecisionTiming(40, 20, 100, 0.5, 0.2, 1));
    }

    private final CombatReader reader = new CombatReader() {
        @Override
        public Optional<CombatFacts> capture(EncounterId encounter, List<MobKey> members, CaptureContext context) {
            return Optional.empty();
        }

        @Override
        public Optional<MobProbe> probe(MobKey mob) {
            return Optional.of(new MobProbe(mob, WORLD, HealthBand.HEALTHY, Optional.empty(), Optional.empty()));
        }

        @Override
        public boolean withinFollowRange(MobKey mob, MobKey member) {
            return true;
        }

        @Override
        public boolean inCombat(UUID entityId) {
            return false;
        }

        @Override
        public boolean isActive(UUID entityId) {
            return true;
        }

        @Override
        public Optional<ThreatTableState> threatTable(MobKey mob) {
            return Optional.of(ThreatTableState.DISABLED);
        }
    };

    private final MobDirectory mobs = new MobDirectory() {
        @Override
        public Optional<String> mobTypeOf(UUID entityId) {
            return Optional.of("Guardian");
        }

        @Override
        public boolean isActive(UUID entityId) {
            return true;
        }

        @Override
        public List<UUID> activeMobIds() {
            return List.of(MOB);
        }
    };

    private final DecisionClient client = new DecisionClient() {
        @Override
        public Preparation prepare(DecisionRequest request) {
            throw new AssertionError("no decision is expected");
        }

        @Override
        public CompletableFuture<DecisionResult> decide(PreparedRequest request) {
            throw new AssertionError("no decision is expected");
        }

        @Override
        public CompletableFuture<ServiceCheck> check() {
            checks++;
            return nextCheck;
        }

        @Override
        public void close() {
        }
    };

    private DecisionServices services(ServiceState initial) {
        var clock = ServerClock.of(() -> tick, () -> tick * 50_000_000L);
        var sessions = new SessionServices(board, clock, key -> {
        }, mobs, reader, mobId -> Optional.of(profile()), () -> {
        }, new ServerThread(() -> true, Runnable::run, line -> {
        }), line -> {
        }, new NoCombatActions());
        sessions.startScan();
        var check = new ServiceCheck(initial, "start");
        var wiring = new DecisionServices.Wiring(DecisionServices.engineSettings(DecisionSettings.standard(),
                JevSettings.standard()), clock, client, check, board, new ContextSignals(),
                new NoMobControl(), () -> 1, new NoCombatActions(),
                DecisionServices.executionSettings(DecisionSettings.standard(), JevSettings.standard()), () -> 0.5,
                info::add, line -> {
                }, "jev-1.13.0", DecisionTap.NONE);
        return new DecisionServices(sessions, wiring);
    }

    @Test
    void theEngineSettingsTranslateTheDefaultConfiguration() {
        var legacy = new EngineSettings(600, 300, 8, 4, 600_000, 10, 2, 64, 16, 64, 2000, 800_000_000L);
        var translated = DecisionServices.engineSettings(DecisionSettings.standard(), JevSettings.standard());
        assertEquals(legacy.maxTickWorkMicros(), translated.maxTickWorkMicros());
        assertEquals(legacy.requestTimeoutNanos(), translated.requestTimeoutNanos());
        assertEquals(64, legacy.maxSessionsPerTick());
        assertEquals(96, translated.maxSessionsPerTick());
        assertEquals(600, translated.tickLayerMaxMicros());
        assertEquals(akyrion.thepredict.domain.CandidateScoring.PRIORITY, translated.candidateScoring());
        assertFalse(translated.candidateDiversity());
    }

    private static final String CONFIGURED = """
            schemaVersion: 2
            decision:
              maxRequestsPerMinute: 120
            storage:
              flushIntervalTicks: 40
            difficulty:
              disengageTicks: 300
            zone:
              maxConcurrentBuilds: 1
            group:
              mode: 'off'
            patterns:
              minTrials: 9
            memory:
              maxHintsPerTick: 4
            """;

    @Test
    void theRootTranslationCarriesEveryConfiguredSection() throws Exception {
        var yaml = new org.bukkit.configuration.file.YamlConfiguration();
        yaml.loadFromString(CONFIGURED);
        var current = new akyrion.thepredict.config.PluginConfigurationLoader().parse(yaml);
        var translated = DecisionServices.engineSettings(current.decision(), current.jev(), current.storage(),
                current.difficulty(), current.zone(), current.group(), current.patterns(), current.memory());
        assertEquals(120, translated.maxRequestsPerMinute());
        assertEquals(40, translated.flushIntervalTicks());
        assertEquals(DecisionServices.STORAGE_REPLIES_PER_TICK, translated.maxStorageRepliesPerTick());
        assertEquals(current.difficulty(), translated.difficulty());
        assertEquals(300, translated.difficulty().disengageTicks());
        assertEquals(current.zone(), translated.zone());
        assertEquals(1, translated.zone().maxConcurrentBuilds());
        assertEquals(current.group(), translated.group());
        assertEquals(akyrion.thepredict.domain.GroupMode.OFF, translated.group().mode());
        assertEquals(9, translated.patterns().minTrials());
        assertEquals(4, translated.memory().maxHintsPerTick());
    }

    @Test
    void theExecutionSettingsTranslateTheDefaultConfiguration() {
        assertEquals(new ExecutionSettings(1_000_000_000L, 3, JevSettings.DEFAULT_MODEL, false),
                DecisionServices.executionSettings(DecisionSettings.standard(), JevSettings.standard()));
    }

    @Test
    void unavailableCombatActionsNeverActAndNeverThrow() {
        var actions = new NoCombatActions();
        var key = new MobKey(MOB, 0);
        assertEquals(ExecutionOutcome.NOT_ELIGIBLE, actions.cast(key, new SkillChoice(new ChoiceId("strike"),
                ChoiceKind.VANILLA_MELEE, Optional.empty(), Set.of(), true, 0), Movement.APPROACH));
        actions.signalTactic(key, new TacticId("hold_ground"));
        actions.addThreat(key, WORLD, 1.0);
    }

    @Test
    void theCoordinatorRunsTheLifecycleAndAnUnconfiguredServiceIsNeverChecked() {
        var decisions = services(ServiceState.UNCONFIGURED);
        for (tick = 0; tick < 5; tick++) {
            decisions.tick();
        }
        assertEquals(1, board.size());
        assertEquals(0, checks);
        assertEquals(ServiceState.UNCONFIGURED, decisions.service().state());
        var status = decisions.diagnostics().status();
        assertEquals(new StatusReport.Budget(0, 600, 0, 8), status.server());
        assertEquals(new StatusReport.Consumption(0, 600_000), status.consumption());
        assertEquals("jev-1.13.0", status.service().configuredModel());
        assertEquals(1, status.sessions().byState().get(SessionState.ACTIVE));
        assertEquals(0, decisions.cancelRequests());
    }

    @Test
    void aRequestedCheckRunsOnTheNextTickAndReportsItsEnumeratedCause() {
        var decisions = services(ServiceState.UNCONFIGURED);
        decisions.requestCheck();
        assertEquals(ServiceState.CHECKING, decisions.service().state());
        decisions.tick();
        tick++;
        decisions.tick();
        assertEquals(1, checks);
        assertEquals(new ServiceCheck(ServiceState.UNCONFIGURED, "check unconfigured"), decisions.service());
        assertTrue(info.stream().allMatch(line -> line.startsWith("Jev service ")), info.toString());
    }

    @Test
    void withoutGoalOwnershipNoMobIsInstalledAndEveryMobKeepsItsAi() {
        var control = new NoMobControl();
        var key = new MobKey(MOB, 0);
        assertInstanceOf(InstallResult.Unsupported.class, control.ensureInstalled(key, profile()));
        assertTrue(control.describeGoals(key).isEmpty());
        assertTrue(control.progress(key).targetDistanceBlocks().isEmpty());
        control.release(key);
    }

    @Test
    void aCheckFromTheCommandNeverWaitsForAnIncompleteFutureAndReportsOnceItCompletes() {
        var pending = new CompletableFuture<ServiceCheck>();
        nextCheck = pending;
        var decisions = services(ServiceState.CHECKING);
        var results = new ArrayList<ServiceCheck>();
        var commands = new EngineCommands(decisions);
        assertEquals(CommandServices.CheckStart.STARTED, commands.requestCheck(results::add));
        for (tick = 0; tick < 40; tick++) {
            decisions.tick();
        }
        assertTrue(results.isEmpty());
        assertEquals(ServiceState.CHECKING, decisions.service().state());
        pending.complete(new ServiceCheck(ServiceState.READY, "credential valid; model unverified until first inference"));
        decisions.tick();
        assertEquals(List.of(new ServiceCheck(ServiceState.READY,
                "credential valid; model unverified until first inference")), results);
        assertEquals(ServiceState.READY, decisions.service().state());
        decisions.tick();
        assertEquals(1, results.size());
    }

    @Test
    void checkWatchersAreBoundedAndTheCommandSaysSoWhenFull() {
        nextCheck = new CompletableFuture<>();
        var commands = new EngineCommands(services(ServiceState.CHECKING));
        for (var index = 0; index < ResultIntake.MAX_CHECK_WATCHERS; index++) {
            assertEquals(CommandServices.CheckStart.STARTED, commands.requestCheck(check -> {
            }));
        }
        assertEquals(CommandServices.CheckStart.BUSY, commands.requestCheck(check -> {
        }));
    }

    @Test
    void theCommandServicesReportTheSessionAndSetADebugIntentOfItsProfileOnly() {
        var decisions = services(ServiceState.UNCONFIGURED);
        for (tick = 0; tick < 3; tick++) {
            decisions.tick();
        }
        var commands = new EngineCommands(decisions);
        assertTrue(commands.bound(MOB));
        var report = commands.session(MOB).orElseThrow();
        assertEquals("guardian_ai", report.profileId());
        assertEquals(Optional.of(ThreatTableState.DISABLED), report.threatTable());
        assertInstanceOf(DebugIntents.Outcome.Rejected.class, commands.setIntent(MOB, new TacticId("retreat_far")));
        var applied = assertInstanceOf(DebugIntents.Outcome.Applied.class,
                commands.setIntent(MOB, new TacticId("hold_ground"), 60));
        assertEquals(60, applied.durationTicks());
        assertTrue(commands.session(MOB).orElseThrow().intent().orElseThrow().debug());
        assertEquals(1, commands.status().orElseThrow().sessions().debugIntents());
        assertTrue(commands.release(MOB));
        assertTrue(commands.session(MOB).orElseThrow().intent().isEmpty());
        assertTrue(commands.session(new UUID(9, 9)).isEmpty());
    }

    @Test
    void withoutACoordinatorTheCommandServicesReportNothingAndChangeNothing() {
        var commands = new EngineCommands.Unavailable();
        assertTrue(commands.status().isEmpty());
        assertTrue(commands.status(WORLD).isEmpty());
        assertTrue(commands.session(MOB).isEmpty());
        assertTrue(commands.goals(MOB).isEmpty());
        assertTrue(commands.progress(MOB).targetDistanceBlocks().isEmpty());
        assertInstanceOf(DebugIntents.Outcome.Rejected.class, commands.setIntent(MOB, new TacticId("hold_ground")));
        assertEquals(CommandServices.CheckStart.UNAVAILABLE, commands.requestCheck(check -> {
        }));
        assertTrue(!commands.release(MOB) && !commands.bound(MOB));
    }
}
