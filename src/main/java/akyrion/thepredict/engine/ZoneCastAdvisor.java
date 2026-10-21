package akyrion.thepredict.engine;

import akyrion.thepredict.domain.DangerRules;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.ZoneCastOrder;

/**
 * Asks the cast order before a tell can open (M24.6, revision 3). The default matches today's tell: an enforced class
 * may open and any other class casts. A release of an open tell does not call this type.
 */
@FunctionalInterface
interface ZoneCastAdvisor {
    /** Opens an enforced class and casts the rest, which is the behaviour from before the zone. */
    ZoneCastAdvisor ALLOW = (session, choice, tick) -> {
        var danger = DangerRules.classify(choice);
        return danger.enforced() ? ZoneCastOrder.Step.OPEN : ZoneCastOrder.Step.CAST;
    };

    /**
     * The step for a choice that is not already holding a tell.
     *
     * @param session the caster
     * @param choice the choice
     * @param tick server tick
     * @return the step
     */
    ZoneCastOrder.Step advise(MobSession session, SkillChoice choice, long tick);
}
