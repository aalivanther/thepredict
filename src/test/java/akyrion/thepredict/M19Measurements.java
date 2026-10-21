package akyrion.thepredict;

import akyrion.thepredict.adapter.server.binding.M19BindingMeasurements;
import akyrion.thepredict.domain.CastMode;
import akyrion.thepredict.domain.ChoiceId;
import akyrion.thepredict.domain.CooldownClass;
import akyrion.thepredict.domain.DamageTier;
import akyrion.thepredict.domain.Delivery;
import akyrion.thepredict.domain.DistanceBand;
import akyrion.thepredict.domain.HealthBand;
import akyrion.thepredict.domain.HealthThresholds;
import akyrion.thepredict.domain.IntentionParse;
import akyrion.thepredict.domain.IntentionVocabulary;
import akyrion.thepredict.domain.MobFacts;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.MovementSettings;
import akyrion.thepredict.domain.OptionPruning;
import akyrion.thepredict.domain.ParticipantAlias;
import akyrion.thepredict.domain.PurposeOrigin;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.SkillPurpose;
import akyrion.thepredict.domain.SkillRole;
import akyrion.thepredict.domain.Tactic;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.domain.TargetRelation;
import akyrion.thepredict.domain.ThreatWeights;
import akyrion.thepredict.domain.Trend;
import akyrion.thepredict.domain.UseCondition;
import akyrion.thepredict.engine.M19EngineMeasurements;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;

/**
 * Local measurements of M19, called from {@link M13Measurements} and runnable on its own. Doubles only: no socket, no
 * environment and no paid call. Times are wall times of this machine.
 */
public final class M19Measurements {
    /** Consumes results so a timed call is not removed. */
    public static volatile int sink;
    private static final int MICRO_WARMUP = 500;
    private static final int MICRO_SAMPLES = 2_000;
    private static final List<String> LINE = List.of("DAMAGE", "SUSTAIN", "ESCAPE", "MELEE", "DAMAGE_LOW",
            "WHEN_TARGET_IN_MELEE", "WHEN_TARGET_AT_RANGE", "WHEN_SELF_LOW_HEALTH", "WHEN_TARGET_FLEEING",
            "RISK_NEEDS_LINE_OF_SIGHT", "RISK_BLOCKED_BY_SHIELD", "RISK_ROOTS_CASTER", "RISK_LEAVES_CASTER_EXPOSED",
            "IGNORES_SHIELD", "NOT_A_TOKEN", "DAMAGE");

    private M19Measurements() {
    }

    /**
     * Writes the M19 rows.
     *
     * @param report report to write into
     */
    public static void write(MeasurementReport report) {
        report.section("Intention, native facts, ready casts and pruning (M19)");
        report.line("Wall time of this machine. `CombatActions` is a double: `ready` and `ticksUntilReady` are O(1) and"
                + " do not take the synchronized cooldown registry of MythicMobs, and a cast does not run a skill."
                + " `NativeSkillReader` does parse lines with `MythicLineConfigImpl`. No socket is opened.");
        vocabulary(report);
        M19BindingMeasurements.write(report);
        M19EngineMeasurements.write(report);
        pruning(report);
    }

    /**
     * Writes only the M19 rows. The first argument is the report path.
     *
     * @param arguments report path
     * @throws IOException if the report cannot be written
     */
    public static void main(String[] arguments) throws IOException {
        var output = Path.of(arguments.length > 0 ? arguments[0] : "build/reports/m13/m19-measurements.md");
        var report = new MeasurementReport();
        report.line("# M19 local measurements");
        report.line("");
        report.line("Java " + Runtime.version() + ", " + System.getProperty("os.name") + " "
                + System.getProperty("os.arch") + ".");
        write(report);
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.writeString(output, report.text());
        System.out.println(report.text());
    }

    /**
     * Percentile of a sample, using the same rank as {@link MeasurementReport}.
     *
     * @param nanos samples in nanoseconds
     * @param ratio percentile in {@code (0, 1]}
     * @return the value in nanoseconds
     */
    public static long percentile(long[] nanos, double ratio) {
        var sorted = nanos.clone();
        Arrays.sort(sorted);
        var index = (int) Math.ceil(ratio * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(sorted.length - 1, index))];
    }

    /**
     * {@code p50 / p99 / max} in nanoseconds.
     *
     * @param nanos samples
     * @return the text
     */
    public static String nanos(long[] nanos) {
        return percentile(nanos, 0.50) + " / " + percentile(nanos, 0.99) + " / " + nanos[maxIndex(nanos)] + " ns";
    }

    /**
     * Times {@code samples} calls after {@code warmup} discarded calls.
     *
     * @param warmup discarded calls
     * @param samples kept calls
     * @param work one call
     * @return nanoseconds of each kept call
     */
    public static long[] sample(int warmup, int samples, Runnable work) {
        for (var index = 0; index < warmup; index++) {
            work.run();
        }
        var nanos = new long[samples];
        for (var index = 0; index < samples; index++) {
            var started = System.nanoTime();
            work.run();
            nanos[index] = System.nanoTime() - started;
        }
        return nanos;
    }

    /** Warm-up iterations of a micro-benchmark. */
    public static int microWarmup() {
        return MICRO_WARMUP;
    }

    /** Kept iterations of a micro-benchmark. */
    public static int microSamples() {
        return MICRO_SAMPLES;
    }

    private static void vocabulary(MeasurementReport report) {
        var nanos = sample(MICRO_WARMUP, MICRO_SAMPLES, () -> {
            if (IntentionVocabulary.parse(LINE) instanceof IntentionParse.Parsed) {
                sink++;
            }
        });
        report.line("`IntentionVocabulary.parse` of 16 raw primitives (14 valid, one unknown, one repeated). Budget:"
                + " p99 ≤ 20 µs. Measured p99 " + micros(percentile(nanos, 0.99)) + " µs.");
        report.table(List.of("Row", "Samples", "p50 / p99 / max"), List.of(List.of("IntentionVocabulary.parse",
                String.valueOf(MICRO_SAMPLES), nanos(nanos) + " (" + micros(percentile(nanos, 0.99)) + " µs p99)")));
    }

    private static void pruning(MeasurementReport report) {
        var profile = pruneProfile();
        var mob = new MobFacts(new MobKey(UUID.fromString("00000000-0000-0000-0000-000000000019"), 0),
                HealthBand.HEALTHY, Optional.of(new TargetRelation(new ParticipantAlias(1), DistanceBand.MELEE,
                Trend.CLOSING, true, true, false, true, false)), Set.of(), Set.of(), Set.of(), Set.of());
        var ordered = new ArrayList<>(profile.tactics().keySet());
        var nanos = sample(MICRO_WARMUP, MICRO_SAMPLES, () -> sink += OptionPruning.prune(ordered, profile, mob,
                Set.of(), CastMode.ANY_READY, 8).kept().size());
        var p50 = percentile(nanos, 0.50);
        var p99 = percentile(nanos, 0.99);
        report.line("`OptionPruning.prune` of 8 tactics and 4 choices, `ANY_READY`, cap 8, one sustain veto and the"
                + " approach merge. Budget: p50 ≤ 3 µs and p99 ≤ 10 µs. Measured p50 " + micros(p50) + " µs, p99 "
                + micros(p99) + " µs.");
        report.table(List.of("Row", "Samples", "p50 / p99 / max"), List.of(List.of("OptionPruning.prune",
                String.valueOf(MICRO_SAMPLES), nanos(nanos))));
    }

    private static TacticalProfile pruneProfile() {
        var melee = purpose(SkillRole.DAMAGE, Delivery.MELEE, OptionalDouble.of(2.5), OptionalDouble.empty(),
                DamageTier.MEDIUM, Set.of(UseCondition.TARGET_IN_MELEE));
        var slam = purpose(SkillRole.CONTROL, Delivery.AREA_AROUND_SELF, OptionalDouble.empty(), OptionalDouble.of(3),
                DamageTier.MEDIUM, Set.of(UseCondition.ENEMIES_GROUPED));
        var shot = purpose(SkillRole.DAMAGE, Delivery.HITSCAN, OptionalDouble.of(16), OptionalDouble.empty(),
                DamageTier.LOW, Set.of(UseCondition.TARGET_AT_RANGE));
        var defense = new SkillPurpose(SkillRole.SUSTAIN, Set.of(SkillRole.SUSTAIN), Delivery.SELF,
                OptionalDouble.empty(), OptionalDouble.empty(), 0, CooldownClass.MEDIUM, DamageTier.NONE,
                Set.of(UseCondition.SELF_LOW_HEALTH), Set.of(), false, PurposeOrigin.DECLARED);
        var choices = new LinkedHashMap<ChoiceId, SkillChoice>();
        choices.put(new ChoiceId("melee"), SkillChoice.typed(new ChoiceId("melee"), "Melee", Set.of(), true, 0, melee));
        choices.put(new ChoiceId("slam"), SkillChoice.typed(new ChoiceId("slam"), "Slam", Set.of(), true, 0, slam));
        choices.put(new ChoiceId("shot"), SkillChoice.typed(new ChoiceId("shot"), "Shot", Set.of(), true, 0, shot));
        choices.put(new ChoiceId("defense"), SkillChoice.typed(new ChoiceId("defense"), "Defense", Set.of(), false, 0,
                defense));
        var tactics = new LinkedHashMap<TacticId, Tactic>();
        tactic(tactics, "press", Movement.APPROACH, "melee", 70);
        tactic(tactics, "slam", Movement.APPROACH, "slam", 65);
        tactic(tactics, "approach", Movement.APPROACH, null, 0);
        tactic(tactics, "flank", Movement.FLANK, "melee", 40);
        tactic(tactics, "hold", Movement.HOLD, "shot", 30);
        tactic(tactics, "retreat", Movement.RETREAT, "defense", 90);
        tactic(tactics, "keep", Movement.KEEP_DISTANCE, "shot", 20);
        tactic(tactics, "wait", Movement.HOLD, null, 5);
        return new TacticalProfile("prune_ai", "Guardian", "Defeat the target", tactics, choices, List.of(),
                new MovementSettings(2.5, 5, 9, 16, 10, 1.2, 1.15, OptionalDouble.empty()),
                new HealthThresholds(0.3, 0.6), new ThreatWeights(1, 1, 1, 0.5, 10), List.of(),
                new akyrion.thepredict.domain.DecisionTiming(20, 20, 100, 0.5, 0.2, 1).withCasting(CastMode.ANY_READY,
                        0));
    }

    private static SkillPurpose purpose(SkillRole role, Delivery delivery, OptionalDouble reach, OptionalDouble radius,
                                        DamageTier damage, Set<UseCondition> bestWhen) {
        var roles = role == SkillRole.DAMAGE ? Set.of(SkillRole.DAMAGE) : Set.of(role, SkillRole.DAMAGE);
        return new SkillPurpose(role, roles, delivery, reach, radius, 0, CooldownClass.SHORT, damage, bestWhen,
                Set.of(), false, PurposeOrigin.DECLARED);
    }

    private static void tactic(LinkedHashMap<TacticId, Tactic> tactics, String id, Movement movement, String choice,
                               int priority) {
        var tactic = new TacticId(id);
        tactics.put(tactic, new Tactic(tactic, movement, Optional.ofNullable(choice).map(ChoiceId::new),
                Optional.empty(), priority));
    }

    private static int maxIndex(long[] nanos) {
        var index = 0;
        for (var cursor = 1; cursor < nanos.length; cursor++) {
            if (nanos[cursor] > nanos[index]) {
                index = cursor;
            }
        }
        return index;
    }

    /**
     * Formats nanoseconds as microseconds with three decimals.
     *
     * @param nanos nanoseconds
     * @return the text
     */
    public static String micros(long nanos) {
        return String.format(Locale.ROOT, "%.3f", nanos / 1000.0);
    }
}
