package akyrion.thepredict.engine.duel;

import akyrion.thepredict.domain.GroupPlan;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Publishes the measured skirmish cells (M25.12). A cell outside its band is {@code no cumple} and the defaults stay.
 */
class M25SkirmishTest {
    private static final SkirmishSimulator.Fight THREE = new SkirmishSimulator.Fight("3v1", 3, 1, false,
            SkirmishSimulator.TICKS);
    private static final SkirmishSimulator.Fight FIVE = new SkirmishSimulator.Fight("5v1", 5, 1, false,
            SkirmishSimulator.TICKS);
    private static final SkirmishSimulator.Fight BOSS = new SkirmishSimulator.Fight("jefe+4v1", 5, 1, true,
            SkirmishSimulator.TICKS);
    private static final SkirmishSimulator.Fight MIRROR = new SkirmishSimulator.Fight("3v3", 3, 3, false,
            SkirmishSimulator.TICKS);
    private static final SkirmishSimulator.Fight EIGHT = new SkirmishSimulator.Fight("8v2", 8, 2, false,
            SkirmishSimulator.TICKS);
    private static final SkirmishSimulator.Fight COST = new SkirmishSimulator.Fight("16v4", 16, 4, false, 80);

    @Test
    void theHarnessIsTheDuelAndPublishesEveryCell() throws Exception {
        assertEquals(1200, SkirmishSimulator.TICKS);
        assertEquals(1500, SkirmishSimulator.HORIZON);
        assertRivalRules();
        var offA = SkirmishSimulator.run(FIVE, SkirmishSimulator.Arm.OFF, 1);
        var offB = SkirmishSimulator.run(FIVE, SkirmishSimulator.Arm.OFF, 1);
        assertEquals(offA.transcript(), offB.transcript());
        assertTrue(offA.impacts() > 0, "off produced no impacts");
        var fairSeeds = new SkirmishSimulator.Measured[4];
        var offSeeds = new SkirmishSimulator.Measured[4];
        for (var seed = 1; seed <= 4; seed++) {
            fairSeeds[seed - 1] = SkirmishSimulator.run(FIVE, SkirmishSimulator.Arm.FAIR, seed);
            offSeeds[seed - 1] = SkirmishSimulator.run(FIVE, SkirmishSimulator.Arm.OFF, seed);
            assertEquals(0, fairSeeds[seed - 1].fairnessViolations());
            assertTrue(fairSeeds[seed - 1].denials() > 0);
        }
        var reflected = SkirmishSimulator.run(MIRROR, SkirmishSimulator.Arm.FAIR, 3, true);
        var straight = SkirmishSimulator.run(MIRROR, SkirmishSimulator.Arm.FAIR, 3, false);
        assertEquals(straight.transcript(), reflected.transcript());
        assertEquals(straight.impacts(), reflected.impacts());
        var five = SkirmishSimulator.oracle(FIVE, 1);
        var pair = SkirmishSimulator.oracle(new SkirmishSimulator.Fight("2v1", 2, 1, false, 40), 1);
        assertNotEquals(GroupPlan.SPLIT_TARGETS, five);
        assertNotEquals(GroupPlan.SURROUND_TARGET, pair);
        assertNotEquals(GroupPlan.SPLIT_TARGETS, pair);

        var coordinate = SkirmishSimulator.run(FIVE, SkirmishSimulator.Arm.COORDINATE, 1);
        var full = SkirmishSimulator.run(FIVE, SkirmishSimulator.Arm.FULL, 1);
        var boss = SkirmishSimulator.run(BOSS, SkirmishSimulator.Arm.COORDINATE, 2);
        var three = SkirmishSimulator.run(THREE, SkirmishSimulator.Arm.FAIR, 1);
        var eight = SkirmishSimulator.run(EIGHT, SkirmishSimulator.Arm.FAIR, 1);
        var cost = SkirmishSimulator.run(COST, SkirmishSimulator.Arm.FAIR, 1);
        var fair = fairSeeds[0];
        var off = offSeeds[0];
        var triples = 0;
        var p99 = 0;
        var fairImpacts = 0;
        var offImpacts = 0;
        var secondHand = 0;
        var conflicts = 0;
        for (var seed = 0; seed < 4; seed++) {
            triples += fairSeeds[seed].triples();
            p99 = Math.max(p99, fairSeeds[seed].impactsPerSecondP99());
            fairImpacts += fairSeeds[seed].impacts();
            offImpacts += offSeeds[seed].impacts();
            secondHand += fairSeeds[seed].secondHand();
            conflicts += fairSeeds[seed].answerConflict();
        }
        var deaths = SkirmishSimulator.playerDeaths();
        assertEquals(120, deaths.length);
        var inside = 0;
        var low = deaths[0];
        var high = deaths[0];
        for (var death : deaths) {
            assertTrue(death >= -1 && death <= SkirmishSimulator.HORIZON, "death " + death);
            low = Math.min(low, death);
            high = Math.max(high, death);
            if (death >= 960 && death <= 1500) {
                inside++;
            }
        }
        assertTrue(low < high, "deaths " + low + ".." + high);
        var fewer = offImpacts == 0 ? 1.0 : (offImpacts - fairImpacts) / (double) offImpacts;
        var general = Math.max(p99, Math.max(three.impactsPerSecondP99(), eight.impactsPerSecondP99()));
        assertTrue(boss.squadWounds() > 0, "player side left the squad unwounded");
        assertTrue(boss.healChances() > 0, "jefe+4 generated no heal occasion");
        var support = boss.healerTicks() == 0 ? 0.0 : boss.supportTicks() / (double) boss.healerTicks();
        var healed = boss.heals() == 0 ? 0.0 : boss.healsOnWounded() / (double) boss.heals();
        var clump = fair.clumpPairTicks() == 0 ? -1.0
                : (fair.clumpPairTicks() - coordinate.clumpPairTicks()) / (double) fair.clumpPairTicks();
        var sector = coordinate.sectorTicks() == 0 ? 0.0 : coordinate.sectorFreeTicks() / (double) coordinate.sectorTicks();
        var lines = new StringBuilder();
        lines.append("# Escaramuza M25\n\n");
        lines.append("Procedimiento del plan: duelo de M24.9, T=1200, horizonte 1500, curación DuelHeal, ")
                .append("avisos por GroupCastGate. Semillas 1 a 4. Fuera de banda: no cumple. ")
                .append("El contraste no cambia.\n\n");
        lines.append(cell("5v1 tríos en ±5 ticks", triples == 0, Integer.toString(triples), "0")).append('\n');
        lines.append(cell("5v1 impactos/s p99", p99 <= 3, Integer.toString(p99), "<= 3")).append('\n');
        lines.append(cell("p99 general", general <= 4, Integer.toString(general), "<= 4")).append('\n');
        lines.append(cell("5v1 impactos/min frente a off", fewer <= 0.10,
                String.format(Locale.ROOT, "%.1f %% menos (fair %d, off %d)", fewer * 100, fairImpacts, offImpacts),
                "<= 10 % menos")).append('\n');
        lines.append(cell("jefe+4 SUPPORT", boss.healerTicks() > 0 && support >= 0.95,
                String.format(Locale.ROOT, "%.1f %%", support * 100), ">= 95 %")).append('\n');
        lines.append(healCell(boss, healed)).append('\n');
        lines.append("jefe+4 curaciones detalle: ").append(boss.healsOnWounded()).append('/').append(boss.heals())
                .append(" ocasiones ").append(boss.healChances()).append(" heridas ").append(boss.squadWounds())
                .append('\n');
        lines.append(cell("jefe+4 cambios de rol", boss.roleChanges() <= 12, Integer.toString(boss.roleChanges()),
                "<= 12 en la pelea; p95 entre peleas no calculado")).append('\n');
        lines.append(cell("5v1 coordinate CLUMPING frente a fair", fair.clumpPairTicks() > 0 && clump >= 0.70,
                fair.clumpPairTicks() == 0 ? "fair sin pares" : String.format(Locale.ROOT, "%.1f %%", clump * 100),
                "reducción >= 70 %")).append('\n');
        lines.append(cell("5v1 sector libre", coordinate.sectorTicks() > 0 && sector >= 0.95,
                String.format(Locale.ROOT, "%.1f %%", sector * 100), ">= 95 %")).append('\n');
        lines.append("SECOND_HAND_ATTACKS=").append(secondHand).append(" ANSWER_CONFLICT_TICKS=").append(conflicts)
                .append(" FAIRNESS_VIOLATIONS=0\n");
        lines.append(cell("5v1 modo jugador 59 % de peleas 11-40 en [0.8, 1.25]·T", inside * 100 >= 59 * 120,
                inside + "/120 rango " + low + ".." + high,
                ">= 59 % con T=60 s, horizonte 1500, banda [960, 1500]")).append('\n');
        lines.append("16v4 coste: ").append(cost.nanos() / 1_000L).append(" µs en ").append(COST.ticks())
                .append(" ticks, impactos ").append(cost.impacts()).append('\n');
        lines.append("5v1 full impactos ").append(full.impacts()).append(" plan ").append(five.option()).append('\n');
        lines.append("5v1 off una semilla impactos ").append(off.impacts()).append('\n');
        var shown = String.format(Locale.ROOT, "%.1f %%", healed * 100);
        assertTrue(lines.toString().contains("jefe+4 curaciones: medido " + shown));
        assertFalse(lines.toString().contains("sin ocasiones"));
        var report = Path.of("build/reports/m25/skirmish.md");
        Files.createDirectories(report.getParent());
        Files.writeString(report, lines.toString());
        assertTrue(lines.toString().contains("T=1200"));
        assertTrue(lines.toString().contains(inside + "/120"));
        assertTrue(Files.exists(report));
    }

    private static void assertRivalRules() {
        var bodies = SkirmishWorld.place(2, 1, false, false);
        var world = new SkirmishWorld(bodies, hit -> {
        });
        var mob = bodies.getFirst();
        var player = bodies.get(2);
        mob.x = 0;
        mob.z = 0;
        mob.health = 20;
        player.x = 2;
        player.z = 0;
        assertEquals(DuelSkill.GUARD, SquadOpponent.of("classic-tuned").offer(world, mob, player, 0, 1, true));
        assertEquals(DuelSkill.STRIKE, SquadOpponent.of("local-best").offer(world, mob, player, 0, 1, true));
        player.x = 0;
        player.z = 0;
        mob.z = 3;
        SquadOpponent.of("kiter-archer").movePlayer(world, player, mob);
        assertTrue(player.z < 0, "kiter steps away");
        player.x = 0;
        player.z = 0;
        SquadOpponent.of("tower").movePlayer(world, player, mob);
        assertEquals(0.0, player.z, 1.0e-9);
        assertEquals(0.0, player.x, 1.0e-9);
    }

    private static String healCell(SkirmishSimulator.Measured boss, double healed) {
        if (boss.healChances() == 0) {
            return "jefe+4 curaciones: medido sin ocasiones; banda >= 80 %; no medido";
        }
        return cell("jefe+4 curaciones", healed >= 0.80, String.format(Locale.ROOT, "%.1f %%", healed * 100),
                ">= 80 %");
    }

    private static String cell(String name, boolean inside, String measured, String band) {
        return name + ": medido " + measured + "; banda " + band + "; " + (inside ? "cumple" : "no cumple");
    }
}
