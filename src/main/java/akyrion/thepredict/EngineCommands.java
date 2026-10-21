package akyrion.thepredict;

import akyrion.thepredict.admin.CommandServices;
import akyrion.thepredict.admin.DifficultyFormatter;
import akyrion.thepredict.domain.GoalSummary;
import akyrion.thepredict.domain.MovementProgress;
import akyrion.thepredict.domain.ServiceCheck;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.engine.DebugIntents;
import akyrion.thepredict.engine.SessionReport;
import akyrion.thepredict.engine.StatusReport;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Adapts the coordinator of {@link DecisionServices} to the {@link CommandServices} of {@code /thepredict} (M12), and
 * provides the variant for a plugin whose coordinator did not start. Built only by {@link ThePredictPlugin} (rule D8);
 * server thread only.
 */
final class EngineCommands implements CommandServices {
    private final DecisionServices decisions;

    EngineCommands(DecisionServices decisions) {
        this.decisions = Objects.requireNonNull(decisions, "decisions");
    }

    @Override
    public Optional<StatusReport> status() {
        return Optional.of(decisions.diagnostics().status());
    }

    @Override
    public Optional<StatusReport> status(UUID worldId) {
        return Optional.of(decisions.diagnostics().status(worldId));
    }

    @Override
    public Optional<SessionReport> session(UUID entityId) {
        return decisions.diagnostics().session(entityId);
    }

    @Override
    public java.util.OptionalLong tickSignals(UUID entityId) {
        return decisions.diagnostics().tickSignals(entityId);
    }

    @Override
    public boolean bound(UUID entityId) {
        return decisions.diagnostics().bound(entityId);
    }

    @Override
    public Optional<String> difficultyView(UUID entityId) {
        return decisions.diagnostics().difficultyView(entityId).map(view -> DifficultyFormatter.admin(view.preset(),
                view.entropy(), view.reactionTicks(), view.adaptSeconds(), view.rung()));
    }

    @Override
    public java.util.List<String> fairnessLines() {
        var diagnostics = decisions.diagnostics();
        var lines = new java.util.ArrayList<String>();
        if (diagnostics.immediateWindUp()) {
            lines.add("short wind-up immediate is loaded");
        }
        var opened = diagnostics.windowOpened();
        var aborted = diagnostics.windowAborted();
        if (opened > 0 && aborted * 50 > opened) {
            lines.add("telegraph self-abort is above 2%");
        }
        return java.util.List.copyOf(lines);
    }

    @Override
    public java.util.List<String> inspectStance(java.util.UUID entityId) {
        return decisions.diagnostics().stanceLines(entityId);
    }

    @Override
    public java.util.List<String> inspectTelegraph(java.util.UUID entityId) {
        return decisions.diagnostics().telegraphLines(entityId);
    }

    @Override
    public java.util.List<String> inspectGroup(java.util.UUID entityId) {
        return decisions.diagnostics().groupLines(entityId);
    }

    @Override
    public java.util.List<String> inspectPatterns(java.util.UUID entityId) {
        return decisions.diagnostics().patternInspect(entityId).map(view -> java.util.List.of(
                akyrion.thepredict.admin.PatternInspect.format(view.tokens(), view.forecast(), view.accuracyPermille(),
                        view.pattern(), view.state(), view.confidenceMillis(), view.trials(), view.streak(),
                        view.resting(), view.quotaUsed(), view.quotaCap(), view.facts())))
                .orElse(java.util.List.of());
    }

    @Override
    public boolean forcePressure(UUID entityId, int rung) {
        return decisions.diagnostics().forcePressure(entityId, rung);
    }

    @Override
    public List<GoalSummary> goals(UUID entityId) {
        return decisions.diagnostics().goals(entityId);
    }

    @Override
    public MovementProgress progress(UUID entityId) {
        return decisions.diagnostics().progress(entityId);
    }

    @Override
    public DebugIntents.Outcome setIntent(UUID entityId, TacticId tactic) {
        return decisions.debugIntents().set(entityId, tactic, decisions.clock().currentTick(),
                decisions.clock().nanoTime());
    }

    @Override
    public DebugIntents.Outcome setIntent(UUID entityId, TacticId tactic, int durationTicks) {
        return decisions.debugIntents().set(entityId, tactic, durationTicks, decisions.clock().currentTick(),
                decisions.clock().nanoTime());
    }

    @Override
    public boolean release(UUID entityId) {
        return decisions.debugIntents().release(entityId, decisions.clock().currentTick());
    }

    @Override
    public CheckStart requestCheck(Consumer<ServiceCheck> result) {
        if (!decisions.awaitCheck(result)) {
            return CheckStart.BUSY;
        }
        decisions.requestCheck();
        return CheckStart.STARTED;
    }

    /** The command of a plugin whose coordinator did not start: nothing to report and nothing to change. */
    static final class Unavailable implements CommandServices {
        @Override
        public Optional<StatusReport> status() {
            return Optional.empty();
        }

        @Override
        public Optional<StatusReport> status(UUID worldId) {
            return Optional.empty();
        }

        @Override
        public Optional<SessionReport> session(UUID entityId) {
            return Optional.empty();
        }

        @Override
        public boolean bound(UUID entityId) {
            return false;
        }

        @Override
        public List<GoalSummary> goals(UUID entityId) {
            return List.of();
        }

        @Override
        public MovementProgress progress(UUID entityId) {
            return new MovementProgress(false, false, false, OptionalDouble.empty());
        }

        @Override
        public DebugIntents.Outcome setIntent(UUID entityId, TacticId tactic) {
            return new DebugIntents.Outcome.Rejected("the coordinator did not start; see the console");
        }

        @Override
        public DebugIntents.Outcome setIntent(UUID entityId, TacticId tactic, int durationTicks) {
            return setIntent(entityId, tactic);
        }

        @Override
        public boolean release(UUID entityId) {
            return false;
        }

        @Override
        public CheckStart requestCheck(Consumer<ServiceCheck> result) {
            return CheckStart.UNAVAILABLE;
        }
    }
}
