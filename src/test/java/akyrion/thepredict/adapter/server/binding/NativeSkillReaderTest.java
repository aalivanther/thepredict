package akyrion.thepredict.adapter.server.binding;

import akyrion.thepredict.adapter.server.binding.NativeSkillView.Line;
import akyrion.thepredict.adapter.server.binding.NativeSkillView.Targeter;
import akyrion.thepredict.domain.DisplacementKind;
import akyrion.thepredict.domain.DisplacementOrigin;
import akyrion.thepredict.domain.NativeSkillFacts;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** M19.3: top-level facts through {@link NativeSkillView}, with MythicMobs parsing the lines. */
class NativeSkillReaderTest {
    @Test
    void theExampleSkillsReadTheWindUpReachRadiusAndSightThePlanDescribes() {
        var melee = read("ThePredictGuardianMelee", mechanics(
                cosmetic("effect:particles{p=crit;a=12;hs=0.4;vs=0.4;y=1} @self"),
                delay("5"),
                damage("damage{a=6} @target", Targeter.OTHER)));
        assertEquals(OptionalInt.of(5), melee.facts().windUpTicks());
        assertTrue(melee.facts().hasDamagingMechanic());
        assertTrue(melee.facts().targetWithinBlocks().isEmpty());
        assertTrue(melee.facts().radiusBlocks().isEmpty());
        assertFalse(melee.facts().requiresLineOfSight());

        var ranged = new View();
        ranged.conditions = List.of(condition("targetwithin", "targetwithin{d=16}", true));
        ranged.targetConditions = List.of(condition("lineofsight", "lineofsight", false));
        ranged.mechanics = mechanics(
                cosmetic("effect:particles{p=flame;a=20;hs=0.3;vs=0.6;y=1} @self"),
                delay("10"),
                cosmetic("effect:particles{p=flame;a=10;hs=0.2;vs=0.2;y=1} @target"),
                damage("damage{a=4} @target", Targeter.OTHER));
        var rangedFacts = NativeSkillReader.read("ThePredictGuardianRanged", ranged).facts();
        assertEquals(OptionalInt.of(10), rangedFacts.windUpTicks());
        assertEquals(OptionalDouble.of(16), rangedFacts.targetWithinBlocks());
        assertTrue(rangedFacts.requiresLineOfSight());
        assertTrue(rangedFacts.hasDamagingMechanic());

        var defense = read("ThePredictGuardianDefense", mechanics(
                cosmetic("effect:particles{p=heart;a=6;hs=0.5;vs=0.5;y=1.5} @self"),
                line("heal", "heal{a=8} @self", false, Targeter.OTHER)));
        assertEquals(OptionalInt.of(0), defense.facts().windUpTicks());
        assertFalse(defense.facts().hasDamagingMechanic());

        var slam = read("ThePredictGuardianSlam", mechanics(
                cosmetic("effect:particles{p=cloud;a=30;hs=1.5;vs=0.1;y=0.2} @self"),
                delay("15"),
                damage("damage{a=5} @PlayersInRadius{r=3}", Targeter.RADIUS),
                line("throw", "throw{velocity=6;velocityY=1} @PlayersInRadius{r=3}", false, Targeter.RADIUS)));
        assertEquals(OptionalInt.of(15), slam.facts().windUpTicks());
        assertEquals(OptionalDouble.of(3), slam.facts().radiusBlocks());
        assertTrue(slam.facts().hasDamagingMechanic());
        assertTrue(slam.notes().isEmpty());
    }

    @Test
    void delaysUseTheMythicDurationParserAndStopAtTheFirstMechanicThatIsNotCosmetic() {
        assertEquals(OptionalInt.of(20), LiveNativeSkill.staticTicks("1s"));
        assertEquals(OptionalInt.of(20), LiveNativeSkill.staticTicks("20t"));
        assertTrue(LiveNativeSkill.staticTicks("<caster.var.d>").isEmpty());
        var particles = read("Flash", mechanics(
                line("effect:particles", "effect:particles{delay=5} @self", false, Targeter.OTHER),
                delay("5"),
                damage("damage{a=1} @target", Targeter.OTHER)));
        assertEquals(OptionalInt.of(10), particles.facts().windUpTicks());
        var second = read("Second", mechanics(damage("damage{delay=1s} @target", Targeter.OTHER)));
        assertEquals(OptionalInt.of(20), second.facts().windUpTicks());
        var ticks = read("Ticks", mechanics(damage("damage{delay=20t} @target", Targeter.OTHER)));
        assertEquals(OptionalInt.of(20), ticks.facts().windUpTicks());
        var placeholder = read("Late", mechanics(damage("damage{delay=<caster.var.d>} @target", Targeter.OTHER)));
        assertTrue(placeholder.facts().windUpTicks().isEmpty());
        assertEquals(List.of("skill 'Late': a delay is a placeholder or a range; ThePredict does not know its"
                + " wind-up"), placeholder.notes());
        var repeated = read("Repeat", mechanics(damage("damage{repeat=3;delay=4} @target", Targeter.OTHER)));
        assertEquals(OptionalInt.of(4), repeated.facts().windUpTicks());
        var nested = read("Nest", mechanics(
                delay("5"),
                line("skill", "skill{s=Nested}", false, Targeter.OTHER),
                damage("damage{delay=99} @PlayersInRadius{r=6}", Targeter.RADIUS)));
        assertEquals(OptionalInt.of(5), nested.facts().windUpTicks());
        assertTrue(nested.facts().hasDamagingMechanic());
        assertEquals(OptionalDouble.of(6), nested.facts().radiusBlocks());
    }

    @Test
    void anUnrecognizedTypeEmptiesTheWindUpAndALongDelayIsCappedAtOneHundred() {
        var blank = read("Odd", mechanics(line("", "mystery", false, Targeter.OTHER)));
        assertTrue(blank.facts().windUpTicks().isEmpty());
        assertEquals(List.of("skill 'Odd': a mechanic type is not recognized; ThePredict does not know its wind-up"),
                blank.notes());
        var capped = read("Slow", mechanics(delay("110")));
        assertEquals(OptionalInt.of(100), capped.facts().windUpTicks());
        assertEquals(List.of("skill 'Slow': its delays add 110 ticks; ThePredict caps the wind-up at 100"),
                capped.notes());
    }

    @Test
    void shieldAndInvulnerabilityFlagsFollowDamagingMechanicsOnly() {
        assertTrue(read("Shield", mechanics(damage("damage{ignoreshield=true}", Targeter.OTHER)))
                .facts().damageIgnoresShield());
        assertTrue(read("Short", mechanics(damage("damage{is=true}", Targeter.OTHER))).facts().damageIgnoresShield());
        assertTrue(read("Alias", mechanics(damage("damage{ignore_shield=true}", Targeter.OTHER)))
                .facts().damageIgnoresShield());
        assertFalse(read("Heal", mechanics(line("heal", "heal{ignoreshield=true} @self", false, Targeter.OTHER)))
                .facts().damageIgnoresShield());
        var one = read("One", mechanics(
                damage("damage{ignoreinvulnerability=true}", Targeter.OTHER),
                damage("damage{a=1}", Targeter.OTHER)));
        assertFalse(one.facts().damageIgnoresInvulnerability());
        var both = read("Both", mechanics(
                damage("damage{ignoreinvulnerability=true}", Targeter.OTHER),
                damage("damage{ii=true}", Targeter.OTHER)));
        assertTrue(both.facts().damageIgnoresInvulnerability());
        assertTrue(read("Names", mechanics(
                damage("damage{ignoreinvulnerable=true}", Targeter.OTHER),
                damage("damage{ignore_invulnerability=true}", Targeter.OTHER))).facts().damageIgnoresInvulnerability());
    }

    @Test
    void cooldownChargesAndModeFollowTheMythicDefaults() {
        var none = new View();
        none.mode = "NONE";
        var unreadCooldown = NativeSkillReader.read("Passive", none).facts();
        assertEquals(OptionalDouble.of(1), unreadCooldown.cooldownSeconds());
        var open = new View();
        assertTrue(NativeSkillReader.read("Open", open).facts().cooldownSeconds().isEmpty());
        var counted = new View();
        counted.charges = 0;
        counted.mode = " ";
        var charges = NativeSkillReader.read("Charges", counted).facts();
        assertEquals(1, charges.charges());
        assertEquals(NativeSkillFacts.SEQUENTIAL, charges.cooldownMode());
        var declared = new View();
        declared.declared = true;
        declared.seconds = OptionalDouble.of(3);
        assertEquals(OptionalDouble.of(3), NativeSkillReader.read("Wait", declared).facts().cooldownSeconds());
    }

    @Test
    void targetWithinAndRadiusKeepTheFirstUsableNumberAndCapInSilence() {
        var placeholder = new View();
        placeholder.conditions = List.of(condition("targetwithin", "targetwithin{d=<caster.var.d>}", true));
        var missing = NativeSkillReader.read("Far", placeholder);
        assertTrue(missing.facts().targetWithinBlocks().isEmpty());
        assertEquals(List.of("skill 'Far': targetwithin{d} is a placeholder; ThePredict does not check its reach"),
                missing.notes());
        assertTrue(within("Zero", "targetwithin{d=0}").isEmpty());
        assertTrue(within("Negative", "targetwithin{d=-2}").isEmpty());
        assertEquals(OptionalDouble.of(64), within("Long", "targetwithin{d=80}"));
        var later = new View();
        later.conditions = List.of(
                condition("targetwithin", "targetwithin{d=4}", false),
                condition("targetwithin", "targetwithin{d=9}", true));
        assertEquals(OptionalDouble.of(9), NativeSkillReader.read("Later", later).facts().targetWithinBlocks());
        assertTrue(NativeSkillReader.read("Sight", sight("LineOfSight")).facts().requiresLineOfSight());
        var substituted = read("PlaceholderRadius", mechanics(
                damage("damage{a=1} @PlayersInRadius{r=<caster.var.r>}", Targeter.RADIUS)));
        assertTrue(substituted.facts().radiusBlocks().isEmpty());
        assertEquals(OptionalDouble.of(5), read("DefaultRadius", mechanics(
                damage("damage{a=1} @PlayersInRadius", Targeter.RADIUS))).facts().radiusBlocks());
        assertEquals(OptionalDouble.of(16), read("DefaultCone", mechanics(
                damage("damage{a=1} @EntitiesInCone", Targeter.CONE))).facts().coneRangeBlocks());
        assertEquals(OptionalDouble.of(16), read("Wide", mechanics(
                damage("damage{a=1} @PlayersInRadius{r=20}", Targeter.RADIUS))).facts().radiusBlocks());
        assertEquals(OptionalDouble.of(3), read("First", mechanics(
                damage("damage{a=1} @PlayersInRadius{r=3}", Targeter.RADIUS),
                damage("damage{a=1} @PlayersInRadius{r=8}", Targeter.RADIUS))).facts().radiusBlocks());
        assertEquals(OptionalDouble.of(4), read("AfterCosmetic", mechanics(
                line("effect:particles", "effect:particles @PlayersInRadius{r=2}", false, Targeter.RADIUS),
                damage("damage{a=1} @PlayersInRadius{r=4}", Targeter.RADIUS))).facts().radiusBlocks());
    }

    @Test
    void theRealDamageLineWithPkIsNoneAndAThrowStaysStrongPush() {
        var stopped = read("Stopped", mechanics(damage("damage{a=5;pk=true}", Targeter.OTHER)));
        assertEquals(DisplacementKind.NONE, stopped.facts().targetDisplacement());
        assertEquals(DisplacementOrigin.READ, stopped.facts().displacementOrigin());
        var open = read("Open", mechanics(damage("damage{a=5}", Targeter.OTHER)));
        assertEquals(DisplacementKind.KNOCKBACK, open.facts().targetDisplacement());
        assertEquals(DisplacementOrigin.READ, open.facts().displacementOrigin());
        var thrown = read("Thrown", mechanics(damage("damage{a=5;pk=true}", Targeter.OTHER),
                line("throw", "throw{velocity=6;pk=true}", false, Targeter.OTHER)));
        assertEquals(DisplacementKind.STRONG_PUSH, thrown.facts().targetDisplacement());
        var named = read("Named", mechanics(damage("damage{a=5}", Targeter.OTHER),
                line("preventknockback", "preventknockback", false, Targeter.OTHER)));
        assertEquals(DisplacementKind.NONE, named.facts().targetDisplacement());
    }

    private static OptionalDouble within(String id, String line) {
        var view = new View();
        view.conditions = List.of(condition("targetwithin", line, true));
        return NativeSkillReader.read(id, view).facts().targetWithinBlocks();
    }

    private static View sight(String type) {
        var view = new View();
        view.targetConditions = List.of(condition(type, "lineofsight", false));
        return view;
    }

    private static NativeSkillReader.Reading read(String id, List<Line> mechanics) {
        var view = new View();
        view.mechanics = mechanics;
        return NativeSkillReader.read(id, view);
    }

    private static List<Line> mechanics(Line... lines) {
        return Arrays.asList(lines);
    }

    private static Line delay(String ticks) {
        return line("delay", "delay " + ticks, false, Targeter.OTHER);
    }

    private static Line cosmetic(String text) {
        return line("effect:particles", text, false, Targeter.OTHER);
    }

    private static Line damage(String text, Targeter targeter) {
        return line("damage", text, true, targeter);
    }

    private static Line line(String type, String text, boolean damaging, Targeter targeter) {
        return new Line(type, text, damaging, targeter, false);
    }

    private static Line condition(String type, String text, boolean required) {
        return new Line(type, text, false, Targeter.OTHER, required);
    }

    private static final class View implements NativeSkillView {
        private boolean declared;
        private OptionalDouble seconds = OptionalDouble.empty();
        private int charges = 1;
        private String mode = NativeSkillFacts.SEQUENTIAL;
        private List<Line> conditions = List.of();
        private List<Line> targetConditions = List.of();
        private List<Line> mechanics = List.of();

        @Override
        public boolean cooldownDeclared() {
            return declared;
        }

        @Override
        public OptionalDouble staticCooldownSeconds() {
            return seconds;
        }

        @Override
        public int charges() {
            return charges;
        }

        @Override
        public String cooldownMode() {
            return mode;
        }

        @Override
        public List<Line> conditions() {
            return conditions;
        }

        @Override
        public List<Line> targetConditions() {
            return targetConditions;
        }

        @Override
        public List<Line> mechanics() {
            return mechanics;
        }

        @Override
        public OptionalInt ticks(String text) {
            return LiveNativeSkill.staticTicks(text);
        }
    }
}
