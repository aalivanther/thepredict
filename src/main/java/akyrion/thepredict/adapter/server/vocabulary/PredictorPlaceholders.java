package akyrion.thepredict.adapter.server.vocabulary;

import akyrion.thepredict.domain.CombatStance;
import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.IntentView;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.TellAnswer;
import akyrion.thepredict.domain.ZoneFact;
import akyrion.thepredict.engine.IntentBoard;
import akyrion.thepredict.engine.ServerClock;
import io.lumine.mythic.api.skills.placeholders.PlaceholderManager;
import io.lumine.mythic.core.skills.placeholders.Placeholder;
import io.lumine.mythic.core.skills.placeholders.PlaceholderMeta;

import java.util.Locale;
import java.util.Objects;

/**
 * The {@code <caster.predictor.*>} placeholders (M04.4): tactic, movement, level, confidence and state
 * of the caster's current view.
 *
 * <p>Registered under the names {@code caster.predictor.tactic}, {@code movement}, {@code level},
 * {@code confidence} and {@code state}; DT-11 confirms in game that this produces
 * {@code <caster.predictor.tactic>}. Values only read {@link IntentBoard} and {@link ServerClock#nanoTime()}
 * and are safe on any thread (rule T3); the UUID read follows the note in {@code IntentCondition}.
 * Every text is precomputed, so formatting allocates nothing (rule C12).
 */
public final class PredictorPlaceholders {
    /** Prefix of every registered placeholder name. */
    public static final String PREFIX = "caster.predictor.";

    /** Text of every field without a session, the level and the confidence included (DOC-10). */
    public static final String NONE = "none";

    /** Text of the state while the view controls the mob. */
    public static final String CONTROLLED = "controlled";

    /** Text of the level at {@code BASE} and of the state when the view does not control. */
    public static final String BASE = "base";

    private static final String[] MOVEMENT_TEXT = lowerCaseNames(Movement.values());
    private static final String[] LEVEL_TEXT = lowerCaseNames(ControlLevel.values());
    private static final String[] CONFIDENCE_TEXT = confidenceTexts();
    private static final String[] PRESSURE_TEXT = {"0", "1", "2", "3", "4", "5", "6", "7", "8"};
    private static final String[] STANCE_TEXT = lowerCaseNames(CombatStance.values());
    private static final String[] ANSWER_TEXT = lowerCaseNames(TellAnswer.values());
    private static final String[] COUNT_TEXT = countTexts();
    private static final String[] DANGER_TEXT = {"none", "harm", "lethal", "unknown"};
    private static final String[] ZONE_TEXT = zoneTexts();
    private static final String[] ROLE_TEXT = lowerCaseNames(akyrion.thepredict.domain.GroupRole.values());
    private static final String[] PLAN_TEXT = lowerCaseNames(akyrion.thepredict.domain.GroupPlan.values());
    private static final String[] TOKEN_TEXT = {"none", "melee", "ranged", "both"};
    private static final String[] PATTERN_TEXT = lowerCaseNames(akyrion.thepredict.domain.EnemyPattern.values());
    private static final String[] ACTION_TEXT = lowerCaseNames(akyrion.thepredict.domain.EnemyAction.values());
    private static final int ACTIVE_PATTERNS = (1 << 25) - 1;
    private static final int BEST_PATTERN = 1 << 30;
    private static final int CADENCE_BIT = 1 << 24;

    /** Values a placeholder can show. */
    public enum Field {
        /** Current tactic, {@code none} without a session. */
        TACTIC,
        /** Movement in lower case, {@code none} without a session. */
        MOVEMENT,
        /**
         * Control level in lower case ({@code jev}, {@code alternative}, {@code local}, {@code base}), {@code none}
         * without a session.
         */
        LEVEL,
        /** Confidence with two decimals and {@link Locale#ROOT}, {@code none} without a session. */
        CONFIDENCE,
        /** {@code controlled} while the view controls, {@code base} otherwise, {@code none} without a session. */
        STATE,
        /** Rung 0–8 while the controller plays, {@code none} otherwise. */
        PRESSURE,
        /** Visible stance in lower case, {@code none} without a session. */
        STANCE,
        /** Poise percent 0–100, {@code none} when it was not published. */
        POISE,
        /** Tell answer in lower case, {@code none} without a tell. */
        ANSWER,
        /** Ticks until impact, {@code none} without a tell. */
        TELEGRAPH,
        /** Published zone facts, {@code none} without a ready zone. */
        ZONE,
        /** Push danger, {@code none} without a ready zone. */
        PUSHDANGER,
        /** Published group role, {@code none} when the group assigned none. */
        ROLE,
        /** Published group plan, {@code none} when the group has none. */
        PLAN,
        /** Held token, {@code melee}, {@code ranged}, {@code both} or {@code none}. */
        TOKEN,
        /** Published slot, {@code none} when the group assigned none. */
        SLOT,
        /** Squad size, {@code none} when the member has no squad. */
        SQUAD,
        /** Highest-confidence pattern of the target, or {@code none}. */
        PATTERN,
        /** Forecast action already gated by accuracy, or {@code none}. */
        FORECAST;

        /**
         * Returns the full registration name, for example {@code caster.predictor.tactic}.
         *
         * @return placeholder name without angle brackets
         */
        public String placeholderName() {
            return PREFIX + name().toLowerCase(Locale.ROOT);
        }
    }

    private final IntentBoard board;
    private final ServerClock clock;

    /**
     * Creates the placeholder set.
     *
     * @param board intent board read on evaluation
     * @param clock clock whose {@code nanoTime} dates the evaluation
     */
    public PredictorPlaceholders(IntentBoard board, ServerClock clock) {
        this.board = Objects.requireNonNull(board, "board");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Registers every field with MythicMobs. Server thread; call at enable and on each
     * {@code MythicPlaceholdersLoadEvent}.
     *
     * <p>{@code Placeholder.meta} is {@code @Deprecated(forRemoval = true)} in MythicMobs 5.13.1-SNAPSHOT
     * and logs a deprecated-API warning once. The non-deprecated path, {@code PlaceholderManager.register(Class)}
     * and {@code CustomComponentRegistry}, instantiates placeholder classes by reflection with a fixed
     * {@code GenericPlaceholderArguments} constructor, so the board could only be reached through static
     * state, which rule D8 forbids. The decision is tracked as DT-13 in the M01 decision register.
     *
     * @param manager MythicMobs placeholder manager
     */
    @SuppressWarnings("removal")
    public void registerWith(PlaceholderManager manager) {
        Objects.requireNonNull(manager, "manager");
        for (var field : Field.values()) {
            manager.register(field.placeholderName(), Placeholder.meta((meta, argument) -> resolve(field, meta)));
        }
    }

    /**
     * Resolves one field for the caster of a placeholder evaluation. Any thread; allocation-free. Only
     * {@link Field#TELEGRAPH} reads the server tick, because that read is a Bukkit call in production (rule T3) and
     * no other field uses it.
     *
     * @param field requested field
     * @param meta placeholder metadata; a missing caster or entity reads as no session
     * @return the text shown to players
     */
    public String resolve(Field field, PlaceholderMeta meta) {
        var caster = meta == null ? null : meta.getCaster();
        var entity = caster == null ? null : caster.getEntity();
        var entityId = entity == null ? null : entity.getUniqueId();
        var view = entityId == null ? IntentView.ABSENT : board.current(entityId);
        var tick = field == Field.TELEGRAPH ? board.publishedTick() : 0L;
        return format(field, view, clock.nanoTime(), tick);
    }

    /**
     * Formats one field; pure, allocation-free and tested without MythicMobs.
     *
     * @param field requested field
     * @param view current view, {@link IntentView#ABSENT} without a session
     * @param nowNanos current monotonic time
     * @return the text shown to players
     */
    public static String format(Field field, IntentView view, long nowNanos) {
        return format(field, view, nowNanos, 0L);
    }

    /**
     * Formats one field. {@code tick} is the server tick used by {@link Field#TELEGRAPH}.
     *
     * @param field requested field
     * @param view current view
     * @param nowNanos current monotonic time
     * @param tick current server tick
     * @return the text shown to players
     */
    public static String format(Field field, IntentView view, long nowNanos, long tick) {
        return switch (field) {
            case TACTIC -> view.tactic().value();
            case MOVEMENT -> MOVEMENT_TEXT[view.movement().ordinal()];
            case LEVEL -> view.generation() < 0 ? NONE : LEVEL_TEXT[view.level().ordinal()];
            case CONFIDENCE -> view.generation() < 0 ? NONE
                    : CONFIDENCE_TEXT[(int) Math.round((double) view.confidence() * 100.0)];
            case STATE -> view.generation() < 0 ? NONE : view.controls(nowNanos) ? CONTROLLED : BASE;
            case PRESSURE -> view.pressure() < 0 || view.pressure() > 8 ? NONE : PRESSURE_TEXT[view.pressure()];
            case STANCE -> view.generation() < 0 ? NONE : STANCE_TEXT[view.stance().ordinal()];
            case POISE -> view.poisePercent() < 0 ? NONE : COUNT_TEXT[view.poisePercent()];
            case ANSWER -> answerText(view);
            case TELEGRAPH -> remaining(view.impactAtTick(), tick);
            case ZONE -> view.generation() < 0 || view.zoneFacts() < 0 || view.zoneFacts() >= ZONE_TEXT.length
                    ? NONE : ZONE_TEXT[view.zoneFacts()];
            case PUSHDANGER -> view.generation() < 0 ? NONE : DANGER_TEXT[view.pushDanger()];
            case ROLE -> roleText(view);
            case PLAN -> planText(view);
            case TOKEN -> view.generation() < 0 || view.tokenMask() <= 0 || view.tokenMask() >= TOKEN_TEXT.length
                    ? NONE : TOKEN_TEXT[view.tokenMask()];
            case SLOT -> view.generation() < 0 || view.slotIndex() < 0 || view.slotIndex() >= COUNT_TEXT.length
                    ? NONE : COUNT_TEXT[view.slotIndex()];
            case SQUAD -> view.generation() < 0 || view.squadSize() <= 0 || view.squadSize() >= COUNT_TEXT.length
                    ? NONE : COUNT_TEXT[view.squadSize()];
            case PATTERN -> patternName(view);
            case FORECAST -> view.generation() < 0 || view.forecast() < 0 ? NONE : ACTION_TEXT[view.forecast()];
        };
    }

    private static String answerText(IntentView view) {
        var answer = view.answer();
        return answer.isPresent() ? ANSWER_TEXT[answer.get().ordinal()] : NONE;
    }

    private static String roleText(IntentView view) {
        var role = view.role();
        return view.generation() < 0 || role.isEmpty() ? NONE : ROLE_TEXT[role.get().ordinal()];
    }

    private static String planText(IntentView view) {
        var plan = view.plan();
        return view.generation() < 0 || plan.isEmpty() ? NONE : PLAN_TEXT[plan.get().ordinal()];
    }

    private static String patternName(IntentView view) {
        if (view.generation() < 0) {
            return NONE;
        }
        var packed = view.targetPatternMask();
        if ((packed & BEST_PATTERN) != 0) {
            var ordinal = (packed >>> 25) & 31;
            if (ordinal < PATTERN_TEXT.length && (CADENCE_BIT & (1 << ordinal)) == 0) {
                return PATTERN_TEXT[ordinal];
            }
        }
        var mask = packed & ACTIVE_PATTERNS & ~CADENCE_BIT;
        return mask == 0 ? NONE : PATTERN_TEXT[Integer.numberOfTrailingZeros(mask)];
    }

    private static String[] zoneTexts() {
        var texts = new String[1 << ZoneFact.values().length];
        texts[0] = NONE;
        for (var mask = 1; mask < texts.length; mask++) {
            var builder = new StringBuilder();
            for (var fact : ZoneFact.values()) {
                if (fact.exposed() && (mask & (1 << fact.ordinal())) != 0) {
                    if (!builder.isEmpty()) {
                        builder.append(',');
                    }
                    builder.append(fact.token());
                }
            }
            texts[mask] = builder.isEmpty() ? NONE : builder.toString();
        }
        return texts;
    }

    private static String remaining(long impactAtTick, long tick) {
        if (impactAtTick < 0) {
            return NONE;
        }
        var left = impactAtTick - tick;
        if (left <= 0) {
            return COUNT_TEXT[0];
        }
        if (left >= COUNT_TEXT.length) {
            return COUNT_TEXT[COUNT_TEXT.length - 1];
        }
        return COUNT_TEXT[(int) left];
    }

    private static String[] countTexts() {
        var texts = new String[201];
        for (var value = 0; value < texts.length; value++) {
            texts[value] = Integer.toString(value);
        }
        return texts;
    }

    /**
     * Lower-case names indexed by ordinal.
     *
     * @param constants enum constants in ordinal order
     * @return names in lower case with {@link Locale#ROOT}
     */
    private static String[] lowerCaseNames(Enum<?>[] constants) {
        var names = new String[constants.length];
        for (var constant : constants) {
            names[constant.ordinal()] = constant.name().toLowerCase(Locale.ROOT);
        }
        return names;
    }

    /**
     * Texts {@code 0.00} to {@code 1.00} indexed by hundredths. A float confidence times 100 is exact in
     * double precision, so {@code Math.round} gives the same half-up rounding as {@code %.2f}.
     *
     * @return 101 texts
     */
    private static String[] confidenceTexts() {
        var texts = new String[101];
        for (var hundredths = 0; hundredths <= 100; hundredths++) {
            var fraction = hundredths % 100;
            texts[hundredths] = (hundredths / 100) + (fraction < 10 ? ".0" : ".") + fraction;
        }
        return texts;
    }
}
