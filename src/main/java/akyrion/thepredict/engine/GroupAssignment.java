package akyrion.thepredict.engine;

import java.util.UUID;

/**
 * One clipped {@code GROUP_ASSIGNMENT} handed to the threat writer (M25). The group step never calls
 * {@code setTarget}.
 */
@FunctionalInterface
public interface GroupAssignment {
    /**
     * Applies one change for a shared table.
     *
     * @param encounter the encounter
     * @param member the mob that owns the table
     * @param participant the assigned participant
     * @param magnitude clipped magnitude
     * @param tick server tick
     */
    void apply(Encounter encounter, MobSession member, UUID participant, double magnitude, long tick);
}
