package akyrion.thepredict.engine.duel;

import java.util.ArrayList;
import java.util.List;

/**
 * The M22.0 matrix: arms A–D, modes none and oracle 0.85, seeds 1 through 200. Errata G2 keeps it under
 * {@code golden/m22}.
 */
public final class BaselineMatrix {
    /** Column header. */
    public static final String HEADER = "arm\tmode\tseed\twinner\tticks\tsha256";

    private BaselineMatrix() {
    }

    /**
     * Rows for seeds {@code from} through {@code to}, inclusive, with the header when {@code from} is 1.
     *
     * @param from first seed
     * @param to last seed
     * @return the lines
     */
    public static List<String> rows(int from, int to) {
        var lines = new ArrayList<String>();
        if (from == 1) {
            lines.add(HEADER);
        }
        for (var mode : List.of(new DuelSimulator.Mode.None(), new DuelSimulator.Mode.Oracle(0.85, 85L))) {
            var label = mode instanceof DuelSimulator.Mode.None ? "NONE" : "ORACLE";
            for (var seed = from; seed <= to; seed++) {
                var simulator = new DuelSimulator(mode);
                for (var arm : DuelSimulator.Arm.values()) {
                    var result = simulator.round(arm, seed, false, DuelSimulator.Options.PLAIN);
                    lines.add(arm.name() + "\t" + label + "\t" + seed + "\t" + result.winner().name() + "\t"
                            + result.ticks() + "\t" + result.eventDigest());
                }
            }
        }
        return lines;
    }
}
