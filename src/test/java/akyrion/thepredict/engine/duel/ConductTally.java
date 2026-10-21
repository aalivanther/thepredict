package akyrion.thepredict.engine.duel;

import akyrion.thepredict.domain.EnemyAction;
import akyrion.thepredict.domain.IntentView;
import akyrion.thepredict.domain.Movement;

import java.util.Map;

/**
 * Counts the {@link Conduct} of one duelist during a fair round, from the world, the published intent and the hits.
 * It only reads; nothing it counts reaches the fight.
 */
final class ConductTally {
    /** Ticks after its cast within which a shield that takes a hit counts as put before that impact. */
    static final int TIMELY_TICKS = 20;
    private static final double MELEE_GAP = 2.5;

    private final DuelWorld.Body body;
    private final java.util.Optional<DuelSkill> strike;
    private int controlled;
    private int retreat;
    private int band;
    private int melee;
    private int leaps;
    private int guards;
    private int guardsUsed;
    private int guardsTimely;
    private double absorbed;
    private int noSight;
    private int sightGaps;
    private boolean blind;
    private int hits;
    private int punishes;
    private int forecastTicks;
    private int rivalStrikes;
    private int foreseen;
    private int forecast = -1;
    private long shieldTick = Long.MIN_VALUE;
    private boolean shieldUsed = true;

    /**
     * A tally of one body.
     *
     * @param body the body
     * @param strike the melee skill of the kit, whose cooldown on the rival is a recharge; empty for a kit without
     *     skills, whose recharge is the vanilla hit
     */
    ConductTally(DuelWorld.Body body, java.util.Optional<DuelSkill> strike) {
        this.body = body;
        this.strike = strike;
    }

    /**
     * One tick of a body that ThePredict controls.
     *
     * @param view the intent it published
     * @param next the forecast of the pattern model of its rival ({@code EnemyModel.forecast}), or {@code -1}
     */
    void controlled(IntentView view, int next) {
        controlled++;
        retreat += view.movement() == Movement.RETREAT ? 1 : 0;
        band += view.movement() == Movement.KEEP_DISTANCE ? 1 : 0;
        forecast = next;
        forecastTicks += forecast >= 0 ? 1 : 0;
    }

    /** One tick of the world after it moved. */
    void tick(DuelWorld world) {
        melee += world.gap() <= MELEE_GAP ? 1 : 0;
        var sees = world.lineOfSight();
        if (!sees) {
            noSight++;
        } else if (blind) {
            sightGaps++;
        }
        blind = !sees;
    }

    /** A cast of this body. */
    void cast(DuelSkill skill, long tick) {
        if (skill.leaps()) {
            leaps++;
        }
        if (skill.shield > 0) {
            guards++;
            shieldTick = tick;
            shieldUsed = false;
        }
    }

    /** A hit of the round, by or on this body. */
    void hit(DuelWorld.Hit hit, DuelWorld world) {
        if (hit.attacker() == body && hit.damage() > 0) {
            hits++;
            punishes += cooling(world.rival(body), hit.tick()) ? 1 : 0;
        }
        if (hit.victim() != body) {
            return;
        }
        rivalStrikes++;
        foreseen += forecast == EnemyAction.STRIKE.ordinal() ? 1 : 0;
        var took = hit.damage() - hit.lost();
        if (took > 0) {
            absorbed += took;
            if (!shieldUsed) {
                shieldUsed = true;
                guardsUsed++;
                guardsTimely += hit.tick() - shieldTick <= TIMELY_TICKS ? 1 : 0;
            }
        }
    }

    /** Whether the rival's own melee hit (the strike of the kit, or the vanilla hit without one) is cooling down. */
    private boolean cooling(DuelWorld.Body rival, long tick) {
        return strike.map(skill -> !rival.ready(skill, tick)).orElse(tick < rival.nextPunch);
    }

    /** The conduct of the round. */
    Conduct conduct(Map<String, Integer> reactions, double modelAccuracy) {
        return new Conduct(reactions, controlled, retreat, band, melee, leaps, guards, guardsUsed, guardsTimely,
                absorbed, noSight, sightGaps, hits, punishes, forecastTicks, rivalStrikes, foreseen, modelAccuracy);
    }
}
