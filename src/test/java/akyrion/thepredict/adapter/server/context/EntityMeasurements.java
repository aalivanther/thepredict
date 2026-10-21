package akyrion.thepredict.adapter.server.context;

import akyrion.thepredict.MeasurementReport;
import akyrion.thepredict.adapter.server.context.EntityDoubles.FakeEntity;
import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.ParticipantKind;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.SpawnCategory;
import org.bukkit.entity.Zombie;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Cost of perceiving entities (M17.3), run by {@code ./gradlew m13Measure} and never by the build: one capture of 30
 * members with 2 players and 60 zombies around them, with the default policy of 0.0.1, with zombies admitted as
 * targets only, and with zombies perceived as non-targets. Pure doubles; nothing leaves the machine.
 */
public final class EntityMeasurements {
    private static final int RUNS = 2_000;
    private static final int MOBS = 30;
    private static final int ZOMBIES = 60;

    private EntityMeasurements() {
    }

    /**
     * Writes the section of M17.3.
     *
     * @param report report to write into
     */
    public static void write(MeasurementReport report) {
        report.section("Capture cost with entities around (M17.3)");
        var rows = new ArrayList<List<String>>();
        rows.add(row("30 mobs, 2 players, 60 zombies, default policy (0.0.1)", ParticipantPolicy.DEFAULT));
        rows.add(row("Same, zombies admitted (kind:MONSTER), targets only", EntityDoubles.policy(List.of(
                "kind:MONSTER"), false, 8)));
        rows.add(row("Same, perceiveNonTargets true, maxCandidatesPerMember 8", EntityDoubles.policy(List.of(
                "kind:MONSTER"), true, 8)));
        report.table(List.of("Capture", "Player queries", "Living queries", "Participants (players + entities)",
                "Lines of sight", "us p50 / p95 / p99 / max"), rows);
        report.line("One query per member and capture in every row; with perceiveNonTargets the living query replaces"
                + " the players' query and at most 8 entities per member are read, then 16 participants in all."
                + " Times are of this machine with proxy doubles, not of a server.");
    }

    private static List<String> row(String name, ParticipantPolicy policy) {
        var doubles = new CaptureDoubles();
        var keys = new ArrayList<MobKey>();
        var bound = CaptureDoubles.bound(policy);
        for (var index = 1; index <= MOBS; index++) {
            doubles.mob(index, index % 6, index / 6.0, bound);
            keys.add(new MobKey(CaptureDoubles.mobId(index), 0));
        }
        doubles.player(1, 3, 2);
        doubles.player(2, -3, 2);
        var zombies = new ArrayList<FakeEntity>();
        for (var index = 1; index <= ZOMBIES; index++) {
            var zombie = new FakeEntity(new UUID(0x2AL, index), Zombie.class, EntityType.ZOMBIE, SpawnCategory.MONSTER,
                    doubles.world, (index % 10) - 5.0, index / 10.0);
            zombie.recordReads = false;
            doubles.world.living.add(zombie.entity);
            zombies.add(zombie);
        }
        for (var index = 1; index <= MOBS; index++) {
            doubles.source.targets.put(CaptureDoubles.mobId(index), index % 3 == 0 ? zombies.get(index).entity
                    : doubles.world.players.getFirst());
        }
        var capture = doubles.capture();
        var facts = capture.capture(new EncounterId(1), keys, doubles.context()).orElseThrow();
        var players = doubles.world.calls.stream().filter(call -> call.startsWith("getNearbyPlayers")).count();
        var living = doubles.world.calls.stream().filter(call -> call.startsWith("getNearbyLivingEntities")).count();
        var sights = doubles.world.calls.stream().filter(call -> call.startsWith("hasLineOfSight")).count();
        var entities = facts.participants().stream().filter(p -> p.alias().kind() == ParticipantKind.ENTITY).count();
        var nanos = new long[RUNS];
        for (var index = 0; index < RUNS; index++) {
            var started = System.nanoTime();
            capture.capture(new EncounterId(1), keys, doubles.context());
            nanos[index] = System.nanoTime() - started;
        }
        return List.of(name, String.valueOf(players), String.valueOf(living), (facts.participants().size() - entities)
                + " + " + entities, String.valueOf(sights), MeasurementReport.micros(nanos));
    }
}
