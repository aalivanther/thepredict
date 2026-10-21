package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ParticipantFacts;
import akyrion.thepredict.domain.TargetDistribution;

import java.util.UUID;

/**
 * Shared-threat focus of one group assignment (M25.9). The weakest participant is offered only to an entropy member.
 */
final class GroupTargetFocus {
    private final UUID[] ids = new UUID[16];
    private final double[] shared = new double[16];
    private final int[] health = new int[16];
    private int count;
    private UUID focus;
    private UUID weakest;

    private GroupTargetFocus() {
        for (var index = 0; index < health.length; index++) {
            health[index] = Integer.MAX_VALUE;
        }
    }

    /**
     * Sums the samples the members stored and picks the focus and, when health is known, the weakest.
     *
     * @param encounter the encounter
     * @param members members in visit order
     * @param count how many members
     * @param fallback leader target when no sample was stored
     * @return the choice
     */
    static GroupTargetFocus of(Encounter encounter, MobSession[] members, int count, UUID fallback) {
        var choice = new GroupTargetFocus();
        var limit = Math.min(count, members.length);
        for (var index = 0; index < limit; index++) {
            var session = members[index];
            if (session == null) {
                continue;
            }
            for (var sample = 0; sample < session.threatSampleCount(); sample++) {
                choice.add(session.threatSampleId(sample), session.threatSample(sample));
            }
        }
        choice.readHealth(encounter);
        var focusIndex = TargetDistribution.focus(choice.shared, choice.count);
        if (focusIndex >= 0) {
            choice.focus = choice.ids[focusIndex];
        }
        var weakIndex = TargetDistribution.weakest(choice.health, choice.count);
        if (weakIndex >= 0) {
            choice.weakest = choice.ids[weakIndex];
        }
        if (choice.count == 0) {
            choice.focus = null;
            choice.weakest = null;
        }
        return choice;
    }

    /**
     * Threat this session holds on {@code aim}, or the table value stored for the current target.
     *
     * @param session the writer
     * @param aim assigned participant
     * @return the threat used by the clip
     */
    static double threatOf(MobSession session, UUID aim) {
        if (session == null || aim == null) {
            return 0.0;
        }
        for (var index = 0; index < session.threatSampleCount(); index++) {
            if (aim.equals(session.threatSampleId(index))) {
                return session.threatSample(index);
            }
        }
        return session.tableOnTarget();
    }

    UUID focus() {
        return focus;
    }

    UUID weakest() {
        return weakest;
    }

    int count() {
        return count;
    }

    UUID idAt(int index) {
        return index < 0 || index >= count ? null : ids[index];
    }

    private void add(UUID id, double points) {
        if (id == null || count >= ids.length) {
            return;
        }
        for (var index = 0; index < count; index++) {
            if (id.equals(ids[index])) {
                shared[index] += points;
                return;
            }
        }
        ids[count] = id;
        shared[count] = points;
        count++;
    }

    private void readHealth(Encounter encounter) {
        var facts = encounter.lastFacts();
        if (facts.isEmpty()) {
            return;
        }
        for (ParticipantFacts participant : facts.get().participants()) {
            var ordinal = participant.health() == null ? Integer.MAX_VALUE : participant.health().ordinal();
            for (var index = 0; index < count; index++) {
                var alias = encounter.aliases().find(ids[index]);
                if (alias.isPresent() && alias.get().equals(participant.alias())) {
                    health[index] = Math.min(health[index], ordinal);
                }
            }
        }
    }
}
