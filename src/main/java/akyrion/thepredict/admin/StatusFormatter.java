package akyrion.thepredict.admin;

import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.ServiceState;
import akyrion.thepredict.domain.SessionState;
import akyrion.thepredict.engine.DecisionMetrics;
import akyrion.thepredict.engine.RequestBudget;
import akyrion.thepredict.engine.StatusReport;
import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Formats {@code /thepredict status} (M12.1): nine sections in a fixed order, one line per datum, followed by the
 * counters of the decision path, the context and the enriched threat. Pure; the output has at most
 * {@link #MAX_LINES} lines whatever the number of mobs, and holds no key, header or player name (rules C8, I6).
 */
public final class StatusFormatter {
    /** Public price of Jev 1.13 per million input tokens, in USD, for the estimate: {@link RequestBudget}'s (C13). */
    public static final double USD_PER_MILLION_INPUT_TOKENS = RequestBudget.USD_PER_MILLION_INPUT_TOKENS;

    /** Most lines of one status. */
    public static final int MAX_LINES = 48;

    private static final List<DecisionMetrics.Span> SPANS = List.of(DecisionMetrics.Span.CAPTURE,
            DecisionMetrics.Span.PREPARE, DecisionMetrics.Span.ROUNDTRIP, DecisionMetrics.Span.INBOX_WAIT, DecisionMetrics.Span.APPLY,
            DecisionMetrics.Span.DISPATCH, DecisionMetrics.Span.TICK_WORK);

    /** Creates the formatter; it holds no state. */
    public StatusFormatter() {
    }

    /**
     * Estimates the cost per hour of a token rate: {@code tokens per minute × 60 × price / 1 000 000}.
     *
     * @param inputTokensPerMinute input tokens of the last minute
     * @return estimated USD per hour
     */
    public static double estimatedUsdPerHour(long inputTokensPerMinute) {
        return inputTokensPerMinute * 60.0 * USD_PER_MILLION_INPUT_TOKENS / 1_000_000.0;
    }

    /**
     * Formats the status when the coordinator did not start: the installation sections and a note in the others.
     *
     * @param setup installation facts from the composition root
     * @return the lines
     */
    public List<Component> format(SetupReport setup) {
        var lines = new ArrayList<Component>();
        environment(setup, lines);
        lines.add(AdminText.header("Service"));
        setup.configurationProblem().ifPresent(problem -> lines.add(AdminText.problem("Configuration", problem)));
        lines.add(AdminText.problem("State", "the coordinator did not start; sessions, budget and metrics are"
                + " unavailable. See the console."));
        catalog(setup, lines);
        return List.copyOf(lines);
    }

    /**
     * Formats the whole status.
     *
     * @param setup installation facts from the composition root
     * @param report report of the engine
     * @return the lines
     */
    public List<Component> format(SetupReport setup, StatusReport report) {
        Objects.requireNonNull(report, "report");
        var lines = new ArrayList<Component>();
        environment(setup, lines);
        service(setup, report.service(), lines);
        catalog(setup, lines);
        sessions(report.sessions(), lines);
        encounters(report.encounters(), lines);
        budget(report, lines);
        consumption(report.consumption(), lines);
        latency(report.latency(), lines);
        errors(report, lines);
        counters(report, lines);
        effectiveness(report.effectiveness(), lines);
        report.storage().ifPresent(block -> storage(block, lines));
        report.patterns().ifPresent(block -> lines.add(AdminText.line("Patterns",
                "mode=" + block.mode() + (block.cause().isEmpty() ? "" : " cause=" + block.cause()))));
        return List.copyOf(lines);
    }

    /**
     * Bounds a whole status to {@link #MAX_LINES} lines once the fairness and memory lines are added after the
     * formatter (CAR-23): when there are more, the last kept line says how many are not shown.
     *
     * @param lines every line of one status
     * @return at most {@link #MAX_LINES} lines
     */
    public static List<Component> bounded(List<Component> lines) {
        if (lines.size() <= MAX_LINES) {
            return List.copyOf(lines);
        }
        var kept = new ArrayList<>(lines.subList(0, MAX_LINES - 1));
        kept.add(AdminText.info((lines.size() - MAX_LINES + 1) + " more status lines are not shown"));
        return List.copyOf(kept);
    }

    /**
     * The outcome windows by control level and the use of the contingencies (M16.6, M16.8): one line
     * {@code outcomes: level=JEV windows= dealt= taken= reach= hit_rate= effect=} per level with windows, the means per
     * window in fractions of maximum health, then {@code contingencies: asked= fired= effective=}.
     */
    private static void effectiveness(StatusReport.Effectiveness effectiveness, List<Component> lines) {
        lines.add(AdminText.header("Effectiveness"));
        if (effectiveness.levels().isEmpty()) {
            lines.add(AdminText.line("outcomes", "no closed window yet"));
        }
        for (var level : effectiveness.levels()) {
            lines.add(AdminText.line("outcomes", "level=" + level.level() + " windows=" + level.windows() + " dealt="
                    + AdminText.decimal(level.dealt()) + " taken=" + AdminText.decimal(level.taken()) + " reach="
                    + AdminText.decimal(level.reachRate()) + " hit_rate=" + AdminText.decimal(level.hitRate())
                    + " effect=" + AdminText.decimal(level.effect())));
        }
        lines.add(AdminText.line("contingencies", "asked=" + effectiveness.contingenciesAsked() + " fired="
                + effectiveness.contingenciesFired() + " effective=" + effectiveness.contingenciesEffective()));
    }

    /** Three lines, only when storage was wired (M21.9). */
    private static void storage(akyrion.thepredict.engine.StorageStatus block, List<Component> lines) {
        lines.add(AdminText.header("Storage"));
        lines.add(AdminText.line("State", block.state() + (block.cause().isEmpty() ? "" : " " + block.cause())
                + " records " + block.recordsLevel() + " dropped " + block.droppedEvents() + " inFlight "
                + block.batchesInFlight() + " failed " + block.failedBatches()));
        lines.add(AdminText.line("Store", "sqlite " + block.sqliteVersion() + " synchronous " + block.synchronous()
                + " journal " + block.journalMode() + (block.pausedSize() ? " paused" : "")
                + (block.networkWarning() ? " network" : "")
                + (block.recordsFailure().isEmpty() ? "" : " failure " + block.recordsFailure())));
    }

    private static void environment(SetupReport setup, List<Component> lines) {
        lines.add(AdminText.header("Environment"));
        lines.add(AdminText.line("Versions", "Java " + setup.javaFeature() + " (minimum 25), Minecraft "
                + setup.minecraftVersion() + " (minimum 26.2), MythicMobs " + setup.mythicVersion()));
    }

    private static void service(SetupReport setup, StatusReport.Service service, List<Component> lines) {
        lines.add(AdminText.header("Service"));
        var status = service.status();
        var text = "Jev " + status.state() + " (" + status.detail() + ")" + (service.trial() ? ", trial" : "");
        lines.add(status.state() == ServiceState.READY ? AdminText.line("State", text) : AdminText.problem("State", text));
        setup.configurationProblem().ifPresent(problem -> lines.add(AdminText.problem("Configuration", problem)));
        lines.add(AdminText.line("Model", "configured " + service.configuredModel() + "; last answer "
                + service.lastModel().orElse("none yet")));
        service.pauseRemainingMillis().ifPresent(millis -> lines.add(AdminText.line("Pause",
                "ends in " + (millis + 999) / 1000 + " s")));
    }

    private static void catalog(SetupReport setup, List<Component> lines) {
        lines.add(AdminText.header("Catalog"));
        var summary = setup.catalogSummary();
        lines.add(setup.catalog() == SetupReport.CatalogState.INVALID ? AdminText.problem("Profiles", summary)
                : AdminText.line("Profiles", summary));
        setup.locationNotice().ifPresent(notice -> lines.add(AdminText.problem("Location", notice)));
        lines.add(setup.migrationReport().map(report -> AdminText.problem("Migration pending", "review " + report
                        + " and the .proposed files next to it"))
                .orElse(AdminText.line("Migration", "none pending")));
    }

    private static void sessions(StatusReport.Sessions sessions, List<Component> lines) {
        lines.add(AdminText.header("Sessions"));
        lines.add(AdminText.line("Bound mobs", sessions.byState().get(SessionState.ACTIVE) + " active, "
                + sessions.byState().get(SessionState.SUSPENDED) + " suspended; levels " + levels(sessions.byLevel())
                + "; debug intents " + sessions.debugIntents()));
        lines.add(AdminText.line("Lifecycle", "opened " + sessions.opened() + ", ended " + joined(sessions.ended())
                + "; pending releases " + sessions.pendingReleases() + ", failed releases " + sessions.failedReleases()));
    }

    private static String levels(Map<ControlLevel, Integer> byLevel) {
        return byLevel.entrySet().stream().map(entry -> entry.getKey() + " " + entry.getValue())
                .collect(Collectors.joining(", "));
    }

    private static void encounters(StatusReport.Encounters encounters, List<Component> lines) {
        lines.add(AdminText.header("Encounters"));
        lines.add(AdminText.line("Encounters", encounters.active() + " active, " + encounters.requestInFlight()
                + " with a request in flight, " + encounters.contextPending() + " with pending context; opened "
                + encounters.opened() + ", closed " + encounters.closed() + " (average " + encounters.averageTicks()
                + " ticks)"));
    }

    private static void budget(StatusReport report, List<Component> lines) {
        lines.add(AdminText.header("Budget"));
        lines.add(AdminText.line("Server", budget(report.server())));
        report.world().ifPresent(world -> lines.add(AdminText.line("Your world", budget(world))));
    }

    private static String budget(StatusReport.Budget budget) {
        return budget.requestsLastMinute() + "/" + budget.maxRequestsPerMinute() + " requests in 60 s, "
                + budget.inFlight() + "/" + budget.maxInFlight() + " in flight";
    }

    private static void consumption(StatusReport.Consumption consumption, List<Component> lines) {
        lines.add(AdminText.header("Consumption"));
        lines.add(AdminText.line("Input tokens", consumption.inputTokensLastMinute() + "/"
                + consumption.maxInputTokensPerMinute() + " in 60 s; estimated cost "
                + String.format(Locale.ROOT, "%.4f", estimatedUsdPerHour(consumption.inputTokensLastMinute()))
                + " USD per hour (estimate at " + USD_PER_MILLION_INPUT_TOKENS + " USD per million input tokens)"));
    }

    private static void latency(Map<DecisionMetrics.Span, StatusReport.Latency> latency, List<Component> lines) {
        lines.add(AdminText.header("Latency (p50, p95, p99 of the last two minutes)"));
        for (var span : SPANS) {
            var value = latency.get(span);
            if (value != null) {
                lines.add(AdminText.line(span.name().toLowerCase(Locale.ROOT), AdminText.micros(value.p50Micros())
                        + ", " + AdminText.micros(value.p95Micros()) + ", " + AdminText.micros(value.p99Micros())
                        + "; " + value.samples() + " samples, max " + value.maxMicros() + " us"));
            }
        }
    }

    private static void errors(StatusReport report, List<Component> lines) {
        lines.add(AdminText.header("Errors"));
        lines.add(AdminText.line("Recent failures", recent(report.recentFailures())));
        lines.add(AdminText.line("Recent fallbacks", recent(report.recentFallbacks())));
    }

    private static String recent(List<StatusReport.RecentCause> causes) {
        return causes.isEmpty() ? "none" : causes.stream().map(cause -> cause.cause() + " "
                + AdminText.seconds(cause.ageTicks()) + " s ago").collect(Collectors.joining(", "));
    }

    private static void counters(StatusReport report, List<Component> lines) {
        lines.add(AdminText.header("Counters"));
        for (var group : report.counters()) {
            lines.add(AdminText.line(group.name(), group.values().isEmpty() ? "none" : joined(group.values())));
        }
        var context = report.context();
        lines.add(AdminText.line("Context", "events recorded " + context.recordedEvents() + ", repeated "
                + context.repeatedEvents() + ", pending marks " + context.pendingMarks() + ", member paths "
                + context.memberPaths() + "; predictordecide pending " + context.signalsPending() + ", dropped "
                + context.signalsDropped()));
        var threat = report.threat();
        lines.add(AdminText.line("Threat", "assists " + threat.assists() + ", blocked hits " + threat.blockedHits()
                + ", harass " + threat.rangedHarass() + ", unreachable decays " + threat.decays() + ", limited "
                + threat.limited() + ", failures " + threat.failures() + "; points +"
                + AdminText.decimal(threat.pointsAdded()) + " / -" + AdminText.decimal(threat.pointsRemoved())));
    }

    private static String joined(Map<String, Long> values) {
        return values.entrySet().stream().map(entry -> entry.getKey().toLowerCase(Locale.ROOT) + "=" + entry.getValue())
                .collect(Collectors.joining(" "));
    }
}
