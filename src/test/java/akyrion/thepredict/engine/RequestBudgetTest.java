package akyrion.thepredict.engine;

import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.QuestionKey;
import akyrion.thepredict.domain.QuestionSpec;
import akyrion.thepredict.domain.Situation;
import akyrion.thepredict.domain.TacticId;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;

import static akyrion.thepredict.engine.EngineFixtures.OTHER_WORLD;
import static akyrion.thepredict.engine.EngineFixtures.WORLD;
import static akyrion.thepredict.engine.EngineFixtures.id;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestBudgetTest {
    private static final long SECOND = 1_000_000_000L;

    private static EngineSettings limits(int perMinute, int perWorld, int inFlight, int inFlightPerWorld, int tokens) {
        return new EngineSettings(perMinute, perWorld, inFlight, inFlightPerWorld, tokens, 10, 2, 64, 16, 64, 2000,
                800_000_000L);
    }

    private static EncounterId encounter(int value) {
        return new EncounterId(value);
    }

    private static InFlightRequest request(int encounter, UUID world, long sentAt, int estimated, int questions,
                                           int participants) {
        return request(encounter, world, sentAt, estimated, questions, participants, (int) (estimated * 1.5));
    }

    private static InFlightRequest request(int encounter, UUID world, long sentAt, int estimated, int questions,
                                           int participants, int bodyChars) {
        var byKey = new HashMap<QuestionKey, QuestionSpec>();
        for (var index = 0; index < questions; index++) {
            var key = QuestionKey.of(index);
            byKey.put(key, new QuestionSpec(key, new MobKey(id(index), 0), Situation.NOW,
                    List.of(new TacticId("hold_ground"), new TacticId("press_attack"))));
        }
        return new InFlightRequest(encounter(encounter), world, 1, 1, byKey, Map.of(), participants, sentAt, sentAt,
                sentAt + 2 * SECOND, estimated, bodyChars);
    }

    @Test
    void theKeptSumOfASecondFollowsEveryReservationCorrectionAndExpiry() {
        var budget = new RequestBudget(limits(600, 300, 8, 8, 600_000));
        var now = 10 * SECOND;
        assertTrue(budget.available(WORLD, now));
        assertEquals(0, budget.requestsLastMinute(now));
        assertTrue(budget.tryReserve(WORLD, encounter(1), 100, now));
        assertEquals(1, budget.requestsLastMinute(now), "a reservation in the second already summed is counted");
        assertEquals(100, budget.tokensLastMinute(now));
        assertTrue(budget.tryReserve(WORLD, encounter(2), 50, now + 1));
        assertEquals(150, budget.tokensLastMinute(now + 1));
        budget.complete(request(1, WORLD, now, 100, 1, 1), OptionalInt.of(40), now + 2);
        assertEquals(90, budget.tokensLastMinute(now + 2), "a correction in the same second is counted");
        assertEquals(2, budget.requestsLastMinute(now + 59 * SECOND));
        assertEquals(0, budget.requestsLastMinute(now + 60 * SECOND), "the minute window still expires");
        assertEquals(0, budget.tokensLastMinute(now + 60 * SECOND));
        assertTrue(budget.available(WORLD, now + 60 * SECOND));
    }

    @Test
    void theRequestsInFlightOfTheServerAreLimited() {
        var budget = new RequestBudget(limits(600, 300, 2, 2, 600_000));
        assertTrue(budget.tryReserve(WORLD, encounter(1), 10, 0));
        assertTrue(budget.tryReserve(OTHER_WORLD, encounter(2), 10, 0));
        assertFalse(budget.tryReserve(OTHER_WORLD, encounter(3), 10, 0));
        budget.complete(request(1, WORLD, 0, 10, 1, 1), OptionalInt.empty(), SECOND);
        assertTrue(budget.tryReserve(OTHER_WORLD, encounter(3), 10, SECOND));
        assertEquals(2, budget.inFlight());
    }

    @Test
    void theRequestsInFlightOfOneWorldAreLimited() {
        var budget = new RequestBudget(limits(600, 300, 8, 1, 600_000));
        assertTrue(budget.tryReserve(WORLD, encounter(1), 10, 0));
        assertFalse(budget.tryReserve(WORLD, encounter(2), 10, 0));
        assertTrue(budget.tryReserve(OTHER_WORLD, encounter(3), 10, 0));
    }

    @Test
    void oneEncounterNeverHoldsTwoReservations() {
        var budget = new RequestBudget(limits(600, 300, 8, 4, 600_000));
        assertTrue(budget.tryReserve(WORLD, encounter(1), 10, 0));
        assertFalse(budget.tryReserve(WORLD, encounter(1), 10, 0));
    }

    @Test
    void theServerWindowSlidesAfterSixtySecondsAndAFailedOrLateRequestStillCounts() {
        var budget = new RequestBudget(limits(3, 3, 8, 4, 600_000));
        for (var index = 1; index <= 3; index++) {
            assertTrue(budget.tryReserve(WORLD, encounter(index), 10, index * SECOND));
            budget.complete(request(index, WORLD, index * SECOND, 10, 1, 1), OptionalInt.empty(), index * SECOND);
        }
        assertFalse(budget.tryReserve(WORLD, encounter(4), 10, 60 * SECOND));
        assertEquals(3, budget.requestsLastMinute(60 * SECOND));
        assertTrue(budget.tryReserve(WORLD, encounter(4), 10, 61 * SECOND));
        assertEquals(3, budget.requestsLastMinute(61 * SECOND));
        assertEquals(0, budget.requestsLastMinute(200 * SECOND));
    }

    @Test
    void twoWorldsShareTheServerWindowButEachHasItsOwnShare() {
        var budget = new RequestBudget(limits(3, 2, 8, 4, 600_000));
        assertTrue(budget.tryReserve(WORLD, encounter(1), 10, 0));
        assertTrue(budget.tryReserve(WORLD, encounter(2), 10, 0));
        assertFalse(budget.tryReserve(WORLD, encounter(3), 10, 0));
        assertTrue(budget.tryReserve(OTHER_WORLD, encounter(4), 10, 0));
        assertFalse(budget.tryReserve(OTHER_WORLD, encounter(5), 10, 0));
    }

    @Test
    void theTokenWindowRefusesARequestThatWouldExceedItAndCorrectsTheEstimateWithTheRealCount() {
        var budget = new RequestBudget(limits(600, 300, 8, 4, 1000));
        assertTrue(budget.tryReserve(WORLD, encounter(1), 800, 5 * SECOND));
        assertFalse(budget.tryReserve(WORLD, encounter(2), 300, 5 * SECOND));
        budget.complete(request(1, WORLD, 5 * SECOND, 800, 1, 1), OptionalInt.of(500), 6 * SECOND);
        assertEquals(500, budget.tokensLastMinute(6 * SECOND));
        assertTrue(budget.tryReserve(WORLD, encounter(2), 300, 6 * SECOND));
        assertEquals(800, budget.tokensLastMinute(6 * SECOND));
    }

    @Test
    void theTokensOfARequestAreItsPreparedBodyAtOneAndAHalfCharactersPerTokenRoundedUp() {
        var budget = new RequestBudget(limits(600, 300, 8, 4, 10_000_000));
        assertEquals(1.5, budget.charsPerToken());
        assertEquals(0, budget.tokensFor(0));
        assertEquals(1, budget.tokensFor(1));
        assertEquals(2, budget.tokensFor(3));
        assertEquals(1400, budget.tokensFor(2100));
        assertEquals(1401, budget.tokensFor(2101));
    }

    @Test
    void theCharactersPerTokenLearnFromEachAnswerWithTheMovingAverageOfM16() {
        var budget = new RequestBudget(limits(600, 300, 8, 4, 10_000_000));
        assertTrue(budget.tryReserve(WORLD, encounter(1), 1400, SECOND));
        budget.complete(request(1, WORLD, SECOND, 1400, 1, 1, 2100), OptionalInt.of(1200), SECOND);
        assertEquals(1.5 + 0.2 * (2100.0 / 1200 - 1.5), budget.charsPerToken(), 1e-12);
        for (var index = 2; index <= 60; index++) {
            var tokens = budget.tokensFor(2100);
            assertTrue(budget.tryReserve(WORLD, encounter(index), tokens, index * SECOND));
            budget.complete(request(index, WORLD, index * SECOND, tokens, 1, 1, 2100), OptionalInt.of(1200),
                    index * SECOND);
        }
        assertEquals(1.75, budget.charsPerToken(), 1e-6);
        assertTrue(Math.abs(budget.tokensFor(2100) - 1200) <= 1, String.valueOf(budget.tokensFor(2100)));
    }

    @Test
    void theLearnedCharactersPerTokenStayWithinOnePointThreeAndTwo() {
        var dense = new RequestBudget(limits(600, 300, 8, 4, 10_000_000));
        var sparse = new RequestBudget(limits(600, 300, 8, 4, 10_000_000));
        for (var index = 1; index <= 40; index++) {
            assertTrue(dense.tryReserve(WORLD, encounter(index), 10, index * SECOND));
            dense.complete(request(index, WORLD, index * SECOND, 10, 1, 1, 100), OptionalInt.of(100), index * SECOND);
            assertTrue(sparse.tryReserve(WORLD, encounter(index), 10, index * SECOND));
            sparse.complete(request(index, WORLD, index * SECOND, 10, 1, 1, 1000), OptionalInt.of(100), index * SECOND);
        }
        assertEquals(RequestBudget.MIN_CHARS_PER_TOKEN, dense.charsPerToken());
        assertEquals(RequestBudget.MAX_CHARS_PER_TOKEN, sparse.charsPerToken());
    }

    @Test
    void aFailureOrAnEmptyBodyTeachesNothing() {
        var budget = new RequestBudget(limits(600, 300, 8, 4, 10_000_000));
        assertTrue(budget.tryReserve(WORLD, encounter(1), 10, SECOND));
        budget.complete(request(1, WORLD, SECOND, 10, 1, 1, 100), OptionalInt.empty(), SECOND);
        assertTrue(budget.tryReserve(WORLD, encounter(2), 10, SECOND));
        budget.complete(request(2, WORLD, SECOND, 10, 1, 1, 0), OptionalInt.of(100), SECOND);
        assertTrue(budget.tryReserve(WORLD, encounter(3), 10, SECOND));
        budget.complete(request(3, WORLD, SECOND, 10, 1, 1, 100), OptionalInt.of(0), SECOND);
        assertEquals(1.5, budget.charsPerToken());
    }

    @Test
    void aSecondCompletionOfTheSameRequestChangesNothing() {
        var budget = new RequestBudget(limits(600, 300, 8, 1, 600_000));
        assertTrue(budget.tryReserve(WORLD, encounter(1), 100, 0));
        var request = request(1, WORLD, 0, 100, 1, 1);
        budget.complete(request, OptionalInt.empty(), 0);
        assertTrue(budget.tryReserve(WORLD, encounter(2), 100, 0));
        budget.complete(request, OptionalInt.of(10), 0);
        assertEquals(1, budget.inFlight());
        assertEquals(200, budget.tokensLastMinute(0));
        assertFalse(budget.tryReserve(WORLD, encounter(3), 100, 0));
    }

    @Test
    void aWorldIsForgottenOnceNothingIsInFlightAndItsWindowIsEmpty() {
        var budget = new RequestBudget(limits(600, 300, 8, 4, 600_000));
        assertTrue(budget.tryReserve(WORLD, encounter(1), 10, 0));
        budget.complete(request(1, WORLD, 0, 10, 1, 1), OptionalInt.empty(), SECOND);
        assertTrue(budget.tryReserve(OTHER_WORLD, encounter(2), 10, 59 * SECOND));
        assertEquals(2, budget.trackedWorlds());
        assertTrue(budget.tryReserve(OTHER_WORLD, encounter(3), 10, 60 * SECOND));
        assertEquals(1, budget.trackedWorlds());
        assertEquals(2, budget.requestsLastMinute(60 * SECOND));
    }

    @Test
    void aCompletionAfterItsWorldWasForgottenNeitherThrowsNorCountsTwice() {
        var budget = new RequestBudget(limits(600, 300, 8, 4, 600_000));
        assertTrue(budget.tryReserve(WORLD, encounter(1), 100, 0));
        var request = request(1, WORLD, 0, 100, 1, 1);
        budget.complete(request, OptionalInt.empty(), SECOND);
        assertTrue(budget.tryReserve(OTHER_WORLD, encounter(2), 100, 61 * SECOND));
        assertEquals(1, budget.trackedWorlds());
        budget.complete(request, OptionalInt.of(40), 62 * SECOND);
        assertEquals(1, budget.trackedWorlds());
        assertEquals(1, budget.inFlight());
        assertEquals(1, budget.requestsLastMinute(62 * SECOND));
        assertEquals(100, budget.tokensLastMinute(62 * SECOND));
        budget.complete(request(2, OTHER_WORLD, 61 * SECOND, 100, 1, 1), OptionalInt.empty(), 62 * SECOND);
        assertEquals(0, budget.inFlight());
    }

    @Test
    void theBudgetIsAvailableUntilAWindowOfTheServerTheWorldOrTheTokensIsFull() {
        var budget = new RequestBudget(limits(3, 2, 8, 4, 1000));
        assertTrue(budget.available(WORLD, 0));
        assertTrue(budget.tryReserve(WORLD, encounter(1), 10, 0));
        assertTrue(budget.tryReserve(WORLD, encounter(2), 10, 0));
        assertFalse(budget.available(WORLD, SECOND), "the world share is full");
        assertTrue(budget.available(OTHER_WORLD, SECOND));
        assertTrue(budget.tryReserve(OTHER_WORLD, encounter(3), 10, SECOND));
        assertFalse(budget.available(OTHER_WORLD, SECOND), "the server window is full");
        assertTrue(budget.available(WORLD, 61 * SECOND), "the window slides");
        var tokens = new RequestBudget(limits(600, 300, 8, 4, 1000));
        assertTrue(tokens.tryReserve(WORLD, encounter(1), 1000, 0));
        assertFalse(tokens.available(WORLD, 0), "the token window is full");
    }
}
