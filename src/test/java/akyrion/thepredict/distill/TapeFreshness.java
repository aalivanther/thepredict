package akyrion.thepredict.distill;

/**
 * Whether a stored champion tape still matches the live profile and model (M29.6). A mismatch is
 * {@code TAPE_STALE}. Nothing here calls Jev.
 */
public final class TapeFreshness {
    private TapeFreshness() {
    }

    /**
     * The status.
     *
     * @param recordedFingerprint fingerprint stored with the tape
     * @param liveFingerprint fingerprint now
     * @param recordedModel model stored with the tape
     * @param liveModel model now
     * @return {@code FRESH} or {@code TAPE_STALE}
     */
    public static String status(String recordedFingerprint, String liveFingerprint, String recordedModel,
                                String liveModel) {
        if (recordedFingerprint.equals(liveFingerprint) && recordedModel.equals(liveModel)) {
            return "FRESH";
        }
        return "TAPE_STALE";
    }
}
