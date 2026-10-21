package akyrion.thepredict.adapter.server.zone;

import akyrion.thepredict.domain.ZoneColumn;
import akyrion.thepredict.domain.ZoneGrid;
import akyrion.thepredict.domain.ZoneLayers;
import akyrion.thepredict.domain.ZoneView;

/**
 * The map published for one encounter (M24.2). It is built from a chunk snapshot on the zone thread and then only
 * read on the server thread. Columns the snapshot did not cover stay unknown.
 */
public final class ZoneMap implements ZoneView {
    private final ZoneGrid grid;
    private final boolean zoneThread;
    private final boolean[] hazardSources;
    private final boolean[] edgeSources;
    private final boolean[] obstacleSources;

    /**
     * Publishes a grid. The caller transfers ownership of the grid.
     *
     * @param grid the scanned grid
     * @param zoneThread whether the scan ran on the zone thread
     * @throws IllegalArgumentException when the grid is missing
     */
    public ZoneMap(ZoneGrid grid, boolean zoneThread) {
        if (grid == null) {
            throw new IllegalArgumentException("grid is required");
        }
        var cells = grid.width() * grid.width();
        hazardSources = new boolean[cells];
        edgeSources = new boolean[cells];
        obstacleSources = new boolean[cells];
        ZoneLayers.sources(grid.columns(), grid.width(), grid.maxSafeDrop(), hazardSources, edgeSources,
                obstacleSources);
        grid.seal();
        this.grid = grid;
        this.zoneThread = zoneThread;
    }

    /**
     * A map of unknown columns, used when a scan fails. The failure does not stop the zone thread.
     *
     * @param originX world X of column 0
     * @param originZ world Z of column 0
     * @param anchorY anchor height
     * @param safeDrop safe drop in blocks
     * @param width width of the square
     * @param epoch the build epoch
     * @param zoneThread whether the caller is the zone thread
     * @return the map
     */
    public static ZoneMap unknown(int originX, int originZ, int anchorY, int safeDrop, int width, int epoch,
                                  boolean zoneThread) {
        var grid = new ZoneGrid(width, originX, originZ, anchorY, safeDrop);
        grid.ready(epoch);
        return new ZoneMap(grid, zoneThread);
    }

    /**
     * Whether the column is a hazard source kept from the build.
     *
     * @param index column index
     * @return the stored bit
     */
    public boolean hazardSource(int index) {
        return index >= 0 && index < hazardSources.length && hazardSources[index];
    }

    /**
     * Whether the column is an edge source kept from the build.
     *
     * @param index column index
     * @return the stored bit
     */
    public boolean edgeSource(int index) {
        return index >= 0 && index < edgeSources.length && edgeSources[index];
    }

    /**
     * Whether the column is an obstacle source kept from the build.
     *
     * @param index column index
     * @return the stored bit
     */
    public boolean obstacleSource(int index) {
        return index >= 0 && index < obstacleSources.length && obstacleSources[index];
    }

    /**
     * The published grid. Writes throw.
     *
     * @return the sealed grid
     */
    ZoneGrid publishedGrid() {
        return grid;
    }

    /**
     * Whether the scan that produced this map ran on the zone thread.
     *
     * @return {@code true} when it did
     */
    public boolean scannedOnZoneThread() {
        return zoneThread;
    }

    /**
     * How many columns have a floor. Unknown columns are not counted.
     *
     * @return the count
     */
    public int floorColumns() {
        var floors = 0;
        var cells = grid.width() * grid.width();
        for (var index = 0; index < cells; index++) {
            if (ZoneColumn.state(grid.column(index)) == ZoneColumn.FLOOR) {
                floors++;
            }
        }
        return floors;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int width() {
        return grid.width();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int originX() {
        return grid.originX();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int originZ() {
        return grid.originZ();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int anchorY() {
        return grid.anchorY();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long column(int index) {
        return grid.column(index);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public short hazardThirds(int index) {
        return grid.hazardThirds(index);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public short edgeThirds(int index) {
        return grid.edgeThirds(index);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public short clearanceThirds(int index) {
        return grid.clearanceThirds(index);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int epoch() {
        return grid.epoch();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int maxSafeDrop() {
        return grid.maxSafeDrop();
    }
}
