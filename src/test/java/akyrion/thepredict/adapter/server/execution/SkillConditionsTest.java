package akyrion.thepredict.adapter.server.execution;

import io.lumine.mythic.core.skills.SkillCondition;
import io.lumine.mythic.core.skills.conditions.all.ChanceCondition;
import io.lumine.mythic.core.skills.conditions.all.TargetWithinCondition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pre-cast evaluation never rolls a random condition (SA-09): MythicMobs rolls {@code chance} once, in
 * {@code executeIfUsable}, and a second roll here would reject half of the intents that would have passed.
 *
 * <p>{@code SkillCondition}'s constructor asks the running plugin for its skill manager, so the real condition classes
 * are allocated without running a constructor (the serialization path of the JDK): the test needs only their type,
 * which is what {@link SkillConditions#gating} reads besides a {@code null} action and type name.
 */
class SkillConditionsTest {
    @Test
    void aChanceConditionIsNotEvaluatedBeforeTheCastWhileAnotherConditionIs() throws Exception {
        assertFalse(SkillConditions.gating(allocate(ChanceCondition.class)));
        assertTrue(SkillConditions.gating(allocate(TargetWithinCondition.class)),
                "a condition with the default action gates the cast");
    }

    private static SkillCondition allocate(Class<? extends SkillCondition> type) throws Exception {
        var factory = sun.reflect.ReflectionFactory.getReflectionFactory();
        var constructor = factory.newConstructorForSerialization(type, Object.class.getDeclaredConstructor());
        return type.cast(constructor.newInstance());
    }
}
