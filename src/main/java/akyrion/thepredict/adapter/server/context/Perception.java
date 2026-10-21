package akyrion.thepredict.adapter.server.context;

import org.bukkit.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Natural perception of one capture (M07.2, M17.3, plan §6): a mob perceives the participants its policy admits within
 * its {@code FOLLOW_RANGE}, capped by {@code decision.searchRadiusBlocks}, and sees their visual cues only with line of
 * sight while they are not invisible, for every kind of participant. One instance lives for one capture on the server
 * thread and computes each mob-participant line of sight at most once, only for the selected participants and
 * targets. Who may be a participant is decided by {@link ParticipantPolicy}, which replaced the eligibility check of
 * 0.0.1 (players in {@code SURVIVAL} or {@code ADVENTURE}, its default).
 */
final class Perception {
    private final Map<Pair, Boolean> lineOfSight = new HashMap<>();
    private int computed;

    /**
     * Perception radius of a mob: its follow range capped by the global safety cap; the cap alone when the follow
     * range is missing, not positive or not finite.
     *
     * @param followRangeBlocks value of {@code FOLLOW_RANGE}
     * @param searchRadiusCapBlocks {@code decision.searchRadiusBlocks}
     * @return radius in blocks
     */
    static double radiusBlocks(double followRangeBlocks, double searchRadiusCapBlocks) {
        return followRangeBlocks > 0.0 && Double.isFinite(followRangeBlocks)
                ? Math.min(followRangeBlocks, searchRadiusCapBlocks) : searchRadiusCapBlocks;
    }

    /**
     * Tells whether a squared distance lies inside the perception sphere; {@code World#getNearbyPlayers} and
     * {@code getNearbyLivingEntities} return a box, so its corners are filtered out here.
     *
     * @param distanceSquared squared distance from the mob
     * @param radiusBlocks perception radius
     * @return {@code true} inside or on the sphere
     */
    static boolean withinRadius(double distanceSquared, double radiusBlocks) {
        return distanceSquared <= radiusBlocks * radiusBlocks;
    }

    /**
     * Visibility of a participant to a mob: line of sight and not invisible.
     *
     * @param lineOfSight whether the mob has line of sight to the participant
     * @param invisible whether the participant is invisible
     * @return {@code true} if the mob sees the participant
     */
    static boolean visible(boolean lineOfSight, boolean invisible) {
        return lineOfSight && !invisible;
    }

    /**
     * Line of sight from a mob to a participant, computed once per pair in this capture.
     *
     * @param mob member mob
     * @param participant perceived participant, a player or any other living entity
     * @return {@code LivingEntity#hasLineOfSight(participant)}
     */
    boolean lineOfSight(LivingEntity mob, LivingEntity participant) {
        return lineOfSight.computeIfAbsent(new Pair(mob.getUniqueId(), participant.getUniqueId()), pair -> {
            computed++;
            return mob.hasLineOfSight(participant);
        });
    }

    /**
     * Counts the lines of sight computed in this capture.
     *
     * @return computed lines of sight
     */
    int computedLinesOfSight() {
        return computed;
    }

    /** One mob and one participant. */
    private record Pair(UUID mob, UUID participant) {
    }
}
