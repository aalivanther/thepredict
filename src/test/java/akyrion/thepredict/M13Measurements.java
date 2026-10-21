package akyrion.thepredict;

import akyrion.thepredict.adapter.jev.JevMeasurements;
import akyrion.thepredict.adapter.server.context.EntityMeasurements;
import akyrion.thepredict.adapter.server.context.ReachMeasurements;
import akyrion.thepredict.engine.EngineMeasurements;
import akyrion.thepredict.engine.EngineScenarioMeasurements;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Local measurements of M13, run by {@code ./gradlew m13Measure} and never by the build: the engine under load, its
 * scenarios and A/B harnesses, the Jev path against the loopback contract server and, since M15, the capture cost of
 * the reach and grouped facts, and since M17 the capture cost with entities around. Nothing leaves the machine and
 * nothing reads the environment or {@code .env}. The report goes to the path of the first argument.
 */
public final class M13Measurements {
    private M13Measurements() {
    }

    /**
     * Writes the report.
     *
     * @param arguments the report path
     * @throws IOException if the report cannot be written
     */
    public static void main(String[] arguments) throws IOException {
        var output = Path.of(arguments.length > 0 ? arguments[0] : "build/reports/m13/measurements.md");
        var report = new MeasurementReport();
        report.line("# M13 local measurements");
        report.line("");
        report.line("Java " + Runtime.version() + " (" + System.getProperty("java.vm.name") + ", "
                + System.getProperty("java.vendor") + "), " + System.getProperty("os.name") + " "
                + System.getProperty("os.version") + " " + System.getProperty("os.arch") + ", "
                + Runtime.getRuntime().availableProcessors() + " processors, max heap "
                + Runtime.getRuntime().maxMemory() / (1024 * 1024) + " MiB.");
        EngineMeasurements.write(report);
        EngineScenarioMeasurements.write(report);
        JevMeasurements.write(report);
        ReachMeasurements.write(report);
        EntityMeasurements.write(report);
        M19Measurements.write(report);
        M20Measurements.write(report);
        M22Measurements.write(report);
        M23Measurements.write(report);
        M24Measurements.write(report);
        M25Measurements.write(report);
        M26Measurements.write(report);
        M27Measurements.write(report);
        M28Measurements.write(report);
        M29Measurements.write(report);
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.writeString(output, report.text());
        System.out.println(report.text());
    }
}
