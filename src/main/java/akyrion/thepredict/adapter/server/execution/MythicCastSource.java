package akyrion.thepredict.adapter.server.execution;

import akyrion.thepredict.adapter.server.context.MemberSource;
import io.lumine.mythic.api.skills.Skill;
import io.lumine.mythic.core.mobs.ActiveMob;
import io.lumine.mythic.core.mobs.MobExecutor;
import org.bukkit.entity.LivingEntity;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * {@link CastSource} over the MythicMobs mob manager. Server thread only.
 *
 * <p>Evidence (javap, MythicMobs 5.13.1): {@code MobExecutor.getActiveMob(UUID)} reads the active registry;
 * {@code ActiveMob.signalMob(AbstractEntity, String)} builds skill metadata with that trigger and runs the
 * {@code ~onSignal} skills whose name matches exactly (DT-11); {@code ActiveMob.ThreatTable.threatGain} and
 * {@code threatLoss} take an {@code AbstractEntity} and a {@code double}; {@link MythicSkillExecutor#adapt} gives that
 * entity for a player or any other living entity (M17.7). The target lookup is the one of the capture
 * ({@link MemberSource#target}), so casts aim where Jev was told the target is.
 *
 * <p>{@link #handlesSignal} answers from the {@code ~onSignal} handlers the binder read into the mob's
 * {@link akyrion.thepredict.adapter.server.binding.BoundProfile} (M23.3, M23.5), through {@link MemberSource#member}:
 * {@code MobType} keeps its signal map private, so the lines are the only readable source, and they are reread at
 * every rebind ({@code /mm reload}).
 */
public final class MythicCastSource implements CastSource {
    private final MobExecutor mobs;
    private final MythicSkillExecutor skills;
    private final MemberSource targets;

    /**
     * Creates the source.
     *
     * @param mobs {@code MythicBukkit.inst().getMobManager()}
     * @param skills executor with the {@code THEPREDICT} trigger
     * @param targets target lookup of the capture
     */
    public MythicCastSource(MobExecutor mobs, MythicSkillExecutor skills, MemberSource targets) {
        this.mobs = Objects.requireNonNull(mobs, "mobs");
        this.skills = Objects.requireNonNull(skills, "skills");
        this.targets = Objects.requireNonNull(targets, "targets");
    }

    @Override
    public Optional<LivingEntity> caster(UUID mobId) {
        return active(mobId).map(mob -> mob.getEntity().getBukkitEntity()).filter(LivingEntity.class::isInstance)
                .map(LivingEntity.class::cast);
    }

    @Override
    public Optional<LivingEntity> target(UUID mobId) {
        return targets.target(mobId);
    }

    @Override
    public boolean onCooldown(UUID mobId, Skill skill) {
        return active(mobId).map(mob -> skills.onCooldown(mob, skill)).orElse(true);
    }

    @Override
    public java.util.OptionalLong remainingMillis(UUID mobId, Skill skill) {
        return active(mobId).map(mob -> skills.remainingMillis(mob, skill)).orElse(java.util.OptionalLong.empty());
    }

    @Override
    public Optional<UUID> aiTarget(UUID mobId) {
        return caster(mobId).filter(org.bukkit.entity.Mob.class::isInstance).map(org.bukkit.entity.Mob.class::cast)
                .map(org.bukkit.entity.Mob::getTarget).map(org.bukkit.entity.Entity::getUniqueId);
    }

    @Override
    public boolean conditionsHold(UUID mobId, Skill skill) {
        return active(mobId).map(mob -> skills.conditionsHold(mob, skill)).orElse(false);
    }

    @Override
    public boolean conditionsHoldAt(UUID mobId, Skill skill, LivingEntity target) {
        return active(mobId).map(mob -> skills.conditionsHoldAt(mob, skill, target)).orElse(false);
    }

    @Override
    public boolean execute(UUID mobId, Skill skill) {
        return active(mobId).map(mob -> skills.execute(mob, skill)).orElse(false);
    }

    @Override
    public boolean executeAt(UUID mobId, Skill skill, LivingEntity target) {
        return active(mobId).map(mob -> skills.executeAt(mob, skill, target)).orElse(false);
    }

    @Override
    public boolean executeAt(UUID mobId, Skill skill, LivingEntity target,
                             java.util.concurrent.atomic.AtomicBoolean terminated) {
        return active(mobId).map(mob -> skills.executeAt(mob, skill, target, terminated)).orElse(false);
    }

    @Override
    public void writeVariable(UUID mobId, String key, String value) {
        active(mobId).ifPresent(mob -> mob.getVariables().putString(key, value));
    }

    @Override
    public void setStance(UUID mobId, String stance) {
        active(mobId).ifPresent(mob -> mob.setStance(stance));
    }

    @Override
    public boolean handlesSignal(UUID mobId, String signal) {
        Objects.requireNonNull(signal, "signal");
        var member = targets.member(mobId);
        return member.isPresent() && member.get().profile().cueHandlers().handles(signal);
    }

    @Override
    public boolean signal(UUID mobId, String signal) {
        var mob = active(mobId);
        mob.ifPresent(found -> found.signalMob(found.getEntity(), signal));
        return mob.isPresent();
    }

    @Override
    public boolean changeThreat(UUID mobId, LivingEntity participant, double amount) {
        var mob = active(mobId).filter(ActiveMob::hasThreatTable);
        if (mob.isEmpty()) {
            return false;
        }
        var entity = MythicSkillExecutor.adapt(participant);
        if (amount > 0) {
            mob.get().getThreatTable().threatGain(entity, amount);
        } else {
            mob.get().getThreatTable().threatLoss(entity, -amount);
        }
        return true;
    }

    private Optional<ActiveMob> active(UUID mobId) {
        return mobs.getActiveMob(mobId).filter(mob -> !mob.isDead() && mob.getEntity() != null
                && mob.getEntity().isValid() && !mob.getEntity().isDead());
    }
}
