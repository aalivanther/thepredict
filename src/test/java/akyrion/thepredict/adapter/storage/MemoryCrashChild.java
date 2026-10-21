package akyrion.thepredict.adapter.storage;

import akyrion.thepredict.domain.DifficultySection;
import akyrion.thepredict.domain.FightOutcome;
import akyrion.thepredict.domain.MemorySettings;
import akyrion.thepredict.domain.PartyBand;
import akyrion.thepredict.engine.AggregateBatch;
import akyrion.thepredict.engine.FightDigest;
import akyrion.thepredict.engine.MemoryFold;
import akyrion.thepredict.engine.PlayerMemoryDelta;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Child of {@link MemoryBatchTest}. It commits one memory batch, checkpoints, writes a marker and waits to be killed
 * before any reply can be read.
 */
public final class MemoryCrashChild {
    static final UUID SUBJECT = new UUID(27, 8);

    private MemoryCrashChild() {
    }

    /**
     * The batch the parent reapplies after the kill.
     *
     * @return the committed batch
     */
    static AggregateBatch batch() {
        var digest = new FightDigest(SUBJECT, 3, FightOutcome.WIN, PartyBand.ONE, DifficultySection.SOLO,
                Optional.empty(), 8L, false);
        var fold = new MemoryFold(List.of(digest), Optional.empty(), 1L, MemorySettings.DEFAULT);
        var delta = new PlayerMemoryDelta(SUBJECT, new byte[0], 0, 8L, false, Optional.of(fold));
        return new AggregateBatch(1, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(delta));
    }

    /**
     * Opens the database named by the arguments.
     *
     * @param arguments database path and marker path
     * @throws Exception when the database cannot be prepared
     */
    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 2) {
            System.out.println("usage");
            return;
        }
        var database = Path.of(arguments[0]);
        var marker = Path.of(arguments[1]);
        SchemaMigrator.open(database, SchemaMigrator.bundled(), "normal", 2000, "0.0.1", 1L,
                database.getParent().resolve("backups"));
        try (var connection = SqliteConnections.open(database, "normal", 2000)) {
            try (var statement = connection.prepareStatement(
                    "INSERT INTO runs(run, started_epoch_ms, clean_shutdown, plugin_version, model, config_sha256, sqlite_version) "
                            + "VALUES ('run', 1, 0, '0.0.1', 'jev-1.0.0', 'abc', '3.49.1')")) {
                statement.executeUpdate();
            }
            BatchApply.apply(connection, batch(), "run");
            try (var statement = connection.createStatement()) {
                statement.execute("PRAGMA wal_checkpoint(FULL)");
            }
        }
        Files.writeString(marker, "READY\n");
        System.out.println("READY");
        System.out.flush();
        while (true) {
            java.util.concurrent.locks.LockSupport.parkNanos(1_000_000_000L);
        }
    }
}