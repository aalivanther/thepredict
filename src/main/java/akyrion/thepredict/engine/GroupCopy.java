package akyrion.thepredict.engine;

import akyrion.thepredict.domain.AttackPool;
import akyrion.thepredict.domain.GroupMode;
import akyrion.thepredict.domain.GroupPlan;
import akyrion.thepredict.domain.GroupRole;

import java.util.Optional;

/**
 * Fields the tick layer may read for one session (M25, rule T9). One object, updated in place. A stale copy
 * degrades that session to fair for the tick and still uses the ledgers.
 */
public final class GroupCopy {
    /** Creates an empty copy at epoch 1. The group has not published a role. */
    public GroupCopy() {
    }

    private long membershipEpoch = 1;
    private long planEpoch = 1;
    private long seenEpoch = 1;
    private long seenPlan = 1;
    private GroupRole role;
    private int slotIndex = -1;
    private int slotCell;
    private int planOrdinal = -1;
    private int tokenMask;
    private int seesTargetMask;
    private GroupMode mode = GroupMode.OFF;
    private boolean stale;
    private boolean meleeAsk;
    private boolean slotFar;
    private int regroupElapsed;
    private boolean planAnswered;

    /**
     * Whether this session holds a token. A holder is never cold.
     *
     * @return whether a pool bit is set
     */
    public boolean holdsToken() {
        return tokenMask != 0;
    }

    /**
     * Whether the melee bit is set.
     *
     * @return whether melee is held
     */
    public boolean holdsMelee() {
        return (tokenMask & 1) != 0;
    }

    /**
     * Published role, empty when none was assigned.
     *
     * @return the role
     */
    public Optional<GroupRole> role() {
        return Optional.ofNullable(role);
    }

    /**
     * Published plan, empty when the group has none.
     *
     * @return the plan
     */
    public Optional<GroupPlan> plan() {
        int ordinal = planOrdinal;
        if (ordinal < 0 || ordinal >= GroupPlan.values().length) {
            return Optional.empty();
        }
        return Optional.of(GroupPlan.values()[ordinal]);
    }

    /**
     * Squad size published for the placeholder.
     *
     * @return the size, or 0
     */
    public int squadSize() {
        return slotCell >>> 16;
    }

    /**
     * Published slot. {@code -1} when the group assigned none. A valid angle is {@code (ring << 8) | index}.
     *
     * @return the slot
     */
    public int slotIndex() {
        return slotIndex;
    }

    /**
     * Slot ring: 0 inner, 1 outer, 2 ranged, or -1.
     *
     * @return the ring
     */
    public int slotRing() {
        return slotIndex < 0 ? -1 : slotIndex >>> 8;
    }

    /**
     * Token mask. Bit 0 is melee and bit 1 is ranged.
     *
     * @return the mask
     */
    public int tokenMask() {
        return tokenMask;
    }

    /**
     * Whether bit 0 of the sight mask is set. Second-hand sight leaves it clear.
     *
     * @return whether the member sees the target itself
     */
    public boolean seesFirstHand() {
        return (seesTargetMask & 1) != 0;
    }

    /**
     * Effective mode for this tick. A stale copy is fair.
     *
     * @return the mode
     */
    public GroupMode mode() {
        return stale ? GroupMode.FAIR : mode;
    }

    /**
     * Whether the copy is behind the encounter.
     *
     * @return whether the tick should count a stale copy
     */
    public boolean stale() {
        return stale;
    }

    void publish(long membership, long plan, GroupRole role, int slot, int squadSize, int planOrdinal, int tokens,
                 int sight, GroupMode mode) {
        this.membershipEpoch = membership;
        this.planEpoch = plan;
        this.seenEpoch = membership;
        this.seenPlan = plan;
        this.role = role;
        this.slotIndex = slot;
        this.slotCell = squadSize << 16;
        this.planOrdinal = planOrdinal;
        this.tokenMask = tokens;
        this.seesTargetMask = sight;
        this.mode = mode;
        this.stale = false;
    }

    void noteEpoch(long membership, long plan) {
        stale = seenEpoch != membership || seenPlan != plan;
    }

    /**
     * Membership epoch last published.
     *
     * @return the epoch
     */
    public long membershipEpoch() {
        return membershipEpoch;
    }

    /**
     * Plan epoch last published.
     *
     * @return the epoch
     */
    public long planEpoch() {
        return planEpoch;
    }

    /**
     * Sets the bit of a token the ledger just granted, for the rest of this tick; the next publication copies the
     * bits from the ledger again (EFO-15).
     *
     * @param pool the pool granted
     */
    void token(AttackPool pool) {
        tokenMask |= 1 << pool.ordinal();
    }

    void clearToken(AttackPool pool) {
        tokenMask &= ~(1 << pool.ordinal());
    }

    /**
     * Sets the sight mask between two publications. The group publishes it with each copy; the tests of the gate set it
     * alone, without a role, a plan or a mode (EFO-27 keeps this seam: {@code publish} would set all of them).
     *
     * @param mask bit 0 when the member sees the target first hand
     */
    void sight(int mask) {
        seesTargetMask = mask;
    }

    /**
     * Asks the next arbitration for a melee token. Enter uses this until contact.
     */
    public void askMelee() {
        meleeAsk = true;
    }

    boolean takeMeleeAsk() {
        var asked = meleeAsk;
        meleeAsk = false;
        return asked;
    }

    /**
     * Whether the member is more than a block and a half from its slot.
     *
     * @return whether micro may walk to the slot
     */
    public boolean slotFar() {
        return slotFar;
    }

    void slotFar(boolean far) {
        slotFar = far;
    }

    /**
     * Ticks the current {@code fall_back_and_regroup} has been in force.
     *
     * @return the elapsed ticks, or 0
     */
    public int regroupElapsed() {
        return regroupElapsed;
    }

    void regroupElapsed(int ticks) {
        regroupElapsed = Math.max(0, ticks);
    }

    /**
     * Whether the published plan is the one Jev chose in its {@code g0} answer, for {@code inspect group} (EFO-25).
     *
     * @return {@code true} for a plan of Jev, {@code false} for a local plan or none
     */
    public boolean planAnswered() {
        return planAnswered && planOrdinal >= 0;
    }

    void planAnswered(boolean answered) {
        planAnswered = answered;
    }
}
