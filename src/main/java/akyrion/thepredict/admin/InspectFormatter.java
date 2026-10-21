package akyrion.thepredict.admin;

import akyrion.thepredict.domain.ChoiceId;
import akyrion.thepredict.domain.ContextId;
import akyrion.thepredict.domain.EncounterEvent;
import akyrion.thepredict.domain.GoalSummary;
import akyrion.thepredict.domain.MobFacts;
import akyrion.thepredict.domain.MovementProgress;
import akyrion.thepredict.domain.ParticipantFacts;
import akyrion.thepredict.domain.PruneReason;
import akyrion.thepredict.domain.SkillPurpose;
import akyrion.thepredict.domain.SkillRisk;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.ThreatTableState;
import akyrion.thepredict.domain.TickSignals;
import akyrion.thepredict.engine.OutcomeWindow;
import akyrion.thepredict.engine.SessionReport;
import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Formats {@code /thepredict inspect} (M12.2) and {@code /thepredict debug goals} (M12.3). Pure; an inspection has at
 * most fourteen lines (twelve always, plus the next options of an intent and the aggregate line) and a goal listing
 * at most 42, and no line names a player, only aliases (rule I6).
 */
public final class InspectFormatter {
    /** Creates the formatter; it holds no state. */
    public InspectFormatter() {
    }

    /**
     * Formats one session: identity and level, intent, why, perception, the purpose of each skill, target, last events,
     * goals, movement, threat and the last outcome windows (M16.8).
     *
     * @param report report of the session
     * @return the lines
     */
    public List<Component> format(SessionReport report) {
        Objects.requireNonNull(report, "report");
        var lines = new ArrayList<Component>();
        lines.add(AdminText.header("Mob " + AdminText.shortId(report.key().entityId()) + " generation "
                + report.key().generation()));
        lines.add(AdminText.line("Session", report.mythicMobId() + ", profile " + report.profileId() + ", "
                + report.state() + ", level " + report.level() + report.encounter().map(id -> ", encounter "
                + id.value()).orElse(", no encounter")));
        lines.add(report.intent().map(InspectFormatter::intent)
                .orElse(AdminText.line("Intent", "none; level 4 runs the base AI of the goal set")));
        report.intent().ifPresent(intent -> lines.add(AdminText.line("Next options", alternatives(intent))));
        lines.add(AdminText.line("Why", "last fallback " + report.lastFallbackReason().map(Enum::name).orElse("none")
                + "; last cast " + report.lastOutcome().map(Enum::name).orElse("none") + "; streaks low confidence "
                + report.lowConfidenceStreak() + ", no progress " + report.noProgressStreak()));
        lines.add(AdminText.line("Perception", report.perception().map(InspectFormatter::perception)
                .orElse("no capture yet (no perceived target)")));
        lines.add(AdminText.line("Skills", skills(report.purposes()) + pruned(report.pruned())));
        lines.add(AdminText.line("Target", report.targetFacts().map(InspectFormatter::target).orElse("none")
                + "; perceived players " + report.perceivedPlayers() + "; combat " + report.combatSeconds() + " s"));
        lines.add(AdminText.line("Events", report.recentEvents().isEmpty() ? "none" : report.recentEvents().stream()
                .map(InspectFormatter::event).collect(Collectors.joining(", "))));
        lines.add(AdminText.line("Goals", installation(report) + "; " + goals(report.goals())));
        lines.add(AdminText.line("Movement", movement(report.progress())));
        lines.add(AdminText.line("Threat", threat(report)));
        lines.add(AdminText.line("Outcomes", report.outcomes().isEmpty() ? "no closed window yet"
                : report.outcomes().stream().map(InspectFormatter::outcome).collect(Collectors.joining("; "))));
        report.aggregate().ifPresent(text -> lines.add(AdminText.line("Aggregate", text)));
        return List.copyOf(lines);
    }

    /** One closed outcome window, newest last: level, tactic, damage dealt and taken, goal, casts and hits (M16.8). */
    private static String outcome(OutcomeWindow.Summary summary) {
        return summary.level() + " " + summary.tactic().value() + " dealt=" + AdminText.decimal(summary.dealt())
                + " taken=" + AdminText.decimal(summary.taken()) + " reached=" + (summary.reached() ? "yes" : "no")
                + " casts=" + summary.casts() + " hits=" + summary.hits() + (summary.mobDied() ? " mob died" : "")
                + (summary.targetDied() ? " target died" : "");
    }

    /**
     * Lists the goals of a mob, one per line, and its movement progress.
     *
     * @param entityId UUID of the mob, shown in full so it can be copied into a console command
     * @param generation generation of its session, or {@code -1} without a session
     * @param goals goals of the mob
     * @param progress movement progress of the mob
     * @return the lines
     */
    public List<Component> goals(UUID entityId, int generation, List<GoalSummary> goals, MovementProgress progress) {
        var lines = new ArrayList<Component>();
        lines.add(AdminText.header("Goals of " + entityId + (generation < 0 ? " (no session)"
                : " generation " + generation) + ": " + goals.size()));
        for (var goal : goals) {
            lines.add(AdminText.info("  " + goal.key() + " " + goal.types().stream().sorted().toList()
                    + (goal.running() ? " running" : "")));
        }
        lines.add(AdminText.line("Movement", movement(progress)));
        return List.copyOf(lines);
    }

    private static Component intent(SessionReport.Intent intent) {
        return AdminText.line("Intent", intent.tactic().value() + " (" + intent.movement()
                + intent.choice().map(ChoiceId::value).map(choice -> ", choice " + choice).orElse("")
                + "), confidence " + AdminText.decimal(intent.confidence()) + ", " + intent.remainingTicks()
                + " ticks left, choice tried " + (intent.skillConsumed() ? "yes" : "no")
                + (intent.debug() ? " [debug]" : ""));
    }

    private static String alternatives(SessionReport.Intent intent) {
        var alternatives = intent.alternatives().isEmpty() ? "none"
                : intent.alternatives().stream().map(TacticId::value).collect(Collectors.joining(", "));
        var contingencies = intent.contingencies().isEmpty() ? "none" : intent.contingencies().entrySet().stream()
                .map(entry -> entry.getKey() + " -> " + entry.getValue().value()).collect(Collectors.joining(", "));
        return "alternatives " + alternatives + "; contingencies " + contingencies;
    }

    private static String perception(MobFacts facts) {
        var relation = facts.target().map(target -> "target " + target.target() + " " + target.distance() + " "
                + target.trend() + ", reachable " + yes(target.reachable()) + ", visible " + yes(target.visible())
                + ", outside target reach " + yes(target.outsideTargetReach()) + ", flankable "
                + yes(target.flankable()) + ", grouped " + yes(target.grouped())).orElse("no target");
        return "health " + facts.health() + "; " + relation + "; contexts " + sorted(facts.activeContexts().stream()
                .map(ContextId::value)) + "; eligible " + sorted(facts.eligibleTactics().stream().map(TacticId::value))
                + "; out of reach " + sorted(facts.choicesOutOfReach().stream().map(ChoiceId::value)) + "; cues "
                + sorted(facts.cues().stream().map(Enum::name));
    }

    /**
     * One entry per choice: delivery, reach with its measure, radius, wind-up, sight, cooldown class and origin, or
     * {@code untyped} when the choice has no purpose (M15, M19).
     */
    private static String skills(Map<ChoiceId, Optional<SkillPurpose>> purposes) {
        if (purposes.isEmpty()) {
            return "none";
        }
        return purposes.entrySet().stream().map(entry -> entry.getValue().map(purpose -> {
            var reach = purpose.reachBlocks().isPresent() ? " reach " + AdminText.decimal(purpose.reachBlocks()
                    .getAsDouble()) + " " + purpose.reachMeasure() : "";
            var radius = purpose.radiusBlocks().isPresent() ? " radius " + AdminText.decimal(purpose.radiusBlocks()
                    .getAsDouble()) : "";
            var sight = purpose.risks().contains(SkillRisk.NEEDS_LINE_OF_SIGHT) ? " needs sight" : "";
            return entry.getKey().value() + " " + purpose.delivery() + reach + radius + " wind-up "
                    + purpose.windUpTicks() + sight + " cooldown " + purpose.cooldown() + " "
                    + purpose.origin().name().toLowerCase(Locale.ROOT);
        }).orElse(entry.getKey().value() + " untyped")).collect(Collectors.joining(", "));
    }

    /** Options the last pruned question dropped, appended to the skills line so the inspection stays the same length. */
    private static String pruned(Map<TacticId, PruneReason> pruned) {
        if (pruned.isEmpty()) {
            return "";
        }
        return "; pruned " + pruned.entrySet().stream()
                .map(entry -> entry.getKey().value() + " " + entry.getValue().name().toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(", "));
    }

    /** The target participant; an entity with its species (M17.9), such as {@code entity_1 (zombie)}. */
    private static String target(ParticipantFacts participant) {
        return participant.alias() + participant.species().map(species -> " (" + species + ")").orElse("")
                + " health " + participant.health() + ", armor " + participant.armor()
                + ", threat rank " + participant.threatRank() + ", cues "
                + sorted(participant.cues().stream().map(Enum::name));
    }

    private static String event(EncounterEvent event) {
        return event.type() + event.participant().map(alias -> " " + alias).orElse("") + " at tick " + event.tick();
    }

    private static List<String> sorted(Stream<String> values) {
        return values.sorted().toList();
    }

    private static String installation(SessionReport report) {
        if (report.installPending()) {
            return "installation pending";
        }
        return report.installedRevision().isPresent() ? "installed for revision "
                + report.installedRevision().getAsLong() : "not installed (MythicMobs keeps its AI)";
    }

    private static String goals(List<GoalSummary> goals) {
        var running = goals.stream().filter(GoalSummary::running).map(GoalSummary::key).collect(Collectors.joining(", "));
        return goals.size() + " goals, running " + (running.isEmpty() ? "none" : running);
    }

    private static String movement(MovementProgress progress) {
        var distance = progress.targetDistanceBlocks();
        return "movement goal running " + yes(progress.movementGoalRunning()) + ", path " + yes(progress.hasPath())
                + ", path reaches target " + yes(progress.pathReachesTarget()) + ", target "
                + (distance.isPresent() ? AdminText.decimal(distance.getAsDouble()) + " blocks" : "none");
    }

    private static String threat(SessionReport report) {
        var added = "; added by ThePredict in the open windows " + AdminText.decimal(report.threatAddedInWindowPoints());
        if (report.threatTable().isEmpty()) {
            return "table unreadable (mob not active)" + added;
        }
        ThreatTableState table = report.threatTable().get();
        if (!table.enabled()) {
            return "no threat table (Modules.ThreatTable off): enriched threat inactive for this mob" + added;
        }
        return "table on, " + table.entries() + " entries, leader " + AdminText.decimal(table.topThreat())
                + ", total " + AdminText.decimal(table.totalThreat()) + added;
    }

    private static String yes(boolean value) {
        return value ? "yes" : "no";
    }

    /**
     * Decoded fields of one tick reading (M20.2). The layout is not fixed by the plan: one line per field.
     *
     * @param signals packed word
     * @return the lines
     */
    public static List<Component> tick(long signals) {
        var lines = new ArrayList<Component>();
        lines.add(AdminText.header("Tick"));
        lines.add(AdminText.line("Valid", yes(TickSignals.bit(signals, TickSignals.VALID))));
        lines.add(AdminText.line("Target", yes(TickSignals.bit(signals, TickSignals.HAS_TARGET))
                + (TickSignals.bit(signals, TickSignals.TARGET_MISMATCH) ? ", mismatch" : "")));
        lines.add(AdminText.line("Gap", (TickSignals.gapQuarters(signals) / 4.0) + " blocks, vertical "
                + yes(TickSignals.bit(signals, TickSignals.VERTICAL_GAP))));
        lines.add(AdminText.line("Octant", String.valueOf(TickSignals.octant(signals))));
        lines.add(AdminText.line("Facing", String.valueOf(TickSignals.facingError(signals))));
        lines.add(AdminText.line("Hittable", TickSignals.hittableTicks(signals) + " ticks"));
        lines.add(AdminText.line("Shield", yes(TickSignals.bit(signals, TickSignals.TARGET_BLOCKING))));
        lines.add(AdminText.line("Shot", yes(TickSignals.bit(signals, TickSignals.TARGET_SHOT_READY))));
        lines.add(AdminText.line("Recharging", yes(TickSignals.bit(signals, TickSignals.TARGET_ATTACK_RECHARGING))));
        lines.add(AdminText.line("Consuming", yes(TickSignals.bit(signals, TickSignals.TARGET_CONSUMING))));
        lines.add(AdminText.line("Projectile", yes(TickSignals.bit(signals, TickSignals.INCOMING_PROJECTILE))));
        lines.add(AdminText.line("Ground", yes(TickSignals.bit(signals, TickSignals.MOB_ON_GROUND))));
        lines.add(AdminText.line("Staggered", yes(TickSignals.bit(signals, TickSignals.MOB_STAGGERED))));
        lines.add(AdminText.line("Health", TickSignals.mobHealthPercent(signals) + "% / target "
                + TickSignals.targetHealthPercent(signals) + "%"));
        lines.add(AdminText.line("Left", yes(TickSignals.bit(signals, TickSignals.MOB_LEFT_OF_TARGET))));
        return lines;
    }
}
