package akyrion.thepredict.adapter.server.events;

import akyrion.thepredict.engine.DecisionMetrics;
import akyrion.thepredict.engine.EncounterRegistry;
import akyrion.thepredict.engine.SessionRegistry;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;

import java.util.Objects;
import java.util.UUID;

/**
 * Counts four vanilla target reasons (M25.11). Monitor and ignore-cancelled. The listener never cancels the event.
 * A reason outside the four, or an entity that is neither a member nor a participant, is ignored.
 */
public final class GroupTargetWatch implements Listener {
    private final SessionRegistry sessions;
    private final EncounterRegistry encounters;
    private final DecisionMetrics metrics;

    /**
     * Creates the listener.
     *
     * @param sessions member lookup
     * @param encounters participant lookup
     * @param metrics counters
     */
    public GroupTargetWatch(SessionRegistry sessions, EncounterRegistry encounters, DecisionMetrics metrics) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.encounters = Objects.requireNonNull(encounters, "encounters");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    /**
     * Counts the reason when the actor or the target is known. Does not cancel.
     *
     * @param event a living target change
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        var actor = event.getEntity();
        var target = event.getTarget();
        var world = actor.getWorld() == null ? null : actor.getWorld().getUID();
        count(actor.getUniqueId(), target == null ? null : target.getUniqueId(), world, event.getReason());
    }

    /**
     * Counts one reason in O(1) when the actor or the target is a member or a participant. Never cancels.
     *
     * @param actor actor id
     * @param target target id, or null
     * @param world world of the actor
     * @param reason vanilla reason
     */
    public void count(UUID actor, UUID target, UUID world, EntityTargetEvent.TargetReason reason) {
        var bucket = bucket(reason);
        if (bucket < 0 || actor == null) {
            return;
        }
        if (!known(actor, world) && (target == null || !known(target, world))) {
            return;
        }
        metrics.noteNativeGroupAlert(bucket);
    }

    /**
     * Reason bucket, or -1 when the reason is not one of the four.
     *
     * @param reason vanilla reason
     * @return 0 to 3, or -1
     */
    public static int bucket(EntityTargetEvent.TargetReason reason) {
        if (reason == null) {
            return -1;
        }
        return switch (reason) {
            case TARGET_ATTACKED_NEARBY_ENTITY -> 0;
            case REINFORCEMENT_TARGET -> 1;
            case FOLLOW_LEADER -> 2;
            case OWNER_ATTACKED_TARGET -> 3;
            default -> -1;
        };
    }

    private boolean known(UUID id, UUID world) {
        if (sessions.find(id).isPresent()) {
            return true;
        }
        return world != null && !encounters.ofParticipant(world, id).isEmpty();
    }
}
