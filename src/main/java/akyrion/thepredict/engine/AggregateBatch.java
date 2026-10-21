package akyrion.thepredict.engine;

import java.util.List;
import java.util.Objects;

/**
 * One aggregate batch of a run (M21.6). {@code batchId} is 1, 2, 3… per run.
 *
 * @param batchId identity of the batch, {@code >= 1}
 * @param outcomes outcome rows
 * @param decisions daily decision rows
 * @param cache daily cache rows
 * @param difficulty difficulty rows (M22)
 * @param calibration calibration bins (M22)
 * @param patterns pattern-stat rows (M26); empty when the step recorded none
 * @param memory player-memory rows (M27); empty when the step recorded none
 * @param values learned-value rows (M28); empty when the step recorded none
 * @param choices teaching rows (M29); empty when distillation recorded none
 * @param agreements outcome-difference rows (M29); empty when distillation recorded none
 */
public record AggregateBatch(long batchId, List<OutcomeStatDelta> outcomes, List<DecisionDailyDelta> decisions,
                             List<AnswerCacheDelta> cache, List<DifficultyStatDelta> difficulty,
                             List<CalibrationBinDelta> calibration, List<PatternStatDelta> patterns,
                             List<PlayerMemoryDelta> memory,
                             List<akyrion.thepredict.domain.learn.ValueStatDelta> values,
                             List<akyrion.thepredict.domain.distill.ChoiceStatDelta> choices,
                             List<akyrion.thepredict.domain.distill.AgreementStatDelta> agreements) {
    /**
     * Copies the lists.
     *
     * @throws NullPointerException if a list is {@code null}
     * @throws IllegalArgumentException if the id is not positive
     */
    public AggregateBatch {
        outcomes = List.copyOf(outcomes);
        decisions = List.copyOf(decisions);
        cache = List.copyOf(cache);
        difficulty = List.copyOf(difficulty);
        calibration = List.copyOf(calibration);
        patterns = List.copyOf(patterns);
        memory = List.copyOf(memory);
        values = List.copyOf(values);
        choices = List.copyOf(choices);
        agreements = List.copyOf(agreements);
        if (batchId < 1) {
            throw new IllegalArgumentException("batchId must be >= 1, was " + batchId);
        }
    }

    /**
     * A batch from before distillation rows (rule W5). The choice and agreement lists are empty.
     *
     * @param batchId identity of the batch
     * @param outcomes outcome rows
     * @param decisions daily decision rows
     * @param cache daily cache rows
     * @param difficulty difficulty rows
     * @param calibration calibration bins
     * @param patterns pattern-stat rows
     * @param memory player-memory rows
     * @param values learned-value rows
     */
    public AggregateBatch(long batchId, List<OutcomeStatDelta> outcomes, List<DecisionDailyDelta> decisions,
                          List<AnswerCacheDelta> cache, List<DifficultyStatDelta> difficulty,
                          List<CalibrationBinDelta> calibration, List<PatternStatDelta> patterns,
                          List<PlayerMemoryDelta> memory,
                          List<akyrion.thepredict.domain.learn.ValueStatDelta> values) {
        this(batchId, outcomes, decisions, cache, difficulty, calibration, patterns, memory, values, List.of(),
                List.of());
    }

    /**
     * A batch from before player-memory rows (rule W5). The memory list is empty.
     *
     * @param batchId identity of the batch
     * @param outcomes outcome rows
     * @param decisions daily decision rows
     * @param cache daily cache rows
     * @param difficulty difficulty rows
     * @param calibration calibration bins
     * @param patterns pattern-stat rows
     * @param memory player-memory rows
     */
    public AggregateBatch(long batchId, List<OutcomeStatDelta> outcomes, List<DecisionDailyDelta> decisions,
                          List<AnswerCacheDelta> cache, List<DifficultyStatDelta> difficulty,
                          List<CalibrationBinDelta> calibration, List<PatternStatDelta> patterns,
                          List<PlayerMemoryDelta> memory) {
        this(batchId, outcomes, decisions, cache, difficulty, calibration, patterns, memory, List.of());
    }

    /**
     * A batch from before learned-value rows (rule W5). The value list is empty.
     *
     * @param batchId identity of the batch
     * @param outcomes outcome rows
     * @param decisions daily decision rows
     * @param cache daily cache rows
     * @param difficulty difficulty rows
     * @param calibration calibration bins
     * @param patterns pattern-stat rows
     */
    public AggregateBatch(long batchId, List<OutcomeStatDelta> outcomes, List<DecisionDailyDelta> decisions,
                          List<AnswerCacheDelta> cache, List<DifficultyStatDelta> difficulty,
                          List<CalibrationBinDelta> calibration, List<PatternStatDelta> patterns) {
        this(batchId, outcomes, decisions, cache, difficulty, calibration, patterns, List.of(), List.of());
    }

    /**
     * A batch from before player memory (rule W5). The memory list is empty.
     *
     * @param batchId identity of the batch
     * @param outcomes outcome rows
     * @param decisions daily decision rows
     * @param cache daily cache rows
     * @param difficulty difficulty rows
     * @param calibration calibration bins
     */
    public AggregateBatch(long batchId, List<OutcomeStatDelta> outcomes, List<DecisionDailyDelta> decisions,
                          List<AnswerCacheDelta> cache, List<DifficultyStatDelta> difficulty,
                          List<CalibrationBinDelta> calibration) {
        this(batchId, outcomes, decisions, cache, difficulty, calibration, List.of());
    }

    /**
     * A batch of M21: no difficulty and no calibration rows.
     *
     * @param batchId identity of the batch
     * @param outcomes outcome rows
     * @param decisions daily decision rows
     * @param cache daily cache rows
     */
    public AggregateBatch(long batchId, List<OutcomeStatDelta> outcomes, List<DecisionDailyDelta> decisions,
                          List<AnswerCacheDelta> cache) {
        this(batchId, outcomes, decisions, cache, List.of(), List.of());
    }

    /**
     * Whether the batch carries nothing.
     *
     * @return {@code true} when every list is empty
     */
    public boolean empty() {
        return outcomes.isEmpty() && decisions.isEmpty() && cache.isEmpty() && difficulty.isEmpty()
                && calibration.isEmpty() && patterns.isEmpty() && memory.isEmpty() && values.isEmpty()
                && choices.isEmpty() && agreements.isEmpty();
    }
}
