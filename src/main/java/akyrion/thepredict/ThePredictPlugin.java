package akyrion.thepredict;

import akyrion.thepredict.adapter.server.binding.MythicProfileBinder;
import akyrion.thepredict.adapter.server.context.CombatCapture;
import akyrion.thepredict.adapter.server.duel.DuelRecorder;
import akyrion.thepredict.adapter.server.events.GroupTargetWatch;
import akyrion.thepredict.adapter.server.events.MemoryPresenceListener;
import akyrion.thepredict.adapter.server.events.ProjectileWatch;
import akyrion.thepredict.adapter.server.events.TickGuard;
import akyrion.thepredict.adapter.server.goals.GoalOwnership;
import akyrion.thepredict.adapter.server.patterns.EnemyWatch;
import akyrion.thepredict.adapter.server.zone.HazardAttribution;
import akyrion.thepredict.adapter.server.zone.ZoneBlockWatch;
import akyrion.thepredict.adapter.server.zone.ZoneDestination;
import akyrion.thepredict.adapter.server.zone.ZoneFlank;
import akyrion.thepredict.adapter.server.zone.ZoneRegistry;
import akyrion.thepredict.adapter.server.zone.ZoneWorker;
import akyrion.thepredict.adapter.storage.ProfileBases;
import akyrion.thepredict.adapter.storage.RecordTap;
import akyrion.thepredict.adapter.storage.StorageService;
import akyrion.thepredict.admin.CommandServices;
import akyrion.thepredict.admin.EvalCommand;
import akyrion.thepredict.admin.MemoryStatus;
import akyrion.thepredict.admin.SetupMessages;
import akyrion.thepredict.admin.SetupReport;
import akyrion.thepredict.admin.ThePredictCommand;
import akyrion.thepredict.config.ConfigLocations;
import akyrion.thepredict.config.InertKeys;
import akyrion.thepredict.config.PluginConfigurationLoader;
import akyrion.thepredict.config.PluginSettings;
import akyrion.thepredict.config.ProfileLoader;
import akyrion.thepredict.domain.HintKind;
import akyrion.thepredict.domain.PatternMode;
import akyrion.thepredict.domain.ServiceCheck;
import akyrion.thepredict.domain.ServiceState;
import akyrion.thepredict.domain.distill.DistilledPolicy;
import akyrion.thepredict.domain.learn.ConfidenceGate;
import akyrion.thepredict.domain.learn.ValueStatDelta;
import akyrion.thepredict.engine.CalibrationBinDelta;
import akyrion.thepredict.engine.CombatActions;
import akyrion.thepredict.engine.ContextSignals;
import akyrion.thepredict.engine.CreditClose;
import akyrion.thepredict.engine.DecisionClient;
import akyrion.thepredict.engine.DecisionMetrics;
import akyrion.thepredict.engine.DuelRecording;
import akyrion.thepredict.engine.GateVerdicts;
import akyrion.thepredict.engine.GroupDirector;
import akyrion.thepredict.engine.HazardLedger;
import akyrion.thepredict.engine.IntentBoard;
import akyrion.thepredict.engine.KinematicBook;
import akyrion.thepredict.engine.MemoryCounters;
import akyrion.thepredict.engine.MemoryHost;
import akyrion.thepredict.engine.MemoryModeLoader;
import akyrion.thepredict.engine.MemoryPrefetchHints;
import akyrion.thepredict.engine.MobControl;
import akyrion.thepredict.engine.MobSession;
import akyrion.thepredict.engine.PatternDiag;
import akyrion.thepredict.engine.DifficultyStats;
import akyrion.thepredict.engine.PatternDirector;
import akyrion.thepredict.engine.PatternInbox;
import akyrion.thepredict.engine.PatternMemoryLink;
import akyrion.thepredict.engine.PatternModeGate;
import akyrion.thepredict.engine.ServerClock;
import akyrion.thepredict.engine.StorageControl;
import akyrion.thepredict.engine.StorageReply;
import akyrion.thepredict.engine.SwitchableDecisionTap;
import akyrion.thepredict.engine.distill.DistillControl;
import akyrion.thepredict.engine.distill.DistillPublish;
import akyrion.thepredict.engine.distill.DistillRefresh;
import akyrion.thepredict.engine.distill.DistillRuntime;
import akyrion.thepredict.engine.distill.DistillStatus;
import akyrion.thepredict.engine.distill.TeachingBoard;
import akyrion.thepredict.engine.learn.LearnStatus;
import akyrion.thepredict.engine.learn.LearnedArtifacts;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Composition root (rule D8): the only class that builds and connects the components, in this order: startup state,
 * catalog, adapters over MythicMobs and Paper, sessions, the Jev client with the coordinator, storage and the learned
 * files, the command, zones, group and patterns, and the engine task. Only the root reads {@code config} and
 * translates it for the other packages (rule D10). One periodic task runs the coordinator (M09), and shutdown follows
 * rule T7. The class is larger than 200 lines because it is the one place that wires everything; every method stays
 * within 30 lines, and the translations live in {@link StorageWiring}, {@link LearningWiring}, {@link VerdictGates},
 * {@link SystemOneWiring} and {@link DecisionServices}.
 */
public final class ThePredictPlugin extends JavaPlugin implements Listener {
    private final IntentBoard intentBoard = new IntentBoard();
    private final ContextSignals contextSignals = new ContextSignals();
    private final SwitchableDecisionTap decisionTap = new SwitchableDecisionTap();
    private final ServerClock clock = ServerClock.of(Bukkit::getCurrentTick, System::nanoTime);
    private final Clock wallClock = Clock.systemUTC();
    private StartupState startup;
    private ConfigLocations locations;
    private ProfileCatalogs catalogs;
    private Optional<SessionServices> sessions = Optional.empty();
    private Optional<DecisionServices> decisions = Optional.empty();
    private Optional<BukkitTask> engineTask = Optional.empty();
    private Optional<DuelRecorder> duels = Optional.empty();
    private Optional<StorageService> records = Optional.empty();
    private Optional<ZoneRegistry> zones = Optional.empty();
    private final DuelWiring.SampleHook duelSamples = new DuelWiring.SampleHook();
    private final GateVerdicts gates = new GateVerdicts();
    private final VerdictGates verdictGates = new VerdictGates();
    private final PatternModeGate patternGate = new PatternModeGate();
    private final MemoryCounters memoryCounters = new MemoryCounters();
    private final MemoryPrefetchHints memoryHints = new MemoryPrefetchHints();
    private MemoryHost memoryHost;
    private Optional<DistillControl> distillControl = Optional.empty();
    private DecisionClient client = DecisionClient.unavailable(new ServiceCheck(ServiceState.UNCONFIGURED, "not started"));
    private ServiceState startState = ServiceState.UNCONFIGURED;

    /** Creates the Bukkit plugin entry point. */
    public ThePredictPlugin() {
    }

    /** Wires every component after MythicMobs has loaded; a failed prerequisite disables the plugin. */
    @Override
    public void onEnable() {
        if (!start()) {
            return;
        }
        var settings = startup.settingsInUse();
        catalogs = createCatalogs(settings);
        var components = new ServerComponents(this, intentBoard, clock);
        components.registerVocabulary(contextSignals);
        var control = components.goals(catalogs::revision, settings.decision().maxWaypointPathsPerTick(),
                settings.decision().maxWaypointPathMicrosPerTick());
        var actions = components.combatActions(entityId -> sessions.flatMap(services -> services.registry()
                .find(entityId)).map(session -> session.profile().movement()), catalogs::binding,
                settings.decision().castThread());
        sessions = enableSessions(components, control, actions, settings.decision().searchRadiusBlocks());
        sessions.ifPresent(services -> services.registry().baseSeed(settings.decision().debugSeed()));
        var initial = startup.settings().map(value -> SystemOneWiring.initialState(value.jev(), System::getenv))
                .orElse(new ServiceCheck(ServiceState.INVALID_CONFIGURATION,
                        PluginConfigurationLoader.FILE + " is invalid"));
        startState = initial.state();
        client = createClient(initial);
        decisions = sessions.flatMap(services -> enableDecisions(services, initial, control, actions));
        records = enableStorage();
        wireStorage();
        wireServer(components);
        startup.configurationProblem().ifPresentOrElse(catalogs::startBlocked, catalogs::start);
        var hazards = wireZones(settings, control);
        decisions.ifPresent(found -> sessions.ifPresent(services -> wireGroup(found, services, settings, hazards)));
        startEngine();
        announce(initial.state());
    }

    /**
     * Shuts down in the order of T7, each step on its own (CAR-18): stops the engine task, cancels the requests in
     * flight, empties {@code IntentBoard}, closes the credit of every session, releases the goals of at most 200
     * sessions, stops a duel recording with at most 2 s for its writer, closes storage with at most 3 s and logs what
     * it left, stops the zone worker and closes the Jev client last.
     */
    @Override
    public void onDisable() {
        var shutdown = new ShutdownSequence(getLogger()::warning);
        shutdown.step("engine task", () -> engineTask.ifPresent(BukkitTask::cancel));
        shutdown.step("requests", () -> decisions.ifPresent(DecisionServices::cancelRequests));
        shutdown.step("intent board", intentBoard::clearAll);
        shutdown.step("credit", this::closeCredit);
        shutdown.step("sessions", () -> sessions.ifPresent(SessionServices::shutdown));
        shutdown.step("duel", () -> duels.ifPresent(DuelRecorder::shutdown));
        shutdown.step("storage", this::closeStorage);
        shutdown.step("zone", () -> zones.ifPresent(ZoneRegistry::closeWorker));
        shutdown.step("jev client", client::close);
    }

    /**
     * Tells an administrator who joins what needs attention: invalid configuration or profiles, a pending migration or
     * a service that is not ready (M12.4).
     *
     * @param event player joining the server
     */
    @EventHandler
    public void onAdministratorJoin(PlayerJoinEvent event) {
        if (event.getPlayer().hasPermission("thepredict.admin")) {
            SetupMessages.joinMessage(setupReport(), serviceState()).ifPresent(event.getPlayer()::sendMessage);
        }
    }

    /** Checks Java, Minecraft and MythicMobs, creates the first-run files and reads the configuration. */
    private boolean start() {
        try {
            var versions = new RuntimeCompatibility().verify(getServer());
            var mythic = getServer().getPluginManager().getPlugin("MythicMobs");
            if (mythic == null) {
                throw new IllegalStateException("MythicMobs must be enabled before ThePredict reads configuration");
            }
            locations = new ConfigLocations(mythic.getDataFolder().toPath(), getDataFolder().toPath());
            startup = StartupState.load(locations, versions, LocalDateTime.now(), getLogger()::info, System::getenv);
            startup.configurationProblem().ifPresent(problem -> getLogger().severe(problem
                    + "; ThePredict stays inactive"));
            startup.settings().ifPresent(settings -> InertKeys.of(settings)
                    .forEach(problem -> getLogger().warning(problem.toString())));
            SystemOneWiring.headerWarning(System::getProperty).ifPresent(getLogger()::warning);
            return true;
        } catch (IOException | RuntimeException | LinkageError exception) {
            getLogger().log(Level.SEVERE, "Startup prerequisite failed: " + exception.getMessage(), exception);
            getServer().getPluginManager().disablePlugin(this);
            return false;
        }
    }

    /** Builds the catalog over {@code AIConfig/Profiles} and {@code autoconfig/active}; nothing is read yet. */
    private ProfileCatalogs createCatalogs(PluginSettings settings) {
        var created = new ProfileCatalogs(new CatalogReloader(new ProfileLoader(settings),
                locations.profilesDirectory(),
                new MythicProfileBinder(ServerComponents.participantDefaults(settings.participants())),
                exception -> getLogger().log(Level.SEVERE, "Profile reload"
                        + " failed unexpectedly; no profile is active until the next successful reload", exception)),
                getLogger(), (task, ticks) -> Bukkit.getScheduler().runTaskLater(this, task, ticks), this::isEnabled,
                () -> sessions.ifPresent(SessionServices::catalogChanged));
        created.useDataFolder(getDataFolder().toPath());
        return created;
    }

    /** Builds the M06 sessions and registers their listeners; on failure bound mobs keep their MythicMobs AI. */
    private Optional<SessionServices> enableSessions(ServerComponents components, MobControl control,
                                                     CombatActions actions, double searchRadiusBlocks) {
        try {
            var reader = components.combatReader(searchRadiusBlocks, catalogs::binding);
            var services = new SessionServices(intentBoard, clock, control::release, components.mobDirectory(),
                    reader, catalogs::profile, catalogs::onMythicReload, components.serverThread(),
                    getLogger()::warning, actions);
            services.listeners().forEach(listener -> getServer().getPluginManager().registerEvents(listener, this));
            if (reader instanceof CombatCapture capture) {
                getServer().getPluginManager().registerEvents(new ProjectileWatch(capture.incoming(),
                        clock::currentTick), this);
                getServer().getPluginManager().registerEvents(new GroupTargetWatch(services.registry(),
                        services.encounters(), services.decisionMetrics()), this);
            }
            return Optional.of(services);
        } catch (RuntimeException | LinkageError exception) {
            getLogger().log(Level.SEVERE, "Sessions could not start; bound mobs keep their MythicMobs AI", exception);
            return Optional.empty();
        }
    }

    /**
     * Creates the Jev client once per process (M08.9): the null client without a valid configuration or a key; its
     * timing lines reach the log only with {@code decision.logDecisions}, and its answered decisions are recorded in
     * {@code scenarios/} only with {@code debug.recordScenarios} (M13.6).
     */
    private DecisionClient createClient(ServiceCheck initial) {
        var decision = startup.settingsInUse().decision();
        Consumer<String> timings = decision.logDecisions() ? getLogger()::info : line -> {
        };
        var recorder = SystemOneWiring.recorder(decision.recordScenarios(), getDataFolder().toPath(),
                getLogger()::warning);
        return startup.settings().map(value -> SystemOneWiring.createForStart(initial, value.jev(), System::getenv,
                        getPluginMeta().getVersion(), clock, timings, recorder))
                .orElseGet(() -> DecisionClient.unavailable(initial));
    }

    /** Composes the coordinator (M09.7); on failure the sessions keep only their lifecycle. */
    private Optional<DecisionServices> enableDecisions(SessionServices services, ServiceCheck initial,
                                                       MobControl control, CombatActions actions) {
        try {
            var current = startup.settingsInUse();
            return Optional.of(new DecisionServices(services, new DecisionServices.Wiring(
                    DecisionServices.engineSettings(current.decision(), current.jev(), current.storage(),
                            current.difficulty(), current.zone(), current.group(), current.patterns(),
                            current.memory()), clock, client,
                    initial,
                    intentBoard, contextSignals, control, catalogs::revision, actions,
                    DecisionServices.executionSettings(current.decision(), current.jev()),
                    () -> ThreadLocalRandom.current().nextDouble(), getLogger()::info, getLogger()::warning,
                    current.jev().modelLabel(), decisionTap)));
        } catch (RuntimeException exception) {
            getLogger().log(Level.SEVERE, "The coordinator could not start; bound mobs keep their MythicMobs AI",
                    exception);
            return Optional.empty();
        }
    }

    /**
     * Opens records and aggregates after the coordinator exists. Both off, or an invalid {@code config.yml}, start no
     * daemon (CAR-19).
     */
    private Optional<StorageService> enableStorage() {
        if (decisions.isEmpty()) {
            return Optional.empty();
        }
        try {
            var current = startup.settingsInUse();
            var idle = StorageWiring.idleWord(startup.configurationProblem());
            if (idle.isPresent()) {
                return Optional.of(StorageService.idle(idle.get()));
            }
            var services = decisions.get();
            var config = locations.masterFile();
            var yaml = Files.exists(config) ? Files.readString(config) : "";
            var spec = StorageWiring.spec(getDataFolder().toPath(), current, yaml, getPluginMeta().getVersion(),
                    wallClock);
            var storage = current.storage();
            return Optional.of(StorageService.open(spec, storage.level(), storage.bodies(),
                    current.decision().questions().questionDetail(), storage.answerCache(), services.inbox(),
                    services.aggregates(), services.answers()::clear, clock::currentTick));
        } catch (RuntimeException | IOException exception) {
            getLogger().log(Level.SEVERE, "Storage could not start; records and aggregates stay off", exception);
            return Optional.empty();
        }
    }

    /**
     * Attaches the memory host, the verdict listener, the learned files, the writer, the records tap and the storage
     * step. The answer cache learns the real model here.
     */
    private void wireStorage() {
        if (decisions.isEmpty()) {
            return;
        }
        var services = decisions.get();
        var current = startup.settingsInUse();
        var learning = new LearningWiring(LearningWiring.folder(getDataFolder().toPath(), current.storage()),
                getLogger()::warning);
        memoryHost = createMemoryHost(services, current);
        MemoryModeLoader.startupWarning(current.memory()).ifPresent(getLogger()::warning);
        LearnedArtifacts.startupWarning(current.learning().allowUnverified(), learning.valueArtifact())
                .ifPresent(getLogger()::warning);
        services.aggregates().hearVerdicts(loaded -> hearVerdicts(loaded, services, current, learning));
        services.aggregates().useVerdicts(gates, akyrion.thepredict.adapter.storage.VerdictFiles.MAX_FILES);
        wireLearning(services, current, learning);
        current.jev().ignoredKeyWarning().ifPresent(getLogger()::warning);
        installCalibrator(services, current, learning);
        wireEncounters(services, current);
        records.ifPresent(service -> wireRecords(service, services));
        services.coordinator().useStorage((tick, nanos) -> {
            services.aggregates().onTick(tick, nanos);
            records.ifPresent(service -> service.expire(nanos));
        });
        services.coordinator().useMemoryHints(() -> memoryHost.drain(
                memoryHints.drain(current.memory().maxHintsPerTick()), clock.nanoTime()));
        services.diagnostics().hearStorage(() -> records.map(StorageService::status));
        services.diagnostics().hearAggregates(services.aggregates());
        services.answers().noteContext(catalogs.revision(), current.jev().model(),
                current.decision().questions().questionDetail().name().toLowerCase(Locale.ROOT));
    }

    private MemoryHost createMemoryHost(DecisionServices services, PluginSettings current) {
        return new MemoryHost(current.memory(), memoryCounters, services.aggregates(), decisionTap,
                new MemoryHost.Presence() {
                    @Override
                    public boolean online(UUID subject) {
                        return Bukkit.getPlayer(subject) != null;
                    }

                    @Override
                    public boolean exempt(UUID subject) {
                        var player = Bukkit.getPlayer(subject);
                        return player != null && player.hasPermission("thepredict.memory.exempt");
                    }
                }, wallClock::millis);
    }

    /**
     * Applies a load of gate verdicts (W8): the memory mode, the pattern mode and the calibrator each take the highest
     * state their verdicts allow (CAR-02, CAR-07, LD-17).
     */
    private void hearVerdicts(StorageReply.VerdictsLoaded loaded, DecisionServices services, PluginSettings current,
                              LearningWiring learning) {
        gates.load(loaded.rows());
        verdictGates.load(loaded.rows());
        verdictGates.offerMemory(memoryHost.loader(), current.memory()).ifPresent(getLogger()::warning);
        verdictGates.offerPatterns(patternGate, current.patterns()).ifPresent(getLogger()::warning);
        installCalibrator(services, current, learning);
    }

    /** Joins learning and distillation (M28, M29) with the ceiling of the distilled artifact in force. */
    private void wireLearning(DecisionServices services, PluginSettings current, LearningWiring learning) {
        var teaching = new TeachingBoard();
        sessions.ifPresent(found -> {
            found.encounters().useLearning(current.learning(), services.aggregates()::acceptValues, wallClock::millis);
            found.registry().difficultyStats(new DifficultyStats(services.aggregates()::acceptDifficulty,
                    wallClock::millis));
            found.encounters().useTeaching(teaching, services.aggregates()::acceptChoices);
            found.encounters().useAgreements(services.aggregates()::acceptAgreements);
        });
        services.coordinator().useLearning(current.learning());
        services.coordinator().useDistill(current.distill(), teaching);
        var distillRuntime = new DistillRuntime();
        services.coordinator().useRuntime(distillRuntime);
        var control = new DistillControl(current.distill().mode(),
                learning.distillCeiling(current.distill().maxArtifactBytes()));
        services.coordinator().useControl(control);
        distillControl = Optional.of(control);
        DistillPublish.refresh(distillRuntime, List.of(), DistilledPolicy.empty(), clock.nanoTime() / 60_000_000_000L,
                "", "outcome", false);
        DistillRefresh.install(services.aggregates(), distillRuntime, () -> clock.nanoTime() / 60_000_000_000L,
                control);
        distillRuntime.notePackaged(learning.packaged(getClass().getClassLoader()));
        records.ifPresent(service -> service.useDistill(control));
        services.onCoverageStale(distillRuntime::markModelStale);
    }

    /**
     * With {@code decision.confidenceGate: calibrated}, installs the calibrator for the configured question detail and
     * minimum (CAR-20, LD-17): the identity until a {@code PASS} for the artifact arrives.
     */
    private void installCalibrator(DecisionServices services, PluginSettings current, LearningWiring learning) {
        var decision = current.decision();
        if (decision.confidenceGate() != ConfidenceGate.CALIBRATED) {
            return;
        }
        var model = current.jev().model();
        var key = LearningWiring.calibrationKey(model, decision.questions().questionDetail());
        var installed = learning.calibrator(model, key, gates);
        services.coordinator().useCalibrator(decision.confidenceGate(), installed.calibrator(), key, model,
                LearningWiring.calibrationTarget(decision.calibratedMinimum()));
    }

    /** Joins the memory host to arrivals and closes, and the calibration bins to the aggregates. */
    private void wireEncounters(DecisionServices services, PluginSettings current) {
        sessions.ifPresent(found -> {
            found.encounters().hearArrivals((encounter, subject) -> {
                if (Bukkit.getPlayer(subject) != null) {
                    memoryHost.admit(encounter, subject, wallClock.millis() / 86_400_000L);
                }
            });
            found.encounters().hearCloses(encounter -> memoryHost.closeEncounter(encounter, clock.currentTick(),
                    encounter.groupMode(current.group().mode())));
            found.outcomes().hearBins((profile, delta) -> services.aggregates().noteBin(new CalibrationBinDelta(
                    delta.model(), delta.questionDetail(), delta.target(), delta.bin(), delta.n(),
                    delta.confidenceSum(), delta.successSum(), wallClock.millis())));
        });
    }

    /**
     * Joins the storage service: memory resets, the store, the admin results (an applied or reverted overlay reloads
     * the catalog, CAR-06) and the records tap.
     */
    private void wireRecords(StorageService service, DecisionServices services) {
        catalogs.onRead(read -> service.useProfiles(ProfileBases.of(read.profiles().values(), read.fingerprints())));
        service.onMemoryReset(subject -> {
            try {
                memoryHost.prepareReset(UUID.fromString(subject));
            } catch (IllegalArgumentException ignored) {
                return;
            }
        });
        if (service.aggregatesAttached()) {
            services.store().use(service);
        }
        services.aggregates().hearAdmin(result -> {
            service.deliver(result);
            if (ProfileCatalogs.changesOverlay(result.ok(), result.detail())) {
                catalogs.reloadAfterOverlay();
            }
        });
        service.recordTap().ifPresent(tap -> wireTap(tap, services));
    }

    private void wireTap(RecordTap tap, DecisionServices services) {
        decisionTap.fixRecords(tap, exception -> {
            tap.fail(exception);
            getLogger().warning("Records stopped: " + exception.getClass().getSimpleName());
        });
        services.upkeep().hear(tap);
        sessions.ifPresent(found -> {
            found.encounters().hear(tap);
            found.outcomes().hearAggregate((profile, summary) -> services.aggregates().outcomeClosed(profile,
                    summary, wallClock.millis()));
        });
    }

    /** Registers the tick guard, the duel recorder, the command and the join and presence listeners. */
    private void wireServer(ServerComponents components) {
        decisions.ifPresent(services -> getServer().getPluginManager().registerEvents(new TickGuard(
                services::guardMspt, () -> getServer().getAverageTickTime(), services::noteGuard), this));
        duels = DuelWiring.recorder(this, clock, intentBoard, decisionTap, catalogs, components.serverThread(),
                duelSamples);
        registerCommand();
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new MemoryPresenceListener(memoryHints), this);
        for (var player : getServer().getOnlinePlayers()) {
            memoryHints.offer(player.getUniqueId(), HintKind.JOIN, true);
        }
    }

    /** Creates the zone registry (M24), its block watch, hazard attribution and the destination and flank guards. */
    private HazardAttribution wireZones(PluginSettings settings, MobControl control) {
        var zoneCaps = settings.zone();
        var metrics = sessions.map(SessionServices::decisionMetrics)
                .orElseGet(() -> new DecisionMetrics(clock::currentTick));
        var registry = new ZoneRegistry(new ZoneWorker(zoneCaps.maxConcurrentBuilds()), System::nanoTime, zoneCaps,
                metrics);
        zones = Optional.of(registry);
        getServer().getPluginManager().registerEvents(new ZoneBlockWatch(registry, Bukkit::getCurrentTick), this);
        var hazards = new HazardAttribution(new HazardLedger(), decisionTap, id -> sessions.flatMap(found ->
                found.registry().find(id)).map(MobSession::key), Bukkit::getCurrentTick,
                zoneCaps.pushAttributionTicks());
        getServer().getPluginManager().registerEvents(hazards, this);
        sessions.ifPresent(found -> {
            found.encounters().useZone(registry);
            if (found.reader() instanceof CombatCapture capture) {
                capture.useZone(registry);
            }
        });
        if (control instanceof GoalOwnership ownership) {
            ownership.useDestination((entityId, behaviour, point, blockX, blockZ) -> ZoneDestination.ask(registry,
                    id -> sessions.flatMap(found -> found.registry().find(id)), entityId, behaviour, point, blockX,
                    blockZ));
            ownership.useFlank((entityId, point) -> ZoneFlank.allows(registry,
                    id -> sessions.flatMap(found -> found.registry().find(id)), entityId, point));
        }
        return hazards;
    }

    /**
     * Joins zone and hazards to the coordinator, creates the group director with the difficulty section in use
     * (EFO-12) and, with the combat capture, the pattern step.
     */
    private void wireGroup(DecisionServices found, SessionServices services, PluginSettings settings,
                           HazardAttribution hazards) {
        zones.ifPresent(registry -> found.coordinator().useZone(registry));
        found.useHazard(hazards::acceptedCast);
        var director = new GroupDirector(services.encounters(), clock, services.decisionMetrics(), settings.group(),
                services.agenda());
        director.useDifficulty(settings.difficulty());
        found.coordinator().useGroup(director);
        services.hearGroup(director);
        if (services.reader() instanceof CombatCapture capture) {
            wirePatterns(found, services, capture, settings);
        }
    }

    /**
     * The pattern step (M26): the director exists before memory and encounter closes are joined to it (B1), and the
     * gate starts at observe until a verdict arrives (CAR-02).
     */
    private void wirePatterns(DecisionServices found, SessionServices services, CombatCapture capture,
                              PluginSettings settings) {
        var book = new KinematicBook();
        var limits = settings.patterns();
        capture.useKinematics(book, limits.strafeBlocks());
        var inbox = new PatternInbox();
        patternGate.offer(limits.mode(), false);
        var diag = new PatternDiag();
        var watch = new EnemyWatch(inbox, clock::currentTick);
        watch.useMember(id -> services.registry().find(id).flatMap(MobSession::encounter).isPresent());
        watch.useAir(limits.airSwings());
        watch.useDiag(diag);
        getServer().getPluginManager().registerEvents(watch, this);
        var patterns = new PatternDirector(inbox, book, limits, patternGate, clock::nanoTime);
        patterns.bind(services.registry(), services.encounters(), services.decisionMetrics());
        patterns.useReader(capture);
        patterns.useTap(decisionTap);
        services.hearPatterns(patterns);
        patterns.useDiag(diag);
        patterns.useStats(found.aggregates()::acceptPatterns, wallClock::millis);
        getServer().getPluginManager().registerEvent(PlayerQuitEvent.class, this, EventPriority.MONITOR,
                (listener, event) -> patterns.forget(((PlayerQuitEvent) event).getPlayer().getUniqueId()), this);
        PatternMemoryLink.attach(patterns, memoryHost, settings.memory().minFightTrials(), services.encounters());
        capture.usePatternFacts(key -> services.registry().find(key.entityId()).map(MobSession::admittedPatterns)
                .orElse(Set.of()));
        capture.useFactCap(limits.maxFactsPerParticipant());
        capture.useRequestFactCap(limits.maxFactsPerRequest());
        if (patternGate.effective() != PatternMode.OFF) {
            found.coordinator().usePatterns(patterns);
        }
        found.diagnostics().hearPatterns(patternGate);
        found.diagnostics().hearPatternDirector(patterns);
    }

    /** Registers {@code /thepredict} with its tab completion (M12). */
    private void registerCommand() {
        CommandServices services = decisions.<CommandServices>map(EngineCommands::new)
                .orElseGet(EngineCommands.Unavailable::new);
        var current = startup.settingsInUse();
        var command = new ThePredictCommand(this::setupReport, services,
                duels.<DuelRecording>map(recorder -> recorder).orElse(DuelRecording.UNAVAILABLE), DuelWiring::world,
                () -> List.copyOf(catalogs.boundProfiles()),
                records.<StorageControl>map(service -> service).orElse(StorageControl.NONE));
        command.useMemoryStatus(() -> MemoryStatus.lines(memoryCounters));
        command.useLearnStatus(() -> LearnStatus.line(current.learning()));
        distillControl.ifPresent(control -> command.useDistillStatus(
                () -> DistillStatus.line(control, current.distill().mode())));
        command.useDefaultDifficulty(current.difficulty());
        command.useVerdicts(() -> verdictGates.lines(EvalCommand.MAX_VERDICT_LINES));
        var registered = Objects.requireNonNull(getCommand("thepredict"), "thepredict command");
        registered.setExecutor(command);
        registered.setTabCompleter(command);
    }

    /**
     * Scans the existing mobs at 32 per tick and starts the one periodic task of the engine, which also runs the
     * samples of a duel recording (nothing without one).
     */
    private void startEngine() {
        sessions.ifPresent(services -> {
            services.startScan();
            Runnable engine = decisions.isPresent() ? decisions.get()::tick : services::tick;
            Runnable work = () -> {
                engine.run();
                duelSamples.run();
            };
            engineTask = Optional.of(Bukkit.getScheduler().runTaskTimer(this, work, 1L, 1L));
        });
    }

    /** Closes the credit of every live session and hands the rows to the aggregates (M28). */
    private void closeCredit() {
        sessions.ifPresent(found -> {
            var rows = new ArrayList<ValueStatDelta>();
            var epoch = wallClock.millis();
            for (var session : found.registry().all()) {
                rows.addAll(CreditClose.shutdown(session, epoch));
            }
            decisions.ifPresent(services -> services.aggregates().acceptValues(rows));
        });
    }

    /** Applies the final batch and closes storage within 3 s; what it could not apply is logged (JS-11). */
    private void closeStorage() {
        records.ifPresent(service -> {
            var batch = decisions.flatMap(found -> found.aggregates().finalBatch());
            var report = batch.isPresent() ? service.close(batch.get(), 3000) : service.close(3000);
            if (!report.finished()) {
                getLogger().warning("Storage did not finish within 3 s; not applied: " + report.unapplied());
            }
        });
    }

    /** Logs the start messages of M12.4 and, while the service is checked, its result with the next step. */
    private void announce(ServiceState initial) {
        SetupMessages.startup(setupReport(), initial).forEach(getLogger()::info);
        if (initial == ServiceState.CHECKING) {
            decisions.ifPresent(services -> services.awaitCheck(check -> getLogger().info(
                    SetupMessages.checkResult(check))));
        }
    }

    private SetupReport setupReport() {
        return startup.report(catalogs.catalog(), catalogs.profilesRead(), catalogs.summary());
    }

    private ServiceState serviceState() {
        return decisions.map(services -> services.service().state()).orElse(startState);
    }
}
