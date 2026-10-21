package akyrion.thepredict.adapter.server.vocabulary;

import akyrion.thepredict.engine.ContextSignals;
import akyrion.thepredict.engine.IntentBoard;
import akyrion.thepredict.engine.ServerClock;
import io.lumine.mythic.api.config.MythicLineConfig;
import io.lumine.mythic.api.skills.conditions.ISkillCondition;
import io.lumine.mythic.api.skills.placeholders.PlaceholderManager;
import io.lumine.mythic.bukkit.events.MythicConditionLoadEvent;
import io.lumine.mythic.bukkit.events.MythicMechanicLoadEvent;
import io.lumine.mythic.bukkit.events.MythicPlaceholdersLoadEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Registers ThePredict's MythicMobs vocabulary through the MythicMobs load events (rules C15 and D8,
 * M04.5): the conditions {@code predictorcontrol}, {@code predictormove} and {@code predictortactic},
 * the mechanic {@code predictordecide} and the {@code <caster.predictor.*>} placeholders. Names match without case
 * (rule I8).
 *
 * <p>MythicMobs resolves custom components one tick after reading them, so registering this listener
 * in {@code onEnable} reaches the first load; every {@code /mm reload} fires the events again. The
 * placeholders are registered once at enable with {@link #registerPlaceholders} and again on every
 * {@code MythicPlaceholdersLoadEvent}. Dependencies arrive through the constructor; there is no static
 * state and {@code CustomComponentRegistry} is not used. Handlers run on the thread MythicMobs fires the
 * event on; they only build components and write rate-limited load messages (rule C8).
 */
public final class VocabularyRegistrar implements Listener {
    /** Every component name this registrar answers, in lower case. */
    public static final List<String> NAMES = List.of(PredictorControlCondition.NAME, PredictorMoveCondition.NAME,
            PredictorTacticCondition.NAME, PredictorStanceCondition.NAME, PredictorZoneCondition.NAME,
            PredictorZoneCondition.ALIAS, PredictorRoleCondition.NAME, PredictorPlanCondition.NAME,
            PredictorTokenCondition.NAME, PredictorPatternCondition.NAME, PredictorDecideMechanic.NAME);

    private final IntentBoard board;
    private final ContextSignals signals;
    private final ServerClock clock;
    private final PredictorPlaceholders placeholders;
    private final Consumer<String> info;
    private final LoadWaveLog resolutions;
    private final LoadWaveLog warnings;

    /**
     * Creates the registrar; the composition root registers it with Bukkit during {@code onEnable}.
     *
     * @param board intent board read by the conditions and placeholders
     * @param signals queue fed by {@code predictordecide}
     * @param clock clock whose {@code nanoTime} dates evaluations and load waves
     * @param info thread-safe receiver of evidence lines
     * @param warning thread-safe receiver of warnings about invalid arguments
     */
    public VocabularyRegistrar(IntentBoard board, ContextSignals signals, ServerClock clock, Consumer<String> info,
                               Consumer<String> warning) {
        this.board = Objects.requireNonNull(board, "board");
        this.signals = Objects.requireNonNull(signals, "signals");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.info = Objects.requireNonNull(info, "info");
        this.placeholders = new PredictorPlaceholders(board, clock);
        this.resolutions = new LoadWaveLog(info, clock::nanoTime);
        this.warnings = new LoadWaveLog(Objects.requireNonNull(warning, "warning"), clock::nanoTime);
    }

    /**
     * Supplies a ThePredict condition when MythicMobs resolves one of its names.
     *
     * @param event MythicMobs condition load event
     */
    @EventHandler
    public void onConditionLoad(MythicConditionLoadEvent event) {
        var name = event.getConditionName();
        if (name == null) {
            return;
        }
        var condition = conditionNamed(name.toLowerCase(Locale.ROOT), event.getConfig());
        if (condition.isEmpty()) {
            return;
        }
        event.register(condition.get());
        resolved("condition", name);
        problemOf(condition.get()).ifPresent(problem -> warnings.write(name.toLowerCase(Locale.ROOT) + ": " + problem
                + "; the condition is always false"));
    }

    /** The condition of one of our lower-case names, empty for a name of another plugin. */
    private Optional<ISkillCondition> conditionNamed(String lower, MythicLineConfig config) {
        return Optional.ofNullable(switch (lower) {
            case PredictorControlCondition.NAME -> new PredictorControlCondition(config, board, clock);
            case PredictorMoveCondition.NAME -> new PredictorMoveCondition(config, board, clock);
            case PredictorTacticCondition.NAME -> new PredictorTacticCondition(config, board, clock);
            case PredictorStanceCondition.NAME -> new PredictorStanceCondition(config, board, clock);
            case PredictorRoleCondition.NAME -> new PredictorRoleCondition(config, board, clock);
            case PredictorPlanCondition.NAME -> new PredictorPlanCondition(config, board, clock);
            case PredictorTokenCondition.NAME -> new PredictorTokenCondition(config, board, clock);
            case PredictorPatternCondition.NAME -> new PredictorPatternCondition(config, board, clock);
            case PredictorZoneCondition.NAME, PredictorZoneCondition.ALIAS -> new PredictorZoneCondition(config, board,
                    clock);
            default -> null;
        });
    }

    /** The argument problem of a registered condition; every condition of ours reports one the same way (C13). */
    private static Optional<String> problemOf(ISkillCondition condition) {
        if (condition instanceof IntentCondition intent) {
            return intent.problem();
        }
        if (condition instanceof PredictorZoneCondition zone) {
            return zone.problem();
        }
        return condition instanceof PredictorPatternCondition pattern ? pattern.problem() : Optional.empty();
    }

    /**
     * Supplies a ThePredict mechanic when MythicMobs resolves one of its names.
     *
     * @param event MythicMobs mechanic load event
     */
    @EventHandler
    public void onMechanicLoad(MythicMechanicLoadEvent event) {
        var name = event.getMechanicName();
        if (isNamed(name, PredictorDecideMechanic.NAME)) {
            event.register(new PredictorDecideMechanic(signals));
            resolved("mechanic", name);
        }
    }

    /**
     * Registers the placeholders again after MythicMobs reloads its placeholder registry.
     *
     * @param event MythicMobs placeholder load event
     */
    @EventHandler
    public void onPlaceholdersLoad(MythicPlaceholdersLoadEvent event) {
        placeholders.registerWith(event.getManager());
        info.accept("Registered <caster.predictor.*> placeholders after MythicPlaceholdersLoadEvent");
    }

    /**
     * Registers the placeholders directly; {@code MythicPlaceholdersLoadEvent} of the first load fires
     * before ThePredict is enabled. Server thread, during {@code onEnable}.
     *
     * @param manager MythicMobs placeholder manager
     */
    public void registerPlaceholders(PlaceholderManager manager) {
        placeholders.registerWith(manager);
    }

    /**
     * Compares a MythicMobs component name with one of ours without distinguishing case (rule I8).
     *
     * @param written name as MythicMobs passes it, possibly {@code null}
     * @param expected lower-case name of ThePredict
     * @return whether they are the same component
     */
    public static boolean isNamed(String written, String expected) {
        return written != null && written.toLowerCase(Locale.ROOT).equals(expected);
    }

    /** Writes the evidence line of a resolved component once per load wave. */
    private void resolved(String kind, String name) {
        resolutions.write("MythicMobs resolved " + kind + " " + name + " on thread " + Thread.currentThread().getName());
    }
}
