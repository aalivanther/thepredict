package akyrion.thepredict.engine.duel;

import akyrion.thepredict.eval.SyntheticPlayer;

/**
 * Named campaign families (M27.11). The adjustment family is Bernoulli. The test family adds a switch.
 */
public final class CampaignScripts {
    private CampaignScripts() {
    }

    /**
     * A stable Bernoulli habit.
     *
     * @param skill skill in 0 to 1
     * @param probability habit probability
     * @return the script
     */
    public static CampaignScript stable(double skill, double probability) {
        return CampaignScript.stable(new SyntheticPlayer("duelist", skill), new PatternScript("spam_clicks", probability),
                skill);
    }

    /**
     * A habit that inverts on the second fight.
     *
     * @param skill skill in 0 to 1
     * @return the script
     */
    public static CampaignScript invert(double skill) {
        return stable(skill, 0.9).invert();
    }

    /**
     * A habit that dilutes on the second fight.
     *
     * @param skill skill in 0 to 1
     * @return the script
     */
    public static CampaignScript dilute(double skill) {
        return stable(skill, 0.9).dilute();
    }
}
