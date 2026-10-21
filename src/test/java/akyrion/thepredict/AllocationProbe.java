package akyrion.thepredict;

import java.lang.management.ManagementFactory;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Measures heap bytes allocated by the current thread, to prove that hot paths do not allocate (rule C12). */
public final class AllocationProbe {
    /** Calls made before measuring, so class loading and call-site linking are not counted. */
    public static final int WARM_UP_CALLS = 20_000;

    /** Calls measured; one allocation of even 16 bytes per call would add up to megabytes. */
    public static final int MEASURED_CALLS = 200_000;

    /** Tolerance for the measurement itself, far below one allocation per call. */
    public static final long TOLERANCE_BYTES = 64 * 1024;

    private AllocationProbe() {
    }

    /**
     * Runs an operation many times and returns the bytes the current thread allocated meanwhile.
     * Skips the test when the JVM cannot measure thread allocation.
     *
     * @param operation operation under test
     * @return bytes allocated during the measured calls
     */
    public static long bytesAllocatedBy(Runnable operation) {
        var bean = ManagementFactory.getThreadMXBean();
        assumeTrue(bean instanceof com.sun.management.ThreadMXBean);
        var threads = (com.sun.management.ThreadMXBean) bean;
        assumeTrue(threads.isThreadAllocatedMemorySupported());
        threads.setThreadAllocatedMemoryEnabled(true);
        for (var call = 0; call < WARM_UP_CALLS; call++) {
            operation.run();
        }
        var before = threads.getCurrentThreadAllocatedBytes();
        for (var call = 0; call < MEASURED_CALLS; call++) {
            operation.run();
        }
        return threads.getCurrentThreadAllocatedBytes() - before;
    }
}
