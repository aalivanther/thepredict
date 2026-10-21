package akyrion.thepredict.adapter.server.patterns;

import akyrion.thepredict.domain.EnemyKinematics;

/**
 * Builds kinematic word v1 from a sample the caller already read (M26.1). This class does not call
 * {@code getLocation} or {@code getVelocity}. A player double supplies the numbers. Movement tokens follow the
 * witness rule of A14: without a witness, or with an invisible enemy, the stamp advances and no lateral or radial
 * code is written.
 *
 * @param x block x
 * @param z block z
 * @param yawDegrees yaw in degrees
 * @param fallDistance {@code getFallDistance()}
 * @param sprinting the enemy is sprinting
 * @param invisible the enemy is invisible
 * @param witnessX witness x
 * @param witnessZ witness z
 * @param witness whether a witness perceives the enemy
 * @param strafeBlocks minimum movement that counts
 */
public record EnemyKinematicsReader(double x, double z, float yawDegrees, double fallDistance, boolean sprinting,
                                    boolean invisible, double witnessX, double witnessZ, boolean witness,
                                    double strafeBlocks) {
    /**
     * Packs one sample against the previous word. The displacement is the unwrapped difference of the two quantized
     * stamps, so a still player reads zero at any coordinate.
     *
     * @param previous previous word, {@code 0} when none
     * @param gapTicks ticks since that word
     * @return the word
     */
    public long read(long previous, int gapTicks) {
        var motion = 0;
        if (EnemyKinematics.valid(previous) && WitnessRule.visual(witness, invisible)) {
            var dx = KinematicStamp.deltaQuarters(x, KinematicStamp.xQuarters(previous)) / 4.0;
            var dz = KinematicStamp.deltaQuarters(z, KinematicStamp.zQuarters(previous)) / 4.0;
            motion = KinematicStamp.motion(dx, dz, witnessX - x, witnessZ - z, strafeBlocks, true);
        }
        return KinematicStamp.pack(x, z, yawDegrees, previous, motion & 3, motion >>> 2, fallDistance > 0.0,
                sprinting, invisible, gapTicks);
    }

    /**
     * A new sprint token waits 11 ticks. Ten ticks is still the same sprint.
     *
     * @param tick the server tick
     * @param lastSprintTick the previous sprint token, or negative when none
     * @return whether a new sprint token is due
     */
    public static boolean sprintAgain(int tick, int lastSprintTick) {
        return lastSprintTick < 0 || tick - lastSprintTick >= 11;
    }
}
