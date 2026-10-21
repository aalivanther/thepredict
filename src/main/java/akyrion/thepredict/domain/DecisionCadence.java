package akyrion.thepredict.domain;

/**
 * How often an encounter asks Jev (M20.7). The only default lives here (rule C13).
 */
public enum DecisionCadence {
    /** Economy mode: the expiry and the heartbeat of M19. */
    STANDARD,
    /** Real time: a short horizon from the capture, extended only while the situation holds. */
    REACTIVE;

    /** Delivery default (decision M20-D4). */
    public static final DecisionCadence DEFAULT = REACTIVE;
}
