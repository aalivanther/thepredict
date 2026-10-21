package akyrion.thepredict.engine.duel;

import akyrion.thepredict.domain.ChoiceKind;
import akyrion.thepredict.domain.ExecutionOutcome;
import akyrion.thepredict.domain.ImpactTiming;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.engine.CombatActions;

import java.util.OptionalInt;
import java.util.UUID;

/**
 * The combat actions of the simulator over the {@link DuelWorld}: each cast plays on the world with the reach of its
 * purpose and the own conditions of its skill. With {@link TickRules#LEGACY} a damaging cast waits while the rival is
 * invulnerable at all, and a vanilla hit is the zombie's own (1.43 blocks, every 20 ticks), as in M18.7. With
 * {@link TickRules#serverPorts} it waits only when {@code ImpactTiming.mustWait} says so, as
 * {@code HitChecks.deferInvulnerable} does (SIM-08 a), and a vanilla choice hits as {@code CombatActionsAdapter} does with
 * {@code Mob.attack}: within the melee reach of the profile, by the gap between the boxes, with no attack cooldown of
 * its own.
 */
final class DuelActions implements CombatActions {
    private final DuelEngine engine;

    DuelActions(DuelEngine engine) {
        this.engine = engine;
    }

    @Override
    public ExecutionOutcome cast(MobKey mob, SkillChoice choice, Movement movement) {
        var world = engine.world;
        var body = engine.body(mob.entityId());
        var rival = world.rival(body);
        var tick = engine.clock.tick;
        if (choice.kind() == ChoiceKind.VANILLA_MELEE) {
            return engine.rules.serverPorts() && engine.rules.mobAttack() ? attack(body, rival, choice, tick)
                    : punch(body, rival, tick);
        }
        var skill = engine.skill(choice).orElseThrow();
        if (!body.ready(skill, tick) || engine.outOfReach(choice) || !world.conditions(body, skill)) {
            return ExecutionOutcome.NOT_ELIGIBLE;
        }
        if (skill.damage > 0 && waits(rival, choice, tick)) {
            return ExecutionOutcome.DEFERRED_INVULNERABLE;
        }
        if (world.windingUp(body, tick)) {
            engine.preparationCasts++;
        }
        world.cast(body, skill, tick);
        engine.casts.accept(body, skill);
        return cast();
    }

    private ExecutionOutcome punch(DuelWorld.Body body, DuelWorld.Body rival, long tick) {
        if (engine.world.distance() > DuelWorld.VANILLA_REACH) {
            return ExecutionOutcome.NOT_ELIGIBLE;
        }
        if (tick < rival.invulnerableUntil) {
            return ExecutionOutcome.DEFERRED_INVULNERABLE;
        }
        if (!engine.world.punch(body, tick)) {
            return ExecutionOutcome.NOT_ELIGIBLE;
        }
        if (engine.rules.serverPorts()) {
            engine.casts.accept(body, DuelSkill.VANILLA_HIT);
        }
        return cast();
    }

    private ExecutionOutcome attack(DuelWorld.Body body, DuelWorld.Body rival, SkillChoice choice, long tick) {
        var profile = engine.profile(body.id);
        if (engine.world.gap() > profile.movement().meleeReachBlocks()) {
            return ExecutionOutcome.NOT_ELIGIBLE;
        }
        if (waits(rival, choice, tick)) {
            return ExecutionOutcome.DEFERRED_INVULNERABLE;
        }
        engine.world.attack(body, tick);
        engine.casts.accept(body, DuelSkill.VANILLA_HIT);
        return cast();
    }

    private boolean waits(DuelWorld.Body rival, SkillChoice choice, long tick) {
        if (!engine.rules.serverPorts()) {
            return tick < rival.invulnerableUntil;
        }
        return ImpactTiming.mustWait(DuelSignals.hittable(rival, tick), choice.busyTicks());
    }

    private ExecutionOutcome cast() {
        engine.actionCasts++;
        return ExecutionOutcome.CAST;
    }

    @Override
    public boolean ready(MobKey mob, SkillChoice choice) {
        var skill = engine.skill(choice);
        return skill.isEmpty() || engine.body(mob.entityId()).ready(skill.get(), engine.clock.tick);
    }

    /**
     * Reach without a line of sight, as {@code CombatActionsAdapter.cheaplyAdmitted} checks it on a server with
     * {@code HitChecks.inReach} (SIM-E2, fase de efectividad): a choice without a target passes; a vanilla hit needs
     * the gap between the boxes within the melee reach of the profile, as {@link #attack} does; any other choice the
     * reach of its purpose, as {@link #cast} does. With {@link TickRules#LEGACY} every choice passes, as in M18.7.
     */
    @Override
    public boolean cheaplyAdmitted(MobKey mob, SkillChoice choice, Movement movement) {
        if (!engine.rules.serverPorts() || !choice.requiresTarget() && choice.kind() != ChoiceKind.VANILLA_MELEE) {
            return true;
        }
        if (choice.kind() == ChoiceKind.VANILLA_MELEE) {
            return engine.world.gap() <= engine.profile(mob.entityId()).movement().meleeReachBlocks();
        }
        return !engine.outOfReach(choice);
    }

    /**
     * The ticks left of the native cooldown, as {@code CombatActionsAdapter.ticksUntilReady} reads them on a server
     * from {@code SkillExecutor.getSkillCooldownRemainingMillis} (SIM-E1, fase de efectividad): empty for a vanilla
     * hit, whose rhythm the engine keeps, and for a choice without a skill of the kit.
     */
    @Override
    public OptionalInt ticksUntilReady(MobKey mob, SkillChoice choice) {
        if (choice.kind() != ChoiceKind.MYTHIC_SKILL) {
            return OptionalInt.empty();
        }
        var skill = engine.skill(choice);
        if (skill.isEmpty()) {
            return OptionalInt.empty();
        }
        var at = engine.body(mob.entityId()).readyAt(skill.get());
        var tick = engine.clock.tick;
        return OptionalInt.of(at <= tick ? 0 : (int) Math.min(Integer.MAX_VALUE, at - tick));
    }

    @Override
    public void signalTactic(MobKey mob, TacticId tactic) {
    }

    @Override
    public void addThreat(MobKey mob, UUID participantId, double amount) {
    }
}
