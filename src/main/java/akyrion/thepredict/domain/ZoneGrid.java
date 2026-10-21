package akyrion.thepredict.domain;

import java.util.Arrays;

/**
 * A square zone held in primitive arrays (M24.1). The three layers are {@code short} arrays.
 */
public final class ZoneGrid implements ZoneView {
    private final int width;
    private final int originX;
    private final int originZ;
    private final int anchorY;
    private final int maxSafeDrop;
    private final long[] columns;
    private final short[] hazard;
    private final short[] edge;
    private final short[] clearance;
    private int epoch;
    private boolean sealed;

    /**
     * An empty grid of unknown columns.
     *
     * @param width width in columns
     * @param originX world X of column 0
     * @param originZ world Z of column 0
     * @param anchorY anchor height
     * @param maxSafeDrop safe drop in blocks
     */
    public ZoneGrid(int width, int originX, int originZ, int anchorY, int maxSafeDrop) {
        if (width < 1) {
            throw new IllegalArgumentException("width must be >= 1, was " + width);
        }
        this.width = width;
        this.originX = originX;
        this.originZ = originZ;
        this.anchorY = anchorY;
        this.maxSafeDrop = maxSafeDrop;
        var cells = width * width;
        columns = new long[cells];
        hazard = new short[cells];
        edge = new short[cells];
        clearance = new short[cells];
        Arrays.fill(hazard, (short) ZoneLayers.CAP);
        Arrays.fill(edge, (short) ZoneLayers.CAP);
        Arrays.fill(clearance, (short) ZoneLayers.CAP);
    }

    /**
     * Replaces one column. The caller rebuilds or patches the layers afterwards.
     *
     * @param index column index
     * @param column packed column
     */
    public void set(int index, long column) {
        writable();
        columns[index] = column;
    }

    /**
     * Copies a published view into a new grid the caller may still write. The source is only read.
     *
     * @param view the source
     * @return an unsealed grid
     */
    public static ZoneGrid copyOf(ZoneView view) {
        var grid = new ZoneGrid(view.width(), view.originX(), view.originZ(), view.anchorY(), view.maxSafeDrop());
        var cells = view.width() * view.width();
        for (var index = 0; index < cells; index++) {
            grid.columns[index] = view.column(index);
            grid.hazard[index] = view.hazardThirds(index);
            grid.edge[index] = view.edgeThirds(index);
            grid.clearance[index] = view.clearanceThirds(index);
        }
        grid.epoch = view.epoch();
        return grid;
    }

    /**
     * Refuses later writes. Publication calls this once the zone thread has finished the arrays.
     */
    public void seal() {
        sealed = true;
    }

    /**
     * Whether publication has closed the arrays.
     *
     * @return {@code true} after {@link #seal()}
     */
    public boolean sealed() {
        return sealed;
    }

    /**
     * The column array. The caller owns it after publication.
     *
     * @return the columns
     */
    public long[] columns() {
        writable();
        return columns;
    }

    /**
     * The hazard layer.
     *
     * @return thirds
     */
    public short[] hazardLayer() {
        writable();
        return hazard;
    }

    /**
     * The edge layer.
     *
     * @return thirds
     */
    public short[] edgeLayer() {
        writable();
        return edge;
    }

    /**
     * The clearance layer.
     *
     * @return thirds
     */
    public short[] clearanceLayer() {
        writable();
        return clearance;
    }

    /**
     * Marks the grid ready at an epoch.
     *
     * @param epoch the installed epoch, at least 1
     */
    public void ready(int epoch) {
        writable();
        this.epoch = epoch;
    }

    private void writable() {
        if (sealed) {
            throw new IllegalStateException("published zone grid");
        }
    }

    @Override
    public int width() {
        return width;
    }

    @Override
    public int originX() {
        return originX;
    }

    @Override
    public int originZ() {
        return originZ;
    }

    @Override
    public int anchorY() {
        return anchorY;
    }

    @Override
    public long column(int index) {
        return index < 0 || index >= columns.length ? 0L : columns[index];
    }

    @Override
    public short hazardThirds(int index) {
        return index < 0 || index >= hazard.length ? ZoneLayers.CAP : hazard[index];
    }

    @Override
    public short edgeThirds(int index) {
        return index < 0 || index >= edge.length ? 0 : edge[index];
    }

    @Override
    public short clearanceThirds(int index) {
        return index < 0 || index >= clearance.length ? 0 : clearance[index];
    }

    @Override
    public int epoch() {
        return epoch;
    }

    @Override
    public int maxSafeDrop() {
        return maxSafeDrop;
    }
}
