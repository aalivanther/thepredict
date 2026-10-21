package akyrion.thepredict.adapter.server.execution;

import io.lumine.mythic.api.adapters.AbstractEntity;
import io.lumine.mythic.api.skills.Skill;
import io.lumine.mythic.api.skills.SkillMetadata;
import io.lumine.mythic.api.skills.SkillTrigger;
import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.core.mobs.ActiveMob;
import akyrion.thepredict.domain.CastThread;
import io.lumine.mythic.core.skills.AbstractSkill;
import io.lumine.mythic.core.skills.SkillExecutor;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.OptionalLong;

/**
 * Runs a skill of a tactic through MythicMobs' own checks and execution with the {@code THEPREDICT} trigger (M10.3),
 * moved from the retired legacy adapter package. Server thread only.
 *
 * <p>Evidence (javap, MythicMobs 5.13.1): {@code MetaSkill.executeIfUsable(meta)} is {@code isUsable(meta)} followed
 * by the private {@code executeInternal(meta)}, which fires the cancellable {@code MythicSkillEvent}, takes the
 * cooldown lock and consumes a charge, and returns {@code false} when any of them refuses. {@code MetaSkill.isUsable}
 * rolls the chance, checks {@code isSkillOnCooldown} and evaluates, in this order, the target, trigger and caster
 * conditions with {@code SkillCondition.evaluateTargets}, {@code evaluateTrigger} and {@code evaluateCaster}, running their
 * {@code onFail}/{@code onPass} skills. {@code SkillCondition.handleOutcome} runs another skill for the actions
 * {@code CAST}, {@code CASTINSTEAD} and {@code ORELSECAST}, multiplies the power for {@code POWER}, and for
 * {@code REQUIRED} and {@code CANCEL} only sends a message when the caster is a player. So {@link #conditionsHold}
 * evaluates only the {@code REQUIRED} and {@code CANCEL} conditions of the three lists, on a metadata of its own and
 * without the {@code onFail}/{@code onPass} skills: for a mob caster that has no side effect. The other actions and the
 * chance are resolved once, by the single {@code executeIfUsable}. {@code SkillTrigger.register} uses
 * {@code putIfAbsent}, so registering the trigger again after a restart is harmless.
 */
public final class MythicSkillExecutor {
    /** Name of the MythicMobs trigger of the skills ThePredict casts; {@code ~onTHEPREDICT} is never written by hand. */
    public static final String TRIGGER_NAME = "THEPREDICT";

    private final SkillExecutor skills;
    private final SkillTrigger<?> trigger;
    private final boolean synchronous;

    /**
     * Creates the executor and registers the {@code THEPREDICT} trigger.
     *
     * @param skills {@code MythicBukkit.inst().getSkillManager()}
     */
    public MythicSkillExecutor(SkillExecutor skills) {
        this(skills, CastThread.DEFAULT);
    }

    /**
     * Creates the executor. {@link CastThread#SAME_TICK} builds synchronous metadata, so a {@code delay} waits in
     * server ticks (M19.4).
     *
     * @param skills {@code MythicBukkit.inst().getSkillManager()}
     * @param thread global {@code decision.castThread}
     */
    public MythicSkillExecutor(SkillExecutor skills, CastThread thread) {
        this.skills = Objects.requireNonNull(skills, "skills");
        this.synchronous = Objects.requireNonNull(thread, "thread") == CastThread.SAME_TICK;
        this.trigger = SkillTrigger.create(TRIGGER_NAME);
        this.trigger.register();
    }

    /**
     * Tells whether metadata is built synchronous.
     *
     * @return {@code true} for {@link CastThread#SAME_TICK}
     */
    public boolean synchronous() {
        return synchronous;
    }

    /**
     * Remaining cooldown in milliseconds. {@code 0} when a charge remains. {@link Long#MAX_VALUE} when MythicMobs has
     * no pending recharge. Empty when the skill is not an {@link AbstractSkill}.
     *
     * @param mob caster
     * @param skill skill of the choice
     * @return milliseconds, or empty
     */
    public OptionalLong remainingMillis(ActiveMob mob, Skill skill) {
        if (!(skill instanceof AbstractSkill abstractSkill)) {
            return OptionalLong.empty();
        }
        if (!abstractSkill.onCooldown(mob)) {
            return OptionalLong.of(0);
        }
        return OptionalLong.of(skills.getSkillCooldownRemainingMillis(mob, abstractSkill));
    }

    /**
     * Tells whether the skill is on its native cooldown for the mob, without side effects.
     *
     * @param mob caster
     * @param skill skill of the choice
     * @return {@code true} while on cooldown
     */
    public boolean onCooldown(ActiveMob mob, Skill skill) {
        return skill.onCooldown(mob);
    }

    /**
     * Evaluates the gating conditions of the skill without side effects, with no trigger entity or target.
     *
     * @param mob caster
     * @param skill skill of the choice
     * @return {@code true} when every {@code REQUIRED} or {@code CANCEL} caster and trigger condition passes
     */
    public boolean conditionsHold(ActiveMob mob, Skill skill) {
        return SkillConditions.hold(skill, metadata(mob));
    }

    /**
     * Evaluates the gating conditions of the skill without side effects, with the target as trigger entity and target.
     *
     * @param mob caster
     * @param skill skill of the choice
     * @param target current target of the mob, a player or any other living entity
     * @return {@code true} when every {@code REQUIRED} or {@code CANCEL} caster, trigger and target condition passes
     */
    public boolean conditionsHoldAt(ActiveMob mob, Skill skill, LivingEntity target) {
        return SkillConditions.hold(skill, metadataAt(mob, target));
    }

    /**
     * Executes the skill without a target if MythicMobs still considers it usable.
     *
     * @param mob caster
     * @param skill skill of the choice
     * @return whether MythicMobs executed it
     */
    public boolean execute(ActiveMob mob, Skill skill) {
        return skill.executeIfUsable(metadata(mob));
    }

    /**
     * Executes the skill with the target as trigger entity and target if MythicMobs still considers it usable.
     *
     * @param mob caster
     * @param skill skill of the choice
     * @param target current target of the mob, a player or any other living entity
     * @return whether MythicMobs executed it
     */
    public boolean executeAt(ActiveMob mob, Skill skill, LivingEntity target) {
        return skill.executeIfUsable(metadataAt(mob, target));
    }

    /**
     * Executes at a target and stores the tell flag before {@code executeIfUsable} (M23.7).
     *
     * @param mob caster
     * @param skill skill of the choice
     * @param target current target
     * @param terminated flag of this tell
     * @return whether MythicMobs executed it
     */
    public boolean executeAt(ActiveMob mob, Skill skill, LivingEntity target,
                             java.util.concurrent.atomic.AtomicBoolean terminated) {
        var metadata = metadataAt(mob, target);
        storeTerminated(metadata, terminated);
        return skill.executeIfUsable(metadata);
    }

    /**
     * Stores the tell flag on the skill metadata before execution (M23.7).
     *
     * @param metadata metadata about to run
     * @param terminated flag of this tell
     */
    public static void storeTerminated(SkillMetadata metadata, java.util.concurrent.atomic.AtomicBoolean terminated) {
        metadata.setTerminatedReference(java.util.Objects.requireNonNull(terminated, "terminated"));
    }

    private SkillMetadata metadata(ActiveMob mob) {
        return skills.getEventBus().buildSkillMetadata(trigger, mob, null, mob.getLocation(), synchronous);
    }

    /**
     * The MythicMobs entity of a target (M17.7): {@code BukkitAdapter.adapt(Player)} for a player, as in 0.0.1, because
     * {@code adapt(Entity)} always builds a plain {@code BukkitEntity} (javap, MythicMobs 5.13.1) and a player must
     * stay an {@code AbstractPlayer} for its conditions; {@code adapt(Entity)} for any other living entity.
     *
     * @param target a player or any other living entity
     * @return its MythicMobs entity
     */
    public static AbstractEntity adapt(LivingEntity target) {
        Objects.requireNonNull(target, "target");
        return target instanceof Player player ? BukkitAdapter.adapt(player) : BukkitAdapter.adapt(target);
    }

    private SkillMetadata metadataAt(ActiveMob mob, LivingEntity target) {
        var adapted = adapt(target);
        var metadata = skills.getEventBus().buildSkillMetadata(trigger, mob, adapted, mob.getLocation(), synchronous);
        metadata.setEntityTarget(adapted);
        return metadata;
    }
}
