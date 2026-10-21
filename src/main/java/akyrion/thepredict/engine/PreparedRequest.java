package akyrion.thepredict.engine;

import akyrion.thepredict.domain.DecisionRequest;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.QuestionKey;

import java.util.Objects;
import java.util.Set;

/**
 * A request encoded and deduplicated before it is dispatched (M16.1): what is really sent and its exact body.
 * Immutable, so it may travel to the HTTP threads (rule A4). The body is opaque text for {@code engine}; only its size
 * reaches the budget.
 *
 * @param sent the request with only the questions sent, each with the options sent, and only the fit checks of those
 *        options
 * @param body the exact JSON that is sent
 * @param droppedQuestions questions removed: a {@code NOW} with fewer than two distinct options and its contingencies,
 *        or a contingency with fewer than two
 * @param localMobs mobs whose {@code NOW} question is not sent
 * @param bodyChars characters of {@code body}
 */
public record PreparedRequest(DecisionRequest sent, String body, Set<QuestionKey> droppedQuestions,
                              Set<MobKey> localMobs, int bodyChars) {
    /**
     * Validates and copies the request.
     *
     * @throws NullPointerException if a component or element is {@code null}
     * @throws IllegalArgumentException if {@code bodyChars} is not the length of {@code body}
     */
    public PreparedRequest {
        Objects.requireNonNull(sent, "sent");
        Objects.requireNonNull(body, "body");
        droppedQuestions = Set.copyOf(Objects.requireNonNull(droppedQuestions, "droppedQuestions"));
        localMobs = Set.copyOf(Objects.requireNonNull(localMobs, "localMobs"));
        if (bodyChars != body.length()) {
            throw new IllegalArgumentException("bodyChars must be the length of the body, was " + bodyChars);
        }
    }
}
