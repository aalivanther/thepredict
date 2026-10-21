package akyrion.thepredict.adapter.server.binding;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Heuristic that flags author text that probably is not English, because Jev reads English. It only ever
 * produces a warning: false alarms are possible (M03.4).
 */
public final class EnglishTextHint {
    private static final Pattern WORD = Pattern.compile("[\\p{L}']+");
    private static final Set<String> FOREIGN_WORDS = Set.of("el", "los", "las", "del", "que", "para", "con", "una",
            "por", "mientras", "objetivo", "enemigo", "sobrevivir", "atacar", "und", "der", "das", "mit", "nicht",
            "gegen", "les", "des", "pour", "avec", "ennemi", "che", "della", "nemico", "uma", "com", "inimigo");

    private EnglishTextHint() {
    }

    /**
     * Explains why a text may not be English: characters outside ASCII or a frequent word of another language.
     *
     * @param text author text such as {@code Objective} or a tactic {@code Description}
     * @return the reason, or empty when the text looks like English
     */
    public static Optional<String> reason(String text) {
        Objects.requireNonNull(text, "text");
        for (int index = 0; index < text.length(); index++) {
            if (text.charAt(index) > 127) {
                return Optional.of("contains the non-ASCII character '" + text.charAt(index) + "'");
            }
        }
        var words = WORD.matcher(text.toLowerCase(Locale.ROOT));
        while (words.find()) {
            if (FOREIGN_WORDS.contains(words.group())) {
                return Optional.of("contains the word '" + words.group() + "', which is common in other languages");
            }
        }
        return Optional.empty();
    }
}
