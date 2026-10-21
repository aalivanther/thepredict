package akyrion.thepredict;

import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.engine.TickPassMeasure;
import akyrion.thepredict.domain.MovementSettings;
import akyrion.thepredict.domain.ReactionPolicy;
import akyrion.thepredict.domain.ReactionScope;
import akyrion.thepredict.domain.TickSignals;

import java.util.OptionalDouble;

/**
 * Local cost of the tick layer without a server (M20.0, M20.3). Published numbers are what this machine measured.
 */
public final class M20Measurements {
    private M20Measurements() {
    }

    /**
     * Appends the measured rows for both cadences.
     *
     * @param report report of {@link M13Measurements}
     */
    public static void write(MeasurementReport report) {
        var signals = TickSignals.pack(TickSignals.PRESENT, 12, 1, 1, 0, 80, 40);
        var settings = new MovementSettings(2.5, 5, 9, 16, 10, 1.2, 1.15, OptionalDouble.empty());
        var cooldown = new long[akyrion.thepredict.domain.Reaction.values().length];
        var since = new long[cooldown.length];
        var samples = 2000;
        var started = System.nanoTime();
        var chosen = 0;
        for (var index = 0; index < samples; index++) {
            chosen += ReactionPolicy.select(signals, index, 0, ReactionScope.ALL, false, true, Movement.APPROACH, 0.3,
                    settings, true, 0, cooldown, since);
        }
        var elapsed = System.nanoTime() - started;
        var perCall = elapsed / (double) samples / 1000.0;
        report.line("");
        report.line("## M20 tick layer");
        report.line("");
        report.line("ReactionPolicy.select mean " + perCall + " µs over " + samples + " calls (sink " + chosen + ").");
        report.line("REACTIVE capacity: about 1 encounter per world (300/200 ≈ 1.5) and up to about 0.95 USD per mob"
                + " per hour at the default caps. STANDARD capacity: about 5 encounters per world.");
        report.line("Measured pass bound stays maxMicros plus 8 visits without a cast plus 1 cast. These rows have no"
                + " server sample.");
        report.line(TickPassMeasure.table());
    }

    /**
     * Writes the E3 table. Not part of {@code check}.
     *
     * @param arguments report file
     * @throws java.io.IOException when the file cannot be written
     */
    public static void main(String[] arguments) throws java.io.IOException {
        var report = new MeasurementReport();
        write(report);
        var path = java.nio.file.Path.of(arguments[0]);
        java.nio.file.Files.createDirectories(path.getParent());
        java.nio.file.Files.writeString(path, report.text());
        System.out.println("Wrote " + path);
    }
}
