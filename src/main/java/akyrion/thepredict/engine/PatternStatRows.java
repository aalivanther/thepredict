package akyrion.thepredict.engine;

import akyrion.thepredict.domain.EnemyPattern;
import akyrion.thepredict.domain.PatternMode;
import akyrion.thepredict.domain.TargetKind;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The {@code m26_pattern_stats} rows of one closing encounter (M26.9, JS point 7), keyed as the table: profile,
 * pattern, target kind and mode. Every member profile whose mode observes saw the models of the encounter, so each
 * keeps the encounter (once) and the activations of the participants of that kind for every pattern with own trials;
 * the motivated casts of a member, and those a hit settled as landed, count under its profile and the kind of its
 * target. The counter effect of M26.10 needs the damage of each result window against the target's health, which the
 * events do not carry yet, so {@code counter_windows} stays 0 and says that no effect was measured. A participant the
 * encounter never aliased has no known kind and writes nothing. Built only at the close, never on the tick.
 */
final class PatternStatRows {
    private static final EnemyPattern[] PATTERNS = EnemyPattern.values();

    private PatternStatRows() {
    }

    /**
     * The rows of an encounter about to close, its members still attached.
     *
     * @param encounter the encounter
     * @param models its pattern models
     * @param epochMs wall time supplied by the adapter
     * @return the rows, empty when no observing member saw a trial
     */
    static List<PatternStatDelta> of(Encounter encounter, PatternModels models, long epochMs) {
        var rows = new LinkedHashMap<String, Row>();
        for (var slot = 0; slot < models.size(); slot++) {
            var alias = encounter.aliases().find(models.participant(slot));
            if (alias.isPresent()) {
                observed(encounter, models.model(slot), kind(alias.get().kind()), rows);
            }
        }
        for (var member : encounter.memberSessions()) {
            exploited(member, rows);
        }
        var built = new ArrayList<PatternStatDelta>(rows.size());
        for (var row : rows.values()) {
            built.add(row.delta(epochMs));
        }
        return List.copyOf(built);
    }

    private static void observed(Encounter encounter, EnemyModel model, String kind, Map<String, Row> rows) {
        for (var pattern : PATTERNS) {
            if (model.ownTrials(pattern) == 0) {
                continue;
            }
            for (var member : encounter.memberSessions()) {
                if (member.patternMode() == PatternMode.OFF) {
                    continue;
                }
                var row = row(rows, member, pattern, kind);
                row.encounters = 1;
                row.activations += model.activations(pattern);
            }
        }
    }

    private static void exploited(MobSession member, Map<String, Row> rows) {
        if (member.patternMode() == PatternMode.OFF) {
            return;
        }
        var kind = member.playerTarget() ? TargetKind.PLAYER.token() : TargetKind.ENTITY.token();
        for (var pattern : PATTERNS) {
            var casts = member.exploitCasts(pattern.ordinal());
            if (casts == 0) {
                continue;
            }
            var row = row(rows, member, pattern, kind);
            row.casts += casts;
            row.landed += member.exploitHits(pattern.ordinal());
        }
    }

    private static Row row(Map<String, Row> rows, MobSession member, EnemyPattern pattern, String kind) {
        var profile = member.profile().id();
        var mode = member.patternMode().name().toLowerCase(Locale.ROOT);
        var name = pattern.name().toLowerCase(Locale.ROOT);
        return rows.computeIfAbsent(profile + '\u0000' + name + '\u0000' + kind + '\u0000' + mode,
                key -> new Row(profile, name, kind, mode));
    }

    private static String kind(akyrion.thepredict.domain.ParticipantKind kind) {
        return kind == akyrion.thepredict.domain.ParticipantKind.PLAYER ? TargetKind.PLAYER.token()
                : TargetKind.ENTITY.token();
    }

    /** One key of the table being added up. */
    private static final class Row {
        private final String profile;
        private final String pattern;
        private final String kind;
        private final String mode;
        private long encounters;
        private long activations;
        private long casts;
        private long landed;

        private Row(String profile, String pattern, String kind, String mode) {
            this.profile = profile;
            this.pattern = pattern;
            this.kind = kind;
            this.mode = mode;
        }

        private PatternStatDelta delta(long epochMs) {
            return new PatternStatDelta(profile, pattern, kind, mode, encounters, activations, casts, landed, 0, 0.0,
                    0.0, epochMs, epochMs);
        }
    }
}
