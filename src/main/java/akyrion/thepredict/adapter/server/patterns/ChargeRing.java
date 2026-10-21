package akyrion.thepredict.adapter.server.patterns;

import java.util.UUID;

/**
 * Charge, sprint and spear sampled before the attack cooldown resets, plus the air-swing blocks of this tick and the
 * previous one. Eight slots, no map. Only this tick and the previous one matter, so a slot whose last note and last
 * flag are both older than that is free again (SP-10); a ring that is still full of live entries reuses slot 0.
 */
final class ChargeRing {
    private final UUID[] ids = new UUID[8];
    private final int[] at = new int[8];
    private final double[] charge = new double[8];
    private final boolean[] sprinting = new boolean[8];
    private final boolean[] spear = new boolean[8];
    private final boolean[] block = new boolean[8];
    private final boolean[] right = new boolean[8];
    private final boolean[] projectile = new boolean[8];
    private final int[] flagged = new int[8];

    void note(UUID id, int tick, double attackCharge, boolean sprint, boolean spearWeapon) {
        var slot = slot(id, tick);
        ids[slot] = id;
        at[slot] = tick;
        charge[slot] = attackCharge;
        sprinting[slot] = sprint;
        spear[slot] = spearWeapon;
    }

    void flag(UUID id, int tick, boolean placed, boolean clicked, boolean launched) {
        var slot = slot(id, tick);
        ids[slot] = id;
        flagged[slot] = tick;
        block[slot] = placed;
        right[slot] = clicked;
        projectile[slot] = launched;
    }

    Shot take(UUID id, int tick) {
        for (var index = 0; index < ids.length; index++) {
            if (id.equals(ids[index]) && at[index] == tick) {
                return new Shot(charge[index], sprinting[index], spear[index]);
            }
        }
        return new Shot(1.0, false, false);
    }

    Flags flags(UUID id, int tick) {
        for (var index = 0; index < ids.length; index++) {
            if (!id.equals(ids[index]) || flagged[index] < tick - 1) {
                continue;
            }
            return new Flags(block[index], right[index], projectile[index]);
        }
        return new Flags(false, false, false);
    }

    private int slot(UUID id, int tick) {
        var free = 0;
        for (var index = 0; index < ids.length; index++) {
            if (id.equals(ids[index])) {
                return index;
            }
            if (ids[index] == null || Math.max(at[index], flagged[index]) < tick - 1) {
                free = index;
            }
        }
        var taken = ids[free];
        if (taken != null) {
            clear(free);
        }
        return free;
    }

    private void clear(int slot) {
        at[slot] = 0;
        charge[slot] = 0.0;
        sprinting[slot] = false;
        spear[slot] = false;
        flagged[slot] = 0;
        block[slot] = false;
        right[slot] = false;
        projectile[slot] = false;
    }

    record Shot(double charge, boolean sprinting, boolean spear) {
    }

    record Flags(boolean block, boolean right, boolean projectile) {
        String blocked() {
            if (block) {
                return "block";
            }
            if (right) {
                return "interact";
            }
            return projectile ? "projectile" : "";
        }
    }
}
