package akyrion.thepredict.domain;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * What a valid {@code Intention} line says about a skill, before the binder completes it with the native metadata of
 * MythicMobs into a {@link SkillPurpose} with origin {@code SKILL} (M19.1). It has no reach, radius, wind-up nor
 * cooldown: the line never carries numbers (user decision 6). Immutable.
 *
 * <p>Invariants: {@code roles} holds {@code primary} and has 1 to {@value IntentionVocabulary#MAX_ROLES} entries;
 * {@code declaredDamage}, when present, is {@code LOW}, {@code MEDIUM} or {@code HIGH}; at most
 * {@value SkillPurpose#MAX_BEST_WHEN} conditions and {@value SkillPurpose#MAX_RISKS} risks; {@code ignoresShield}
 * excludes {@code BLOCKED_BY_SHIELD}.
 *
 * @param primary primary role, the first role of the line
 * @param roles every role, including {@code primary}
 * @param delivery how the skill reaches its target
 * @param declaredDamage damage tier written in the line; empty without a {@code DAMAGE_} primitive, which the binder
 *        resolves to {@code UNRATED} or {@code NONE}
 * @param bestWhen conditions in which the skill works best
 * @param risks drawbacks of the skill
 * @param ignoresShield whether the line says {@code IGNORES_SHIELD}
 */
public record IntentionDraft(SkillRole primary, Set<SkillRole> roles, Delivery delivery,
                             Optional<DamageTier> declaredDamage, Set<UseCondition> bestWhen, Set<SkillRisk> risks,
                             boolean ignoresShield) {
    /**
     * Validates the draft and copies the sets.
     *
     * @throws NullPointerException if a component or element is {@code null}
     * @throws IllegalArgumentException if an invariant does not hold
     */
    public IntentionDraft {
        Objects.requireNonNull(primary, "primary");
        roles = Set.copyOf(Objects.requireNonNull(roles, "roles"));
        Objects.requireNonNull(delivery, "delivery");
        Objects.requireNonNull(declaredDamage, "declaredDamage");
        bestWhen = Set.copyOf(Objects.requireNonNull(bestWhen, "bestWhen"));
        risks = Set.copyOf(Objects.requireNonNull(risks, "risks"));
        if (!roles.contains(primary) || roles.size() > IntentionVocabulary.MAX_ROLES) {
            throw new IllegalArgumentException("roles must hold the primary role and at most "
                    + IntentionVocabulary.MAX_ROLES + " entries");
        }
        if (declaredDamage.filter(tier -> tier == DamageTier.NONE || tier == DamageTier.UNRATED).isPresent()) {
            throw new IllegalArgumentException("declaredDamage must be LOW, MEDIUM or HIGH");
        }
        DomainChecks.requireMaximumSize(bestWhen, SkillPurpose.MAX_BEST_WHEN, "bestWhen");
        DomainChecks.requireMaximumSize(risks, SkillPurpose.MAX_RISKS, "risks");
        if (ignoresShield && risks.contains(SkillRisk.BLOCKED_BY_SHIELD)) {
            throw new IllegalArgumentException("ignoresShield cannot be true while risks has BLOCKED_BY_SHIELD");
        }
    }

    /**
     * The roles in canonical order: {@code primary} first, then the others in the order of {@link SkillRole}.
     *
     * @return a new ordered set
     */
    public Set<SkillRole> orderedRoles() {
        var ordered = new LinkedHashSet<SkillRole>();
        ordered.add(primary);
        ordered.addAll(EnumSet.copyOf(roles));
        return ordered;
    }
}
