package akyrion.thepredict.adapter.storage;

import akyrion.thepredict.domain.distill.OverlayPath;
import akyrion.thepredict.domain.distill.OverlayStages;
import akyrion.thepredict.domain.distill.ProfileKeys;
import akyrion.thepredict.domain.distill.ProfileOverlay;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * File commands of {@code /thepredict autoconfig} (M29.8). Writes stay under {@code plugins/ThePredict/autoconfig/}.
 * The author file is never written. An applied overlay is the overlay of one profile: the active file starts with
 * {@code profile=<id>} (the {@code profile=} line of the proposal, or its file name without the extension) and
 * {@code base=<baseProfileSha256>}, then the unpinned keys, in the order of the proposal. {@code apply} refuses before it
 * writes, with an {@link OverlayRefusal}, what the reload would refuse: a raise of a global mode, a mode value that is
 * not a mode, a file above {@link #MAX_BYTES}, a proposal without a base and, once the root handed the profiles read
 * now, a profile that does not exist, an older base or a key that does not apply.
 */
public final class AutoconfigAdmin {
    /** Largest proposal read and largest active file written, the cap the reload reads (A5). */
    public static final int MAX_BYTES = 64 * 1024;

    private static final String NOTHING = "; nothing was written";

    private AutoconfigAdmin() {
    }

    /**
     * Copies a proposal into {@code inbox}.
     *
     * @param path the proposal path
     * @return the note
     * @throws IOException when the path is refused or the copy fails
     */
    public static String copyIn(String path) throws IOException {
        var proposal = file(path);
        var inbox = autoconfig(proposal).resolve("inbox");
        Files.createDirectories(inbox);
        var target = inbox.resolve(proposal.getFileName());
        Files.copy(proposal, target, StandardCopyOption.REPLACE_EXISTING);
        return "imported " + proposal.getFileName();
    }

    /**
     * Writes the active overlay against the shipped global modes, without the profiles read now: the reload stays the
     * only check of the base and of the keys (rule W5).
     *
     * @param path the proposal path
     * @return the note
     * @throws IOException when the path is refused, the proposal is refused or the write fails
     */
    public static String apply(String path) throws IOException {
        return apply(path, OverlayModes.DEFAULTS, ProfileBases.UNKNOWN);
    }

    /**
     * Writes the active overlay of one profile. Nothing is written when the proposal is refused.
     *
     * @param path the proposal path
     * @param modes the global modes in use, which no key may raise
     * @param bases the profiles read now; {@link ProfileBases#UNKNOWN} skips the checks that need them
     * @return the note
     * @throws IOException when the path is refused, the proposal is refused or the write fails
     */
    public static String apply(String path, OverlayModes modes, ProfileBases bases) throws IOException {
        Objects.requireNonNull(modes, "modes");
        Objects.requireNonNull(bases, "bases");
        var proposal = file(path);
        var name = proposal.getFileName().toString();
        if (Files.size(proposal) > MAX_BYTES) {
            throw new OverlayRefusal("OVERLAY_UNREADABLE: larger than " + MAX_BYTES + " bytes" + NOTHING);
        }
        var parsed = parse(Files.readString(proposal), stem(name));
        var keys = propose(parsed, modes);
        check(parsed, keys, bases);
        writeActive(autoconfig(proposal), name, parsed, keys);
        return "applied " + name;
    }

    /**
     * Reads the active overlay.
     *
     * @param path the proposal path whose name selects the active file
     * @return the active text, or {@code none}
     * @throws IOException when the path is refused
     */
    public static String show(String path) throws IOException {
        var proposal = file(path);
        var active = autoconfig(proposal).resolve("active").resolve(proposal.getFileName());
        if (!Files.isRegularFile(active)) {
            return "none";
        }
        return Files.readString(active).trim();
    }

    /**
     * Removes the active overlay.
     *
     * @param path the proposal path whose name selects the active file
     * @return the note
     * @throws IOException when the path is refused
     */
    public static String revert(String path) throws IOException {
        var proposal = file(path);
        Files.deleteIfExists(autoconfig(proposal).resolve("active").resolve(proposal.getFileName()));
        return "reverted";
    }

    private static Path file(String path) throws IOException {
        OverlayPath.require(path);
        var proposal = Path.of(path).normalize();
        if (!proposal.startsWith(autoconfig(proposal))) {
            throw new IOException("path");
        }
        return proposal;
    }

    private static Path autoconfig(Path proposal) throws IOException {
        for (var cursor = proposal.getParent(); cursor != null; cursor = cursor.getParent()) {
            if (named(cursor, "autoconfig") && named(cursor.getParent(), "ThePredict")) {
                return cursor;
            }
        }
        throw new IOException("path");
    }

    private static boolean named(Path path, String name) {
        return path != null && path.getFileName() != null && path.getFileName().toString().equals(name);
    }

    /**
     * Reads a proposal as the reload reads an active file: keys and values trimmed, {@code #} lines skipped. Every
     * {@code pin=} line is kept, since a proposal may pin several keys; the last {@code profile=} and {@code base=} win.
     */
    private static Parsed parse(String text, String fileProfile) throws OverlayRefusal {
        var profile = fileProfile;
        var base = "";
        var keys = new LinkedHashMap<String, String>();
        var pins = new ArrayList<String>();
        var number = 0;
        for (var line : text.split("\n")) {
            number++;
            var trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.charAt(0) == '#') {
                continue;
            }
            var cut = trimmed.indexOf('=');
            if (cut <= 0) {
                throw new OverlayRefusal("OVERLAY_INVALID: line " + number + " has no key=value" + NOTHING);
            }
            var key = trimmed.substring(0, cut).trim();
            var value = trimmed.substring(cut + 1).trim();
            switch (key) {
                case "profile" -> profile = value;
                case "base" -> base = value;
                case "pin" -> pins.add(value);
                default -> keys.put(key, value);
            }
        }
        return new Parsed(profile, base, keys, List.copyOf(pins));
    }

    /** The unpinned keys, in proposal order, once no mode key raises its global mode (SG-01: read as the reload). */
    private static Map<String, String> propose(Parsed parsed, OverlayModes modes) throws OverlayRefusal {
        var proposed = new LinkedHashMap<String, String>();
        for (var entry : parsed.keys().entrySet()) {
            if (parsed.pins().contains(entry.getKey())) {
                continue;
            }
            checkMode(entry.getKey(), entry.getValue(), modes);
            proposed.put(entry.getKey(), entry.getValue());
        }
        return proposed;
    }

    private static void checkMode(String key, String value, OverlayModes modes) throws OverlayRefusal {
        var global = modes.globalRank(key);
        if (global < 0) {
            return;
        }
        var token = value.trim().toLowerCase(Locale.ROOT);
        int rank;
        try {
            rank = OverlayModes.valueRank(key, token);
        } catch (IllegalArgumentException exception) {
            throw new OverlayRefusal("OVERLAY_INVALID: " + reason(exception) + NOTHING);
        }
        try {
            OverlayStages.check(key, token, global, rank);
        } catch (IllegalArgumentException exception) {
            throw new OverlayRefusal(exception.getMessage());
        }
    }

    /** The base and the trial application against the profile read now (CAR-03, CAR-04). */
    private static void check(Parsed parsed, Map<String, String> keys, ProfileBases bases) throws OverlayRefusal {
        if (!bases.known()) {
            if (parsed.base().isEmpty()) {
                throw new OverlayRefusal("OVERLAY_INVALID: the proposal has no base= line" + NOTHING);
            }
            return;
        }
        var found = bases.base(parsed.profile()).orElseThrow(() -> new OverlayRefusal(
                "OVERLAY_UNMATCHED: no profile read now is named '" + parsed.profile() + "'" + NOTHING));
        try {
            new ProfileOverlay(parsed.profile(), keys, parsed.base()).apply(Map.of(), parsed.pins(),
                    found.fingerprint());
        } catch (IllegalArgumentException stale) {
            throw new OverlayRefusal("OVERLAY_STALE: the proposal was not built on the profile read now (base "
                    + found.fingerprint() + ")" + NOTHING);
        }
        try {
            ProfileKeys.apply(found.profile(), keys);
        } catch (RuntimeException exception) {
            throw new OverlayRefusal("OVERLAY_INVALID: " + reason(exception) + NOTHING);
        }
    }

    /** Replaces the active overlay atomically, so a crash never leaves half of it to be applied (M29.8). */
    private static void writeActive(Path root, String name, Parsed parsed, Map<String, String> keys)
            throws IOException {
        var body = new StringBuilder();
        body.append("profile=").append(parsed.profile()).append('\n');
        body.append("base=").append(parsed.base()).append('\n');
        for (var entry : keys.entrySet()) {
            body.append(entry.getKey()).append('=').append(entry.getValue()).append('\n');
        }
        var text = body.toString();
        if (text.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new OverlayRefusal("OVERLAY_UNREADABLE: larger than " + MAX_BYTES + " bytes" + NOTHING);
        }
        AtomicFiles.write(root.resolve("active").resolve(name), text);
    }

    private static String reason(RuntimeException exception) {
        return Objects.requireNonNullElse(exception.getMessage(), exception.getClass().getSimpleName());
    }

    private static String stem(String file) {
        var dot = file.lastIndexOf('.');
        return dot > 0 ? file.substring(0, dot) : file;
    }

    private record Parsed(String profile, String base, Map<String, String> keys, List<String> pins) {
    }
}
