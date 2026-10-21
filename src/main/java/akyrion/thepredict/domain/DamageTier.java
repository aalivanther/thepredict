package akyrion.thepredict.domain;

/** How much damage a skill deals, as declared by the author or inferred from {@code PredictValues} (M15.1). */
public enum DamageTier {
    /** Deals no damage. */
    NONE,
    /** Deals damage of an unknown amount; only inference produces it, from an attack label without a tier. */
    UNRATED,
    /** Small damage. */
    LOW,
    /** Moderate damage. */
    MEDIUM,
    /** Large damage. */
    HIGH
}
