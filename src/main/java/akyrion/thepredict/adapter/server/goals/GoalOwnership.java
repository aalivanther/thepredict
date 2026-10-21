package akyrion.thepredict.adapter.server.goals;

import akyrion.thepredict.domain.GoalSummary;
import akyrion.thepredict.domain.InstallResult;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.MovementProgress;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.engine.MobControl;
import akyrion.thepredict.engine.ServerClock;
import io.lumine.mythic.core.mobs.ActiveMob;
import org.bukkit.entity.Mob;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * Implements {@link MobControl}: ThePredict owns the movement AI of a bound mob by installing the goal set
 * generated from its profile (M05.2, M05.4 and M05.5). Server thread only (rule T1): every port method
 * rejects other threads with {@link IllegalStateException}.
 *
 * <p>The adapter is stateless per mob (rule A3): the installed or refused revision and generation live in the
 * {@code owner} marker of each mob. A failed or incomplete installation (a MythicMobs line that produced no
 * goal) removes ThePredict's Paper goals, reapplies the mob's {@code AIGoalSelectors}, leaves a refusal marker,
 * is logged with a limit (C8) and returns {@code Unsupported}; a repeated call with the same revision and
 * generation returns it again without touching the goals. Only an unavailable mob is {@code Deferred}.
 */
public final class GoalOwnership implements MobControl {
    /** Reason of {@code Deferred} when the entity is not a live Paper mob with an {@code ActiveMob}. */
    public static final String ENTITY_UNAVAILABLE = "entity unavailable";

    private final Function<UUID, Optional<ActiveMob>> activeMobs;
    private final GoalSetInstaller installer;
    private final GoalLineGenerator generator;
    private final BaseAiResolver resolver;
    private final LongSupplier catalogRevision;
    private final BooleanSupplier serverThread;
    private final FailureLog failures;

    /**
     * Creates the port implementation.
     *
     * @param activeMobs {@code MythicBukkit.inst().getMobManager()::getActiveMob}
     * @param installer native goal writer
     * @param generator goal set generator
     * @param resolver base AI resolver
     * @param catalogRevision current catalog revision (rule I11)
     * @param serverThread whether the caller runs on the server thread, normally {@code Bukkit::isPrimaryThread}
     * @param clock server clock for the failure log limit
     * @param warnings receiver of rate-limited warnings
     */
    public GoalOwnership(Function<UUID, Optional<ActiveMob>> activeMobs, GoalSetInstaller installer,
                         GoalLineGenerator generator, BaseAiResolver resolver, LongSupplier catalogRevision,
                         BooleanSupplier serverThread, ServerClock clock, Consumer<String> warnings) {
        this.activeMobs = Objects.requireNonNull(activeMobs, "activeMobs");
        this.installer = Objects.requireNonNull(installer, "installer");
        this.generator = Objects.requireNonNull(generator, "generator");
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.catalogRevision = Objects.requireNonNull(catalogRevision, "catalogRevision");
        this.serverThread = Objects.requireNonNull(serverThread, "serverThread");
        this.failures = new FailureLog(clock, warnings);
    }

    /**
     * Q4 used by micro goals installed after this call.
     *
     * @param destinations the guard
     */
    public void useDestination(DestinationGuard destinations) {
        installer.useDestination(destinations);
    }

    /**
     * Q4 used by flank goals installed after this call.
     *
     * @param safety the filter
     */
    public void useFlank(FlankSafety safety) {
        installer.useFlank(safety);
    }

    @Override
    public InstallResult ensureInstalled(MobKey key, TacticalProfile profile) {
        requireServerThread();
        Objects.requireNonNull(profile, "profile");
        var live = resolve(key);
        if (live.isEmpty()) {
            return new InstallResult.Deferred(ENTITY_UNAVAILABLE);
        }
        return ensureInstalled(key, profile, live.get(), MovementReading.followRangeBlocks(live.get().entity(),
                profile.movement().rangedReachBlocks()));
    }

    /**
     * Installation steps 2 to 6 of M05.2 on a resolved mob; package-private for tests with doubles.
     *
     * @param key session identity
     * @param profile tactical profile
     * @param live resolved mob
     * @param followRangeBlocks maximum range of {@code APPROACH}
     * @return the installation result
     */
    InstallResult ensureInstalled(MobKey key, TacticalProfile profile, LiveMob live, double followRangeBlocks) {
        var species = SpeciesSupport.unsupportedReason(live.entity());
        if (species.isPresent()) {
            return new InstallResult.Unsupported(species.get());
        }
        var revision = catalogRevision.getAsLong();
        try {
            var marker = installer.marker(live.entity());
            if (InstallDecision.decide(marker, revision, key.generation()) == InstallDecision.NO_OP) {
                return marker instanceof InstallDecision.Marker.Refused refused
                        ? new InstallResult.Unsupported(refused.reason())
                        : new InstallResult.Installed(((InstallDecision.Marker.Owned) marker).goalCount());
            }
            var plan = generator.generate(profile, resolver.resolve(profile, live.type()), followRangeBlocks, revision,
                    profile.timing().tickLayer());
            var missing = installer.install(live.entity(), plan, key.generation(), profile.movement());
            if (missing > 0) {
                return refuse(live, key, revision, "install incomplete: " + missing + " MythicMobs goals missing");
            }
            return new InstallResult.Installed(plan.goalCount());
        } catch (RuntimeException | LinkageError error) {
            return refuse(live, key, revision, "install failed: " + error.getClass().getSimpleName());
        }
    }

    @Override
    public MovementProgress progress(MobKey key) {
        requireServerThread();
        return resolve(key).map(live -> MovementReading.of(live.entity(), installer.movementGoalRunning(live.entity())))
                .orElse(MovementReading.NONE);
    }

    @Override
    public List<GoalSummary> describeGoals(MobKey key) {
        requireServerThread();
        return resolve(key).map(live -> installer.inspector().summaries(live.entity())).orElse(List.of());
    }

    @Override
    public void release(MobKey key) {
        requireServerThread();
        resolve(key).ifPresent(this::restore);
    }

    /**
     * Asks MythicMobs to reinstall the mob's own {@code AIGoalSelectors}; it does nothing when those lines have no
     * {@code clear} ({@code MobType.reapplyAIGoalSelectors}, javap; DT-04).
     */
    private static void reapplyMythicGoals(ActiveMob activeMob) {
        activeMob.getType().reapplyAIGoalSelectors(activeMob);
    }

    /** Resolves a live Paper mob with its MythicMobs instance. */
    private Optional<LiveMob> resolve(MobKey key) {
        var activeMob = activeMobs.apply(Objects.requireNonNull(key, "key").entityId());
        if (activeMob.isEmpty() || activeMob.get().getEntity() == null) {
            return Optional.empty();
        }
        var mythic = activeMob.get();
        if (!(mythic.getEntity().getBukkitEntity() instanceof Mob mob) || !mob.isValid()) {
            return Optional.empty();
        }
        return Optional.of(new LiveMob(mob, mythic.getType(), () -> reapplyMythicGoals(mythic)));
    }

    /** Removes ThePredict's Paper goals and reapplies the MythicMobs goals; logs a failure with the limit. */
    private void restore(LiveMob live) {
        try {
            installer.removePaperGoals(live.entity());
            live.restoreMythicGoals().run();
        } catch (RuntimeException | LinkageError error) {
            failures.record(live.entity().getUniqueId(), error.getClass().getSimpleName());
        }
    }

    /** Gives the goals back to MythicMobs, leaves a refusal marker and logs the fixed cause with the limit. */
    private InstallResult refuse(LiveMob live, MobKey key, long revision, String reason) {
        restore(live);
        try {
            installer.markRefused(live.entity(), revision, key.generation(), reason);
        } catch (RuntimeException | LinkageError error) {
            failures.record(key.entityId(), error.getClass().getSimpleName());
        }
        failures.record(key.entityId(), reason);
        return new InstallResult.Unsupported(reason);
    }

    /** Rejects calls outside the server thread (rule T1). */
    private void requireServerThread() {
        if (!serverThread.getAsBoolean()) {
            throw new IllegalStateException("MobControl must be called on the server thread");
        }
    }
}
