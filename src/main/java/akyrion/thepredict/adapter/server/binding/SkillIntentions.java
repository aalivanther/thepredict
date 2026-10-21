package akyrion.thepredict.adapter.server.binding;

import akyrion.thepredict.domain.CooldownClass;
import akyrion.thepredict.domain.DamageTier;
import akyrion.thepredict.domain.Delivery;
import akyrion.thepredict.domain.IntentionDraft;
import akyrion.thepredict.domain.IntentionParse;
import akyrion.thepredict.domain.MovementSettings;
import akyrion.thepredict.domain.NativeSkillFacts;
import akyrion.thepredict.domain.PurposeOrigin;
import akyrion.thepredict.domain.PurposeRules;
import akyrion.thepredict.domain.ReachMeasure;
import akyrion.thepredict.domain.SkillPurpose;
import akyrion.thepredict.domain.SkillRisk;
import akyrion.thepredict.domain.SkillRole;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Builds the purpose of origin {@code SKILL} from the {@code Intention} line of a skill and its native facts (M19.2,
 * M19.3). Stateless and pure; the binder calls it once per skill choice without a declared purpose.
 *
 * <p>The native facts fill what the line never says: the reach and its measure ({@code targetwithin} with
 * {@code POSITIONS_STRICT}, the {@code range} of {@code @EntitiesInCone} with {@code POSITIONS}, otherwise the
 * {@code MeleeReach} of the profile for a {@code MELEE} hit with {@code BOX_GAP}), the radius of an area, the wind-up,
 * and {@code ignoreshield=true} of a damage mechanic. Without a declared tier the damage is {@code UNRATED} when the
 * line has {@code DAMAGE} or {@code FINISHER} or the skill has a damaging mechanic, and {@code NONE} otherwise. A
 * {@code CONE} without range nor {@code targetwithin} and an {@code AREA_AROUND_SELF} without a radius targeter cannot
 * be completed: the line is then unusable and the choice stays untyped.
 */
final class SkillIntentions {
    private SkillIntentions() {
    }

    /**
     * The purpose of a usable line, or why it cannot be completed.
     *
     * @param purpose the purpose, with cooldown {@code UNKNOWN} until the binder reads the native {@code Cooldown}
     * @param problem why the line could not be completed, empty when the purpose is present
     */
    record Completion(Optional<SkillPurpose> purpose, Optional<String> problem) {
        Completion {
            Objects.requireNonNull(purpose, "purpose");
            Objects.requireNonNull(problem, "problem");
        }
    }

    /** Completes a parsed draft with the native facts of its skill. */
    static Completion complete(IntentionParse.Parsed parsed, NativeSkillFacts facts, MovementSettings movement) {
        var draft = parsed.draft();
        var delivery = draft.delivery();
        if (delivery == Delivery.CONE && facts.coneRangeBlocks().isEmpty() && facts.targetWithinBlocks().isEmpty()) {
            return failed("CONE needs @EntitiesInCone{range} or targetwithin{d}");
        }
        if (delivery == Delivery.AREA_AROUND_SELF && facts.radiusBlocks().isEmpty()) {
            return failed("AREA_AROUND_SELF needs a radius targeter such as @PlayersInRadius{r}");
        }
        var measure = measure(delivery, facts);
        var radius = switch (delivery) {
            case AREA_AROUND_SELF -> facts.radiusBlocks();
            case AREA_AT_TARGET -> facts.radiusBlocks().isPresent() ? facts.radiusBlocks()
                    : OptionalDouble.of(PurposeRules.DEFAULT_AREA_RADIUS_BLOCKS);
            default -> OptionalDouble.empty();
        };
        var ignoresShield = draft.ignoresShield() || facts.damageIgnoresShield()
                && !draft.risks().contains(SkillRisk.BLOCKED_BY_SHIELD);
        return new Completion(Optional.of(new SkillPurpose(draft.primary(), draft.roles(), delivery,
                reach(delivery, facts, movement), radius, facts.windUpOrZero(), CooldownClass.UNKNOWN,
                damage(draft, facts), draft.bestWhen(), draft.risks(), ignoresShield, PurposeOrigin.SKILL, measure)),
                Optional.empty());
    }

    private static Completion failed(String problem) {
        return new Completion(Optional.empty(), Optional.of(problem));
    }

    private static OptionalDouble reach(Delivery delivery, NativeSkillFacts facts, MovementSettings movement) {
        return switch (delivery) {
            case MELEE -> facts.targetWithinBlocks().isPresent() ? facts.targetWithinBlocks()
                    : OptionalDouble.of(Math.min(movement.meleeReachBlocks(), SkillPurpose.MAX_REACH_BLOCKS));
            case CONE -> facts.coneRangeBlocks().isPresent() ? facts.coneRangeBlocks() : facts.targetWithinBlocks();
            case PROJECTILE, HITSCAN, AREA_AT_TARGET -> facts.targetWithinBlocks();
            case SELF, AREA_AROUND_SELF -> OptionalDouble.empty();
        };
    }

    private static ReachMeasure measure(Delivery delivery, NativeSkillFacts facts) {
        return switch (delivery) {
            case MELEE, PROJECTILE, HITSCAN, AREA_AT_TARGET -> facts.targetWithinBlocks().isPresent()
                    ? ReachMeasure.POSITIONS_STRICT : ReachMeasure.defaultFor(delivery);
            case CONE -> facts.coneRangeBlocks().isPresent() ? ReachMeasure.POSITIONS : ReachMeasure.POSITIONS_STRICT;
            case SELF, AREA_AROUND_SELF -> ReachMeasure.defaultFor(delivery);
        };
    }

    private static DamageTier damage(IntentionDraft draft, NativeSkillFacts facts) {
        if (draft.declaredDamage().isPresent()) {
            return draft.declaredDamage().get();
        }
        var damaging = draft.roles().contains(SkillRole.DAMAGE) || draft.roles().contains(SkillRole.FINISHER)
                || facts.hasDamagingMechanic();
        return damaging ? DamageTier.UNRATED : DamageTier.NONE;
    }
}
