package akyrion.thepredict.engine;

import akyrion.thepredict.domain.DifficultyKey;
import akyrion.thepredict.domain.DifficultySection;
import akyrion.thepredict.domain.FightEventKind;
import akyrion.thepredict.domain.FightSummary;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * One rung per player of a group (M25). A single member stays on the solo section. The anchor profile is taken
 * when the clock opens and again when the leader changes.
 */
public final class GroupFightClock {
    private static final Optional<FightEventKind> NO_EVENT = Optional.empty();
    private static final Optional<FightEventKind> ENGAGED = Optional.of(FightEventKind.ENGAGED);
    private static final Optional<FightEventKind> TARGET_DIED = Optional.of(FightEventKind.TARGET_DIED);
    private static final Optional<FightEventKind> GROUP_WIPED = Optional.of(FightEventKind.GROUP_WIPED);

    /** Creates a clock that has not opened. The section stays solo until two members are seen. */
    public GroupFightClock() {
    }

    private String anchor = "";
    private String openedAnchor = "";
    private int notedRung;
    private DifficultySection section = DifficultySection.SOLO;
    private boolean open;
    private int members;
    private boolean anchorChanged;
    private Runnable closed = () -> {
    };
    private final UUID[] player = new UUID[16];
    private final boolean[] engaged = new boolean[16];
    private final long[] engagedAt = new long[16];
    private final long[] lastHit = new long[16];
    private final FightEventKind[] last = new FightEventKind[16];
    private int clocks;
    private FightSummary summary;

    /**
     * Opens one player clock on the first damage either way. The subject of the key stays empty.
     *
     * @param index stable player index, 0 to 15
     * @param playerId the player
     * @param tick server tick
     * @param died whether the player died on this hit
     * @param profile key profile when no leader was stored; it does not become the opening anchor
     * @return the event, empty when the hit only refreshes an open clock or the index has no room (EFO-24)
     */
    public Optional<FightEventKind> noteHit(int index, UUID playerId, long tick, boolean died, String profile) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(profile, "profile");
        if (index < 0 || index >= player.length) {
            return NO_EVENT;
        }
        if (player[index] == null) {
            if (clocks >= player.length) {
                return NO_EVENT;
            }
            player[index] = playerId;
            clocks++;
        }
        lastHit[index] = tick;
        if (!engaged[index]) {
            engaged[index] = true;
            engagedAt[index] = tick;
            if (!open) {
                begin(profile, Math.max(1, members));
            }
            last[index] = died ? FightEventKind.TARGET_DIED : FightEventKind.ENGAGED;
            return died ? TARGET_DIED : ENGAGED;
        }
        if (died) {
            last[index] = FightEventKind.TARGET_DIED;
            return TARGET_DIED;
        }
        return NO_EVENT;
    }

    /**
     * The player of an engaged clock died (EFO-07): that clock ends as {@code TARGET_DIED} whatever the share of the
     * last hit, so a player worn down by many small hits counts as killed. The last-hit tick does not move. A player
     * that is not stored, or whose clock is not engaged, changes nothing.
     *
     * @param playerId the player that died
     * @return whether an engaged clock noted the death
     */
    public boolean playerDied(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        for (var index = 0; index < player.length; index++) {
            if (playerId.equals(player[index]) && engaged[index]) {
                last[index] = FightEventKind.TARGET_DIED;
                return true;
            }
        }
        return false;
    }

    /**
     * Closes quiet player clocks. One quiet gap with no damage disengages that player.
     *
     * @param tick server tick
     * @param quietTicks ticks without damage
     * @return how many clocks disengaged
     */
    public int quiet(long tick, int quietTicks) {
        var closedClocks = 0;
        for (var index = 0; index < player.length; index++) {
            if (!engaged[index] || lastHit[index] == 0 || tick - lastHit[index] < quietTicks) {
                continue;
            }
            engaged[index] = false;
            last[index] = FightEventKind.DISENGAGED;
            closedClocks++;
        }
        if (closedClocks > 0 && !anyEngaged()) {
            close();
        }
        return closedClocks;
    }

    /**
     * Closes every clock because the group has no members left.
     *
     * @param tick server tick
     * @return the wiped event when a clock was open, else empty (EFO-24)
     */
    public Optional<FightEventKind> wiped(long tick) {
        var any = anyEngaged() || open;
        for (var index = 0; index < player.length; index++) {
            if (player[index] != null) {
                last[index] = FightEventKind.GROUP_WIPED;
            }
            engaged[index] = false;
        }
        if (any) {
            close();
            return GROUP_WIPED;
        }
        return NO_EVENT;
    }

    /**
     * Drops the oldest quiet clock so a new player can take its index.
     *
     * @return the freed index, or -1 when every clock is still engaged
     */
    public int evictQuietest() {
        var chosen = -1;
        var oldest = Long.MAX_VALUE;
        for (var index = 0; index < player.length; index++) {
            if (player[index] != null && !engaged[index] && engagedAt[index] < oldest) {
                oldest = engagedAt[index];
                chosen = index;
            }
        }
        if (chosen < 0) {
            return -1;
        }
        player[chosen] = null;
        last[chosen] = FightEventKind.EVICTED;
        engagedAt[chosen] = 0;
        lastHit[chosen] = 0;
        clocks = Math.max(0, clocks - 1);
        return chosen;
    }

    /**
     * Last event of one player clock.
     *
     * @param index player index
     * @return the event, or empty
     */
    public Optional<FightEventKind> last(int index) {
        if (index < 0 || index >= last.length || last[index] == null) {
            return Optional.empty();
        }
        return Optional.of(last[index]);
    }

    /**
     * How many player clocks are stored.
     *
     * @return the count, at most 16
     */
    public int clocks() {
        return clocks;
    }

    private boolean anyEngaged() {
        for (var openClock : engaged) {
            if (openClock) {
                return true;
            }
        }
        return false;
    }

    /**
     * Records how many members the next open uses. It does not open the clock.
     *
     * @param memberCount controlled members
     */
    public void noteMembers(int memberCount) {
        members = memberCount;
        if (!open) {
            section = memberCount >= 2 ? DifficultySection.GROUP : DifficultySection.SOLO;
        }
    }

    /**
     * Opens the clock on the first damage. A stored leader is kept and becomes the opening anchor. A profile fills
     * the key only when no leader was stored, and that profile is not the opening anchor.
     *
     * @param profile key profile used only when the clock has no anchor yet
     * @param memberCount controlled members
     */
    public void begin(String profile, int memberCount) {
        members = memberCount;
        if (open) {
            return;
        }
        var stored = !anchor.isEmpty();
        if (!stored && profile != null && !profile.isEmpty()) {
            anchor = profile;
        }
        open = true;
        if (stored) {
            openedAnchor = anchor;
        }
        section = memberCount >= 2 ? DifficultySection.GROUP : DifficultySection.SOLO;
    }

    /**
     * Records the leader. Before the clock opens it does not count as a change. Afterwards the change is the opening
     * leader against this one.
     *
     * @param profile the leader's profile
     */
    public void leaderChanged(String profile) {
        var next = profile == null ? "" : profile;
        if (!open) {
            anchor = next;
            return;
        }
        if (openedAnchor.isEmpty()) {
            openedAnchor = next;
            anchor = next;
            return;
        }
        if (!openedAnchor.equals(next)) {
            anchorChanged = true;
        }
        anchor = next;
    }

    /**
     * Hears a close that was open (M27, errata E25-4 applied).
     *
     * @param listener the listener
     */
    public void onClosed(Runnable listener) {
        var next = Objects.requireNonNull(listener, "listener");
        var previous = this.closed;
        this.closed = () -> {
            previous.run();
            next.run();
        };
    }

    /**
     * Anchor profile taken when the clock opened, or the later leader.
     *
     * @return the anchor, empty when the clock has not opened
     */
    public String anchor() {
        return anchor;
    }

    /**
     * Anchor stored when the clock first opened. A later leader change does not replace it.
     *
     * @return the opening anchor, empty when the clock has not opened
     */
    public String openedAnchor() {
        return openedAnchor;
    }

    /**
     * Stores the rung of the anchor session. Solo clocks do not write it.
     *
     * @param rung the rung
     */
    public void noteRung(int rung) {
        notedRung = rung;
    }

    /**
     * Rung noted for the group sample.
     *
     * @return the rung, {@code 0} until {@link #noteRung(int)}
     */
    public int rung() {
        return notedRung;
    }

    /**
     * Player stored at an index.
     *
     * @param index player index
     * @return the id, empty when the slot is empty
     */
    public Optional<UUID> playerAt(int index) {
        if (index < 0 || index >= player.length || player[index] == null) {
            return Optional.empty();
        }
        return Optional.of(player[index]);
    }

    /**
     * Log of the ticks between the first hit and the last hit of one player.
     *
     * @param playerId the player
     * @return the log time, {@code 0} when the clock has no span
     */
    public double lnElapsed(UUID playerId) {
        for (var index = 0; index < player.length; index++) {
            if (playerId.equals(player[index]) && lastHit[index] > engagedAt[index]) {
                return StrictMath.log(lastHit[index] - engagedAt[index]);
            }
        }
        return 0.0;
    }

    /**
     * Whether the anchor changed while the clock was open.
     *
     * @return whether it changed
     */
    public boolean anchorChanged() {
        return anchorChanged;
    }

    /**
     * Closes the clock.
     */
    public void close() {
        var was = open;
        open = false;
        if (was) {
            remember();
            closed.run();
        }
    }

    /**
     * Summary left by the last close. Empty when the clock has not closed.
     *
     * @return the summary
     */
    public Optional<FightSummary> summary() {
        return Optional.ofNullable(summary);
    }

    /**
     * Text of the close for {@code group_summary}. Empty when the clock has not closed.
     *
     * @return members, kind, elapsed ticks, log time and the anchor profile
     */
    public String summaryLine() {
        if (summary == null) {
            return "";
        }
        return "members=" + members + ",kind=" + summary.kind().name() + ",elapsed=" + summary.elapsedTicks()
                + ",ln=" + summary.observedLn() + ",anchor=" + anchor;
    }

    private void remember() {
        var kind = FightEventKind.DISENGAGED;
        for (var event : last) {
            if (event == FightEventKind.GROUP_WIPED) {
                kind = event;
                break;
            }
            if (event == FightEventKind.TARGET_DIED) {
                kind = event;
            } else if (event == FightEventKind.EVICTED && kind == FightEventKind.DISENGAGED) {
                kind = event;
            }
        }
        var elapsed = elapsedTicks();
        var ln = elapsed > 0 ? StrictMath.log(elapsed) : 0.0;
        summary = new FightSummary(kind, elapsed, ln, notedRung);
    }

    private int elapsedTicks() {
        var start = Long.MAX_VALUE;
        var end = 0L;
        for (var index = 0; index < player.length; index++) {
            if (player[index] == null || engagedAt[index] <= 0) {
                continue;
            }
            start = Math.min(start, engagedAt[index]);
            end = Math.max(end, lastHit[index]);
        }
        if (start == Long.MAX_VALUE) {
            return 0;
        }
        return (int) Math.max(0L, end - start);
    }

    /**
     * Whether the clock is open.
     *
     * @return whether damage has started a fight
     */
    public boolean open() {
        return open;
    }

    /**
     * Members recorded at the last begin.
     *
     * @return the count
     */
    public int members() {
        return members;
    }

    /**
     * The difficulty key of this fight.
     *
     * @param context context name
     * @return the key, section {@code group} when two or more members opened it
     */
    public DifficultyKey key(String context) {
        return new DifficultyKey(anchor, context, Optional.empty(), section);
    }
}
