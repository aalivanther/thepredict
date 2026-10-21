package akyrion.thepredict;

import akyrion.thepredict.adapter.server.binding.BoundProfile;
import akyrion.thepredict.adapter.server.context.Blocking;
import akyrion.thepredict.adapter.server.context.CombatCapture;
import akyrion.thepredict.adapter.server.context.MythicMemberSource;
import akyrion.thepredict.adapter.server.context.MythicParticipantLookup;
import akyrion.thepredict.adapter.server.context.ParticipantDefaults;
import akyrion.thepredict.adapter.server.context.NativeContextEvaluator;
import akyrion.thepredict.adapter.server.context.PaperEntityReads;
import akyrion.thepredict.adapter.server.context.ShieldGeometry;
import akyrion.thepredict.adapter.server.events.MythicMobDirectory;
import akyrion.thepredict.adapter.server.events.ServerThread;
import akyrion.thepredict.adapter.server.execution.CombatActionsAdapter;
import akyrion.thepredict.adapter.server.execution.MythicCastSource;
import akyrion.thepredict.adapter.server.execution.MythicSkillExecutor;
import akyrion.thepredict.adapter.server.goals.BaseAiResolver;
import akyrion.thepredict.adapter.server.goals.GoalKeys;
import akyrion.thepredict.adapter.server.goals.GoalLineGenerator;
import akyrion.thepredict.adapter.server.goals.GoalOwnership;
import akyrion.thepredict.adapter.server.goals.GoalSetInstaller;
import akyrion.thepredict.adapter.server.vocabulary.VocabularyRegistrar;
import akyrion.thepredict.config.ParticipantSettings;
import akyrion.thepredict.domain.CastThread;
import akyrion.thepredict.domain.MovementSettings;
import akyrion.thepredict.engine.CombatActions;
import akyrion.thepredict.engine.ContextSignals;
import akyrion.thepredict.engine.IntentBoard;
import akyrion.thepredict.engine.MobControl;
import akyrion.thepredict.engine.ServerClock;
import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.bukkit.MythicBukkit;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Builds the adapters over MythicMobs and Paper for {@link ThePredictPlugin} (rule D8): the vocabulary, the goal
 * ownership of M05, the combat reader of M07 and the combat actions of M10. Server thread only, during
 * {@code onEnable}. A failure of one adapter is logged once and replaced by a component that never acts, so bound
 * mobs keep their MythicMobs AI (rule A6).
 */
final class ServerComponents {
    private final JavaPlugin plugin;
    private final IntentBoard board;
    private final ServerClock clock;
    private final Logger logger;

    ServerComponents(JavaPlugin plugin, IntentBoard board, ServerClock clock) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.board = Objects.requireNonNull(board, "board");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.logger = plugin.getLogger();
    }

    /**
     * Registers the MythicMobs vocabulary through load events (M04.5, rule C15) and the placeholders. If it fails,
     * MythicMobs reports the {@code predictor*} names as unknown and ThePredict never controls a mob.
     */
    void registerVocabulary(ContextSignals signals) {
        try {
            var registrar = new VocabularyRegistrar(board, signals, clock, logger::info, logger::warning);
            plugin.getServer().getPluginManager().registerEvents(registrar, plugin);
            registrar.registerPlaceholders(MythicBukkit.inst().getPlaceholderManager());
        } catch (RuntimeException | LinkageError exception) {
            logger.log(Level.SEVERE, "The MythicMobs vocabulary could not be registered", exception);
        }
    }

    /**
     * Builds the goal ownership of M05, the {@code MobControl} of the coordinator.
     *
     * @param catalogRevision revision of the profile catalog
     * @param maxWaypointPaths {@code decision.tickLayer.maxWaypointPathsPerTick}, translated by the root (rule D10)
     * @param maxWaypointMicros {@code decision.tickLayer.maxWaypointPathMicrosPerTick}
     * @return the port, or one that installs nothing when the goal tools failed to start
     */
    MobControl goals(LongSupplier catalogRevision, int maxWaypointPaths, int maxWaypointMicros) {
        try {
            var mythic = MythicBukkit.inst();
            var installer = new GoalSetInstaller(mythic.getVolatileCodeHandler().getAIHandler(), Bukkit.getMobGoals(),
                    GoalKeys.forPlugin(plugin), board, clock, maxWaypointPaths, maxWaypointMicros);
            return new GoalOwnership(mythic.getMobManager()::getActiveMob, installer, new GoalLineGenerator(),
                    new BaseAiResolver(), catalogRevision, Bukkit::isPrimaryThread, clock, logger::warning);
        } catch (RuntimeException | LinkageError exception) {
            logger.log(Level.SEVERE, "Goal ownership could not start; bound mobs keep their MythicMobs AI", exception);
            return new NoMobControl();
        }
    }

    /**
     * Builds the combat reader of M07 over MythicMobs and Paper; the bindings are read at each capture.
     *
     * @param searchRadiusBlocks {@code decision.searchRadiusBlocks}, translated by the root (rule D10)
     * @param bindings bound profile of a MythicMobs mob ID in the current catalog
     */
    CombatCapture combatReader(double searchRadiusBlocks, Function<String, Optional<BoundProfile>> bindings) {
        var mobs = MythicBukkit.inst().getMobManager();
        var lookup = new MythicParticipantLookup(mobs, bindings);
        return new CombatCapture(new MythicMemberSource(mobs, bindings, lookup, MythicBukkit.inst().getSkillManager()),
                new PaperEntityReads(lookup),
                entityId -> Optional.ofNullable(Bukkit.getEntity(entityId)),
                new NativeContextEvaluator(logger::warning, clock::currentTick),
                (condition, entity) -> condition.evaluateEntity(BukkitAdapter.adapt(entity)), clock,
                Bukkit::isPrimaryThread, searchRadiusBlocks, lookup);
    }

    /**
     * Translates the {@code participants} section of {@code config.yml} into the settings of the adapter (rule D10).
     *
     * @param settings the section, validated by {@code config}
     * @return the global participant settings of the binder and the capture
     */
    static ParticipantDefaults participantDefaults(ParticipantSettings settings) {
        return new ParticipantDefaults(settings.global(), settings.profilesMayAllow(),
                settings.maxCandidatesPerMember());
    }

    /**
     * Builds the combat actions of M10: casts with the {@code THEPREDICT} trigger, tactic signals and threat.
     *
     * @param movement movement settings of the profile of a mob's session, read at each cast
     * @param bindings bound profile of a MythicMobs mob ID in the current catalog, whose participant policy decides
     *                 which targets a cast may aim at (M17.7)
     * @return the port, or one that never acts when the execution adapter failed to start
     */
    CombatActions combatActions(Function<UUID, Optional<MovementSettings>> movement,
                                Function<String, Optional<BoundProfile>> bindings, CastThread castThread) {
        try {
            var mythic = MythicBukkit.inst();
            var mobs = mythic.getMobManager();
            var source = new MythicCastSource(mobs, new MythicSkillExecutor(mythic.getSkillManager(), castThread),
                    new MythicMemberSource(mobs, bindings, new MythicParticipantLookup(mobs, bindings)));
            return new CombatActionsAdapter(source, mythic.getSkillManager()::getSkill,
                    ShieldGeometry::raisedShieldAngleDegrees, Blocking::blocking, movement,
                    entityId -> Optional.ofNullable(Bukkit.getEntity(entityId)).filter(LivingEntity.class::isInstance)
                            .map(LivingEntity.class::cast), Bukkit::isPrimaryThread, clock);
        } catch (RuntimeException | LinkageError exception) {
            logger.log(Level.SEVERE, "Combat actions could not start; ThePredict casts no skill", exception);
            return new NoCombatActions();
        }
    }

    /** Returns the directory of active MythicMobs mobs used by the lifecycle scan of M06. */
    MythicMobDirectory mobDirectory() {
        return new MythicMobDirectory(MythicBukkit.inst().getMobManager());
    }

    /** Returns the server thread guard of the listeners: tasks for the next tick and rate-limited warnings. */
    ServerThread serverThread() {
        return new ServerThread(Bukkit::isPrimaryThread, task -> Bukkit.getScheduler().runTask(plugin, task),
                logger::warning);
    }
}
