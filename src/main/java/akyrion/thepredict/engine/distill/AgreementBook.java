package akyrion.thepredict.engine.distill;

import akyrion.thepredict.domain.distill.AgreementStatDelta;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shadow and audit agreement rows of one session (M29.1, errata E29-1 applied). Shadow uses the value table.
 * An audit comparison uses the closed windows. Every prepared window remembers its own cell (artifact, regime and
 * fine context), so a later capture in another cell never relabels it. At the close, delegated and audited rewards
 * are paired in order inside each cell, never across cells, and each row carries the keys of its cell. At most
 * {@value #MAX_WINDOWS} rewards per arm wait for the close (rule A5); later ones are dropped.
 */
public final class AgreementBook {
    /** Rewards kept per arm until the encounter closes. */
    public static final int MAX_WINDOWS = 256;

    private Cell shadowCell;
    private double shadowDelta;
    private boolean shadowAgreed;
    private boolean shadowMeasured;
    private Cell auditCell;
    private Cell delegatedCell;
    private final Map<Cell, ArrayDeque<Double>> audits = new LinkedHashMap<>();
    private final Map<Cell, ArrayDeque<Double>> delegations = new LinkedHashMap<>();
    private int auditCount;
    private int delegatedCount;

    /**
     * Starts with no pending comparison.
     */
    public AgreementBook() {
    }

    /**
     * Legacy entry point: records a shadow comparison whose two values were usable.
     *
     * @param artifactId artifact token
     * @param regimeKey regime key
     * @param contextKey fine context
     * @param delta student value minus executed value
     * @param agreed whether the tactics matched
     */
    public void prepareShadow(String artifactId, String regimeKey, String contextKey, double delta, boolean agreed) {
        prepareShadow(artifactId, regimeKey, contextKey, delta, agreed, true);
    }

    /**
     * Records a shadow comparison. The delta is the table, not an outcome. Without two usable values the delta
     * measures nothing, so the row only counts the comparison and adds no evidence to {@code n}.
     *
     * @param artifactId artifact token
     * @param regimeKey regime key
     * @param contextKey fine context
     * @param delta student value minus executed value
     * @param agreed whether the tactics matched
     * @param measured whether both values came from usable cells
     */
    public void prepareShadow(String artifactId, String regimeKey, String contextKey, double delta, boolean agreed,
                              boolean measured) {
        shadowCell = new Cell(artifactId, regimeKey, contextKey);
        shadowDelta = delta;
        shadowAgreed = agreed;
        shadowMeasured = measured;
    }

    /**
     * Marks the next closed window as the audit arm of this cell.
     *
     * @param artifactId artifact token
     * @param regimeKey regime key
     * @param contextKey fine context
     */
    public void prepareAudit(String artifactId, String regimeKey, String contextKey) {
        auditCell = new Cell(artifactId, regimeKey, contextKey);
    }

    /**
     * Marks the next closed window as the delegated arm of this cell.
     *
     * @param artifactId artifact token
     * @param regimeKey regime key
     * @param contextKey fine context
     */
    public void prepareDelegated(String artifactId, String regimeKey, String contextKey) {
        delegatedCell = new Cell(artifactId, regimeKey, contextKey);
    }

    /**
     * Emits the shadow row of one closed window and stores an audit or delegated reward in its cell.
     *
     * @param profile profile id
     * @param effect window effect
     * @param epochMs wall time supplied by the adapter
     * @return the shadow row, or empty
     */
    public List<AgreementStatDelta> onWindow(String profile, double effect, long epochMs) {
        var rows = new ArrayList<AgreementStatDelta>(1);
        if (shadowCell != null) {
            var n = shadowMeasured ? 1 : 0;
            var delta = shadowMeasured ? shadowDelta : 0.0;
            rows.add(row(shadowCell, profile, epochMs, n, delta, delta * delta, 1, shadowAgreed ? 1 : 0, 0, 0));
            shadowCell = null;
        }
        if (auditCell != null) {
            auditCount += keep(audits, auditCell, effect, auditCount);
            auditCell = null;
        }
        if (delegatedCell != null) {
            delegatedCount += keep(delegations, delegatedCell, effect, delegatedCount);
            delegatedCell = null;
        }
        return List.copyOf(rows);
    }

    /**
     * One outcome delta per pair of a delegated and an audited window of the same cell, in order. Unpaired rewards
     * are dropped with the encounter.
     *
     * @param profile profile id
     * @param epochMs wall time supplied by the adapter
     * @return the rows
     */
    public List<AgreementStatDelta> auditClose(String profile, long epochMs) {
        var rows = new ArrayList<AgreementStatDelta>();
        for (var entry : delegations.entrySet()) {
            var audited = audits.get(entry.getKey());
            while (audited != null && !audited.isEmpty() && !entry.getValue().isEmpty()) {
                var delta = entry.getValue().removeFirst() - audited.removeFirst();
                var agreed = Math.abs(delta) < 1.0E-9 ? 1 : 0;
                rows.add(row(entry.getKey(), profile, epochMs, 1, delta, delta * delta, 0, 0, 1, agreed));
            }
        }
        audits.clear();
        delegations.clear();
        auditCount = 0;
        delegatedCount = 0;
        return List.copyOf(rows);
    }

    private static int keep(Map<Cell, ArrayDeque<Double>> arm, Cell cell, double reward, int stored) {
        if (stored >= MAX_WINDOWS) {
            return 0;
        }
        arm.computeIfAbsent(cell, ignored -> new ArrayDeque<>()).addLast(reward);
        return 1;
    }

    private static AgreementStatDelta row(Cell cell, String profile, long epochMs, int n, double sum, double squares,
                                          int compared, int agreed, int audited, int auditAgreed) {
        var week = (int) Math.floorDiv(epochMs, 604_800_000L);
        return new AgreementStatDelta(cell.artifact, profile, cell.regime, cell.context, week, n, sum, squares,
                compared, agreed, audited, auditAgreed, epochMs);
    }

    private record Cell(String artifact, String regime, String context) {
    }
}
