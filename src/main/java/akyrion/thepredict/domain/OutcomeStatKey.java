package akyrion.thepredict.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Key of one {@link OutcomeStat} row (M21.5). {@code context} is {@code v1:<start_band>:<target_kind>}. M28 may widen
 * the pattern later; this release accepts only {@code v\d+:[A-Z0-9_:]+}.
 *
 * @param profile profile id
 * @param tactic tactic of the window
 * @param movement movement of the window
 * @param level control level of the window
 * @param context versioned context, at most 64 characters
 */
public record OutcomeStatKey(String profile, TacticId tactic, Movement movement, ControlLevel level, String context) {
    private static final Pattern CONTEXT = Pattern.compile("v\\d+:[A-Z0-9_:]+");

    /**
     * Validates the key.
     *
     * @throws NullPointerException if a component is {@code null}
     * @throws IllegalArgumentException if the profile is blank or the context does not match
     */
    public OutcomeStatKey {
        DomainChecks.requireText(profile, "profile");
        Objects.requireNonNull(tactic, "tactic");
        Objects.requireNonNull(movement, "movement");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(context, "context");
        if (context.length() > 64 || !CONTEXT.matcher(context).matches()) {
            throw new IllegalArgumentException("context must match v<digits>:<A-Z0-9_:>, was " + context);
        }
    }

    /**
     * Builds the v1 context. An absent band or kind is {@code NONE}.
     *
     * @param startBand band at the start of the window, or empty
     * @param targetKind kind of the judged target, or empty
     * @return {@code v1:<band>:<kind>}
     */
    public static String context(String startBand, String targetKind) {
        var band = startBand == null || startBand.isBlank() ? "NONE" : startBand;
        var kind = targetKind == null || targetKind.isBlank() ? "NONE" : targetKind;
        return "v1:" + band + ":" + kind;
    }
}
