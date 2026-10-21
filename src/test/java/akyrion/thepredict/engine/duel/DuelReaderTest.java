package akyrion.thepredict.engine.duel;

import akyrion.thepredict.adapter.server.duel.DuelTestKit;
import akyrion.thepredict.domain.CaptureContext;
import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.ParticipantAlias;
import akyrion.thepredict.domain.TargetRelation;
import akyrion.thepredict.domain.Trend;
import akyrion.thepredict.engine.DecisionTap;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The reader of the simulator reads the trend of the rival as {@code TargetTrend} does on a server (SIM-11): its last
 * step projected on the line to the mob, then corrected by a change of distance between captures. A capture reads it
 * only with the ports of a server; with those of M18.7 it stays steady.
 */
class DuelReaderTest {
    private static DuelWorld.Body body(int index, double x, double z) {
        return new DuelWorld.Body(new UUID(5, index), index == 0 ? DuelWorld.Side.RED : DuelWorld.Side.BLUE, x, z);
    }

    @Test
    void aClosingRivalReadsClosing() {
        var mob = body(0, 0, 0);
        var rival = body(1, 0, 6);
        rival.previousZ = 6.2;
        assertEquals(Trend.CLOSING, DuelReader.trend(mob, rival, DuelPhysics.VANILLA));
        rival.previousZ = 5.8;
        assertEquals(Trend.BACKING_AWAY, DuelReader.trend(mob, rival, DuelPhysics.VANILLA));
        rival.previousZ = 6;
        rival.previousX = 0.2;
        assertEquals(Trend.STRAFING, DuelReader.trend(mob, rival, DuelPhysics.VANILLA));
        rival.previousX = 0;
        rival.previousZ = 6.05;
        assertEquals(Trend.STEADY, DuelReader.trend(mob, rival, DuelPhysics.VANILLA),
                "0.05 × 0.546 is below the 0.05 blocks per tick of a moving entity");
    }

    @Test
    void theCaptureReadsTheTrendOnlyWithTheServerPorts() {
        assertEquals(Trend.CLOSING, captured(TickRules.FAIR).trend());
        assertEquals(Trend.STEADY, captured(TickRules.LEGACY).trend(), "M18.7: always steady");
    }

    private static TargetRelation captured(TickRules rules) {
        var engine = new DuelEngine(new DuelSimulator.Mode.None(), DecisionTap.NONE, rules, Kit.K1);
        var red = new DuelWorld.Body(new UUID(5, 10), DuelWorld.Side.RED, 0, 0, 60);
        var blue = new DuelWorld.Body(new UUID(5, 11), DuelWorld.Side.BLUE, 0, 6, 60);
        red.previousX = 0;
        red.previousZ = 0;
        blue.previousX = 0;
        blue.previousZ = 6.2;
        engine.round(new DuelWorld(red, blue, hit -> {
        }, DuelPhysics.VANILLA, rules, Arena.OPEN18, 0), (body, skill) -> {
        });
        engine.open(red, DuelTestKit.packProfiles().get("DuelRed"), false, -1);
        var context = new CaptureContext((id, kind) -> new ParticipantAlias(kind, 1), Set.of(), Map.of(), List.of(),
                DuelEngine.WORLD, Set.of(), sighting -> {
                });
        var facts = new DuelReader(engine).capture(new EncounterId(1), List.of(new MobKey(red.id, 0)), context)
                .orElseThrow();
        return facts.mobs().getFirst().target().orElseThrow();
    }

    @Test
    void aChangeOfDistanceBetweenCapturesOverridesTheVelocity() {
        assertEquals(Trend.CLOSING, DuelReader.corrected(Trend.STEADY, 4.0, 5.5));
        assertEquals(Trend.BACKING_AWAY, DuelReader.corrected(Trend.CLOSING, 7.0, 5.5));
        assertEquals(Trend.STRAFING, DuelReader.corrected(Trend.STRAFING, 5.0, 5.5));
    }
}
