package akyrion.thepredict.engine.duel;

import akyrion.thepredict.adapter.server.duel.DuelTestKit;
import akyrion.thepredict.domain.CastMode;
import akyrion.thepredict.domain.DecisionCadence;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TickLayerMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * The ThePredict profile of each kit is written from the kit and loaded with the real loader (§7.4): for the duel pack
 * it is the pack's profile; the defaults of delivery are the same file without {@code Timing}.
 */
class BenchProfilesTest {
    @TempDir
    Path root;

    @Test
    void theGeneratedPackProfileIsThePack() {
        var generated = BenchProfiles.load(Kit.K1, BenchProfiles.Timing.PACK, root.resolve("pack"));
        assertEquals(DuelTestKit.packProfiles(), generated);
    }

    @Test
    void theDefaultsOfDeliveryOnlyDropTheTiming() {
        var pack = BenchProfiles.load(Kit.K1, BenchProfiles.Timing.PACK, root.resolve("pack")).get("DuelRed");
        var defaults = BenchProfiles.load(Kit.K1, BenchProfiles.Timing.DEFAULTS, root.resolve("defaults"))
                .get("DuelRed");
        assertEquals(TickLayerMode.CASTS, pack.timing().tickLayer());
        assertEquals(DecisionCadence.STANDARD, pack.timing().cadence());
        assertEquals(TickLayerMode.DEFAULT, defaults.timing().tickLayer());
        assertEquals(DecisionCadence.DEFAULT, defaults.timing().cadence());
        assertEquals(CastMode.DEFAULT, defaults.timing().castMode());
        assertEquals(pack.tactics(), defaults.tactics());
        assertEquals(pack.choices(), defaults.choices());
        assertEquals(pack.movement(), defaults.movement());
        assertNotEquals(BenchProfiles.sha256(Kit.K1, BenchProfiles.Timing.PACK),
                BenchProfiles.sha256(Kit.K1, BenchProfiles.Timing.DEFAULTS));
    }

    @Test
    void theHealApproachVariantOnlyMovesTheHealTactic() {
        var defaults = BenchProfiles.load(Kit.K1, BenchProfiles.Timing.DEFAULTS, root.resolve("defaults"))
                .get("DuelRed");
        var variant = BenchProfiles.load(Kit.K1, BenchProfiles.Timing.HEAL_APPROACH, root.resolve("variant"))
                .get("DuelRed");
        var heal = new TacticId("retreat_and_heal");
        assertEquals(Movement.RETREAT, defaults.tactics().get(heal).movement());
        assertEquals(Movement.APPROACH, variant.tactics().get(heal).movement());
        assertEquals(defaults.tactics().get(heal).choice(), variant.tactics().get(heal).choice());
        assertEquals(defaults.tactics().get(heal).priority(), variant.tactics().get(heal).priority());
        for (var id : defaults.tactics().keySet()) {
            if (!id.equals(heal)) {
                assertEquals(defaults.tactics().get(id), variant.tactics().get(id), id.value());
            }
        }
        assertEquals(defaults.tactics().keySet(), variant.tactics().keySet());
        assertEquals(defaults.choices(), variant.choices());
        assertEquals(defaults.movement(), variant.movement());
        assertEquals(defaults.timing(), variant.timing());
    }

    @Test
    void everyKitLoadsWithoutAProblem() {
        for (var kit : Kit.ALL) {
            for (var timing : BenchProfiles.Timing.values()) {
                var profiles = BenchProfiles.load(kit, timing, root.resolve(kit.id() + timing));
                assertEquals(2, profiles.size(), kit.id());
                assertEquals(kit.skills().isEmpty() ? 1 : kit.skills().size(), profiles.get("DuelBlue").choices()
                        .size(), kit.id());
            }
        }
    }
}
