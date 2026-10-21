package akyrion.thepredict.admin;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/** How {@code inspect} and {@code debug} choose their mob. */
public sealed interface MobSelector permits MobSelector.Nearest, MobSelector.ById {
    /** Strict textual UUID, as {@code UUID.toString} writes it in either case. */
    Pattern UUID_FORMAT = Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    /**
     * Reads {@code nearest} or a strictly formatted UUID.
     *
     * @param text argument as typed
     * @return the selector, empty for anything else
     */
    static Optional<MobSelector> parse(String text) {
        Objects.requireNonNull(text, "text");
        if (text.equalsIgnoreCase("nearest")) {
            return Optional.of(new Nearest());
        }
        return UUID_FORMAT.matcher(text).matches() ? Optional.of(new ById(UUID.fromString(text))) : Optional.empty();
    }

    /** The closest bound mob within 16 blocks of the player who runs the command; needs a player sender. */
    record Nearest() implements MobSelector {
    }

    /**
     * A mob chosen by its entity UUID; also works from the console.
     *
     * @param entityId UUID of the mob
     */
    record ById(UUID entityId) implements MobSelector {
        /** Rejects a missing UUID. */
        public ById {
            Objects.requireNonNull(entityId, "entityId");
        }
    }
}
