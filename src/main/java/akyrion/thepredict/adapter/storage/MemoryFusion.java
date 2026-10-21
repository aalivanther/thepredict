package akyrion.thepredict.adapter.storage;

import akyrion.thepredict.domain.MemoryDecoded;
import akyrion.thepredict.domain.PlayerMemory;
import akyrion.thepredict.domain.PlayerMemoryCodec;
import akyrion.thepredict.engine.FightDigest;
import akyrion.thepredict.engine.MemoryCounters;
import akyrion.thepredict.engine.PlayerMemoryDelta;
import akyrion.thepredict.engine.PlayerMemoryUpdater;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;

/**
 * Merges digests into {@code m27_player_memory} inside the caller's transaction (M27.3). A corrupt or newer row is
 * left untouched.
 */
final class MemoryFusion {
    private MemoryFusion() {
    }

    static PlayerMemorySql.ApplyResult merge(Connection connection, PlayerMemoryDelta delta) throws SQLException {
        var subject = PlayerMemorySql.bytes(delta.subject());
        var stored = select(connection, subject);
        if (stored.present && (stored.recordV > 1 || skip(stored.record))) {
            return stored.recordV > 1 || PlayerMemoryCodec.newer(stored.record)
                    ? PlayerMemorySql.ApplyResult.SKIPPED_NEWER : PlayerMemorySql.ApplyResult.SKIPPED_CORRUPT;
        }
        var memory = stored.present ? ready(stored.record) : PlayerMemory.empty();
        var fold = delta.fold().orElseThrow();
        var fights = new ArrayList<FightDigest>(fold.fights());
        fights.sort(Comparator.comparingLong(FightDigest::endEpochMillis).thenComparingInt(FightDigest::profileHash));
        var next = PlayerMemoryUpdater.apply(memory, fights, fold.style(), fold.epochDay(), fold.settings(),
                new MemoryCounters());
        var encoded = PlayerMemoryCodec.encode(next, fold.settings().maxProfilesPerPlayer()).bytes();
        if (stored.present) {
            update(connection, subject, encoded, next.fights(), stored.rev + 1, delta.updatedEpochMs());
        } else {
            insert(connection, subject, encoded, next.fights(), delta.updatedEpochMs());
        }
        return PlayerMemorySql.ApplyResult.WROTE;
    }

    private static boolean skip(byte[] record) {
        var decoded = PlayerMemoryCodec.decode(record);
        return decoded instanceof MemoryDecoded.Corrupt || decoded instanceof MemoryDecoded.Newer;
    }

    private static PlayerMemory ready(byte[] record) {
        var decoded = PlayerMemoryCodec.decode(record);
        if (decoded instanceof MemoryDecoded.Ready ready) {
            return ready.memory();
        }
        return PlayerMemory.empty();
    }

    private static Stored select(Connection connection, byte[] subject) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT record, rev, record_v FROM m27_player_memory WHERE subject = ?")) {
            statement.setBytes(1, subject);
            try (var result = statement.executeQuery()) {
                if (!result.next()) {
                    return new Stored(new byte[0], 0, 1, false);
                }
                return new Stored(result.getBytes(1), result.getLong(2), result.getInt(3), true);
            }
        }
    }

    private static void insert(Connection connection, byte[] subject, byte[] record, long fights, long updated)
            throws SQLException {
        try (var statement = connection.prepareStatement(
                "INSERT INTO m27_player_memory(subject, record_v, record, fights, rev, first_epoch_ms, updated_epoch_ms) "
                        + "VALUES (?, 1, ?, ?, 1, ?, ?)")) {
            statement.setBytes(1, subject);
            statement.setBytes(2, record);
            statement.setLong(3, fights);
            statement.setLong(4, updated);
            statement.setLong(5, updated);
            statement.executeUpdate();
        }
    }

    private static void update(Connection connection, byte[] subject, byte[] record, long fights, long rev, long updated)
            throws SQLException {
        try (var statement = connection.prepareStatement(
                "UPDATE m27_player_memory SET record_v = 1, record = ?, fights = ?, rev = ?, updated_epoch_ms = ? "
                        + "WHERE subject = ?")) {
            statement.setBytes(1, record);
            statement.setLong(2, fights);
            statement.setLong(3, rev);
            statement.setLong(4, updated);
            statement.setBytes(5, subject);
            statement.executeUpdate();
        }
    }

    private record Stored(byte[] record, long rev, int recordV, boolean present) {
    }
}
