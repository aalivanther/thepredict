package akyrion.thepredict.engine.duel;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The conduct of ThePredict over the rounds of one cell (descriptive; it changes no verdict): how often it reacted,
 * kept away or stayed in melee, moved with a skill, put a shield before an impact, lost sight of the rival behind a
 * pillar, punished a recharge and foresaw the rival's next hit.
 *
 * @param reactionsPerMinute reactions armed per minute, by name
 * @param retreatShare share of controlled ticks with the intent {@code RETREAT}
 * @param bandShare share of controlled ticks with the intent {@code KEEP_DISTANCE}
 * @param meleeShare share of ticks with the rival in melee reach
 * @param leapsPerMinute casts that moved ThePredict, per minute
 * @param guardsPerMinute shields put, per minute
 * @param guardsUsedShare share of shields that took part of a rival hit
 * @param guardsTimelyShare share of shields that took a rival hit within 20 ticks of their cast
 * @param absorbedPerRound health the shields took per round
 * @param noSightShare share of ticks without line of sight
 * @param ticksToSight mean length of a spell without line of sight, NaN without one
 * @param punishShare share of ThePredict's hits that landed while the rival's melee hit recharged
 * @param forecastShare share of controlled ticks with a forecast of the rival's next action
 * @param foreseenShare share of the rival's hits forecast as {@code STRIKE} the tick they landed, NaN without hits
 * @param modelAccuracy mean running accuracy of the pattern model at the end of a round, NaN without a model
 */
record ConductSummary(Map<String, Double> reactionsPerMinute, double retreatShare, double bandShare,
                      double meleeShare, double leapsPerMinute, double guardsPerMinute, double guardsUsedShare,
                      double guardsTimelyShare, double absorbedPerRound, double noSightShare, double ticksToSight,
                      double punishShare, double forecastShare, double foreseenShare, double modelAccuracy) {
    private static final double TICKS_PER_MINUTE = 1200;

    static ConductSummary of(BenchCell.Result cell) {
        var total = new Total();
        for (var list : List.of(cell.a(), cell.b())) {
            for (var duel : list) {
                total.add(duel.ticks(), duel.conduct());
            }
        }
        return total.summary(cell.rounds());
    }

    private static double share(double part, double whole) {
        return whole <= 0 ? 0 : part / whole;
    }

    /** The sums of a cell. */
    private static final class Total {
        private final Map<String, Double> reactions = new TreeMap<>();
        private double ticks;
        private double controlled;
        private double retreat;
        private double band;
        private double melee;
        private double leaps;
        private double guards;
        private double guardsUsed;
        private double guardsTimely;
        private double absorbed;
        private double noSight;
        private double sightGaps;
        private double hits;
        private double punishes;
        private double forecastTicks;
        private double rivalStrikes;
        private double foreseen;
        private double accuracy;
        private int models;

        void add(int roundTicks, Conduct did) {
            ticks += roundTicks;
            controlled += did.controlled();
            retreat += did.retreat();
            band += did.band();
            melee += did.melee();
            leaps += did.leaps();
            guards += did.guards();
            guardsUsed += did.guardsUsed();
            guardsTimely += did.guardsTimely();
            absorbed += did.absorbed();
            noSight += did.noSight();
            sightGaps += did.sightGaps();
            hits += did.hits();
            punishes += did.punishes();
            forecastTicks += did.forecastTicks();
            rivalStrikes += did.rivalStrikes();
            foreseen += did.foreseen();
            did.reactions().forEach((name, count) -> reactions.merge(name, (double) count, Double::sum));
            if (!Double.isNaN(did.modelAccuracy())) {
                accuracy += did.modelAccuracy();
                models++;
            }
        }

        ConductSummary summary(int rounds) {
            var minutes = ticks / TICKS_PER_MINUTE;
            var perMinute = new TreeMap<String, Double>();
            reactions.forEach((name, count) -> perMinute.put(name, share(count, minutes)));
            return new ConductSummary(perMinute, share(retreat, controlled), share(band, controlled),
                    share(melee, ticks), share(leaps, minutes), share(guards, minutes), share(guardsUsed, guards),
                    share(guardsTimely, guards), absorbed / Math.max(1, rounds), share(noSight, ticks),
                    sightGaps == 0 ? Double.NaN : noSight / sightGaps, share(punishes, hits),
                    share(forecastTicks, controlled), rivalStrikes == 0 ? Double.NaN : foreseen / rivalStrikes,
                    models == 0 ? Double.NaN : accuracy / models);
        }
    }
}
