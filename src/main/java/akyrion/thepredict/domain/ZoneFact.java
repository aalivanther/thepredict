package akyrion.thepredict.domain;

/**
 * Terrain facts v1 (M24.7). Ordinals only grow. The phrase is the English sentence without the mob alias.
 */
public enum ZoneFact {
    /** A lethal push sits behind the mob. */
    EDGE_BEHIND("has a deadly drop or hazard close behind it"),
    /** The raw retreat query found nothing. */
    NO_SAFE_RETREAT("has no safe ground to retreat to"),
    /** The target's floor is at least two above the mob. */
    TARGET_HIGH_GROUND("stands on higher ground"),
    /** The mob's floor is at least two above the target. */
    HIGH_GROUND("stands on higher ground than its target"),
    /** Cover exists within four blocks. */
    COVER_NEAR("has cover from its target close by"),
    /** Clearance at the mob is at most one block. */
    NARROW_GROUND("is fighting in a narrow passage"),
    /** The mob's cell is harmful and is not lava. */
    HAZARD_UNDERFOOT("stands on harmful ground"),
    /** The target stands next to a lethal push. Entities only. */
    TARGET_NEAR_DROP("stands next to a deadly drop or hazard"),
    /** Less than a quarter of the walkable columns see the sky. */
    ENCLOSED("the fight takes place indoors or underground"),
    /** At least five percent of the columns are a hazard, a pit or void. */
    HAZARDOUS_ARENA("the arena has hazards such as lava or deadly drops");

    private final String phrase;

    ZoneFact(String phrase) {
        this.phrase = phrase;
    }

    /**
     * The fixed English phrase, without the mob alias (rule I12).
     *
     * @return the phrase
     */
    public String phrase() {
        return phrase;
    }

    /**
     * Whether this fact describes the encounter rather than one mob.
     *
     * @return {@code true} for the enclosed arena and the hazardous arena
     */
    public boolean encounter() {
        return this == ENCLOSED || this == HAZARDOUS_ARENA;
    }

    /**
     * Whether a condition may name this fact. {@link #TARGET_NEAR_DROP} stays inside the engine.
     *
     * @return {@code false} only for the entity-only drop
     */
    public boolean exposed() {
        return this != TARGET_NEAR_DROP;
    }

    /**
     * The condition token of an exposed fact.
     *
     * @return the lower-case name
     */
    public String token() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * Resolves a condition token.
     *
     * @param token lower-case fact name
     * @return the fact
     * @throws IllegalArgumentException when the name is unknown or not exposed
     */
    public static ZoneFact exposed(String token) {
        for (var fact : values()) {
            if (fact.exposed() && fact.token().equals(token)) {
                return fact;
            }
        }
        throw new IllegalArgumentException("predictorzone fact is unknown: " + token);
    }
}
