package akyrion.thepredict.domain;

/**
 * Context key of a difficulty row (M22.6): {@code v1:<target_kind>:<party_band>:<world_difficulty>}.
 * The subject of a prior stays empty until a later module.
 */
public final class DifficultyContext {
    private DifficultyContext() {
    }

    /**
     * Party band of a size.
     *
     * @param partySize players in the fight
     * @return {@code 1}, {@code 2_3} or {@code 4P}
     */
    public static String partyBand(int partySize) {
        return PartyBand.of(partySize).text();
    }

    /**
     * The context string.
     *
     * @param kind target kind
     * @param partySize players
     * @param worldDifficulty vanilla difficulty name
     * @return the context
     */
    public static String of(TargetKind kind, int partySize, String worldDifficulty) {
        return "v1:" + kind.token() + ":" + partyBand(partySize) + ":" + worldDifficulty;
    }
}
