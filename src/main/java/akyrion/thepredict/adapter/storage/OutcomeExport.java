package akyrion.thepredict.adapter.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Writes {@code outcome_stats} as RFC 4180 CSV under {@code records/exports} (M21.9).
 */
public final class OutcomeExport {
    private OutcomeExport() {
    }

    /**
     * Exports one profile, or every profile when {@code profile} is empty.
     *
     * @param connection the read connection
     * @param records the records directory
     * @param profile the profile, or empty
     * @return a short result
     * @throws IOException when the file cannot be written
     * @throws SQLException when the query fails
     */
    public static String write(Connection connection, Path records, String profile) throws IOException, SQLException {
        var exports = records.resolve("exports");
        Files.createDirectories(exports);
        var name = profile == null || profile.isBlank() ? "outcomes-all.csv" : "outcomes-" + safe(profile) + ".csv";
        var target = exports.resolve(name);
        var sql = "SELECT profile, tactic, movement, level, context, windows, effect_mean, effect_m2, dealt_sum, "
                + "taken_sum, reached, mob_died, target_died, casts, hits, first_epoch_ms, last_epoch_ms "
                + "FROM outcome_stats";
        if (profile != null && !profile.isBlank()) {
            sql += " WHERE profile = ?";
        }
        var text = new StringBuilder();
        text.append("profile,tactic,movement,level,context,windows,effect_mean,effect_m2,dealt_sum,taken_sum,")
                .append("reached,mob_died,target_died,casts,hits,first_epoch_ms,last_epoch_ms\r\n");
        var rows = 0;
        try (var statement = connection.prepareStatement(sql)) {
            if (profile != null && !profile.isBlank()) {
                statement.setString(1, profile);
            }
            try (var result = statement.executeQuery()) {
                while (result.next()) {
                    for (var column = 1; column <= 17; column++) {
                        if (column > 1) {
                            text.append(',');
                        }
                        text.append(cell(result.getString(column)));
                    }
                    text.append("\r\n");
                    rows++;
                }
            }
        }
        Files.writeString(target, text.toString(), StandardCharsets.UTF_8);
        return "outcomes " + rows + " " + name;
    }

    private static String safe(String profile) {
        var cleaned = profile.replaceAll("[^A-Za-z0-9_-]", "_");
        return cleaned.isBlank() ? "profile" : cleaned;
    }

    private static String cell(String value) {
        var text = value == null ? "" : value;
        if (text.indexOf(',') < 0 && text.indexOf('"') < 0 && text.indexOf('\n') < 0 && text.indexOf('\r') < 0) {
            return text;
        }
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }
}
