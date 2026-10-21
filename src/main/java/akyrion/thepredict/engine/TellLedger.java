package akyrion.thepredict.engine;

import akyrion.thepredict.domain.DangerClass;
import akyrion.thepredict.domain.GroupFairnessReason;
import akyrion.thepredict.domain.TelegraphEnd;
import akyrion.thepredict.domain.TellAnswer;

import java.util.Optional;

/**
 * Open tells against one player (M25). One heavy or severe, at most two medium of the same answer,
 * and a leader reserve that lasts at most {@code leaderReserveTicks + 10}. Every entry checks the player index the
 * same way: an index outside the 16 rows keeps nothing, holds nothing back and never throws.
 */
public final class TellLedger {
    private static final int PLAYERS = 16;
    private static final int RING = 4;

    private final int[] heavy = new int[PLAYERS];
    private final int[] medium = new int[PLAYERS];
    private final int[] answer = new int[PLAYERS];
    private final long[] bigTick = new long[PLAYERS * RING];
    private final boolean[] bigHeavy = new boolean[PLAYERS * RING];
    private final boolean[] bigLive = new boolean[PLAYERS * RING];
    private final int[] bigCount = new int[PLAYERS];
    private final long[] leaderUntil = new long[PLAYERS];
    private final long[] leaderImpact = new long[PLAYERS];
    private final long[] leaderQuietUntil = new long[PLAYERS];
    private boolean leaderExpired;

    /** Creates an empty ledger. */
    public TellLedger() {
        java.util.Arrays.fill(answer, -1);
    }

    /**
     * Whether a tell of this class may open.
     *
     * @param player player index
     * @param danger class of the tell
     * @param told answer the tell asks for
     * @param impact forecast impact
     * @param now current tick
     * @param spacing big-impact spacing
     * @param mediumCap medium tells allowed
     * @param firstHand whether the caster sees the target
     * @param leader whether the caster is the leader
     * @return empty when the tell may open
     */
    public Optional<GroupFairnessReason> admit(int player, DangerClass danger, TellAnswer told, long impact, long now,
                                              int spacing, int mediumCap, boolean firstHand, boolean leader) {
        return tracked(player) ? refusal(player, danger, told, impact, now, spacing, mediumCap, firstHand, leader)
                : Optional.empty();
    }

    private Optional<GroupFairnessReason> refusal(int player, DangerClass danger, TellAnswer told, long impact,
                                                  long now, int spacing, int mediumCap, boolean firstHand,
                                                  boolean leader) {
        if (danger.ordinal() >= DangerClass.MEDIUM.ordinal() && !firstHand) {
            return Optional.of(GroupFairnessReason.SECOND_HAND);
        }
        if (danger.ordinal() >= DangerClass.HEAVY.ordinal() && heavy[player] > 0) {
            return Optional.of(GroupFairnessReason.BIG_TELL_OPEN);
        }
        if (danger == DangerClass.MEDIUM) {
            if (medium[player] >= mediumCap) {
                return Optional.of(GroupFairnessReason.MEDIUM_CAP);
            }
            if (medium[player] > 0 && answer[player] != told.ordinal()) {
                return Optional.of(GroupFairnessReason.ANSWER_CONFLICT);
            }
        }
        if (danger.ordinal() >= DangerClass.MEDIUM.ordinal()
                && !spaced(player, impact, spacing, true, danger.ordinal() >= DangerClass.HEAVY.ordinal())) {
            return Optional.of(GroupFairnessReason.BIG_IMPACT_SPACING);
        }
        if (!leader && leaderUntil[player] > now && danger.ordinal() >= DangerClass.HEAVY.ordinal()) {
            return Optional.of(GroupFairnessReason.LEADER_RESERVE);
        }
        if (!leader && leaderUntil[player] > now && danger.ordinal() >= DangerClass.MEDIUM.ordinal()
                && Math.abs(impact - leaderImpact[player]) < spacing) {
            return Optional.of(GroupFairnessReason.LEADER_RESERVE);
        }
        return Optional.empty();
    }

    /**
     * Records a tell that opened.
     *
     * @param player player index
     * @param danger class
     * @param told answer
     * @param impact forecast impact
     * @param leader whether the caster is the leader
     */
    public void open(int player, DangerClass danger, TellAnswer told, long impact, boolean leader) {
        if (!tracked(player)) {
            return;
        }
        if (danger.ordinal() >= DangerClass.HEAVY.ordinal()) {
            heavy[player]++;
            pushBig(player, impact, true);
        } else if (danger == DangerClass.MEDIUM) {
            medium[player]++;
            answer[player] = told.ordinal();
            pushBig(player, impact, false);
        }
        if (leader) {
            leaderUntil[player] = 0;
        }
    }

    /**
     * Publishes the leader reserve.
     *
     * @param player player index
     * @param now current tick
     * @param reserveTicks configured reserve
     * @param impact forecast impact
     * @return whether it was published
     */
    public boolean reserveLeader(int player, long now, int reserveTicks, long impact) {
        if (!tracked(player) || now < leaderQuietUntil[player] || leaderUntil[player] > now) {
            return false;
        }
        leaderUntil[player] = now + reserveTicks + 10L;
        leaderImpact[player] = impact;
        return true;
    }

    /**
     * Expires a reserve that never opened.
     *
     * @param player player index
     * @param now current tick
     * @return whether this call expired it
     */
    public boolean expireLeader(int player, long now) {
        if (!tracked(player) || leaderUntil[player] == 0 || leaderUntil[player] > now) {
            return false;
        }
        leaderUntil[player] = 0;
        leaderQuietUntil[player] = now + 40;
        leaderExpired = true;
        return true;
    }

    /**
     * Whether this player's leader reserve is still open.
     *
     * @param player player index
     * @param now current tick
     * @return whether another member must yield
     */
    public boolean leaderHolding(int player, long now) {
        return tracked(player) && leaderUntil[player] > now;
    }

    /**
     * Whether any player still has a leader reserve.
     *
     * @param now current tick
     * @return whether a reserve is open
     */
    public boolean anyLeader(long now) {
        for (var player = 0; player < PLAYERS; player++) {
            if (leaderUntil[player] > now) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a reserve expired since the last read.
     *
     * @return whether {@code LEADER_RESERVE_EXPIRED} should be counted
     */
    public boolean takeExpired() {
        var expired = leaderExpired;
        leaderExpired = false;
        return expired;
    }

    /**
     * Closes one tell. A feint removes its big impact.
     *
     * @param player player index
     * @param danger class
     * @param feint whether the tell was a feint
     * @param impact forecast impact
     */
    public void close(int player, DangerClass danger, boolean feint, long impact) {
        if (!tracked(player)) {
            return;
        }
        releaseCount(player, danger);
        if (feint) {
            removeBig(player, impact, danger.ordinal() >= DangerClass.HEAVY.ordinal());
        } else {
            markPast(player, impact, danger.ordinal() >= DangerClass.HEAVY.ordinal());
        }
    }

    /**
     * Closes one tell by its end. A feint and any end without an impact delete the ring entry. A whiff and a landing
     * stay as past impacts. Past entries older than {@code now - retention} are dropped and live ones are not.
     *
     * @param player player index
     * @param danger class
     * @param end how the tell closed
     * @param impact forecast impact
     * @param now current tick
     * @param retention ticks a past impact is kept
     */
    public void closeEnd(int player, DangerClass danger, TelegraphEnd end, long impact, long now, int retention) {
        if (!tracked(player) || end == null) {
            return;
        }
        evictPast(player, now, retention);
        releaseCount(player, danger);
        var heavyTell = danger.ordinal() >= DangerClass.HEAVY.ordinal();
        if (end == TelegraphEnd.WHIFFED || end == TelegraphEnd.RELEASED) {
            markPast(player, impact, heavyTell);
            return;
        }
        removeBig(player, impact, heavyTell);
    }

    /**
     * Drops past big impacts older than {@code now - retention}. A live tell stays.
     *
     * @param player player index
     * @param now current tick
     * @param retention ticks a past impact is kept
     */
    public void evictPast(int player, long now, int retention) {
        if (!tracked(player)) {
            return;
        }
        var limit = now - retention;
        var kept = 0;
        for (var index = 0; index < bigCount[player]; index++) {
            var at = player * RING + index;
            if (!bigLive[at] && bigTick[at] < limit) {
                continue;
            }
            copyBig(player, kept, at);
            kept++;
        }
        bigCount[player] = kept;
    }

    /** Whether the ledger keeps rows for this player index: 0 to 15, the participant cap of an encounter (A5). */
    private static boolean tracked(int player) {
        return player >= 0 && player < PLAYERS;
    }

    private void releaseCount(int player, DangerClass danger) {
        if (danger.ordinal() >= DangerClass.HEAVY.ordinal() && heavy[player] > 0) {
            heavy[player]--;
        }
        if (danger == DangerClass.MEDIUM && medium[player] > 0) {
            medium[player]--;
            if (medium[player] == 0) {
                answer[player] = -1;
            }
        }
    }

    private boolean spaced(int player, long impact, int spacing, boolean heavyWanted, boolean mediumWanted) {
        for (var index = 0; index < bigCount[player]; index++) {
            var at = player * RING + index;
            if (bigHeavy[at] && !heavyWanted) {
                continue;
            }
            if (!bigHeavy[at] && !mediumWanted) {
                continue;
            }
            var delta = bigTick[at] - impact;
            if (delta < 0) {
                delta = -delta;
            }
            if (delta < spacing) {
                return false;
            }
        }
        return true;
    }

    private void pushBig(int player, long impact, boolean heavy) {
        if (bigCount[player] >= RING) {
            dropOldestPast(player);
        }
        if (bigCount[player] >= RING) {
            return;
        }
        var at = player * RING + bigCount[player];
        bigTick[at] = impact;
        bigHeavy[at] = heavy;
        bigLive[at] = true;
        bigCount[player]++;
    }

    private void dropOldestPast(int player) {
        var oldest = -1;
        var tick = Long.MAX_VALUE;
        for (var index = 0; index < bigCount[player]; index++) {
            var at = player * RING + index;
            if (!bigLive[at] && bigTick[at] < tick) {
                tick = bigTick[at];
                oldest = index;
            }
        }
        if (oldest < 0) {
            return;
        }
        var kept = 0;
        for (var index = 0; index < bigCount[player]; index++) {
            if (index == oldest) {
                continue;
            }
            copyBig(player, kept, player * RING + index);
            kept++;
        }
        bigCount[player] = kept;
    }

    private void markPast(int player, long impact, boolean heavy) {
        for (var index = 0; index < bigCount[player]; index++) {
            var at = player * RING + index;
            if (bigTick[at] == impact && bigHeavy[at] == heavy) {
                bigLive[at] = false;
            }
        }
    }

    private void copyBig(int player, int kept, int from) {
        var to = player * RING + kept;
        bigTick[to] = bigTick[from];
        bigHeavy[to] = bigHeavy[from];
        bigLive[to] = bigLive[from];
    }

    private void removeBig(int player, long impact, boolean heavy) {
        var kept = 0;
        for (var index = 0; index < bigCount[player]; index++) {
            var at = player * RING + index;
            if (bigTick[at] == impact && bigHeavy[at] == heavy) {
                continue;
            }
            copyBig(player, kept, at);
            kept++;
        }
        bigCount[player] = kept;
    }
}
