package akyrion.thepredict.engine.duel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * What each side dealt and cast per minute of play in a cell: the section «Daño por minuto» of {@code banco.md} and its
 * tables {@code danio.tsv} and {@code habilidades.tsv}. Descriptive only: it does not change the verdict. The damage is
 * what the hits took before absorption, in health points. With the vanilla rule a hit inside the window of
 * invulnerability that beats the last hit deals only the excess and still counts as a hit; one that does not beat it is
 * lost whole (SIM-08). By skill: the casts and the hits that dealt damage. The vanilla hit of a classic is its goal, not
 * a cast, so only its hits count. Pure.
 */
final class BenchDamage {
    private static final double TICKS_PER_MINUTE = 1200;

    /**
     * One skill of a cell, per minute of play.
     *
     * @param id the skill, {@code vanilla} for the vanilla hit
     * @param casts casts of ThePredict
     * @param hits hits of ThePredict that dealt damage
     * @param rivalCasts casts of the rival
     * @param rivalHits hits of the rival that dealt damage
     */
    record Skill(String id, double casts, double hits, double rivalCasts, double rivalHits) {
    }

    /**
     * One cell, per minute of play.
     *
     * @param dealt health ThePredict's hits took from the rival
     * @param taken health the rival's hits took from ThePredict
     * @param lost damaging hits of ThePredict lost whole to the rival's window
     * @param rivalLost damaging hits of the rival lost whole to ThePredict's window
     * @param skills by skill, in id order
     */
    record Summary(double dealt, double taken, double lost, double rivalLost, List<Skill> skills) {
        Summary {
            skills = List.copyOf(skills);
        }
    }

    private BenchDamage() {
    }

    /**
     * The summary of a cell over both arms.
     *
     * @param cell the cell
     * @return per minute of play; NaN when no tick was played
     */
    static Summary of(BenchCell.Result cell) {
        var ticks = 0L;
        var totals = new double[4];
        var counts = new TreeMap<String, double[]>();
        for (var list : List.of(cell.a(), cell.b())) {
            for (var duel : list) {
                ticks += duel.ticks();
                totals[0] += duel.dealt();
                totals[1] += duel.taken();
                totals[2] += duel.lostHits();
                totals[3] += duel.rivalLostHits();
                add(counts, duel.casts(), 0);
                add(counts, duel.hits(), 1);
                add(counts, duel.rivalCasts(), 2);
                add(counts, duel.rivalHits(), 3);
            }
        }
        var minutes = ticks / TICKS_PER_MINUTE;
        var health = cell.spec().scenario().maxHealth();
        var skills = new ArrayList<Skill>();
        counts.forEach((id, count) -> skills.add(new Skill(id, count[0] / minutes, count[1] / minutes,
                count[2] / minutes, count[3] / minutes)));
        return new Summary(totals[0] * health / minutes, totals[1] * health / minutes, totals[2] / minutes,
                totals[3] / minutes, skills);
    }

    private static void add(Map<String, double[]> counts, Map<String, Integer> by, int column) {
        by.forEach((id, count) -> counts.computeIfAbsent(id, key -> new double[4])[column] += count);
    }

    /** The section of the report, one table per ThePredict configuration. */
    static String section(BenchOutcome outcome) {
        var out = new StringBuilder("## Daño por minuto (descriptivo: no cambia el veredicto)\n\n"
                + "Por minuto de juego de cada celda: la vida que quitaron los golpes de cada lado antes de la "
                + "absorción, los golpes con daño que la ventana de invulnerabilidad del otro anuló enteros, y por "
                + "habilidad los casts y los golpes que hicieron daño (casts → golpes). Con la regla vanilla, un golpe "
                + "dentro de la ventana que supera al último hace solo el exceso y cuenta como golpe; uno que no lo "
                + "supera se pierde entero. El golpe vanilla del clásico es su meta, no un cast: solo cuentan sus "
                + "golpes.\n\n");
        for (var timing : outcome.settings().timings()) {
            out.append("### ").append(timing.label).append("\n\n| Escenario | Rival | Daño / min (ThePredict · rival) "
                    + "| Golpes perdidos enteros / min (ThePredict · rival) | ThePredict: casts → golpes / min "
                    + "| Rival: casts → golpes / min |\n|---|---|---|---|---|---|\n");
            for (var cell : outcome.cells().get(timing)) {
                out.append(row(cell));
            }
            out.append('\n');
        }
        return out.toString();
    }

    private static String row(BenchCell.Result cell) {
        var damage = cell.damage();
        var mine = new StringBuilder();
        var theirs = new StringBuilder();
        for (var skill : damage.skills()) {
            mine.append(mine.isEmpty() ? "" : "; ").append(String.format(Locale.ROOT, "%s %.1f → %.1f", skill.id(),
                    skill.casts(), skill.hits()));
            theirs.append(theirs.isEmpty() ? "" : "; ").append(String.format(Locale.ROOT, "%s %.1f → %.1f",
                    skill.id(), skill.rivalCasts(), skill.rivalHits()));
        }
        return String.format(Locale.ROOT, "| %s | %s | %.1f · %.1f | %.2f · %.2f | %s | %s |%n",
                cell.spec().scenario().id(), cell.spec().rival(), damage.dealt(), damage.taken(), damage.lost(),
                damage.rivalLost(), mine, theirs);
    }

    /** {@code danio.tsv}: one row per cell, the cells of the verdict, the sensitivities and Jev. */
    static String table(BenchOutcome outcome) {
        var out = new StringBuilder("configuracion\tescenario\trival\tmodo\tdanio_min\tdanio_recibido_min"
                + "\tperdidos_min\tperdidos_rival_min\n");
        for (var group : groups(outcome).entrySet()) {
            for (var cell : group.getValue()) {
                var damage = cell.damage();
                out.append(prefix(group.getKey(), cell));
                for (var value : new double[] {damage.dealt(), damage.taken(), damage.lost(), damage.rivalLost()}) {
                    out.append('\t').append(BenchTables.decimal(value));
                }
                out.append('\n');
            }
        }
        return out.toString();
    }

    /** {@code habilidades.tsv}: one row per cell and skill. */
    static String skills(BenchOutcome outcome) {
        var out = new StringBuilder("configuracion\tescenario\trival\tmodo\thabilidad\tcasts_min\tgolpes_min"
                + "\tcasts_rival_min\tgolpes_rival_min\n");
        for (var group : groups(outcome).entrySet()) {
            for (var cell : group.getValue()) {
                for (var skill : cell.damage().skills()) {
                    out.append(prefix(group.getKey(), cell)).append('\t').append(skill.id());
                    for (var value : new double[] {skill.casts(), skill.hits(), skill.rivalCasts(),
                            skill.rivalHits()}) {
                        out.append('\t').append(BenchTables.decimal(value));
                    }
                    out.append('\n');
                }
            }
        }
        return out.toString();
    }

    private static Map<String, List<BenchCell.Result>> groups(BenchOutcome outcome) {
        var groups = new java.util.LinkedHashMap<String, List<BenchCell.Result>>();
        outcome.cells().forEach((timing, cells) -> groups.put(timing.name(), cells));
        groups.put("SENSIBILIDAD", outcome.sensitivities());
        groups.put("JEV", outcome.jev());
        return groups;
    }

    private static String prefix(String group, BenchCell.Result cell) {
        var timing = group.equals("SENSIBILIDAD") || group.equals("JEV")
                ? cell.spec().timing().name() + "-" + group : group;
        return timing + '\t' + cell.spec().scenario().id() + '\t' + cell.spec().rival() + '\t' + cell.spec().mode();
    }
}
