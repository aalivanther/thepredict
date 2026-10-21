package akyrion.thepredict.domain;

import java.util.Objects;

/**
 * Resolved tell settings of one profile (M23.4). Global keys are inherited; commitment lives on the profile.
 * One set of defaults (rule C13).
 *
 * @param againstPlayers whether a player target is enforced
 * @param againstEntities whether an entity target is enforced
 * @param light minimum base of {@link DangerClass#LIGHT}
 * @param medium minimum base of {@link DangerClass#MEDIUM}
 * @param heavy minimum base of {@link DangerClass#HEAVY}
 * @param severe minimum base of {@link DangerClass#SEVERE}
 * @param blockTicks added ticks of {@link TellAnswer#BLOCK}
 * @param dodgeTicks added ticks of {@link TellAnswer#DODGE}
 * @param backOffTicks added ticks of {@link TellAnswer#BACK_OFF}
 * @param breakSightTicks added ticks of {@link TellAnswer#BREAK_SIGHT}
 * @param interruptTicks added ticks of {@link TellAnswer#INTERRUPT}
 * @param deliveryMarginTicks ticks added for delivery
 * @param maxReleasesPerTick releases admitted in one tick
 * @param whiffRecoveryTicks recovery after a whiff, a lost target or a rejection
 * @param whiffCooldownTicks cooldown of that choice after those ends and after a feint
 * @param resultWindowTicks ticks watched after impact
 * @param commitmentPercent chance the tell is real, 50–100
 * @param entropyCommitmentPercent chance the tell is real while entropy is on, 0–50
 * @param shortWindUp what a short wind-up does
 * @param defaultRecoveryTicks recovery when the skill leaves the caster exposed or roots it and nothing else says
 */
public record TelegraphSettings(boolean againstPlayers, boolean againstEntities, int light, int medium, int heavy,
                                int severe, int blockTicks, int dodgeTicks, int backOffTicks, int breakSightTicks,
                                int interruptTicks, int deliveryMarginTicks, int maxReleasesPerTick,
                                int whiffRecoveryTicks, int whiffCooldownTicks, int resultWindowTicks,
                                int commitmentPercent, int entropyCommitmentPercent, ShortWindUp shortWindUp,
                                int defaultRecoveryTicks) {
    /** Installation defaults. */
    public static final TelegraphSettings DEFAULT = new TelegraphSettings(true, false, 0, 8, 16, 24, 0, 0, 3, 3, 3, 1,
            32, 10, 40, 10, 100, 25, ShortWindUp.DEFAULT, 20);

    /**
     * Validates ranges. {@code light ≤ medium ≤ heavy ≤ severe}, {@code heavy ≥ 10}.
     *
     * @throws NullPointerException if the short wind-up is null
     * @throws IllegalArgumentException if a range fails
     */
    public TelegraphSettings {
        Objects.requireNonNull(shortWindUp, "shortWindUp");
        DomainChecks.requireRange(light, 0, 20, "light");
        DomainChecks.requireRange(medium, light, 30, "medium");
        DomainChecks.requireRange(heavy, Math.max(medium, 10), 40, "heavy");
        DomainChecks.requireRange(severe, heavy, 60, "severe");
        DomainChecks.requireRange(blockTicks, 0, 10, "blockTicks");
        DomainChecks.requireRange(dodgeTicks, 0, 10, "dodgeTicks");
        DomainChecks.requireRange(backOffTicks, 0, 10, "backOffTicks");
        DomainChecks.requireRange(breakSightTicks, 0, 10, "breakSightTicks");
        DomainChecks.requireRange(interruptTicks, 0, 10, "interruptTicks");
        DomainChecks.requireRange(deliveryMarginTicks, 0, 4, "deliveryMarginTicks");
        DomainChecks.requireRange(maxReleasesPerTick, 4, 256, "maxReleasesPerTick");
        DomainChecks.requireRange(whiffRecoveryTicks, 0, 40, "whiffRecoveryTicks");
        DomainChecks.requireRange(whiffCooldownTicks, 0, 400, "whiffCooldownTicks");
        DomainChecks.requireRange(resultWindowTicks, 2, 60, "resultWindowTicks");
        DomainChecks.requireRange(commitmentPercent, 50, 100, "commitmentPercent");
        DomainChecks.requireRange(entropyCommitmentPercent, 0, 50, "entropyCommitmentPercent");
        DomainChecks.requireRange(defaultRecoveryTicks, 0, 100, "defaultRecoveryTicks");
    }

    /**
     * Base plus answer plus margin. {@code NONE} and {@code LIGHT} stay 0.
     *
     * @param danger class
     * @param answer answer
     * @return ticks
     */
    public int minimumTicks(DangerClass danger, TellAnswer answer) {
        if (!danger.enforced()) {
            return 0;
        }
        int base = switch (danger) {
            case MEDIUM -> medium;
            case HEAVY -> heavy;
            case SEVERE -> severe;
            default -> 0;
        };
        int answerTicks = switch (answer) {
            case BLOCK -> blockTicks;
            case DODGE -> dodgeTicks;
            case BACK_OFF -> backOffTicks;
            case BREAK_SIGHT -> breakSightTicks;
            case INTERRUPT -> interruptTicks;
        };
        return base + answerTicks + deliveryMarginTicks;
    }

    /**
     * Copy with another commitment. Out of range is rejected, never clipped (errata E23-9).
     *
     * @param percent 50–100
     * @param entropyPercent 0–50
     * @return the copy
     */
    public TelegraphSettings withCommitment(int percent, int entropyPercent) {
        return new TelegraphSettings(againstPlayers, againstEntities, light, medium, heavy, severe, blockTicks,
                dodgeTicks, backOffTicks, breakSightTicks, interruptTicks, deliveryMarginTicks, maxReleasesPerTick,
                whiffRecoveryTicks, whiffCooldownTicks, resultWindowTicks, percent, entropyPercent, shortWindUp,
                defaultRecoveryTicks);
    }
}
