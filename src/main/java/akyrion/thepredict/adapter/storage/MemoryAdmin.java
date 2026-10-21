package akyrion.thepredict.adapter.storage;

import akyrion.thepredict.domain.MemoryDecoded;
import akyrion.thepredict.domain.PlayerMemoryCodec;
import akyrion.thepredict.engine.PlayerMemoryDelta;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.UUID;

/**
 * Administrator work on player memory (M27.9). The storage thread runs it.
 */
final class MemoryAdmin {
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(ZoneOffset.UTC);
    private static final long EXPORT_CAP = 512L * 1024L * 1024L;
    private static final int CHUNK = 1000;

    private MemoryAdmin() {
    }

    static String show(Connection read, String subject) throws SQLException {
        var loaded = PlayerMemorySql.load(read, UUID.fromString(subject));
        if (loaded.isEmpty()) {
            return "absent";
        }
        var decoded = PlayerMemoryCodec.decode(loaded.get());
        if (decoded instanceof MemoryDecoded.Ready ready) {
            return akyrion.thepredict.engine.MemoryInspect.loaded(ready.memory());
        }
        return decoded instanceof MemoryDecoded.Newer ? "newer" : "corrupt";
    }

    static String reset(Connection write, String subject) throws SQLException {
        PlayerMemorySql.apply(write, new PlayerMemoryDelta(UUID.fromString(subject), new byte[0], 0,
                System.currentTimeMillis(), true));
        return "reset";
    }

    static String purge(Connection write, int days) throws SQLException {
        var older = System.currentTimeMillis() - days * 86_400_000L;
        var removed = 0;
        int chunk;
        do {
            chunk = PlayerMemorySql.purge(write, older, CHUNK);
            removed += chunk;
        } while (chunk == CHUNK);
        return "purged " + removed;
    }

    static String export(Connection read, java.nio.file.Path records, String selector) throws SQLException, IOException {
        var directory = records.resolve("exports");
        Files.createDirectories(directory);
        var file = directory.resolve("memory-" + STAMP.format(Instant.now()) + ".jsonl");
        var written = 0L;
        var rows = 0;
        var filtered = selector != null && !selector.isBlank() && !selector.equalsIgnoreCase("all");
        var sql = "SELECT subject, record, updated_epoch_ms FROM m27_player_memory"
                + (filtered ? " WHERE subject = ?" : "") + " ORDER BY updated_epoch_ms";
        try (var statement = read.prepareStatement(sql)) {
            if (filtered) {
                statement.setBytes(1, PlayerMemorySql.bytes(UUID.fromString(selector)));
            }
            try (var result = statement.executeQuery();
             var writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            while (result.next()) {
                var line = line(result.getBytes(1), result.getLong(3), result.getBytes(2)) + "\n";
                if (written + line.length() > EXPORT_CAP) {
                    break;
                }
                writer.write(line);
                written += line.length();
                rows++;
            }
            }
        }
        return "exported " + rows + " " + file.getFileName();
    }

    static String importFile(Connection write, java.nio.file.Path records, String path, boolean replace)
            throws IOException, SQLException {
        var file = records.resolve("imports").resolve(path).normalize();
        if (!file.startsWith(records.resolve("imports").normalize())) {
            throw new IOException("path");
        }
        var lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        var applied = 0;
        var pending = 0;
        var rejected = new StringBuilder();
        var lineNo = 0;
        write.setAutoCommit(false);
        try {
            for (var line : lines) {
                lineNo++;
                if (line.isBlank()) {
                    continue;
                }
                try {
                    if (applyLine(write, line, replace)) {
                        applied++;
                        pending++;
                    }
                } catch (RuntimeException exception) {
                    rejected.append(" line ").append(lineNo).append(": ").append(cause(exception));
                }
                if (pending == CHUNK) {
                    write.commit();
                    pending = 0;
                }
            }
            write.commit();
        } catch (SQLException exception) {
            write.rollback();
            throw exception;
        } finally {
            write.setAutoCommit(true);
        }
        return "imported " + applied + (rejected.isEmpty() ? "" : " rejected" + rejected);
    }

    private static String cause(RuntimeException exception) {
        var message = exception.getMessage() == null ? "rejected" : exception.getMessage();
        if (message.contains("uuid") || message.contains("UUID")) {
            return "uuid";
        }
        if (message.contains("newer")) {
            return "newer";
        }
        if (message.contains("record") || message.contains("bytes")) {
            return "bytes";
        }
        return message;
    }

    private static boolean applyLine(Connection write, String line, boolean replace) throws SQLException {
        UUID subject;
        try {
            subject = UUID.fromString(field(line, "subject"));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("uuid");
        }
        if (line.contains("\"record_v\":") && Integer.parseInt(field(line, "record_v")) > 1) {
            throw new IllegalArgumentException("newer");
        }
        var updated = Long.parseLong(field(line, "updated_epoch_ms"));
        var record = Base64.getDecoder().decode(field(line, "record_b64"));
        if (record.length > 1024) {
            throw new IllegalArgumentException("bytes");
        }
        if (PlayerMemoryCodec.decode(record) instanceof MemoryDecoded.Newer) {
            throw new IllegalArgumentException("newer");
        }
        var stored = PlayerMemorySql.updatedMillis(write, subject);
        if (stored >= updated && !replace) {
            return false;
        }
        var fights = 0L;
        if (PlayerMemoryCodec.decode(record) instanceof MemoryDecoded.Ready ready) {
            fights = ready.memory().fights();
        }
        PlayerMemorySql.apply(write, new PlayerMemoryDelta(subject, record, fights, updated, false));
        return true;
    }

    private static String line(byte[] subject, long updated, byte[] record) {
        var id = uuid(subject);
        var decoded = PlayerMemoryCodec.decode(record);
        var summary = decoded instanceof MemoryDecoded.Ready ready
                ? akyrion.thepredict.engine.MemoryInspect.loaded(ready.memory()) : "unread";
        return "{\"v\":1,\"subject\":\"" + id + "\",\"record_v\":1,\"updated_epoch_ms\":" + updated
                + ",\"record_b64\":\"" + Base64.getEncoder().encodeToString(record) + "\",\"decoded\":\""
                + summary.replace("\"", "") + "\"}";
    }

    private static String field(String line, String name) {
        var key = "\"" + name + "\":";
        var start = line.indexOf(key);
        if (start < 0) {
            throw new IllegalArgumentException(name);
        }
        start += key.length();
        if (line.charAt(start) == '"') {
            var end = line.indexOf('"', start + 1);
            return line.substring(start + 1, end);
        }
        var end = line.indexOf(',', start);
        if (end < 0) {
            end = line.indexOf('}', start);
        }
        return line.substring(start, end);
    }

    private static UUID uuid(byte[] subject) {
        var buffer = java.nio.ByteBuffer.wrap(subject);
        return new UUID(buffer.getLong(), buffer.getLong());
    }
}
