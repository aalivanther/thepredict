package akyrion.thepredict.engine.learn;

import akyrion.thepredict.domain.learn.LearnCause;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Activation state of the learned artifacts (M28.9). Nothing activates without a passing verdict. Loading stays on
 * the previous policy: a pending or rejected candidate never replaces the active artifact. Disable is immediate.
 * Rollback returns the previous artifact with its own verdict, checked again. No automatic removal.
 */
public final class LearnedArtifacts {
    /** Lifecycle of one artifact. */
    public enum State {
        /** Nothing loaded. */
        NONE,
        /** Verdicts are still loading. */
        PENDING_VERDICT,
        /** A passing verdict is in force. */
        ACTIVE,
        /** The file has no passing verdict. */
        UNVERIFIED_ARTIFACT,
        /** Drift or entropy. The artifact stays, with a warning. */
        DEGRADED
    }

    private State state = State.NONE;
    private Slot active = Slot.EMPTY;
    private Slot previous = Slot.EMPTY;
    private String candidate = "";

    /**
     * Creates a holder with nothing loaded.
     */
    public LearnedArtifacts() {
    }

    /**
     * The state.
     *
     * @return the state
     */
    public State state() {
        return state;
    }

    /**
     * Active fingerprint, or empty.
     *
     * @return the sha256
     */
    public String active() {
        return active.sha;
    }

    /**
     * Previous fingerprint, or empty.
     *
     * @return the sha256
     */
    public String previous() {
        return previous.sha;
    }

    /**
     * Marks a candidate waiting for its verdict. Decisions keep the active artifact meanwhile.
     *
     * @param sha256 candidate
     */
    public void pending(String sha256) {
        candidate = sha256;
        state = State.PENDING_VERDICT;
    }

    /**
     * Activates when the verdict passes for the scope. Bench accepts {@code PASS_SIM_ONLY}. Players need {@code PASS}.
     * A refused candidate leaves the active artifact as it was.
     *
     * @param sha256 candidate
     * @param gateVerdict verdict name
     * @param players whether the scope includes players
     * @return the cause when activation is refused, or empty
     */
    public Optional<LearnCause> activate(String sha256, String gateVerdict, boolean players) {
        var slot = new Slot(sha256, gateVerdict, players);
        candidate = "";
        if (!slot.passes()) {
            state = active.sha.isEmpty() ? State.UNVERIFIED_ARTIFACT : State.ACTIVE;
            return Optional.of(LearnCause.UNVERIFIED_ARTIFACT);
        }
        previous = active;
        active = slot;
        state = State.ACTIVE;
        return Optional.empty();
    }

    /**
     * Warns on every startup when unverified artifacts may load. Names the file and its path.
     *
     * @param allowUnverified the learning switch
     * @param artifact active artifact file
     * @return the warning, or empty when the switch is off
     */
    public static Optional<String> startupWarning(boolean allowUnverified, Path artifact) {
        if (!allowUnverified) {
            return Optional.empty();
        }
        return Optional.of("learning.allowUnverified is true; M28 artifact " + artifact.getFileName() + " at "
                + artifact.toAbsolutePath());
    }

    /**
     * Returns to the previous artifact and its own verdict. A verdict that no longer passes leaves the artifact
     * unverified, so it does not act.
     */
    public void rollback() {
        var swapped = previous;
        previous = active;
        active = swapped;
        candidate = "";
        if (active.sha.isEmpty()) {
            state = State.NONE;
        } else {
            state = active.passes() ? State.ACTIVE : State.UNVERIFIED_ARTIFACT;
        }
    }

    /**
     * Drops both artifacts. The next decision uses priority.
     */
    public void disable() {
        active = Slot.EMPTY;
        previous = Slot.EMPTY;
        candidate = "";
        state = State.NONE;
    }

    /**
     * Marks degradation without removing the artifact.
     */
    public void degrade() {
        if (state == State.ACTIVE) {
            state = State.DEGRADED;
        }
    }

    /**
     * Verdict of the active artifact.
     *
     * @return the verdict, or empty
     */
    public String verdict() {
        return active.verdict;
    }

    /**
     * Candidate waiting for its verdict.
     *
     * @return the sha256, or empty
     */
    public String candidate() {
        return candidate;
    }

    private record Slot(String sha, String verdict, boolean players) {
        private static final Slot EMPTY = new Slot("", "", false);

        private boolean passes() {
            return "PASS".equals(verdict) || !players && "PASS_SIM_ONLY".equals(verdict);
        }
    }
}
