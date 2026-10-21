package akyrion.thepredict.domain.learn;

import akyrion.thepredict.domain.DistanceBand;
import akyrion.thepredict.domain.PatternGroup;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Context key v2 (M28.2, rule I21). Computed when an intent is adopted and stable for the window. Distance uses the
 * three {@link DistanceBand} names plus a reserved {@code OUT} code that the live band never produces, so the
 * cardinality stays {@value #CARDINALITY}. Names differ from the plan's {@code NEAR} example because the domain band
 * is {@code MELEE}.
 *
 * @param packed bits 0–1 distance, 2–3 own health, 4–5 target health, 6 player, 7–8 zone, 9–10 group, 11–13 pattern
 * @param text {@code v2:<dist>:<own>:<tgt>:<kind>:<zone>:<group>:<pattern>}
 * @param coarse {@code v2c:<dist>:<kind>:<pattern>}
 */
public record ContextKey(long packed, String text, String coarse) {
    /** Fine keys: 4 distances, 3 own, 3 target, 2 kinds, 4 zones, 3 groups, 7 patterns. */
    public static final int CARDINALITY = 4 * 3 * 3 * 2 * 4 * 3 * 7;
    /** Longest accepted text. */
    public static final int MAX_CHARS = 64;
    private static final Pattern SHAPE = Pattern.compile("v\\d+[a-z]?:[A-Z0-9_:]+");
    private static final String[] DISTANCE = {"MELEE", "RANGED", "BEYOND", "OUT"};
    private static final String[] KIND = {"MOB", "PLAYER"};
    private static final String[] PATTERNS = patternNames();
    /** Closed set. {@code of} is an index, so adopting a key stays inside 100 ns. */
    private static final ContextKey[] CACHE = fill();
    /** The same closed set by text, for a build that reads stored rows back. */
    private static final Map<String, ContextKey> BY_TEXT = byText();

    /**
     * Validates the texts.
     *
     * @throws IllegalArgumentException if a text is not a v2 key
     */
    public ContextKey {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(coarse, "coarse");
        if (text.length() > MAX_CHARS || coarse.length() > MAX_CHARS || !SHAPE.matcher(text).matches()
                || !SHAPE.matcher(coarse).matches()) {
            throw new IllegalArgumentException("context key must match v2 and stay within 64 characters");
        }
    }

    /**
     * Builds a key. Pattern code 0 is {@code NONE}.
     *
     * @param distance 0 melee, 1 ranged, 2 beyond, 3 reserved out
     * @param own own health
     * @param target target health
     * @param player whether the target is a player
     * @param zone zone band
     * @param group group band
     * @param patternCode 0 for none, otherwise {@link PatternGroup#ordinal()} + 1
     * @return the key
     */
    public static ContextKey of(int distance, HealthBand3 own, HealthBand3 target, boolean player, ZoneBand zone,
                                GroupBand group, int patternCode) {
        require(distance, 0, 3);
        require(patternCode, 0, 6);
        return CACHE[index(distance, own.ordinal(), target.ordinal(), player ? 1 : 0, zone.ordinal(),
                group.ordinal(), patternCode)];
    }

    /**
     * Distance code of a live band. {@code OUT} is not produced here.
     *
     * @param band the band
     * @return 0, 1 or 2
     */
    public static int distanceCode(DistanceBand band) {
        return band.ordinal();
    }

    /**
     * Pattern code. {@code NONE} unless the mode is at least bias and the exploit policy allows the pattern now. A
     * caller that observes no group passes {@code allows = false} with any group, or uses code 0 directly.
     *
     * @param group the observed group
     * @param allows whether the pattern may be exploited now
     * @return 0 or the group ordinal plus one
     */
    public static int patternCode(PatternGroup group, boolean allows) {
        Objects.requireNonNull(group, "group");
        return allows ? group.ordinal() + 1 : 0;
    }

    /**
     * Coarse form of a fine key. Level 2 drops own health, target health, zone and group.
     *
     * @return the coarse key text
     */
    public String folded() {
        return coarse;
    }

    /**
     * Lossless code of the coarse key: distance in bits 0–1, player in bit 2, pattern in bits 3–5. Two fine keys
     * share it exactly when they share {@link #coarse()}.
     *
     * @return the code, below 64
     */
    public long coarseCode() {
        return (packed & 3L) | (((packed >> 6) & 1L) << 2) | (((packed >> 11) & 7L) << 3);
    }

    /**
     * The key whose fine text is {@code text}. A stored row from an older key version, or any other text, is empty.
     *
     * @param text a fine key text
     * @return the key, or empty
     */
    public static Optional<ContextKey> parse(String text) {
        return Optional.ofNullable(BY_TEXT.get(text));
    }

    private static Map<String, ContextKey> byText() {
        var map = new HashMap<String, ContextKey>(CARDINALITY * 2);
        for (var key : CACHE) {
            map.put(key.text(), key);
        }
        return Map.copyOf(map);
    }

    private static ContextKey[] fill() {
        var health = HealthBand3.values();
        var zones = ZoneBand.values();
        var groups = GroupBand.values();
        var cache = new ContextKey[CARDINALITY];
        for (var pattern = 0; pattern <= 6; pattern++) {
            for (var group = 0; group < groups.length; group++) {
                for (var zone = 0; zone < zones.length; zone++) {
                    for (var player = 0; player < KIND.length; player++) {
                        for (var target = 0; target < health.length; target++) {
                            for (var own = 0; own < health.length; own++) {
                                for (var distance = 0; distance < DISTANCE.length; distance++) {
                                    var slot = index(distance, own, target, player, zone, group, pattern);
                                    cache[slot] = raw(distance, health[own], health[target], player == 1, zones[zone],
                                            groups[group], pattern);
                                }
                            }
                        }
                    }
                }
            }
        }
        return cache;
    }

    private static int index(int distance, int own, int target, int player, int zone, int group, int pattern) {
        return ((((((pattern * 3 + group) * 4 + zone) * 2 + player) * 3 + target) * 3 + own) * 4 + distance);
    }

    private static ContextKey raw(int distance, HealthBand3 own, HealthBand3 target, boolean player, ZoneBand zone,
                                  GroupBand group, int patternCode) {
        var packed = (distance & 3L) | ((long) own.ordinal() << 2) | ((long) target.ordinal() << 4)
                | ((player ? 1L : 0L) << 6) | ((long) zone.ordinal() << 7) | ((long) group.ordinal() << 9)
                | ((patternCode & 7L) << 11);
        var kind = player ? KIND[1] : KIND[0];
        var pattern = PATTERNS[patternCode];
        var text = "v2:" + DISTANCE[distance] + ":" + own.name() + ":" + target.name() + ":" + kind + ":"
                + zone.name() + ":" + group.name() + ":" + pattern;
        var coarse = "v2c:" + DISTANCE[distance] + ":" + kind + ":" + pattern;
        return new ContextKey(packed, text, coarse);
    }

    private static String[] patternNames() {
        var groups = PatternGroup.values();
        var names = new String[groups.length + 1];
        names[0] = "NONE";
        for (var index = 0; index < groups.length; index++) {
            names[index + 1] = groups[index].name();
        }
        return names;
    }

    private static void require(int value, int low, int high) {
        if (value < low || value > high) {
            throw new IllegalArgumentException("context code out of range: " + value);
        }
    }
}
