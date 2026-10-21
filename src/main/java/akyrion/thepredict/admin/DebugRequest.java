package akyrion.thepredict.admin;

import akyrion.thepredict.domain.TacticId;

import java.util.Objects;
import java.util.OptionalInt;

/**
 * One validated {@code /thepredict debug} request (M12.3). Pure data; {@link ThePredictCommand} executes it on the
 * server thread.
 */
public sealed interface DebugRequest permits DebugRequest.SetIntent, DebugRequest.Release, DebugRequest.ListGoals {
    /**
     * Sets a local intent of a tactic of the mob's profile.
     *
     * @param mob selected mob
     * @param tactic tactic of the mob's profile
     * @param durationTicks validity in ticks, from 1 to {@code DebugIntents.MAX_TICKS}; empty for the
     *                      {@code maximumIntentTicks} of the profile
     */
    record SetIntent(MobSelector mob, TacticId tactic, OptionalInt durationTicks) implements DebugRequest {
        /** Validates the components. */
        public SetIntent {
            Objects.requireNonNull(mob, "mob");
            Objects.requireNonNull(tactic, "tactic");
            Objects.requireNonNull(durationTicks, "durationTicks");
        }
    }

    /**
     * Drops the intent of the mob to level 4; the fallback chain decides again on its next cycle.
     *
     * @param mob selected mob
     */
    record Release(MobSelector mob) implements DebugRequest {
        /** Validates the components. */
        public Release {
            Objects.requireNonNull(mob, "mob");
        }
    }

    /**
     * Lists the installed and running goals of the mob and its movement progress.
     *
     * @param mob selected mob
     */
    record ListGoals(MobSelector mob) implements DebugRequest {
        /** Validates the components. */
        public ListGoals {
            Objects.requireNonNull(mob, "mob");
        }
    }
}
