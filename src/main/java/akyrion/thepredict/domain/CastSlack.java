package akyrion.thepredict.domain;

/**
 * Slack on a damaging cast (M22.7). The anchor is the first tick on which every other term already allows the cast.
 * The ready tick is {@code max(native, nextChoice, nextMelee, anchor + slack)}. Slack 0 does not move the tick.
 * The anchor is never replaced by "now" while it still holds.
 */
public final class CastSlack {
    /** No anchor. */
    public static final long UNANCHORED = Long.MIN_VALUE;

    private CastSlack() {
    }

    /**
     * Whether a choice is damaging for the slack: untyped counts, and a purpose with damage other than
     * {@link DamageTier#NONE} counts unless it escapes, defends or sustains.
     *
     * @param choice the choice
     * @return whether slack applies
     */
    public static boolean damaging(SkillChoice choice) {
        var purpose = choice.purpose();
        if (purpose.isEmpty()) {
            return true;
        }
        var body = purpose.get();
        if (body.damage() == DamageTier.NONE) {
            return false;
        }
        var roles = body.orderedRoles();
        return !roles.contains(SkillRole.ESCAPE) && !roles.contains(SkillRole.DEFENSE)
                && !roles.contains(SkillRole.SUSTAIN);
    }

    /**
     * Next anchor.
     *
     * @param previous the stored anchor, or {@link #UNANCHORED}
     * @param now current tick
     * @param termsAllow whether native readiness, cooldowns and reach already allow the cast
     * @return the anchor to store
     */
    public static long anchor(long previous, long now, boolean termsAllow) {
        if (!termsAllow) {
            return UNANCHORED;
        }
        return previous == UNANCHORED ? now : previous;
    }

    /**
     * Ready tick after slack.
     *
     * @param nativeAt tick from the native wait and the cooldowns
     * @param anchor stored anchor
     * @param slackTicks slack
     * @param damaging whether this choice takes slack
     * @return the ready tick; unchanged when slack is 0 or the choice is not damaging
     */
    public static long ready(long nativeAt, long anchor, int slackTicks, boolean damaging) {
        if (!damaging || slackTicks <= 0 || anchor == UNANCHORED) {
            return nativeAt;
        }
        return Math.max(nativeAt, anchor + slackTicks);
    }
}
