package akyrion.thepredict.eval;

import akyrion.thepredict.engine.duel.BaselineMatrix;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Offline entry points of the M22 Gradle tasks. A {@code live} kind is refused: this process does not read
 * {@code .env} and does not call TypeSafe.
 */
public final class EvalTools {
    private EvalTools() {
    }

    /**
     * Writes a one-line note for the kind. {@code live} returns 2. {@code baseline} writes the golden matrix.
     * {@code tune} writes the best classic of the legal space of the duel pack (SIM-02).
     *
     * @param arguments kind, output directory, and optional body size, spent dollars and cap
     * @return the process status
     * @throws Exception when the note cannot be written
     */
    public static int dispatch(String[] arguments) throws Exception {
        var kind = arguments.length > 0 ? arguments[0] : "report";
        if ("live".equals(kind)) {
            System.err.println("LIVE refused");
            return 2;
        }
        var output = Path.of(arguments.length > 1 ? arguments[1] : "build/reports/m22");
        Files.createDirectories(output);
        if ("baseline".equals(kind)) {
            baseline(output);
            return 0;
        }
        if ("tune".equals(kind)) {
            var report = akyrion.thepredict.engine.duel.EffectivenessBench.tune(Path.of("src/test/resources/eval"),
                    output.resolve("perfiles"), Integer.getInteger("thepredict.bench.threads", 4));
            Files.writeString(output.resolve("tune.md"), report);
            return 0;
        }
        if ("run".equals(kind)) {
            var chars = arguments.length > 2 ? Integer.parseInt(arguments[2]) : 1500;
            var spent = arguments.length > 3 ? Double.parseDouble(arguments[3]) : 0.0;
            var maxUsd = arguments.length > 4 ? Double.parseDouble(arguments[4]) : 1.0;
            if (!EvalPlan.withinCap(spent, EvalPlan.estimateUsd(chars), maxUsd)) {
                Files.writeString(output.resolve(kind + ".txt"), "below the cap, not sent\n");
                return 0;
            }
        }
        Files.writeString(output.resolve(kind + ".txt"), kind + " offline\n");
        return 0;
    }

    /** Writes the golden matrix of M22 and how long it took. */
    private static void baseline(Path output) throws java.io.IOException {
        var started = System.nanoTime();
        var rows = BaselineMatrix.rows(1, 200);
        var golden = Path.of("src/test/resources/golden/m22/baseline-matrix.tsv");
        Files.createDirectories(golden.getParent());
        Files.write(golden, rows);
        var seconds = (System.nanoTime() - started) / 1_000_000_000.0;
        var rounds = rows.size() - 1;
        Files.writeString(output.resolve("baseline.txt"), "rounds " + rounds + " seconds " + seconds
                + " roundsPerSecond " + (seconds == 0.0 ? rounds : rounds / seconds) + "\n");
    }

    /**
     * Writes a one-line note for the kind. {@code live} exits with status 2.
     *
     * @param arguments kind and output directory
     * @throws Exception when the note cannot be written
     */
    public static void main(String[] arguments) throws Exception {
        var code = dispatch(arguments);
        if (code != 0) {
            System.exit(code);
        }
    }
}
