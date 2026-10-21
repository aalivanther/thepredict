package akyrion.thepredict.domain;

/** Short movement the tick layer publishes on the intent view (M20.5). {@code NATIVE} leaves the native goals. */
public enum MicroBehaviour {
    /** The native goals move the mob. */
    NATIVE,
    /** Wait at the edge of the target's reach. Players only. */
    HOVER,
    /** Circle inside the distance band. */
    ORBIT,
    /** Step aside from a shot. */
    SIDESTEP,
    /** Close in while the target is exposed. */
    ENTER,
    /** Step back after a cast or out of a critical melee. */
    BACKSTEP,
    /** Stay still. A wind-up or a stagger publishes this in {@code FULL}. */
    STILL,
    /** Step toward a safe retreat for eight ticks. */
    REPOSITION,
    /** Step toward the surround slot published by the group (M25). */
    SLOT
}
