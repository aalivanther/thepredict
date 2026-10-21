package akyrion.thepredict;

import akyrion.thepredict.adapter.storage.OverlayModes;
import akyrion.thepredict.adapter.storage.RunIds;
import akyrion.thepredict.adapter.storage.SegmentFiles;
import akyrion.thepredict.adapter.storage.StorageLimits;
import akyrion.thepredict.adapter.storage.StorageWriter;
import akyrion.thepredict.config.ConfigProblem;
import akyrion.thepredict.config.PluginSettings;
import akyrion.thepredict.domain.RecordLevel;

import java.nio.file.Path;
import java.time.Clock;
import java.util.Optional;

/**
 * Translates the {@code records} and {@code storage} sections into the writer specification (rule D10, M21). The
 * specification carries the A5 caps of the configured memory, learning and distill sections (JS-05).
 */
final class StorageWiring {
    /** Replies the writer may hold for the engine. */
    static final int REPLY_CAPACITY = 256;

    private StorageWiring() {
    }

    /**
     * Why storage starts without a writer, as the level word of its status: only an invalid {@code config.yml}, because
     * the plugin stays inactive and its settings are only the defaults (CAR-19). Records and aggregates both off open a
     * writer that only serves the gate verdicts (T1-ter, CAR-08).
     *
     * @param problem the problem of {@code config.yml}, if any
     * @return the level word of an idle service, or empty when the writer opens
     */
    static Optional<String> idleWord(Optional<ConfigProblem> problem) {
        return problem.isPresent() ? Optional.of("off") : Optional.empty();
    }

    /**
     * The writer specification of one start.
     *
     * @param dataFolder the plugin data folder
     * @param current the settings in use
     * @param yaml text of {@code config.yml}, for its fingerprint without {@code apiKey}
     * @param pluginVersion plugin version
     * @param wall wall clock of the run id
     * @return the specification
     */
    static StorageWriter.Spec spec(Path dataFolder, PluginSettings current, String yaml, String pluginVersion,
                                   Clock wall) {
        var storage = current.storage();
        return new StorageWriter.Spec(dataFolder.resolve(storage.directory()), dataFolder.resolve(storage.file()),
                storage.level() != RecordLevel.OFF, "sqlite".equals(storage.aggregates()), storage.queueCapacity(),
                storage.segmentMegabytes() * 1024L * 1024L, storage.segmentMinutes() * 60_000_000_000L,
                storage.compressClosedSegments(), storage.retentionDays(), storage.maxTotalMegabytes() * 1024L * 1024L,
                storage.synchronous(), storage.busyTimeoutMillis(), storage.backupEveryHours(), storage.backupsKept(),
                RunIds.run(wall), pluginVersion, current.jev().model(), RunIds.configSha256(yaml), SegmentFiles.DISK,
                REPLY_CAPACITY, StorageLimits.of(current.memory(), current.learning(), current.distill()),
                new OverlayModes(current.zone().mode(), current.group().mode(), current.distill().mode(),
                        current.patterns().mode()));
    }
}
