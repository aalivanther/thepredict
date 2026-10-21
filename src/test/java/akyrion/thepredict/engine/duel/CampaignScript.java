package akyrion.thepredict.engine.duel;

import akyrion.thepredict.eval.SyntheticPlayer;

/**
 * One multi-fight script (M27.11). Only {@code MemoryEval} reads {@link #label()}.
 */
public final class CampaignScript {
    private final SyntheticPlayer player;
    private final PatternScript pattern;
    private final double skill;
    private final String rule;
    private final int adaptAfter;
    private final String nextStyle;
    private final int switchFight;
    private final boolean invert;
    private final boolean dilute;
    private final boolean drift;
    private final int returnAfterDays;
    private final double skillDelta;

    private CampaignScript(SyntheticPlayer player, PatternScript pattern, double skill, String rule, int adaptAfter,
                           String nextStyle, int switchFight, boolean invert, boolean dilute, boolean drift,
                           int returnAfterDays, double skillDelta) {
        this.player = player;
        this.pattern = pattern;
        this.skill = skill;
        this.rule = rule;
        this.adaptAfter = adaptAfter;
        this.nextStyle = nextStyle;
        this.switchFight = switchFight;
        this.invert = invert;
        this.dilute = dilute;
        this.drift = drift;
        this.returnAfterDays = returnAfterDays;
        this.skillDelta = skillDelta;
    }

    /**
     * A habit that does not change.
     *
     * @param player the synthetic
     * @param pattern the labelled layer
     * @param skill skill in 0 to 1
     * @return the script
     */
    public static CampaignScript stable(SyntheticPlayer player, PatternScript pattern, double skill) {
        return new CampaignScript(player, pattern, skill, "stable", 0, "", -1, false, false, false, -1, 0.0);
    }

    /**
     * Changes style after {@code k} exploited fights.
     *
     * @param exploitedFights fights before the change
     * @param newStyle the next ground-truth name
     * @return a copy
     */
    public CampaignScript adaptsAfterExploited(int exploitedFights, String newStyle) {
        return copy("adapt", exploitedFights, newStyle, switchFight, invert, dilute, drift, returnAfterDays,
                skillDelta);
    }

    /**
     * Changes style at a fight index.
     *
     * @param fight the first fight of the new style
     * @return a copy
     */
    public CampaignScript switchAt(int fight) {
        return copy("switch", adaptAfter, nextStyle, fight, invert, dilute, drift, returnAfterDays, skillDelta);
    }

    /**
     * Inverts the habit from the second fight.
     *
     * @return a copy
     */
    public CampaignScript invert() {
        return copy("invert", adaptAfter, nextStyle, switchFight, true, dilute, drift, returnAfterDays, skillDelta);
    }

    /**
     * Dilutes the habit from the second fight.
     *
     * @return a copy
     */
    public CampaignScript dilute() {
        return copy("dilute", adaptAfter, nextStyle, switchFight, invert, true, drift, returnAfterDays, skillDelta);
    }

    /**
     * Drifts the style name with the fight index.
     *
     * @return a copy
     */
    public CampaignScript drift() {
        return copy("drift", adaptAfter, nextStyle, switchFight, invert, dilute, true, returnAfterDays, skillDelta);
    }

    /**
     * Restores the original style after a number of days.
     *
     * @param days days away
     * @return a copy
     */
    public CampaignScript returnsAfter(int days) {
        return copy("return", adaptAfter, nextStyle, switchFight, invert, dilute, drift, days, skillDelta);
    }

    /**
     * Raises skill by a delta, clipped to 1.
     *
     * @param delta the growth
     * @return a copy
     */
    public CampaignScript skillGrowth(double delta) {
        return copy("skill", adaptAfter, nextStyle, switchFight, invert, dilute, drift, returnAfterDays, delta);
    }

    /**
     * Ground-truth label. Only {@code MemoryEval} may call this.
     *
     * @return the rule and the pattern name
     */
    public String label() {
        return rule + ":" + pattern.label();
    }

    /**
     * Style name at one fight.
     *
     * @param fight fight index
     * @param exploited fights already exploited
     * @param day simulated day
     * @return the style name
     */
    public String styleAt(int fight, int exploited, long day) {
        if (returnAfterDays >= 0 && day >= returnAfterDays) {
            return pattern.label();
        }
        if (adaptAfter > 0 && exploited >= adaptAfter && !nextStyle.isEmpty()) {
            return nextStyle;
        }
        if (switchFight >= 0 && fight >= switchFight) {
            return "switched";
        }
        if (invert && fight > 0) {
            return "inverted";
        }
        if (dilute && fight > 0) {
            return "diluted";
        }
        if (drift) {
            return pattern.label() + "-" + fight;
        }
        return pattern.label();
    }

    /**
     * Skill after growth.
     *
     * @return the clipped skill
     */
    public double grownSkill() {
        var next = skill + skillDelta;
        if (next < 0.0) {
            return 0.0;
        }
        return next > 1.0 ? 1.0 : next;
    }

    /**
     * The synthetic.
     *
     * @return the player
     */
    public SyntheticPlayer player() {
        return player;
    }

    /**
     * The pattern layer.
     *
     * @return the layer
     */
    public PatternScript pattern() {
        return pattern;
    }

    private CampaignScript copy(String nextRule, int nextAdapt, String style, int nextSwitch, boolean nextInvert,
                                boolean nextDilute, boolean nextDrift, int nextReturn, double nextDelta) {
        return new CampaignScript(player, pattern, skill, nextRule, nextAdapt, style, nextSwitch, nextInvert,
                nextDilute, nextDrift, nextReturn, nextDelta);
    }
}
