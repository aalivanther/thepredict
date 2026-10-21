package akyrion.thepredict.domain.distill;

import akyrion.thepredict.domain.DecisionCadence;
import akyrion.thepredict.domain.DecisionTiming;
import akyrion.thepredict.domain.GroupMode;
import akyrion.thepredict.domain.GroupSettings;
import akyrion.thepredict.domain.PatternMode;
import akyrion.thepredict.domain.PatternProfile;
import akyrion.thepredict.domain.ReactionScope;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.domain.TickLayerMode;
import akyrion.thepredict.domain.ZoneMode;
import akyrion.thepredict.domain.ZoneSettings;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Function;

/** Dotted keys the loader already stored (M29.8). Omitted sections are not invented. */
public final class ProfileKeys {
    private ProfileKeys() {
    }

    /**
     * Reads the keys the loader bound. Inherited sentinels are left out.
     *
     * @param profile a profile from the loader
     * @return dotted key to value
     */
    public static Map<String, String> of(TacticalProfile profile) {
        var keys = new LinkedHashMap<String, String>();
        writeZone(profile.zone(), keys);
        profile.group().mode().ifPresent(mode -> keys.put("Group.Mode", token(mode)));
        profile.patterns().mode().ifPresent(mode -> keys.put("Patterns.Mode", token(mode)));
        writeDecision(profile.decision(), keys);
        profile.distillMode().ifPresent(mode -> keys.put("Distill.Mode", token(mode)));
        return Map.copyOf(keys);
    }

    /**
     * Unions several profiles. The first value of a repeated key wins.
     *
     * @param profiles loader profiles
     * @return dotted key to value
     */
    public static Map<String, String> ofAll(Collection<TacticalProfile> profiles) {
        var keys = new LinkedHashMap<String, String>();
        for (var profile : profiles) {
            of(profile).forEach(keys::putIfAbsent);
        }
        return Map.copyOf(keys);
    }

    /**
     * Copies the profile and replaces the named keys, with no ceiling on {@code Timing.TickLayer} (rule W5): the trial
     * application of {@code autoconfig apply} only checks that the keys apply.
     *
     * @param profile the loader profile
     * @param keys dotted overrides, author values plus unfixed overlay keys
     * @return the same instance when no modeled key changes
     */
    public static TacticalProfile apply(TacticalProfile profile, Map<String, String> keys) {
        return apply(profile, keys, TickLayerMode.FULL);
    }

    /**
     * Copies the profile and replaces the named keys. A key this profile does not model is ignored. A
     * {@code Timing.TickLayer} above the global {@code decision.tickLayer.mode} is lowered to it, as the loader lowers
     * the one of the profile (DOC-04).
     *
     * @param profile the loader profile
     * @param keys dotted overrides, author values plus unfixed overlay keys
     * @param tickLayerCeiling the global {@code decision.tickLayer.mode}
     * @return the same instance when no modeled key changes
     */
    public static TacticalProfile apply(TacticalProfile profile, Map<String, String> keys,
                                        TickLayerMode tickLayerCeiling) {
        Objects.requireNonNull(tickLayerCeiling, "tickLayerCeiling");
        var zone = zone(profile.zone(), keys);
        var group = group(profile.group(), keys);
        var patterns = patterns(profile.patterns(), keys);
        var decision = decision(profile.decision(), keys);
        var distill = distill(profile.distillMode(), keys);
        var timing = timing(profile.timing(), keys, tickLayerCeiling);
        if (zone == profile.zone() && group == profile.group() && patterns == profile.patterns()
                && decision == profile.decision() && distill.equals(profile.distillMode())
                && timing == profile.timing()) {
            return profile;
        }
        return profile.withOverlay(timing, zone, group, patterns, decision, distill);
    }

    private static void writeZone(ZoneSettings zone, Map<String, String> keys) {
        zone.mode().ifPresent(mode -> keys.put("Zone.Mode", token(mode)));
        if (zone.radius().isPresent()) {
            keys.put("Zone.Radius", Integer.toString(zone.radius().getAsInt()));
        }
    }

    private static void writeDecision(ProfileDecision decision, Map<String, String> keys) {
        put(keys, "Decision.Pruning", decision.pruning(), "inherit");
        if (decision.maxOptions() != 0) {
            keys.put("Decision.MaxOptions", Integer.toString(decision.maxOptions()));
        }
        put(keys, "Decision.CandidateScoring", decision.scoring(), "inherit");
        put(keys, "Decision.Diversity", decision.diversity(), "inherit");
    }

    private static void put(Map<String, String> keys, String key, String value, String absent) {
        if (!absent.equals(value)) {
            keys.put(key, value);
        }
    }

    private static ZoneSettings zone(ZoneSettings current, Map<String, String> keys) {
        var mode = parsed(keys, "Zone.Mode", current.mode(), ZoneMode::parse);
        var radius = radius(current.radius(), keys.get("Zone.Radius"));
        if (mode.equals(current.mode()) && radius.equals(current.radius())) {
            return current;
        }
        return new ZoneSettings(mode, radius, current.facts(), current.hazardPush());
    }

    private static OptionalInt radius(OptionalInt current, String value) {
        if (value == null) {
            return current;
        }
        return OptionalInt.of(Integer.parseInt(value.trim()));
    }

    private static GroupSettings group(GroupSettings current, Map<String, String> keys) {
        var mode = parsed(keys, "Group.Mode", current.mode(), GroupMode::parse);
        if (mode.equals(current.mode())) {
            return current;
        }
        return new GroupSettings(mode, current.squad(), current.leaderPriority(), current.weight(), current.roles(),
                current.onLeaderDeath(), current.againstEntities(), current.questions(), current.planQuestion(),
                current.capabilities());
    }

    private static PatternProfile patterns(PatternProfile current, Map<String, String> keys) {
        var mode = parsed(keys, "Patterns.Mode", current.mode(), PatternMode::parse);
        if (mode.equals(current.mode())) {
            return current;
        }
        return new PatternProfile(mode, current.againstEntities(), current.facts());
    }

    private static ProfileDecision decision(ProfileDecision current, Map<String, String> keys) {
        var pruning = text(keys, "Decision.Pruning", current.pruning());
        var scoring = text(keys, "Decision.CandidateScoring", current.scoring());
        var diversity = text(keys, "Decision.Diversity", current.diversity());
        var max = keys.containsKey("Decision.MaxOptions")
                ? Integer.parseInt(keys.get("Decision.MaxOptions").trim()) : current.maxOptions();
        if (pruning.equals(current.pruning()) && scoring.equals(current.scoring())
                && diversity.equals(current.diversity()) && max == current.maxOptions()) {
            return current;
        }
        return new ProfileDecision(pruning, max, scoring, diversity);
    }

    private static Optional<DistillMode> distill(Optional<DistillMode> current, Map<String, String> keys) {
        var value = keys.get("Distill.Mode");
        if (value == null) {
            return current;
        }
        return Optional.of(DistillMode.parse(value.trim().toLowerCase(Locale.ROOT)));
    }

    private static DecisionTiming timing(DecisionTiming current, Map<String, String> keys, TickLayerMode ceiling) {
        if (!keys.containsKey("Timing.Cadence") && !keys.containsKey("Timing.ReactiveInterval")
                && !keys.containsKey("Timing.TickLayer") && !keys.containsKey("Timing.Reactions")) {
            return current;
        }
        var cadence = enumeration(keys, "Timing.Cadence", current.cadence(), DecisionCadence.class);
        var layer = enumeration(keys, "Timing.TickLayer", current.tickLayer(), TickLayerMode.class).lesser(ceiling);
        var reactions = enumeration(keys, "Timing.Reactions", current.reactions(), ReactionScope.class);
        var interval = keys.containsKey("Timing.ReactiveInterval")
                ? Integer.parseInt(keys.get("Timing.ReactiveInterval").trim()) : current.reactiveIntervalTicks();
        if (cadence == current.cadence() && layer == current.tickLayer() && reactions == current.reactions()
                && interval == current.reactiveIntervalTicks()) {
            return current;
        }
        return current.withLayer(layer, reactions, current.reactionsAhead(), current.reactionDelayTicks(), cadence,
                interval, current.intentHorizonTicks());
    }

    private static <T> Optional<T> parsed(Map<String, String> keys, String key, Optional<T> current,
                                          Function<String, T> parse) {
        var value = keys.get(key);
        if (value == null) {
            return current;
        }
        return Optional.of(parse.apply(value.trim().toLowerCase(Locale.ROOT)));
    }

    private static String text(Map<String, String> keys, String key, String current) {
        var value = keys.get(key);
        return value == null ? current : value.trim();
    }

    private static <E extends Enum<E>> E enumeration(Map<String, String> keys, String key, E current, Class<E> type) {
        var value = keys.get(key);
        if (value == null) {
            return current;
        }
        return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
    }

    private static String token(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }
}
