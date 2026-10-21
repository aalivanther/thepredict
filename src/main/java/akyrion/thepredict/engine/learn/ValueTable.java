package akyrion.thepredict.engine.learn;

import akyrion.thepredict.domain.learn.ContextKey;
import akyrion.thepredict.domain.learn.ValueArtifact;
import akyrion.thepredict.domain.learn.ValueEstimate;

import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable open-addressed value table (M28.3). Lookup tries levels 3, 2, 1, 0 and returns the first present cell.
 * A present cell with {@code nEff} below the minimum is weak and does not fall through. No floating point.
 * Load factor is at most 0.5. At most {@value #MAX_ENTRIES} entries.
 *
 * <p>A table read from a {@link ValueArtifact} keeps its sorted profile and tactic names, and
 * {@link #lookup(String, String, ContextKey)} resolves them by binary search: a name that is not in the artifact is
 * {@link ValueEstimate#NONE}, and two names never share a key. The fine level is probed with
 * {@link ContextKey#packed()} and the coarse level with {@link ContextKey#coarseCode()}. The integer lookup is the
 * legacy entry point of callers that still pass hashed codes.
 */
public final class ValueTable {
    /** Cap from {@code learning.maxEntries}. */
    public static final int MAX_ENTRIES = 20_000;
    /** Empty slot. A real key is never this value. */
    static final long EMPTY = Long.MIN_VALUE;
    private static final String[] NO_NAMES = new String[0];

    private final long[] keys;
    private final int[] valueMillis;
    private final int[] sdMillis;
    private final int[] nEff;
    private final int[] level;
    private final int[] jevSeen;
    private final int[] jevAgree;
    private final int[] deltaMillis;
    private final String[] profiles;
    private final String[] tactics;

    ValueTable(long[] keys, int[] valueMillis, int[] sdMillis, int[] nEff, int[] level, int[] jevSeen, int[] jevAgree,
               int[] deltaMillis, String[] profiles, String[] tactics) {
        this.keys = keys;
        this.valueMillis = valueMillis;
        this.sdMillis = sdMillis;
        this.nEff = nEff;
        this.level = level;
        this.jevSeen = jevSeen;
        this.jevAgree = jevAgree;
        this.deltaMillis = deltaMillis;
        this.profiles = profiles;
        this.tactics = tactics;
    }

    /**
     * An empty table. Every lookup is {@link ValueEstimate#NONE}.
     *
     * @return the table
     */
    public static ValueTable empty() {
        return new ValueTable(new long[] {EMPTY}, new int[1], new int[1], new int[1], new int[1], new int[1],
                new int[1], new int[1], NO_NAMES, NO_NAMES);
    }

    /**
     * The table of a checked artifact, with its name tables.
     *
     * @param artifact the artifact
     * @return the table
     */
    public static ValueTable of(ValueArtifact artifact) {
        var builder = new ValueTableBuilder();
        for (var cell : artifact.cells()) {
            builder.add(cell);
        }
        return builder.build(artifact.profiles().toArray(String[]::new), artifact.tactics().toArray(String[]::new));
    }

    /**
     * Index of a profile in the artifact.
     *
     * @param profile profile id
     * @return the index, or −1 when the artifact does not know it
     */
    public int profileIndex(String profile) {
        var found = Arrays.binarySearch(profiles, Objects.requireNonNull(profile, "profile"));
        return found < 0 ? -1 : found;
    }

    /**
     * Index of a tactic in the artifact.
     *
     * @param tactic tactic id
     * @return the index, or −1 when the artifact does not know it
     */
    public int tacticIndex(String tactic) {
        var found = Arrays.binarySearch(tactics, Objects.requireNonNull(tactic, "tactic"));
        return found < 0 ? -1 : found;
    }

    /**
     * Looks up a cell by name and live context. An unknown profile or tactic is none.
     *
     * @param profile profile id
     * @param tactic tactic id
     * @param context context key of the capture
     * @return the estimate of the first present level
     */
    public ValueEstimate lookup(String profile, String tactic, ContextKey context) {
        var profileAt = profileIndex(profile);
        var tacticAt = tacticIndex(tactic);
        if (profileAt < 0 || tacticAt < 0) {
            return ValueEstimate.NONE;
        }
        return probe(profileAt, tacticAt, context.packed(), context.coarseCode());
    }

    /**
     * Legacy entry point: looks up a cell by code. Unknown profile or tactic codes are negative and return none.
     *
     * @param profile profile code, negative when unknown
     * @param tactic tactic code, negative when unknown
     * @param fine packed fine context
     * @param coarse packed coarse context
     * @param minimum usable windows; the caller compares it with {@link ValueEstimate#weak(int)}
     * @return the estimate
     */
    public ValueEstimate lookup(int profile, int tactic, long fine, long coarse, int minimum) {
        if (profile < 0 || tactic < 0) {
            return ValueEstimate.NONE;
        }
        return probe(profile, tactic, fine, coarse);
    }

    /**
     * Packs a key: profile 16 bits, tactic 16, level 2, context 20.
     *
     * @param profile profile index or code
     * @param tactic tactic index or code
     * @param level hierarchy level
     * @param context fine or coarse code
     * @return the key
     */
    public static long key(int profile, int tactic, int level, long context) {
        return (profile & 0xffffL) | ((tactic & 0xffffL) << 16) | ((level & 3L) << 32) | ((context & 0xfffffL) << 34);
    }

    private ValueEstimate probe(int profile, int tactic, long fine, long coarse) {
        var found = find(key(profile, tactic, 3, fine));
        if (found < 0) {
            found = find(key(profile, tactic, 2, coarse));
        }
        if (found < 0) {
            found = find(key(profile, tactic, 1, 0));
        }
        if (found < 0) {
            found = find(key(0, tactic, 0, 0));
        }
        if (found < 0) {
            return ValueEstimate.NONE;
        }
        return new ValueEstimate(valueMillis[found], sdMillis[found], nEff[found], level[found], jevSeen[found],
                jevAgree[found], deltaMillis[found]);
    }

    private int find(long key) {
        var mask = keys.length - 1;
        var slot = slot(key, keys.length);
        for (var step = 0; step < keys.length; step++) {
            var found = keys[slot];
            if (found == EMPTY) {
                return -1;
            }
            if (found == key) {
                return slot;
            }
            slot = (slot + 1) & mask;
        }
        return -1;
    }

    static int slot(long key, int length) {
        var z = key * 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        return (int) (z ^ (z >>> 27)) & (length - 1);
    }
}
