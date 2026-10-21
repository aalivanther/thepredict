package akyrion.thepredict.engine.duel;

import akyrion.thepredict.domain.CastMode;
import akyrion.thepredict.domain.TacticalProfile;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The four configurations of M19.9 (E2): {@code INTENT_ONLY} and {@code ANY_READY} by horizon 0 and 20, on the paired
 * seeds of M18. It measures and does not change a default, and it claims no superiority.
 */
final class DuelEvaluation {
    private static final List<DuelSimulator.Arm> ARMS = List.of(DuelSimulator.Arm.A, DuelSimulator.Arm.B,
            DuelSimulator.Arm.C, DuelSimulator.Arm.D);

    private DuelEvaluation() {
    }

    /** The whole matrix and the non-regression lines. No wall clock, so two runs match byte for byte. */
    static String markdown(int rounds, List<DuelMeasure.Casting> castings) {
        var out = new StringBuilder("# E2. Duel matrix by cast mode and horizon (M19.9)\n\n");
        out.append("Simulator of M18.7 with the real engine. ").append(rounds)
                .append(" paired seeds per arm; the same seeds in every arm and every configuration; spawn points ")
                .append("swapped every two rounds. Modes NONE and ORACLE(0.85). Pruning stays off and the option cap ")
                .append("stays 8. No network and no claim of superiority.\n\n");
        var baseline = new LinkedHashMap<DuelSimulator.Mode, DuelMatrix.Summary>();
        Play intent = null;
        for (var casting : castings) {
            var profiles = retimed(casting.mode(), casting.horizon());
            for (var mode : modes()) {
                var play = play(rounds, profiles, mode.mode());
                out.append(DuelMatrix.markdown(mode.title() + " — " + casting.name(), play.matrix()));
                out.append(cadence(play.audit()));
                out.append(gate(casting, mode, play, baseline));
                if (casting.mode() == CastMode.INTENT_ONLY && casting.horizon() == 0) {
                    baseline.put(mode.mode(), play.matrix().get(DuelSimulator.Arm.A));
                    intent = play;
                }
            }
        }
        if (intent == null) {
            throw new IllegalStateException("INTENT_ONLY with horizon 0 is missing");
        }
        return out.toString();
    }

    private static List<ModeRun> modes() {
        return List.of(new ModeRun("Mode NONE (no key: LOCAL and BASE)", new DuelSimulator.Mode.None()),
                new ModeRun("Mode ORACLE(0.85)", new DuelSimulator.Mode.Oracle(0.85, 20260929L)));
    }

    private static Play play(int rounds, Map<String, TacticalProfile> profiles, DuelSimulator.Mode mode) {
        var audit = new DuelCastAudit();
        var simulator = new DuelSimulator(mode, profiles, audit);
        var results = new EnumMap<DuelSimulator.Arm, List<DuelResult>>(DuelSimulator.Arm.class);
        for (var index = 0; index < rounds; index++) {
            var order = index % 2 == 0 ? ARMS : ARMS.reversed();
            for (var arm : order) {
                results.computeIfAbsent(arm, key -> new ArrayList<>()).add(simulator.round(arm, index,
                        (index / 2) % 2 == 1, DuelSimulator.Options.PLAIN));
            }
        }
        var matrix = new EnumMap<DuelSimulator.Arm, DuelMatrix.Summary>(DuelSimulator.Arm.class);
        results.forEach((arm, list) -> matrix.put(arm, DuelMatrix.summary(arm, list)));
        return new Play(matrix, audit);
    }

    private static Map<String, TacticalProfile> retimed(CastMode mode, int horizon) {
        var out = new LinkedHashMap<String, TacticalProfile>();
        DuelSimulator.packs().forEach((id, profile) -> out.put(id, copy(profile, mode, horizon)));
        return Map.copyOf(out);
    }

    private static TacticalProfile copy(TacticalProfile profile, CastMode mode, int horizon) {
        return new TacticalProfile(profile.id(), profile.mythicMobId(), profile.objective(), profile.tactics(),
                profile.choices(), profile.contexts(), profile.movement(), profile.health(), profile.threat(),
                profile.baseAi(), profile.timing().withCasting(mode, horizon), profile.participants());
    }

    private static String cadence(DuelCastAudit audit) {
        var out = new StringBuilder("| Skill | Engine casts | Ticks in reach | Cooldown | Casts / cooldown max |\n");
        out.append("|---|---|---|---|---|\n");
        for (var skill : DuelSkill.values()) {
            var ticks = audit.inRangeTicks[skill.ordinal()];
            var casts = audit.casts[skill.ordinal()];
            out.append(String.format(Locale.ROOT, "| %s | %d | %d | %d | %s |%n", skill.id, casts, ticks,
                    skill.cooldownTicks, ticks == 0 ? "n/a" : percent(casts * skill.cooldownTicks, ticks)));
        }
        return out.append('\n').toString();
    }

    private static String gate(DuelMeasure.Casting casting, ModeRun mode, Play play,
                               Map<DuelSimulator.Mode, DuelMatrix.Summary> baseline) {
        var duplicates = play.matrix().values().stream().mapToLong(DuelMatrix.Summary::duplicateCasts).sum();
        var reference = baseline.get(mode.mode());
        var arm = play.matrix().get(DuelSimulator.Arm.A);
        var wilson = reference == null || arm.wilson()[0] + 1e-9 >= reference.wilson()[0];
        var cadenceMet = cadenceMet(play.audit());
        var met = duplicates == 0 && play.audit().preparationStarts == 0 && play.audit().lateTicks == 0 && cadenceMet
                && wilson;
        var out = new StringBuilder("Criterio de no regresion (");
        out.append(casting.name()).append(", ").append(mode.title()).append("): ");
        out.append(met ? "cumple" : "no cumple").append(". Duplicados ").append(duplicates)
                .append(" (").append(duplicates == 0 ? "cumple" : "no cumple").append("). Inicios durante una preparacion ")
                .append(play.audit().preparationStarts).append(" (")
                .append(play.audit().preparationStarts == 0 ? "cumple" : "no cumple")
                .append("). Ticks de espera lista-cast sin guarda ").append(play.audit().lateTicks).append(" (")
                .append(play.audit().lateTicks == 0 ? "cumple" : "no cumple").append("). Cadencia en alcance ")
                .append(cadenceMet ? "cumple" : "no cumple").append(". Wilson del brazo A ");
        if (reference == null) {
            out.append("es la referencia INTENT_ONLY horizonte 0");
        } else {
            out.append(String.format(Locale.ROOT, "[%.3f, %.3f] frente a [%.3f, %.3f] de INTENT_ONLY horizonte 0 (%s)",
                    arm.wilson()[0], arm.wilson()[1], reference.wilson()[0], reference.wilson()[1],
                    wilson ? "cumple" : "no cumple"));
        }
        return out.append(".\n\n").toString();
    }

    private static boolean cadenceMet(DuelCastAudit audit) {
        for (var skill : DuelSkill.values()) {
            var ticks = audit.inRangeTicks[skill.ordinal()];
            if (ticks > 0 && audit.casts[skill.ordinal()] * skill.cooldownTicks * 20 < ticks * 19) {
                return false;
            }
        }
        return true;
    }

    private static String percent(long part, long whole) {
        var tenths = (part * 1000 + whole / 2) / whole;
        return (tenths / 10) + "." + (tenths % 10) + " %";
    }

    private record ModeRun(String title, DuelSimulator.Mode mode) {
    }

    private record Play(Map<DuelSimulator.Arm, DuelMatrix.Summary> matrix, DuelCastAudit audit) {
    }
}
