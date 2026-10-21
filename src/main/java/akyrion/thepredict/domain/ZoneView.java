package akyrion.thepredict.domain;

/**
 * A read-only zone (M24.1). Off-grid columns are unknown. Layers are thirds capped at 24 and are never {@code int}
 * arrays.
 */
public interface ZoneView {
    /**
     * Width of the square, in columns.
     *
     * @return the width
     */
    int width();

    /**
     * World block X of column 0.
     *
     * @return the origin
     */
    int originX();

    /**
     * World block Z of column 0.
     *
     * @return the origin
     */
    int originZ();

    /**
     * Anchor height.
     *
     * @return the anchor Y
     */
    int anchorY();

    /**
     * One column. An index outside the square is unknown.
     *
     * @param index column index, row-major
     * @return the packed column
     */
    long column(int index);

    /**
     * Hazard distance in thirds.
     *
     * @param index column index
     * @return 0–24
     */
    short hazardThirds(int index);

    /**
     * Edge distance in thirds.
     *
     * @param index column index
     * @return 0–24
     */
    short edgeThirds(int index);

    /**
     * Clearance from an obstacle, in thirds.
     *
     * @param index column index
     * @return 0–24
     */
    short clearanceThirds(int index);

    /**
     * Epoch of the installed build. Zero means the zone is not ready.
     *
     * @return the epoch
     */
    int epoch();

    /**
     * Drop, in blocks, that is still safe. A larger drop is an edge.
     *
     * @return the threshold
     */
    int maxSafeDrop();
}
