package akyrion.thepredict.adapter.server.context;

import akyrion.thepredict.domain.ParticipantRules;

import java.util.Objects;

/**
 * The global participant settings the root translates from {@code config.yml} for the adapter (rules D10, M17.2):
 * the global rules every profile starts from, whether profiles may add entity selectors, and the cap of non-target
 * candidates per member. Immutable.
 *
 * @param global global game modes, allowed and denied selectors and {@code perceiveNonTargets}
 * @param profilesMayAllow whether the {@code Allow} of a profile adds to the global selectors
 * @param maxCandidatesPerMember most non-target entities kept per member and capture, 1 to 16
 */
public record ParticipantDefaults(ParticipantRules global, boolean profilesMayAllow, int maxCandidatesPerMember) {
    /**
     * The settings of 0.0.1: players in {@code SURVIVAL} or {@code ADVENTURE}, no other entity, and the default cap of
     * candidates, all taken from {@link ParticipantRules} (rule C13).
     */
    public static final ParticipantDefaults STANDARD = new ParticipantDefaults(ParticipantRules.DEFAULT,
            ParticipantRules.DEFAULT_PROFILES_MAY_ALLOW, ParticipantRules.DEFAULT_MAX_CANDIDATES_PER_MEMBER);

    /**
     * Validates the settings.
     *
     * @throws NullPointerException if {@code global} is {@code null}
     * @throws IllegalArgumentException if {@code maxCandidatesPerMember} is outside
     *                                  {@link ParticipantRules#MIN_CANDIDATES_PER_MEMBER} to
     *                                  {@link ParticipantRules#MAX_CANDIDATES_PER_MEMBER}
     */
    public ParticipantDefaults {
        Objects.requireNonNull(global, "global");
        if (maxCandidatesPerMember < ParticipantRules.MIN_CANDIDATES_PER_MEMBER
                || maxCandidatesPerMember > ParticipantRules.MAX_CANDIDATES_PER_MEMBER) {
            throw new IllegalArgumentException("maxCandidatesPerMember must be between "
                    + ParticipantRules.MIN_CANDIDATES_PER_MEMBER + " and " + ParticipantRules.MAX_CANDIDATES_PER_MEMBER
                    + ", was " + maxCandidatesPerMember);
        }
    }
}
