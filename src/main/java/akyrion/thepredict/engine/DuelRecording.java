package akyrion.thepredict.engine;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The duel recorder of {@code /thepredict duel} (M18.4; 00 §4, amendment of M18): {@code admin} starts, reads and stops
 * it, and {@code adapter.server.duel.DuelRecorder} implements it, because rule D6 keeps {@code admin} away from the
 * adapters. Server thread only (rule T1). Off by default; nothing is recorded until {@link #start}.
 */
public interface DuelRecording {
    /**
     * Starts a recording. A refused start leaves any recording running untouched.
     *
     * @param request what to record
     * @return the status after the attempt: {@code recording} is {@code false} and {@code stoppedBy} says why when the
     *         recording could not start
     */
    DuelStatus start(DuelRequest request);

    /**
     * Reads the status of the current or last recording.
     *
     * @return the status; a recorder that never recorded answers {@link DuelStatus#IDLE}
     */
    DuelStatus status();

    /**
     * Stops the current recording: the queue is written, then {@code summary.md}, with {@code stopped_by: command}.
     *
     * @return the status after the stop; nothing changes when nothing records
     */
    DuelStatus stop();

    /** A recorder that never records, for a plugin whose recorder could not start. */
    DuelRecording UNAVAILABLE = new DuelRecording() {
        @Override
        public DuelStatus start(DuelRequest request) {
            return DuelStatus.refused(request.label(), "the duel recorder did not start; see the console");
        }

        @Override
        public DuelStatus status() {
            return DuelStatus.IDLE;
        }

        @Override
        public DuelStatus stop() {
            return DuelStatus.IDLE;
        }
    };

    /**
     * What a recording follows.
     *
     * @param label name of the recording, {@link #LABEL}: no slash nor dot, so it never walks out of its folder
     * @param sideA faction written as side {@code red}
     * @param sideB faction written as side {@code blue}, different from {@code sideA} ignoring case
     * @param worldId world of the arena
     * @param centerX centre of the arena
     * @param centerY centre of the arena
     * @param centerZ centre of the arena
     * @param radiusBlocks radius of the arena, in {@code (0, 64]}
     * @param countedSkills MythicMobs skills counted as casts; empty for the default of M18.4
     * @param requester player who asked, told when the recording stops by itself; empty for the console
     */
    record DuelRequest(String label, String sideA, String sideB, UUID worldId, double centerX, double centerY,
                       double centerZ, double radiusBlocks, Set<String> countedSkills, Optional<UUID> requester) {
        /** Pattern of a label. */
        public static final Pattern LABEL = Pattern.compile("[a-z0-9-]{1,40}");
        /** Radius of the arena when the command gives none. */
        public static final double DEFAULT_RADIUS_BLOCKS = 24.0;
        /** Largest radius of the arena. */
        public static final double MAX_RADIUS_BLOCKS = 64.0;

        /**
         * Validates the request and copies the skills.
         *
         * @throws NullPointerException if a component is {@code null}
         * @throws IllegalArgumentException if the label does not match {@link #LABEL}, a side is blank or both sides are
         *         equal, a coordinate is not finite or the radius is out of {@code (0, 64]}
         */
        public DuelRequest {
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(sideA, "sideA");
            Objects.requireNonNull(sideB, "sideB");
            Objects.requireNonNull(worldId, "worldId");
            Objects.requireNonNull(requester, "requester");
            countedSkills = Set.copyOf(Objects.requireNonNull(countedSkills, "countedSkills"));
            if (!LABEL.matcher(label).matches()) {
                throw new IllegalArgumentException("label must match [a-z0-9-]{1,40}, was '" + label + "'");
            }
            if (sideA.isBlank() || sideB.isBlank() || sideA.equalsIgnoreCase(sideB)) {
                throw new IllegalArgumentException("the two factions must be named and different");
            }
            if (!Double.isFinite(centerX) || !Double.isFinite(centerY) || !Double.isFinite(centerZ)) {
                throw new IllegalArgumentException("the centre must be finite");
            }
            if (!(radiusBlocks > 0.0) || radiusBlocks > MAX_RADIUS_BLOCKS) {
                throw new IllegalArgumentException("radius must be in (0, 64], was " + radiusBlocks);
            }
        }
    }

    /**
     * The status of a recording.
     *
     * @param recording whether a recording runs now
     * @param label label of the current or last recording, empty text before the first
     * @param rounds rounds started
     * @param trackedMobs duelists followed now
     * @param rowsDropped rows dropped because the queue was full
     * @param bytesWritten bytes written to the files
     * @param stoppedBy why the last recording stopped or why a start was refused
     * @param directory folder of the files, empty text when none was created
     * @param requests requests sent about the duelists while recording, to watch the cost (rule W7)
     * @param inputTokens input tokens of their answers
     */
    record DuelStatus(boolean recording, String label, int rounds, int trackedMobs, long rowsDropped,
                      long bytesWritten, Optional<String> stoppedBy, String directory, long requests,
                      long inputTokens) {
        /** The status of a recorder that never recorded. */
        public static final DuelStatus IDLE = new DuelStatus(false, "", 0, 0, 0, 0, Optional.empty(), "", 0, 0);

        /**
         * Validates the status.
         *
         * @throws NullPointerException if a component is {@code null}
         * @throws IllegalArgumentException if a count is negative
         */
        public DuelStatus {
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(stoppedBy, "stoppedBy");
            Objects.requireNonNull(directory, "directory");
            if (rounds < 0 || trackedMobs < 0 || rowsDropped < 0 || bytesWritten < 0 || requests < 0
                    || inputTokens < 0) {
                throw new IllegalArgumentException("counts must not be negative");
            }
        }

        /**
         * The status of a refused start.
         *
         * @param label label asked for
         * @param reason why the recording did not start
         * @return a status that does not record
         */
        public static DuelStatus refused(String label, String reason) {
            return new DuelStatus(false, label, 0, 0, 0, 0, Optional.of(reason), "", 0, 0);
        }
    }
}
