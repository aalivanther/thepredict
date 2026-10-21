package akyrion.thepredict.engine.duel;

import akyrion.thepredict.domain.Reaction;

import java.util.List;
import java.util.Locale;

/**
 * The conduct section of {@code banco.md} and its table {@code conducta.tsv}: what ThePredict did in each cell,
 * descriptive only. A figure that the simulator cannot give says so instead of a number.
 */
final class BenchConduct {
    private BenchConduct() {
    }

    static String section(BenchOutcome outcome) {
        var out = new StringBuilder("## Conducta de ThePredict (descriptiva: no cambia el veredicto)\n\n"
                + "Por celda: reacciones de M20.4 armadas por minuto (`DecisionTap.reactionFired`); parte de los ticks "
                + "con control en retirada y en banda, y con el rival al alcance cuerpo a cuerpo; casts que lo "
                + "desplazan por minuto; guardias por minuto, las que absorbieron un golpe del rival y las que lo "
                + "hicieron en menos de 20 ticks (puestas antes del impacto), y la absorción por ronda; parte del tiempo "
                + "sin línea de visión y ticks medios hasta recuperarla (solo `PILLARS18` tiene pilares); parte de sus "
                + "golpes que cayeron mientras el golpe cuerpo a cuerpo del rival recargaba; y la previsión del modelo "
                + "de patrones del rival (`EnemyModel.forecast`, modo `OBSERVE` por defecto): cobertura, acierto sobre "
                + "los golpes del rival y precisión propia del modelo. Es un diagnóstico: en `OBSERVE` el motor no la "
                + "usa para decidir y no llega a `IntentView.forecast`, que queda en −1 también en el servidor. Contra "
                + "una entidad el paso de patrones solo ve `STRIKE` cuando un golpe del rival cae; no hay lectura de "
                + "teclas ni de carga, que son de jugador.\n\n");
        for (var timing : outcome.settings().timings()) {
            out.append("### ").append(timing.label).append("\n\n| Escenario | Rival | Reacciones / min | Retirada | Banda "
                    + "| Cuerpo a cuerpo | Desplazamientos / min | Guardias / min | Absorben (< 20 ticks) | Absorción / "
                    + "ronda | Sin visión (ticks hasta verla) | Castigos en recarga | Previsión: cobertura, acierto, "
                    + "modelo |\n|---|---|---|---|---|---|---|---|---|---|---|---|---|\n");
            for (var cell : outcome.cells().get(timing)) {
                out.append(row(cell));
            }
            out.append('\n');
        }
        return out.toString();
    }

    private static String row(BenchCell.Result cell) {
        var did = cell.conduct();
        return String.format(Locale.ROOT, "| %s | %s | %s | %s | %s | %s | %.2f | %.2f | %s (%s) | %.1f | %s (%s) | %s "
                        + "| %s, %s, %s |%n", cell.spec().scenario().id(), cell.spec().rival(),
                reactions(did), BenchTables.percent(did.retreatShare()), BenchTables.percent(did.bandShare()),
                BenchTables.percent(did.meleeShare()), did.leapsPerMinute(), did.guardsPerMinute(),
                BenchTables.percent(did.guardsUsedShare()), BenchTables.percent(did.guardsTimelyShare()),
                did.absorbedPerRound(), BenchTables.percent(did.noSightShare()), number(did.ticksToSight()),
                BenchTables.percent(did.punishShare()), BenchTables.percent(did.forecastShare()),
                share(did.foreseenShare()), share(did.modelAccuracy()));
    }

    private static String reactions(ConductSummary did) {
        var out = new StringBuilder();
        did.reactionsPerMinute().forEach((name, rate) -> {
            if (rate > 0) {
                out.append(out.isEmpty() ? "" : "; ").append(name).append(String.format(Locale.ROOT, " %.2f", rate));
            }
        });
        return out.isEmpty() ? "ninguna" : out.toString();
    }

    private static String number(double value) {
        return Double.isNaN(value) ? "—" : String.format(Locale.ROOT, "%.1f", value);
    }

    private static String share(double value) {
        return Double.isNaN(value) ? "—" : BenchTables.percent(value);
    }

    static String table(BenchOutcome outcome) {
        var out = new StringBuilder("configuracion\tescenario\trival\tmodo");
        for (var reaction : Reaction.values()) {
            out.append('\t').append("reaccion_").append(reaction.name().toLowerCase(Locale.ROOT)).append("_min");
        }
        out.append("\tretirada\tbanda\tcuerpo_a_cuerpo\tdesplazamientos_min\tguardias_min\tguardias_absorben"
                + "\tguardias_a_tiempo\tabsorcion_ronda\tsin_vision\tticks_hasta_vision\tcastigos_en_recarga"
                + "\tprevision_cobertura\tprevision_acierto\tprecision_modelo\n");
        for (var entry : outcome.cells().entrySet()) {
            for (var cell : entry.getValue()) {
                out.append(line(entry.getKey().name(), cell)).append('\n');
            }
        }
        for (var cell : outcome.jev()) {
            out.append(line(cell.spec().timing().name() + "-JEV", cell)).append('\n');
        }
        return out.toString();
    }

    private static String line(String timing, BenchCell.Result cell) {
        var did = cell.conduct();
        var out = new StringBuilder(timing).append('\t').append(cell.spec().scenario().id()).append('\t')
                .append(cell.spec().rival()).append('\t').append(cell.spec().mode());
        for (var reaction : Reaction.values()) {
            out.append('\t').append(String.format(Locale.ROOT, "%.3f", did.reactionsPerMinute()
                    .getOrDefault(reaction.name(), 0.0)));
        }
        for (var value : List.of(did.retreatShare(), did.bandShare(), did.meleeShare(), did.leapsPerMinute(),
                did.guardsPerMinute(), did.guardsUsedShare(), did.guardsTimelyShare(), did.absorbedPerRound(),
                did.noSightShare(), did.ticksToSight(), did.punishShare(), did.forecastShare(), did.foreseenShare(),
                did.modelAccuracy())) {
            out.append('\t').append(BenchTables.decimal(value));
        }
        return out.toString();
    }
}
