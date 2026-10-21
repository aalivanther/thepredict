package akyrion.thepredict.engine;

import akyrion.thepredict.domain.DisplacementKind;
import akyrion.thepredict.domain.MobKey;

import java.util.UUID;

/**
 * Hears an accepted cast whose target moves (M24.10). The adapter attributes the later hazard.
 */
public interface HazardNotes {
    /**
     * A cast whose target displacement is not {@link DisplacementKind#NONE}.
     *
     * @param mob the caster
     * @param target the entity that was moved
     * @param kind how the choice moves the target
     * @param tick the server tick of the cast
     */
    void acceptedCast(MobKey mob, UUID target, DisplacementKind kind, long tick);
}
