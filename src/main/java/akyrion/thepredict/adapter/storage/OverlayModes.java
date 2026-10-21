package akyrion.thepredict.adapter.storage;

import akyrion.thepredict.domain.GroupCaps;
import akyrion.thepredict.domain.GroupMode;
import akyrion.thepredict.domain.PatternMode;
import akyrion.thepredict.domain.ZoneCaps;
import akyrion.thepredict.domain.ZoneMode;
import akyrion.thepredict.domain.distill.DistillMode;
import akyrion.thepredict.domain.distill.DistillSettings;

import java.util.Objects;

/**
 * The global modes of the {@code config.yml} in use that an overlay may never raise (M29.8, N2): {@code zone.mode},
 * {@code group.mode}, {@code distill.mode} and {@code patterns.mode}. The root passes them in the writer specification
 * (JS-20); {@link #DEFAULTS} are the shipped modes. Immutable; any thread.
 *
 * @param zone {@code zone.mode}
 * @param group {@code group.mode}
 * @param distill {@code distill.mode}
 * @param patterns {@code patterns.mode}
 */
public record OverlayModes(ZoneMode zone, GroupMode group, DistillMode distill, PatternMode patterns) {
    /** The modes of a fresh installation. */
    public static final OverlayModes DEFAULTS = new OverlayModes(ZoneCaps.DEFAULT.mode(), GroupCaps.DEFAULT.mode(),
            DistillSettings.DEFAULT.mode(), PatternMode.DEFAULT);

    private static final String ZONE = "Zone.Mode";
    private static final String GROUP = "Group.Mode";
    private static final String DISTILL = "Distill.Mode";
    private static final String PATTERNS = "Patterns.Mode";

    /**
     * Validates the modes.
     *
     * @throws NullPointerException if a mode is {@code null}
     */
    public OverlayModes {
        Objects.requireNonNull(zone, "zone");
        Objects.requireNonNull(group, "group");
        Objects.requireNonNull(distill, "distill");
        Objects.requireNonNull(patterns, "patterns");
    }

    /**
     * Rank of the global mode behind a profile key.
     *
     * @param key dotted profile key, as {@code ProfileKeys} spells it
     * @return the rank, or {@code -1} when the key is not one of the four mode keys
     */
    int globalRank(String key) {
        return switch (key) {
            case ZONE -> zone.rank();
            case GROUP -> group.rank();
            case DISTILL -> distill.rank();
            case PATTERNS -> patterns.rank();
            default -> -1;
        };
    }

    /**
     * Rank of a proposed mode, read as the reload reads it: trimmed and in lower case.
     *
     * @param key dotted profile key
     * @param token the proposed value, already trimmed and in lower case
     * @return the rank, or {@code -1} when the key is not one of the four mode keys
     * @throws IllegalArgumentException when the value is not a mode of that key
     */
    static int valueRank(String key, String token) {
        return switch (key) {
            case ZONE -> ZoneMode.parse(token).rank();
            case GROUP -> GroupMode.parse(token).rank();
            case DISTILL -> DistillMode.parse(token).rank();
            case PATTERNS -> PatternMode.parse(token).rank();
            default -> -1;
        };
    }
}
