package akyrion.thepredict.adapter.storage;

import akyrion.thepredict.domain.TacticalProfile;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The profiles read now, each with the fingerprint of its normalized form (the {@code baseProfileSha256} of M29.8), as
 * the root hands them to the writer after every read of {@code Profiles/}. {@code autoconfig apply} checks a proposal
 * against them before it writes (CAR-03, CAR-04): the profile must exist, its base must be the live fingerprint and its
 * keys must apply to the profile. {@link #UNKNOWN} means the root has not handed any read yet; the reload then stays the
 * only check. Immutable; any thread.
 */
public final class ProfileBases {
    /** No read handed yet. */
    public static final ProfileBases UNKNOWN = new ProfileBases(false, Map.of());

    private final boolean known;
    private final Map<String, Base> bases;

    private ProfileBases(boolean known, Map<String, Base> bases) {
        this.known = known;
        this.bases = Map.copyOf(bases);
    }

    /**
     * One profile read now.
     *
     * @param fingerprint lower-case hex SHA-256 of the normalized profile
     * @param profile the profile as the loader read it, before any overlay
     */
    public record Base(String fingerprint, TacticalProfile profile) {
        /**
         * Validates the parts.
         *
         * @throws NullPointerException if a part is {@code null}
         */
        public Base {
            Objects.requireNonNull(fingerprint, "fingerprint");
            Objects.requireNonNull(profile, "profile");
        }
    }

    /**
     * The bases of one read. A profile without a fingerprint is left out; with two profiles of the same id, the first
     * one stays.
     *
     * @param profiles the profiles the loader read, before any overlay
     * @param fingerprints fingerprint of each profile, by profile id
     * @return the bases, known even when empty
     */
    public static ProfileBases of(Collection<TacticalProfile> profiles, Map<String, String> fingerprints) {
        Objects.requireNonNull(fingerprints, "fingerprints");
        var bases = new LinkedHashMap<String, Base>();
        for (var profile : Objects.requireNonNull(profiles, "profiles")) {
            var fingerprint = fingerprints.get(profile.id());
            if (fingerprint != null) {
                bases.putIfAbsent(profile.id(), new Base(fingerprint, profile));
            }
        }
        return new ProfileBases(true, bases);
    }

    /**
     * Whether the root handed a read.
     *
     * @return {@code false} for {@link #UNKNOWN}
     */
    public boolean known() {
        return known;
    }

    /**
     * The profile read now under an id.
     *
     * @param profileId the profile id
     * @return the base, or empty when no profile read now has that id
     */
    public Optional<Base> base(String profileId) {
        return Optional.ofNullable(bases.get(profileId));
    }
}
