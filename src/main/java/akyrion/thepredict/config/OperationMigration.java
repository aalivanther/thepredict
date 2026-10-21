package akyrion.thepredict.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Converts schema 1 {@code Operations} into schema 2 contexts (M03.6): each operation becomes the equivalent
 * shortcut context, earlier operations get higher priorities because schema 1 used the first match, and
 * {@code DirectChoice} becomes {@code DirectTactic}. Repeated or unknown behaviors are left for manual correction.
 */
final class OperationMigration {
    private static final Map<String, String> SHORTCUTS = Map.of("IDLE", "idle", "ATTACKING", "attacking_phase",
            "ATTACKINGPHASE", "attacking_phase", "LOWHEALTH", "low_health_phase", "LOWHEALTHPHASE", "low_health_phase",
            "TRACKING", "tracking_enemy", "TRACKINGENEMY", "tracking_enemy");

    private OperationMigration() {
    }

    /** Converts the operations of one profile into contexts, appending them to the profile text. */
    static void convert(String where, ConfigurationSection profile, Map<String, String> tactics, StringBuilder text,
                        MigrationNotes notes) {
        if (profile.contains("Behaviors")) {
            notes.decision(where + ".Behaviors was dropped: in schema 2 only declared contexts restrict tactics");
        }
        var operations = profile.getMapList("Operations");
        if (operations.isEmpty()) {
            return;
        }
        if (!profile.getBoolean("Combinators", false)) {
            notes.manual(where + ".Operations is ignored because Combinators is not true; add Contexts by hand");
            return;
        }
        var contexts = new StringBuilder("  Contexts:\n");
        var used = new HashSet<String>();
        for (int index = 0; index < operations.size(); index++) {
            var context = convertOperation(where + ".Operations[" + index + "]", operations.get(index), tactics, used,
                    Math.max(0, 100 - 10 * index), notes);
            contexts.append(context);
        }
        if (!used.isEmpty()) {
            text.append(contexts);
        }
    }

    private static String convertOperation(String where, Map<?, ?> operation, Map<String, String> tactics,
                                           Set<String> used, int priority, MigrationNotes notes) {
        var behavior = String.valueOf(operation.get("Behavior")).replace("_", "").replace("-", "")
                .toUpperCase(Locale.ROOT);
        var context = SHORTCUTS.get(behavior);
        if (context == null || !used.add(context)) {
            notes.manual(where + " (Behavior " + operation.get("Behavior") + ") is unknown or repeats a context; "
                    + "correct it by hand");
            return "";
        }
        var direct = operation.get("DirectChoice");
        var mapped = mapTactics(where, operation, tactics, notes);
        if (mapped.isEmpty()) {
            used.remove(context);
            return "";
        }
        var text = new StringBuilder("    ").append(context).append(":\n      Priority: ").append(priority)
                .append("\n      Tactics: ").append(YamlText.identifiers(mapped)).append('\n');
        if (direct instanceof String) {
            text.append("      DirectTactic: ").append(mapped.getFirst()).append('\n');
        }
        notes.decision(where + " became context " + context + " with Priority " + priority
                + (direct instanceof String ? " and DirectTactic " + mapped.getFirst() : ""));
        return text.toString();
    }

    private static List<String> mapTactics(String where, Map<?, ?> operation, Map<String, String> tactics,
                                           MigrationNotes notes) {
        var direct = operation.get("DirectChoice");
        var listed = direct instanceof String name ? List.of(name) : names(operation.get("Choices"));
        if (listed.isEmpty() || operation.containsKey("DirectChoice") && operation.containsKey("Choices")) {
            notes.manual(where + " needs exactly one of Choices or DirectChoice; correct it by hand");
            return List.of();
        }
        var mapped = new ArrayList<String>();
        for (var name : listed) {
            if (!tactics.containsKey(name)) {
                notes.manual(where + " references the unknown choice '" + name + "'; correct it by hand");
                return List.of();
            }
            mapped.add(tactics.get(name));
        }
        return mapped;
    }

    private static List<String> names(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(String::valueOf).toList();
    }
}
