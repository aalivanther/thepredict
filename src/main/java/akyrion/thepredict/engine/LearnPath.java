package akyrion.thepredict.engine;

import java.util.Objects;

/**
 * File names accepted by {@code /thepredict learn}. A name is one segment: no slash, no {@code ..}.
 */
public final class LearnPath {
    private LearnPath() {
    }

    /**
     * Rejects a path that leaves {@code learn/}.
     *
     * @param name the file name
     */
    public static void requireName(String name) {
        Objects.requireNonNull(name, "name");
        if (name.isBlank() || name.contains("..") || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0
                || name.indexOf(':') >= 0) {
            throw new IllegalArgumentException("path");
        }
    }
}
