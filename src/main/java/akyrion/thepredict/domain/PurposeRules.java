package akyrion.thepredict.domain;

import java.util.EnumSet;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * Pure rule that tells whether the movement of a tactic lets its skill reach the target (M15.4). Stateless, reads no
 * clock, safe on any thread.
 *
 * <p>With {@code r} the effective reach of the skill ({@link SkillPurpose#effectiveReachBlocks()}): a skill without a
 * reach limit always {@code FITS}. {@code APPROACH} and {@code FLANK} stop at {@code meleeReachBlocks}, so they fit with
 * {@code r >= meleeReach - }{@value #APPROACH_MARGIN_BLOCKS}; {@code KEEP_DISTANCE} keeps the target between the inner
 * and outer edges of the band; {@code RETREAT} and {@code HOLD} fit only with {@code r >= rangedReach}; {@code NONE}
 * keeps an unknown native movement and always fits.
 *
 * <p>Tolerance of the measures (M19.3): the bands of the profile are compared with {@code r} whatever its
 * {@link ReachMeasure}. A reach read from {@code targetwithin} is measured between positions ({@code POSITIONS_STRICT})
 * and an approach stops at a box gap, so the two differ by at most about 0.6 blocks (half the width of a player box),
 * within the {@value #APPROACH_MARGIN_BLOCKS} of {@code APPROACH} plus the gap the table already tolerates; the table
 * does not change.
 *
 * <p>It also keeps the purpose data shared by the binder and the rules: the radius of an area at the target without a
 * radius targeter, the fixed purpose of a vanilla melee choice and the conservative values of an untyped choice.
 */
public final class PurposeRules {
    /** Margin under {@code meleeReachBlocks} within which an approach still counts as reaching the target. */
    public static final double APPROACH_MARGIN_BLOCKS = 0.25;
    /**
     * Radius of an {@code AREA_AT_TARGET} whose skill has no radius targeter, in blocks; it only feeds phrases (no
     * pre-cast check reads it). Moved from the retired {@code PurposeInference} (M19.3).
     */
    public static final double DEFAULT_AREA_RADIUS_BLOCKS = 3.0;
    /** The conservative values every rule uses for an untyped choice (M19.2, rule A6). */
    public static final UntypedDefaults UNTYPED_DEFAULTS = new UntypedDefaults(Set.of(), DamageTier.UNRATED, true,
            false);

    private PurposeRules() {
    }

    /** How the movement of a tactic fits the reach of its skill. */
    public enum PurposeFit {
        /** The movement brings or keeps the target within reach. */
        FITS,
        /** The skill casts only if the target itself comes within reach. */
        CASTS_ONLY_IF_TARGET_COMES,
        /** The movement keeps the target beyond reach: the tactic never casts. */
        NEVER_CASTS
    }

    /**
     * Tells how a movement fits the reach of a skill.
     *
     * @param movement movement of the tactic
     * @param purpose purpose of the tactic's skill
     * @param settings movement settings of the profile
     * @return the fit
     */
    public static PurposeFit fit(Movement movement, SkillPurpose purpose, MovementSettings settings) {
        Objects.requireNonNull(movement, "movement");
        Objects.requireNonNull(settings, "settings");
        var reach = purpose.effectiveReachBlocks();
        if (reach.isEmpty()) {
            return PurposeFit.FITS;
        }
        var r = reach.getAsDouble();
        return switch (movement) {
            case APPROACH, FLANK -> r >= settings.meleeReachBlocks() - APPROACH_MARGIN_BLOCKS
                    ? PurposeFit.FITS : PurposeFit.CASTS_ONLY_IF_TARGET_COMES;
            case KEEP_DISTANCE -> keepDistance(r, settings);
            case RETREAT, HOLD -> r >= settings.rangedReachBlocks()
                    ? PurposeFit.FITS : PurposeFit.CASTS_ONLY_IF_TARGET_COMES;
            case NONE -> PurposeFit.FITS;
        };
    }

    /**
     * Tells whether a movement brings the target within the reach of a skill: {@code APPROACH} and {@code FLANK}
     * always, {@code KEEP_DISTANCE} when the skill reaches the outer edge of the band; any other movement, or
     * {@code KEEP_DISTANCE} with a skill without a reach limit, does not.
     *
     * @param movement movement of the tactic
     * @param purpose purpose of the tactic's skill
     * @param settings movement settings of the profile
     * @return whether the movement closes the distance for this skill
     */
    public static boolean closesDistance(Movement movement, SkillPurpose purpose, MovementSettings settings) {
        Objects.requireNonNull(movement, "movement");
        Objects.requireNonNull(settings, "settings");
        return switch (movement) {
            case APPROACH, FLANK -> true;
            case KEEP_DISTANCE -> purpose.effectiveReachBlocks().isPresent()
                    && purpose.effectiveReachBlocks().getAsDouble() >= settings.bandOuterBlocks();
            case RETREAT, HOLD, NONE -> false;
        };
    }

    /**
     * The fixed purpose of a vanilla melee choice: {@code LivingEntity#attack} always hits, so it is a damaging melee
     * hit with {@code UNRATED} damage that works best in melee, reaching {@code meleeReachBlocks} (capped at
     * {@link SkillPurpose#MAX_REACH_BLOCKS}, so a valid 0.0.1 profile with a very long melee reach still loads). Origin
     * {@code INFERRED}: it comes from the kind, never from {@code PredictValues}, and behaves exactly as in the 0.0.1.
     *
     * @param settings movement settings of the profile
     * @return the purpose, with cooldown {@code UNKNOWN} until the binder reads the {@code CD}
     */
    public static SkillPurpose vanillaMelee(MovementSettings settings) {
        Objects.requireNonNull(settings, "settings");
        return new SkillPurpose(SkillRole.DAMAGE, EnumSet.of(SkillRole.DAMAGE), Delivery.MELEE,
                OptionalDouble.of(Math.min(settings.meleeReachBlocks(), SkillPurpose.MAX_REACH_BLOCKS)),
                OptionalDouble.empty(), 0, CooldownClass.UNKNOWN, DamageTier.UNRATED,
                EnumSet.of(UseCondition.TARGET_IN_MELEE), Set.of(), false, PurposeOrigin.INFERRED);
    }

    /**
     * The conservative values of an untyped choice (M19.2): they never make it more dangerous nor quieter. Later
     * modules add theirs on the same line of the consolidated plan (danger class {@code MEDIUM} or the {@code Telegraph}
     * of the choice and answer {@code BACK_OFF} in M23, assumed displacement {@code KNOCKBACK} in M24).
     *
     * @param roles roles of an untyped choice: none, so no role-based rule picks it
     * @param damage damage tier: {@code UNRATED}, damaging for the shield and invulnerability checks and last in the
     *        damage order
     * @param damaging whether it counts as damaging when it needs a target
     * @param reachChecked whether a reach check applies: never, because nothing tells its reach
     */
    public record UntypedDefaults(Set<SkillRole> roles, DamageTier damage, boolean damaging, boolean reachChecked) {
        /**
         * Validates the values and copies the roles.
         *
         * @throws NullPointerException if a component is {@code null}
         */
        public UntypedDefaults {
            roles = Set.copyOf(Objects.requireNonNull(roles, "roles"));
            Objects.requireNonNull(damage, "damage");
        }
    }

    private static PurposeFit keepDistance(double reachBlocks, MovementSettings settings) {
        if (reachBlocks >= settings.bandOuterBlocks()) {
            return PurposeFit.FITS;
        }
        return reachBlocks >= settings.bandInnerBlocks() ? PurposeFit.CASTS_ONLY_IF_TARGET_COMES : PurposeFit.NEVER_CASTS;
    }
}
