package akyrion.thepredict.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.OptionalInt;

/**
 * One outcome per profile of an encounter (M27.1, B1). A profile already eliminated is not a loss. The killing blow
 * names {@code lastKiller}. The winning profile names {@code lastVictim}.
 */
public final class EncounterOutcomePolicy {
    private EncounterOutcomePolicy() {
    }

    /**
     * Assigns an outcome to each profile.
     *
     * @param playerDied whether the player died in the encounter
     * @param clocks one row per clock
     * @param minDamageShareRatio share required for a win
     * @return the assignment
     */
    public static Assignment assign(boolean playerDied, List<ClockClose> clocks, double minDamageShareRatio) {
        var grouped = new LinkedHashMap<Integer, List<ClockClose>>();
        for (var clock : clocks) {
            grouped.computeIfAbsent(clock.profileHash(), ignored -> new ArrayList<>()).add(clock);
        }
        var outcomes = new ArrayList<ProfileOutcome>();
        var killer = OptionalInt.empty();
        var victim = OptionalInt.empty();
        for (var entry : grouped.entrySet()) {
            var rows = entry.getValue();
            var share = share(rows);
            var outcome = outcome(playerDied, rows, share, minDamageShareRatio);
            outcomes.add(new ProfileOutcome(entry.getKey(), outcome, share));
            if (rows.stream().anyMatch(ClockClose::killingBlow)) {
                killer = OptionalInt.of(entry.getKey());
            }
            if (outcome == FightOutcome.WIN) {
                victim = OptionalInt.of(entry.getKey());
            }
        }
        return new Assignment(outcomes, killer, victim);
    }

    private static FightOutcome outcome(boolean playerDied, List<ClockClose> rows, double share, double floor) {
        var engaged = rows.stream().anyMatch(ClockClose::engagedAtPlayerDeath);
        var blow = rows.stream().anyMatch(ClockClose::killingBlow);
        if (playerDied && (engaged || blow)) {
            return FightOutcome.LOSS;
        }
        var died = rows.stream().anyMatch(ClockClose::mobDied);
        var allDead = rows.stream().allMatch(ClockClose::mobDied);
        if (allDead && share >= floor) {
            return FightOutcome.WIN;
        }
        if (died && share < floor) {
            return FightOutcome.ASSIST;
        }
        if (rows.stream().allMatch(ClockClose::disengaged)) {
            return FightOutcome.DISENGAGED;
        }
        return FightOutcome.OTHER;
    }

    private static double share(List<ClockClose> rows) {
        var weighted = 0.0;
        var health = 0.0;
        for (var row : rows) {
            weighted += row.damageShare() * row.maxHealth();
            health += row.maxHealth();
        }
        return health <= 0.0 ? 0.0 : weighted / health;
    }

    /**
     * One clock at close.
     *
     * @param profileHash profile hash
     * @param disengaged the clock disengaged
     * @param mobDied the mob died before the player
     * @param engagedAtPlayerDeath a living mob of this clock was engaged when the player died
     * @param killingBlow this clock's profile landed the killing blow
     * @param damageShare share of this mob's max health that the target took away in the fight (M27.1)
     * @param maxHealth this mob's max health
     */
    public record ClockClose(int profileHash, boolean disengaged, boolean mobDied, boolean engagedAtPlayerDeath,
                             boolean killingBlow, double damageShare, double maxHealth) {
    }

    /**
     * One profile result.
     *
     * @param profileHash profile hash
     * @param outcome the outcome
     * @param damageShare weighted share
     */
    public record ProfileOutcome(int profileHash, FightOutcome outcome, double damageShare) {
    }

    /**
     * The encounter assignment.
     *
     * @param outcomes one per profile
     * @param lastKiller hash of the killing profile, empty when the player lived
     * @param lastVictim hash of the winning profile
     */
    public record Assignment(List<ProfileOutcome> outcomes, OptionalInt lastKiller, OptionalInt lastVictim) {
        /**
         * Copies the outcomes.
         */
        public Assignment {
            outcomes = List.copyOf(outcomes);
        }
    }
}
