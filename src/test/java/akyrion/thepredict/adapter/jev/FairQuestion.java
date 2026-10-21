package akyrion.thepredict.adapter.jev;

import akyrion.thepredict.domain.CombatFacts;
import akyrion.thepredict.domain.DecisionRequest;
import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.HealthBand;
import akyrion.thepredict.domain.MobFacts;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.QuestionKey;
import akyrion.thepredict.domain.QuestionSpec;
import akyrion.thepredict.domain.Situation;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TacticalProfile;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The body Jev receives for one question about one mob, from the encoder production uses (M22.7, A10): the same
 * facts with two profiles that differ only in levers, such as a pinned rung, must give the same bytes. It lives with
 * the tests because production builds its bodies from a real capture (JS-15).
 */
public final class FairQuestion {
    private static final MobKey MOB = new MobKey(new UUID(0, 1), 0);
    private static final EncounterId ENCOUNTER = new EncounterId(1);

    private FairQuestion() {
    }

    /**
     * Encodes a request with one mob, no target and one question, as {@code DecisionClient.prepare} would.
     *
     * @param situation situation
     * @param options offered tactics
     * @param profile profile of the mob
     * @return the JSON text of the whole body
     */
    public static String json(Situation situation, List<TacticId> options, TacticalProfile profile) {
        var mob = new MobFacts(MOB, HealthBand.HEALTHY, Optional.empty(), Set.of(), Set.copyOf(options), Set.of(),
                Set.of());
        var facts = new CombatFacts(ENCOUNTER, 1, 0L, List.of(mob), List.of(), List.of(), 1);
        var question = new QuestionSpec(QuestionKey.of(0), MOB, situation, options);
        var request = new DecisionRequest(ENCOUNTER, 1, facts, List.of(question), Map.of(MOB, profile));
        return new RequestEncoder("jev-1.13.0", new SemanticEncoder(12_000)).encode(request);
    }
}
