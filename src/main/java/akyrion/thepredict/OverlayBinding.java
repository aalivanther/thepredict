package akyrion.thepredict;

import akyrion.thepredict.adapter.server.binding.BoundProfile;
import akyrion.thepredict.config.ConfigLocations;
import akyrion.thepredict.config.ConfigProblem;
import akyrion.thepredict.config.ProfileLoadResult;
import akyrion.thepredict.domain.TickLayerMode;
import akyrion.thepredict.domain.distill.ProfileKeys;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Applies the active overlays onto the bindings of a fresh read (M29.8): each overlay changes only the binding of its
 * own profile, as the loader profile plus the overlay keys. An overlay is skipped with a warning, and the author
 * profile stays (A6), when no profile read has its name ({@code OVERLAY_UNMATCHED}), when an earlier file already names
 * the same profile ({@code OVERLAY_DUPLICATE}), when its {@code base=} differs from the fingerprint of the profile read
 * now ({@code OVERLAY_STALE}), or when one of its keys does not apply ({@code OVERLAY_INVALID}). A
 * {@code Timing.TickLayer} above the global {@code decision.tickLayer.mode} is lowered to it with a warning, as the
 * loader lowers the one of the profile (DOC-04). Nothing here throws.
 */
final class OverlayBinding {
    private OverlayBinding() {
    }

    /**
     * What the overlays left.
     *
     * @param bindings bindings by MythicMobs mob ID, with the overlays that applied
     * @param keys applied keys, each as {@code <profile ID>:<dotted key>}
     * @param problems warnings of the read and of every overlay that did not apply
     */
    record Result(Map<String, BoundProfile> bindings, Map<String, String> keys, List<ConfigProblem> problems) {
        /**
         * Copies the collections, keeping the order of the bindings.
         */
        Result {
            bindings = Collections.unmodifiableMap(new LinkedHashMap<>(bindings));
            keys = Map.copyOf(keys);
            problems = List.copyOf(problems);
        }
    }

    /**
     * Applies every overlay in file name order.
     *
     * @param read the active overlays and the problems of their read
     * @param bindings the bindings of the profiles read now, by MythicMobs mob ID
     * @param loaded the read those bindings came from: profile files and fingerprints
     * @param tickLayerCeiling the global {@code decision.tickLayer.mode}
     * @return the bindings, the applied keys and the problems
     */
    static Result apply(ActiveOverlays.Read read, Map<String, BoundProfile> bindings, ProfileLoadResult loaded,
                        TickLayerMode tickLayerCeiling) {
        var next = new LinkedHashMap<>(bindings);
        var applied = new LinkedHashMap<String, String>();
        var problems = new ArrayList<>(read.problems());
        var seen = new HashMap<String, String>();
        for (var overlay : read.overlays()) {
            var first = seen.putIfAbsent(overlay.profileId(), overlay.file());
            var refusal = first == null ? refusal(overlay, loaded) : Optional.of("OVERLAY_DUPLICATE: " + first
                    + " already holds the overlay of this profile; not applied");
            var cause = refusal.isPresent() ? refusal : applyOne(overlay, next, applied, tickLayerCeiling);
            if (cause.isEmpty() && applied.containsKey(overlay.profileId() + ":Timing.TickLayer")) {
                cause = lowered(overlay, tickLayerCeiling);
            }
            cause.ifPresent(text -> problems.add(ConfigProblem.warning(ActiveOverlays.LABEL + "/" + overlay.file(),
                    overlay.profileId(), text)));
        }
        return new Result(next, applied, problems);
    }

    private static Optional<String> refusal(ActiveOverlays.Overlay overlay, ProfileLoadResult loaded) {
        if (!loaded.files().containsKey(overlay.profileId())) {
            return Optional.of("OVERLAY_UNMATCHED: no profile read from " + ConfigLocations.profilesLabel()
                    + " is named '" + overlay.profileId() + "'; not applied");
        }
        var live = loaded.fingerprints().get(overlay.profileId());
        if (overlay.base().isPresent() && !overlay.base().get().equals(live)) {
            return Optional.of("OVERLAY_STALE: the author profile changed after the overlay was built; the author"
                    + " profile is used");
        }
        return Optional.empty();
    }

    /** The notice of an applied overlay whose {@code Timing.TickLayer} was lowered to the global mode. */
    private static Optional<String> lowered(ActiveOverlays.Overlay overlay, TickLayerMode ceiling) {
        var asked = overlay.keys().get("Timing.TickLayer");
        if (asked == null) {
            return Optional.empty();
        }
        var mode = TickLayerMode.valueOf(asked.trim().toUpperCase(Locale.ROOT));
        if (mode.lesser(ceiling) == mode) {
            return Optional.empty();
        }
        var used = ceiling.name().toLowerCase(Locale.ROOT);
        return Optional.of("Timing.TickLayer is above the global " + used + "; using " + used);
    }

    private static Optional<String> applyOne(ActiveOverlays.Overlay overlay, Map<String, BoundProfile> bindings,
                                             Map<String, String> applied, TickLayerMode ceiling) {
        for (var entry : bindings.entrySet()) {
            var bound = entry.getValue();
            if (bound.profile().id().equals(overlay.profileId())) {
                try {
                    entry.setValue(bound.withProfile(ProfileKeys.apply(bound.profile(), overlay.keys(), ceiling)));
                } catch (RuntimeException exception) {
                    return Optional.of("OVERLAY_INVALID: " + Objects.requireNonNullElse(exception.getMessage(),
                            exception.getClass().getSimpleName()) + "; the author profile is used");
                }
                overlay.keys().forEach((key, value) -> applied.put(overlay.profileId() + ":" + key, value));
                return Optional.empty();
            }
        }
        return Optional.empty();
    }
}
