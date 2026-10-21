package akyrion.thepredict.adapter.jev;

import akyrion.thepredict.domain.ActiveUseConditions;
import akyrion.thepredict.domain.CastMode;
import akyrion.thepredict.domain.DecisionRequest;
import akyrion.thepredict.domain.OptionContext;
import akyrion.thepredict.domain.OptionPruning;
import akyrion.thepredict.domain.PruneReason;
import akyrion.thepredict.domain.PruningMode;
import akyrion.thepredict.domain.QuestionDetail;
import akyrion.thepredict.domain.QuestionSpec;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TacticalProfile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * M19.9 E1: the labelled bank with and without safe pruning, caps 8 and 4, cast modes {@code INTENT_ONLY} and
 * {@code ANY_READY}. One {@code prepare}, no Jev call and no clock, so two runs write the same bytes. Publishing the
 * report does not change {@code pruning}, the option cap or the readiness horizon.
 */
public final class BankPruningReport {
    private static final RequestEncoder ENCODER = new RequestEncoder(LiveJevGate.MODEL, new SemanticEncoder(12_000));

    private BankPruningReport() {
    }

    /**
     * Writes the report to the path given as the first argument.
     *
     * @param arguments the report file
     * @throws IOException when the report cannot be written
     */
    public static void main(String[] arguments) throws IOException {
        var report = Path.of(arguments[0]).toAbsolutePath();
        Files.createDirectories(report.getParent());
        Files.writeString(report, text(), StandardCharsets.UTF_8);
        System.out.println("Wrote " + report);
    }

    /**
     * The whole report. Two calls return equal strings.
     *
     * @return the markdown report
     */
    public static String text() {
        var scenes = BankPruningScan.load();
        var rows = new ArrayList<BankPruningScan.Tally>();
        for (var mode : List.of(CastMode.INTENT_ONLY, CastMode.ANY_READY)) {
            for (var pruning : List.of(PruningMode.OFF, PruningMode.SAFE)) {
                for (var cap : List.of(8, 4)) {
                    rows.add(BankPruningScan.measure(scenes, mode, pruning, cap));
                }
            }
        }
        return render(scenes.size(), rows, production());
    }

    private static String render(int scenarios, List<BankPruningScan.Tally> rows, Production production) {
        var out = new StringBuilder("# E1. Poda del banco (M19.9)\n\n");
        out.append(scenarios).append(" escenarios de scenarios/bank, etiquetas almacenadas, un prepare local, sin red ")
                .append("y sin Jev. Ocho filas: poda OFF y SAFE, topes 8 y 4, modos INTENT_ONLY y ANY_READY. La puerta ")
                .append("es SAFE con tope 4. Los defaults no cambian: pruning off, tope 8, horizonte 0. ")
                .append("Ajuste unico de E1, sin tocar etiquetas: SUSTAIN_WHILE_HEALTHY no quita la cura cuando la ")
                .append("pregunta supone SELF_LOW_HEALTH ni cuando el objetivo no se ve o no se alcanza.\n\n");
        table(out, rows);
        reasons(out, rows);
        out.append("## Produccion FULL, sin fit checks\n\n");
        out.append("El prefijo de 4 opciones es el recorte de M19.7 (orden de la pregunta, sin vetos). La fila SAFE ")
                .append("reordena por prioridad y aplica los vetos. El criterio de caracteres usa ese prefijo y, ")
                .append("aparte, el agregado del banco.\n\n");
        production.lines().forEach(out::append);
        out.append('\n');
        out.append(gate(rows, production));
        losses(out, rows);
        return out.toString();
    }

    private static void table(StringBuilder out, List<BankPruningScan.Tally> rows) {
        out.append("| Config | Reviewed | Best | Acceptable | Unique removed | Forbidden removed | Cures by SUSTAIN | ")
                .append("Healthy sustain removed | Chars | Local <2 | Mismatches | Vetoes undone |\n");
        out.append("|---|---|---|---|---|---|---|---|---|---|---|---|\n");
        for (var row : rows) {
            out.append(String.format(Locale.ROOT, "| %s | %d | %d/%d | %d/%d | %d | %d | %d | %d | %d | %d | %d | %d |%n",
                    row.name, row.reviewed, row.bestKept, row.bestApplicable, row.acceptableKept, row.acceptableBase,
                    row.uniqueRemoved, row.forbiddenRemoved, row.curesRemoved, row.healthySustainRemoved, row.chars,
                    row.local, row.mismatches.size(), row.undone));
        }
        out.append('\n');
    }

    private static void reasons(StringBuilder out, List<BankPruningScan.Tally> rows) {
        out.append("| Config |");
        for (var reason : PruneReason.values()) {
            out.append(' ').append(reason.name()).append(" |");
        }
        out.append("\n|---|");
        out.append("---|".repeat(PruneReason.values().length)).append('\n');
        for (var row : rows) {
            out.append("| ").append(row.name).append(" |");
            for (var reason : PruneReason.values()) {
                out.append(' ').append(row.reasons.getOrDefault(reason, 0)).append(" |");
            }
            out.append('\n');
        }
        out.append('\n');
    }

    private static String gate(List<BankPruningScan.Tally> rows, Production production) {
        var safe = row(rows, CastMode.ANY_READY, PruningMode.SAFE, 4);
        var off = row(rows, CastMode.ANY_READY, PruningMode.OFF, 8);
        var intent = row(rows, CastMode.INTENT_ONLY, PruningMode.SAFE, 4);
        var out = new StringBuilder("## Puerta para activar safe y el tope 4\n\n");
        out.append("No se activa. Fila de puerta: ").append(safe.name).append(", frente a ").append(off.name)
                .append(".\n\n");
        out.append(verdict(safe, off, production));
        out.append("\n\nMisma poda en INTENT_ONLY (no es la fila de puerta): ").append(intent.name).append(". ");
        out.append(verdict(intent, row(rows, CastMode.INTENT_ONLY, PruningMode.OFF, 8), production));
        return out.append("\n\n").toString();
    }

    private static String verdict(BankPruningScan.Tally safe, BankPruningScan.Tally off, Production production) {
        var best = safe.bestKept * 20 >= safe.bestApplicable * 19;
        var acceptable = safe.acceptableKept == safe.acceptableBase;
        var unique = safe.uniqueRemoved == 0;
        var cure = safe.curesRemoved == 0;
        var bankTenths = off.chars == 0 ? 0 : tenths(off.chars - safe.chars, off.chars);
        var productionTenths = tenths(production.full() - production.prefix(), production.full());
        var chars = productionTenths >= 250 && bankTenths >= 250;
        var met = best && acceptable && unique && cure && chars;
        return (met ? "cumple" : "no cumple") + ". best " + safe.bestKept + "/" + safe.bestApplicable + " ("
                + (best ? "cumple" : "no cumple") + "). aceptable " + safe.acceptableKept + "/" + safe.acceptableBase
                + " (" + (acceptable ? "cumple" : "no cumple") + "). aceptable unica quitada " + safe.uniqueRemoved
                + " (" + (unique ? "cumple" : "no cumple") + "). curas best o aceptables quitadas por "
                + "SUSTAIN_WHILE_HEALTHY " + safe.curesRemoved + " (" + (cure ? "cumple" : "no cumple")
                + "). caracteres de produccion " + production.full() + " a " + production.prefix() + ", "
                + percent(productionTenths) + " (" + (productionTenths >= 250 ? "cumple" : "no cumple")
                + "; el criterio pide 25 % o mas). banco " + off.chars + " a " + safe.chars + ", "
                + percent(bankTenths) + " (" + (bankTenths >= 250 ? "cumple" : "no cumple") + ").";
    }

    private static void losses(StringBuilder out, List<BankPruningScan.Tally> rows) {
        out.append("## Perdidas de la fila SAFE tope 4\n\n");
        for (var mode : List.of(CastMode.ANY_READY, CastMode.INTENT_ONLY)) {
            var row = row(rows, mode, PruningMode.SAFE, 4);
            out.append(row.name).append(row.losses.isEmpty() ? ": ninguna.\n" : ":\n");
            for (var loss : row.losses) {
                out.append("- ").append(loss).append('\n');
            }
        }
        var mismatches = row(rows, CastMode.ANY_READY, PruningMode.OFF, 8).mismatches;
        out.append("\nOpciones del archivo distintas del vivo OFF tope 8: ").append(mismatches.size());
        if (!mismatches.isEmpty()) {
            out.append(" (").append(String.join(", ", mismatches)).append(')');
        }
        out.append(".\n");
    }

    private static BankPruningScan.Tally row(List<BankPruningScan.Tally> rows, CastMode mode, PruningMode pruning,
                                             int cap) {
        return rows.stream().filter(row -> row.mode == mode && row.pruning == pruning && row.cap == cap).findFirst()
                .orElseThrow();
    }

    private static Production production() {
        var profile = JevFixtures.detailed(ScenarioFacts.example(), QuestionDetail.FULL, false);
        var request = ScenarioFacts.production(profile);
        var full = JevMeasurements.bodyChars(ENCODER, request);
        var prefix = JevMeasurements.bodyChars(ENCODER, JevMeasurements.capped(request, 4));
        var lines = new ArrayList<String>();
        lines.add("| Fila | Chars | Tokens a 1,5 |\n|---|---|---|\n");
        lines.add(line("completo", full));
        lines.add(line("prefijo tope 4", prefix));
        lines.add(line("SAFE tope 4 INTENT_ONLY", safeChars(request, CastMode.INTENT_ONLY)));
        lines.add(line("SAFE tope 4 ANY_READY", safeChars(request, CastMode.ANY_READY)));
        return new Production(full, prefix, lines);
    }

    private record Production(long full, long prefix, List<String> lines) {
    }

    private static String line(String name, long chars) {
        var tokens = chars < 0 ? "refused" : String.valueOf(RequestSizeGuard.tokens(chars));
        return String.format(Locale.ROOT, "| %s | %d | %s |%n", name, chars, tokens);
    }

    private static long safeChars(DecisionRequest request, CastMode mode) {
        var profile = request.profiles().values().iterator().next();
        var mob = request.facts().mobs().getFirst();
        var questions = new ArrayList<QuestionSpec>();
        for (var question : request.questions()) {
            var ordered = question.options().stream().filter(profile.tactics()::containsKey)
                    .sorted(Comparator.comparingInt((TacticId id) -> -profile.tactics().get(id).priority())
                            .thenComparing(TacticId::value)).toList();
            var now = OptionContext.hypothetical(ActiveUseConditions.of(mob, request.facts().participants()),
                    question.situation());
            var kept = OptionPruning.prune(ordered, BankPruningScan.retimed(profile, mode), mob, now, mode, 4).kept();
            if (kept.size() >= QuestionSpec.MIN_OPTIONS) {
                questions.add(question.withOptions(kept));
            }
        }
        if (questions.isEmpty()) {
            return 0;
        }
        return JevMeasurements.bodyChars(ENCODER, new DecisionRequest(request.encounter(), request.sequence(),
                request.facts(), questions, request.profiles()));
    }

    private static long tenths(long drop, long whole) {
        return whole <= 0 ? 0 : (drop * 1000 + whole / 2) / whole;
    }

    private static String percent(long tenths) {
        return (tenths / 10) + "." + (tenths % 10) + " %";
    }
}
