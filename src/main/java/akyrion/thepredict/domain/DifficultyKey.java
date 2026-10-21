package akyrion.thepredict.domain;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Anchor of one slow-loop rung (M25, errata E25-5). The two-argument form is solo with no subject (rule W5).
 * Groups of two or more use {@link DifficultySection#GROUP}. No digest is stored here.
 *
 * @param profile anchor profile id
 * @param context difficulty context text of M22
 * @param subject player the group clock follows, empty for a solo fight
 * @param section solo or group
 */
public record DifficultyKey(String profile, String context, Optional<UUID> subject, DifficultySection section) {
    /**
     * Checks the key.
     *
     * @throws NullPointerException if a component is null
     * @throws IllegalArgumentException if the profile or the context is blank
     */
    public DifficultyKey {
        DomainChecks.requireText(profile, "profile");
        DomainChecks.requireText(context, "context");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(section, "section");
    }

    /**
     * Solo key without a subject (rule W5).
     *
     * @param profile anchor profile id
     * @param context difficulty context text
     */
    public DifficultyKey(String profile, String context) {
        this(profile, context, Optional.empty(), DifficultySection.SOLO);
    }
}
