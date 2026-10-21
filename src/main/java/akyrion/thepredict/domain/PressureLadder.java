package akyrion.thepredict.domain;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Ladder v5 (M26). The resource {@code difficulty/ladder-v5.json} is v4 plus {@code exploitShare}. {@code k} is not
 * recalibrated. A fingerprint mismatch makes player and auto fights use the bench levers plus the floor. Edge danger
 * is on every rung. Hazard push is on none. The frozen v4 bytes stay in the test resources (errata E26-9).
 */
public final class PressureLadder {
    /** SHA-256 of the bundled {@code ladder-v5.json} bytes. */
    public static final String LADDER_SHA256 =
            "c4665ef7e89d59e032ae3294681338a5dbc9b2b5ef2c3d2faef3dd73f1d7bdb0";

    /** Classpath of the bundled ladder. */
    public static final String RESOURCE = "/difficulty/ladder-v5.json";

    private static final int[] DELAY = {20, 18, 16, 14, 12, 10, 8, 6, 0};
    private static final int[] SLACK = {20, 16, 12, 8, 6, 4, 2, 0, 0};
    private static final boolean[] PREFERENCE = {false, false, false, false, false, true, true, true, true};
    private static final int PENALTY = 150;
    private static final int[] GROUP_MELEE = {1, 1, 1, 1, 1, 2, 2, 2, 2};
    private static final int[] GROUP_GAP = {20, 16, 14, 12, 10, 10, 8, 6, 6};
    /** Rung 0 is the easiest and does not exploit. Rung 8, the hardest, exploits half of its casts. */
    private static final double[] EXPLOIT = {0.0, 0.0, 0.0, 0.10, 0.20, 0.30, 0.40, 0.50, 0.50};

    private PressureLadder() {
    }

    /**
     * Levers of one rung. Rung 8 uses the floor as its delay. Against an entity the floor is 0.
     *
     * @param rung rung 0 to 8
     * @param profileDelayTicks {@code Timing.ReactionDelay}
     * @param floorTicks preset floor; ignored against an entity
     * @param againstPlayer whether the target is a player
     * @return the levers
     */
    public static PressureLevers levers(int rung, int profileDelayTicks, int floorTicks, boolean againstPlayer) {
        var step = Math.max(0, Math.min(8, rung));
        var floor = againstPlayer ? floorTicks : 0;
        var table = step == 8 ? floor : DELAY[step];
        var delay = Math.max(profileDelayTicks, Math.max(floor, table));
        return new PressureLevers(delay, mask(step), SLACK[step], PREFERENCE[step], step <= 3 ? PENALTY : 0, 2, 6,
                EXPLOIT[step]);
    }

    /**
     * Levers of one rung for a group of two or more. The rhythm columns match {@link #levers}. Melee and the impact
     * gap come from the group section and never exceed A13.
     *
     * @param rung rung 0 to 8
     * @param profileDelayTicks {@code Timing.ReactionDelay}
     * @param floorTicks preset floor; ignored against an entity
     * @param againstPlayer whether the target is a player
     * @return the levers
     */
    public static PressureLevers groupLevers(int rung, int profileDelayTicks, int floorTicks, boolean againstPlayer) {
        var solo = levers(rung, profileDelayTicks, floorTicks, againstPlayer);
        var step = Math.max(0, Math.min(8, rung));
        return new PressureLevers(solo.reactionDelayTicks(), solo.reactionMask(), solo.damagingCastSlackTicks(),
                solo.damagePreference(), solo.localDamagePenaltyMillis(), GROUP_MELEE[step], GROUP_GAP[step],
                solo.exploitShare());
    }

    /**
     * Melee weight of the group section. Rung 0 is the easiest: one melee attacker.
     *
     * @param rung rung 0 to 8
     * @return the weight
     */
    public static int groupMelee(int rung) {
        return GROUP_MELEE[Math.max(0, Math.min(8, rung))];
    }

    /**
     * Impact gap of the group section. Rung 0 is the easiest: the widest gap.
     *
     * @param rung rung 0 to 8
     * @return ticks
     */
    public static int groupGap(int rung) {
        return GROUP_GAP[Math.max(0, Math.min(8, rung))];
    }

    /**
     * Bench levers: rung 8, floor 0, the profile delay, full mask.
     *
     * @param profileDelayTicks {@code Timing.ReactionDelay}
     * @return the levers
     */
    public static PressureLevers bench(int profileDelayTicks) {
        return levers(8, profileDelayTicks, 0, false);
    }

    /**
     * Reads the bundled ladder and checks its fingerprint.
     *
     * @return whether the bytes match {@link #LADDER_SHA256}
     */
    public static boolean bundledMatches() {
        try (var input = PressureLadder.class.getResourceAsStream(RESOURCE)) {
            return input != null && sha256(input.readAllBytes()).equals(LADDER_SHA256);
        } catch (IOException exception) {
            return false;
        }
    }

    /**
     * SHA-256 of bytes, lower-case hex.
     *
     * @param bytes the file
     * @return the hex
     */
    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    /**
     * Checks an opened stream.
     *
     * @param input the resource
     * @return the hex when it matches
     * @throws IllegalStateException when the fingerprint differs
     */
    public static String require(InputStream input) {
        try {
            var hex = sha256(input.readAllBytes());
            if (!hex.equals(LADDER_SHA256)) {
                throw new IllegalStateException("ladder fingerprint");
            }
            return hex;
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static long mask(int rung) {
        var base = switch (rung) {
            case 5 -> ReactionMask.without(Reaction.PUNISH_RECHARGE);
            case 4 -> ReactionMask.without(Reaction.PUNISH_RECHARGE, Reaction.PUNISH_CONSUMING);
            case 3, 2, 1, 0 -> ReactionMask.without(Reaction.PUNISH_RECHARGE, Reaction.PUNISH_CONSUMING,
                    Reaction.FINISH);
            default -> ReactionMask.ALL;
        };
        return (base & ~ReactionMask.bit(Reaction.HAZARD_PUSH)) | ReactionMask.bit(Reaction.EDGE_DANGER);
    }
}
