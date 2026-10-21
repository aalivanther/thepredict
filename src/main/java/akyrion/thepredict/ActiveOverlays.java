package akyrion.thepredict;

import akyrion.thepredict.config.ConfigProblem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The files of {@code autoconfig/active} (M29.8), read one by one: each file is the overlay of one profile, never of
 * every profile. The profile is the {@code profile=} line, or the file name without its extension. An optional
 * {@code base=} line is the {@code baseProfileSha256} the overlay was built on. Every other {@code key=value} line is
 * an overlay key. Hidden files, {@code .tmp} files and lines without {@code =} are skipped. At most
 * {@link #MAX_FILES} files of at most {@link #MAX_BYTES} bytes are read (A5); the rest is reported.
 */
final class ActiveOverlays {
    /** File label cited by the problems of the active overlays. */
    static final String LABEL = "autoconfig/active";
    /** Most active files read on one reload. */
    static final int MAX_FILES = 256;
    /** Largest active file read. */
    static final long MAX_BYTES = 64L * 1024L;

    private static final Read NONE = new Read(List.of(), List.of());

    private ActiveOverlays() {
    }

    /**
     * One active file.
     *
     * @param file file name inside {@code autoconfig/active}
     * @param profileId profile the overlay belongs to
     * @param base fingerprint of the author profile the overlay was built on; empty when the file has none
     * @param keys dotted profile keys of the overlay
     */
    record Overlay(String file, String profileId, Optional<String> base, Map<String, String> keys) {
        /**
         * Copies the keys.
         */
        Overlay {
            Objects.requireNonNull(file, "file");
            Objects.requireNonNull(profileId, "profileId");
            Objects.requireNonNull(base, "base");
            keys = Map.copyOf(keys);
        }
    }

    /**
     * What a read found.
     *
     * @param overlays the overlays in file name order
     * @param problems warnings of the files that could not be read
     */
    record Read(List<Overlay> overlays, List<ConfigProblem> problems) {
        /**
         * Copies the lists.
         */
        Read {
            overlays = List.copyOf(overlays);
            problems = List.copyOf(problems);
        }

        /**
         * Whether there is nothing to apply or report.
         *
         * @return {@code true} without overlays and without problems
         */
        boolean isEmpty() {
            return overlays.isEmpty() && problems.isEmpty();
        }
    }

    /**
     * Reads every active file. A missing directory reads as nothing.
     *
     * @param active the {@code autoconfig/active} directory
     * @return the overlays and the problems; never throws
     */
    static Read read(Path active) {
        try {
            if (!Files.isDirectory(active)) {
                return NONE;
            }
            List<Path> files;
            try (var listing = Files.list(active)) {
                files = listing.filter(ActiveOverlays::candidate).sorted().toList();
            }
            return readAll(files);
        } catch (IOException | RuntimeException exception) {
            return new Read(List.of(), List.of(ConfigProblem.warning(LABEL, "",
                    "OVERLAY_UNREADABLE: the directory could not be read; no overlay is applied")));
        }
    }

    private static Read readAll(List<Path> files) {
        var overlays = new ArrayList<Overlay>();
        var problems = new ArrayList<ConfigProblem>();
        for (var index = 0; index < files.size(); index++) {
            var name = files.get(index).getFileName().toString();
            if (index >= MAX_FILES) {
                problems.add(ConfigProblem.warning(LABEL, "", (files.size() - MAX_FILES)
                        + " more files are not read; at most " + MAX_FILES + " overlays are active"));
                break;
            }
            try {
                if (Files.size(files.get(index)) > MAX_BYTES) {
                    problems.add(ConfigProblem.warning(LABEL + "/" + name, "", "OVERLAY_UNREADABLE: larger than "
                            + MAX_BYTES + " bytes; not applied"));
                } else {
                    overlays.add(parse(name, Files.readString(files.get(index))));
                }
            } catch (IOException | RuntimeException exception) {
                problems.add(ConfigProblem.warning(LABEL + "/" + name, "", "OVERLAY_UNREADABLE: "
                        + exception.getClass().getSimpleName() + "; not applied"));
            }
        }
        return new Read(overlays, problems);
    }

    /**
     * Reads the lines of one active file.
     *
     * @param file file name, which names the profile when no {@code profile=} line does
     * @param text file content
     * @return the overlay
     */
    static Overlay parse(String file, String text) {
        var profile = stem(file);
        var base = Optional.<String>empty();
        var keys = new LinkedHashMap<String, String>();
        for (var line : text.split("\n")) {
            var trimmed = line.trim();
            var cut = trimmed.indexOf('=');
            if (trimmed.startsWith("#") || cut <= 0) {
                continue;
            }
            var key = trimmed.substring(0, cut).trim();
            var value = trimmed.substring(cut + 1).trim();
            switch (key) {
                case "profile" -> profile = value;
                case "base" -> base = value.isEmpty() ? Optional.empty() : Optional.of(value);
                case "pin" -> {
                }
                default -> keys.put(key, value);
            }
        }
        return new Overlay(file, profile, base, keys);
    }

    private static boolean candidate(Path path) {
        var name = path.getFileName().toString();
        return Files.isRegularFile(path) && !name.startsWith(".") && !name.endsWith(".tmp");
    }

    private static String stem(String file) {
        var dot = file.lastIndexOf('.');
        return dot > 0 ? file.substring(0, dot) : file;
    }
}
