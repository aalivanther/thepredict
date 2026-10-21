package akyrion.thepredict.adapter.server.duel;

import akyrion.thepredict.domain.TacticalProfile;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * What the duel recorder reads of the world (M18.4), kept apart so the recorder is tested with doubles and fed by the
 * local simulator of M18.7 (rule P4); the server implementation is {@link LiveDuelScene}. Server thread only.
 */
public interface DuelScene {
    /** Most duelists read in one sample, the nearest to the centre first. */
    int MAX_DUELISTS = 8;

    /**
     * Reads the living entities of either faction inside the arena.
     *
     * @param worldId world of the arena
     * @param centerX centre of the arena
     * @param centerY centre of the arena
     * @param centerZ centre of the arena
     * @param radiusBlocks radius of the arena
     * @param sideA faction of side {@code red}, compared ignoring case as MythicMobs does
     * @param sideB faction of side {@code blue}, compared ignoring case
     * @return at most {@link #MAX_DUELISTS} readings; empty when the world is not loaded
     */
    List<DuelistReading> duelists(UUID worldId, double centerX, double centerY, double centerZ, double radiusBlocks,
                                  String sideA, String sideB);

    /**
     * Reads the MythicMobs faction of an entity, an O(1) lookup by UUID.
     *
     * @param entityId the entity
     * @return its faction, empty for any other entity
     */
    Optional<String> faction(UUID entityId);

    /**
     * Finds the ThePredict profile a mob is bound to.
     *
     * @param entityId the entity
     * @return the bound profile, empty for a classic mob or any other entity
     */
    Optional<TacticalProfile> profile(UUID entityId);

    /**
     * Lists the bound profiles of the current catalog, for the counted skills of M18.4.
     *
     * @return the profiles
     */
    Collection<TacticalProfile> profiles();

    /**
     * Tells whether a world is loaded.
     *
     * @param worldId the world
     * @return {@code true} when it is loaded
     */
    boolean worldLoaded(UUID worldId);
}
