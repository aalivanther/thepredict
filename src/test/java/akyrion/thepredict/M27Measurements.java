package akyrion.thepredict;

import akyrion.thepredict.domain.DifficultySection;
import akyrion.thepredict.domain.FightOutcome;
import akyrion.thepredict.domain.HintKind;
import akyrion.thepredict.domain.MemoryEntryState;
import akyrion.thepredict.domain.MemoryMode;
import akyrion.thepredict.domain.MemorySettings;
import akyrion.thepredict.domain.PartyBand;
import akyrion.thepredict.domain.PlayerMemory;
import akyrion.thepredict.domain.PlayerMemoryCodec;
import akyrion.thepredict.domain.ProfileMemory;
import akyrion.thepredict.engine.FightDigest;
import akyrion.thepredict.engine.MemoryAdmissionPolicy;
import akyrion.thepredict.engine.MemoryCache;
import akyrion.thepredict.engine.MemoryCounters;
import akyrion.thepredict.engine.MemoryDigests;
import akyrion.thepredict.engine.MemoryPrefetchHints;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Local sizes and timings for M27. No server and no network. A figure past its budget is published as
 * {@code no cumple}. The shipped mode stays {@code record}.
 */
public final class M27Measurements {
    private M27Measurements() {
    }

    /**
     * Prints the local table. The argument is the output file, or standard out when omitted.
     *
     * @param arguments optional path
     * @throws Exception when the file cannot be written
     */
    public static void main(String[] arguments) throws Exception {
        var report = new MeasurementReport();
        write(report);
        if (arguments.length == 0) {
            System.out.print(report.text());
            return;
        }
        java.nio.file.Files.writeString(java.nio.file.Path.of(arguments[0]), report.text());
    }

    /**
     * Writes the Memory table.
     *
     * @param report the report
     */
    public static void write(MeasurementReport report) {
        report.line("## Memory (M27)");
        report.line("");
        report.line("| jugadores | encuentros | bytes p50 | bytes p99 | bytes max | alta p50 ns | alta p99 ns | digestos ns | drenado ns | heap bytes |");
        report.line("| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |");
        var admit = admit();
        var digests = digests();
        var drain = drain();
        var heap = heap();
        var worstP99 = 0;
        var worstMax = 0;
        for (var players : new int[] {1, 50, 500}) {
            for (var encounters : new int[] {1, 5, 10}) {
                var lengths = lengths(players, encounters);
                var p50 = percentile(lengths, 0.50);
                var p99 = percentile(lengths, 0.99);
                var max = lengths[lengths.length - 1];
                worstP99 = Math.max(worstP99, p99);
                worstMax = Math.max(worstMax, max);
                report.line("| " + players + " | " + encounters + " | " + p50 + " | " + p99 + " | " + max + " | "
                        + admit[0] + " | " + admit[1] + " | " + digests + " | " + drain + " | " + heap + " |");
            }
        }
        report.line("");
        report.line("bytes p99 " + worstP99 + (worstP99 <= 600 ? " cumple" : " no cumple") + "; max " + worstMax
                + (worstMax <= 1024 ? " cumple" : " no cumple") + ".");
        var perEntry = heap / 500.0;
        report.line("digestos " + digests + " ns " + (digests >= 0 && digests <= 1000 ? "cumple" : "no cumple")
                + "; drenado " + drain + " ns " + (drain >= 0 && drain <= 1000 ? "cumple" : "no cumple") + ".");
        report.line("heap " + heap + " bytes para 500 entradas (" + perEntry + " por entrada) "
                + (heap > 0 && perEntry <= 416.0 ? "cumple" : "no cumple")
                + " frente a 416 bytes por entrada.");
        report.line("Cada fila codifica un registro por jugador, con tantos perfiles como encuentros, tope "
                + MemorySettings.DEFAULT.maxProfilesPerPlayer() + ". Semilla local 27. Sin sesión de servidor.");
        report.line("Columna memoria: una frase fija por participante cuando el modo es full.");
        report.line("");
    }

    private static int[] lengths(int players, int encounters) {
        var cap = MemorySettings.DEFAULT.maxProfilesPerPlayer();
        var profiles = Math.min(cap, Math.max(1, encounters));
        var values = new int[Math.max(1, players)];
        var none = ProfileMemory.NONE;
        for (var index = 0; index < values.length; index++) {
            var rows = new ArrayList<ProfileMemory>(profiles);
            for (var profile = 0; profile < profiles; profile++) {
                var rungs = new int[] {none, none, none, none, none, none};
                rungs[profile % ProfileMemory.RUNGS] = (index + profile * 17) & 0xFE;
                rows.add(new ProfileMemory(27 * 31 + index * 8 + profile, encounters, index + 1L, profile,
                        0L, FightOutcome.WIN, 27L, 1f, 3.4f, 0.2f, rungs, List.of()));
            }
            var memory = new PlayerMemory(27L, 27L + encounters, encounters, Optional.empty(), Optional.empty(),
                    Optional.empty(), rows);
            values[index] = PlayerMemoryCodec.encode(memory, cap).bytes().length;
        }
        java.util.Arrays.sort(values);
        return values;
    }

    private static long[] admit() {
        var counters = new MemoryCounters();
        var samples = new long[200];
        var subject = new UUID(1, 1);
        for (var index = 0; index < samples.length; index++) {
            var started = System.nanoTime();
            MemoryAdmissionPolicy.admit(MemoryMode.RECORD, false, MemoryEntryState.ABSENT, new byte[0], false, false,
                    "boss", false, 4, 1, MemorySettings.DEFAULT, subject, counters, DifficultySection.SOLO,
                    PartyBand.ONE);
            samples[index] = System.nanoTime() - started;
        }
        java.util.Arrays.sort(samples);
        return new long[] {samples[samples.length / 2], samples[(int) (samples.length * 0.99)]};
    }

    private static long digests() {
        for (var warm = 0; warm < 64; warm++) {
            timeDigests(warm);
        }
        var samples = new long[21];
        for (var round = 0; round < samples.length; round++) {
            samples[round] = timeDigests(200 + round);
        }
        Arrays.sort(samples);
        return samples[samples.length / 2];
    }

    private static long timeDigests(int round) {
        var digests = new MemoryDigests(MemorySettings.DEFAULT, new MemoryCounters());
        var count = 32;
        var started = System.nanoTime();
        for (var index = 0; index < count; index++) {
            digests.add(digest(round, index));
        }
        return (System.nanoTime() - started) / count;
    }

    private static FightDigest digest(int round, int index) {
        return new FightDigest(new UUID(3, round * 1000L + index + 1L), index, FightOutcome.WIN, PartyBand.ONE,
                DifficultySection.SOLO, Optional.empty(), index, false);
    }

    private static long drain() {
        var hints = new MemoryPrefetchHints();
        for (var warm = 0; warm < 8; warm++) {
            fill(hints, warm + 1);
            if (hints.drain(64).size() != 64) {
                return -1L;
            }
        }
        var samples = new long[21];
        for (var round = 0; round < samples.length; round++) {
            fill(hints, 40 + round);
            var started = System.nanoTime();
            var taken = hints.drain(16);
            samples[round] = System.nanoTime() - started;
            if (taken.size() != 16 || hints.drain(64).size() != 48) {
                return -1L;
            }
        }
        Arrays.sort(samples);
        return samples[samples.length / 2];
    }

    private static void fill(MemoryPrefetchHints hints, int high) {
        for (var index = 0; index < 64; index++) {
            hints.offer(new UUID(high, index + 1L), HintKind.JOIN, true);
        }
    }

    private static long heap() {
        var bytes = PlayerMemoryCodec.encode(PlayerMemory.empty(), 8).bytes();
        var samples = new long[7];
        MemoryCache keep = null;
        for (var trial = 0; trial < samples.length; trial++) {
            System.gc();
            var before = used();
            var cache = new MemoryCache(512, new MemoryCounters());
            for (var index = 0; index < 500; index++) {
                cache.loaded(new UUID(4, trial * 1000L + index + 1L), bytes, true, false, index);
            }
            System.gc();
            samples[trial] = used() - before;
            keep = cache;
        }
        java.lang.ref.Reference.reachabilityFence(keep);
        Arrays.sort(samples);
        return samples[samples.length / 2];
    }

    private static long used() {
        var runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private static int percentile(int[] sorted, double fraction) {
        var index = (int) Math.min(sorted.length - 1, Math.ceil(fraction * sorted.length) - 1);
        return sorted[Math.max(0, index)];
    }
}