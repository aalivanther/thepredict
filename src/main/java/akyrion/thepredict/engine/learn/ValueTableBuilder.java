package akyrion.thepredict.engine.learn;

import akyrion.thepredict.domain.learn.ArtifactFormat;
import akyrion.thepredict.domain.learn.ShrinkageMath;

/**
 * Builds a {@link ValueTable} off the server thread (M28.3). Cells are inserted in the order given, which the caller
 * makes canonical. Shrinkage is baked in before insert. Lookup itself does no floating point.
 */
public final class ValueTableBuilder {
    private long[] keys = new long[16];
    private int[] value = new int[16];
    private int[] sd = new int[16];
    private int[] count = new int[16];
    private int[] level = new int[16];
    private int[] seen = new int[16];
    private int[] agree = new int[16];
    private int[] delta = new int[16];
    private int size;

    /**
     * Creates an empty builder.
     */
    public ValueTableBuilder() {
        java.util.Arrays.fill(keys, ValueTable.EMPTY);
    }

    /**
     * Inserts one already-shrunk cell.
     *
     * @param profile profile code
     * @param tactic tactic code
     * @param cellLevel hierarchy level
     * @param context packed context
     * @param mean reward mean
     * @param within within variance
     * @param windows effective windows
     * @param kappa shrinkage used
     */
    public void add(int profile, int tactic, int cellLevel, long context, double mean, double within, int windows,
                    double kappa) {
        add(profile, tactic, cellLevel, context, mean, within, windows, kappa, 0, 0, 0);
    }

    /**
     * Inserts one already-shrunk cell with its Jev counts and disagreement delta.
     *
     * @param profile profile code
     * @param tactic tactic code
     * @param cellLevel hierarchy level
     * @param context packed context
     * @param mean reward mean
     * @param within within variance
     * @param windows effective windows
     * @param kappa shrinkage used
     * @param jevSeen questions seen in the cell
     * @param jevAgree agreements in the cell
     * @param deltaMillis stored disagreement delta
     */
    public void add(int profile, int tactic, int cellLevel, long context, double mean, double within, int windows,
                    double kappa, int jevSeen, int jevAgree, int deltaMillis) {
        if (size + 1 > ValueTable.MAX_ENTRIES) {
            throw new IllegalArgumentException("learning.maxEntries exceeded");
        }
        if ((size + 1) * 2 > keys.length) {
            grow();
        }
        put(ValueTable.key(profile, tactic, cellLevel, context), ShrinkageMath.millis(mean),
                ShrinkageMath.millis(ShrinkageMath.deviation(within, windows, kappa)), windows, cellLevel, jevSeen,
                jevAgree, deltaMillis);
    }

    /**
     * Inserts one cell of an artifact, already quantized by the build.
     *
     * @param cell the cell
     */
    public void add(ArtifactFormat.ValueCell cell) {
        if (size + 1 > ValueTable.MAX_ENTRIES) {
            throw new IllegalArgumentException("learning.maxEntries exceeded");
        }
        if ((size + 1) * 2 > keys.length) {
            grow();
        }
        put(ValueTable.key(cell.profile(), cell.tactic(), cell.level(), cell.context()), cell.valueMillis(),
                cell.sdMillis(), cell.nEff(), cell.level(), cell.jevSeen(), cell.jevAgree(), cell.deltaMillis());
    }

    /**
     * Freezes the table. It has no name tables, so only the integer lookup finds its cells.
     *
     * @return the immutable table
     */
    public ValueTable build() {
        return build(new String[0], new String[0]);
    }

    /**
     * Freezes the table with the sorted name tables of its artifact.
     *
     * @param profiles profile ids, sorted
     * @param tactics tactic ids, sorted
     * @return the immutable table
     */
    ValueTable build(String[] profiles, String[] tactics) {
        return new ValueTable(keys.clone(), value.clone(), sd.clone(), count.clone(), level.clone(), seen.clone(),
                agree.clone(), delta.clone(), profiles.clone(), tactics.clone());
    }

    private void put(long key, int meanMillis, int sdMillis, int windows, int cellLevel, int jevSeen, int jevAgree,
                     int deltaMillis) {
        var mask = keys.length - 1;
        var slot = ValueTable.slot(key, keys.length);
        while (keys[slot] != ValueTable.EMPTY && keys[slot] != key) {
            slot = (slot + 1) & mask;
        }
        if (keys[slot] == ValueTable.EMPTY) {
            size++;
        }
        keys[slot] = key;
        value[slot] = meanMillis;
        sd[slot] = sdMillis;
        count[slot] = windows;
        level[slot] = cellLevel;
        seen[slot] = jevSeen;
        agree[slot] = jevAgree;
        delta[slot] = deltaMillis;
    }

    private void grow() {
        var next = new ValueTableBuilder();
        next.keys = new long[keys.length * 2];
        next.value = new int[keys.length * 2];
        next.sd = new int[keys.length * 2];
        next.count = new int[keys.length * 2];
        next.level = new int[keys.length * 2];
        next.seen = new int[keys.length * 2];
        next.agree = new int[keys.length * 2];
        next.delta = new int[keys.length * 2];
        java.util.Arrays.fill(next.keys, ValueTable.EMPTY);
        for (var index = 0; index < keys.length; index++) {
            if (keys[index] != ValueTable.EMPTY) {
                next.put(keys[index], value[index], sd[index], count[index], level[index], seen[index], agree[index],
                        delta[index]);
            }
        }
        keys = next.keys;
        value = next.value;
        sd = next.sd;
        count = next.count;
        level = next.level;
        seen = next.seen;
        agree = next.agree;
        delta = next.delta;
        size = next.size;
    }
}
