package akyrion.thepredict.engine;

import akyrion.thepredict.domain.EnemyAction;
import akyrion.thepredict.domain.EnemyPattern;
import akyrion.thepredict.domain.PatternGroup;
import akyrion.thepredict.domain.PatternRules;

/**
 * Occasions of the pattern table (M26.3): the tokens on which a pattern could have shown itself. A token inside the
 * family of a pattern is one Bernoulli trial of that pattern; the success is the pattern's own occasion
 * ({@link PatternRules#matches}, or the rhythm rule for spears) and any other token of the family is the failure. A
 * token outside the family is no trial at all, so a strike never dilutes the evidence of a strafe habit and a strafe
 * never feeds a shield habit.
 *
 * <p>The family of a group is the union of the own occasions of its beta patterns. The rhythm family also holds the
 * plain and the sprint hit, which are the ordinary negatives of clicking early, jumping for a critical and w-tapping.
 * {@link EnemyPattern#FIXED_CADENCE} has no family: it is not a beta detector.
 */
final class EnemyOccasions {
    private static final int[] FAMILIES = families();

    private EnemyOccasions() {
    }

    /**
     * Whether a token is a trial of a pattern, successful or not.
     *
     * @param pattern the pattern
     * @param action the token
     * @return {@code true} when the token belongs to the family of the pattern's group
     */
    static boolean trial(EnemyPattern pattern, EnemyAction action) {
        return pattern.usesBeta() && (FAMILIES[pattern.group().ordinal()] & bit(action)) != 0;
    }

    private static int[] families() {
        var families = new int[PatternGroup.values().length];
        for (var pattern : EnemyPattern.values()) {
            if (pattern.usesBeta()) {
                families[pattern.group().ordinal()] |= bit(PatternRules.occasion(pattern));
            }
        }
        families[PatternGroup.RHYTHM.ordinal()] |= bit(EnemyAction.STRIKE) | bit(EnemyAction.SPRINT_STRIKE);
        return families;
    }

    private static int bit(EnemyAction action) {
        return 1 << action.ordinal();
    }
}
