package akyrion.thepredict.adapter.server.vocabulary;

import akyrion.thepredict.domain.EnemyPattern;
import akyrion.thepredict.domain.IntentView;
import akyrion.thepredict.engine.IntentBoard;
import akyrion.thepredict.engine.ServerClock;
import io.lumine.mythic.api.adapters.AbstractEntity;
import io.lumine.mythic.api.config.MythicLineConfig;
import io.lumine.mythic.api.skills.SkillCaster;
import io.lumine.mythic.api.skills.conditions.ICasterCondition;
import io.lumine.mythic.api.skills.conditions.IEntityCondition;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * {@code predictorpattern{pattern=spams_clicks}} (key {@code pattern} or {@code p}). True when the caster's current
 * target has that pattern in {@code ACTIVE}. Reads only {@link IntentBoard}. An unknown name is always false and is
 * reported through {@link #problem()}.
 */
public final class PredictorPatternCondition implements IEntityCondition, ICasterCondition {
    /** Name of the condition. */
    public static final String NAME = "predictorpattern";

    private final IntentBoard board;
    private final ServerClock clock;
    private final int bit;
    private final boolean valid;

    /**
     * Creates the condition from its MythicMobs line.
     *
     * @param config line configuration
     * @param board intent board
     * @param clock clock whose {@code nanoTime} dates the evaluation
     */
    public PredictorPatternCondition(MythicLineConfig config, IntentBoard board, ServerClock clock) {
        this(argument(config), board, clock);
    }

    /**
     * Creates the condition from the pattern token.
     *
     * @param token lower-case pattern name
     * @param board intent board
     * @param clock clock
     */
    public PredictorPatternCondition(String token, IntentBoard board, ServerClock clock) {
        this.board = Objects.requireNonNull(board, "board");
        this.clock = Objects.requireNonNull(clock, "clock");
        var parsed = bitOf(token);
        this.bit = parsed;
        this.valid = parsed >= 0;
    }

    /**
     * Reads {@code pattern} or {@code p}.
     *
     * @param config line configuration
     * @return the token
     */
    public static String argument(MythicLineConfig config) {
        var value = Objects.requireNonNull(config, "config").getString(new String[] {"pattern", "p"}, "");
        return value == null ? "" : value;
    }

    /**
     * The argument problem found while parsing, so the registrar can warn once per load wave (C13 amended).
     *
     * @return the problem, or empty when the pattern is an enemy pattern
     */
    public Optional<String> problem() {
        return valid ? Optional.empty() : Optional.of("pattern is not an enemy pattern");
    }

    /**
     * Whether the published view has the pattern.
     *
     * @param view the caster view
     * @return the match
     */
    public boolean matches(IntentView view) {
        return valid && view != null && view.generation() >= 0 && (view.targetPatternMask() & bit) != 0;
    }

    @Override
    public boolean check(AbstractEntity entity) {
        if (entity == null || !valid) {
            return false;
        }
        var view = board.current(entity.getUniqueId());
        return view.controls(clock.nanoTime()) && matches(view);
    }

    @Override
    public boolean check(SkillCaster caster) {
        return caster != null && check(caster.getEntity());
    }

    private static int bitOf(String token) {
        if (token == null || token.isBlank()) {
            return -1;
        }
        try {
            return EnemyPattern.valueOf(token.toUpperCase(Locale.ROOT)).bit();
        } catch (IllegalArgumentException exception) {
            return -1;
        }
    }
}
