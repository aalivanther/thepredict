package akyrion.thepredict.engine;

import akyrion.thepredict.domain.DecisionResult;
import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.QuestionKey;
import akyrion.thepredict.domain.TacticId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Cache hits that still wait for the live answer (M21.7). Capped at {@code maxInFlightRequests + 8}. A full map
 * counts {@code SHADOW_SKIPPED_FULL} and stores nothing. Every completion removes its entry.
 */
public final class ShadowPending {
    private final int cap;
    private final LinkedHashMap<Key, Entry> waiting = new LinkedHashMap<>();
    private long skippedFull;

    /**
     * Creates the map.
     *
     * @param cap most entries
     */
    public ShadowPending(int cap) {
        this.cap = cap;
    }

    /**
     * Remembers a hit that will be compared.
     *
     * @param encounter the encounter
     * @param sequence the sequence
     * @param tactic best tactic of the cached answer
     * @param confidence confidence of that answer
     * @param ageTicks age of the cached capture
     * @return {@code false} when the map was full
     */
    public boolean put(EncounterId encounter, long sequence, TacticId tactic, double confidence, long ageTicks) {
        if (waiting.size() >= cap) {
            skippedFull++;
            return false;
        }
        waiting.put(new Key(encounter, sequence), new Entry(tactic, confidence, ageTicks));
        return true;
    }

    /**
     * Removes and returns the entry of a finished request.
     *
     * @param encounter the encounter
     * @param sequence the sequence
     * @return the entry
     */
    public Optional<Entry> take(EncounterId encounter, long sequence) {
        return Optional.ofNullable(waiting.remove(new Key(encounter, sequence)));
    }

    /**
     * Times the map refused an entry.
     *
     * @return the count
     */
    public long skippedFull() {
        return skippedFull;
    }

    /**
     * Waiting entries.
     *
     * @return the size
     */
    public int size() {
        return waiting.size();
    }

    /**
     * One pending comparison.
     *
     * @param tactic cached best tactic
     * @param confidence cached confidence
     * @param ageTicks age when it was stored
     */
    public record Entry(TacticId tactic, double confidence, long ageTicks) {
    }

    private record Key(EncounterId encounter, long sequence) {
    }

    /**
     * Best tactic of an answer, if it has a {@code NOW} question.
     *
     * @param answered the answer
     * @param questions questions of the request
     * @return the tactic
     */
    public static Optional<TacticId> best(DecisionResult.Answered answered, Map<QuestionKey, ?> questions) {
        for (var question : questions.keySet()) {
            var answer = answered.answers().get(question);
            if (answer != null) {
                return Optional.of(answer.best());
            }
        }
        return Optional.empty();
    }
}
