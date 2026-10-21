package akyrion.thepredict.domain;

import java.util.Objects;

/**
 * Close of one fight for the pattern summary (M22, M26). This record did not exist before M26: the close of a
 * session stays {@code SessionDifficulty.closeAs}. The two pattern fields default to {@code -1} when a caller does
 * not have them (rule W5). The failure field is {@code answerFailPermille} (errata E26-2).
 *
 * @param kind how the fight ended
 * @param elapsedTicks ticks the fight lasted, {@code >= 0}
 * @param observedLn observed log time, finite; {@code 0} for a fight of no ticks
 * @param rung last rung
 * @param reactionP50Ticks median reaction, or {@code -1}
 * @param answerFailPermille failed tell answers per thousand, or {@code -1}
 * @param targetDamageSharePermille share of this mob's max health that the target took away in the fight, in
 *        thousandths, 0 to 1000 (M27.1)
 * @param mobMaxHealth this mob's max health, which weights that share; finite and {@code >= 0}
 */
public record FightSummary(FightEventKind kind, int elapsedTicks, double observedLn, int rung,
                           int reactionP50Ticks, int answerFailPermille, int targetDamageSharePermille,
                           float mobMaxHealth) {
    /**
     * A close without pattern measurements.
     *
     * @param kind how the fight ended
     * @param elapsedTicks ticks the fight lasted
     * @param observedLn observed log time
     * @param rung last rung
     */
    public FightSummary(FightEventKind kind, int elapsedTicks, double observedLn, int rung) {
        this(kind, elapsedTicks, observedLn, rung, -1, -1);
    }

    /**
     * A close from before damage-share fields (rule W5). Both are zero.
     *
     * @param kind how the fight ended
     * @param elapsedTicks ticks the fight lasted
     * @param observedLn observed log time
     * @param rung last rung
     * @param reactionP50Ticks median reaction, or {@code -1}
     * @param answerFailPermille failed tell answers per thousand, or {@code -1}
     */
    public FightSummary(FightEventKind kind, int elapsedTicks, double observedLn, int rung, int reactionP50Ticks,
                        int answerFailPermille) {
        this(kind, elapsedTicks, observedLn, rung, reactionP50Ticks, answerFailPermille, 0, 0f);
    }

    /**
     * Checks every field the clocks fill, so a NaN never reaches the rung priors.
     *
     * @throws NullPointerException if {@code kind} is {@code null}
     * @throws IllegalArgumentException when the duration is negative, the log time or the max health is not finite,
     *         the max health is negative, or a permille is outside its range
     */
    public FightSummary {
        Objects.requireNonNull(kind, "kind");
        if (elapsedTicks < 0) {
            throw new IllegalArgumentException("elapsedTicks must be >= 0, was " + elapsedTicks);
        }
        DomainChecks.requireFinite(observedLn, "observedLn");
        if (reactionP50Ticks < -1 || answerFailPermille < -1 || answerFailPermille > 1000) {
            throw new IllegalArgumentException("fight summary pattern fields are out of range");
        }
        if (targetDamageSharePermille < 0 || targetDamageSharePermille > 1000) {
            throw new IllegalArgumentException("targetDamageSharePermille must be within [0, 1000], was "
                    + targetDamageSharePermille);
        }
        DomainChecks.requireNonNegative(mobMaxHealth, "mobMaxHealth");
    }
}
