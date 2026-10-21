package akyrion.thepredict.domain;

/**
 * Sixteen bits that decide whether {@code g0} is worth asking again (M25).
 */
public final class GroupSignature {
    private GroupSignature() {
    }

    /**
     * Packs the signature.
     *
     * @param memberBand members in bands of the plan (0 to 3)
     * @param leaderAlive whether the leader is alive
     * @param confidence confidence ordinal
     * @param allyInTrouble whether an ally is in trouble
     * @param participantBand participants in bands (0 to 3)
     * @param possiblePlans mask of possible plan ordinals, 7 bits
     * @return the packed value
     */
    public static int pack(int memberBand, boolean leaderAlive, GroupConfidence confidence, boolean allyInTrouble,
                           int participantBand, int possiblePlans) {
        var members = memberBand & 3;
        var participants = participantBand & 3;
        var plans = possiblePlans & 0x7f;
        var bits = members;
        bits |= (leaderAlive ? 1 : 0) << 2;
        bits |= (confidence.ordinal() & 7) << 3;
        bits |= (allyInTrouble ? 1 : 0) << 6;
        bits |= participants << 7;
        bits |= plans << 9;
        return bits;
    }
}
