package akyrion.thepredict.domain;

import java.util.Objects;

/**
 * One closed tell (M23.10). Recorded at {@code DECISIONS}. No default-cue field (errata E23-3).
 *
 * @param choiceIndex index in the listed choices
 * @param choiceId choice name
 * @param end how it closed
 * @param danger class shown
 * @param answer answer shown
 * @param feint whether the tell was a feint
 * @param result what the result window saw
 * @param profileId profile that opened it
 * @param target target id, empty when there was none
 * @param targetKind {@code player}, {@code entity} or {@code none}
 * @param startTick tick the tell opened
 * @param leadTicks ticks of advance
 * @param windupTicks author wind-up
 * @param telegraphTicks ticks shown
 * @param minTicks effective minimum
 * @param shortWindUp how a short wind-up was treated
 * @param releaseDeferred whether the release waited for a later tick
 * @param damageRatio damage dealt inside the window over the target's health
 * @param recoveryTicks recovery that followed
 * @param punished whether the target hit the caster during the watched recovery
 * @param punishDamageRatio damage of that hit
 * @param cueChannels author cue bits
 * @param rung difficulty rung at the open
 */
public record TelegraphSummary(int choiceIndex, String choiceId, TelegraphEnd end, DangerClass danger,
                               TellAnswer answer, boolean feint, TelegraphResult result, String profileId,
                               String target, String targetKind, long startTick, int leadTicks, int windupTicks,
                               int telegraphTicks, int minTicks, ShortWindUp shortWindUp, boolean releaseDeferred,
                               double damageRatio, int recoveryTicks, boolean punished, double punishDamageRatio,
                               int cueChannels, int rung) {
    /**
     * Validates the summary.
     *
     * @throws NullPointerException if a component is {@code null}
     * @throws IllegalArgumentException if a number is out of range
     */
    public TelegraphSummary {
        Objects.requireNonNull(choiceId, "choiceId");
        Objects.requireNonNull(end, "end");
        Objects.requireNonNull(danger, "danger");
        Objects.requireNonNull(answer, "answer");
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(profileId, "profileId");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(targetKind, "targetKind");
        Objects.requireNonNull(shortWindUp, "shortWindUp");
        if (choiceIndex < 0 || leadTicks < 0 || windupTicks < 0 || telegraphTicks < 0 || minTicks < 0
                || recoveryTicks < 0) {
            throw new IllegalArgumentException("tell summary counts must be >= 0");
        }
        if (!Double.isFinite(damageRatio) || !Double.isFinite(punishDamageRatio)) {
            throw new IllegalArgumentException("damage ratios must be finite");
        }
    }
}
