package akyrion.thepredict.adapter.server.execution;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract of the production {@link CastSource} (SA-01). {@code MobExecutor} and {@code SkillExecutor} cannot be
 * instantiated without MythicMobs (rule P4), so the contract is checked on the class: the live source must answer
 * {@link CastSource#handlesSignal} itself, because the interface default refuses every cue and
 * {@link CombatActionsAdapter#cue} sends a signal only when the source says the mob handles it.
 */
class MythicCastSourceTest {
    @Test
    void theLiveSourceDecidesWhichSignalsTheMobHandlesInsteadOfInheritingTheRefusingDefault() throws Exception {
        var declared = MythicCastSource.class.getDeclaredMethod("handlesSignal", UUID.class, String.class);
        assertEquals(MythicCastSource.class, declared.getDeclaringClass());
        assertTrue(Modifier.isPublic(declared.getModifiers()));
        assertEquals(boolean.class, declared.getReturnType());
    }
}
