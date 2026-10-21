package akyrion.thepredict.domain;

/**
 * Which side an orbit or a sidestep uses (M26.5). Terrain wins, then a group gap, then a pattern, then the seed.
 */
public final class MicroSidePriority {
    private MicroSidePriority() {
    }

    /**
     * The side that wins. A negative pattern side means the pattern has no opinion.
     *
     * @param terrainSide side required by terrain, or negative when terrain is silent
     * @param groupSide side required by a group gap, or negative
     * @param patternSide side of the pattern, or negative
     * @param seedSide side of the seed
     * @return the winning side
     */
    public static int choose(int terrainSide, int groupSide, int patternSide, int seedSide) {
        if (terrainSide >= 0) {
            return terrainSide;
        }
        if (groupSide >= 0) {
            return groupSide;
        }
        return patternSide >= 0 ? patternSide : seedSide;
    }
}
