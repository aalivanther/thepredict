package akyrion.thepredict.adapter.server.vocabulary;

import akyrion.thepredict.domain.IntentView;
import akyrion.thepredict.domain.ZoneFact;
import akyrion.thepredict.engine.IntentBoard;
import akyrion.thepredict.engine.ServerClock;
import io.lumine.mythic.api.adapters.AbstractEntity;
import io.lumine.mythic.api.config.MythicLineConfig;
import io.lumine.mythic.api.skills.SkillCaster;
import io.lumine.mythic.api.skills.conditions.ICasterCondition;
import io.lumine.mythic.api.skills.conditions.IEntityCondition;

import java.util.Objects;
import java.util.Optional;

/**
 * {@code predictorzone{fact=cover_near}} (alias {@code predictorzonefact}, key {@code fact} or {@code f}). True when
 * the published zone bit of that fact is on. {@code target_near_drop} is not a condition name (errata E24-1). An
 * unknown name is always false and is reported through {@link #problem()}.
 */
public final class PredictorZoneCondition implements IEntityCondition, ICasterCondition {
    /** Name of the condition. */
    public static final String NAME = "predictorzone";
    /** Alias. */
    public static final String ALIAS = "predictorzonefact";

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
    public PredictorZoneCondition(MythicLineConfig config, IntentBoard board, ServerClock clock) {
        this(argument(config), board, clock);
    }

    /**
     * Creates the condition from the fact token.
     *
     * @param token lower-case fact name
     * @param board intent board
     * @param clock clock
     */
    public PredictorZoneCondition(String token, IntentBoard board, ServerClock clock) {
        this.board = Objects.requireNonNull(board, "board");
        this.clock = Objects.requireNonNull(clock, "clock");
        var parsed = bitOf(token);
        this.bit = parsed;
        this.valid = parsed >= 0;
    }

    /**
     * Reads {@code fact} or {@code f}.
     *
     * @param config line configuration
     * @return the token
     */
    public static String argument(MythicLineConfig config) {
        var value = Objects.requireNonNull(config, "config").getString(new String[] {"fact", "f"}, "");
        return value == null ? "" : value;
    }

    /**
     * The argument problem found while parsing, so the registrar can warn once per load wave (C13 amended).
     *
     * @return the problem, or empty when the fact is one of the exposed zone facts
     */
    public Optional<String> problem() {
        return valid ? Optional.empty() : Optional.of("fact is not an exposed zone fact");
    }

    /**
     * Whether the published view has the fact.
     *
     * @param view the view
     * @return the match
     */
    public boolean matches(IntentView view) {
        return valid && view != null && view.generation() >= 0 && (view.zoneFacts() & (1 << bit)) != 0;
    }

    @Override
    public boolean check(AbstractEntity entity) {
        if (entity == null || !valid) {
            return false;
        }
        var view = board.current(entity.getUniqueId());
        return view.controls(clock.nanoTime()) && (view.zoneFacts() & (1 << bit)) != 0;
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
            var fact = ZoneFact.exposed(token.toLowerCase(java.util.Locale.ROOT));
            return fact.ordinal();
        } catch (IllegalArgumentException exception) {
            return -1;
        }
    }
}
