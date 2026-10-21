package akyrion.thepredict.domain;

/**
 * Author cue bits (M23.3). ThePredict adds none.
 */
public final class CueChannels {
    /** A sound mechanic. */
    public static final int AUDIO = 1;
    /** A particle, glow, swing, pose, animation or model state. */
    public static final int VISUAL = 2;

    private CueChannels() {
    }

    /**
     * Whether the bits include audio.
     *
     * @param bits channel bits
     * @return whether audio is set
     */
    public static boolean audio(int bits) {
        return (bits & AUDIO) != 0;
    }

    /**
     * Whether the bits include a visual cue.
     *
     * @param bits channel bits
     * @return whether visual is set
     */
    public static boolean visual(int bits) {
        return (bits & VISUAL) != 0;
    }
}
