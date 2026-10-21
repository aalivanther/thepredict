package akyrion.thepredict.adapter.server.patterns;

/**
 * Decides whether an air swing is a real attack (M26.1). {@code observe} never tokens it. {@code on} requires a
 * same-tick {@code LEFT_CLICK_AIR}, a main-hand weapon, and no block place, right click or projectile on this tick
 * or the previous one.
 */
public final class AirSwingGate {
    private AirSwingGate() {
    }

    /**
     * Whether the swing may become a token.
     *
     * @param policyOn {@code patterns.airSwings: on}
     * @param leftClickAir the animation is a left click in the air
     * @param mainHandWeapon sword, axe, mace, spear or trident
     * @param blockPlace a block was placed this tick or the previous
     * @param rightClick a right click happened this tick or the previous
     * @param projectile a projectile was spawned this tick or the previous
     * @return {@code true} only for a clean weapon swing while the policy is on
     */
    public static boolean tokens(boolean policyOn, boolean leftClickAir, boolean mainHandWeapon, boolean blockPlace,
                                 boolean rightClick, boolean projectile) {
        return policyOn && leftClickAir && mainHandWeapon && !blockPlace && !rightClick && !projectile;
    }
}
