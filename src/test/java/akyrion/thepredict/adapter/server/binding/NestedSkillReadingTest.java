package akyrion.thepredict.adapter.server.binding;

import akyrion.thepredict.adapter.server.binding.NativeSkillView.Line;
import akyrion.thepredict.adapter.server.binding.NativeSkillView.Targeter;
import akyrion.thepredict.domain.DisplacementKind;
import akyrion.thepredict.domain.DisplacementOrigin;
import akyrion.thepredict.domain.HitShape;
import akyrion.thepredict.domain.NativeSkillFacts;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A12 for skills the reader does not follow (SA-07): a sub-skill, a projectile or an aura may hit and push, so the
 * displacement is the assumed knockback and the skill counts as able to deal damage.
 */
class NestedSkillReadingTest {
    @Test
    void aSubSkillCallIsAssumedToKnockBackAndToDealDamage() {
        var reading = read("Slam", List.of(new Line("skill", "skill{s=Slam_Hit} @target", false, Targeter.OTHER, false,
                true)));
        assertEquals(DisplacementKind.KNOCKBACK, reading.facts().targetDisplacement());
        assertEquals(DisplacementOrigin.ASSUMED, reading.facts().displacementOrigin());
        assertTrue(reading.facts().hasDamagingMechanic());
        assertEquals(HitShape.SINGLE_TARGET, reading.facts().hitShape());
        assertEquals(List.of("skill 'Slam': it runs nested skills, projectiles or auras that ThePredict does not"
                + " follow; it may deal damage and a knockback is assumed"), reading.notes());
    }

    @Test
    void aProjectileLineGivesTheProjectileShapeAndTheAssumedKnockback() {
        var reading = read("Bolt", List.of(new Line("projectile", "projectile{onHit=Bolt_Hit;v=8} @target", false,
                Targeter.OTHER, false, true)));
        assertEquals(HitShape.PROJECTILE, reading.facts().hitShape());
        assertEquals(DisplacementKind.KNOCKBACK, reading.facts().targetDisplacement());
        assertEquals(DisplacementOrigin.ASSUMED, reading.facts().displacementOrigin());
        assertTrue(reading.facts().hasDamagingMechanic());
    }

    @Test
    void aReadSkillWithoutNestedLinesKeepsItsReadDisplacement() {
        var heal = read("Heal", List.of(new Line("heal", "heal{a=8} @self", false, Targeter.OTHER, false)));
        assertEquals(DisplacementKind.NONE, heal.facts().targetDisplacement());
        assertEquals(DisplacementOrigin.READ, heal.facts().displacementOrigin());
        assertFalse(heal.facts().hasDamagingMechanic());
        assertTrue(heal.notes().isEmpty());
        var braked = read("Tap", List.of(new Line("damage", "damage{a=2;pkb=true} @target", true, Targeter.OTHER,
                false)));
        assertEquals(DisplacementKind.NONE, braked.facts().targetDisplacement());
        assertEquals(DisplacementOrigin.READ, braked.facts().displacementOrigin());
    }

    private static NativeSkillReader.Reading read(String id, List<Line> mechanics) {
        return NativeSkillReader.read(id, new View(mechanics));
    }

    private record View(List<Line> mechanics) implements NativeSkillView {
        @Override
        public boolean cooldownDeclared() {
            return false;
        }

        @Override
        public OptionalDouble staticCooldownSeconds() {
            return OptionalDouble.empty();
        }

        @Override
        public int charges() {
            return 1;
        }

        @Override
        public String cooldownMode() {
            return NativeSkillFacts.SEQUENTIAL;
        }

        @Override
        public List<Line> conditions() {
            return List.of();
        }

        @Override
        public List<Line> targetConditions() {
            return List.of();
        }

        @Override
        public OptionalInt ticks(String text) {
            return LiveNativeSkill.staticTicks(text);
        }
    }
}
