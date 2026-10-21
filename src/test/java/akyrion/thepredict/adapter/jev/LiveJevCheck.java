package akyrion.thepredict.adapter.jev;

import akyrion.thepredict.MeasurementReport;
import akyrion.thepredict.domain.ServiceCheck;
import akyrion.thepredict.engine.ServerClock;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.LongConsumer;

/**
 * Live tool of M13.4 and M12: {@code GET /v1/models} against the real account, {@code thepredict.live.calls} times
 * (50 by default) spaced {@code thepredict.live.spacingSeconds} (10 by default). It is the {@code /thepredict check}
 * of the plugin: it lists models and consumes no tokens, but it contacts the account. Run only with
 * {@code ./gradlew liveJevCheck -PliveJev=true} after the user approves; the build never runs it.
 */
public final class LiveJevCheck {
    private LiveJevCheck() {
    }

    /**
     * Runs the checks and writes the report to the first argument.
     *
     * @param arguments the report path
     * @throws IOException if the report cannot be written
     */
    public static void main(String[] arguments) throws IOException {
        var refusal = LiveJevGate.refusal(System::getProperty, System::getenv);
        if (refusal.isPresent()) {
            System.err.println(refusal.get());
            System.exit(2);
            return;
        }
        var settings = LiveJevGate.settings(System::getProperty, System::getenv);
        var calls = LiveJevGate.positive(System::getProperty, "thepredict.live.calls", 50);
        var spacing = LiveJevGate.positive(System::getProperty, "thepredict.live.spacingSeconds", 10);
        var report = run(settings, calls, spacing * 1000L, millis -> sleep(millis));
        var output = Path.of(arguments.length > 0 ? arguments[0] : "build/reports/m13/live-check.md");
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.writeString(output, report.text());
        System.out.println(report.text());
    }

    /**
     * Runs the checks with a given pause between them; the tests call it against the loopback contract server.
     *
     * @return the report
     */
    static MeasurementReport run(SystemOneSettings settings, int calls, long spacingMillis, LongConsumer pause) {
        var client = new SystemOneClient(settings, ServerClock.of(() -> 0, System::nanoTime));
        var nanos = new long[calls];
        Map<String, Integer> states = new TreeMap<>();
        var details = new ArrayList<String>();
        try {
            for (var index = 0; index < calls; index++) {
                if (index > 0) {
                    pause.accept(spacingMillis);
                }
                var started = System.nanoTime();
                var check = client.check().get(30, TimeUnit.SECONDS);
                nanos[index] = System.nanoTime() - started;
                states.merge(check.state().name(), 1, Integer::sum);
                keepDetail(details, check);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new IllegalStateException("a check did not complete", exception);
        } finally {
            client.close();
        }
        var report = new MeasurementReport();
        report.line("# M13 live check: GET /v1/models");
        report.line("");
        report.line("Model " + settings.model() + ", " + calls + " calls " + spacingMillis / 1000 + " s apart.");
        report.table(List.of("Samples", "us p50 / p95 / p99 / max", "States", "Details"), List.of(List.of(
                String.valueOf(calls), MeasurementReport.micros(nanos), states.toString(), String.join("; ", details))));
        return report;
    }

    private static void keepDetail(List<String> details, ServiceCheck check) {
        if (details.size() < 5 && !details.contains(check.detail())) {
            details.add(check.detail());
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
