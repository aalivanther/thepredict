package akyrion.thepredict.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static akyrion.thepredict.engine.EngineFixtures.WORLD;

/**
 * Cost of {@link EncounterRegistry#assign} with scattered mobs (pending of M06 for M13): each call counts the
 * follow-range predicates it evaluates and the open encounters its target had in the world before the call, so the
 * bound of k·16 predicates can be checked and measured. Mobs stand within the follow range of a random player and
 * every round they switch to a random player, which is the worst case for {@code assign}. Deterministic: positions
 * come from a seeded generator.
 */
final class EncounterScale {
    private final EngineFixtures.Sessions sessions = new EngineFixtures.Sessions();
    private final Map<UUID, double[]> positions = new HashMap<>();
    private final List<UUID> players = new ArrayList<>();
    private final List<MobSession> mobs = new ArrayList<>();
    private final List<Call> calls = new ArrayList<>();
    private final Random random;
    private long nanos;

    private EncounterScale(int mobCount, int playerCount, double sideBlocks, long seed) {
        random = new Random(seed);
        for (var player = 1; player <= playerCount; player++) {
            players.add(EngineFixtures.player(player));
        }
        var centers = new ArrayList<double[]>();
        for (var player = 1; player <= playerCount; player++) {
            centers.add(new double[] {random.nextDouble() * sideBlocks, random.nextDouble() * sideBlocks});
        }
        for (var index = 1; index <= mobCount; index++) {
            var id = EngineFixtures.id(index);
            var center = centers.get(random.nextInt(playerCount));
            var angle = random.nextDouble() * 2 * Math.PI;
            var radius = LoadScenarios.TARGET_RADIUS_BLOCKS * Math.sqrt(random.nextDouble());
            positions.put(id, new double[] {center[0] + radius * Math.cos(angle), center[1] + radius * Math.sin(angle)});
            mobs.add(sessions.registry.open(id, EngineFixtures.profile("Guardian")));
        }
    }

    /**
     * Places every mob against a random player, then changes the target of every mob {@code rounds} times.
     *
     * @return the scale with every call recorded
     */
    static EncounterScale run(int mobCount, int playerCount, double sideBlocks, int rounds, long seed) {
        var scale = new EncounterScale(mobCount, playerCount, sideBlocks, seed);
        for (var round = 0; round <= rounds; round++) {
            for (var mob : scale.mobs) {
                scale.assign(mob, scale.players.get(scale.random.nextInt(scale.players.size())));
            }
        }
        return scale;
    }

    private void assign(MobSession session, UUID target) {
        var candidates = sessions.encounters.ofParticipant(WORLD, target).size();
        var checks = new long[1];
        var self = positions.get(session.key().entityId());
        var start = System.nanoTime();
        sessions.encounters.assign(session, WORLD, target, member -> {
            checks[0]++;
            var other = positions.get(member.entityId());
            return member.equals(session.key()) || Math.hypot(self[0] - other[0], self[1] - other[1])
                    <= LoadFixture.FOLLOW_RANGE_BLOCKS;
        });
        nanos += System.nanoTime() - start;
        calls.add(new Call(candidates, checks[0]));
    }

    List<Call> calls() {
        return List.copyOf(calls);
    }

    long maxChecks() {
        return calls.stream().mapToLong(Call::checks).max().orElse(0);
    }

    double meanChecks() {
        return calls.stream().mapToLong(Call::checks).average().orElse(0);
    }

    int maxCandidates() {
        return calls.stream().mapToInt(Call::candidates).max().orElse(0);
    }

    int encounters() {
        return sessions.encounters.size();
    }

    /** Wall time of all calls in nanoseconds; only the local measurements read it, never a test assertion. */
    long nanos() {
        return nanos;
    }

    /** One call: the open encounters of its target before the call, and the predicates it evaluated. */
    record Call(int candidates, long checks) {
    }
}
