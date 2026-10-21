package akyrion.thepredict.engine;

import akyrion.thepredict.domain.DecisionResult;
import akyrion.thepredict.domain.FailureKind;
import akyrion.thepredict.domain.ServiceCheck;
import akyrion.thepredict.domain.ServiceState;

import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;

/**
 * State of the Jev service with a growing pause, {@code retry-after} and a trial period (M09.4, rules A6, A7).
 * Server thread only; it reads no clock, the time comes as a parameter (rule C6).
 *
 * <p>A transient failure pauses for {@code retry-after} or the growing pause of {@link ServicePause}; the end of the pause asks for a check, and a {@code READY} check opens a trial with one
 * request in flight in the whole server until the first answer. Credentials or a model rejected by the service
 * block every request until {@link #requestCheck()} ({@code /thepredict check}) or a restart. A failure produced
 * locally ({@link #LOCAL_FAILURE_DETAILS}) says nothing about the service. Each check has a generation, so an old
 * check never changes the state. Every change of state is logged once
 * with its enumerated cause and never with a detail text (rule C8).
 */
public final class ServiceStatusTracker {
    /** First pause of a streak. */
    public static final long BASE_PAUSE_NANOS = Duration.ofSeconds(1).toNanos();
    /** Longest calculated pause, also the pause after an {@code INVALID_REQUEST}. */
    public static final long MAX_PAUSE_NANOS = Duration.ofSeconds(60).toNanos();
    /** Consecutive timeouts that pause the service. */
    public static final int TIMEOUTS_TO_PAUSE = 2;
    /** Consecutive invalid responses that pause the service. */
    public static final int INVALID_RESPONSES_TO_PAUSE = 3;
    /** Details of the failures the client produces itself (M08): cancellation, a full queue and a closed client. */
    public static final Set<String> LOCAL_FAILURE_DETAILS = Set.of("cancelled", "client queue is full", "client closed");

    private final long checkTimeoutNanos;
    private final ServicePause pause;
    private final Consumer<String> log;
    private ServiceState state;
    private String cause;
    private int timeoutStreak;
    private int invalidStreak;
    private boolean trial;
    private long generation;
    private boolean checkOutstanding;
    private long checkStartedNanos;

    /**
     * Creates the tracker.
     *
     * @param initial {@code UNCONFIGURED} or {@code INVALID_CONFIGURATION} to never contact the service; any other
     *                state starts {@code CHECKING}
     * @param checkTimeoutNanos time after which a check without result is started again
     * @param random uniform source in [0, 1) for the pause factor; injected for tests
     * @param log receiver of one line per change of state
     */
    public ServiceStatusTracker(ServiceCheck initial, long checkTimeoutNanos, DoubleSupplier random, Consumer<String> log) {
        this.checkTimeoutNanos = checkTimeoutNanos;
        this.pause = new ServicePause(random);
        this.log = Objects.requireNonNull(log, "log");
        var start = Objects.requireNonNull(initial, "initial").state();
        state = start == ServiceState.UNCONFIGURED || start == ServiceState.INVALID_CONFIGURATION ? start
                : ServiceState.CHECKING;
        cause = state == ServiceState.CHECKING ? "start" : start.name().toLowerCase(Locale.ROOT);
    }

    /**
     * Returns the current state and the enumerated cause of the last change, for {@code /thepredict status}.
     *
     * @return state and cause, never a detail from the service
     */
    public ServiceCheck status() {
        return new ServiceCheck(state, cause);
    }

    /**
     * Returns the current state without building a report, for the fallback chain of each probe (M10.6).
     *
     * @return current state
     */
    public ServiceState state() {
        return state;
    }

    /**
     * Returns the time left in the current pause, for {@code /thepredict status} (M12.1).
     *
     * @param nowNanos monotonic time now
     * @return nanoseconds until the pause ends, empty unless the state is {@code PAUSED}
     */
    public OptionalLong pauseRemainingNanos(long nowNanos) {
        return state == ServiceState.PAUSED ? OptionalLong.of(pause.remainingNanos(nowNanos)) : OptionalLong.empty();
    }

    /**
     * Tells whether a request may be sent now.
     *
     * @param inFlight requests in flight of the whole server
     * @return {@code true} only in {@code READY}; during the trial only while nothing is in flight
     */
    public boolean allowsDispatch(int inFlight) {
        return state == ServiceState.READY && (!trial || inFlight == 0);
    }

    /**
     * Tells whether the trial period after a check is still open.
     *
     * @return {@code true} until the first answer after a {@code READY} check
     */
    public boolean inTrial() {
        return trial;
    }

    /**
     * Ends a finished pause and starts a check when one is due: in {@code CHECKING} without a check in progress,
     * or when the check in progress took longer than the check timeout.
     *
     * @param nowNanos monotonic time now
     * @return the generation of the check to start, empty when none is due
     */
    public OptionalLong checkToStart(long nowNanos) {
        if (state == ServiceState.PAUSED && pause.over(nowNanos)) {
            change(ServiceState.CHECKING, "pause ended");
        }
        if (state != ServiceState.CHECKING || checkOutstanding && nowNanos - checkStartedNanos < checkTimeoutNanos) {
            return OptionalLong.empty();
        }
        checkOutstanding = true;
        checkStartedNanos = nowNanos;
        return OptionalLong.of(++generation);
    }

    /**
     * Applies a finished check; a check of an older generation, or one arriving outside {@code CHECKING}, is ignored.
     *
     * @param checkGeneration generation returned by {@link #checkToStart}
     * @param check state reported by the client
     * @param nowNanos monotonic time now
     */
    public void onCheck(long checkGeneration, ServiceCheck check, long nowNanos) {
        if (checkGeneration != generation || state != ServiceState.CHECKING) {
            return;
        }
        checkOutstanding = false;
        switch (check.state()) {
            case READY -> {
                trial = true;
                change(ServiceState.READY, "check ready, trial");
            }
            case CHECKING, PAUSED -> pause(nowNanos, "check failed");
            default -> change(check.state(), "check " + check.state().name().toLowerCase(Locale.ROOT));
        }
    }

    /**
     * Applies the result of a request, on time or late (the table of M09.4).
     *
     * @param result answers or typed failure
     * @param nowNanos monotonic time now
     */
    public void onResult(DecisionResult result, long nowNanos) {
        if (blocked()) {
            return;
        }
        switch (result) {
            case DecisionResult.Answered ignored -> answered();
            case DecisionResult.Failed failed when LOCAL_FAILURE_DETAILS.contains(failed.detail()) -> {
            }
            case DecisionResult.Failed failed -> failed(failed, nowNanos);
        }
    }

    /**
     * Starts a new check at once ({@code /thepredict check}, M12); results of the previous generation are ignored.
     */
    public void requestCheck() {
        generation++;
        checkOutstanding = false;
        trial = false;
        change(ServiceState.CHECKING, "check requested");
    }

    private void answered() {
        timeoutStreak = 0;
        invalidStreak = 0;
        if (state == ServiceState.READY) {
            trial = false;
            pause.reset();
        }
    }

    private void failed(DecisionResult.Failed failed, long nowNanos) {
        var kind = failed.kind();
        timeoutStreak = kind == FailureKind.TIMEOUT ? timeoutStreak + 1 : 0;
        invalidStreak = kind == FailureKind.INVALID_RESPONSE ? invalidStreak + 1 : 0;
        var name = kind.name().toLowerCase(Locale.ROOT);
        switch (kind) {
            case RATE_LIMITED, OVERLOADED -> failed.retryAfter().ifPresentOrElse(
                    delay -> pauseFor(nowNanos, delay.toNanos(), name), () -> pause(nowNanos, name));
            case NETWORK, SERVICE_ERROR -> pause(nowNanos, name);
            case TIMEOUT -> pauseAfter(timeoutStreak, TIMEOUTS_TO_PAUSE, nowNanos, name);
            case INVALID_RESPONSE -> pauseAfter(invalidStreak, INVALID_RESPONSES_TO_PAUSE, nowNanos, name);
            case INVALID_REQUEST -> pauseFor(nowNanos, MAX_PAUSE_NANOS, name);
            case AUTHENTICATION -> block(ServiceState.INVALID_CREDENTIALS, name);
            case MODEL_UNAVAILABLE -> block(ServiceState.INVALID_MODEL, name);
        }
    }

    private void pauseAfter(int streak, int threshold, long nowNanos, String name) {
        if (streak >= threshold) {
            pause(nowNanos, name);
        }
    }

    private void pause(long nowNanos, String reason) {
        paused(pause.calculated(nowNanos, state == ServiceState.PAUSED), reason);
    }

    private void pauseFor(long nowNanos, long durationNanos, String reason) {
        paused(pause.fixed(nowNanos, durationNanos, state == ServiceState.PAUSED), reason);
    }

    /** Enters or stays in {@code PAUSED}: no trial, and a check in progress becomes stale. */
    private void paused(long remainingNanos, String reason) {
        trial = false;
        checkOutstanding = false;
        generation++;
        change(ServiceState.PAUSED, reason + ", " + Duration.ofNanos(remainingNanos).toMillis() + " ms");
    }

    private void block(ServiceState blockedState, String reason) {
        trial = false;
        checkOutstanding = false;
        generation++;
        change(blockedState, reason);
    }

    private boolean blocked() {
        return switch (state) {
            case UNCONFIGURED, INVALID_CONFIGURATION, INVALID_CREDENTIALS, INVALID_MODEL -> true;
            case CHECKING, READY, PAUSED -> false;
        };
    }

    private void change(ServiceState next, String reason) {
        cause = reason;
        if (next != state) {
            log.accept("Jev service " + state.name().toLowerCase(Locale.ROOT) + " -> "
                    + next.name().toLowerCase(Locale.ROOT) + " (" + reason + ")");
        }
        state = next;
    }
}
