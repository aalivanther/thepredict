package akyrion.thepredict.engine.learn;

import akyrion.thepredict.domain.learn.CreditMath;

import java.util.Arrays;
import java.util.HashMap;

/**
 * Per-tactic factor in {@code [0.9, 1.1]} for {@code localPick} only (M28.5, errata E28-10 applied). The step is the
 * sign of the immediate part, not the deferred credit. Thousandths, no allocation per update of a known tactic.
 * Discarded when the encounter closes. Persisted only for {@code PredictorTraining}, by the caller, as ordinary value
 * rows. A tactic keeps its own slot by id, so the factor it earned follows it whatever its place in a later eligible
 * list; at most {@value #MAX_TACTICS} tactics are tracked in one fight (rule A5). The integer slots are the legacy
 * entry points of callers that still pass a list position; a caller uses ids or positions, never both.
 */
public final class InFightWeights {
    /** Tactics tracked in one fight. A tactic beyond the cap keeps the neutral factor. */
    public static final int MAX_TACTICS = 64;
    private static final int STEP = 8;
    private static final int LIMIT = 100;
    private int[] delta;
    private final HashMap<String, Integer> slots = new HashMap<>();

    /**
     * One slot per tactic index.
     *
     * @param tactics how many tactics
     */
    public InFightWeights(int tactics) {
        delta = new int[Math.max(tactics, 0)];
    }

    /**
     * Decays and steps one tactic slot. A slot past the end grows the table and keeps every earlier slot.
     *
     * @param tactic tactic index
     * @param effect immediate effect term
     * @param avoided avoided-damage term
     */
    public void observe(int tactic, double effect, double avoided) {
        if (tactic < 0 || tactic >= MAX_TACTICS) {
            return;
        }
        if (tactic >= delta.length) {
            delta = Arrays.copyOf(delta, tactic + 1);
        }
        var next = delta[tactic] * 4 / 5 + STEP * CreditMath.immediateSign(effect, avoided);
        delta[tactic] = Math.max(-LIMIT, Math.min(LIMIT, next));
    }

    /**
     * Decays and steps one tactic by id.
     *
     * @param tactic tactic id
     * @param effect immediate effect term
     * @param avoided avoided-damage term
     */
    public void observe(String tactic, double effect, double avoided) {
        var slot = slots.get(tactic);
        if (slot == null) {
            if (slots.size() >= MAX_TACTICS) {
                return;
            }
            slot = slots.size();
            slots.put(tactic, slot);
        }
        observe(slot, effect, avoided);
    }

    /**
     * Multiplicative factor in millis, 900 to 1100. A slot never observed is 1000.
     *
     * @param tactic tactic index
     * @return 1000 + delta
     */
    public int factorMillis(int tactic) {
        return tactic >= 0 && tactic < delta.length ? 1000 + delta[tactic] : 1000;
    }

    /**
     * Multiplicative factor of one tactic by id, in millis. A tactic never observed is 1000.
     *
     * @param tactic tactic id
     * @return 1000 + delta
     */
    public int factorMillis(String tactic) {
        var slot = slots.get(tactic);
        return slot == null ? 1000 : factorMillis(slot);
    }

    /**
     * How many tactic slots this fight allocated.
     *
     * @return the slot count
     */
    public int size() {
        return delta.length;
    }
}
