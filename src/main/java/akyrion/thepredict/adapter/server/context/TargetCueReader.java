package akyrion.thepredict.adapter.server.context;

import akyrion.thepredict.domain.TargetCue;

import java.util.EnumSet;
import java.util.Set;

/**
 * Turns a {@link ParticipantReading} into {@link TargetCue} values (M07.3), with one pure method per cue so each
 * is tested on its own. Stateless and safe on any thread; the reading itself happens on the server thread.
 *
 * <p>Perception (M07.2): an invisible player shows only {@code INVISIBLE}; a player the mobs do not see shows no
 * cue, because every other cue is visual. The bow, mace and pearl are identified by type or item group, the
 * justified exception to "no {@code Material} lists" (A1): none has a verified exclusive component.
 */
public final class TargetCueReader {
    /** Attack cooldown below which the player's attack counts as recharging. */
    public static final float RECHARGED_ATTACK_RATIO = 0.9f;
    /** Ticks of drawing after which a vanilla bow shoots at full power. */
    public static final int FULL_DRAW_TICKS = 20;
    /** Fall distance above which a player holding a mace counts as diving for a smash. */
    public static final float MACE_DIVE_FALL_BLOCKS = 1.5f;

    private TargetCueReader() {
    }

    /**
     * Reads every perceivable cue.
     *
     * @param reading raw state of the player
     * @param seen whether a member has line of sight to the player
     * @param recentShieldDisable whether the encounter history holds a fresh {@code SHIELD_DISABLED} of the player
     * @return the cues; {@code INVISIBLE} alone for an invisible player, none for an unseen one
     */
    public static Set<TargetCue> cues(ParticipantReading reading, boolean seen, boolean recentShieldDisable) {
        if (invisible(reading)) {
            return EnumSet.of(TargetCue.INVISIBLE);
        }
        var cues = EnumSet.noneOf(TargetCue.class);
        if (!seen) {
            return cues;
        }
        add(cues, TargetCue.BLOCKING, blocking(reading));
        add(cues, TargetCue.RAISING_SHIELD, raisingShield(reading));
        add(cues, TargetCue.SHIELD_DISABLED, shieldDisabled(reading, recentShieldDisable));
        add(cues, TargetCue.ATTACK_RECHARGING, attackRecharging(reading));
        add(cues, TargetCue.DRAWING_BOW, drawingBow(reading));
        add(cues, TargetCue.BOW_FULLY_DRAWN, bowFullyDrawn(reading));
        add(cues, TargetCue.CROSSBOW_LOADED, reading.crossbowLoaded());
        add(cues, TargetCue.CONSUMING, consuming(reading));
        add(cues, TargetCue.HOLDS_TOTEM, reading.holdsTotem());
        add(cues, TargetCue.PEARL_ON_COOLDOWN, pearlOnCooldown(reading));
        add(cues, TargetCue.GLIDING, reading.gliding());
        add(cues, TargetCue.LONG_REACH_WEAPON, reading.longReachWeapon());
        add(cues, TargetCue.MACE_DIVE, maceDive(reading));
        return cues;
    }

    /**
     * {@code BLOCKING}: {@code isBlocking()}, which already waits the vanilla block delay.
     *
     * @param reading raw state of the player
     * @return whether the shield blocks now
     */
    public static boolean blocking(ParticipantReading reading) {
        return reading.blocking();
    }

    /**
     * {@code RAISING_SHIELD}: an active item with {@code BLOCKS_ATTACKS} that does not block yet; never together
     * with {@code BLOCKING}.
     *
     * @param reading raw state of the player
     * @return whether the shield is up but not blocking
     */
    public static boolean raisingShield(ParticipantReading reading) {
        return reading.activeBlocksAttacks() && !reading.blocking();
    }

    /**
     * {@code SHIELD_DISABLED}: the held shield is on cooldown, or the history has a fresh disable event.
     *
     * @param reading raw state of the player
     * @param recentShieldDisable fresh {@code SHIELD_DISABLED} event of the player
     * @return whether the shield is disabled
     */
    public static boolean shieldDisabled(ParticipantReading reading, boolean recentShieldDisable) {
        return reading.shieldOnCooldown() || recentShieldDisable;
    }

    /**
     * {@code ATTACK_RECHARGING}: attack cooldown below {@value #RECHARGED_ATTACK_RATIO}.
     *
     * @param reading raw state of the player
     * @return whether the attack is recharging
     */
    public static boolean attackRecharging(ParticipantReading reading) {
        return reading.attackCooldownRatio() < RECHARGED_ATTACK_RATIO;
    }

    /**
     * {@code DRAWING_BOW}: the active item is a bow.
     *
     * @param reading raw state of the player
     * @return whether a bow is being drawn
     */
    public static boolean drawingBow(ParticipantReading reading) {
        return reading.activeBow();
    }

    /**
     * {@code BOW_FULLY_DRAWN}: drawing for at least {@value #FULL_DRAW_TICKS} ticks.
     *
     * @param reading raw state of the player
     * @return whether the bow would shoot at full power
     */
    public static boolean bowFullyDrawn(ParticipantReading reading) {
        return reading.activeBow() && reading.activeItemUsedTicks() >= FULL_DRAW_TICKS;
    }

    /**
     * {@code CONSUMING}: the active item carries {@code CONSUMABLE}.
     *
     * @param reading raw state of the player
     * @return whether the player eats or drinks
     */
    public static boolean consuming(ParticipantReading reading) {
        return reading.activeConsumable();
    }

    /**
     * {@code PEARL_ON_COOLDOWN}: the ender pearl group has cooldown left.
     *
     * @param reading raw state of the player
     * @return whether pearls are on cooldown
     */
    public static boolean pearlOnCooldown(ParticipantReading reading) {
        return reading.pearlCooldownTicks() > 0;
    }

    /**
     * {@code MACE_DIVE}: a mace in the main hand while falling more than {@value #MACE_DIVE_FALL_BLOCKS} blocks.
     *
     * @param reading raw state of the player
     * @return whether the player dives for a smash
     */
    public static boolean maceDive(ParticipantReading reading) {
        return reading.maceInMainHand() && reading.fallDistanceBlocks() > MACE_DIVE_FALL_BLOCKS;
    }

    /**
     * {@code INVISIBLE}: {@code isInvisible()}.
     *
     * @param reading raw state of the player
     * @return whether the player is invisible
     */
    public static boolean invisible(ParticipantReading reading) {
        return reading.invisible();
    }

    private static void add(Set<TargetCue> cues, TargetCue cue, boolean present) {
        if (present) {
            cues.add(cue);
        }
    }
}
