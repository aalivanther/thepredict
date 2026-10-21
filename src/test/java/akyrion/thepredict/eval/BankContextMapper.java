package akyrion.thepredict.eval;

import akyrion.thepredict.domain.learn.ContextKey;
import akyrion.thepredict.domain.learn.GroupBand;
import akyrion.thepredict.domain.learn.HealthBand3;
import akyrion.thepredict.domain.learn.ZoneBand;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Maps bank scenario text onto context bands (M29.11). Facts that have no band are listed. Fewer than 40 mapped
 * scenarios out of 60 makes G2 inconclusive. Jev answers are not labels.
 */
public final class BankContextMapper {
    private BankContextMapper() {
    }

    /**
     * One scenario.
     *
     * @param id file name
     * @param key the key, empty when a required fact is missing
     * @param unmapped facts with no band
     */
    public record Mapped(String id, Optional<ContextKey> key, List<String> unmapped) {
    }

    /**
     * Reads up to 60 bank files.
     *
     * @param directory {@code scenarios/bank}
     * @return one row per file, at most 60
     * @throws IOException when a file cannot be read
     */
    public static List<Mapped> read(Path directory) throws IOException {
        var rows = new ArrayList<Mapped>();
        try (Stream<Path> files = Files.list(directory)) {
            var names = files.filter(path -> path.getFileName().toString().endsWith(".json")).sorted().toList();
            for (var path : names) {
                if (rows.size() == 60) {
                    break;
                }
                rows.add(map(path.getFileName().toString(), Files.readString(path)));
            }
        }
        return rows;
    }

    /**
     * G2 of the student.
     *
     * @param rows mapped rows
     * @return {@code INCONCLUSIVE} when fewer than 40 of 60 map
     */
    public static String verdict(List<Mapped> rows) {
        var mapped = 0;
        for (var row : rows) {
            if (row.key().isPresent()) {
                mapped++;
            }
        }
        return rows.size() >= 60 && mapped >= 40 ? "MAPPED" : "INCONCLUSIVE";
    }

    static Mapped map(String id, String text) {
        var unmapped = new ArrayList<String>();
        var distance = distance(text);
        var own = health(text, "mob_1");
        var target = health(text, "target");
        if (distance < 0) {
            unmapped.add("distance");
        }
        if (own == null) {
            unmapped.add("own-health");
        }
        if (target == null) {
            unmapped.add("target-health");
        }
        if (!unmapped.isEmpty()) {
            return new Mapped(id, Optional.empty(), unmapped);
        }
        var participants = participants(text);
        var key = ContextKey.of(distance, own, target, text.contains("target is player"), ZoneBand.OPEN,
                GroupBand.of(participants), 0);
        return new Mapped(id, Optional.of(key), List.of("zone"));
    }

    private static int distance(String text) {
        if (text.contains("inside melee reach")) {
            return 0;
        }
        if (text.contains("within ranged reach")) {
            return 1;
        }
        if (text.contains("beyond ranged reach") || text.contains("out of reach")) {
            return 2;
        }
        return -1;
    }

    private static HealthBand3 health(String text, String who) {
        if (text.contains(who + " is healthy") || text.contains(who + "'s health is healthy")) {
            return HealthBand3.HIGH;
        }
        if (text.contains(who + " is wounded") || text.contains(who + "'s health is wounded")) {
            return HealthBand3.MID;
        }
        if (text.contains(who + " is critical") || text.contains(who + "'s health is critical")
                || text.contains(who + " health is critical")) {
            return HealthBand3.LOW;
        }
        return null;
    }

    private static int participants(String text) {
        var marker = "\"participants\": ";
        var at = text.indexOf(marker);
        if (at < 0) {
            return 1;
        }
        var end = text.indexOf(',', at);
        return Integer.parseInt(text.substring(at + marker.length(), end).trim());
    }
}
