package akyrion.thepredict.engine.distill;

import akyrion.thepredict.domain.EnemyPattern;
import akyrion.thepredict.domain.EnemyPatternFact;

import java.util.Set;

/**
 * Pattern facts of M26.7 as a JSON array for {@code pattern_facts} (M29.4).
 */
public final class PatternFactsNote {
    private PatternFactsNote() {
    }

    /**
     * An array of pattern and fact objects. Empty when nothing was admitted.
     *
     * @param patterns admitted patterns
     * @return the array, or an empty string
     */
    public static String json(Set<EnemyPattern> patterns) {
        if (patterns.isEmpty()) {
            return "";
        }
        var body = new StringBuilder("[");
        var first = true;
        for (var pattern : patterns) {
            var phrase = EnemyPatternFact.phrase(pattern);
            if (phrase.isEmpty()) {
                continue;
            }
            if (!first) {
                body.append(',');
            }
            first = false;
            body.append("{\"pattern\":\"").append(escape(pattern.name())).append("\",\"fact\":\"")
                    .append(escape(phrase)).append("\"}");
        }
        if (first) {
            return "";
        }
        return body.append(']').toString();
    }

    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
