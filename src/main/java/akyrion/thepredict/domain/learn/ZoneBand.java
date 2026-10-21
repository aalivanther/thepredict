package akyrion.thepredict.domain.learn;

import akyrion.thepredict.domain.ZoneFact;

import java.util.Set;

/** Coarse zone of a context key (M28.2). {@code NA} is used when {@code zone.mode} is not {@code full}. */
public enum ZoneBand {
    /** No enclosure and no hazard fact. */
    OPEN,
    /** Enclosed, and not hazardous. */
    ENCLOSED,
    /** Hazardous arena, edge behind, or no safe retreat. */
    HAZARDOUS,
    /** Zone mode is not full, so the zone is not in the key. */
    NA;

    /**
     * Band of the filtered zone facts of a capture (M28.2): {@link #HAZARDOUS} with a hazardous arena, an edge behind
     * or no safe retreat; otherwise {@link #ENCLOSED} when enclosed; otherwise {@link #OPEN}. Outside {@code full}
     * the band is {@link #NA}, whatever the facts say.
     *
     * @param full whether {@code zone.mode} is {@code full}
     * @param facts the facts the session published
     * @return the band
     */
    public static ZoneBand of(boolean full, Set<ZoneFact> facts) {
        if (!full) {
            return NA;
        }
        if (facts.contains(ZoneFact.HAZARDOUS_ARENA) || facts.contains(ZoneFact.EDGE_BEHIND)
                || facts.contains(ZoneFact.NO_SAFE_RETREAT)) {
            return HAZARDOUS;
        }
        return facts.contains(ZoneFact.ENCLOSED) ? ENCLOSED : OPEN;
    }
}
