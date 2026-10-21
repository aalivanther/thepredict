package akyrion.thepredict.engine.duel;

import java.util.Map;

/**
 * What one duelist did in a fair round, for the descriptive conduct of the bench: it does not change any verdict. The
 * movements are those the intent published while ThePredict controlled the body.
 *
 * @param reactions reactions armed, by name ({@code DecisionTap.reactionFired})
 * @param controlled ticks ThePredict controlled the body
 * @param retreat controlled ticks with the intent {@code RETREAT}
 * @param band controlled ticks with the intent {@code KEEP_DISTANCE}
 * @param melee ticks with the rival within the melee reach of the profile (gap of 2.5 blocks or less)
 * @param leaps casts that moved the caster (displacement)
 * @param guards casts that put a shield
 * @param guardsUsed shields that took part of a rival hit
 * @param guardsTimely shields that took part of a rival hit within 20 ticks of their cast: put before an impact
 * @param absorbed health the shields took from rival hits
 * @param noSight ticks without line of sight between the two
 * @param sightGaps spells without line of sight that ended
 * @param hits damaging hits that landed
 * @param punishes those that landed while the rival's own melee hit was cooling down
 * @param forecastTicks controlled ticks with a forecast of the rival's next action from its pattern model
 * @param rivalStrikes hits of the rival that landed on this duelist, each a {@code STRIKE} of the pattern step
 * @param foreseen those whose forecast, published the tick before, was {@code STRIKE}
 * @param modelAccuracy running accuracy of the pattern model of the rival at the end, NaN without one
 */
record Conduct(Map<String, Integer> reactions, int controlled, int retreat, int band, int melee, int leaps, int guards,
               int guardsUsed, int guardsTimely, double absorbed, int noSight, int sightGaps, int hits, int punishes,
               int forecastTicks, int rivalStrikes, int foreseen, double modelAccuracy) {
    /** A round that measured nothing: the rounds of M18.7. */
    static final Conduct EMPTY = new Conduct(Map.of(), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, Double.NaN);

    Conduct {
        reactions = Map.copyOf(reactions);
    }
}
