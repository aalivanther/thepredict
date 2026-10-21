package akyrion.thepredict.adapter.server.execution;

import akyrion.thepredict.adapter.server.context.Blocking;
import akyrion.thepredict.adapter.server.context.ShieldGeometry;
import akyrion.thepredict.adapter.server.vocabulary.TacticSignals;
import akyrion.thepredict.domain.ChoiceKind;
import akyrion.thepredict.domain.CueRequest;
import akyrion.thepredict.domain.ExecutionOutcome;
import akyrion.thepredict.domain.ReleaseOutcome;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.MovementSettings;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.SkillPurpose;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.engine.CastInterrupt;
import akyrion.thepredict.engine.CombatActions;
import akyrion.thepredict.engine.ServerClock;
import io.lumine.mythic.api.skills.Skill;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * {@link CombatActions} over MythicMobs and Paper (M10.3, M10.8, M10.9, M11.4). Server thread only (rule T1): every
 * method throws {@link IllegalStateException} on another thread, which makes the rule checkable (C7). The only state
 * is the {@link SightCache} of the constructor with a clock: one line of sight per mob and tick, shared by
 * {@link #seesTarget} and the reach check of a cast (M19.5, C12).
 *
 * <p>A cast checks, in order: the caster and, when the choice needs one, the target ({@code NOT_ELIGIBLE}); a skill no
 * longer loaded ({@code REJECTED_BY_MYTHICMOBS}); the native cooldown of a skill ({@code NOT_ELIGIBLE}); the reach
 * ({@code NOT_ELIGIBLE}): a vanilla melee choice uses the gap against {@code meleeReachBlocks}; a typed purpose uses
 * its reach measure and, last, the line of sight it declares (M15.6, M19.3); an untyped choice is not measured
 * (M19.2); for a damaging choice aimed at
 * the target ({@code HitChecks.damaging}) the invulnerability window ({@code DEFERRED_INVULNERABLE}, omitted when the
 * native facts ignore it, M19.3) and the shield: a
 * {@code FLANK} intent against a raised shield waits ({@code NOT_ELIGIBLE}) while the mob stands within the blocking
 * angle read from {@code BLOCKS_ATTACKS}, and any other intent against a blocking target ({@link Blocking}: a human
 * as in 0.0.1, or any other entity whose shield already blocks, M17.7) is {@code SKIPPED_SHIELD} unless
 * the purpose of the choice ignores shields ({@code IgnoresShield}; a PredictValues label does not, E19-1 DP5);
 * then the gating conditions of the skill, evaluated without side effects ({@code NOT_ELIGIBLE}). Every
 * {@code NOT_ELIGIBLE} is retried by {@code engine} in a later reactive cycle of the same intent. A skill then runs
 * once through {@code executeIfUsable}: {@code CAST}, or {@code REJECTED_BY_MYTHICMOBS} when the chance fails, the
 * {@code MythicSkillEvent} is cancelled or no charge is left. A vanilla melee choice calls {@code LivingEntity#attack}
 * and {@code swingMainHand} ({@code CAST}). The target may be any living entity the participant policy admits
 * (M17.7): invulnerability, reach and the vanilla hit already work for any {@code LivingEntity}.
 */
public final class CombatActionsAdapter implements CombatActions {
    /** Melee reach when the mob has no session profile, the default of {@code MovementSettings}. */
    public static final double DEFAULT_MELEE_REACH_BLOCKS = MovementSettings.DEFAULTS.meleeReachBlocks();

    private final CastSource source;
    private final Function<String, Optional<Skill>> skills;
    private final Function<LivingEntity, OptionalDouble> shieldAngle;
    private final Predicate<LivingEntity> blocking;
    private final Function<UUID, Optional<MovementSettings>> movement;
    private final Function<UUID, Optional<LivingEntity>> entities;
    private final BooleanSupplier serverThread;
    private final LineOfSight sight;

    /**
     * Creates the adapter that reads a line of sight on every call ({@link LineOfSight#DIRECT}). Kept for the doubles
     * without a clock (rule W5); the root uses the constructor with a {@link ServerClock}.
     *
     * @param source MythicMobs operations on a mob
     * @param skills loaded skill by exact ID ({@code SkillExecutor::getSkill}), read at each cast so a reload is seen
     * @param shieldAngle blocking angle of the shield a target raises ({@code ShieldGeometry::raisedShieldAngleDegrees})
     * @param blocking whether a target blocks now ({@code Blocking::blocking}: {@code isBlocking()} of a human, the
     *                 active item and its block delay for any other entity)
     * @param movement movement settings of the profile of a mob's session, for the melee reach
     * @param entities loaded living entity by UUID ({@code Bukkit::getEntity} filtered to {@code LivingEntity})
     * @param serverThread {@code Bukkit::isPrimaryThread}
     */
    public CombatActionsAdapter(CastSource source, Function<String, Optional<Skill>> skills,
                                Function<LivingEntity, OptionalDouble> shieldAngle, Predicate<LivingEntity> blocking,
                                Function<UUID, Optional<MovementSettings>> movement,
                                Function<UUID, Optional<LivingEntity>> entities, BooleanSupplier serverThread) {
        this(source, skills, shieldAngle, blocking, movement, entities, serverThread, LineOfSight.DIRECT);
    }

    /**
     * Creates the adapter that reads one line of sight per mob and server tick (M19.5, C12).
     *
     * @param source MythicMobs operations on a mob
     * @param skills loaded skill by exact ID ({@code SkillExecutor::getSkill}), read at each cast so a reload is seen
     * @param shieldAngle blocking angle of the shield a target raises ({@code ShieldGeometry::raisedShieldAngleDegrees})
     * @param blocking whether a target blocks now ({@code Blocking::blocking})
     * @param movement movement settings of the profile of a mob's session, for the melee reach
     * @param entities loaded living entity by UUID ({@code Bukkit::getEntity} filtered to {@code LivingEntity})
     * @param serverThread {@code Bukkit::isPrimaryThread}
     * @param clock server clock whose {@code currentTick} bounds a remembered line of sight
     */
    public CombatActionsAdapter(CastSource source, Function<String, Optional<Skill>> skills,
                                Function<LivingEntity, OptionalDouble> shieldAngle, Predicate<LivingEntity> blocking,
                                Function<UUID, Optional<MovementSettings>> movement,
                                Function<UUID, Optional<LivingEntity>> entities, BooleanSupplier serverThread,
                                ServerClock clock) {
        this(source, skills, shieldAngle, blocking, movement, entities, serverThread, new SightCache(clock));
    }

    private CombatActionsAdapter(CastSource source, Function<String, Optional<Skill>> skills,
                                 Function<LivingEntity, OptionalDouble> shieldAngle, Predicate<LivingEntity> blocking,
                                 Function<UUID, Optional<MovementSettings>> movement,
                                 Function<UUID, Optional<LivingEntity>> entities, BooleanSupplier serverThread,
                                 LineOfSight sight) {
        this.source = Objects.requireNonNull(source, "source");
        this.skills = Objects.requireNonNull(skills, "skills");
        this.shieldAngle = Objects.requireNonNull(shieldAngle, "shieldAngle");
        this.blocking = Objects.requireNonNull(blocking, "blocking");
        this.movement = Objects.requireNonNull(movement, "movement");
        this.entities = Objects.requireNonNull(entities, "entities");
        this.serverThread = Objects.requireNonNull(serverThread, "serverThread");
        this.sight = sight;
    }

    @Override
    public ExecutionOutcome cast(MobKey mob, SkillChoice choice, Movement intentMovement) {
        requireServerThread();
        return fire(mob, choice, intentMovement, Optional.empty());
    }

    /**
     * Casts and hands MythicMobs the tell interrupt (M23.4): a skill released with a lead of 0 is cut when the poise
     * breaks during its own wind-up, like one released after a tell.
     */
    @Override
    public ExecutionOutcome cast(MobKey mob, SkillChoice choice, Movement intentMovement, CastInterrupt interrupt) {
        requireServerThread();
        Objects.requireNonNull(interrupt, "interrupt");
        return fire(mob, choice, intentMovement, Optional.of(interrupt));
    }

    /**
     * Casts at the expected target with the tell interrupt: the checks of
     * {@link #cast(MobKey, SkillChoice, Movement, UUID)} and the flag of
     * {@link #cast(MobKey, SkillChoice, Movement, CastInterrupt)}. The engine calls it for a tell with a lead of 0
     * (M23, SA-02).
     *
     * @param mob session of the caster
     * @param choice choice to cast
     * @param intentMovement movement of the intent
     * @param expectedTarget target captured when the intent was published
     * @param interrupt flag MythicMobs reads after the next delay
     * @return the same outcomes as {@link #cast(MobKey, SkillChoice, Movement, UUID)}
     */
    @Override
    public ExecutionOutcome cast(MobKey mob, SkillChoice choice, Movement intentMovement, UUID expectedTarget,
                                 CastInterrupt interrupt) {
        requireServerThread();
        Objects.requireNonNull(interrupt, "interrupt");
        if (!targetsMatch(mob, choice, expectedTarget)) {
            return ExecutionOutcome.NOT_ELIGIBLE;
        }
        return fire(mob, choice, intentMovement, Optional.of(interrupt));
    }

    /** Longest ready wait kept, one hour, so a remaining cooldown never overflows an int (M19.4). */
    static final int MAX_READY_TICKS = 72_000;

    @Override
    public ExecutionOutcome cast(MobKey mob, SkillChoice choice, Movement intentMovement, UUID expectedTarget) {
        requireServerThread();
        if (!targetsMatch(mob, choice, expectedTarget)) {
            return ExecutionOutcome.NOT_ELIGIBLE;
        }
        return fire(mob, choice, intentMovement, Optional.empty());
    }

    /**
     * The live target is the expected one and, for a skill with a native {@code targetwithin}, so is the AI target
     * that condition measures (M19.4).
     */
    private boolean targetsMatch(MobKey mob, SkillChoice choice, UUID expectedTarget) {
        Objects.requireNonNull(expectedTarget, "expectedTarget");
        return liveTargetMatches(mob, expectedTarget)
                && (choice.nativeFacts().targetWithinBlocks().isEmpty() || aiTargetMatches(mob, expectedTarget));
    }

    /** The cast itself, after the thread and target checks; {@code interrupt} is stored on the skill when present. */
    private ExecutionOutcome fire(MobKey mob, SkillChoice choice, Movement intentMovement,
                                  Optional<CastInterrupt> interrupt) {
        var mobId = mob.entityId();
        var caster = source.caster(mobId);
        var target = caster.isPresent() ? source.target(mobId) : Optional.<LivingEntity>empty();
        var melee = choice.kind() == ChoiceKind.VANILLA_MELEE;
        if (caster.isEmpty() || (choice.requiresTarget() || melee) && target.isEmpty()) {
            return ExecutionOutcome.NOT_ELIGIBLE;
        }
        var skill = melee ? Optional.<Skill>empty() : skills.apply(choice.mythicSkillId().orElseThrow());
        if (!melee && skill.isEmpty()) {
            return ExecutionOutcome.REJECTED_BY_MYTHICMOBS;
        }
        var waiting = waiting(new Attempt(mobId, choice, intentMovement, caster.get(), target, skill));
        if (waiting.isPresent()) {
            return waiting.get();
        }
        if (melee) {
            caster.get().attack(target.get());
            caster.get().swingMainHand();
            return ExecutionOutcome.CAST;
        }
        return execute(mobId, skill.get(), target, interrupt) ? ExecutionOutcome.CAST
                : ExecutionOutcome.REJECTED_BY_MYTHICMOBS;
    }

    private boolean execute(UUID mobId, Skill skill, Optional<LivingEntity> target, Optional<CastInterrupt> interrupt) {
        if (target.isEmpty()) {
            return source.execute(mobId, skill);
        }
        if (interrupt.isPresent()) {
            return source.executeAt(mobId, skill, target.get(), interrupt.get().flag());
        }
        return source.executeAt(mobId, skill, target.get());
    }

    @Override
    public boolean liveTargetMatches(MobKey mob, UUID expectedTarget) {
        requireServerThread();
        Objects.requireNonNull(expectedTarget, "expectedTarget");
        var live = source.target(mob.entityId());
        return live.isPresent() && expectedTarget.equals(live.get().getUniqueId());
    }

    @Override
    public boolean aiTargetMatches(MobKey mob, UUID expectedTarget) {
        requireServerThread();
        Objects.requireNonNull(expectedTarget, "expectedTarget");
        var ai = source.aiTarget(mob.entityId());
        return ai.isPresent() && expectedTarget.equals(ai.get());
    }

    @Override
    public akyrion.thepredict.domain.ReadyIn readyIn(MobKey mob, SkillChoice choice) {
        requireServerThread();
        if (choice.kind() == akyrion.thepredict.domain.ChoiceKind.VANILLA_MELEE) {
            return akyrion.thepredict.domain.ReadyIn.in(0);
        }
        if (choice.kind() != akyrion.thepredict.domain.ChoiceKind.MYTHIC_SKILL) {
            return akyrion.thepredict.domain.ReadyIn.UNKNOWN;
        }
        var skill = skills.apply(choice.mythicSkillId().orElseThrow());
        if (skill.isEmpty()) {
            return akyrion.thepredict.domain.ReadyIn.UNKNOWN;
        }
        var remaining = source.remainingMillis(mob.entityId(), skill.get());
        if (remaining.isPresent() && remaining.getAsLong() == Long.MAX_VALUE) {
            return akyrion.thepredict.domain.ReadyIn.EXHAUSTED;
        }
        var ticks = ticksUntilReady(mob, choice);
        return ticks.isEmpty() ? akyrion.thepredict.domain.ReadyIn.UNKNOWN
                : akyrion.thepredict.domain.ReadyIn.in(ticks.getAsInt());
    }

    @Override
    public OptionalInt ticksUntilReady(MobKey mob, SkillChoice choice) {
        requireServerThread();
        if (choice.kind() != ChoiceKind.MYTHIC_SKILL) {
            return OptionalInt.empty();
        }
        var skill = skills.apply(choice.mythicSkillId().orElseThrow());
        if (skill.isEmpty()) {
            return OptionalInt.empty();
        }
        var loaded = skill.get();
        var remaining = source.remainingMillis(mob.entityId(), loaded);
        if (!source.onCooldown(mob.entityId(), loaded)) {
            return remaining.isEmpty() ? OptionalInt.empty() : OptionalInt.of(0);
        }
        if (remaining.isEmpty() || remaining.getAsLong() == Long.MAX_VALUE || remaining.getAsLong() < 0) {
            return OptionalInt.empty();
        }
        var millis = remaining.getAsLong();
        if (millis == 0) {
            return OptionalInt.of(0);
        }
        var ticks = millis / 50 + (millis % 50 == 0 ? 0 : 1);
        return OptionalInt.of((int) Math.min(MAX_READY_TICKS, ticks));
    }

    @Override
    public boolean ready(MobKey mob, SkillChoice choice) {
        requireServerThread();
        if (choice.kind() == ChoiceKind.VANILLA_MELEE) {
            return true;
        }
        var skill = skills.apply(choice.mythicSkillId().orElseThrow());
        return skill.isPresent() && !source.onCooldown(mob.entityId(), skill.get());
    }

    /**
     * Reach, and a present target when the choice needs one, without a line of sight (M19.5). A missed line of sight
     * stays for {@link #seesTarget} so another skill of the same tick can still be tried when this one is merely out
     * of reach.
     */
    @Override
    public boolean cheaplyAdmitted(MobKey mob, SkillChoice choice, Movement movement) {
        requireServerThread();
        Objects.requireNonNull(choice, "choice");
        Objects.requireNonNull(movement, "movement");
        var caster = source.caster(mob.entityId());
        if (caster.isEmpty()) {
            return false;
        }
        if (!choice.requiresTarget() && choice.kind() != ChoiceKind.VANILLA_MELEE) {
            return true;
        }
        var target = source.target(mob.entityId());
        if (target.isEmpty() || !caster.get().getWorld().equals(target.get().getWorld())) {
            return false;
        }
        var reach = this.movement.apply(mob.entityId()).map(MovementSettings::meleeReachBlocks)
                .orElse(DEFAULT_MELEE_REACH_BLOCKS);
        return HitChecks.inReach(choice, caster.get(), target.get(), reach);
    }

    /** The line of sight of the caster toward its current target, at most one ray per mob and tick (M19.5). */
    @Override
    public boolean seesTarget(MobKey mob) {
        requireServerThread();
        var caster = source.caster(mob.entityId());
        var target = source.target(mob.entityId());
        return caster.isPresent() && target.isPresent()
                && caster.get().getWorld().equals(target.get().getWorld())
                && sight.sees(mob.entityId(), caster.get(), target.get());
    }

    @Override
    public void signalTactic(MobKey mob, TacticId tactic) {
        requireServerThread();
        source.signal(mob.entityId(), TacticSignals.nameOf(tactic));
    }

    @Override
    public void addThreat(MobKey mob, UUID participantId, double amount) {
        requireServerThread();
        if (!Double.isFinite(amount) || amount == 0.0) {
            return;
        }
        var caster = source.caster(mob.entityId());
        var participant = entities.apply(participantId).filter(CombatActionsAdapter::present);
        if (caster.isPresent() && participant.isPresent()
                && caster.get().getWorld().equals(participant.get().getWorld())) {
            source.changeThreat(mob.entityId(), participant.get(), amount);
        }
    }

    /**
     * Releases a tell without a shield check (M23.4, errata E23-5). Invulnerability is decided by the engine with the
     * shown length. The skill stores the tell flag.
     */
    @Override
    public ReleaseOutcome release(MobKey mob, SkillChoice choice, Movement intentMovement, UUID expectedTarget,
                                  CastInterrupt interrupt) {
        requireServerThread();
        Objects.requireNonNull(expectedTarget, "expectedTarget");
        Objects.requireNonNull(interrupt, "interrupt");
        if (!liveTargetMatches(mob, expectedTarget)) {
            return ReleaseOutcome.TARGET_LOST;
        }
        var caster = source.caster(mob.entityId());
        var target = source.target(mob.entityId());
        if (caster.isEmpty() || target.isEmpty()) {
            return ReleaseOutcome.TARGET_LOST;
        }
        var melee = choice.kind() == ChoiceKind.VANILLA_MELEE;
        var skill = melee ? Optional.<Skill>empty() : skills.apply(choice.mythicSkillId().orElseThrow());
        if (!melee && skill.isEmpty()) {
            return ReleaseOutcome.REJECTED;
        }
        if (skill.isPresent() && source.onCooldown(mob.entityId(), skill.get())) {
            return ReleaseOutcome.REJECTED;
        }
        if (!withinReach(mob.entityId(), choice, caster.get(), target.get())) {
            return ReleaseOutcome.WHIFFED;
        }
        var conditionsHold = skill.isEmpty() || source.conditionsHoldAt(mob.entityId(), skill.get(), target.get());
        if (!conditionsHold) {
            return ReleaseOutcome.REJECTED;
        }
        if (melee) {
            caster.get().attack(target.get());
            caster.get().swingMainHand();
            return ReleaseOutcome.RELEASED;
        }
        var executed = source.executeAt(mob.entityId(), skill.get(), target.get(), interrupt.flag());
        return executed ? ReleaseOutcome.RELEASED : ReleaseOutcome.REJECTED;
    }

    @Override
    public void cue(MobKey mob, CueRequest request) {
        requireServerThread();
        if (source.handlesSignal(mob.entityId(), request.signal())) {
            source.signal(mob.entityId(), request.signal());
        }
    }

    @Override
    public void writeStanceVariable(MobKey mob, String stance) {
        requireServerThread();
        source.writeVariable(mob.entityId(), "predictor_stance", stance);
    }

    @Override
    public void writeNativeStance(MobKey mob, String stance) {
        requireServerThread();
        source.setStance(mob.entityId(), stance);
    }

    /** A player online and alive, as in 0.0.1; any other entity valid and alive (M17.7). */
    private static boolean present(LivingEntity entity) {
        return entity instanceof Player player ? player.isOnline() && !player.isDead()
                : entity.isValid() && !entity.isDead();
    }

    /**
     * The checks that make a cast wait for a later reactive cycle, in order: native cooldown, reach and sight, then for a
     * damaging choice aimed at the target the invulnerability window and the shield or flank angle, and last the
     * gating conditions of the skill, evaluated without side effects.
     *
     * @return the outcome that makes the cast wait or skip it, empty when it may run now
     */
    private Optional<ExecutionOutcome> waiting(Attempt attempt) {
        var choice = attempt.choice();
        var target = attempt.target();
        var skill = attempt.skill();
        if (skill.isPresent() && source.onCooldown(attempt.mobId(), skill.get())
                || target.isPresent() && !withinReach(attempt.mobId(), choice, attempt.caster(), target.get())) {
            return Optional.of(ExecutionOutcome.NOT_ELIGIBLE);
        }
        if (HitChecks.damaging(choice) && target.isPresent()) {
            var blocked = gate(choice, attempt.movement() == Movement.FLANK, attempt.caster(), target.get());
            if (blocked.isPresent()) {
                return blocked;
            }
        }
        var conditionsHold = skill.isEmpty() || (target.isPresent()
                ? source.conditionsHoldAt(attempt.mobId(), skill.get(), target.get())
                : source.conditionsHold(attempt.mobId(), skill.get()));
        return conditionsHold ? Optional.empty() : Optional.of(ExecutionOutcome.NOT_ELIGIBLE);
    }

    /** Invulnerability, then the flank angle or the shield; empty when the hit may land now. */
    private Optional<ExecutionOutcome> gate(SkillChoice choice, boolean flanking, LivingEntity caster,
                                            LivingEntity target) {
        if (HitChecks.deferInvulnerable(choice, target)) {
            return Optional.of(ExecutionOutcome.DEFERRED_INVULNERABLE);
        }
        var angle = flanking ? shieldAngle.apply(target) : OptionalDouble.empty();
        if (angle.isPresent()) {
            return ShieldGeometry.isOutsideBlockingAngle(target.getLocation(), caster.getLocation(), angle.getAsDouble())
                    ? Optional.empty() : Optional.of(ExecutionOutcome.NOT_ELIGIBLE);
        }
        if (choice.purpose().filter(SkillPurpose::ignoresShield).isEmpty() && blocking.test(target)) {
            return Optional.of(ExecutionOutcome.SKIPPED_SHIELD);
        }
        return Optional.empty();
    }

    /**
     * Reach and sight by delivery ({@link HitChecks#reach}). An untyped skill is always in reach. Vanilla melee and a
     * typed purpose read the world; the melee reach of the session profile applies only to vanilla melee.
     */
    private boolean withinReach(UUID mobId, SkillChoice choice, LivingEntity caster, LivingEntity target) {
        if (choice.purpose().isEmpty() && choice.kind() != ChoiceKind.VANILLA_MELEE) {
            return true;
        }
        var reach = movement.apply(mobId).map(MovementSettings::meleeReachBlocks).orElse(DEFAULT_MELEE_REACH_BLOCKS);
        return caster.getWorld().equals(target.getWorld())
                && HitChecks.reach(choice, caster, target, reach, mobId, sight) == HitChecks.ReachVerdict.IN_REACH;
    }

    private void requireServerThread() {
        if (!serverThread.getAsBoolean()) {
            throw new IllegalStateException("combat actions run on the server thread only");
        }
    }

    /**
     * One cast attempt.
     *
     * @param mobId UUID of the caster
     * @param choice choice of the intent
     * @param movement movement of the intent
     * @param caster living caster
     * @param target current target, if any
     * @param skill loaded skill of a {@code MYTHIC_SKILL} choice; empty for a vanilla melee choice
     */
    private record Attempt(UUID mobId, SkillChoice choice, Movement movement, LivingEntity caster,
                           Optional<LivingEntity> target, Optional<Skill> skill) {
    }
}
