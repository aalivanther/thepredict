package akyrion.thepredict.engine.duel;

import akyrion.thepredict.domain.ChoiceKind;
import akyrion.thepredict.domain.ImpactTiming;
import akyrion.thepredict.domain.QuestionSpec;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TacticalProfile;

import java.util.Optional;
import java.util.UUID;

/**
 * One-step lookahead (M22.3, §7.5 of the simulator audit): for each option, a copy of the world and of the rival is
 * played for {@value #HORIZON_TICKS} ticks while the asking mob follows the option's movement and casts the option's
 * choice whenever it is ready, in reach and not held by the rival's invulnerability, and the rival plays its own
 * rules; the option with the best effect wins, ties in question order. The effect is the share of health the rival lost
 * minus the share the mob lost, plus one for a kill and minus one for a death. It reads the world, never the engine's
 * score, and nothing after the send.
 */
final class LookaheadOracle implements DecisionOracle {
    static final int HORIZON_TICKS = 40;

    private final int horizonTicks;
    private final Kit kit;

    LookaheadOracle(int horizonTicks, Kit kit) {
        this.horizonTicks = horizonTicks;
        this.kit = kit;
    }

    @Override
    public TacticId best(DuelWorld snapshot, ClassicDuelist rival, QuestionSpec question, TacticalProfile profile,
                         int index, long tick) {
        var mob = question.mob().entityId();
        TacticId best = question.options().getFirst();
        var bestEffect = Double.NEGATIVE_INFINITY;
        for (var option : question.options()) {
            var effect = play(snapshot, rival, option, profile, mob, index, tick);
            if (effect > bestEffect) {
                best = option;
                bestEffect = effect;
            }
        }
        return best;
    }

    private double play(DuelWorld snapshot, ClassicDuelist rival, TacticId option, TacticalProfile profile, UUID mob,
                        int index, long tick) {
        var world = snapshot.copy();
        var classic = rival.copy(world);
        var me = world.red.id.equals(mob) ? world.red : world.blue;
        var them = world.rival(me);
        var mine = me.health;
        var theirs = them.health;
        var tactic = profile.tactic(option).orElseThrow();
        var choice = tactic.choice().map(id -> profile.choices().get(id));
        for (var step = 0; step < horizonTicks && !world.over(); step++) {
            var now = tick + step;
            Motion.select(world, me, Motion.intent(world, tactic.movement(), profile.movement()), index + step);
            choice.ifPresent(found -> act(world, me, found, profile, now));
            classic.decide(index + step, now, (body, skill) -> {
            });
            Motion.advance(world);
            world.commit(now, ((index + step) & 1) == 0);
            world.step(now);
        }
        var effect = (theirs - Math.max(0, them.health)) / them.maxHealth - (mine - Math.max(0, me.health)) / me.maxHealth;
        return effect + (them.health <= 0 ? 1 : 0) - (me.health <= 0 ? 1 : 0);
    }

    private void act(DuelWorld world, DuelWorld.Body me, SkillChoice choice, TacticalProfile profile, long tick) {
        var rival = world.rival(me);
        var waits = ImpactTiming.mustWait(DuelSignals.hittable(rival, tick), choice.busyTicks());
        if (choice.kind() == ChoiceKind.VANILLA_MELEE) {
            if (world.gap() <= profile.movement().meleeReachBlocks() && !waits) {
                world.attack(me, tick);
            }
            return;
        }
        Optional<DuelSkill> skill = choice.mythicSkillId().flatMap(kit::skill);
        if (skill.isEmpty() || !me.ready(skill.get(), tick) || DuelEngine.outOfReach(choice, world)
                || !world.conditions(me, skill.get()) || skill.get().damage > 0 && waits) {
            return;
        }
        world.cast(me, skill.get(), tick);
    }
}
