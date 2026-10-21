package akyrion.thepredict.adapter.server.vocabulary;

import akyrion.thepredict.domain.IntentView;
import akyrion.thepredict.engine.IntentBoard;
import akyrion.thepredict.engine.ServerClock;
import io.lumine.mythic.api.adapters.AbstractEntity;
import io.lumine.mythic.api.skills.SkillCaster;
import io.lumine.mythic.api.skills.conditions.ICasterCondition;
import io.lumine.mythic.api.skills.conditions.IEntityCondition;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Common evaluation of the {@code predictor*} conditions (M04.2).
 *
 * <p>Implements {@link IEntityCondition} for mob goal {@code mobconditions} and {@link ICasterCondition}
 * for skill {@code Conditions}. MythicMobs may evaluate either on any thread (rule T3), so evaluation
 * only reads {@link IntentBoard} and {@link ServerClock#nanoTime()}, allocates nothing and writes no log.
 * Every invalid state is false (rule A6): an invalid argument, a missing caster or entity, a mob without
 * a view, a {@code BASE} view and an expired view.
 *
 * <p>Thread rule T3 and the entity UUID: the only entity access is {@link AbstractEntity#getUniqueId()}.
 * MythicMobs' {@code BukkitEntity} caches the UUID after its first read, and that first read calls
 * {@code org.bukkit.entity.Entity#getUniqueId()}, which in Paper 26.2 returns the {@code uuid} field of
 * the server entity ({@code CraftEntity#getUniqueId} and {@code Entity#getUUID}, javap). It is an
 * identity read without world access, locks or side effects, and is the one Bukkit-backed call T3 allows.
 * For skill conditions the caster's entity comes from MythicMobs' {@code ActiveMob.getEntity()}: when it
 * has no cached entity off the main thread, it schedules a sync lookup with {@code Schedulers.sync().run}
 * and returns {@code null} (javap), which these conditions treat as false.
 */
abstract sealed class IntentCondition implements IEntityCondition, ICasterCondition
        permits PredictorControlCondition, PredictorMoveCondition, PredictorTacticCondition, PredictorStanceCondition,
        PredictorRoleCondition, PredictorPlanCondition, PredictorTokenCondition {
    private final IntentBoard board;
    private final ServerClock clock;
    private final String problem;

    /**
     * Stores the dependencies and the argument problem found while parsing.
     *
     * @param board intent board read on evaluation
     * @param clock clock whose {@code nanoTime} dates the evaluation
     * @param problem description of an invalid argument, or empty when the argument is valid
     */
    IntentCondition(IntentBoard board, ServerClock clock, String problem) {
        this.board = Objects.requireNonNull(board, "board");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.problem = Objects.requireNonNull(problem, "problem");
    }

    /**
     * Tells whether the arguments were valid; an invalid condition is always false.
     *
     * @return {@code true} when the condition can match
     */
    public final boolean isValid() {
        return problem.isEmpty();
    }

    /**
     * Describes why the arguments are invalid, for a load warning. Never contains arbitrary author text.
     *
     * @return the problem, or empty when the condition is valid
     */
    public final Optional<String> problem() {
        return problem.isEmpty() ? Optional.empty() : Optional.of(problem);
    }

    /**
     * Evaluates the condition for a mob UUID. Any thread; allocation-free.
     *
     * @param entityId UUID of the evaluated mob
     * @return whether the current view of that mob satisfies the condition
     */
    public final boolean test(UUID entityId) {
        return entityId != null && isValid() && accepts(board.current(entityId), clock.nanoTime());
    }

    /**
     * Evaluates the condition for an entity (mob goal {@code mobconditions}). Any thread.
     *
     * @param entity evaluated entity
     * @return whether its current view satisfies the condition
     */
    @Override
    public final boolean check(AbstractEntity entity) {
        return entity != null && test(entity.getUniqueId());
    }

    /**
     * Evaluates the condition for a skill caster (skill {@code Conditions}). Any thread.
     *
     * @param caster evaluated caster
     * @return whether the current view of its entity satisfies the condition
     */
    @Override
    public final boolean check(SkillCaster caster) {
        return caster != null && check(caster.getEntity());
    }

    /**
     * Pure rule of the concrete condition.
     *
     * @param view current view of the mob, {@link IntentView#ABSENT} without a session
     * @param nowNanos current monotonic time
     * @return whether the view satisfies the condition
     */
    abstract boolean accepts(IntentView view, long nowNanos);
}
