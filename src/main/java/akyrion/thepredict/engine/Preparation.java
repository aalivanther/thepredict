package akyrion.thepredict.engine;

import akyrion.thepredict.domain.MobKey;

import java.util.Objects;
import java.util.Set;

/**
 * Result of {@link DecisionClient#prepare}: the request is ready to send, has nothing left to ask, or cannot be encoded
 * (M16.1). Lives in {@code engine} beside the port, because a ready request carries its body as opaque text that
 * {@code domain} must not hold (01 §9). Immutable.
 */
public sealed interface Preparation permits Preparation.Ready, Preparation.NothingToAsk, Preparation.Invalid {
    /**
     * The request can be sent.
     *
     * @param prepared the prepared request
     */
    record Ready(PreparedRequest prepared) implements Preparation {
        /**
         * Validates the preparation.
         *
         * @throws NullPointerException if {@code prepared} is {@code null}
         */
        public Ready {
            Objects.requireNonNull(prepared, "prepared");
        }
    }

    /**
     * Every question fell: each {@code NOW} kept fewer than two distinct options. Nothing is sent or reserved; the
     * mobs are resolved locally by the fallback chain.
     *
     * @param localMobs mobs whose {@code NOW} question is not sent
     */
    record NothingToAsk(Set<MobKey> localMobs) implements Preparation {
        /**
         * Validates and copies the mobs.
         *
         * @throws NullPointerException if the set or an element is {@code null}
         */
        public NothingToAsk {
            localMobs = Set.copyOf(Objects.requireNonNull(localMobs, "localMobs"));
        }
    }

    /**
     * The body does not fit or a question is invalid; nothing is sent or reserved, the failure is counted as
     * {@code INVALID_REQUEST} without any effect on the service tracker, and the mobs of the encounter follow the local
     * fallback.
     *
     * @param detail short explanation without keys, headers or bodies (rule C8)
     */
    record Invalid(String detail) implements Preparation {
        /**
         * Validates the preparation.
         *
         * @throws NullPointerException if {@code detail} is {@code null}
         */
        public Invalid {
            Objects.requireNonNull(detail, "detail");
        }
    }
}
