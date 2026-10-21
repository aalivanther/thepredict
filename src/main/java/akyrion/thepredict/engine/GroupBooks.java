package akyrion.thepredict.engine;

import akyrion.thepredict.domain.BenchPolicy;
import akyrion.thepredict.domain.GroupCaps;
import akyrion.thepredict.domain.GroupMode;

/**
 * Ledgers of one session's encounter (M25). Absent on a session that is not in a fair group, which keeps
 * today's cast path.
 *
 * @param attack impact and token ledger
 * @param tells tell ledger
 * @param caps limits that the ladder does not change
 * @param mode effective mode
 * @param memberIndex index in join order
 * @param playerIndex participant index, or -1
 * @param bench policy against entities
 * @param entropy whether this member's entropy lifts A13
 * @param leader whether this member is the leader
 * @param meleeCap melee weight, the stricter of the config and the ladder
 * @param gapTicks impact gap, the stricter of the config and the ladder
 * @param grace whether the target is stunned by ThePredict and no one else may take a damaging token
 */
public record GroupBooks(AttackLedger attack, TellLedger tells, GroupCaps caps, GroupMode mode, int memberIndex,
                         int playerIndex, BenchPolicy bench, boolean entropy, boolean leader, int meleeCap,
                         int gapTicks, boolean grace) {
    /**
     * A copy that ignores role, slot and plan for this tick. The ledgers stay.
     *
     * @param mode mode to publish, fair when the copy is stale
     * @return the copy
     */
    public GroupBooks withMode(GroupMode mode) {
        return new GroupBooks(attack, tells, caps, mode, memberIndex, playerIndex, bench, entropy, leader, meleeCap,
                gapTicks, grace);
    }
}
