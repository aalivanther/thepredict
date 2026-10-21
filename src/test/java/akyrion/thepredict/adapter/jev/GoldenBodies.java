package akyrion.thepredict.adapter.jev;

import akyrion.thepredict.domain.DecisionRequest;
import akyrion.thepredict.domain.QuestionDetail;
import akyrion.thepredict.engine.Preparation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Real request bodies for the golden references of M17.0 (rules P3, A8): the encoder of the System One client, with
 * the model of the live tools and the default state limit, without any network. The bank of M16.9 is encoded at the
 * three levels of detail, one body per scenario, with the fit checks off as each level sends it by default.
 */
public final class GoldenBodies {
    private GoldenBodies() {
    }

    /**
     * The preparation of the System One client, without the client: encoding and deduplication only.
     *
     * @return the preparation function
     */
    public static Function<DecisionRequest, Preparation> preparer() {
        return new RequestEncoder(LiveJevGate.MODEL, new SemanticEncoder(12_000))::prepare;
    }

    /**
     * The body of every bank scenario at a level of detail, by file name.
     *
     * @param detail level of detail
     * @return file name to body, in the order of the bank
     */
    public static Map<String, String> bank(QuestionDetail detail) {
        var bodies = new LinkedHashMap<String, String>();
        var preparer = preparer();
        for (var scenario : ScenarioBank.scenarios()) {
            var request = ScenarioBank.request(scenario, detail, false);
            var body = switch (preparer.apply(request)) {
                case Preparation.Ready ready -> ready.prepared().body();
                case Preparation.NothingToAsk ignored -> "nothing to ask";
                case Preparation.Invalid invalid -> "invalid: " + invalid.detail();
            };
            bodies.put(ScenarioBank.fileName(scenario), body);
        }
        return bodies;
    }
}
