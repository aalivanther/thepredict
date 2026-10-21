package akyrion.thepredict.engine;

import akyrion.thepredict.domain.MovementProgress;
import akyrion.thepredict.domain.ServiceState;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.domain.ThreatWeights;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.UUID;

import static akyrion.thepredict.engine.EngineFixtures.WORLD;
import static akyrion.thepredict.engine.EngineFixtures.id;
import static akyrion.thepredict.engine.EngineFixtures.player;

/**
 * Local harness of the comparison of M11.6 (script sections 13.1 to 13.4 and 13.7): the same fight with the enriched
 * threat off (all weights zero, the arm of step 13.8) and with the weights of the example. The native threat table is a
 * model: damage taken adds its amount to the damager, the changes of ThePredict are added as they reach the port, and
 * the leader changes only when another player passes it by 10 %, as script step 13.1 expects of MythicMobs. The model
 * is not MythicMobs; the server measures the real table.
 */
final class ThreatAB {
    /** Weights of the example profile of M12.5. */
    static final ThreatWeights EXAMPLE = new ThreatWeights(1.5, 1.0, 0.25, 2.0, 20.0);
    /** The arm without enriched threat, as step 13.8 of the script. */
    static final ThreatWeights OFF = new ThreatWeights(0, 0, 0, 0, 20.0);
    /** Ratio by which a player must pass the leader to take the lead in the model. */
    static final double LEAD_RATIO = 1.1;

    private ThreatAB() {
    }

    /** Scenarios of M11.6. */
    enum Scenario {
        RANGED_HARASS, BLOCKED_HITS, ASSIST, PILLAR, MIXED_ROLES
    }

    /** What one arm did in one scenario. */
    record Result(Scenario scenario, boolean enriched, int ticks, int leaderChanges, int mostChangesInOneWindow,
                  long firstTickAnotherLeads, long portCalls, double pointsAdded, double pointsRemoved, long limited,
                  Map<UUID, Double> finalTable) {
    }

    /** Runs one scenario with the given weights for every mob. */
    static Result run(Scenario scenario, ThreatWeights weights) {
        var fixture = ExecutionFixture.starting(ServiceState.UNCONFIGURED);
        var profile = withThreat(weights);
        fixture.open(1, profile);
        fixture.target(1, 1);
        fixture.reader.bystanders.add(player(2));
        fixture.reader.bystanders.add(player(3));
        var table = new HashMap<UUID, Double>();
        var ticks = scenario == Scenario.MIXED_ROLES ? 1200 : 600;
        start(scenario, fixture, table);
        fixture.run(5);
        var leader = leader(table, player(1));
        var changes = new ArrayList<Long>();
        var firstOther = -1L;
        var seen = fixture.actions.threats.size();
        for (var tick = 0; tick < ticks; tick++) {
            script(scenario, fixture, table, tick);
            fixture.run(1);
            seen = applyChanges(fixture, table, seen);
            var next = leader(table, leader);
            if (!next.equals(leader)) {
                changes.add((long) tick);
                leader = next;
                if (firstOther < 0 && !next.equals(player(1))) {
                    firstOther = tick;
                }
            }
        }
        var metrics = fixture.threat.metrics();
        return new Result(scenario, weights.maxPerWindow() > 0 && weights.rangedHarass() > 0, ticks, changes.size(),
                mostInWindow(changes, 100), firstOther, fixture.actions.threats.size(), metrics.pointsAdded(),
                metrics.pointsRemoved(), metrics.limited(), Map.copyOf(table));
    }

    /** Adds to the model table the changes ThePredict sent to mob 1 since the last call; returns the new mark. */
    private static int applyChanges(ExecutionFixture fixture, Map<UUID, Double> table, int seen) {
        for (var change : fixture.actions.threats.subList(seen, fixture.actions.threats.size())) {
            if (change.mob().entityId().equals(id(1))) {
                table.merge(change.playerId(), change.amount(), (a, b) -> Math.max(0, a + b));
            }
        }
        return fixture.actions.threats.size();
    }

    private static void start(Scenario scenario, ExecutionFixture fixture, Map<UUID, Double> table) {
        switch (scenario) {
            case BLOCKED_HITS -> {
                table.put(player(1), 10.0);
                table.put(player(2), 8.0);
            }
            case PILLAR -> {
                table.put(player(1), 15.0);
                table.put(player(2), 12.0);
                fixture.reader.unreachable.add(id(1));
                fixture.mobs.progress.put(id(1), new ArrayDeque<>(List.of(new MovementProgress(true, true, false,
                        OptionalDouble.of(6.0)))));
            }
            default -> table.put(player(1), 4.0);
        }
    }

    private static void script(Scenario scenario, ExecutionFixture fixture, Map<UUID, Double> table, int tick) {
        switch (scenario) {
            case RANGED_HARASS -> {
                nativeDamage(table, tick % 20 == 0, player(1), 4.0);
                if (tick % 20 == 10) {
                    table.merge(player(2), 5.0, Double::sum);
                    fixture.threat.rangedHit(id(1), player(2), 5.0, false);
                }
            }
            case BLOCKED_HITS -> {
                if (tick % 10 == 0) {
                    fixture.threat.blockedHit(id(1), player(1));
                }
            }
            case ASSIST -> {
                nativeDamage(table, tick % 20 == 0, player(1), 4.0);
                if (tick % 40 == 20) {
                    fixture.threat.assisted(WORLD, player(2), List.of(player(1), player(2)));
                }
            }
            case PILLAR -> {
            }
            case MIXED_ROLES -> mixedRoles(fixture, table, tick);
        }
    }

    /** Step 13.7: a tank that is the target, an archer, a healer and blocked hits of the tank. */
    private static void mixedRoles(ExecutionFixture fixture, Map<UUID, Double> table, int tick) {
        nativeDamage(table, tick % 20 == 0, player(1), 4.0);
        if (tick % 30 == 15) {
            table.merge(player(2), 5.0, Double::sum);
            fixture.threat.rangedHit(id(1), player(2), 5.0, false);
        }
        if (tick % 40 == 5) {
            fixture.threat.assisted(WORLD, player(3), List.of(player(1), player(2), player(3)));
        }
        if (tick % 15 == 0) {
            fixture.threat.blockedHit(id(1), player(1));
        }
    }

    private static void nativeDamage(Map<UUID, Double> table, boolean now, UUID damager, double damage) {
        if (now) {
            table.merge(damager, damage, Double::sum);
        }
    }

    private static UUID leader(Map<UUID, Double> table, UUID current) {
        var leader = current;
        var lead = table.getOrDefault(current, 0.0);
        for (var entry : table.entrySet()) {
            if (!entry.getKey().equals(leader) && entry.getValue() > lead * LEAD_RATIO) {
                leader = entry.getKey();
                lead = entry.getValue();
            }
        }
        return leader;
    }

    private static int mostInWindow(List<Long> ticks, int window) {
        var most = 0;
        var first = 0;
        for (var last = 0; last < ticks.size(); last++) {
            while (ticks.get(last) - ticks.get(first) >= window) {
                first++;
            }
            most = Math.max(most, last - first + 1);
        }
        return most;
    }

    private static TacticalProfile withThreat(ThreatWeights threat) {
        var profile = ExecutionFixture.profile(ExecutionFixture.timing(1), 0);
        return new TacticalProfile(profile.id(), profile.mythicMobId(), profile.objective(), profile.tactics(),
                profile.choices(), profile.contexts(), profile.movement(), profile.health(), threat, profile.baseAi(),
                profile.timing());
    }
}
