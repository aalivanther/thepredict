package akyrion.thepredict.domain;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * What the mobs perceive of one participant: a player, or any other living entity the participant policy admits
 * (M17.1). Immutable.
 *
 * @param alias alias of the participant in the encounter
 * @param health health band of the participant
 * @param armor armor band of the participant
 * @param cues perceivable conditions of the participant
 * @param threatRank threat rank, {@code 1} is the highest; unique within the encounter
 * @param species key of the entity type without namespace ({@code zombie}, {@code wither_skeleton}), present exactly
 *                when the alias is an {@code entity_N}; never a custom name, a {@code Display} or a MythicMobs ID
 *                (rule I6)
 * @param patterns filtered enemy patterns sent to Jev; empty unless the mode is {@code full} and facts are on
 * @param memory one history phrase, empty unless memory mode is {@code full}
 */
public record ParticipantFacts(ParticipantAlias alias, HealthBand health, ArmorBand armor,
                               Set<TargetCue> cues, int threatRank, Optional<String> species,
                               Set<EnemyPattern> patterns, Optional<MemoryFact> memory) {
    /** Pattern of a species: the key of an entity type without namespace. */
    public static final Pattern SPECIES = Pattern.compile("[a-z0-9_]{1,40}");

    /**
     * Validates the facts and copies the cues.
     *
     * @throws NullPointerException if a component or element is {@code null}
     * @throws IllegalArgumentException if {@code threatRank < 1}, a player has a species, an entity has none, or the
     *                                  species does not match {@link #SPECIES}
     */
    public ParticipantFacts {
        Objects.requireNonNull(alias, "alias");
        Objects.requireNonNull(health, "health");
        Objects.requireNonNull(armor, "armor");
        cues = Set.copyOf(Objects.requireNonNull(cues, "cues"));
        Objects.requireNonNull(species, "species");
        if (threatRank < 1) {
            throw new IllegalArgumentException("threatRank must be >= 1, was " + threatRank);
        }
        if ((alias.kind() == ParticipantKind.ENTITY) != species.isPresent()) {
            throw new IllegalArgumentException("participant " + alias + " must " + (species.isPresent() ? "not " : "")
                    + "have a species");
        }
        species.ifPresent(value -> {
            if (!SPECIES.matcher(value).matches()) {
                throw new IllegalArgumentException("species must match " + SPECIES.pattern() + ", was '" + value + "'");
            }
        });
        patterns = Set.copyOf(Objects.requireNonNull(patterns, "patterns"));
        Objects.requireNonNull(memory, "memory");
        if (patterns.contains(EnemyPattern.FIXED_CADENCE)) {
            throw new IllegalArgumentException("FIXED_CADENCE is not a phrase");
        }
    }

    /**
     * Facts from before history phrases (rule W5). This constructor stores no memory phrase.
     *
     * @param alias alias of the participant
     * @param health health band
     * @param armor armor band
     * @param cues perceivable conditions
     * @param threatRank threat rank
     * @param species species key, present exactly for an entity
     * @param patterns pattern phrases; this constructor stores no memory phrase
     */
    public ParticipantFacts(ParticipantAlias alias, HealthBand health, ArmorBand armor, Set<TargetCue> cues,
                            int threatRank, Optional<String> species, Set<EnemyPattern> patterns) {
        this(alias, health, armor, cues, threatRank, species, patterns, Optional.empty());
    }

    /**
     * Facts from before history phrases (rule W5). The memory phrase is absent.
     *
     * @param alias alias of the participant
     * @param health health band
     * @param armor armor band
     * @param cues perceivable conditions
     * @param threatRank threat rank
     * @param species species key, present exactly for an entity
     */
    public ParticipantFacts(ParticipantAlias alias, HealthBand health, ArmorBand armor, Set<TargetCue> cues,
                            int threatRank, Optional<String> species) {
        this(alias, health, armor, cues, threatRank, species, Set.of());
    }

    /**
     * Creates the facts of a player, without a species. Kept for the tests and doubles that build player facts
     * (rule W5); the capture always passes the species.
     *
     * @param alias alias of a player
     * @param health health band
     * @param armor armor band
     * @param cues perceivable conditions
     * @param threatRank threat rank
     * @throws IllegalArgumentException if the alias is an {@code entity_N} or {@code threatRank < 1}
     */
    public ParticipantFacts(ParticipantAlias alias, HealthBand health, ArmorBand armor, Set<TargetCue> cues,
                            int threatRank) {
        this(alias, health, armor, cues, threatRank, Optional.empty());
    }
}
