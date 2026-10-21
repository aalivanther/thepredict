package akyrion.thepredict.adapter.server.goals;

import java.util.Objects;

/**
 * Pure rule of M05.2: whether a mob needs its goal set installed, from what its {@code owner} marker says.
 * A missing marker means MythicMobs, a skill, a reload or a chunk load replaced the goals; a marker of another
 * class means another ThePredict instance (for example before a plugin reload) installed them; a marker of
 * another revision or generation means the set is stale. A refusal marker of the same revision and generation
 * means the set already failed for these inputs: nothing changes and the caller answers {@code Unsupported}.
 */
public enum InstallDecision {
    /** The installed set is current; nothing changes. */
    NO_OP,
    /** Install the whole set again. */
    INSTALL;

    /**
     * Decides from the marker.
     *
     * @param marker what the {@code owner} key holds
     * @param expectedRevision current catalog revision
     * @param expectedGeneration generation of the session
     * @return {@link #NO_OP} only for an own or refusal marker with the same revision and generation
     */
    public static InstallDecision decide(Marker marker, long expectedRevision, int expectedGeneration) {
        return switch (Objects.requireNonNull(marker, "marker")) {
            case Marker.Missing missing -> INSTALL;
            case Marker.Foreign foreign -> INSTALL;
            case Marker.Owned owned -> owned.revision() == expectedRevision && owned.generation() == expectedGeneration
                    ? NO_OP : INSTALL;
            case Marker.Refused refused -> refused.revision() == expectedRevision
                    && refused.generation() == expectedGeneration ? NO_OP : INSTALL;
        };
    }

    /** What the {@code owner} goal key of a mob holds. */
    public sealed interface Marker permits Marker.Missing, Marker.Foreign, Marker.Owned, Marker.Refused {
        /** No goal has the key. */
        record Missing() implements Marker {
        }

        /** A goal has the key but is not an {@link OwnerMarkerGoal} of this plugin instance. */
        record Foreign() implements Marker {
        }

        /**
         * ThePredict's own marker.
         *
         * @param revision catalog revision stored in the marker
         * @param generation session generation stored in the marker
         * @param goalCount goals of the installed set
         */
        record Owned(long revision, int generation, int goalCount) implements Marker {
        }

        /**
         * ThePredict's refusal marker: the set of this revision and generation could not be installed.
         *
         * @param revision catalog revision stored in the marker
         * @param generation session generation stored in the marker
         * @param reason cause returned as {@code Unsupported}
         */
        record Refused(long revision, int generation, String reason) implements Marker {
        }
    }
}
