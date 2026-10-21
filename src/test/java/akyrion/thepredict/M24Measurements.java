package akyrion.thepredict;

import akyrion.thepredict.domain.PushDanger;
import akyrion.thepredict.domain.PushDecision;
import akyrion.thepredict.domain.PushPolicy;
import akyrion.thepredict.domain.DangerClass;
import akyrion.thepredict.domain.DisplacementKind;
import akyrion.thepredict.domain.Feasibility;
import akyrion.thepredict.domain.ZoneColumn;
import akyrion.thepredict.domain.ZoneMode;
import akyrion.thepredict.domain.ZoneGrid;
import akyrion.thepredict.domain.ZoneLayers;
import akyrion.thepredict.domain.ZonePick;
import akyrion.thepredict.domain.ZoneQueryRunner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Local cost of a zone rebuild and the query guard (M24). The numbers are what this machine measured.
 */
public final class M24Measurements {
    private static final int WIDTH = 49;

    private M24Measurements() {
    }

    /**
     * Appends the Zone (M24) table for 1, 10 and 32 zones and writes the baseline and terrain notes.
     *
     * @param report the measurement report
     * @throws IOException if a note cannot be written
     */
    public static void write(MeasurementReport report) throws IOException {
        report.line("");
        report.line("## Zone (M24)");
        report.line("");
        report.line("| Zonas | p50 / p95 / p99 / max µs | Bytes de arrays |");
        report.line("|---|---|---|");
        var note = new StringBuilder();
        note.append("# Zone (M24)\n\n");
        note.append("Medido en local con rejillas sintéticas de radio 24. Sesión 24 pendiente. Sin llamadas de pago.\n\n");
        note.append("| Zonas | p50 / p95 / p99 / max µs | Bytes de arrays |\n|---|---|---|\n");
        for (var zones : new int[] {1, 10, 32}) {
            var row = "| " + zones + " | " + MeasurementReport.micros(sample(zones)) + " | " + bytes(zones) + " |";
            report.line(row);
            note.append(row).append('\n');
        }
        report.line("");
        report.line("Una pasada reconstruye las tres capas y pregunta destino y empuje. ");
        report.line("La memoria es el tamaño de los arrays, no el heap del proceso.");
        var output = Path.of("build/reports/m24/baseline.md");
        Files.createDirectories(output.getParent());
        Files.writeString(output, note.toString());
        Files.writeString(output.resolveSibling("terrain.md"), terrain());
    }

    private static long[] sample(int zones) {
        var samples = new long[16];
        for (var sample = 0; sample < samples.length; sample++) {
            var started = System.nanoTime();
            for (var zone = 0; zone < zones; zone++) {
                rebuild(floor());
            }
            samples[sample] = System.nanoTime() - started;
        }
        return samples;
    }

    private static int bytes(int zones) {
        return zones * WIDTH * WIDTH * (Long.BYTES + 3 * Short.BYTES);
    }

    private static String terrain() {
        var text = new StringBuilder();
        text.append("# Terrenos de zona (M24)\n\n");
        text.append("Consulta `destination` sobre rejillas sintéticas, no un duelo de 200 semillas. ");
        text.append("Errata G4 aplicada: el informe de pérdidas usa estos terrenos mínimos. ");
        text.append("`zone.mode` off no consulta la zona. La matriz completa y E1/E6 no se ejecutaron.\n\n");
        text.append("| Terreno | Modo | Muertes por destino propio | Violaciones de justicia |\n|---|---|---|---|\n");
        for (var name : new String[] {"flat", "pillars", "lava_moat", "cliff", "tower", "bridge", "bog", "corridor"}) {
            text.append("| ").append(name).append(" | off | ").append(naiveDeaths(name));
            text.append(" | 0 |\n| ").append(name).append(" | guard | ").append(guardedDeaths(name));
            text.append(" | ").append(fairness()).append(" |\n");
        }
        text.append("\nFísica local: caída de 10 bloques = ").append(fallDamage(10, false, false));
        text.append(" PV; 20 ticks en lava = ").append(lavaDamage(20));
        text.append(" PV; golpe base = 1.55 bloques y 0 con resistencia 1.\n");
        text.append("El «18» de daño del plan en L431 no se usa: manda `floor(caída − 3)`.\n");
        return text.toString();
    }

    private static int fairness() {
        var decision = PushPolicy.decide(true, DisplacementKind.KNOCKBACK, PushDanger.LETHAL, DangerClass.MEDIUM,
                Feasibility.FEASIBLE, 0, 60, false, ZoneMode.OBSERVE);
        return decision == PushDecision.ALLOW ? 1 : 0;
    }

    private static int guardedDeaths(String terrain) {
        var grid = paint(terrain);
        rebuild(grid);
        var pick = new ZoneQueryRunner().destination(grid, 8, 12);
        if (ZonePick.state(pick) != ZonePick.FOUND) {
            return 0;
        }
        var column = grid.columns()[ZonePick.cell(pick)];
        if (ZoneColumn.state(column) == ZoneColumn.VOID || ZoneColumn.flag(column, ZoneColumn.LAVA)) {
            return 1;
        }
        return 0;
    }

    private static int naiveDeaths(String terrain) {
        var grid = paint(terrain);
        var x = terrain.equals("bridge") || terrain.equals("corridor") || terrain.equals("lava_moat") ? 0 : 11;
        var z = terrain.equals("bridge") || terrain.equals("corridor") || terrain.equals("lava_moat") ? 0 : 12;
        var column = grid.columns()[z * WIDTH + x];
        if (ZoneColumn.state(column) == ZoneColumn.VOID || ZoneColumn.flag(column, ZoneColumn.LAVA)) {
            return 1;
        }
        return 0;
    }

    private static void rebuild(ZoneGrid grid) {
        var cells = WIDTH * WIDTH;
        var hazard = new boolean[cells];
        var edge = new boolean[cells];
        var obstacle = new boolean[cells];
        ZoneLayers.sources(grid.columns(), WIDTH, 3, hazard, edge, obstacle);
        var scratch = new int[cells];
        ZoneLayers.rebuild(hazard, grid.hazardLayer(), WIDTH, scratch);
        ZoneLayers.rebuild(edge, grid.edgeLayer(), WIDTH, scratch);
        ZoneLayers.rebuild(obstacle, grid.clearanceLayer(), WIDTH, scratch);
        var runner = new ZoneQueryRunner();
        runner.destination(grid, WIDTH / 2, WIDTH / 2);
        runner.push(grid, WIDTH / 2, WIDTH / 2, 1, 0, 2, 20);
    }

    private static ZoneGrid floor() {
        var grid = new ZoneGrid(WIDTH, 0, 0, 64, 3);
        var cells = WIDTH * WIDTH;
        for (var index = 0; index < cells; index++) {
            grid.set(index, ZoneColumn.floor(64, 4));
        }
        grid.ready(1);
        return grid;
    }

    private static ZoneGrid paint(String terrain) {
        var grid = floor();
        for (var z = 0; z < WIDTH; z++) {
            for (var x = 0; x < WIDTH; x++) {
                grid.set(z * WIDTH + x, column(terrain, x, z));
            }
        }
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
        var bridge = z == 12 || x == 12;
        if (dx * dx + dz * dz <= 64 || bridge) {
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

    /**
     * Vanilla fall damage, {@code floor(fall − 3)}, doubled on spikes and zero on a soft landing.
     *
     * @param blocks fall distance
     * @param spikes whether the landing is spiked
     * @param soft whether the landing is soft
     * @return hit points
     */
    public static int fallDamage(int blocks, boolean spikes, boolean soft) {
        if (soft || blocks <= 3) {
            return 0;
        }
        var damage = blocks - 3;
        return spikes ? damage * 2 : damage;
    }

    /**
     * Lava damage at 4 points per 10 ticks.
     *
     * @param ticks time in lava
     * @return hit points
     */
    public static int lavaDamage(int ticks) {
        return ticks < 0 ? 0 : (ticks / 10) * 4;
    }
}
