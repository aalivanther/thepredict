package akyrion.thepredict.adapter.server.context;

import akyrion.thepredict.adapter.server.binding.BoundProfile;
import akyrion.thepredict.domain.GroupRelations;
import akyrion.thepredict.domain.ThreatTableState;
import io.lumine.mythic.api.skills.Skill;
import io.lumine.mythic.core.mobs.ActiveMob;
import io.lumine.mythic.core.mobs.MobExecutor;
import io.lumine.mythic.core.skills.AbstractSkill;
import io.lumine.mythic.core.skills.SkillExecutor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * {@link MemberSource} over the MythicMobs mob manager, keeping the target lookup and the maximum-health check of
 * the legacy snapshot reader that M09 retired (M07.1). Server thread only.
 *
 * <p>Evidence (javap, MythicMobs 5.13.1): {@code ActiveMob.ThreatTable.getThreatTable()} returns the live
 * {@code Map<UUID, Double>} it keeps, {@code getThreat} reads it with {@code getOrDefault(uuid, 0)}, and
 * {@code inCombat()} is {@code !threatTable.isEmpty()}. {@code ActiveMob} is the {@code SkillCaster} that
 * {@code Skill.onCooldown} takes. The target of M17.5 may be any living entity the participant policy of the mob's
 * bound profile admits ({@link #choose}).
 */
public final class MythicMemberSource implements MemberSource {
    private final MobExecutor mobs;
    private final Function<String, Optional<BoundProfile>> profiles;
    private final ParticipantLookup lookup;
    private final Optional<SkillExecutor> skills;

    /**
     * Creates the source.
     *
     * @param mobs {@code MythicBukkit.inst().getMobManager()}
     * @param profiles bound profile of a MythicMobs mob ID in the current catalog
     * @param lookup native identities read by the participant policy (M17.2)
     */
    public MythicMemberSource(MobExecutor mobs, Function<String, Optional<BoundProfile>> profiles,
                              ParticipantLookup lookup) {
        this.mobs = Objects.requireNonNull(mobs, "mobs");
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.lookup = Objects.requireNonNull(lookup, "lookup");
        this.skills = Optional.empty();
    }

    /**
     * Creates the source that also reads {@code SkillExecutor.getSkillCooldownRemainingMillis} for the readiness
     * horizon (M19.6). Evidence: the method exists on MythicMobs 5.13.1 and returns milliseconds.
     *
     * @param mobs {@code MythicBukkit.inst().getMobManager()}
     * @param profiles bound profile of a MythicMobs mob ID in the current catalog
     * @param lookup native identities read by the participant policy
     * @param skills skill executor that reads the remaining cooldown
     */
    public MythicMemberSource(MobExecutor mobs, Function<String, Optional<BoundProfile>> profiles,
                              ParticipantLookup lookup, SkillExecutor skills) {
        this.mobs = Objects.requireNonNull(mobs, "mobs");
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.lookup = Objects.requireNonNull(lookup, "lookup");
        this.skills = Optional.of(Objects.requireNonNull(skills, "skills"));
    }

    @Override
    public Optional<String> authorStance(UUID mobId) {
        var stance = mobs.getActiveMob(mobId).map(ActiveMob::getStance).orElse("");
        if (stance == null || stance.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(stance);
    }

    @Override
    public Optional<Member> member(UUID mobId) {
        var active = mobs.getActiveMob(mobId).filter(MythicMemberSource::alive);
        if (active.isEmpty() || !(active.get().getEntity().getBukkitEntity() instanceof LivingEntity entity)) {
            return Optional.empty();
        }
        return profiles.apply(active.get().getType().getInternalName()).map(profile -> new Member(entity, profile));
    }

    @Override
    public Optional<String> textVariable(UUID mobId, String key) {
        var active = mobs.getActiveMob(mobId);
        if (active.isEmpty() || !active.get().getVariables().has(key)) {
            return Optional.empty();
        }
        var text = active.get().getVariables().getString(key);
        return text == null || text.isEmpty() ? Optional.empty() : Optional.of(text);
    }

    @Override
    public OptionalDouble numberVariable(UUID mobId, String key) {
        var active = mobs.getActiveMob(mobId);
        if (active.isEmpty() || !active.get().getVariables().has(key)) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(active.get().getVariables().getDouble(key));
    }

    @Override
    public Optional<LivingEntity> target(UUID mobId) {
        var active = mobs.getActiveMob(mobId).filter(MythicMemberSource::alive);
        if (active.isEmpty() || !(active.get().getEntity().getBukkitEntity() instanceof LivingEntity actor)) {
            return Optional.empty();
        }
        var mob = active.get();
        var policy = profiles.apply(mob.getType().getInternalName()).map(BoundProfile::participants)
                .orElse(ParticipantPolicy.DEFAULT);
        var holder = mob.hasThreatTable() ? mob.getThreatTable().getTopThreatHolder() : null;
        return choose(holder == null ? null : holder.getBukkitEntity(),
                () -> actor instanceof Mob pathing ? pathing.getTarget() : null, actor,
                candidate -> policy.admits(candidate, actor, Set.of(), lookup));
    }

    /**
     * The target rule of M17.5, pure over its inputs: the top threat holder first, then the target of the pathing mob,
     * which is read only when the holder does not decide. A player decides as in 0.0.1 when it is valid, alive and in
     * the actor's world, whatever its game mode (the capture checks it, so the defaults never change); any other
     * living entity decides only when {@code admits} accepts it and it is in the actor's world.
     *
     * @param holder Bukkit entity of the top threat holder, or {@code null} without a threat table or holder
     * @param pathTarget reads {@code Mob#getTarget()}, {@code null} without one
     * @param actor living entity of the mob
     * @param admits the participant policy of the mob's profile
     * @return the target, if any
     */
    static Optional<LivingEntity> choose(Entity holder, Supplier<Entity> pathTarget, LivingEntity actor,
                                         Predicate<LivingEntity> admits) {
        var first = decides(holder, actor, admits);
        return first.isPresent() ? first : decides(pathTarget.get(), actor, admits);
    }

    private static Optional<LivingEntity> decides(Entity candidate, LivingEntity actor,
                                                  Predicate<LivingEntity> admits) {
        if (candidate instanceof Player player) {
            return player.isValid() && !player.isDead() && player.getWorld().equals(actor.getWorld())
                    ? Optional.of(player) : Optional.empty();
        }
        if (candidate instanceof LivingEntity living && admits.test(living)
                && living.getWorld().equals(actor.getWorld())) {
            return Optional.of(living);
        }
        return Optional.empty();
    }

    @Override
    public double threat(UUID mobId, UUID participantId) {
        var active = mobs.getActiveMob(mobId);
        if (active.isEmpty() || !active.get().hasThreatTable()) {
            return 0.0;
        }
        var threat = active.get().getThreatTable().getThreatTable().get(participantId);
        return threat == null ? 0.0 : threat;
    }

    @Override
    public boolean onCooldown(UUID mobId, Skill skill) {
        return mobs.getActiveMob(mobId).map(skill::onCooldown).orElse(true);
    }

    /**
     * {@inheritDoc}
     *
     * <p>{@code 0} when a charge remains. {@link Long#MAX_VALUE} when MythicMobs reports no pending recharge. Empty
     * when the skill is not an {@link AbstractSkill} or this source was built without a skill executor.
     */
    @Override
    public OptionalLong remainingMillis(UUID mobId, Skill skill) {
        if (skills.isEmpty() || !(skill instanceof AbstractSkill abstractSkill)) {
            return OptionalLong.empty();
        }
        var active = mobs.getActiveMob(mobId);
        if (active.isEmpty()) {
            return OptionalLong.empty();
        }
        if (!abstractSkill.onCooldown(active.get())) {
            return OptionalLong.of(0);
        }
        return OptionalLong.of(skills.get().getSkillCooldownRemainingMillis(active.get(), abstractSkill));
    }

    @Override
    public boolean threatInCombat(UUID mobId) {
        return mobs.getActiveMob(mobId).filter(ActiveMob::hasThreatTable)
                .map(mob -> mob.getThreatTable().inCombat()).orElse(false);
    }

    @Override
    public boolean isActive(UUID mobId) {
        return mobs.getActiveMob(mobId).filter(MythicMemberSource::alive).isPresent();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Reads {@code ThreatTable.size()}, {@code getTopTargetThreat()} and {@code getTotalThreat()} (javap,
     * MythicMobs 5.13.1); a value that is negative or not finite reads as {@code 0}.
     */
    @Override
    public Optional<ThreatTableState> threatTable(UUID mobId) {
        var active = mobs.getActiveMob(mobId).filter(MythicMemberSource::alive);
        if (active.isEmpty()) {
            return Optional.empty();
        }
        if (!active.get().hasThreatTable()) {
            return Optional.of(ThreatTableState.DISABLED);
        }
        var table = active.get().getThreatTable();
        return Optional.of(new ThreatTableState(true, table.size(), points(table.getTopTargetThreat()),
                points(table.getTotalThreat())));
    }

    private static double points(double threat) {
        return Double.isFinite(threat) ? Math.max(0.0, threat) : 0.0;
    }

    @Override
    public GroupRelations relations(UUID mobId, Set<UUID> inside) {
        var active = mobs.getActiveMob(mobId);
        if (active.isEmpty()) {
            return GroupRelations.NONE;
        }
        return GroupRelationReader.read(active.get(), inside);
    }

    @Override
    public int threatTableGroup(UUID mobId) {
        var active = mobs.getActiveMob(mobId);
        if (active.isEmpty() || !active.get().hasThreatTable()) {
            return 0;
        }
        return System.identityHashCode(active.get().getThreatTable());
    }

    @Override
    public boolean inheritsThreatTable(UUID mobId) {
        var active = mobs.getActiveMob(mobId);
        if (active.isEmpty() || !active.get().hasThreatTable()) {
            return false;
        }
        var parent = active.get().getParentUUID() == null ? null : active.get().getParentUUID().orElse(null);
        if (parent == null) {
            return false;
        }
        var owner = mobs.getActiveMob(parent);
        if (owner.isEmpty() || !owner.get().hasThreatTable()) {
            return false;
        }
        return active.get().getThreatTable() == owner.get().getThreatTable();
    }

    private static boolean alive(ActiveMob mob) {
        var entity = mob.getEntity();
        return !mob.isDead() && entity != null && entity.isValid() && !entity.isDead();
    }
}
