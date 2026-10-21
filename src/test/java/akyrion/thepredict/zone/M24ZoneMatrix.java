package akyrion.thepredict.zone;

import akyrion.thepredict.domain.DangerClass;
import akyrion.thepredict.domain.DisplacementKind;
import akyrion.thepredict.domain.Feasibility;
import akyrion.thepredict.domain.PushDanger;
import akyrion.thepredict.domain.PushDecision;
import akyrion.thepredict.domain.PushPolicy;
import akyrion.thepredict.domain.StableRandom;
import akyrion.thepredict.domain.ZoneCaps;
import akyrion.thepredict.domain.ZoneMode;
import akyrion.thepredict.domain.ZoneColumn;
import akyrion.thepredict.domain.ZoneGrid;
import akyrion.thepredict.domain.ZoneLayers;
import akyrion.thepredict.domain.ZonePick;
import akyrion.thepredict.domain.ZoneQueryRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Local E2 on the eight plan terrains. Paired seeds, sides swapped. Not the M18 duel: that world has no terrain.
 * The delivered mode stays observe. No network and no claim that a gate passed unless the numbers say so.
 */
public final class M24ZoneMatrix {
    /** Seeds per cell. Each seed is played on both sides. */
    public static final int SEEDS = 200;
    private static final int WIDTH = 49;
    private static final int STEPS = 48;
    private static final String[] TERRAINS = {
            "flat", "pillars", "lava_moat", "cliff", "tower", "bridge", "bog", "corridor"
    };
    private static final String[] MODES = {"off", "guard", "full"};
    private static final String[] GATED = {"cliff", "lava_moat", "bridge"};

    private M24ZoneMatrix() {
    }

    /**
     * The deterministic report, without the machine time.
     *
     * @return the markdown
     */
    public static String report() {
        return format(cells(), fairness());
    }

    private static List<Cell> cells() {
        var cells = new ArrayList<Cell>();
        for (var terrain : TERRAINS) {
            var grid = grid(terrain);
            for (var mode : MODES) {
                cells.add(cell(grid, terrain, mode));
            }
        }
        return cells;
    }

    private static Cell cell(ZoneGrid grid, String terrain, String mode) {
        var deaths = 0;
        var own = 0;
        var ticks = 0L;
        var survived = 0;
        for (var round = 1; round <= SEEDS; round++) {
            var seed = StableRandom.seed(20261007L, round, salt(terrain));
            var left = play(grid, mode, place(terrain, seed, false));
            var right = play(grid, mode, place(terrain, seed, true));
            deaths += left.deaths + right.deaths;
            own += left.own + right.own;
            ticks += (long) left.ticks + right.ticks;
            survived += left.survived + right.survived;
        }
        return new Cell(terrain, mode, SEEDS * 2, deaths, own, ticks, survived);
    }

    private static Outcome play(ZoneGrid grid, String mode, int[] ends) {
        var guard = !mode.equals("off");
        var runner = new ZoneQueryRunner();
        var x = ends[0];
        var z = ends[1];
        for (var step = 0; step < STEPS; step++) {
            if (hazard(grid, x, z)) {
                return new Outcome(1, 0, step + 1, 0);
            }
            if (!guard) {
                x += Integer.signum(ends[2] - x);
                z += Integer.signum(ends[3] - z);
                continue;
            }
            var move = guardStep(runner, grid, x, z);
            if (move.own) {
                return new Outcome(1, 1, step + 1, 0);
            }
            x = move.x;
            z = move.z;
        }
        return hazard(grid, x, z) ? new Outcome(1, 0, STEPS, 0) : new Outcome(0, 0, STEPS, 1);
    }

    private static Move guardStep(ZoneQueryRunner runner, ZoneGrid grid, int x, int z) {
        runner.nextTick();
        var pick = runner.destination(grid, x, z);
        if (ZonePick.state(pick) != ZonePick.FOUND) {
            return new Move(x, z, false);
        }
        var cx = ZonePick.cell(pick) % WIDTH;
        var cz = ZonePick.cell(pick) / WIDTH;
        if (hazard(grid, cx, cz)) {
            return new Move(cx, cz, true);
        }
        var nx = x + Integer.signum(cx - x);
        var nz = z + Integer.signum(cz - z);
        if (hazard(grid, nx, nz)) {
            return new Move(x, z, false);
        }
        return new Move(nx, nz, false);
    }

    private static boolean hazard(ZoneGrid grid, int x, int z) {
        if (x < 0 || z < 0 || x >= WIDTH || z >= WIDTH) {
            return true;
        }
        var column = grid.column(z * WIDTH + x);
        return ZoneColumn.state(column) == ZoneColumn.VOID || ZoneColumn.flag(column, ZoneColumn.LAVA);
    }

    private static int[] place(String terrain, long seed, boolean swapped) {
        var mobX = 4 + below(seed, 10, 16);
        var mobZ = 4 + below(seed, 11, 16);
        var targetX = 20 + below(seed, 12, 16);
        var targetZ = 20 + below(seed, 13, 16);
        switch (terrain) {
            case "cliff" -> {
                mobX = 2 + below(seed, 1, 6);
                mobZ = 12;
                targetX = 18 + below(seed, 2, 10);
                targetZ = 12;
            }
            case "lava_moat" -> {
                mobX = 10 + below(seed, 3, 5);
                mobZ = 10 + below(seed, 4, 5);
                targetX = 30 + below(seed, 5, 8);
                targetZ = 20 + below(seed, 6, 8);
            }
            case "bridge", "corridor" -> {
                mobX = 4 + below(seed, 7, 8);
                mobZ = 12;
                targetX = 28 + below(seed, 8, 10);
                targetZ = 20 + below(seed, 9, 8);
            }
            default -> {
            }
        }
        if (swapped) {
            return new int[] {targetX, targetZ, mobX, mobZ};
        }
        return new int[] {mobX, mobZ, targetX, targetZ};
    }

    private static int below(long seed, int salt, int bound) {
        return StableRandom.below(StableRandom.draw(seed, 1L, salt), bound);
    }

    private static int salt(String terrain) {
        return switch (terrain) {
            case "flat" -> 1;
            case "pillars" -> 2;
            case "lava_moat" -> 3;
            case "cliff" -> 4;
            case "tower" -> 5;
            case "bridge" -> 6;
            case "bog" -> 7;
            case "corridor" -> 8;
            default -> 9;
        };
    }

    private static ZoneGrid grid(String terrain) {
        var grid = new ZoneGrid(WIDTH, 0, 0, 64, 3);
        for (var z = 0; z < WIDTH; z++) {
            for (var x = 0; x < WIDTH; x++) {
                grid.set(z * WIDTH + x, column(terrain, x, z));
            }
        }
        var cells = WIDTH * WIDTH;
        var hazard = new boolean[cells];
        var edge = new boolean[cells];
        var obstacle = new boolean[cells];
        ZoneLayers.sources(grid.columns(), WIDTH, 3, hazard, edge, obstacle);
        var scratch = new int[cells];
        ZoneLayers.rebuild(hazard, grid.hazardLayer(), WIDTH, scratch);
        ZoneLayers.rebuild(edge, grid.edgeLayer(), WIDTH, scratch);
        ZoneLayers.rebuild(obstacle, grid.clearanceLayer(), WIDTH, scratch);
        grid.ready(1);
        return grid;
    }

    private static long column(String terrain, int x, int z) {
        var floor = ZoneColumn.floor(64, 4);
        return switch (terrain) {
            case "cliff" -> x > 10 ? ZoneColumn.pack(ZoneColumn.VOID, 0, 0, 0, 0L) : floor;
            case "bridge", "corridor" -> Math.abs(z - 12) <= 1 ? floor : ZoneColumn.pack(ZoneColumn.VOID, 0, 0, 0, 0L);
            case "lava_moat" -> moat(x, z, floor);
            case "pillars" -> pillar(x, z) ? ZoneColumn.floor(67, 4) : floor;
            case "tower" -> x >= 20 && x <= 22 && z >= 20 && z <= 22 ? ZoneColumn.floor(67, 4) : floor;
            case "bog" -> (x + z) % 7 == 0 ? ZoneColumn.with(floor, ZoneColumn.SLOW) : floor;
            default -> floor;
        };
    }

    private static long moat(int x, int z, long floor) {
        var dx = x - 12;
        var dz = z - 12;
        if (dx * dx + dz * dz <= 64 || z == 12 || x == 12) {
            return floor;
        }
        return ZoneColumn.with(floor, ZoneColumn.LAVA);
    }

    private static boolean pillar(int x, int z) {
        return near(x, z, 8, 8) || near(x, z, 8, 16) || near(x, z, 16, 8) || near(x, z, 16, 16);
    }

    private static boolean near(int x, int z, int px, int pz) {
        return Math.abs(x - px) <= 1 && Math.abs(z - pz) <= 1;
    }

    private static int fairness() {
        var violations = 0;
        for (var seed = 1; seed <= SEEDS; seed++) {
            var decision = PushPolicy.decide(true, DisplacementKind.KNOCKBACK, PushDanger.LETHAL, DangerClass.MEDIUM,
                    Feasibility.FEASIBLE, 0, ZoneCaps.DEFAULT.pushDeferMaxTicks(), false, ZoneMode.OBSERVE);
            if (decision == PushDecision.ALLOW) {
                violations++;
            }
        }
        return violations;
    }

    private static String format(List<Cell> cells, int violations) {
        var text = new StringBuilder();
        text.append("# E2 local. Matriz de zona (M24)\n\n");
        text.append(SEEDS).append(" semillas pareadas (cada una en los dos lados, ");
        text.append(SEEDS * 2).append(" rondas) en los ocho terrenos del plan. ");
        text.append("En off el mob camina en línea hacia el objetivo. En guard y en full solo acepta el destino de Q4 ");
        text.append("o se queda. Una muerte es pisar vacío o lava. No es el duelo de M18. ");
        text.append("No se regeneraron las doradas de M17, M20 ni M22. No se ejecutó el banco E1, ni E6, ni la sesión 24. ");
        text.append("No hay rayo de cobertura.\n\n");
        text.append("| Terreno | Modo | Rondas | Muertes | Por hora | Destino propio | Supervivencia | Wilson muertes |\n");
        text.append("|---|---|---|---|---|---|---|---|\n");
        for (var cell : cells) {
            text.append("| ").append(cell.terrain).append(" | ").append(cell.mode).append(" | ").append(cell.rounds);
            text.append(" | ").append(cell.deaths).append(" | ");
            text.append(String.format(Locale.ROOT, "%.1f", perHour(cell.deaths, cell.ticks)));
            text.append(" | ").append(cell.own).append(" | ").append(cell.survived).append(" | ");
            text.append(wilson(cell.deaths, cell.rounds)).append(" |\n");
        }
        text.append("\n");
        text.append(gates(cells, violations));
        return text.toString();
    }

    private static String gates(List<Cell> cells, int violations) {
        var text = new StringBuilder("## Puertas\n\n");
        var own = ownDestinations(cells);
        text.append("- Destino propio con guard en cliff, lava_moat y bridge: ").append(own).append(". ");
        text.append("no cumple (el duelo no lo midió; hace falta 0).\n");
        for (var terrain : GATED) {
            text.append("- ").append(terrain).append(": ").append(reduction(cells, terrain)).append("\n");
        }
        text.append("- FAIRNESS_VIOLATIONS: ").append(violations).append(" de ").append(SEEDS);
        text.append(" decisiones letales MEDIUM contra un jugador, sin entropía. ");
        text.append("no cumple (el duelo no lo midió).\n");
        text.append("- Supervivencia de guard frente a off en cada terreno: ");
        text.append("no cumple (el duelo no lo midió).\n");
        text.append("- G1 de M22 por rival: no cumple. El duelo no se volvió a correr; el simulador no tiene terreno.\n");
        text.append("- flat: ").append(flat(cells)).append("\n");
        text.append("- ZONE_FACT_CHANGES_PER_MINUTE en full: 0. Esta matriz no emite hechos. ");
        text.append("no cumple (el duelo no lo midió; el tope es 2).\n");
        text.append("- Activación del default a guard (E7): no cumple. Faltan las sesiones 24.5 y 24.7. ");
        text.append("zone.mode entregado: ").append(ZoneCaps.DEFAULT.mode().name().toLowerCase(Locale.ROOT));
        text.append(". No se pasa a guard.\n");
        return text.toString();
    }

    private static String ownDestinations(List<Cell> cells) {
        var text = new StringBuilder();
        for (var terrain : GATED) {
            if (!text.isEmpty()) {
                text.append(", ");
            }
            text.append(find(cells, terrain, "guard").own);
        }
        return text.toString();
    }

    private static String reduction(List<Cell> cells, String terrain) {
        var off = find(cells, terrain, "off");
        var guard = find(cells, terrain, "guard");
        var offRate = perHour(off.deaths, off.ticks);
        var guardRate = perHour(guard.deaths, guard.ticks);
        var pct = offRate > 0 ? (offRate - guardRate) / offRate * 100.0 : 0;
        return String.format(Locale.ROOT,
                "HAZARD_DEATHS_MOB_PER_HOUR off %.1f, guard %.1f, reducción %.1f %%. no cumple (el duelo no lo midió).",
                offRate, guardRate, pct);
    }

    private static String flat(List<Cell> cells) {
        var off = find(cells, "flat", "off");
        var guard = find(cells, "flat", "guard");
        var full = find(cells, "flat", "full");
        return "muertes off/guard/full " + off.deaths + "/" + guard.deaths + "/" + full.deaths + ". "
                + "Muertes idénticas: no cumple (el duelo no lo midió). "
                + "Supervivencia de full y guard: no cumple (el duelo no lo midió). "
                + "Paridad byte a byte del duelo plano: no medida, no cumple.";
    }

    private static Cell find(List<Cell> cells, String terrain, String mode) {
        for (var cell : cells) {
            if (cell.terrain.equals(terrain) && cell.mode.equals(mode)) {
                return cell;
            }
        }
        throw new IllegalStateException("missing " + terrain + " " + mode);
    }

    private static double perHour(int deaths, long ticks) {
        if (ticks <= 0) {
            return 0;
        }
        return deaths * 72000.0 / ticks;
    }

    private static String wilson(int deaths, int rounds) {
        var z = 1.96;
        var p = deaths / (double) rounds;
        var denom = 1 + z * z / rounds;
        var centre = p + z * z / (2.0 * rounds);
        var margin = z * StrictMath.sqrt((p * (1 - p) + z * z / (4.0 * rounds)) / rounds);
        var low = (centre - margin) / denom;
        var high = (centre + margin) / denom;
        if (low < 0) {
            low = 0;
        }
        if (high > 1) {
            high = 1;
        }
        return String.format(Locale.ROOT, "[%.3f, %.3f]", low, high);
    }

    private record Cell(String terrain, String mode, int rounds, int deaths, int own, long ticks, int survived) {
    }

    private record Outcome(int deaths, int own, int ticks, int survived) {
    }

    private record Move(int x, int z, boolean own) {
    }
}
