package akyrion.thepredict.adapter.server.goals;

import akyrion.thepredict.adapter.server.binding.BrainSpecies;
import org.bukkit.entity.Creature;
import org.bukkit.entity.Mob;

import java.util.Optional;

/**
 * Species that cannot receive the generated goal set, checked before any goal changes (M05.6). Pure;
 * package-private helper of {@link GoalOwnership}.
 *
 * <p>Species with a {@code Brain} ignore the goal selector. Mobs that are not a {@link Creature} (slime, magma
 * cube, ghast, phantom, bat) are rejected by MythicMobs' movement goals: {@code float}, {@code randomStroll} and
 * {@code fleeConditional} are valid only when {@code AbstractEntity.isCreature()} is true, and
 * {@code addPathfinderGoals} skips an invalid line (javap), so the set could never be complete.
 */
final class SpeciesSupport {
    /** Reason of {@code Unsupported} for species with a {@code Brain}. */
    static final String BRAIN_SPECIES = "brain species";

    /** Reason of {@code Unsupported} for mobs that are not a {@link Creature}. */
    static final String NOT_A_CREATURE = "species without MythicMobs movement goals";

    private SpeciesSupport() {
    }

    /**
     * Tells why a mob cannot receive the generated set.
     *
     * @param mob live entity
     * @return the reason, or empty when the species is supported
     */
    static Optional<String> unsupportedReason(Mob mob) {
        if (BrainSpecies.contains(mob.getType())) {
            return Optional.of(BRAIN_SPECIES);
        }
        if (!(mob instanceof Creature)) {
            return Optional.of(NOT_A_CREATURE);
        }
        return Optional.empty();
    }
}
