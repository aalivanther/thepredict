package akyrion.thepredict.engine.duel;

import akyrion.thepredict.adapter.server.context.Bands;
import akyrion.thepredict.adapter.server.duel.DuelTestKit;
import akyrion.thepredict.domain.ChoiceId;
import akyrion.thepredict.domain.ChoiceKind;
import akyrion.thepredict.domain.ContextId;
import akyrion.thepredict.domain.Delivery;
import akyrion.thepredict.domain.DistanceBand;
import akyrion.thepredict.domain.EnemyAction;
import akyrion.thepredict.domain.EventType;
import akyrion.thepredict.domain.FallbackReason;
import akyrion.thepredict.domain.HealthBand;
import akyrion.thepredict.domain.ParticipantKind;
import akyrion.thepredict.domain.PatternMode;
import akyrion.thepredict.domain.PatternSettings;
import akyrion.thepredict.domain.ServiceCheck;
import akyrion.thepredict.domain.ServiceState;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.engine.Agenda;
import akyrion.thepredict.engine.ContextEvents;
import akyrion.thepredict.engine.ContextSignals;
import akyrion.thepredict.engine.Coordinator;
import akyrion.thepredict.engine.DecisionClient;
import akyrion.thepredict.engine.DecisionDispatcher;
import akyrion.thepredict.engine.DecisionMetrics;
import akyrion.thepredict.engine.DecisionTap;
import akyrion.thepredict.engine.EncounterCapture;
import akyrion.thepredict.engine.EncounterRegistry;
import akyrion.thepredict.engine.EngineSettings;
import akyrion.thepredict.engine.ExecutionSettings;
import akyrion.thepredict.engine.InFlightRegistry;
import akyrion.thepredict.engine.Inbox;
import akyrion.thepredict.engine.InboxEntry;
import akyrion.thepredict.engine.IntentBoard;
import akyrion.thepredict.engine.KinematicBook;
import akyrion.thepredict.engine.OutcomeEvents;
import akyrion.thepredict.engine.OutcomeMetrics;
import akyrion.thepredict.engine.PatternDirector;
import akyrion.thepredict.engine.PatternInbox;
import akyrion.thepredict.engine.PatternModeGate;
import akyrion.thepredict.engine.RequestBudget;
import akyrion.thepredict.engine.ResultIntake;
import akyrion.thepredict.engine.ServiceStatusTracker;
import akyrion.thepredict.engine.SessionLifecycle;
import akyrion.thepredict.engine.SessionMetrics;
import akyrion.thepredict.engine.SessionReconciler;
import akyrion.thepredict.engine.SessionRegistry;
import akyrion.thepredict.engine.SessionUpkeep;
import akyrion.thepredict.engine.TacticalExecution;
import akyrion.thepredict.engine.ThreatEnrichment;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;

/**
 * The real engine of ThePredict over doubles of its ports, for the simulator of M18.7: the sessions, the coordinator,
 * {@code IntentControl}, {@code ReactiveLayer}, the outcome windows and the {@link DecisionTap}, on a clock of 50 ms per
 * tick (rule P2). The doubles are {@link DuelReader}, {@link DuelActions} and {@link DuelControl}. With
 * {@link TickRules#LEGACY} they are the doubles of M18.7, whose bytes the golden files keep: a steady trend, line of sight
 * always, no outcome event, progress always, and every damaging cast deferred while the rival is invulnerable. With
 * {@link TickRules#serverPorts} they behave as on a server (§7.2 of the simulator audit): the trend and the reach of the
 * rival, the line of sight through the pillars, the hits and deaths as outcome and context events, the real progress
 * of the movement goal, and the wait of {@code ImpactTiming}.
 */
final class DuelEngine {
    static final long TICK_NANOS = 50_000_000L;
    static final UUID WORLD = DuelTestKit.WORLD;
    private static final Pattern HEALTH_PERCENT = Pattern.compile("healthpercent\\{p=([<>]=?)([0-9.]+)%\\}( true)?");

    final DuelTestKit.TickClock clock = new DuelTestKit.TickClock();
    final IntentBoard board = new IntentBoard();
    final SessionRegistry sessions;
    final Optional<OracleJev> jev;
    final TickRules rules;
    final Kit kit;
    final Map<UUID, TacticalProfile> profiles = new HashMap<>();
    final Set<UUID> forcedBase = new HashSet<>();
    private final Map<UUID, Long> blindFrom = new HashMap<>();
    private final Coordinator coordinator;
    private final OutcomeEvents outcomes;
    private final ContextEvents contextEvents;
    private final Optional<PatternDirector> patterns;
    private final PatternInbox inbox = new PatternInbox();
    DuelWorld world;
    BiConsumer<DuelWorld.Body, DuelSkill> casts = (body, skill) -> {
    };
    long actionCasts;
    long preparationCasts;

    DuelEngine(DuelSimulator.Mode mode, DecisionTap tap) {
        this(mode, tap, TickRules.LEGACY, Kit.K1);
    }

    DuelEngine(DuelSimulator.Mode mode, DecisionTap tap, TickRules rules, Kit kit) {
        this.rules = rules;
        this.kit = kit;
        var metrics = new SessionMetrics();
        var agenda = new Agenda();
        var encounterRegistry = new EncounterRegistry(agenda, clock, metrics);
        sessions = new SessionRegistry(board, encounterRegistry, agenda);
        var lifecycle = new SessionLifecycle(sessions, board, key -> {
        }, new SessionReconciler(sessions, encounterRegistry, this::alive, metrics), metrics, clock);
        var decisionMetrics = new DecisionMetrics(clock::currentTick);
        var settings = new EngineSettings(2000, 2000, 8, 4, 600_000, 10, 2, 64, 16, 64, 1_000_000, 800_000_000L);
        var reader = new DuelReader(this);
        var actions = new DuelActions(this);
        var control = new DuelControl(this);
        jev = OracleJev.of(mode, kit);
        var client = client(jev);
        var budget = new RequestBudget(settings);
        var tracker = tracker(jev, settings);
        var inbox = new Inbox<InboxEntry>(settings.inboxCapacity());
        var inFlight = new InFlightRegistry();
        var capture = new EncounterCapture(reader, sessions, encounterRegistry, lifecycle, clock, decisionMetrics);
        var threat = new ThreatEnrichment(encounterRegistry, sessions, actions, clock);
        outcomes = new OutcomeEvents(sessions, encounterRegistry, new OutcomeMetrics(), decisionMetrics);
        contextEvents = new ContextEvents(encounterRegistry, sessions, agenda, clock);
        var steps = new TacticalExecution(new TacticalExecution.Parts(sessions, encounterRegistry, agenda, board, reader,
                control, tracker, budget, decisionMetrics, () -> 1L, threat, outcomes, tap), actions,
                new ExecutionSettings(1_000_000_000L, 3, "jev-1.13.0", false), line -> {
                }, settings.fitThresholds());
        var intake = new ResultIntake(inbox, inFlight, budget, tracker, client, decisionMetrics, steps, clock, tap);
        var upkeep = new SessionUpkeep(sessions, encounterRegistry, agenda, board, new ContextSignals(), reader, control,
                () -> 1L, steps, decisionMetrics);
        var dispatcher = new DecisionDispatcher(capture, sessions, budget, tracker, inFlight, client, inbox,
                decisionMetrics, clock, () -> 1L, settings, line -> {
                }, tap);
        coordinator = new Coordinator(settings, clock, intake, upkeep, dispatcher, steps, agenda, lifecycle::tick,
                decisionMetrics, line -> {
                });
        patterns = serverPorts(upkeep, encounterRegistry, decisionMetrics, reader, tap);
    }

    /**
     * What only the ports of a server add to the wiring: the reactions of the tick layer reach the tap, as
     * {@code ThePredictPlugin} wires it when the records are on (they are only told, nothing decides on them), and the
     * pattern step observes.
     */
    private Optional<PatternDirector> serverPorts(SessionUpkeep upkeep, EncounterRegistry encounters,
                                                  DecisionMetrics metrics, DuelReader reader, DecisionTap tap) {
        if (!rules.serverPorts()) {
            return Optional.empty();
        }
        upkeep.hear(tap);
        return patterns(encounters, metrics, reader, tap);
    }

    private static DecisionClient client(Optional<OracleJev> jev) {
        return jev.isPresent() ? jev.get()
                : DecisionClient.unavailable(new ServiceCheck(ServiceState.UNCONFIGURED, "no key"));
    }

    private static ServiceStatusTracker tracker(Optional<OracleJev> jev, EngineSettings settings) {
        return new ServiceStatusTracker(new ServiceCheck(jev.isPresent() ? ServiceState.CHECKING
                : ServiceState.UNCONFIGURED, "start"), settings.watchdogNanos(), () -> 0.5, line -> {
                });
    }

    /**
     * The pattern step of a server (M26): the default mode, {@code OBSERVE}, learns the rival and publishes a forecast
     * without arming any cast, as {@code ThePredictPlugin} wires it.
     */
    private Optional<PatternDirector> patterns(EncounterRegistry encounters, DecisionMetrics metrics, DuelReader reader,
                                               DecisionTap tap) {
        var gate = new PatternModeGate();
        gate.offer(PatternSettings.DEFAULT.mode(), false);
        if (gate.effective() == PatternMode.OFF) {
            return Optional.empty();
        }
        var director = new PatternDirector(inbox, new KinematicBook(), PatternSettings.DEFAULT, gate, clock::nanoTime);
        director.bind(sessions, encounters, metrics);
        director.useReader(reader);
        director.useTap(tap);
        coordinator.usePatterns(director);
        return Optional.of(director);
    }

    /** The pattern step, empty without the ports of a server. */
    Optional<PatternDirector> patterns() {
        return patterns;
    }

    /** Starts a round on a new world; the ThePredict duelists get their sessions. */
    void round(DuelWorld next, BiConsumer<DuelWorld.Body, DuelSkill> skillCasts) {
        world = next;
        casts = skillCasts;
        profiles.clear();
        forcedBase.clear();
        blindFrom.clear();
    }

    void open(DuelWorld.Body body, TacticalProfile profile, boolean forceBase, long blindAtTick) {
        profiles.put(body.id, profile);
        if (forceBase) {
            forcedBase.add(body.id);
        }
        if (blindAtTick >= 0) {
            blindFrom.put(body.id, blindAtTick);
        }
        sessions.open(body.id, profile);
    }

    void close(DuelWorld.Body body) {
        sessions.close(body.id, FallbackReason.SESSION_INVALID);
        board.clear(body.id);
    }

    /** One tick of the engine, then the answers due; the simulator moves the clock. */
    void tick() {
        coordinator.tick();
        jev.ifPresent(oracle -> oracle.deliver(clock.tick));
    }

    /**
     * A hit of the world as the listeners of a server report it (SIM-07): the damage a member dealt and took, as
     * fractions of the victim's maximum health, the {@code MELEE_HIT} of a member on a participant, and the hit a member
     * received as a {@code STRIKE} of its attacker for the pattern step ({@code EnemyWatch}: a hit of an entity is a
     * full strike). Only with {@link TickRules#serverPorts}.
     *
     * @param hit the hit
     */
    void hit(DuelWorld.Hit hit) {
        if (!rules.serverPorts() || hit.lost() <= 0 && hit.damage() <= 0) {
            return;
        }
        var attacker = hit.attacker().id;
        var victim = hit.victim().id;
        var fraction = Math.max(0, hit.lost()) / hit.victim().maxHealth;
        if (outcomes.tracks(attacker)) {
            outcomes.dealt(attacker, victim, fraction, clock.tick);
        }
        if (outcomes.tracksMember(victim)) {
            outcomes.takenFrom(victim, attacker, fraction, hit.victim().maxHealth, clock.tick);
        }
        contextEvents.memberEvent(attacker, victim, ParticipantKind.ENTITY, EventType.MELEE_HIT, false);
        if (patterns.isPresent() && profiles.containsKey(victim)) {
            inbox.admit(attacker);
            if (inbox.participates(attacker)) {
                inbox.offer(attacker, EnemyAction.STRIKE.ordinal(), (int) clock.tick);
            }
        }
    }

    /**
     * A death as the listeners of a server report it: the participant first, then the member. Only with
     * {@link TickRules#serverPorts}.
     *
     * @param body the body that died
     */
    void died(DuelWorld.Body body) {
        if (!rules.serverPorts()) {
            return;
        }
        outcomes.participantDied(WORLD, body.id);
        outcomes.died(body.id, clock.tick);
    }

    TacticalProfile profile(UUID id) {
        return profiles.get(id);
    }

    boolean alive(UUID id) {
        return world != null && profiles.containsKey(id) && body(id).health > 0;
    }

    DuelWorld.Body body(UUID id) {
        return world.red.id.equals(id) ? world.red : world.blue;
    }

    boolean perceives(UUID id) {
        return blindFrom.getOrDefault(id, Long.MAX_VALUE) > clock.tick;
    }

    /** The skill of a choice in the kit of the round. */
    Optional<DuelSkill> skill(SkillChoice choice) {
        return choice.mythicSkillId().flatMap(kit::skill);
    }

    HealthBand health(DuelWorld.Body body, TacticalProfile profile) {
        return Bands.health(Math.max(0, body.health), body.absorption, body.maxHealth, profile.health());
    }

    DistanceBand band(TacticalProfile profile) {
        if (world.gap() <= profile.movement().meleeReachBlocks()) {
            return DistanceBand.MELEE;
        }
        return world.distance() <= profile.movement().rangedReachBlocks() ? DistanceBand.RANGED : DistanceBand.BEYOND;
    }

    /**
     * The native conditions the duel profiles use, evaluated as MythicMobs 5.13.1 does (javap of
     * {@code HealthPercentCondition.checkHealthRange} and {@code RangedDouble}): {@code healthpercent{p=<N%}} or
     * {@code >N%}, with the health without absorption over the maximum, times 100; any other line is false.
     */
    static boolean holds(String line, DuelWorld.Body body) {
        var matcher = HEALTH_PERCENT.matcher(line.strip());
        if (!matcher.matches()) {
            return false;
        }
        var percent = Math.max(0, body.health) / body.maxHealth * 100;
        var bound = Double.parseDouble(matcher.group(2));
        return switch (matcher.group(1)) {
            case "<" -> percent < bound;
            case "<=" -> percent <= bound;
            case ">" -> percent > bound;
            default -> percent >= bound;
        };
    }

    Set<ContextId> contexts(TacticalProfile profile, HealthBand health, Optional<DistanceBand> band,
                            DuelWorld.Body body) {
        var active = new LinkedHashSet<ContextId>();
        for (var context : profile.contexts()) {
            var holds = switch (context.id().value()) {
                case "low_health_phase" -> health == HealthBand.CRITICAL;
                case "attacking_phase" -> band.filter(DistanceBand.MELEE::equals).isPresent();
                case "tracking_enemy" -> band.filter(value -> value != DistanceBand.MELEE).isPresent();
                case "idle" -> band.isEmpty();
                default -> !context.nativeConditions().isEmpty() && context.nativeConditions().stream()
                        .allMatch(line -> holds(line, body));
            };
            if (holds) {
                active.add(context.id());
            }
        }
        return active;
    }

    boolean outOfReach(SkillChoice choice) {
        return outOfReach(choice, world);
    }

    /**
     * Whether the authored reach of a choice does not cover the rival: the gap between the boxes for a melee delivery,
     * the distance between positions otherwise.
     *
     * @param choice the choice
     * @param world the world
     * @return whether the rival is out of reach
     */
    static boolean outOfReach(SkillChoice choice, DuelWorld world) {
        var purpose = choice.purpose().orElse(null);
        if (purpose == null || !purpose.authored()) {
            return false;
        }
        var reach = purpose.effectiveReachBlocks();
        if (reach.isEmpty()) {
            return false;
        }
        var measured = purpose.delivery() == Delivery.MELEE ? world.gap() : world.distance();
        return measured > reach.getAsDouble();
    }

    /** Casts the engine started while a previous wind-up of the same duelist had not landed. */
    long preparationCasts() {
        return preparationCasts;
    }

    /**
     * Choices whose skill is ready now, or within {@code horizon} ticks. Horizon 0 is exactly ready now, so the
     * question set of M18 does not change until a measurement asks for a horizon.
     */
    Set<ChoiceId> ready(TacticalProfile profile, DuelWorld.Body body, int horizon) {
        var ready = new LinkedHashSet<ChoiceId>();
        var until = clock.tick + horizon;
        for (var choice : profile.choices().values()) {
            var skill = skill(choice);
            if (choice.kind() != ChoiceKind.MYTHIC_SKILL || skill.map(value -> body.readyAt(value) <= until)
                    .orElse(true)) {
                ready.add(choice.id());
            }
        }
        return ready;
    }
}
