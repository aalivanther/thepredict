package akyrion.thepredict.adapter.server.goals;

import org.bukkit.Location;

/**
 * What {@link MicroGoal} reads of a mob. Production wraps the Bukkit mob; tests use a double.
 */
interface MicroBody {
    /**
     * World X.
     *
     * @return X
     */
    double x();

    /**
     * World Y.
     *
     * @return Y
     */
    double y();

    /**
     * World Z.
     *
     * @return Z
     */
    double z();

    /**
     * Whether the mob is on the ground.
     *
     * @return whether it stands on a block
     */
    boolean onGround();

    /**
     * Current no-damage ticks.
     *
     * @return the ticks
     */
    int noDamageTicks();

    /**
     * Maximum no-damage ticks.
     *
     * @return the maximum
     */
    int maximumNoDamageTicks();

    /**
     * Whether a living target is selected.
     *
     * @return whether steering has a target
     */
    boolean hasTarget();

    /**
     * Target X.
     *
     * @return X
     */
    double targetX();

    /**
     * Target Y.
     *
     * @return Y
     */
    double targetY();

    /**
     * Target Z.
     *
     * @return Z
     */
    double targetZ();

    /**
     * Asks a path to a point.
     *
     * @param x destination X
     * @param y destination Y
     * @param z destination Z
     * @param speed speed multiplier
     * @return whether the pathfinder accepted it
     */
    boolean moveToPoint(double x, double y, double z, double speed);

    /**
     * Asks a path onto the current target entity. Enter uses this, not a point.
     *
     * @param speed speed multiplier
     * @return whether the pathfinder accepted it
     */
    boolean moveToTarget(double speed);

    /**
     * Whether a path is visible.
     *
     * @return whether the pathfinder has a path
     */
    boolean hasPath();

    /**
     * Whether the current path has a final point.
     *
     * @return whether an endpoint exists
     */
    boolean hasEnd();

    /**
     * Final point X.
     *
     * @return X
     */
    double endX();

    /**
     * Final point Z.
     *
     * @return Z
     */
    double endZ();

    /** Stops the current path. */
    void stopPathfinding();

    /**
     * A location at the given point, in the mob's world.
     *
     * @param x X
     * @param y Y
     * @param z Z
     * @return the location
     */
    Location at(double x, double y, double z);
}
