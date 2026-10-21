package akyrion.thepredict.adapter.server.context;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * The side of two entities (rule I13, M17.2): the same side when both are MythicMobs instances with factions equal
 * ignoring case, as MythicMobs compares them; otherwise when both are on teams of the main scoreboard whose names are
 * equal ignoring case; otherwise not. Sides never apply to players, so a server that uses teams for its players keeps
 * the behavior of 0.0.1. Stateless; server thread only.
 */
public final class SideReader {
    private SideReader() {
    }

    /**
     * Tells whether two entities are of the same side.
     *
     * @param first one entity
     * @param second the other entity
     * @param lookup native identities
     * @return {@code true} when neither is a player and they share a faction or a team
     */
    public static boolean sameSide(LivingEntity first, LivingEntity second, ParticipantLookup lookup) {
        if (first instanceof Player || second instanceof Player) {
            return false;
        }
        if (equalIgnoringCase(lookup.faction(first.getUniqueId()).orElse(""),
                lookup.faction(second.getUniqueId()).orElse(""))) {
            return true;
        }
        return equalIgnoringCase(lookup.team(first).orElse(""), lookup.team(second).orElse(""));
    }

    /** Two names of a side; an empty name is "no side" and never matches (C4, SP-21). */
    private static boolean equalIgnoringCase(String first, String second) {
        return !first.isEmpty() && !second.isEmpty() && first.equalsIgnoreCase(second);
    }
}
