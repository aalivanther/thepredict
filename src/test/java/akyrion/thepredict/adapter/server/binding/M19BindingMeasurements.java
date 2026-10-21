package akyrion.thepredict.adapter.server.binding;

import akyrion.thepredict.M19Measurements;
import akyrion.thepredict.MeasurementReport;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/** Local measurement of {@link NativeSkillReader} for M19.3. Run only from {@code M19Measurements}. */
public final class M19BindingMeasurements {
    private M19BindingMeasurements() {
    }

    /**
     * Writes the reader row: 20 mechanic lines, one required {@code targetwithin} and one {@code lineofsight}.
     *
     * @param report report to write into
     */
    public static void write(MeasurementReport report) {
        var mechanics = new ArrayList<NativeSkillView.Line>(20);
        for (var index = 0; index < 19; index++) {
            mechanics.add(new NativeSkillView.Line("effect:particles", "effect:particles{delay=1}", false,
                    NativeSkillView.Targeter.OTHER, false));
        }
        mechanics.add(new NativeSkillView.Line("damage", "damage{a=1;delay=1;ignoreshield=true} @PlayersInRadius{r=3}",
                true, NativeSkillView.Targeter.RADIUS, false));
        var view = new FixedView(mechanics);
        var nanos = M19Measurements.sample(M19Measurements.microWarmup(), M19Measurements.microSamples(), () -> {
            var reading = NativeSkillReader.read("BenchSkill", view);
            if (reading.read() && reading.facts().hasDamagingMechanic()) {
                M19Measurements.sink++;
            }
        });
        var p99 = M19Measurements.percentile(nanos, 0.99);
        report.line("`NativeSkillReader.read` of 20 mechanics (19 cosmetic, one damaging radius). Budget: p99 ≤ 80 µs."
                + " Measured p99 " + M19Measurements.micros(p99) + " µs.");
        report.table(List.of("Row", "Samples", "p50 / p99 / max"), List.of(List.of("NativeSkillReader",
                String.valueOf(M19Measurements.microSamples()), M19Measurements.nanos(nanos))));
    }

    private record FixedView(List<NativeSkillView.Line> mechanics) implements NativeSkillView {
        @Override
        public boolean cooldownDeclared() {
            return true;
        }

        @Override
        public OptionalDouble staticCooldownSeconds() {
            return OptionalDouble.of(2.0);
        }

        @Override
        public int charges() {
            return 1;
        }

        @Override
        public String cooldownMode() {
            return "SEQUENTIAL";
        }

        @Override
        public List<Line> conditions() {
            return List.of(new Line("targetwithin", "targetwithin{d=16} true", false, Targeter.OTHER, true),
                    new Line("lineofsight", "lineofsight true", false, Targeter.OTHER, true));
        }

        @Override
        public List<Line> targetConditions() {
            return List.of();
        }

        @Override
        public OptionalInt ticks(String text) {
            return LiveNativeSkill.staticTicks(text);
        }
    }
}
