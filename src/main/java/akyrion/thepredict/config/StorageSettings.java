package akyrion.thepredict.config;

import akyrion.thepredict.domain.AnswerCacheMode;
import akyrion.thepredict.domain.RecordBodies;
import akyrion.thepredict.domain.RecordLevel;

import org.bukkit.configuration.ConfigurationSection;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * The {@code records.*}, {@code storage.*} and {@code decision.answerCache*} keys (M21.12). Defaults live only here.
 * {@code storage.aggregates: mysql} is reserved and rejected. The root translates this into the engine and the writer.
 *
 * @param level how much of a fight is written
 * @param bodies how request bodies are kept
 * @param directory relative records directory
 * @param queueCapacity event queue, 1024 to 131072
 * @param segmentMegabytes rotation size, 8 to 512
 * @param segmentMinutes rotation age, 5 to 1440
 * @param compressClosedSegments whether a closed segment is gzipped
 * @param retentionDays days kept, 1 to 365
 * @param maxTotalMegabytes cap of {@code events/}, 64 to 65536
 * @param aggregates {@code off} or {@code sqlite}
 * @param file database file name
 * @param synchronous {@code normal} or {@code full}
 * @param busyTimeoutMillis SQLite busy timeout, 0 to 30000
 * @param flushIntervalTicks ticks between flushes, 20 to 1200
 * @param maxPendingKeys pending aggregate keys, 256 to 65536
 * @param backupEveryHours 0, or 1 to 168
 * @param backupsKept copies kept, 1 to 60
 * @param answerCache off, shadow or on
 * @param answerCacheTtlTicks validity, 20 to 600
 * @param answerCacheEntries entry cap, 64 to 65536
 * @param answerCacheRevalidateRatio fraction of hits that are also sent, 0 to 1
 * @param patternSummaryBinary whether {@code pattern_summary} also writes {@code summary_b64}
 */
public record StorageSettings(RecordLevel level, RecordBodies bodies, String directory, int queueCapacity,
                              int segmentMegabytes, int segmentMinutes, boolean compressClosedSegments,
                              int retentionDays, int maxTotalMegabytes, String aggregates, String file,
                              String synchronous, int busyTimeoutMillis, int flushIntervalTicks, int maxPendingKeys,
                              int backupEveryHours, int backupsKept, AnswerCacheMode answerCache,
                              int answerCacheTtlTicks, int answerCacheEntries, double answerCacheRevalidateRatio,
                              boolean patternSummaryBinary) {
    /**
     * Validates ranges.
     *
     * @throws NullPointerException if a component is {@code null}
     * @throws IllegalArgumentException if a range fails
     */
    public StorageSettings {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(bodies, "bodies");
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(aggregates, "aggregates");
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(synchronous, "synchronous");
        Objects.requireNonNull(answerCache, "answerCache");
        if (answerCacheRevalidateRatio < 0 || answerCacheRevalidateRatio > 1) {
            throw new IllegalArgumentException("answerCacheRevalidateRatio out of range");
        }
    }

    /**
     * The defaults of a fresh installation.
     *
     * @return the defaults
     */
    public static StorageSettings standard() {
        return new StorageSettings(RecordLevel.DECISIONS, RecordBodies.HASH, "records", 16384, 64, 60, true, 14, 2048,
                "sqlite", "aggregates.db", "normal", 2000, 100, 4096, 24, 7, AnswerCacheMode.OFF, 100, 2048, 0.1,
                false);
    }

    /**
     * Reads the keys. Absent keys use {@link #standard()}.
     *
     * @param configuration the plugin file
     * @return the settings
     * @throws ConfigurationException if a value is invalid, naming the path
     */
    public static StorageSettings from(ConfigurationSection configuration) {
        var standard = standard();
        var aggregates = word(configuration, "storage.aggregates", standard.aggregates());
        if (aggregates.equals("mysql")) {
            throw new ConfigurationException("storage.aggregates",
                    "is reserved; MySQL is not implemented in this release");
        }
        if (!aggregates.equals("off") && !aggregates.equals("sqlite")) {
            throw new ConfigurationException("storage.aggregates", "must be off or sqlite, was a text value");
        }
        var synchronous = word(configuration, "storage.synchronous", standard.synchronous());
        if (!synchronous.equals("normal") && !synchronous.equals("full")) {
            throw new ConfigurationException("storage.synchronous", "must be normal or full");
        }
        var hours = ConfigurationValues.intValue(configuration, "storage.backupEveryHours", standard.backupEveryHours());
        if (hours != 0 && (hours < 1 || hours > 168)) {
            throw new ConfigurationException("storage.backupEveryHours", "must be 0 or within [1, 168], was " + hours);
        }
        AnswerCacheMode cache;
        try {
            cache = AnswerCacheMode.parse(word(configuration, "decision.answerCache",
                    standard.answerCache().name().toLowerCase(java.util.Locale.ROOT)));
        } catch (IllegalArgumentException exception) {
            throw new ConfigurationException("decision.answerCache", "must be off, shadow or on");
        }
        RecordLevel level;
        RecordBodies bodies;
        try {
            level = RecordLevel.parse(word(configuration, "records.level",
                    standard.level().name().toLowerCase(java.util.Locale.ROOT)));
            bodies = RecordBodies.parse(word(configuration, "records.bodies",
                    standard.bodies().name().toLowerCase(java.util.Locale.ROOT)));
        } catch (IllegalArgumentException exception) {
            throw new ConfigurationException(exception.getMessage().startsWith("records.bodies")
                    ? "records.bodies" : "records.level", "is not an allowed value");
        }
        var ratio = ConfigurationValues.decimal(configuration, "decision.answerCacheRevalidateRatio",
                standard.answerCacheRevalidateRatio());
        if (ratio < 0 || ratio > 1) {
            throw new ConfigurationException("decision.answerCacheRevalidateRatio", "must be within [0, 1]");
        }
        return new StorageSettings(level, bodies, directory(configuration, standard.directory()),
                bounded(configuration, "records.queueCapacity", standard.queueCapacity(), 1024, 131072),
                bounded(configuration, "records.segmentMegabytes", standard.segmentMegabytes(), 8, 512),
                bounded(configuration, "records.segmentMinutes", standard.segmentMinutes(), 5, 1440),
                ConfigurationValues.booleanValue(configuration, "records.compressClosedSegments",
                        standard.compressClosedSegments()),
                bounded(configuration, "records.retentionDays", standard.retentionDays(), 1, 365),
                bounded(configuration, "records.maxTotalMegabytes", standard.maxTotalMegabytes(), 64, 65536),
                aggregates, file(configuration, standard.file()), synchronous,
                bounded(configuration, "storage.busyTimeoutMillis", standard.busyTimeoutMillis(), 0, 30000),
                bounded(configuration, "storage.flushIntervalTicks", standard.flushIntervalTicks(), 20, 1200),
                bounded(configuration, "storage.maxPendingKeys", standard.maxPendingKeys(), 256, 65536),
                hours, bounded(configuration, "storage.backupsKept", standard.backupsKept(), 1, 60), cache,
                bounded(configuration, "decision.answerCacheTtlTicks", standard.answerCacheTtlTicks(), 20, 600),
                bounded(configuration, "decision.answerCacheEntries", standard.answerCacheEntries(), 64, 65536),
                ratio, ConfigurationValues.booleanValue(configuration, "records.patternSummaryBinary",
                        standard.patternSummaryBinary()));
    }

    private static int bounded(ConfigurationSection configuration, String path, int fallback, int minimum, int maximum) {
        var value = ConfigurationValues.intValue(configuration, path, fallback);
        if (value < minimum || value > maximum) {
            throw new ConfigurationException(path, "must be within [" + minimum + ", " + maximum + "], was " + value);
        }
        return value;
    }

    private static String directory(ConfigurationSection configuration, String fallback) {
        var value = word(configuration, "records.directory", fallback);
        Path path;
        try {
            path = Path.of(value);
        } catch (InvalidPathException exception) {
            throw new ConfigurationException("records.directory", "is not a valid path on this system");
        }
        if (value.isBlank() || value.contains("..") || path.isAbsolute()) {
            throw new ConfigurationException("records.directory", "must stay inside the plugin folder");
        }
        return value;
    }

    private static String file(ConfigurationSection configuration, String fallback) {
        var value = word(configuration, "storage.file", fallback);
        if (value.isBlank() || value.indexOf('/') >= 0 || value.indexOf('\\') >= 0 || value.contains("..")) {
            throw new ConfigurationException("storage.file", "must be a file name");
        }
        return value;
    }

    private static String word(ConfigurationSection configuration, String path, String fallback) {
        if (!configuration.contains(path)) {
            return fallback;
        }
        var raw = configuration.get(path);
        if (raw instanceof String text && !text.isBlank()) {
            return text.trim();
        }
        if (Boolean.FALSE.equals(raw)) {
            return "off";
        }
        if (Boolean.TRUE.equals(raw) && path.equals("decision.answerCache")) {
            return "on";
        }
        throw new ConfigurationException(path, "must be a string");
    }
}
