package akyrion.thepredict.domain;

/**
 * Global group limits (M25). Every default lives here and only here (rule C13). A13 floors are
 * {@link GroupFairnessRules}, not keys.
 *
 * @param mode delivered mode
 * @param maxSquads squads in one encounter
 * @param brokenTicks ticks a break lasts
 * @param arbitrationPeriodTicks ticks between arbitrations
 * @param replanPeriodTicks ticks between replans
 * @param maxReplansPerTick replans of one world in one tick
 * @param maxMicrosPerTick budget of the group step
 * @param maxMeleeAttackersPerPlayer melee weight
 * @param maxRangedAttackersPerPlayer ranged weight
 * @param minImpactGapTicks gap between impacts
 * @param tokenIdleTimeoutTicks ticks before an idle token returns
 * @param stealMarginMillis score gap required to steal
 * @param idleWarnTicks ticks without a token before a warning
 * @param roleSwitchMarginMillis cost of changing role
 * @param exitSectorDegrees sector left behind the player
 * @param lkpForgetTicks ticks a last-known cell is kept
 * @param maxMediumTellsPerPlayer open medium tells
 * @param bigImpactSpacingTicks spacing around a big impact
 * @param leaderReserveTicks how early the leader reserve opens
 * @param projectileFlightMarginTicks widening of a projectile window
 * @param maxPlanCandidates candidates offered
 * @param minPlanTicks ticks a definitive plan must last
 * @param planHorizonTicks ticks before a plan expires
 * @param assignmentThreatMargin threat added on a group assignment
 * @param factOnTicks samples before a fact turns on
 * @param factOffTicks samples before a fact turns off
 * @param maxFacts facts sent for the group
 */
public record GroupCaps(GroupMode mode, int maxSquads, int brokenTicks, int arbitrationPeriodTicks,
                        int replanPeriodTicks, int maxReplansPerTick, int maxMicrosPerTick,
                        int maxMeleeAttackersPerPlayer, int maxRangedAttackersPerPlayer, int minImpactGapTicks,
                        int tokenIdleTimeoutTicks, int stealMarginMillis, int idleWarnTicks,
                        int roleSwitchMarginMillis, int exitSectorDegrees, int lkpForgetTicks,
                        int maxMediumTellsPerPlayer, int bigImpactSpacingTicks, int leaderReserveTicks,
                        int projectileFlightMarginTicks, int maxPlanCandidates, int minPlanTicks,
                        int planHorizonTicks, int assignmentThreatMargin, int factOnTicks, int factOffTicks,
                        int maxFacts) {
    /** Delivered limits. The mode is fair. */
    public static final GroupCaps DEFAULT = new GroupCaps(GroupMode.FAIR, 4, 100, 4, 20, 2, 150, 2, 2, 6, 30, 150,
            100, 150, 90, 200, 2, 10, 10, 2, 4, 40, 100, 5, 10, 40, 4);

    /**
     * Checks the ranges and the cross checks.
     *
     * @throws IllegalArgumentException when a limit is outside its range or below A13
     */
    public GroupCaps {
        if (mode == null) {
            throw new IllegalArgumentException("group.mode is required");
        }
        range(maxSquads, 1, 8, "group.maxSquads");
        range(brokenTicks, 20, 600, "group.brokenTicks");
        range(arbitrationPeriodTicks, 1, 20, "group.arbitrationPeriodTicks");
        range(replanPeriodTicks, 10, 100, "group.replanPeriodTicks");
        range(maxReplansPerTick, 1, 10, "group.maxReplansPerTick");
        range(maxMicrosPerTick, 50, 1000, "group.maxMicrosPerTick");
        range(maxMeleeAttackersPerPlayer, 1, 4, "group.maxMeleeAttackersPerPlayer");
        range(maxRangedAttackersPerPlayer, 1, 6, "group.maxRangedAttackersPerPlayer");
        range(minImpactGapTicks, GroupFairnessRules.MIN_IMPACT_GAP_TICKS, 20, "group.minImpactGapTicks");
        range(tokenIdleTimeoutTicks, 10, 100, "group.tokenIdleTimeoutTicks");
        range(stealMarginMillis, 0, 500, "group.stealMarginMillis");
        range(idleWarnTicks, 20, 600, "group.idleWarnTicks");
        range(roleSwitchMarginMillis, 0, 500, "group.roleSwitchMarginMillis");
        range(exitSectorDegrees, GroupFairnessRules.MIN_EXIT_SECTOR_DEGREES, 180, "group.exitSectorDegrees");
        range(lkpForgetTicks, 20, 1200, "group.lkpForgetTicks");
        checkTellsAndPlans(maxMediumTellsPerPlayer, bigImpactSpacingTicks, leaderReserveTicks,
                projectileFlightMarginTicks, maxPlanCandidates, minPlanTicks, planHorizonTicks, assignmentThreatMargin,
                factOnTicks, factOffTicks, maxFacts);
    }

    private static void checkTellsAndPlans(int maxMediumTellsPerPlayer, int bigImpactSpacingTicks,
                                           int leaderReserveTicks, int projectileFlightMarginTicks,
                                           int maxPlanCandidates, int minPlanTicks, int planHorizonTicks,
                                           int assignmentThreatMargin, int factOnTicks, int factOffTicks,
                                           int maxFacts) {
        range(maxMediumTellsPerPlayer, 1, 3, "group.maxMediumTellsPerPlayer");
        range(bigImpactSpacingTicks, GroupFairnessRules.MIN_BIG_IMPACT_SPACING_TICKS, 40,
                "group.bigImpactSpacingTicks");
        range(leaderReserveTicks, 0, 40, "group.leaderReserveTicks");
        range(projectileFlightMarginTicks, 0, 5, "group.projectileFlightMarginTicks");
        range(maxPlanCandidates, 3, 4, "group.maxPlanCandidates");
        range(minPlanTicks, 10, 200, "group.minPlanTicks");
        range(planHorizonTicks, minPlanTicks, 600, "group.planHorizonTicks");
        range(assignmentThreatMargin, 0, 50, "group.assignmentThreatMargin");
        range(factOnTicks, 2, 100, "group.factOnTicks");
        range(factOffTicks, factOnTicks, 400, "group.factOffTicks");
        range(maxFacts, 0, 7, "group.maxFacts");
    }

    /**
     * How long a leader reserve may stay open.
     *
     * @return {@code leaderReserveTicks + 10}
     */
    public int leaderReserveLimitTicks() {
        return leaderReserveTicks + 10;
    }

    private static void range(int value, int least, int most, String path) {
        if (value < least || value > most) {
            throw new IllegalArgumentException(path + " must be within [" + least + ", " + most + "], was " + value);
        }
    }
}
