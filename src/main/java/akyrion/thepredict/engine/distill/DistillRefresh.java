package akyrion.thepredict.engine.distill;

import akyrion.thepredict.domain.distill.DistillMode;
import akyrion.thepredict.domain.distill.DistilledPolicy;
import akyrion.thepredict.engine.AggregateCache;

import java.util.function.LongSupplier;

/**
 * Publishes the coverage index when an agreement page arrives between ticks (M29.3).
 */
public final class DistillRefresh {
    private DistillRefresh() {
    }

    /**
     * Listens for agreement pages and refreshes the runtime.
     *
     * @param cache the aggregate cache
     * @param runtime the runtime
     * @param minute minute from {@code ServerClock.nanoTime}
     * @param control the live ceiling
     */
    public static void install(AggregateCache cache, DistillRuntime runtime, LongSupplier minute,
                               DistillControl control) {
        cache.hearAgreements(page -> {
            var policy = runtime.policy().orElseGet(DistilledPolicy::empty);
            var active = control.verdict() != DistillMode.OFF;
            DistillPublish.refresh(runtime, page.rows(), policy, minute.getAsLong(), "", "outcome", active);
        });
    }
}
