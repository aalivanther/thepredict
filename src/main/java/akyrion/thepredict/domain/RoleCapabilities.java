package akyrion.thepredict.domain;

import java.util.Map;

/**
 * Aptitude of a profile for each role, computed when the profile loads (M25). Zero means no capacity.
 * {@link GroupRole#RESERVE} and {@link GroupRole#GUARD} always have capacity.
 *
 * @param pressure melee damage that does not hit allies
 * @param flank melee
 * @param ranged a ranged delivery
 * @param support a skill that can target allies
 * @param control a control skill
 * @param guard always available
 */
public record RoleCapabilities(int pressure, int flank, int ranged, int support, int control, int guard) {
    /** A profile with no readable combat skills. Guard and reserve still exist. */
    public static final RoleCapabilities NONE = new RoleCapabilities(0, 0, 0, 0, 0, 400);

    /**
     * Aptitudes from the choices and tactics already loaded. Guard stays available. A child of the leader adds its
     * bonus later, when the encounter knows the parent.
     *
     * @param choices choices in declaration order
     * @param tactics tactics in declaration order
     * @return the aptitudes
     */
    public static RoleCapabilities from(Map<ChoiceId, SkillChoice> choices, Map<TacticId, Tactic> tactics) {
        var approach = false;
        var flankMove = false;
        var keepOrHold = false;
        var gapClose = false;
        for (var tactic : tactics.values()) {
            switch (tactic.movement()) {
                case APPROACH -> approach = true;
                case FLANK -> flankMove = true;
                case KEEP_DISTANCE, HOLD -> keepOrHold = true;
                default -> {
                }
            }
        }
        var meleeDamage = false;
        var meleeHeavy = false;
        var rangedDamage = false;
        var support = false;
        var control = false;
        for (var choice : choices.values()) {
            if (choice.kind() == ChoiceKind.VANILLA_MELEE) {
                meleeDamage = true;
            }
            if (!choice.isTyped()) {
                continue;
            }
            var purpose = choice.typedPurpose();
            var damaging = purpose.damage() != DamageTier.NONE;
            if (damaging && purpose.damage().ordinal() >= DamageTier.MEDIUM.ordinal()
                    && (purpose.delivery() == Delivery.MELEE || purpose.delivery() == Delivery.CONE
                    || choice.kind() == ChoiceKind.VANILLA_MELEE)) {
                meleeHeavy = true;
            }
            if (damaging && (purpose.delivery() == Delivery.MELEE || purpose.delivery() == Delivery.CONE)) {
                meleeDamage = true;
            }
            if (damaging && (purpose.delivery() == Delivery.PROJECTILE || purpose.delivery() == Delivery.HITSCAN
                    || purpose.delivery() == Delivery.AREA_AT_TARGET)) {
                rangedDamage = true;
            }
            if ((purpose.roles().contains(SkillRole.SUSTAIN) || purpose.roles().contains(SkillRole.BUFF))
                    && choice.targetsAllies()) {
                support = true;
            }
            if (purpose.roles().contains(SkillRole.CONTROL) || purpose.roles().contains(SkillRole.DEBUFF)
                    || purpose.roles().contains(SkillRole.INTERRUPT)) {
                control = true;
            }
            if (purpose.roles().contains(SkillRole.GAP_CLOSE)) {
                gapClose = true;
            }
        }
        var pressure = meleeDamage && approach ? (meleeHeavy ? 500 : 400) : 0;
        var flank = meleeDamage && approach && flankMove ? 500 : (gapClose ? 350 : 0);
        var ranged = rangedDamage && keepOrHold ? 600 : 0;
        return new RoleCapabilities(pressure, flank, ranged, support ? 700 : 0, control ? 500 : 0, 400);
    }

    /**
     * Checks the aptitudes.
     *
     * @throws IllegalArgumentException if an aptitude is outside 0 to 1000
     */
    public RoleCapabilities {
        check(pressure, "pressure");
        check(flank, "flank");
        check(ranged, "ranged");
        check(support, "support");
        check(control, "control");
        check(guard, "guard");
    }

    /**
     * Aptitude of one role. Reserve is 100, which is cost 900 at distance zero.
     *
     * @param role the role
     * @return 0 to 1000
     */
    public int aptitude(GroupRole role) {
        return switch (role) {
            case PRESSURE -> pressure;
            case FLANK -> flank;
            case RANGED -> ranged;
            case SUPPORT -> support;
            case CONTROL -> control;
            case GUARD -> guard;
            case RESERVE -> 100;
        };
    }

    /**
     * Whether the profile can take the role. Reserve and guard always can.
     *
     * @param role the role
     * @return whether the aptitude is positive, or the role is guard or reserve
     */
    public boolean allows(GroupRole role) {
        return role == GroupRole.RESERVE || role == GroupRole.GUARD || aptitude(role) > 0;
    }

    private static void check(int value, String name) {
        if (value < 0 || value > 1000) {
            throw new IllegalArgumentException(name + " must be within [0, 1000], was " + value);
        }
    }
}
