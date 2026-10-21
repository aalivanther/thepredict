package akyrion.thepredict.admin;

import akyrion.thepredict.domain.IntentionVocabulary;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.TacticalProfile;
import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Prints {@code /thepredict intention suggest} (M19.8). The canonical line and the facts that do not fit it come from
 * {@link IntentionVocabulary}; this class only lays them out. It never reads or writes a MythicMobs file (rules D6
 * and C24).
 */
public final class IntentionFormatter {
    /** Usage line of the subcommand. */
    static final String USAGE = "Usage: /thepredict intention suggest [profile]";

    /** Creates the formatter; it holds no state. */
    public IntentionFormatter() {
    }

    /**
     * Formats the suggestion for the arguments of {@code intention}.
     *
     * @param arguments words after {@code intention}; {@code suggest} and, optionally, one profile id
     * @param profiles bound profiles, in any order
     * @return the lines to send; a wrong invocation is a single error line
     */
    public List<Component> format(List<String> arguments, Collection<TacticalProfile> profiles) {
        Objects.requireNonNull(arguments, "arguments");
        Objects.requireNonNull(profiles, "profiles");
        if (arguments.size() > 2 || arguments.isEmpty() || !arguments.getFirst().equalsIgnoreCase("suggest")) {
            return List.of(AdminText.error(usage(arguments)));
        }
        var ordered = ordered(profiles);
        return arguments.size() == 2 ? one(arguments.get(1), ordered) : all(ordered);
    }

    /**
     * Profile ids for tab completion, in alphabetical order and without duplicates.
     *
     * @param profiles bound profiles
     * @return the ids
     */
    public List<String> profileIds(Collection<TacticalProfile> profiles) {
        return Objects.requireNonNull(profiles, "profiles").stream().map(TacticalProfile::id).distinct().sorted()
                .toList();
    }

    private static String usage(List<String> arguments) {
        if (arguments.isEmpty() || arguments.getFirst().equalsIgnoreCase("suggest")) {
            return USAGE;
        }
        return "Unknown intention action '" + AdminText.echo(arguments.getFirst()) + "'. " + USAGE;
    }

    private static List<TacticalProfile> ordered(Collection<TacticalProfile> profiles) {
        var ordered = new ArrayList<>(profiles);
        ordered.sort(Comparator.comparing(TacticalProfile::id));
        return ordered;
    }

    private static List<Component> all(List<TacticalProfile> profiles) {
        if (profiles.isEmpty()) {
            return List.of(AdminText.info("No bound profile."));
        }
        return lines(profiles);
    }

    private static List<Component> one(String name, List<TacticalProfile> profiles) {
        var matched = profiles.stream().filter(profile -> profile.id().equals(name)).toList();
        if (matched.isEmpty()) {
            return List.of(AdminText.error("No bound profile '" + AdminText.echo(name) + "'."));
        }
        return lines(matched);
    }

    private static List<Component> lines(List<TacticalProfile> profiles) {
        var lines = new ArrayList<Component>();
        for (var profile : profiles) {
            lines.add(AdminText.header(profile.id()));
            profile.choices().values().forEach(choice -> lines.addAll(choiceLines(choice)));
        }
        return List.copyOf(lines);
    }

    private static List<Component> choiceLines(SkillChoice choice) {
        if (!choice.isTyped()) {
            return List.of(AdminText.line(choice.id().value(), "untyped; no Intention line"));
        }
        var purpose = choice.typedPurpose();
        var draft = IntentionVocabulary.draftOf(purpose);
        var tokens = choice.telegraph().answer().map(answer -> IntentionVocabulary.render(draft, answer))
                .orElseGet(() -> IntentionVocabulary.render(draft));
        var lines = new ArrayList<Component>();
        lines.add(AdminText.line(choice.id().value(), IntentionVocabulary.text(tokens)));
        IntentionVocabulary.omitted(purpose).forEach(note -> lines.add(AdminText.problem(choice.id().value(), note)));
        return lines;
    }
}
