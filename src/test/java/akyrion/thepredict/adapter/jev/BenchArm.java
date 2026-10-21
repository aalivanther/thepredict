package akyrion.thepredict.adapter.jev;

import akyrion.thepredict.domain.DecisionRequest;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.QuestionDetail;
import akyrion.thepredict.engine.Preparation;
import akyrion.thepredict.engine.PreparedRequest;
import com.google.gson.JsonObject;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * The arms of the re-bench of M16.10, all on the same bank and model: {@code A} is the control (the text criterion of
 * M15.7 and the text instruction of 0.0.1, without deduplication, over the state of M16.4, which has one version only);
 * {@code B} the default {@code compact}; {@code C} {@code standard}; {@code D} {@code full}; {@code E} {@code full} with
 * one fit check per {@code NOW} option. Every arm sees the same options of each scenario, so the labels compare.
 */
enum BenchArm {
    /** Control: the questions of M15.7 as they were sent before M16. */
    A(QuestionDetail.STANDARD, false, true),
    /** The default detail. */
    B(QuestionDetail.COMPACT, false, false),
    /** Objects without what fits now. */
    C(QuestionDetail.STANDARD, false, false),
    /** Objects with what fits now and the focus. */
    D(QuestionDetail.FULL, false, false),
    /** {@code D} plus the fit checks. */
    E(QuestionDetail.FULL, true, false);

    private final QuestionDetail detail;
    private final boolean fitChecks;
    private final boolean control;

    BenchArm(QuestionDetail detail, boolean fitChecks, boolean control) {
        this.detail = detail;
        this.fitChecks = fitChecks;
        this.control = control;
    }

    /** Parses {@code -Parms=A,B,C,D,E}. */
    static Set<BenchArm> parse(String arms) {
        var parsed = new LinkedHashSet<BenchArm>();
        for (var arm : arms.split(",")) {
            parsed.add(BenchArm.valueOf(arm.trim().toUpperCase(Locale.ROOT)));
        }
        if (parsed.isEmpty()) {
            throw new IllegalArgumentException("no arm in " + arms);
        }
        return parsed;
    }

    /** The request of a bank scenario for this arm. */
    DecisionRequest request(BankScenario scenario) {
        return ScenarioBank.request(scenario, detail, fitChecks);
    }

    /** Prepares a scenario as this arm sends it, with the model of the run. */
    PreparedRequest prepare(BankScenario scenario, String model) {
        var request = request(scenario);
        if (control) {
            var body = control(request, model);
            return new PreparedRequest(request, body, Set.of(), Set.of(), body.length());
        }
        return switch (new RequestEncoder(model, new SemanticEncoder(12_000)).prepare(request)) {
            case Preparation.Ready ready -> ready.prepared();
            case Preparation.NothingToAsk ignored -> throw new IllegalStateException(scenario.id() + " has nothing to"
                    + " ask in arm " + this);
            case Preparation.Invalid invalid -> throw new IllegalStateException(scenario.id() + ": " + invalid.detail());
        };
    }

    /** The body of the control arm: state of M16.4, criteria of M15.7, instruction of 0.0.1, no deduplication. */
    private static String control(DecisionRequest request, String model) {
        var asked = new HashSet<MobKey>();
        request.questions().forEach(question -> asked.add(question.mob()));
        var refs = SemanticEncoder.mobRefs(request.facts());
        var questions = new JsonObject();
        for (var question : request.questions()) {
            questions.add(question.key().value(), ControlArmQuestions.build(question.situation(), question.options(),
                    request.profiles().get(question.mob()), refs.get(question.mob())));
        }
        var body = new JsonObject();
        body.addProperty("model", model);
        body.add("state", new SemanticEncoder(12_000).encodeState(request.facts(), request.profiles(), asked));
        body.add("questions", questions);
        return body.toString();
    }
}
