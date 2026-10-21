package akyrion.thepredict.domain;

/**
 * Tell lengths, feints and recovery (M23.4). Stateless and allocation-free.
 */
public final class TelegraphPolicy {
    /** Salt of a feint draw. */
    public static final int SALT_FEINT = 4;

    private TelegraphPolicy() {
    }

    /**
     * Whether a minimum is added. Entropy turns minima off. Entities are off unless the profile enforces them.
     *
     * @param player whether the target is a player
     * @param entropy whether entropy is effective
     * @param settings resolved settings
     * @return whether the minimum applies
     */
    public static boolean minimumApplies(boolean player, boolean entropy, TelegraphSettings settings) {
        if (entropy) {
            return false;
        }
        return player ? settings.againstPlayers() : settings.againstEntities();
    }

    /**
     * Shown length. With a minimum and {@link ShortWindUp#LEAD}, at least the minimum. Otherwise the author's wind-up.
     *
     * @param windUp author's wind-up
     * @param danger class
     * @param answer answer
     * @param applies whether the minimum applies
     * @param settings resolved settings
     * @return ticks
     */
    public static int telegraphTicks(int windUp, DangerClass danger, TellAnswer answer, boolean applies,
                                     TelegraphSettings settings) {
        int authored = Math.max(0, windUp);
        if (!applies || settings.shortWindUp() == ShortWindUp.IMMEDIATE) {
            return authored;
        }
        return Math.max(authored, settings.minimumTicks(danger, answer));
    }

    /**
     * Ticks ThePredict inserts before the cast.
     *
     * @param telegraph shown length
     * @param windUp author's wind-up
     * @return {@code telegraph - windUp}, never negative
     */
    public static int leadTicks(int telegraph, int windUp) {
        return Math.max(0, telegraph - Math.max(0, windUp));
    }

    /**
     * Whether this tell is a feint. Commitment {@code v} feints with probability {@code (100-v)%}. {@code v = 100}
     * never feints. The same seed and sequence always return the same answer.
     *
     * @param seed encounter seed
     * @param sequence tell sequence of the session
     * @param commitmentPercent 0–100
     * @return whether it is a feint
     */
    public static boolean feint(long seed, long sequence, int commitmentPercent) {
        int permille = (100 - commitmentPercent) * 10;
        if (permille <= 0) {
            return false;
        }
        if (permille >= 1000) {
            return true;
        }
        return StableRandom.chance(StableRandom.draw(seed, sequence, SALT_FEINT), permille);
    }

    /**
     * Recovery after a real release. Declaration, then the native read, then the exposed default, else 0.
     *
     * @param choice the choice
     * @param settings resolved settings
     * @return ticks
     */
    public static int recoveryTicks(SkillChoice choice, TelegraphSettings settings) {
        if (choice.telegraph().recoveryTicks().isPresent()) {
            return choice.telegraph().recoveryTicks().getAsInt();
        }
        if (choice.nativeFacts().recoveryTicks().isPresent()) {
            return choice.nativeFacts().recoveryTicks().getAsInt();
        }
        var purpose = choice.purpose();
        if (purpose.isPresent() && (purpose.get().risks().contains(SkillRisk.LEAVES_CASTER_EXPOSED)
                || purpose.get().risks().contains(SkillRisk.ROOTS_CASTER))) {
            return settings.defaultRecoveryTicks();
        }
        return 0;
    }

    /**
     * Recovery after a whiff, a lost target or a rejection: at least the global whiff recovery.
     *
     * @param recovery the release recovery
     * @param settings resolved settings
     * @return ticks
     */
    public static int whiffRecovery(int recovery, TelegraphSettings settings) {
        return Math.max(recovery, settings.whiffRecoveryTicks());
    }

    /**
     * Whether the tell this cast would open lasts at least the minimum of its class and answer (A12 with A11). A
     * lethal push against a player may pass only behind such a tell. {@code AgainstPlayers: off}, entropy and
     * {@code ShortWindUp: immediate} keep the author's wind-up, so a wind-up shorter than the minimum does not count.
     * {@code NONE} and {@code LIGHT} have no minimum and never count.
     *
     * @param player whether the target is a player
     * @param entropy whether entropy is effective
     * @param danger class the tell would use
     * @param answer answer the tell would ask
     * @param windUp author's wind-up
     * @param settings resolved settings
     * @return whether the tell opens and shows at least its minimum
     */
    public static boolean showsMinimum(boolean player, boolean entropy, DangerClass danger, TellAnswer answer,
                                       int windUp, TelegraphSettings settings) {
        var minimum = settings.minimumTicks(danger, answer);
        if (minimum <= 0) {
            return false;
        }
        var applies = minimumApplies(player, entropy, settings);
        return opens(player, applies, danger, windUp)
                && telegraphTicks(windUp, danger, answer, applies, settings) >= minimum;
    }

    /**
     * Whether the cast opens a tell. A player without a minimum still opens when the wind-up is positive. A minimum
     * that applies opens unless the class is {@code NONE} or {@code LIGHT} and the wind-up is 0.
     *
     * @param player whether the target is a player
     * @param applies whether the minimum applies
     * @param danger class
     * @param windUp author's wind-up
     * @return whether to open
     */
    public static boolean opens(boolean player, boolean applies, DangerClass danger, int windUp) {
        if (!player && !applies) {
            return false;
        }
        if (applies && !danger.enforced() && windUp <= 0) {
            return false;
        }
        if (!applies && windUp <= 0) {
            return false;
        }
        return true;
    }
}
