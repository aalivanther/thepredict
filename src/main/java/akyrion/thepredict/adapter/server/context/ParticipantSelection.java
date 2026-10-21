package akyrion.thepredict.adapter.server.context;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.ToDoubleFunction;

/**
 * Pure selection and ranking of the participants of one capture (M07.1). Stateless and safe on any thread.
 *
 * <p>The targets of the members come first, because {@code CombatFacts} requires every target to be a
 * participant; the rest are the players nearest to any member, chosen with {@link NearestK} without sorting every
 * candidate. Threat ranks order by total MythicMobs threat over the members, then by distance, then by selection
 * order, so they are unique and deterministic.
 */
final class ParticipantSelection {
    private ParticipantSelection() {
    }

    /**
     * Chooses at most {@code limit} players.
     *
     * @param targets targets of the members, in member order; repeated UUIDs count once
     * @param distanceSquared squared distance of each perceived player to its nearest member
     * @param limit maximum number of participants
     * @return chosen players: targets first, then the nearest
     */
    static List<UUID> select(List<UUID> targets, Map<UUID, Double> distanceSquared, int limit) {
        var chosen = new LinkedHashSet<UUID>();
        for (var target : targets) {
            if (chosen.size() < limit) {
                chosen.add(target);
            }
        }
        var remaining = limit - chosen.size();
        if (remaining > 0) {
            var others = new ArrayList<UUID>();
            distanceSquared.keySet().forEach(player -> {
                if (!chosen.contains(player)) {
                    others.add(player);
                }
            });
            chosen.addAll(NearestK.select(others, remaining, player -> true, distanceSquared::get));
        }
        return List.copyOf(chosen);
    }

    /**
     * Ranks the chosen players by threat: rank {@code 1} is the highest total threat.
     *
     * @param chosen chosen players in selection order
     * @param threat total threat of a player over the members
     * @param distanceSquared squared distance of a player to its nearest member
     * @return unique rank of each player, from 1
     */
    static Map<UUID, Integer> ranks(List<UUID> chosen, ToDoubleFunction<UUID> threat,
                                    ToDoubleFunction<UUID> distanceSquared) {
        var order = new HashMap<UUID, Integer>();
        for (var index = 0; index < chosen.size(); index++) {
            order.put(chosen.get(index), index);
        }
        var sorted = new ArrayList<>(chosen);
        sorted.sort(Comparator.<UUID>comparingDouble(player -> -threat.applyAsDouble(player))
                .thenComparingDouble(distanceSquared)
                .thenComparingInt(order::get));
        var ranks = new HashMap<UUID, Integer>();
        for (var index = 0; index < sorted.size(); index++) {
            ranks.put(sorted.get(index), index + 1);
        }
        return ranks;
    }
}
