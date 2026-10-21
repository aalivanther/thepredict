package akyrion.thepredict.engine.duel;

import akyrion.thepredict.domain.DangerClass;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.ShortWindUp;
import akyrion.thepredict.domain.TelegraphEnd;
import akyrion.thepredict.domain.TelegraphResult;
import akyrion.thepredict.domain.TelegraphSettings;
import akyrion.thepredict.domain.TelegraphSummary;
import akyrion.thepredict.domain.TellAnswer;
import akyrion.thepredict.engine.DecisionTap;
import akyrion.thepredict.engine.FairnessDrive;

import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * One minute of telegraphed hits against a sword player or a synthetic (M23.10). {@code lead} shows the effective
 * minimum, so a medium or harder hit is never unwarned. The player answers with {@link TellResponseModel}.
 */
final class SwordPlayerDuelist {
    static final String[] ARCHETYPES = {"shield-flanker", "kiter-archer", "tower", "crit-jumper"};
    static final double[] SKILLS = {0.25, 0.5, 0.75, 1.0};
    static final int[] RUNGS = {8, 4, 0};
    private static final int TELLS = 10;
    private static final long TICKS = 1_200L;
    private static final double RTT_MILLIS = 100.0;
    private static final DangerClass[] CLASSES = {DangerClass.MEDIUM, DangerClass.HEAVY, DangerClass.SEVERE};
    private static final TellAnswer[] ANSWERS = {TellAnswer.BLOCK, TellAnswer.DODGE, TellAnswer.BACK_OFF};
    private static final MobKey MOB = new MobKey(new UUID(0x23L, 0x10L), 0);

    private SwordPlayerDuelist() {
    }

    /**
     * One fight. A retreating player whiffs every tell. Any other player answers in time or is hit.
     *
     * @param name {@code sword} or an archetype
     * @param skill skill in 0 to 1; {@code sword} is the typical player
     * @param rung difficulty rung, recorded and not used to shorten a tell
     * @param seed draw seed
     * @param tap where telegraph lines go, or {@link DecisionTap#NONE}
     * @param retreats whether the player leaves reach before the release
     * @return the fight
     */
    static Fight fight(String name, double skill, int rung, long seed, DecisionTap tap, boolean retreats) {
        var speed = speed(name, skill);
        var settings = TelegraphSettings.DEFAULT;
        var counts = new int[11];
        var drive = new FairnessDrive();
        for (var index = 0; index < TELLS; index++) {
            one(name, speed, settings, rung, seed, index, tap, retreats, counts, drive);
        }
        return new Fight(metrics(counts), counts[4], counts[5], counts[6], counts[7], counts[8], counts[9], counts[10]);
    }

    private static void one(String name, TellResponseModel.Player speed, TelegraphSettings settings, int rung,
                            long seed, int index, DecisionTap tap, boolean retreats, int[] counts, FairnessDrive drive) {
        var danger = CLASSES[index % CLASSES.length];
        var answer = ANSWERS[(index / CLASSES.length) % ANSWERS.length];
        var minimum = settings.minimumTicks(danger, answer);
        var step = drive.tell(danger, answer, index * 80L, !retreats);
        var shown = step.shown();
        if (step.opened()) {
            counts[4]++;
        }
        if (danger.enforced() && shown < minimum) {
            counts[5]++;
            counts[6]++;
        }
        if (step.selfAbort()) {
            counts[8]++;
        }
        counts[9] += step.stanceChanges();
        if (step.punished()) {
            counts[10]++;
        }
        var result = retreats ? TelegraphResult.AVOIDED : resultOf(shown, answer, speed, seed + rung, index);
        var end = retreats ? TelegraphEnd.WHIFFED : TelegraphEnd.RELEASED;
        tally(counts, danger, result, retreats);
        if (tap.detail() != akyrion.thepredict.engine.TapDetail.NONE) {
            tap.telegraphClosed(MOB, line(name, danger, answer, shown, minimum, rung, index, end, result));
        }
    }

    private static TelegraphResult resultOf(int shown, TellAnswer answer, TellResponseModel.Player speed, long seed,
                                           int index) {
        var made = TellResponseModel.succeeds(shown, RTT_MILLIS, speed, TellResponseModel.actionTicks(answer), seed,
                index);
        if (!made) {
            return TelegraphResult.LANDED;
        }
        return answer == TellAnswer.BLOCK ? TelegraphResult.BLOCKED : TelegraphResult.AVOIDED;
    }

    private static void tally(int[] counts, DangerClass danger, TelegraphResult result, boolean retreats) {
        switch (result) {
            case LANDED -> counts[0]++;
            case AVOIDED -> counts[1]++;
            case BLOCKED -> counts[2]++;
            case NONE -> {
            }
        }
        if (!retreats && result == TelegraphResult.LANDED) {
            counts[3]++;
        }
        if (danger == DangerClass.SEVERE) {
            switch (result) {
                case LANDED -> counts[7] += 1_000_000;
                case AVOIDED -> counts[7] += 1_000;
                case BLOCKED -> counts[7] += 1;
                case NONE -> {
                }
            }
        }
    }

    private static TellResponseModel.Player speed(String name, double skill) {
        if ("sword".equals(name) || (skill >= 0.5 && skill < 0.75)) {
            return TellResponseModel.Player.TYPICAL;
        }
        if (skill < 0.5) {
            return TellResponseModel.Player.SLOW;
        }
        return TellResponseModel.Player.FAST;
    }

    private static TelegraphSummary line(String name, DangerClass danger, TellAnswer answer, int shown, int minimum,
                                         int rung, int index, TelegraphEnd end, TelegraphResult result) {
        return new TelegraphSummary(index, "hit-" + index, end, danger, answer, false, result, name, "player",
                "player", index * 40L, shown, 0, shown, minimum, ShortWindUp.LEAD, false,
                result == TelegraphResult.LANDED ? 0.1 : 0.0, 20, false, 0.0, 0, rung);
    }

    private static RoundMetrics metrics(int[] counts) {
        return new RoundMetrics("", TICKS, OptionalLong.empty(), 0.0, 0.0, Map.of(), Map.of(), Map.of(), Map.of(), 0,
                0, Map.of(), 0.0, 0L, 0L, 0, 0L, TELLS, counts[0], counts[1], counts[2], counts[3], 0, counts[5]);
    }

    /**
     * @param metrics round counters
     * @param opened tells opened
     * @param unwarnedHits hits without the minimum
     * @param violations fairness violations
     * @param severePacked severe landed × 1_000_000 + avoided × 1_000 + blocked
     * @param selfAborts releases rejected or dropped with the session
     * @param stanceChanges visible stance changes the machine published
     * @param punished recovery hits taken from a rival in range and off cooldown
     */
    record Fight(RoundMetrics metrics, int opened, int unwarnedHits, int violations, int severePacked, int selfAborts,
                 int stanceChanges, int punished) {
        int severeLanded() {
            return severePacked / 1_000_000;
        }

        int severeAvoided() {
            return (severePacked / 1_000) % 1_000;
        }

        int severeBlocked() {
            return severePacked % 1_000;
        }
    }
}
