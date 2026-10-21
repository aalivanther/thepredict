package akyrion.thepredict.engine.duel;

import akyrion.thepredict.adapter.server.duel.DuelFile;
import akyrion.thepredict.adapter.server.duel.DuelTestKit;
import akyrion.thepredict.engine.DuelRecording;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * A full session of the script of M18.6 in the simulator, recorded by the real duel recorder of M18.4: the rounds in
 * the order A, B, C, D, D, C, B, A… without a key, then A, B, C with the oracle, sides swapped every two rounds. Used by
 * {@code duelMeasure} and by the test that checks that a session fits within the limits and writes every file.
 */
final class DuelSessionRun {
    private DuelSessionRun() {
    }

    /** Records a session under {@code root} and describes the files it wrote. */
    static String record(Path root, int localRounds, int keyedRounds) throws IOException {
        var local = folder(root.resolve("local"), localRounds, List.of(DuelSimulator.Arm.A, DuelSimulator.Arm.B,
                DuelSimulator.Arm.C, DuelSimulator.Arm.D), new DuelSimulator.Mode.None(), "local-sim");
        var keyed = folder(root.resolve("oracle"), keyedRounds, List.of(DuelSimulator.Arm.A, DuelSimulator.Arm.B,
                DuelSimulator.Arm.C), new DuelSimulator.Mode.Oracle(0.85, 7L), "oracle-sim");
        var out = new StringBuilder("## One recorded session (M18.4 limits)\n\n| Recording | File | Bytes |\n|---|---|---|\n");
        for (var folder : List.of(local, keyed)) {
            for (var file : DuelFile.values()) {
                out.append(String.format(Locale.ROOT, "| %s | %s | %d |%n", folder.getFileName(), file.fileName(),
                        Files.size(folder.resolve(file.fileName()))));
            }
        }
        return out.append('\n').toString();
    }

    /** Plays and records the rounds of one recording; returns its folder. */
    static Path folder(Path root, int rounds, List<DuelSimulator.Arm> arms, DuelSimulator.Mode mode, String label) {
        var simulator = new DuelSimulator(mode);
        var kit = simulator.recordInto(root);
        var status = kit.recorder.start(new DuelRecording.DuelRequest(label, DuelSimulator.RED_FACTION,
                DuelSimulator.BLUE_FACTION, DuelTestKit.WORLD, 0, 65, 0, 24, Set.of(), Optional.empty()));
        if (!status.recording()) {
            throw new IllegalStateException(status.stoppedBy().orElse("not recording"));
        }
        for (var index = 0; index < rounds; index++) {
            var cycle = index % (2 * arms.size());
            var arm = cycle < arms.size() ? arms.get(cycle) : arms.get(2 * arms.size() - 1 - cycle);
            simulator.round(arm, index, (index / 2) % 2 == 1, DuelSimulator.Options.PLAIN);
        }
        var stopped = kit.recorder.stop();
        if (!kit.recorder.awaitWriter(60_000) || !stopped.stoppedBy().equals(Optional.of("command"))) {
            throw new IllegalStateException("the recording did not end by the command: " + stopped.stoppedBy());
        }
        return Path.of(kit.recorder.status().directory());
    }
}
