package akyrion.thepredict.adapter.server.context;

import akyrion.thepredict.domain.ParticipantKind;

import java.util.Objects;

/**
 * Raw state of one participant read from Paper on the server thread by {@link EntityReads#participant}, so the
 * conversion into bands and cues is a pure function (M07.3, M17.4). The fields that only exist for players take a
 * neutral value on any other entity, one that produces no cue: {@code shieldOnCooldown} false,
 * {@code attackCooldownRatio} 1 and {@code pearlCooldownTicks} 0. Immutable; never leaves the adapter.
 *
 * @param kind kind of the participant
 * @param health current health
 * @param absorption absorption amount
 * @param maxHealth value of the {@code MAX_HEALTH} attribute, {@code NaN} when missing
 * @param armorPoints value of the {@code ARMOR} attribute
 * @param toughnessPoints value of the {@code ARMOR_TOUGHNESS} attribute
 * @param blocking blocking with an item that carries {@code BLOCKS_ATTACKS}: {@code HumanEntity#isBlocking()} for a
 *                 player, which already includes the vanilla block delay, and {@link Blocking} for any other entity
 * @param activeBlocksAttacks the active item carries {@code BLOCKS_ATTACKS}
 * @param shieldOnCooldown a held item with {@code BLOCKS_ATTACKS} is on cooldown
 * @param attackCooldownRatio {@code HumanEntity#getAttackCooldown()}
 * @param activeBow the active item is a bow
 * @param activeItemUsedTicks {@code getActiveItemUsedTime()}
 * @param crossbowLoaded a held item has non-empty {@code CHARGED_PROJECTILES}
 * @param activeConsumable the active item carries {@code CONSUMABLE}
 * @param holdsTotem a held item carries {@code DEATH_PROTECTION}
 * @param pearlCooldownTicks remaining cooldown of the ender pearl group
 * @param gliding {@code isGliding()}
 * @param longReachWeapon the main hand item carries {@code KINETIC_WEAPON}, {@code PIERCING_WEAPON} or
 *                        {@code ATTACK_RANGE}
 * @param fallDistanceBlocks {@code getFallDistance()}
 * @param maceInMainHand the main hand item is a mace
 * @param invisible {@code isInvisible()}, which follows the invisibility effect
 */
public record ParticipantReading(ParticipantKind kind, double health, double absorption, double maxHealth,
                                 double armorPoints, double toughnessPoints, boolean blocking,
                                 boolean activeBlocksAttacks, boolean shieldOnCooldown, float attackCooldownRatio,
                                 boolean activeBow, int activeItemUsedTicks, boolean crossbowLoaded,
                                 boolean activeConsumable, boolean holdsTotem, int pearlCooldownTicks, boolean gliding,
                                 boolean longReachWeapon, float fallDistanceBlocks, boolean maceInMainHand,
                                 boolean invisible) {
    /**
     * Validates the kind.
     *
     * @throws NullPointerException if {@code kind} is {@code null}
     */
    public ParticipantReading {
        Objects.requireNonNull(kind, "kind");
    }

    /**
     * Creates the reading of a player, the only kind before M17. Kept for the tests and doubles that build player
     * readings (rule W5); {@link PaperEntityReads} always passes the kind.
     *
     * @param health current health
     * @param absorption absorption amount
     * @param maxHealth maximum health
     * @param armorPoints armor
     * @param toughnessPoints armor toughness
     * @param blocking blocking
     * @param activeBlocksAttacks the active item carries {@code BLOCKS_ATTACKS}
     * @param shieldOnCooldown a held shield is on cooldown
     * @param attackCooldownRatio attack cooldown
     * @param activeBow the active item is a bow
     * @param activeItemUsedTicks ticks of use of the active item
     * @param crossbowLoaded a held crossbow is loaded
     * @param activeConsumable the active item is consumable
     * @param holdsTotem a held item protects from death
     * @param pearlCooldownTicks cooldown of the pearls
     * @param gliding gliding
     * @param longReachWeapon long reach weapon in the main hand
     * @param fallDistanceBlocks fall distance
     * @param maceInMainHand mace in the main hand
     * @param invisible invisible
     */
    public ParticipantReading(double health, double absorption, double maxHealth, double armorPoints,
                              double toughnessPoints, boolean blocking, boolean activeBlocksAttacks,
                              boolean shieldOnCooldown, float attackCooldownRatio, boolean activeBow,
                              int activeItemUsedTicks, boolean crossbowLoaded, boolean activeConsumable,
                              boolean holdsTotem, int pearlCooldownTicks, boolean gliding, boolean longReachWeapon,
                              float fallDistanceBlocks, boolean maceInMainHand, boolean invisible) {
        this(ParticipantKind.PLAYER, health, absorption, maxHealth, armorPoints, toughnessPoints, blocking,
                activeBlocksAttacks, shieldOnCooldown, attackCooldownRatio, activeBow, activeItemUsedTicks,
                crossbowLoaded, activeConsumable, holdsTotem, pearlCooldownTicks, gliding, longReachWeapon,
                fallDistanceBlocks, maceInMainHand, invisible);
    }
}
