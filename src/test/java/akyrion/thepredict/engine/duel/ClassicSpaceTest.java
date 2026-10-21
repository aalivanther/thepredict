package akyrion.thepredict.engine.duel;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The legal space of §7.3 (SIM-01, SIM-02): its size per kit, its codes that rebuild every configuration, the place of
 * {@code legal-equal} in it, and MythicMobs files that use only names verified with {@code javap}.
 */
class ClassicSpaceTest {
    private static final Set<String> VERIFIED = Set.of("clear", "meleeAttack", "meleeAttackConditional",
            "fleeConditional", "moveWithinConditional", "predictorcontrol", "targetwithin", "healthpercent",
            "skillOnCooldown", "hasaura", "skill", "aura");
    private static final Pattern NAME = Pattern.compile("(?:^|[\\s-])([a-zA-Z]+)\\{");

    @Test
    void theSpaceOfEachKitHasItsDimensions() {
        assertEquals(5 * 3 * 2 * 3 * 3 * 4 * 2 * 3 * 3 * 2 * 2, new ClassicSpace(Kit.K1).size(),
                "the heal below 70 % equals the one below 50 % of a heal that needs 50 %");
        assertEquals(5 * 3 * 2 * 3 * 4 * 3 * 3 * 2 * 2, new ClassicSpace(Kit.K2).size());
        assertEquals(5 * 3 * 3 * 4 * 3 * 3 * 2 * 2, new ClassicSpace(Kit.K3).size());
        assertEquals(5 * 3 * 4 * 3 * 3 * 3 * 2 * 2, new ClassicSpace(Kit.K4).size());
    }

    @Test
    void everyCodeRebuildsItsConfigurationAndNoTwoAreEqual() {
        for (var kit : new Kit[] {Kit.K1, Kit.K2, Kit.K3, Kit.K4}) {
            var space = new ClassicSpace(kit);
            var codes = new HashSet<String>();
            for (var index = 0; index < space.size(); index += Math.max(1, space.size() / 997)) {
                var config = space.config(index);
                assertEquals(config.code(), ClassicSpace.parse(kit, "x", config.code()).code());
                assertTrue(codes.add(config.code()), "duplicate at " + index);
                assertEquals(index, space.index(space.digits(index)));
            }
        }
    }

    @Test
    void legalEqualIsAPointOfTheSpace() {
        var movement = DuelSimulator.packs().get("DuelRed").movement();
        for (var kit : new Kit[] {Kit.K1, Kit.K2, Kit.K3, Kit.K4}) {
            var equal = ClassicSpace.legalEqual(kit, movement);
            assertTrue(new ClassicSpace(kit).indexOf(equal).isPresent(), kit.id());
        }
    }

    @Test
    void theMythicFilesUseOnlyVerifiedNames() {
        var space = new ClassicSpace(Kit.K1);
        for (var index = 0; index < space.size(); index += 4_801) {
            var yaml = space.config(index).toMythicYaml();
            var matcher = NAME.matcher(yaml);
            while (matcher.find()) {
                assertTrue(VERIFIED.contains(matcher.group(1)), matcher.group(1) + " in " + index);
            }
            assertTrue(yaml.contains("~onTimer:") || yaml.contains("~onDamaged"));
        }
    }
}
