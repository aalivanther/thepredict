package akyrion.thepredict.engine;

import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.QuestionKey;
import akyrion.thepredict.domain.QuestionSpec;
import akyrion.thepredict.domain.Situation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

/**
 * The effective window of {@link RequestBudget} (pending of M09 for M13): its 60 buckets of one whole second let a
 * request stop counting between 59 and 60 s after it was sent. This drives the real budget with a controllable time
 * and measures the most requests it admits in any real 60 s window, so the margin against the published account
 * limit can be checked without the account. Each admitted request completes at once, so only the minute windows
 * limit the sends; the time is the parameter, nothing is timed.
 */
final class RequestBudgetWindow {
    private static final long SECOND = 1_000_000_000L;
    private static final long MINUTE = 60 * SECOND;

    private RequestBudgetWindow() {
    }

    /**
     * Sends from the last millisecond of the first second, once every {@code stepNanos}: a greedy sender sends at each
     * step until the budget refuses, a steady sender sends one request per step.
     *
     * @return the send times the budget admitted
     */
    static List<Long> sender(int maxRequestsPerMinute, boolean greedy, long stepNanos, long durationNanos) {
        var budget = new RequestBudget(new EngineSettings(maxRequestsPerMinute, maxRequestsPerMinute, 8, 8, 100_000_000,
                10, 2, 64, 16, 64, 2000, 800_000_000L));
        var admitted = new ArrayList<Long>();
        for (var now = SECOND - 1_000_000L; now < durationNanos; now += stepNanos) {
            var sent = 0;
            while (send(budget, now, admitted) && greedy) {
                if (++sent > maxRequestsPerMinute) {
                    throw new IllegalStateException("the budget never refused the burst");
                }
            }
        }
        return List.copyOf(admitted);
    }

    /** The most admitted sends inside any window {@code [t, t + 60 s)}. */
    static int mostInAnyMinute(List<Long> sends) {
        var most = 0;
        var first = 0;
        for (var last = 0; last < sends.size(); last++) {
            while (sends.get(last) - sends.get(first) >= MINUTE) {
                first++;
            }
            most = Math.max(most, last - first + 1);
        }
        return most;
    }

    private static boolean send(RequestBudget budget, long now, List<Long> admitted) {
        var encounter = new EncounterId(admitted.size() + 1L);
        if (!budget.tryReserve(EngineFixtures.WORLD, encounter, 0, now)) {
            return false;
        }
        admitted.add(now);
        var question = new QuestionSpec(QuestionKey.of(0), new MobKey(EngineFixtures.id(1), 0), Situation.NOW,
                List.of(ExecutionFixture.PRESS, ExecutionFixture.KEEP));
        budget.complete(new InFlightRequest(encounter, EngineFixtures.WORLD, 1, 1, Map.of(question.key(), question),
                Map.of(), 1, now, now, now + SECOND, 0, 0), OptionalInt.empty(), now);
        return true;
    }
}
