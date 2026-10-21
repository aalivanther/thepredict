package akyrion.thepredict.engine;

import java.util.UUID;

/**
 * One air-swing diagnosis waiting for the pattern step (M26.9). The fields are reused. Nothing is allocated per tick.
 */
public final class PatternDiag {
    private UUID participant = new UUID(0, 0);
    private String weaponClass = "";
    private int chargeMillis;
    private boolean leftClickAir;
    private String blockedBy = "";
    private boolean wouldToken;
    private boolean pending;

    /**
     * Keeps one diagnosis slot. The step flushes it.
     */
    public PatternDiag() {
    }

    /**
     * Replaces the pending diagnosis.
     *
     * @param participant the enemy
     * @param weaponClass weapon in the main hand, or empty
     * @param chargeMillis attack charge
     * @param leftClickAir whether the click was in the air
     * @param blockedBy the event that blocked a token, or empty
     * @param wouldToken whether {@code airSwings: on} would have emitted a token
     */
    public void note(UUID participant, String weaponClass, int chargeMillis, boolean leftClickAir, String blockedBy,
                     boolean wouldToken) {
        this.participant = participant;
        this.weaponClass = weaponClass;
        this.chargeMillis = chargeMillis;
        this.leftClickAir = leftClickAir;
        this.blockedBy = blockedBy;
        this.wouldToken = wouldToken;
        this.pending = true;
    }

    /**
     * Emits {@code pattern_diag} once and clears the pending bit.
     *
     * @param tap the tap, not called when nothing is pending
     */
    public void flush(DecisionTap tap) {
        java.util.Objects.requireNonNull(tap, "tap");
        if (!pending) {
            return;
        }
        pending = false;
        tap.patternDiag(participant, weaponClass, chargeMillis, leftClickAir, blockedBy, wouldToken);
    }
}
