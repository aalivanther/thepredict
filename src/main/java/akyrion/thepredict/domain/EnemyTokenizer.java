package akyrion.thepredict.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Turns witnessed actions into a token stream (M26.2). The same strafe or step inside {@code mergeTicks} collapses.
 * An idle token needs a witness and {@code idleTicks} of silence. The bucket drops movement before strikes. Strikes
 * never merge. Callers present same-tick actions in strike, shield, movement, jump, sprint, bow, consume order. A hot
 * path (rule C12): offering and reading tokens allocates nothing; the tokens are shared constants.
 */
public final class EnemyTokenizer {
    private static final int WINDOW = 40;
    private static final EnemyAction[] ACTIONS = EnemyAction.values();
    private static final List<Optional<EnemyAction>> TOKENS = tokens();

    private final int mergeTicks;
    private final int idleTicks;
    private final int maxPerSecond;
    private final int[] held = new int[WINDOW];
    private int heldCount;
    private int secondStart = Integer.MIN_VALUE;
    private int inSecond;
    private int released = -1;
    private EnemyAction last = EnemyAction.IDLE;
    private int lastTick = Integer.MIN_VALUE;
    private int dropped;

    /**
     * A tokenizer with the delivered limits.
     *
     * @param mergeTicks merge window
     * @param idleTicks silence before idle
     * @param maxPerSecond bucket size
     */
    public EnemyTokenizer(int mergeTicks, int idleTicks, int maxPerSecond) {
        this.mergeTicks = mergeTicks;
        this.idleTicks = idleTicks;
        this.maxPerSecond = maxPerSecond;
    }

    /**
     * Offers one action.
     *
     * @param action the action
     * @param tick the server tick
     * @param witnessed whether a member perceived it
     * @return the token, or empty when it merged, lacked a witness or was dropped
     */
    public Optional<EnemyAction> offer(EnemyAction action, int tick, boolean witnessed) {
        if (!witnessed || action == null) {
            return Optional.empty();
        }
        if (action == EnemyAction.IDLE) {
            return idle(tick);
        }
        if (action.merges() && last == action && tick - lastTick <= mergeTicks) {
            lastTick = tick;
            return Optional.empty();
        }
        if (!admit(tick, action)) {
            dropped++;
            return Optional.empty();
        }
        last = action;
        lastTick = tick;
        return TOKENS.get(action.ordinal());
    }

    /**
     * Tokens dropped because the bucket was full.
     *
     * @return the count
     */
    public int dropped() {
        return dropped;
    }

    /**
     * The movement token a full bucket released so a strike could stay. Empty until that happens, and empty again
     * after it is read.
     *
     * @return the released strafe or step
     */
    public Optional<EnemyAction> released() {
        if (released < 0) {
            return Optional.empty();
        }
        var action = TOKENS.get(released);
        released = -1;
        return action;
    }

    private Optional<EnemyAction> idle(int tick) {
        if (lastTick == Integer.MIN_VALUE || tick - lastTick < idleTicks || last == EnemyAction.IDLE) {
            return Optional.empty();
        }
        if (!admit(tick, EnemyAction.IDLE)) {
            return Optional.empty();
        }
        last = EnemyAction.IDLE;
        lastTick = tick;
        return TOKENS.get(EnemyAction.IDLE.ordinal());
    }

    private boolean admit(int tick, EnemyAction action) {
        if (secondStart == Integer.MIN_VALUE || tick - secondStart >= 20) {
            secondStart = tick;
            inSecond = 0;
            heldCount = 0;
        }
        if (inSecond < maxPerSecond) {
            remember(action);
            inSecond++;
            return true;
        }
        if (action.sameTickRank() != 0) {
            return false;
        }
        var slot = movementSlot();
        if (slot < 0) {
            return false;
        }
        released = held[slot];
        held[slot] = action.ordinal();
        dropped++;
        return true;
    }

    private void remember(EnemyAction action) {
        if (heldCount < held.length) {
            held[heldCount++] = action.ordinal();
        }
    }

    private int movementSlot() {
        for (var index = 0; index < heldCount; index++) {
            if (ACTIONS[held[index]].merges()) {
                return index;
            }
        }
        return -1;
    }

    private static List<Optional<EnemyAction>> tokens() {
        var tokens = new ArrayList<Optional<EnemyAction>>(ACTIONS.length);
        for (var action : ACTIONS) {
            tokens.add(Optional.of(action));
        }
        return List.copyOf(tokens);
    }
}
