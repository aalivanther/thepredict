package akyrion.thepredict.adapter.server.context;

import akyrion.thepredict.domain.ContextId;
import akyrion.thepredict.domain.TacticalProfile;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/**
 * Evaluates the contexts of a profile on a mob (M07.7): a context with native lines is active when every one of
 * its MythicMobs conditions holds; a shortcut context of {@code ContextRule.SHORTCUTS} is computed from the bands
 * without MythicMobs. Each context is evaluated once per capture; a profile has at most
 * {@link #MAX_CONTEXTS} contexts (validated in M03). Server thread only.
 *
 * <p>The condition type is generic so the rule is tested without MythicMobs; the capture passes
 * {@code SkillCondition.evaluateEntity} on the mob. That method dispatches {@code IEntityCondition},
 * {@code ILocationCondition} and {@code ICasterCondition} with the entity and catches its own errors, returning
 * false (javap). A condition that still throws disables only its context for that capture and leaves a cause in
 * the log, at most once every {@link #LOG_INTERVAL_TICKS} ticks with the count of omitted ones (rule C8).
 */
public final class NativeContextEvaluator {
    /** Maximum contexts per profile (M03, M07.7). */
    public static final int MAX_CONTEXTS = 16;
    /** Minimum ticks between two logged condition failures. */
    public static final long LOG_INTERVAL_TICKS = 200;

    private final Consumer<String> warnings;
    private final LongSupplier currentTick;
    private long lastLogTick = Long.MIN_VALUE / 2;
    private long omitted;
    private long failures;

    /**
     * Creates the evaluator.
     *
     * @param warnings receiver of the limited failure messages
     * @param currentTick server tick, for the log limit
     */
    public NativeContextEvaluator(Consumer<String> warnings, LongSupplier currentTick) {
        this.warnings = Objects.requireNonNull(warnings, "warnings");
        this.currentTick = Objects.requireNonNull(currentTick, "currentTick");
    }

    /**
     * Returns the active contexts of a mob.
     *
     * @param <C> condition type
     * @param profile profile of the mob
     * @param conditions converted conditions of a native context, empty when it has none or was disabled
     * @param holds evaluates one condition on the mob
     * @param shortcuts bands the shortcut contexts depend on
     * @return active contexts in declaration order
     */
    public <C> Set<ContextId> active(TacticalProfile profile, Function<ContextId, List<C>> conditions,
                                     Predicate<C> holds, ShortcutFacts shortcuts) {
        var active = new LinkedHashSet<ContextId>();
        for (var context : profile.contexts()) {
            var holding = context.isShortcut() ? shortcuts.holds(context.id())
                    : allHold(context.id(), conditions.apply(context.id()), holds);
            if (holding) {
                active.add(context.id());
            }
        }
        return active;
    }

    /**
     * Counts the conditions that threw.
     *
     * @return failures since start
     */
    public long failures() {
        return failures;
    }

    private <C> boolean allHold(ContextId id, List<C> conditions, Predicate<C> holds) {
        if (conditions.isEmpty()) {
            return false;
        }
        try {
            for (var condition : conditions) {
                if (!holds.test(condition)) {
                    return false;
                }
            }
            return true;
        } catch (RuntimeException | LinkageError exception) {
            failed(id, exception);
            return false;
        }
    }

    private void failed(ContextId id, Throwable exception) {
        failures++;
        var tick = currentTick.getAsLong();
        if (tick - lastLogTick < LOG_INTERVAL_TICKS) {
            omitted++;
            return;
        }
        lastLogTick = tick;
        warnings.accept("Context '" + id.value() + "' is inactive for this capture: a MythicMobs condition failed with "
                + exception.getClass().getSimpleName() + " (" + omitted + " similar failures not shown)");
        omitted = 0;
    }
}
