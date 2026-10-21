package akyrion.thepredict.adapter.server.context;

import akyrion.thepredict.MeasurementReport;
import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.MobKey;
import org.bukkit.Location;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

/**
 * Cost of the capture facts of M15.5, run by {@code ./gradlew m13Measure} and never by the build: the reach of the
 * declared skills and the {@code grouped} fact of the target, alone and inside a whole capture with 16 players, with
 * the lines of sight and paths each capture asks for. Pure doubles; nothing leaves the machine.
 */
public final class ReachMeasurements {
    private static final int RUNS = 20_000;

    private ReachMeasurements() {
    }

    /**
     * Writes the section of M15.5.
     *
     * @param report report to write into
     */
    public static void write(MeasurementReport report) {
        report.section("Capture cost of the reach and grouped facts (M15.5)");
        var rows = new ArrayList<List<String>>();
        var declared = ContextFixtures.declaredProfile();
        var caster = new BoundingBox(0, 64, 0, 0.6, 65.95, 0.6);
        var target = new BoundingBox(8, 64, 0, 8.6, 65.8, 0.6);
        rows.add(List.of("ReadyTactics.outOfReach, 4 declared choices, 3 with reach", "-", "-",
                MeasurementReport.micros(timed(() -> ReadyTactics.outOfReach(declared, caster, target, 64.0))), "0", "0"));
        var positions = new LinkedHashMap<UUID, Location>();
        for (var index = 0; index < 16; index++) {
            positions.put(new UUID(9, index), new Location(null, index * 5.0, 64, 0));
        }
        var last = new UUID(9, 15);
        rows.add(List.of("TargetRelationReader.grouped, 16 participants, none grouped", "-", "-",
                MeasurementReport.micros(timed(() -> TargetRelationReader.grouped(last, positions))), "0", "0"));
        rows.add(capture("Whole capture, 1 mob and 16 players, inferred purposes (0.0.1)", false));
        rows.add(capture("Whole capture, 1 mob and 16 players, declared purposes (M15)", true));
        report.table(List.of("Work", "Mob box reads in one capture", "Out of reach", "us p50 / p95 / p99 / max",
                "Lines of sight per capture", "Paths per capture"), rows);
        report.line("The declared profile reads the boxes of the mob and its target once per capture and the inferred one"
                + " none; neither adds a"
                + " line of sight or a path. Times are of this machine with proxy doubles, not of a server.");
    }

    private static List<String> capture(String name, boolean declared) {
        var doubles = new CaptureDoubles();
        var mob = declared ? doubles.mob(1, 0, 0, CaptureDoubles.declaredBound()) : doubles.mob(1, 0, 0);
        for (var index = 1; index <= 16; index++) {
            doubles.player(index, 8 + index * 0.5, index % 4);
        }
        doubles.source.targets.put(CaptureDoubles.mobId(1), doubles.world.players.getFirst());
        var key = new MobKey(CaptureDoubles.mobId(1), 0);
        var capture = doubles.capture();
        var facts = capture.capture(new EncounterId(1), List.of(key), doubles.context()).orElseThrow();
        var sight = mob.sightChecks;
        var paths = doubles.reads.findPathCalls;
        var boxes = mob.boxReads;
        var nanos = timed(() -> capture.capture(new EncounterId(1), List.of(key), doubles.context()));
        var perCapture = (double) (mob.sightChecks - sight) / (2 * RUNS);
        var pathsPerCapture = (double) (doubles.reads.findPathCalls - paths) / (2 * RUNS);
        return List.of(name, String.valueOf(boxes), facts.mobs().getFirst().choicesOutOfReach().toString(),
                MeasurementReport.micros(nanos), MeasurementReport.number(perCapture, 2),
                MeasurementReport.number(pathsPerCapture, 2));
    }

    private static long[] timed(Runnable work) {
        for (var index = 0; index < RUNS; index++) {
            work.run();
        }
        var nanos = new long[RUNS];
        for (var index = 0; index < RUNS; index++) {
            var started = System.nanoTime();
            work.run();
            nanos[index] = System.nanoTime() - started;
        }
        return nanos;
    }
}
