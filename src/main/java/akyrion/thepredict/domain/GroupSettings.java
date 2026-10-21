package akyrion.thepredict.domain;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The {@code Group} section of one profile (M25). An absent section inherits. A mode above the global mode is
 * lowered by the loader.
 *
 * @param mode requested mode, empty to inherit
 * @param squad squad name, empty when the profile names none
 * @param leaderPriority priority 0 to 100
 * @param weight weight 1 to 4
 * @param roles roles the author allows, empty for every role the profile can fill
 * @param onLeaderDeath what happens when the leader dies
 * @param againstEntities fairness against entities
 * @param questions question set, empty for the mode default
 * @param planQuestion when {@code g0} is attached
 * @param capabilities aptitudes computed at load
 */
public record GroupSettings(Optional<GroupMode> mode, String squad, int leaderPriority, int weight,
                            Set<GroupRole> roles, LeaderDeath onLeaderDeath, BenchPolicy againstEntities,
                            Optional<GroupQuestions> questions, PlanQuestion planQuestion,
                            RoleCapabilities capabilities) {
    private static final Pattern SQUAD = Pattern.compile("[a-z][a-z0-9_]{0,39}");

    /** A profile that does not declare {@code Group}. */
    public static final GroupSettings DEFAULT = new GroupSettings(Optional.empty(), "", 0, 1, Set.of(),
            LeaderDeath.SUCCEED, BenchPolicy.FREE, Optional.empty(), PlanQuestion.ON_CHANGE, RoleCapabilities.NONE);

    /**
     * Checks the section.
     *
     * @throws NullPointerException if a component is {@code null}; an absent mode, squad or question set is empty
     * @throws IllegalArgumentException when a field is outside its range or a role has no capacity
     */
    public GroupSettings {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(squad, "squad");
        if (!squad.isEmpty() && !SQUAD.matcher(squad).matches()) {
            throw new IllegalArgumentException("Group.Squad must match [a-z][a-z0-9_]{0,39}");
        }
        if (leaderPriority < 0 || leaderPriority > 100) {
            throw new IllegalArgumentException("Group.LeaderPriority must be within [0, 100], was " + leaderPriority);
        }
        if (weight < 1 || weight > 4) {
            throw new IllegalArgumentException("Group.Weight must be within [1, 4], was " + weight);
        }
        roles = Set.copyOf(Objects.requireNonNull(roles, "roles"));
        Objects.requireNonNull(onLeaderDeath, "onLeaderDeath");
        Objects.requireNonNull(againstEntities, "againstEntities");
        Objects.requireNonNull(questions, "questions");
        Objects.requireNonNull(planQuestion, "planQuestion");
        Objects.requireNonNull(capabilities, "capabilities");
        for (var role : roles) {
            if (!capabilities.allows(role)) {
                throw new IllegalArgumentException("Group.Roles names " + role + " but the profile has no capacity");
            }
        }
    }

    /**
     * Whether the author asked for more than the global mode.
     *
     * @param global the global mode
     * @return whether the requested mode ranks higher
     */
    public boolean above(GroupMode global) {
        return mode.filter(asked -> asked.rank() > global.rank()).isPresent();
    }

    /**
     * The mode this profile may use.
     *
     * @param global the global mode
     * @return the minimum of the two, or the global mode when the profile inherits
     */
    public GroupMode effective(GroupMode global) {
        return mode.map(global::min).orElse(global);
    }

    /**
     * Whether the author wrote a group section that is not the delivered inheritance.
     *
     * @return whether any field was set
     */
    public boolean authored() {
        return mode.isPresent() || !squad.isEmpty() || leaderPriority != 0 || weight != 1 || !roles.isEmpty()
                || onLeaderDeath != LeaderDeath.SUCCEED || againstEntities != BenchPolicy.FREE
                || questions.isPresent() || planQuestion != PlanQuestion.ON_CHANGE;
    }

    /**
     * The question set after the mode default.
     *
     * @param effective the effective mode
     * @param leader whether a leader exists
     * @param members member count
     * @return the set
     */
    public GroupQuestions resolvedQuestions(GroupMode effective, boolean leader, int members) {
        if (members < 2) {
            return GroupQuestions.MEMBERS;
        }
        return questions.orElseGet(() -> GroupQuestions.delivered(effective, leader, members));
    }
}
