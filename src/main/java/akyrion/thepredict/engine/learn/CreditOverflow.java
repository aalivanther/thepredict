package akyrion.thepredict.engine.learn;

import akyrion.thepredict.domain.learn.CreditMath;
import akyrion.thepredict.domain.learn.RewardSpec;
import akyrion.thepredict.domain.learn.TrainingSource;
import akyrion.thepredict.domain.learn.ValueStatDelta;
import akyrion.thepredict.domain.learn.ValueStatKey;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Windows the credit ledger dropped (M28.2, rule A5). A dropped window closes with credit 0: it keeps its immediate
 * effect, its avoided damage and its source, and only the deferred credit is lost. Windows are folded per
 * (tactic, context, source) into at most {@value #KEYS} keys, in first-drop order, so a long fight cannot grow the
 * ledger. A window that finds no free key is counted by {@link #lost()} and not emitted.
 */
final class CreditOverflow {
    /** Folded keys kept until the encounter closes. */
    static final int KEYS = 4 * CreditLedger.CAP;

    private final LinkedHashMap<Key, Fold> folds = new LinkedHashMap<>();
    private int lost;

    CreditOverflow() {
    }

    /**
     * Folds one dropped window.
     *
     * @param tactic tactic id
     * @param context context text
     * @param source window source, or null for a legacy window
     * @param effect normalized effect term
     * @param avoided avoided-damage term
     * @param asked whether the decision asked Jev
     * @param agreed whether Jev matched the learned leader
     */
    void add(String tactic, String context, TrainingSource source, double effect, double avoided, boolean asked,
             boolean agreed) {
        var key = new Key(tactic, context, source);
        var fold = folds.get(key);
        if (fold == null) {
            if (folds.size() >= KEYS) {
                lost++;
                return;
            }
            fold = new Fold();
            folds.put(key, fold);
        }
        fold.add(effect, avoided, asked, agreed);
    }

    /**
     * Emits one row per folded key, with credit 0, and clears.
     *
     * @param profile profile id
     * @param legacyToken token of a window recorded without a source
     * @param week week index
     * @param creditShare η of the reward
     * @param epochMs wall time supplied by the adapter
     * @return the rows in first-drop order
     */
    List<ValueStatDelta> drain(String profile, String legacyToken, int week, double creditShare, long epochMs) {
        var rows = new ArrayList<ValueStatDelta>(folds.size());
        for (var entry : folds.entrySet()) {
            var key = entry.getKey();
            var token = key.source == null ? legacyToken : key.source.token();
            rows.add(entry.getValue().row(new ValueStatKey(profile, key.tactic, key.context, token, RewardSpec.ID,
                    week), creditShare, epochMs));
        }
        folds.clear();
        lost = 0;
        return rows;
    }

    /**
     * Windows dropped without a free key since the last drain.
     *
     * @return the count
     */
    int lost() {
        return lost;
    }

    /**
     * Folded keys waiting for the close.
     *
     * @return the count
     */
    int keys() {
        return folds.size();
    }

    private record Key(String tactic, String context, TrainingSource source) {
    }

    private static final class Fold {
        private int windows;
        private double effects;
        private double avoided;
        private double effectSquares;
        private double avoidedSquares;
        private double cross;
        private int asked;
        private int agreed;

        private void add(double effect, double avoidedDamage, boolean ask, boolean agree) {
            windows++;
            effects += effect;
            avoided += avoidedDamage;
            effectSquares += effect * effect;
            avoidedSquares += avoidedDamage * avoidedDamage;
            cross += effect * avoidedDamage;
            asked += ask ? 1 : 0;
            agreed += ask && agree ? 1 : 0;
        }

        private ValueStatDelta row(ValueStatKey key, double creditShare, long epochMs) {
            var mean = CreditMath.reward(effects / windows, 0.0, avoided / windows, creditShare);
            var alpha = 1.0 - creditShare;
            var zeta = RewardSpec.ZETA;
            var squares = alpha * alpha * effectSquares + 2.0 * alpha * zeta * cross + zeta * zeta * avoidedSquares;
            var m2 = Math.max(0.0, squares - windows * mean * mean);
            return new ValueStatDelta(key, windows, mean, m2, effects, 0.0, avoided, asked, agreed, epochMs);
        }
    }
}
