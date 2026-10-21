package akyrion.thepredict.adapter.server.goals;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Reads {@code AIGoalSelectors} lines as MythicMobs 5.13.1 does: an optional leading priority, then the goal
 * with its arguments. Pure; package-private helper of {@link BaseAiResolver} and {@link GoalLineGenerator}.
 */
final class BaseAiLines {
    private static final Pattern PRIORITY = Pattern.compile("[0-9]{1,6}");
    private static final Pattern NUMBER = Pattern.compile("[+-]?[0-9]+");

    private BaseAiLines() {
    }

    /**
     * One line split into priority and goal text.
     *
     * @param priority explicit or assigned priority
     * @param goal goal name with its arguments, not blank
     */
    record Line(int priority, String goal) {
    }

    /**
     * Assigns a priority to every readable line as MythicMobs 5.13.1 does (A1): a leading number is the
     * priority, and a line without one gets its position in the list, counted from 1 over every entry
     * ({@code addPathfinderGoals}, javap; DT-16). The generator then always writes explicit priorities.
     * Priorities have at most six digits; a line with a longer or signed number, a blank line and a number
     * without a goal are left out.
     *
     * @param lines raw lines
     * @return readable lines with priorities, in their original order
     */
    static List<Line> parse(List<String> lines) {
        var parsed = new ArrayList<Line>(lines.size());
        for (var index = 0; index < lines.size(); index++) {
            var text = lines.get(index).strip();
            var space = firstSpace(text);
            var head = space < 0 ? text : text.substring(0, space);
            var goal = space < 0 ? "" : text.substring(space).strip();
            if (PRIORITY.matcher(head).matches()) {
                if (!goal.isEmpty()) {
                    parsed.add(new Line(Integer.parseInt(head), goal));
                }
            } else if (!text.isEmpty() && !NUMBER.matcher(head).matches()) {
                parsed.add(new Line(index + 1, text));
            }
        }
        return parsed;
    }

    /**
     * Tells whether a line is {@code clear}, with or without priority and in any case, as MythicMobs'
     * {@code MobType.isClearGoalSelector} does.
     *
     * @param line raw line
     * @return {@code true} for a {@code clear} line
     */
    static boolean isClear(String line) {
        return hasGoal(line, "clear");
    }

    /**
     * Tells whether a line installs the goal with the given name, compared without case.
     *
     * @param line raw line
     * @param goalName MythicMobs goal name, for example {@code lookAtTarget}
     * @return {@code true} if the goal name before any argument block matches
     */
    static boolean hasGoal(String line, String goalName) {
        var parsed = parse(List.of(line));
        if (parsed.isEmpty()) {
            return false;
        }
        var goal = parsed.getFirst().goal();
        var brace = goal.indexOf('{');
        var name = brace < 0 ? goal : goal.substring(0, brace);
        return name.strip().toLowerCase(Locale.ROOT).equals(goalName.toLowerCase(Locale.ROOT));
    }

    private static int firstSpace(String text) {
        for (var index = 0; index < text.length(); index++) {
            if (Character.isWhitespace(text.charAt(index))) {
                return index;
            }
        }
        return -1;
    }
}
