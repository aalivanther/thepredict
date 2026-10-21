package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ChoiceKind;
import akyrion.thepredict.domain.Delivery;
import akyrion.thepredict.domain.DistanceBand;
import akyrion.thepredict.domain.MobFacts;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.SkillRole;
import akyrion.thepredict.domain.TacticalIntent;

import java.util.UUID;

/**
 * Lets a stronger own hit that is about to be ready land first (fase de efectividad E-1). Two hits inside one window of
 * invulnerability deal only the stronger of them ({@link OwnImpacts}), so a hit whose landing would fall within
 * {@link OwnImpacts#WINDOW_TICKS} of the next landing of another one pushes that one back, or waits itself. A good
 * author orders a weak long wind-up after a strong short one for that reason; the classic that casts on every tick does
 * not, and its bolt delays its strike. This cast gives way when the other hit would lose more by waiting for it (its
 * weight times the ticks it would move) than this one loses by waiting for the other ({@link #yields}); it then waits
 * for the tick the other can start, the other goes, and this one finds its free landing after that window. The other
 * hit counts when it is a typed damaging skill at least as strong that the ready step would cast: in reach at the last
 * capture, allowed by the direction and the stance. One that is ready now counts only if it comes after this cast in the
 * order of the tick, and then for one tick. A wait that has lasted {@value #MAX_WAIT_TICKS} ticks in a row ends, so a hit
 * that never goes cannot hold another. No allocation (C12).
 *
 * <p>The strong melee hit also opens the exchange ({@link #opens}, segunda ronda de efectividad): out of melee, a
 * damaging gap-closer waits while a stronger melee hit is ready. Cast just before the contact, the gap-closer lands
 * its window when the melee hit comes into reach and pushes that hit back, so a rival that walks or leaps in strikes
 * first in every cycle. Held, the gap-closer follows the first strong hit, or goes once its wait reaches the same cap.
 */
final class StrongerFirst {
    /** Longest a weak hit waits in a row for stronger ones: two windows. */
    static final int MAX_WAIT_TICKS = 2 * OwnImpacts.WINDOW_TICKS;

    private StrongerFirst() {
    }

    /**
     * The tick a damaging cast should wait for, so a stronger own hit lands first, or {@code -1} when none overlaps.
     *
     * @param control reads cooldowns
     * @param session the caster
     * @param intent the current intent
     * @param choice the damaging choice about to be cast
     * @param index its declaration index
     * @param target the captured target
     * @param landing the tick its hit would land, after the windows of the hits in flight
     * @param tick the current tick
     * @return the tick to retry, after {@code tick}, or {@code -1}
     */
    static long until(IntentControl control, MobSession session, TacticalIntent intent, SkillChoice choice, int index,
                      UUID target, long landing, long tick) {
        var impacts = session.ownImpacts();
        if (!impacts.mayWait(index, tick, MAX_WAIT_TICKS)) {
            return -1L;
        }
        var facts = session.lastMobFacts().orElse(null);
        var until = overlapping(control, session, intent, choice, index, target, landing, facts, tick);
        if (until < 0 && opens(control, session, intent, choice, index, facts, tick)) {
            until = tick + 1;
        }
        impacts.waiting(index, tick, until >= 0);
        return until;
    }

    /** The earliest start of a stronger own hit this cast should give way to, or {@code -1} (E-1). */
    private static long overlapping(IntentControl control, MobSession session, TacticalIntent intent,
                                    SkillChoice choice, int index, UUID target, long landing, MobFacts facts,
                                    long tick) {
        var weight = OwnImpacts.weight(choice);
        var choices = session.listedChoices();
        var until = -1L;
        for (var other = 0; other < choices.length; other++) {
            var stronger = choices[other];
            if (other == index || !counts(session, intent, stronger, weight, facts, tick)) {
                continue;
            }
            var at = Math.max(control.readyTickUnnoted(session, stronger, other, tick), session.choiceFreeTick(other));
            if (at <= tick && !ReadyCasts.after(session, intent, choice, index, stronger, other)) {
                continue;
            }
            var start = Math.max(at, tick);
            var strength = OwnImpacts.weight(stronger);
            var theirs = session.ownImpacts().freeLanding(target, start + stronger.busyTicks(), strength, tick);
            if (yields(weight, landing, strength, theirs)) {
                var retry = Math.max(start, tick + 1);
                until = until < 0 ? retry : Math.min(until, retry);
            }
        }
        return until;
    }

    /**
     * Whether a damaging gap-closer gives the opening of the exchange to a stronger melee hit: the target was not in
     * the melee band at the last capture, and a typed melee skill that deals more is ready and could be cast by the
     * ready step (direction and stance). The gap-closer then waits; once in melee, or once that hit is spent, it goes.
     *
     * @param control reads cooldowns
     * @param session the caster
     * @param intent the current intent
     * @param choice the damaging choice about to be cast
     * @param index its declaration index
     * @param facts the last capture, {@code null} before one
     * @param tick the current tick
     * @return whether the gap-closer waits
     */
    static boolean opens(IntentControl control, MobSession session, TacticalIntent intent, SkillChoice choice,
                         int index, MobFacts facts, long tick) {
        var purpose = choice.purpose();
        if (purpose.isEmpty() || !purpose.get().roles().contains(SkillRole.GAP_CLOSE) || facts == null
                || facts.target().isEmpty() || facts.target().get().distance() == DistanceBand.MELEE) {
            return false;
        }
        var weight = OwnImpacts.weight(choice);
        var choices = session.listedChoices();
        for (var other = 0; other < choices.length; other++) {
            var melee = choices[other];
            if (other != index && strongerMelee(melee, weight) && control.ready(session, melee, other, tick)
                    && ReadyCasts.couldFire(session, intent, melee, tick)) {
                return true;
            }
        }
        return false;
    }

    /** A typed damaging skill delivered in melee that deals more than {@code weight}. */
    private static boolean strongerMelee(SkillChoice melee, int weight) {
        var purpose = melee.purpose();
        return melee.kind() == ChoiceKind.MYTHIC_SKILL && purpose.isPresent()
                && purpose.get().delivery() == Delivery.MELEE && OwnImpacts.hits(melee)
                && OwnImpacts.weight(melee) > weight;
    }

    /**
     * Whether a hit landing at {@code landing} should give way to another landing at {@code theirs}: the two overlap
     * inside one window, and the damage the other would lose by waiting for this one ({@code its weight × the ticks it
     * moves}) exceeds what this one loses by waiting for the other.
     *
     * @param weight the weight of this hit
     * @param landing its landing if cast now
     * @param strength the weight of the other hit
     * @param theirs the landing of the other at its first start
     * @return whether this hit waits
     */
    static boolean yields(int weight, long landing, int strength, long theirs) {
        var window = OwnImpacts.WINDOW_TICKS;
        if (theirs >= landing + window || landing >= theirs + window) {
            return false;
        }
        var theyWait = landing + window - theirs;
        var iWait = theirs + window - landing;
        return (long) strength * theyWait > (long) weight * iWait;
    }

    /** A typed damaging skill at least as strong, that the ready step could cast: in reach, direction and stance. */
    private static boolean counts(MobSession session, TacticalIntent intent, SkillChoice stronger, int weight,
                                  MobFacts facts, long tick) {
        if (stronger.kind() != ChoiceKind.MYTHIC_SKILL || stronger.purpose().isEmpty() || !OwnImpacts.hits(stronger)
                || OwnImpacts.weight(stronger) < weight) {
            return false;
        }
        if (facts != null && facts.choicesOutOfReach().contains(stronger.id())) {
            return false;
        }
        return ReadyCasts.couldFire(session, intent, stronger, tick);
    }
}
