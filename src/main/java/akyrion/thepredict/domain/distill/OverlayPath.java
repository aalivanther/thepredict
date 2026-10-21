package akyrion.thepredict.domain.distill;

import java.util.Objects;

/**
 * Where an overlay file may be written (M29.8). Only {@code plugins/ThePredict/autoconfig/}. A path with
 * {@code ..} is refused.
 */
public final class OverlayPath {
    private OverlayPath() {
    }

    /**
     * Whether a path may receive an overlay.
     *
     * @param path the path, with {@code /} or {@code \}
     * @return {@code true} when the path stays inside the autoconfig directory
     */
    public static boolean allowed(String path) {
        var normalized = path.replace('\\', '/');
        if (normalized.contains("..")) {
            return false;
        }
        return normalized.contains("plugins/ThePredict/autoconfig/");
    }

    /**
     * Rejects a path outside the autoconfig directory.
     *
     * @param path the path
     * @throws IllegalArgumentException when the path is refused
     */
    public static void require(String path) {
        if (!allowed(Objects.requireNonNull(path, "path"))) {
            throw new IllegalArgumentException("path");
        }
    }
}
