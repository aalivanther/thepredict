package akyrion.thepredict.adapter.storage;

import akyrion.thepredict.domain.MemoryDecoded;
import akyrion.thepredict.domain.PlayerMemoryCodec;
import akyrion.thepredict.engine.PlayerMemoryDelta;

import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

/**
 * The {@code m27_player_memory} table (M27.3). The codec owns the {@code record} bytes. This class only moves them.
 */
final class PlayerMemorySql {
    /** What one apply did to the row. */
    enum ApplyResult {
        /** The row was inserted or replaced. */
        WROTE,
        /** A newer record was left in place. */
        SKIPPED_NEWER,
        /** A corrupt record was left in place. */
        SKIPPED_CORRUPT,
        /** The row was deleted. */
        DELETED
    }

    private PlayerMemorySql() {
    }

    static ApplyResult apply(Connection connection, PlayerMemoryDelta delta) throws SQLException {
        if (delta.fold().isPresent()) {
            return MemoryFusion.merge(connection, delta);
        }
        var subject = bytes(delta.subject());
        if (delta.delete()) {
            try (var statement = connection.prepareStatement("DELETE FROM m27_player_memory WHERE subject = ?")) {
                statement.setBytes(1, subject);
                statement.executeUpdate();
            }
            return ApplyResult.DELETED;
        }
        var stored = stored(connection, subject);
        if (stored.present() && stored.recordV() > 1) {
            return ApplyResult.SKIPPED_NEWER;
        }
        if (stored.present() && blocked(stored.record())) {
            return PlayerMemoryCodec.newer(stored.record())
                    ? ApplyResult.SKIPPED_NEWER : ApplyResult.SKIPPED_CORRUPT;
        }
        if (!stored.present()) {
            insert(connection, subject, delta);
        } else {
            update(connection, subject, delta, stored.rev() + 1);
        }
        return ApplyResult.WROTE;
    }

    private static boolean blocked(byte[] record) {
        var decoded = PlayerMemoryCodec.decode(record);
        return decoded instanceof MemoryDecoded.Corrupt || decoded instanceof MemoryDecoded.Newer;
    }

    private static Stored stored(Connection connection, byte[] subject) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT rev, record, record_v FROM m27_player_memory WHERE subject = ?")) {
            statement.setBytes(1, subject);
            try (var result = statement.executeQuery()) {
                if (!result.next()) {
                    return new Stored(-1, new byte[0], 0);
                }
                return new Stored(result.getLong(1), result.getBytes(2), result.getInt(3));
            }
        }
    }

    private record Stored(long rev, byte[] record, int recordV) {
        boolean present() {
            return rev >= 0;
        }
    }

    static Optional<byte[]> load(Connection connection, UUID subject) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT record FROM m27_player_memory WHERE subject = ?")) {
            statement.setBytes(1, bytes(subject));
            try (var result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                return Optional.of(result.getBytes(1));
            }
        }
    }

    static int purge(Connection connection, long olderThanMs, int limit) throws SQLException {
        try (var statement = connection.prepareStatement(
                "DELETE FROM m27_player_memory WHERE rowid IN (SELECT rowid FROM m27_player_memory "
                        + "WHERE updated_epoch_ms < ? ORDER BY updated_epoch_ms LIMIT ?)")) {
            statement.setLong(1, olderThanMs);
            statement.setInt(2, limit);
            return statement.executeUpdate();
        }
    }

    static long updatedMillis(Connection connection, UUID subject) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT updated_epoch_ms FROM m27_player_memory WHERE subject = ?")) {
            statement.setBytes(1, bytes(subject));
            try (var result = statement.executeQuery()) {
                return result.next() ? result.getLong(1) : -1L;
            }
        }
    }

    static long count(Connection connection) throws SQLException {
        try (var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT COUNT(*) FROM m27_player_memory")) {
            return result.next() ? result.getLong(1) : 0L;
        }
    }

    private static void insert(Connection connection, byte[] subject, PlayerMemoryDelta delta) throws SQLException {
        try (var statement = connection.prepareStatement(
                "INSERT INTO m27_player_memory(subject, record_v, record, fights, rev, first_epoch_ms, updated_epoch_ms) "
                        + "VALUES (?, 1, ?, ?, 1, ?, ?)")) {
            statement.setBytes(1, subject);
            statement.setBytes(2, delta.record());
            statement.setLong(3, delta.fights());
            statement.setLong(4, delta.updatedEpochMs());
            statement.setLong(5, delta.updatedEpochMs());
            statement.executeUpdate();
        }
    }

    private static void update(Connection connection, byte[] subject, PlayerMemoryDelta delta, long rev)
            throws SQLException {
        try (var statement = connection.prepareStatement(
                "UPDATE m27_player_memory SET record_v = 1, record = ?, fights = ?, rev = ?, updated_epoch_ms = ? "
                        + "WHERE subject = ?")) {
            statement.setBytes(1, delta.record());
            statement.setLong(2, delta.fights());
            statement.setLong(3, rev);
            statement.setLong(4, delta.updatedEpochMs());
            statement.setBytes(5, subject);
            statement.executeUpdate();
        }
    }

    static byte[] bytes(UUID subject) {
        var buffer = ByteBuffer.allocate(16);
        buffer.putLong(subject.getMostSignificantBits());
        buffer.putLong(subject.getLeastSignificantBits());
        return buffer.array();
    }
}
