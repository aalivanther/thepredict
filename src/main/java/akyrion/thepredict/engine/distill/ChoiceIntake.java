package akyrion.thepredict.engine.distill;

import akyrion.thepredict.domain.AnswerSource;
import akyrion.thepredict.domain.distill.ChoiceStatDelta;
import akyrion.thepredict.domain.distill.ChoiceStatKey;
import akyrion.thepredict.domain.distill.LabelRule;
import akyrion.thepredict.domain.distill.PatternFilter;

import java.util.ArrayList;
import java.util.List;

/**
 * The only producer of {@code m29_choice_stats} rows (M29.1). A Jev answer and a cache hit return nothing.
 */
public final class ChoiceIntake {
    private ChoiceIntake() {
    }

    /**
     * Rows for one answer. Live and cache answers are not labels, even when a teaching snapshot is present.
     *
     * @param source the answer source
     * @param board the capture snapshot
     * @param epochMs wall time supplied by the adapter
     * @return no rows for Jev or cache
     */
    public static List<ChoiceStatDelta> answer(AnswerSource source, TeachingBoard board, long epochMs) {
        if (source == AnswerSource.LIVE || source == AnswerSource.CACHE) {
            return board.jev(true);
        }
        return board.jev(false);
    }

    /**
     * One delta per offered tactic. The label is the value lower bound, not the executed tactic.
     *
     * @param profile profile id
     * @param regime regime key
     * @param context fine context
     * @param model teacher model
     * @param offered offered tactics
     * @param estimates estimates of those tactics
     * @param patternFacts facts on the target
     * @param patternNone whether the effective pattern is none
     * @param epochMs wall time supplied by the adapter
     * @return the rows, empty when there is no label
     */
    public static List<ChoiceStatDelta> rows(String profile, String regime, String context, String model,
                                            List<String> offered, List<LabelRule.Estimate> estimates,
                                            int patternFacts, boolean patternNone, long epochMs) {
        if (!PatternFilter.counts(patternFacts, patternNone) || !ChoiceStatKey.allowed(model)) {
            return List.of();
        }
        var label = LabelRule.label(estimates);
        if (label.isEmpty()) {
            return List.of();
        }
        var week = (int) Math.floorDiv(epochMs, 604_800_000L);
        var rows = new ArrayList<ChoiceStatDelta>(offered.size());
        for (var tactic : offered) {
            var chosen = tactic.equals(label.get()) ? 1 : 0;
            rows.add(new ChoiceStatDelta(new ChoiceStatKey(profile, regime, context, tactic, "window", week, model),
                    1, chosen, epochMs));
        }
        return List.copyOf(rows);
    }
}
