package akyrion.thepredict.domain;

/**
 * Twenty-one bits (v3) that say whether the situation a question was asked in still holds (M20.6). A changed bit only
 * cuts an extension or asks rule 8; it never discards an answer by itself. Bits 0–16 are the v1 pack. Bits 17 and 18
 * are the zone signature, set only when the zone mode is {@code full}. Bits 19 and 20 are the pattern signature, set
 * only when the pattern mode is {@code full} and the profile sends pattern facts (M26.7).
 */
public final class SituationSignature {
    private static final EnemyPattern[] PATTERNS = EnemyPattern.values();

    private SituationSignature() {
    }

    /**
     * Packs one signature.
     *
     * @param distance distance band
     * @param self own health band
     * @param target target health band
     * @param shield target is blocking
     * @param shotReady a shot is drawn
     * @param consuming target is consuming
     * @param recharging target is recharging an attack
     * @param visible the target was seen
     * @param reachable the target is in reach of some ready skill
     * @param leaderChanged the threat leader changed since the capture
     * @param readyMask four bits, one per candidate slot, set when that slot is ready within the horizon
     * @return bits 0–16
     */
    public static int pack(DistanceBand distance, HealthBand self, HealthBand target, boolean shield,
                           boolean shotReady, boolean consuming, boolean recharging, boolean visible,
                           boolean reachable, boolean leaderChanged, int readyMask) {
        var word = distance.ordinal();
        word |= self.ordinal() << 2;
        word |= target.ordinal() << 4;
        word |= (shield ? 1 : 0) << 6;
        word |= (shotReady ? 1 : 0) << 7;
        word |= (consuming ? 1 : 0) << 8;
        word |= (recharging ? 1 : 0) << 9;
        word |= (visible ? 1 : 0) << 10;
        word |= (reachable ? 1 : 0) << 11;
        word |= (leaderChanged ? 1 : 0) << 12;
        word |= (readyMask & 15) << 13;
        return word;
    }

    /**
     * Packs a signature with the two zone bits. Outside {@code full} the caller passes both as false.
     *
     * @param distance distance band
     * @param self own health band
     * @param target target health band
     * @param shield target is blocking
     * @param shotReady a shot is drawn
     * @param consuming target is consuming
     * @param recharging target is recharging an attack
     * @param visible the target was seen
     * @param reachable the target is in reach of some ready skill
     * @param leaderChanged the threat leader changed since the capture
     * @param readyMask four bits, one per candidate slot
     * @param edgeBehind filtered edge behind the mob
     * @param noSafeRetreat filtered retreat miss
     * @return bits 0–18
     */
    public static int pack(DistanceBand distance, HealthBand self, HealthBand target, boolean shield,
                           boolean shotReady, boolean consuming, boolean recharging, boolean visible,
                           boolean reachable, boolean leaderChanged, int readyMask, boolean edgeBehind,
                           boolean noSafeRetreat) {
        var word = pack(distance, self, target, shield, shotReady, consuming, recharging, visible, reachable,
                leaderChanged, readyMask);
        if (edgeBehind) {
            word |= 1 << 17;
        }
        if (noSafeRetreat) {
            word |= 1 << 18;
        }
        return word;
    }

    /**
     * Packs a signature with the two pattern bits. They are {@code 0} unless the mode is {@code full} and pattern
     * facts are on. {@code patternBits} is the low two bits of {@code (sum of filtered ordinals + 1) mod 4}.
     *
     * @param distance distance band
     * @param self own health band
     * @param target target health band
     * @param shield target is blocking
     * @param shotReady a shot is drawn
     * @param consuming target is consuming
     * @param recharging target is recharging an attack
     * @param visible the target was seen
     * @param reachable the target is in reach of some ready skill
     * @param leaderChanged the threat leader changed since the capture
     * @param readyMask four bits, one per candidate slot
     * @param edgeBehind filtered edge behind the mob
     * @param noSafeRetreat filtered retreat miss
     * @param patternBits two bits, already reduced
     * @return bits 0–20
     */
    public static int pack(DistanceBand distance, HealthBand self, HealthBand target, boolean shield,
                           boolean shotReady, boolean consuming, boolean recharging, boolean visible,
                           boolean reachable, boolean leaderChanged, int readyMask, boolean edgeBehind,
                           boolean noSafeRetreat, int patternBits) {
        return pack(distance, self, target, shield, shotReady, consuming, recharging, visible, reachable,
                leaderChanged, readyMask, edgeBehind, noSafeRetreat) | ((patternBits & 3) << 19);
    }

    /**
     * The two pattern bits of a filtered mask. {@link EnemyPattern#FIXED_CADENCE} is never a fact, so it is ignored:
     * a mask that holds only that pattern gives {@code 0}, the same as an empty mask.
     *
     * @param filteredMask bits of patterns that passed the fact filter
     * @return {@code (sum of ordinals + 1) mod 4}, or {@code 0} when no fact is set
     */
    public static int patternBits(int filteredMask) {
        var facts = filteredMask & ~EnemyPattern.FIXED_CADENCE.bit();
        if (facts == 0) {
            return 0;
        }
        var sum = 0;
        for (var pattern : PATTERNS) {
            if ((facts & pattern.bit()) != 0) {
                sum += pattern.ordinal();
            }
        }
        return (sum + 1) & 3;
    }

    /**
     * The two pattern bits for one profile (M26.7, C13). They are {@code 0} unless the effective pattern mode is
     * {@code full} and the profile's {@code Patterns.Facts} is on; then they are {@link #patternBits(int)}.
     *
     * @param effective effective pattern mode of the profile
     * @param facts whether the profile sends pattern facts
     * @param filteredMask bits of patterns that passed the fact filter
     * @return the two bits
     */
    public static int patternBits(PatternMode effective, boolean facts, int filteredMask) {
        return effective == PatternMode.FULL && facts ? patternBits(filteredMask) : 0;
    }

    /**
     * One bit of a packed signature.
     *
     * @param signature packed word
     * @param index bit index
     * @return whether that bit is set
     */
    public static boolean bit(int signature, int index) {
        return (signature & (1 << index)) != 0;
    }
}
