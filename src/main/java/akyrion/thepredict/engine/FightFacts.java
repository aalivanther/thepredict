package akyrion.thepredict.engine;

/**
 * Context of one fight line (M22.1). Empty strings and a health of {@code -1} mean the field was not known.
 *
 * @param encounter encounter id, or empty
 * @param profile profile id
 * @param target target UUID, or empty
 * @param targetKind {@code player}, {@code entity}, or empty
 * @param targetHealth health fraction left, or {@code -1}
 * @param worldDifficulty vanilla difficulty name
 * @param naturalRegeneration whether the world regenerates
 * @param partySize players near the fight
 */
public record FightFacts(String encounter, String profile, String target, String targetKind, double targetHealth,
                         String worldDifficulty, boolean naturalRegeneration, int partySize) {
    /** A line with no captured context. */
    public static final FightFacts EMPTY = new FightFacts("", "", "", "", -1.0, "NORMAL", true, 1);

    /**
     * Rejects a missing text.
     *
     * @throws NullPointerException if a text is {@code null}
     */
    public FightFacts {
        java.util.Objects.requireNonNull(encounter, "encounter");
        java.util.Objects.requireNonNull(profile, "profile");
        java.util.Objects.requireNonNull(target, "target");
        java.util.Objects.requireNonNull(targetKind, "targetKind");
        java.util.Objects.requireNonNull(worldDifficulty, "worldDifficulty");
    }
}
