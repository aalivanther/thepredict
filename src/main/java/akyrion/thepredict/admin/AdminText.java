package akyrion.thepredict.admin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Locale;
import java.util.OptionalLong;
import java.util.UUID;

/** Coloured lines of the administrative command with Adventure (rule D6). Package-private; pure. */
final class AdminText {
    /** Prefix of every line. */
    static final String PREFIX = "[ThePredict] ";

    private AdminText() {
    }

    /** A section title. */
    static Component header(String title) {
        return Component.text(PREFIX, NamedTextColor.DARK_AQUA).append(Component.text(title, NamedTextColor.GOLD));
    }

    /** A labelled value. */
    static Component line(String label, String value) {
        return Component.text(PREFIX, NamedTextColor.DARK_AQUA).append(Component.text(label + ": ", NamedTextColor.GRAY))
                .append(Component.text(value, NamedTextColor.WHITE));
    }

    /** A labelled value that needs attention. */
    static Component problem(String label, String value) {
        return Component.text(PREFIX, NamedTextColor.DARK_AQUA).append(Component.text(label + ": ", NamedTextColor.GRAY))
                .append(Component.text(value, NamedTextColor.RED));
    }

    /** A plain message. */
    static Component info(String text) {
        return Component.text(PREFIX, NamedTextColor.DARK_AQUA).append(Component.text(text, NamedTextColor.WHITE));
    }

    /** A refusal or a problem. */
    static Component error(String text) {
        return Component.text(PREFIX, NamedTextColor.DARK_AQUA).append(Component.text(text, NamedTextColor.RED));
    }

    /** Most characters of operator input repeated in a message. */
    static final int MAX_ECHO = 40;

    /** Operator input as it may be repeated in a message: at most {@link #MAX_ECHO} characters, then an ellipsis. */
    static String echo(String input) {
        return input.length() <= MAX_ECHO ? input : input.substring(0, MAX_ECHO) + "…";
    }

    /** The first eight characters of a UUID, enough to tell mobs apart on screen (rule I1). */
    static String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }

    /** A decimal with two digits and a dot, whatever the server locale. */
    static String decimal(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    /** Microseconds of a percentile: a dash without samples, above two seconds for the last bucket. */
    static String micros(OptionalLong value) {
        if (value.isEmpty()) {
            return "-";
        }
        return value.getAsLong() == Long.MAX_VALUE ? ">2 s" : value.getAsLong() + " us";
    }

    /** Whole seconds from ticks at 20 ticks per second. */
    static long seconds(long ticks) {
        return ticks / 20;
    }
}
