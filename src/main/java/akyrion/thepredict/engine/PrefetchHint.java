package akyrion.thepredict.engine;

import akyrion.thepredict.domain.HintKind;

import java.util.Objects;
import java.util.UUID;

/**
 * One prefetch hint (M27.4).
 *
 * @param subject the player
 * @param kind why it was offered
 */
public record PrefetchHint(UUID subject, HintKind kind) {
    /**
     * Validates the hint.
     */
    public PrefetchHint {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(kind, "kind");
    }
}
