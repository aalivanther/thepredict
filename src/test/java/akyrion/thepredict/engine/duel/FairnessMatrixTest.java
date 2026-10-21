package akyrion.thepredict.engine.duel;

import akyrion.thepredict.engine.DecisionTap;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * E2 of M23.11. Two hundred fights per cell, rungs 8, 4 and 0, round trip 100 ms, {@code lead}. Defaults are not moved
 * here.
 */
class FairnessMatrixTest {
    private static final int PAIRS = 200;

    @Test
    void leadKeepsWarnedHitsAndPublishesTheMatrix() throws IOException {
        var rows = new ArrayList<Row>();
        for (var rung : SwordPlayerDuelist.RUNGS) {
            rows.add(measure("sword", 0.5, rung));
            for (var name : SwordPlayerDuelist.ARCHETYPES) {
                for (var skill : SwordPlayerDuelist.SKILLS) {
                    rows.add(measure(name, skill, rung));
                }
            }
        }
        var medium = rows.stream().filter(row -> row.skill == 0.5).toList();
        var unwarned = perMinute(medium);
        var violations = sum(medium, row -> row.violations);
        var abort = ratio(sum(medium, row -> row.selfAborts), sum(medium, row -> row.opened));
        var dodge = ratio(sum(medium, row -> row.severeAvoided) + sum(medium, row -> row.severeBlocked),
                sum(medium, row -> row.severeLanded) + sum(medium, row -> row.severeAvoided)
                        + sum(medium, row -> row.severeBlocked));
        var changes = perMinuteChanges(medium);
        write(rows, unwarned, violations, abort, dodge, changes);
        assertEquals(0.0, unwarned, 0.0);
        assertEquals(0L, violations);
        assertTrue(abort <= 0.02, "self abort " + abort);
        assertTrue(dodge >= 0.85, "severe dodge " + dodge);
    }

    private static Row measure(String name, double skill, int rung) {
        var row = new Row(name, skill, rung);
        for (var seed = 1; seed <= PAIRS; seed++) {
            row.add(SwordPlayerDuelist.fight(name, skill, rung, seed, DecisionTap.NONE, false));
        }
        return row;
    }

    private static void write(List<Row> rows, double unwarned, long violations, double abort, double dodge,
                              double changes) throws IOException {
        var report = new StringBuilder();
        report.append("# E2 justicia (M23)\n\n");
        report.append("Instrumento: `SwordPlayerDuelist` y los cuatro arquetipos, ");
        report.append(PAIRS).append(" pares por celda, peldaños 8, 4 y 0, RTT 100 ms, `lead`. ");
        report.append("Sin servidor y sin llamadas de pago. Los defaults no se mueven. ");
        report.append("Cada aviso lo abre el motor (`TellOpening`, `TelegraphRelease`, `StanceClock`) con `lead` ");
        report.append("y preparación 0, así que lo mostrado es el mínimo de la clase y la respuesta. ");
        report.append("El aborto propio cuenta `REJECTED` y `SESSION_INVALID`. ");
        report.append("Un rival en alcance y sin recarga castiga durante `RECOVERY`. ");
        report.append("Los peldaños no acortan el aviso. El RTT queda fijo en 100 ms. ");
        report.append("La puerta de cambios de postura se publica aunque no se cumpla.\n\n");
        report.append("| Rival | Habilidad | Peldaño | Sin aviso / min | Violaciones | Aborto propio | ");
        report.append("Esquiva SEVERE | Cambios / min | Castigos |\n|---|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        for (var row : rows) {
            var minutes = row.ticks / 1200.0;
            report.append("| ").append(row.name).append(" | ").append(row.skill).append(" | ").append(row.rung);
            report.append(" | ").append(text(row.unwarned / minutes));
            report.append(" | ").append(row.violations);
            report.append(" | ").append(text(ratio(row.selfAborts, row.opened)));
            report.append(" | ").append(text(ratio(row.severeAvoided + row.severeBlocked,
                    row.severeLanded + row.severeAvoided + row.severeBlocked)));
            report.append(" | ").append(text(row.stanceChanges / minutes));
            report.append(" | ").append(row.punished).append(" |\n");
        }
        report.append("\nPuerta de la habilidad media (0,5), clases MEDIUM, HEAVY y SEVERE: ");
        report.append("UNWARNED_HITS_PER_MINUTE ").append(text(unwarned));
        report.append(unwarned == 0.0 ? " cumple" : " no cumple").append("; FAIRNESS_VIOLATIONS ");
        report.append(violations);
        report.append(violations == 0L ? " cumple" : " no cumple");
        report.append("; TELEGRAPH_SELF_ABORT_RATIO ").append(text(abort));
        report.append(abort <= 0.02 ? " cumple" : " no cumple");
        report.append("; DODGE_AFTER_TELEGRAPH_RATIO de SEVERE ").append(text(dodge));
        report.append(dodge >= 0.85 ? " cumple" : " no cumple");
        report.append("; STANCE_CHANGES_PER_MINUTE ").append(text(changes));
        report.append(changes <= 12.0 ? " cumple" : " no cumple").append(".\n");
        var output = Path.of("build/reports/m23/e2.md");
        Files.createDirectories(output.getParent());
        Files.writeString(output, report.toString());
    }

    private static double perMinute(List<Row> rows) {
        var ticks = rows.stream().mapToLong(row -> row.ticks).sum();
        var hits = rows.stream().mapToLong(row -> row.unwarned).sum();
        return ticks == 0 ? 0.0 : hits * 1200.0 / ticks;
    }

    private static double perMinuteChanges(List<Row> rows) {
        var ticks = rows.stream().mapToLong(row -> row.ticks).sum();
        var changes = rows.stream().mapToLong(row -> row.stanceChanges).sum();
        return ticks == 0 ? 0.0 : changes * 1200.0 / ticks;
    }

    private static long sum(List<Row> rows, java.util.function.ToLongFunction<Row> field) {
        return rows.stream().mapToLong(field).sum();
    }

    private static double ratio(long numerator, long denominator) {
        return denominator == 0 ? 0.0 : numerator / (double) denominator;
    }

    private static String text(double value) {
        return String.format(java.util.Locale.ROOT, "%.4f", value);
    }

    private static final class Row {
        private final String name;
        private final double skill;
        private final int rung;
        private long ticks;
        private long unwarned;
        private long violations;
        private long opened;
        private long selfAborts;
        private long severeLanded;
        private long severeAvoided;
        private long severeBlocked;
        private long stanceChanges;
        private long punished;

        private Row(String name, double skill, int rung) {
            this.name = name;
            this.skill = skill;
            this.rung = rung;
        }

        private void add(SwordPlayerDuelist.Fight fight) {
            ticks += fight.metrics().ticks();
            unwarned += fight.unwarnedHits();
            violations += fight.violations();
            opened += fight.opened();
            severeLanded += fight.severeLanded();
            severeAvoided += fight.severeAvoided();
            severeBlocked += fight.severeBlocked();
            selfAborts += fight.selfAborts();
            stanceChanges += fight.stanceChanges();
            punished += fight.punished();
        }
    }
}
