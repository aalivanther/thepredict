package akyrion.thepredict.engine;

import akyrion.thepredict.domain.CombatFacts;
import akyrion.thepredict.domain.DecisionRequest;
import akyrion.thepredict.domain.DecisionResult;
import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.FailureKind;
import akyrion.thepredict.domain.HealthBand;
import akyrion.thepredict.domain.MobFacts;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.QuestionKey;
import akyrion.thepredict.domain.QuestionSpec;
import akyrion.thepredict.domain.ServiceCheck;
import akyrion.thepredict.domain.ServiceState;
import akyrion.thepredict.domain.Situation;
import akyrion.thepredict.domain.TacticId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DecisionClientTest {
    @Test
    void theUnavailableClientReportsItsCheckAtOnce() {
        var check = new ServiceCheck(ServiceState.UNCONFIGURED, "no key");
        var future = DecisionClient.unavailable(check).check();
        assertTrue(future.isDone());
        assertEquals(check, future.join());
    }

    @Test
    void theUnavailableClientFailsEveryDecisionWithoutIo() {
        var expected = Map.of(ServiceState.UNCONFIGURED, FailureKind.AUTHENTICATION,
                ServiceState.INVALID_CREDENTIALS, FailureKind.AUTHENTICATION,
                ServiceState.INVALID_MODEL, FailureKind.MODEL_UNAVAILABLE,
                ServiceState.INVALID_CONFIGURATION, FailureKind.INVALID_REQUEST,
                ServiceState.PAUSED, FailureKind.SERVICE_ERROR);
        expected.forEach((state, kind) -> {
            var client = DecisionClient.unavailable(new ServiceCheck(state, "detail"));
            var result = client.decide(PreparedFixtures.prepared(request()));
            assertTrue(result.isDone());
            var failed = assertInstanceOf(DecisionResult.Failed.class, result.join());
            assertEquals(kind, failed.kind(), state.name());
            client.close();
            client.close();
        });
    }

    @Test
    void theUnavailableClientPreparesNothingWithTheEffectOfAnEncodingFailure() {
        var client = DecisionClient.unavailable(new ServiceCheck(ServiceState.UNCONFIGURED, "no key"));
        var invalid = assertInstanceOf(Preparation.Invalid.class, client.prepare(request()));
        assertEquals("decision service unavailable: unconfigured", invalid.detail());
    }

    @Test
    void theUnavailableClientNeedsACheck() {
        assertThrows(NullPointerException.class, () -> DecisionClient.unavailable(null));
    }

    private static DecisionRequest request() {
        var mob = new MobKey(EngineFixtures.id(1), 0);
        var facts = new CombatFacts(new EncounterId(1), 0, 0, List.of(new MobFacts(mob, HealthBand.HEALTHY,
                Optional.empty(), Set.of(), Set.of(), Set.of(), Set.of())), List.of(), List.of(), 0);
        var question = new QuestionSpec(QuestionKey.of(0), mob, Situation.NOW,
                List.of(new TacticId("hold_ground"), new TacticId("press_attack")));
        return new DecisionRequest(new EncounterId(1), 0, facts, List.of(question),
                Map.of(mob, EngineFixtures.profile("Guardian")));
    }
}
