package akyrion.thepredict.engine;

import akyrion.thepredict.domain.PlayerMemory;
import akyrion.thepredict.domain.ProfileMemory;

/**
 * Administrator text for one record (M27.9, errata E27-8 applied). It is not a player-facing channel.
 */
public final class MemoryInspect {
    private MemoryInspect() {
    }

    /**
     * A record that has not been loaded.
     *
     * @return the fixed phrase
     */
    public static String notLoaded() {
        return "memoria no cargada";
    }

    /**
     * A loaded record with no fights.
     *
     * @return the fixed phrase
     */
    public static String emptyHistory() {
        return "sin historia";
    }

    /**
     * Fights, profiles, the first stored rung, and the note that the seed is computed at admission.
     *
     * @param memory the decoded record
     * @return one line
     */
    public static String loaded(PlayerMemory memory) {
        if (memory.fights() == 0 && memory.profiles().isEmpty()) {
            return emptyHistory();
        }
        var rung = "none";
        var prior = "profile";
        if (!memory.profiles().isEmpty()) {
            var stored = first(memory.profiles().getFirst().rungs());
            if (stored >= 0) {
                rung = Integer.toString(stored);
                prior = "subject " + (stored / 16.0);
            }
        }
        return "fights " + memory.fights() + " profiles " + memory.profiles().size() + " day "
                + memory.lastEpochDay() + " rung " + rung + " seed at admission prior " + prior;
    }

    private static int first(int[] rungs) {
        for (var rung : rungs) {
            if (rung != ProfileMemory.NONE) {
                return rung;
            }
        }
        return -1;
    }
}
