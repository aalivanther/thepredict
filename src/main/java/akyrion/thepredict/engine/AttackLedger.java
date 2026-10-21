package akyrion.thepredict.engine;

import akyrion.thepredict.domain.AttackPool;
import akyrion.thepredict.domain.GroupFairnessReason;
import akyrion.thepredict.domain.GroupFairnessRules;
import akyrion.thepredict.domain.ReadyCastOrder;
import akyrion.thepredict.domain.TelegraphEnd;
import akyrion.thepredict.domain.TokenEnd;

import java.util.Optional;

/**
 * Reserved impacts and tokens of one encounter, per player (M25). Sixteen impacts, never evicting a live one.
 * A feint frees both the impact and the token and is not a past impact. A miss stays as a past impact.
 */
public final class AttackLedger {
    private static final int PLAYERS = 16;
    private static final int RING = GroupFairnessRules.IMPACT_RING;
    private static final int SLOTS = 4;

    private final long[] impactTick = new long[PLAYERS * RING];
    private final int[] impactMember = new int[PLAYERS * RING];
    private final int[] impactMargin = new int[PLAYERS * RING];
    private final boolean[] impactLive = new boolean[PLAYERS * RING];
    private final int[] impactCount = new int[PLAYERS];
    private final int[] holder = new int[PLAYERS * 2 * SLOTS];
    private final long[] grantedAt = new long[PLAYERS * 2 * SLOTS];
    private final boolean[] locked = new boolean[PLAYERS * 2 * SLOTS];
    private final boolean[] reserved = new boolean[PLAYERS * 2 * SLOTS];
    private final int[] heldWeight = new int[PLAYERS * 2 * SLOTS];
    private TokenEnd lastClosed = TokenEnd.RELEASED;
    private GroupFairnessReason lastFairness = GroupFairnessReason.TOKEN_CAPACITY;
    private final MobSession[] bound = new MobSession[16];
    private final int[] boundSlot = new int[16];
    private int boundCount;
    private long rearmTick;

    /** Creates an empty ledger. */
    public AttackLedger() {
        java.util.Arrays.fill(holder, -1);
        java.util.Arrays.fill(boundSlot, -1);
    }

    /**
     * Members whose ledger wait a deleted reservation should clear.
     *
     * @param members sessions in encounter order
     * @param slots stable member index of each session
     * @param count how many
     */
    public void bind(MobSession[] members, int[] slots, int count) {
        boundCount = Math.min(16, Math.max(0, count));
        for (var index = 0; index < boundCount; index++) {
            bound[index] = members[index];
            boundSlot[index] = slots[index];
        }
    }

    /**
     * Whether this member still holds the pool.
     *
     * @param player player index
     * @param who member index
     * @param asked pool
     * @param now current tick
     * @param idleTicks idle timeout
     * @return whether the token is theirs
     */
    public boolean holds(int player, int who, AttackPool asked, long now, int idleTicks) {
        var slot = find(player, asked, who);
        if (slot < 0) {
            return false;
        }
        if (!locked[slot] && reserved[slot]) {
            var yielded = ReadyCastOrder.yieldReservation((int) (now - grantedAt[slot]), idleTicks);
            if (yielded.isPresent()) {
                clear(slot, yielded.get());
                return false;
            }
            return true;
        }
        if (!locked[slot] && now - grantedAt[slot] >= idleTicks) {
            clear(slot, TokenEnd.IDLE_TIMEOUT);
            return false;
        }
        return true;
    }

    /**
     * Marks the token as a pattern reservation. An ordinary token is unchanged.
     *
     * @param player player index
     * @param who member index
     * @param asked pool
     */
    public void markReserved(int player, int who, AttackPool asked) {
        var slot = find(player, asked, who);
        if (slot >= 0) {
            reserved[slot] = true;
        }
    }

    /**
     * How the last token clear ended. A reservation ends as {@link TokenEnd#YIELDED}.
     *
     * @return the end
     */
    public TokenEnd lastClosed() {
        return lastClosed;
    }

    /**
     * Cause stored beside the last token clear. A cap steal is {@link GroupFairnessReason#CAPACITY_LOWERED}
     * with {@link TokenEnd#STOLEN} (errata E25-6).
     *
     * @return the cause
     */
    public GroupFairnessReason lastFairness() {
        return lastFairness;
    }

    /**
     * Whether this member holds the pool, without applying the idle timeout.
     *
     * @param player player index
     * @param who member index
     * @param asked pool
     * @return whether the token is stored
     */
    public boolean holding(int player, int who, AttackPool asked) {
        return find(player, asked, who) >= 0;
    }

    /**
     * Whether any pool of this member is locked.
     *
     * @param who member index
     * @return whether a locked token remains
     */
    public boolean anyLocked(int who) {
        for (var player = 0; player < PLAYERS; player++) {
            for (var asked : AttackPool.values()) {
                var slot = find(player, asked, who);
                if (slot >= 0 && locked[slot]) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Grants a token when the remaining weight allows it.
     *
     * @param player player index
     * @param who member index
     * @param asked pool
     * @param now current tick
     * @param cap weight cap
     * @param unit weight of this member
     * @return empty when granted
     */
    public Optional<GroupFairnessReason> grant(int player, int who, AttackPool asked, long now, int cap, int unit) {
        if (find(player, asked, who) >= 0) {
            return Optional.empty();
        }
        if (used(player, asked) + unit > cap) {
            return Optional.of(GroupFairnessReason.TOKEN_CAPACITY);
        }
        var empty = find(player, asked, -1);
        if (empty < 0) {
            return Optional.of(GroupFairnessReason.TOKEN_CAPACITY);
        }
        holder[empty] = who;
        grantedAt[empty] = now;
        locked[empty] = false;
        heldWeight[empty] = unit;
        return Optional.empty();
    }

    /**
     * Reserves an impact after dropping entries older than the retention.
     *
     * @param player player index
     * @param who member index
     * @param impact forecast tick
     * @param now current tick
     * @param retention ticks a past impact is kept
     * @param gap minimum gap against other members
     * @return the result
     */
    public Reserve reserve(int player, int who, long impact, long now, int retention, int gap) {
        return reserve(player, who, impact, now, retention, gap, 0);
    }

    /**
     * Reserves an impact. A projectile widens the gap by {@code margin} on each side.
     *
     * @param player player index
     * @param who member index
     * @param impact forecast tick
     * @param now current tick
     * @param retention ticks a past impact is kept
     * @param gap minimum gap against other members
     * @param margin extra ticks on this side, zero when the cast is not a projectile
     * @return the result
     */
    public Reserve reserve(int player, int who, long impact, long now, int retention, int gap, int margin) {
        evict(player, now - retention);
        if (!gapAllows(player, who, impact, gap, margin)) {
            return new Reserve(Optional.of(GroupFairnessReason.IMPACT_GAP), now + gap + margin);
        }
        if (impactCount[player] >= RING) {
            return new Reserve(Optional.of(GroupFairnessReason.RESERVATION_RING_FULL), oldest(player) + retention);
        }
        var at = player * RING + impactCount[player];
        impactTick[at] = impact;
        impactMember[at] = who;
        impactMargin[at] = Math.max(0, margin);
        impactLive[at] = true;
        impactCount[player]++;
        return new Reserve(Optional.empty(), impact);
    }

    /**
     * Forgets a feint. It is not a past impact.
     *
     * @param player player index
     * @param who member index
     */
    public void feint(int player, int who) {
        remove(player, who, true);
    }

    /**
     * Drops the impact just inserted when the tell is refused. In-flight impacts stay.
     *
     * @param player player index
     * @param who member index
     * @param impact forecast tick of the fresh reservation
     */
    public void dropFresh(int player, int who, long impact) {
        if (player < 0 || player >= PLAYERS) {
            return;
        }
        var kept = 0;
        for (var index = 0; index < impactCount[player]; index++) {
            var at = player * RING + index;
            if (impactMember[at] == who && impactLive[at] && impactTick[at] == impact) {
                continue;
            }
            copyImpact(player, kept, at);
            kept++;
        }
        impactCount[player] = kept;
    }

    /**
     * Closes one impact whose forecast tick is {@code now}. Prefer {@link #closeImpact(int, int, TelegraphEnd, long, long)}
     * when the close tick and the forecast differ.
     *
     * @param player player index
     * @param who member index
     * @param end how the tell closed
     * @param now tick of the close, also the forecast when the caller stored them equal
     */
    public void closeImpact(int player, int who, TelegraphEnd end, long now) {
        closeImpact(player, who, end, now, now);
    }

    /**
     * Closes the reservation of one impact. A feint deletes that row and is not past. A whiff and a landing stay
     * past. Any other end deletes that row and rearms waiters. Other live rows of the same member stay.
     *
     * @param player player index
     * @param who member index
     * @param end how the tell closed
     * @param now tick of the close, used to rearm
     * @param impact forecast tick of the reservation
     */
    public void closeImpact(int player, int who, TelegraphEnd end, long now, long impact) {
        if (player < 0 || player >= PLAYERS || end == null) {
            return;
        }
        rearmTick = now;
        switch (end) {
            case FEINTED -> dropFresh(player, who, impact);
            case WHIFFED, RELEASED -> markPastImpact(player, who, impact);
            default -> dropFresh(player, who, impact);
        }
        var token = end == TelegraphEnd.RELEASED ? TokenEnd.RELEASED : TokenEnd.TELEGRAPH_CLOSED;
        releaseToken(player, who, token);
        if (end != TelegraphEnd.FEINTED && end != TelegraphEnd.WHIFFED && end != TelegraphEnd.RELEASED) {
            rearm(who);
        }
    }

    /**
     * Forgets a cast that will not land, including an unlocked token.
     *
     * @param player player index
     * @param who member index
     */
    public void cancel(int player, int who) {
        remove(player, who, true);
        cancelUnlocked(player, who);
    }

    /**
     * Drops unlocked tokens of a member who left, on every player. Locked tokens stay until the tell ends.
     *
     * @param who member index
     */
    public void cancelUnlocked(int who) {
        for (var player = 0; player < PLAYERS; player++) {
            cancelUnlocked(player, who);
        }
    }

    /**
     * Locks the token until the tell ends.
     *
     * @param player player index
     * @param who member index
     * @param asked pool
     */
    public void lock(int player, int who, AttackPool asked) {
        var slot = find(player, asked, who);
        if (slot >= 0) {
            locked[slot] = true;
        }
    }

    /**
     * Whether the token is locked.
     *
     * @param player player index
     * @param who member index
     * @param asked pool
     * @return whether a tell holds it
     */
    public boolean locked(int player, int who, AttackPool asked) {
        var slot = find(player, asked, who);
        return slot >= 0 && locked[slot];
    }

    /**
     * Moves an unlocked token to {@code challenger} when the caller already checked the score margin.
     *
     * @param player player index
     * @param asked pool
     * @param holderIndex member who holds it
     * @param challenger member who takes it
     * @param now current tick
     * @param unit weight of the challenger
     * @return whether the token moved
     */
    public boolean stealUnlocked(int player, AttackPool asked, int holderIndex, int challenger, long now, int unit) {
        var slot = find(player, asked, holderIndex);
        if (slot < 0 || locked[slot] || challenger == holderIndex) {
            return false;
        }
        clear(slot, TokenEnd.STOLEN);
        return grant(player, challenger, asked, now, Integer.MAX_VALUE, unit).isEmpty();
    }

    /**
     * Returns one unlocked token of lowest weight when the cap fell.
     *
     * @param player player index
     * @param asked pool
     * @param cap the new cap
     * @return whether a token was stolen
     */
    public boolean stealExcess(int player, AttackPool asked, int cap) {
        if (player < 0 || used(player, asked) <= cap) {
            return false;
        }
        var victim = -1;
        var lightest = Integer.MAX_VALUE;
        for (var offset = 0; offset < SLOTS; offset++) {
            var slot = base(player, asked) + offset;
            if (holder[slot] >= 0 && !locked[slot] && heldWeight[slot] < lightest) {
                lightest = heldWeight[slot];
                victim = slot;
            }
        }
        if (victim < 0) {
            return false;
        }
        clear(victim, TokenEnd.STOLEN);
        lastFairness = GroupFairnessReason.CAPACITY_LOWERED;
        return true;
    }

    /**
     * Entries still stored for one player.
     *
     * @param player player index
     * @return the count
     */
    public int size(int player) {
        return player < 0 || player >= PLAYERS ? 0 : impactCount[player];
    }

    /**
     * Weight currently held.
     *
     * @param player player index
     * @param asked pool
     * @return the sum of holder weights
     */
    public int used(int player, AttackPool asked) {
        if (player < 0 || player >= PLAYERS) {
            return 0;
        }
        var sum = 0;
        for (var offset = 0; offset < SLOTS; offset++) {
            var slot = base(player, asked) + offset;
            if (holder[slot] >= 0) {
                sum += heldWeight[slot];
            }
        }
        return sum;
    }

    /**
     * Member who holds this pool, or -1.
     *
     * @param player player index
     * @param asked pool
     * @param offset slot inside the pool
     * @return the member index
     */
    public int holderAt(int player, AttackPool asked, int offset) {
        if (player < 0 || player >= PLAYERS || offset < 0 || offset >= SLOTS) {
            return -1;
        }
        return holder[base(player, asked) + offset];
    }

    private boolean gapAllows(int player, int who, long impact, int gap, int margin) {
        for (var index = 0; index < impactCount[player]; index++) {
            var at = player * RING + index;
            if (impactMember[at] == who) {
                continue;
            }
            var delta = impactTick[at] - impact;
            if (delta < 0) {
                delta = -delta;
            }
            if (delta < gap + margin + impactMargin[at]) {
                return false;
            }
        }
        return true;
    }

    private void evict(int player, long limit) {
        var kept = 0;
        for (var index = 0; index < impactCount[player]; index++) {
            var at = player * RING + index;
            if (!impactLive[at] && impactTick[at] < limit) {
                continue;
            }
            copyImpact(player, kept, at);
            kept++;
        }
        impactCount[player] = kept;
    }

    private long oldest(int player) {
        var oldest = Long.MAX_VALUE;
        for (var index = 0; index < impactCount[player]; index++) {
            oldest = Math.min(oldest, impactTick[player * RING + index]);
        }
        return oldest == Long.MAX_VALUE ? 0 : oldest;
    }

    private void markPastImpact(int player, int who, long impact) {
        for (var index = 0; index < impactCount[player]; index++) {
            var at = player * RING + index;
            if (impactMember[at] == who && impactLive[at] && impactTick[at] == impact) {
                impactLive[at] = false;
            }
        }
    }

    private void remove(int player, int who, boolean liveOnly) {
        var kept = 0;
        for (var index = 0; index < impactCount[player]; index++) {
            var at = player * RING + index;
            if (impactMember[at] == who && (!liveOnly || impactLive[at])) {
                continue;
            }
            copyImpact(player, kept, at);
            kept++;
        }
        impactCount[player] = kept;
    }

    private void copyImpact(int player, int kept, int from) {
        var to = player * RING + kept;
        impactTick[to] = impactTick[from];
        impactMember[to] = impactMember[from];
        impactMargin[to] = impactMargin[from];
        impactLive[to] = impactLive[from];
    }

    private void releaseToken(int player, int who, TokenEnd end) {
        for (var asked : AttackPool.values()) {
            var slot = find(player, asked, who);
            if (slot >= 0) {
                clear(slot, end);
            }
        }
    }

    private void cancelUnlocked(int player, int who) {
        for (var asked : AttackPool.values()) {
            var slot = find(player, asked, who);
            if (slot >= 0 && !locked[slot]) {
                clear(slot, TokenEnd.HOLDER_GONE);
            }
        }
    }

    private void rearm(int except) {
        for (var index = 0; index < boundCount; index++) {
            if (bound[index] != null && boundSlot[index] != except) {
                bound[index].ledgerWaitUntilTick(rearmTick);
            }
        }
    }

    private int find(int player, AttackPool asked, int who) {
        if (player < 0 || player >= PLAYERS) {
            return -1;
        }
        for (var offset = 0; offset < SLOTS; offset++) {
            var slot = base(player, asked) + offset;
            if (holder[slot] == who) {
                return slot;
            }
        }
        return -1;
    }

    private void clear(int slot, TokenEnd end) {
        lastClosed = end;
        holder[slot] = -1;
        locked[slot] = false;
        reserved[slot] = false;
        grantedAt[slot] = 0;
        heldWeight[slot] = 0;
    }

    private static int base(int player, AttackPool asked) {
        return (player * 2 + asked.ordinal()) * SLOTS;
    }

    /**
     * Result of reserving one impact.
     *
     * @param reason empty when the impact was stored
     * @param waitUntil tick the caller should wait for
     */
    public record Reserve(Optional<GroupFairnessReason> reason, long waitUntil) {
    }
}
