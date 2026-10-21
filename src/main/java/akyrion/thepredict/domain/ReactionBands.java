package akyrion.thepredict.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Bands that fire before the choice of the intent (M20.4). The only default lives here (rule C13, decision M20-D3).
 */
public final class ReactionBands {
    /** Bands ahead of Jev on a fresh profile: survival and opportunity. */
    public static final Set<ReactionBand> DEFAULT_AHEAD = Set.copyOf(EnumSet.of(ReactionBand.SURVIVAL,
            ReactionBand.OPPORTUNITY));

    private ReactionBands() {
    }

    /**
     * Tells whether a band fires before the choice of the intent.
     *
     * @param ahead configured bands
     * @param band band of the reaction
     * @return whether the band is ahead
     */
    public static boolean ahead(Set<ReactionBand> ahead, ReactionBand band) {
        return ahead.contains(band);
    }
}
