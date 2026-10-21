package akyrion.thepredict.adapter.jev;

import akyrion.thepredict.MeasurementReport;
import akyrion.thepredict.domain.DecisionRequest;
import akyrion.thepredict.domain.FitCheck;
import akyrion.thepredict.domain.GroupQuestions;
import akyrion.thepredict.domain.QuestionDetail;
import akyrion.thepredict.domain.QuestionKey;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** M19.7: the detail table names the cap, and a cap of four does not grow the production request. */
class JevMeasurementsTest {
    @Test
    void theDetailTableNamesTheCapColumns() {
        assertEquals(List.of("Detail", "Request", "Body chars", "Tokens at 1.5 (guard)", "Tokens at 1.73 (mean)",
                "Prepare us p50/p95/p99/max", "Chars with cap 4", "Tokens at 1.5 with cap 4"),
                JevMeasurements.DETAIL_COLUMNS);
    }

    @Test
    void cappingFourOptionsNeverGrowsTheBody() {
        var encoder = new RequestEncoder("jev-1.13.0", new SemanticEncoder(12_000));
        for (var detail : QuestionDetail.values()) {
            var request = ScenarioFacts.production(JevFixtures.detailed(ScenarioFacts.example(), detail, false));
            var full = JevMeasurements.bodyChars(encoder, request);
            var capped = JevMeasurements.bodyChars(encoder, JevMeasurements.capped(request, 4));
            assertTrue(full > 0, detail.name());
            assertTrue(capped > 0 && capped <= full, detail.name() + " " + capped + " of " + full);
        }
        var production = ScenarioFacts.production(JevFixtures.detailed(ScenarioFacts.example(), QuestionDetail.FULL,
                false));
        var full = JevMeasurements.bodyChars(encoder, production);
        var four = JevMeasurements.bodyChars(encoder, JevMeasurements.capped(production, 4));
        var eight = JevMeasurements.bodyChars(encoder, JevMeasurements.capped(production, 8));
        assertEquals(4_805L, full);
        assertEquals(4_441L, four);
        assertEquals(full, eight);
        assertTrue(four <= full, "cap 4 chars " + four + " of " + full);
        assertEquals(3_204L, RequestSizeGuard.tokens(full));
        assertEquals(2_961L, RequestSizeGuard.tokens(four));
    }

    @Test
    void theGroupColumnMeasuresBothCadencesFromTheSameBody() {
        var chars = JevMeasurements.groupChars(3, GroupQuestions.PLAN_AND_LEADER);
        assertTrue(chars > 0, "refused");
        var report = new MeasurementReport();
        JevMeasurements.groupColumn(report);
        var text = report.text();
        var tokens = Long.toString(RequestSizeGuard.tokens(chars));
        assertTrue(text.contains("REACTIVE"));
        assertTrue(text.contains("STANDARD"));
        assertTrue(text.contains(Long.toString(chars)));
        assertTrue(text.contains(tokens));
        assertTrue(count(text, Long.toString(chars)) >= 2);
    }

    private static int count(String text, String token) {
        var found = 0;
        var from = 0;
        while (true) {
            from = text.indexOf(token, from);
            if (from < 0) {
                return found;
            }
            found++;
            from += token.length();
        }
    }

    @Test
    void aFitCheckPastTheCapIsDroppedAndOneInsideItStays() {
        var production = ScenarioFacts.production(JevFixtures.detailed(ScenarioFacts.example(), QuestionDetail.COMPACT,
                false));
        var now = production.questions().getFirst();
        var kept = new FitCheck(QuestionKey.of(2), now.mob(), now.options().getFirst());
        var dropped = new FitCheck(QuestionKey.of(3), now.mob(), now.options().get(4));
        var withChecks = new DecisionRequest(production.encounter(), production.sequence(), production.facts(),
                production.questions(), production.profiles(), List.of(kept, dropped));
        var capped = JevMeasurements.capped(withChecks, 4);
        assertEquals(List.of(kept), capped.fitChecks());
        assertEquals(4, capped.questions().getFirst().options().size());
        assertEquals(4, capped.questions().get(1).options().size());
    }
}
