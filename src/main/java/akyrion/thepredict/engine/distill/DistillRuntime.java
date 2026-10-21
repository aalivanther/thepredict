package akyrion.thepredict.engine.distill;

import akyrion.thepredict.domain.distill.CoverageIndex;
import akyrion.thepredict.domain.distill.DistillSource;
import akyrion.thepredict.domain.distill.DistilledPolicy;
import akyrion.thepredict.domain.distill.OodCause;
import akyrion.thepredict.domain.distill.PackagedSource;

import java.util.List;
import java.util.Optional;

/**
 * Coverage and policy read on a capture (M29.3). A teacher-model change marks the index stale
 * (errata E29-13 applied). The teacher token stays {@code outcome}, {@code rollout} or {@code kev-*}.
 */
public final class DistillRuntime {
    /** A runtime that is never published, in place of a missing one (AE-12, C4). */
    public static final DistillRuntime EMPTY = new DistillRuntime(true);

    private final boolean inert;
    private CoverageIndex coverage = CoverageIndex.EMPTY;
    private Optional<DistilledPolicy> policy = Optional.empty();
    private boolean published;
    private List<PackagedSource.Loaded> packaged = List.of();

    /**
     * Starts unpublished. The next capture still uses the session fields.
     */
    public DistillRuntime() {
        this(false);
    }

    private DistillRuntime(boolean inert) {
        this.inert = inert;
    }

    /**
     * Whether an artifact has been published.
     *
     * @return the flag
     */
    public boolean published() {
        return published;
    }

    /**
     * The index in force.
     *
     * @return the index
     */
    public CoverageIndex coverage() {
        return coverage;
    }

    /**
     * The policy in force.
     *
     * @return the policy, empty until {@link #publish}
     */
    public Optional<DistilledPolicy> policy() {
        return policy;
    }

    /**
     * Publishes an index and a policy. The next capture reads them.
     *
     * @param index coverage
     * @param chosen policy
     */
    public void publish(CoverageIndex index, DistilledPolicy chosen) {
        if (inert) {
            return;
        }
        coverage = index;
        policy = Optional.of(chosen);
        published = true;
    }

    /**
     * Marks the published index stale. Disable is not this path.
     */
    public void markModelStale() {
        if (!inert) {
            coverage = coverage.markModelStale();
        }
    }

    /**
     * Keeps the imported packaged list. An empty import is kept too.
     *
     * @param loaded artifacts read from {@code learn/packaged}
     */
    public void notePackaged(List<PackagedSource.Loaded> loaded) {
        if (!inert) {
            packaged = List.copyOf(loaded);
        }
    }

    /**
     * Packaged artifacts imported at startup.
     *
     * @return the list, empty when none were imported
     */
    public List<PackagedSource.Loaded> packaged() {
        return packaged;
    }

    /**
     * Legacy entry point: coverage and the student pick for one capture, compared with the fingerprint and the
     * teacher the index itself holds, so a changed profile or teacher is not seen. Callers that know the live values
     * use {@link #view(String, List, long, long, int, double, int, String, String)}.
     *
     * @param context fine context text
     * @param candidates live tactic ids
     * @param packed packed fine context
     * @param nowMinute minute supplied by the caller
     * @param minDecisions minimum n
     * @param margin non-inferiority margin
     * @param maxAge maximum age in minutes
     * @return the view
     */
    public View view(String context, List<String> candidates, long packed, long nowMinute, int minDecisions,
                     double margin, int maxAge) {
        return view(context, candidates, packed, nowMinute, minDecisions, margin, maxAge,
                coverage.profileFingerprint(), coverage.teacherModel());
    }

    /**
     * Coverage and the student pick for one capture. A profile fingerprint or a teacher model that differs from the
     * index is {@code OOD_PROFILE_CHANGED} or {@code OOD_MODEL_CHANGED}, and the student does not decide.
     *
     * @param context fine context text
     * @param candidates live tactic ids
     * @param packed packed fine context
     * @param nowMinute minute supplied by the caller
     * @param minDecisions minimum n
     * @param margin non-inferiority margin
     * @param maxAge maximum age in minutes
     * @param liveFingerprint fingerprint of the profile the mob runs now
     * @param liveModel teacher model now
     * @return the view
     */
    public View view(String context, List<String> candidates, long packed, long nowMinute, int minDecisions,
                     double margin, int maxAge, String liveFingerprint, String liveModel) {
        var cause = coverage.covered(context, candidates, nowMinute, maxAge, liveFingerprint, liveModel, minDecisions,
                margin);
        if (cause.isPresent() || policy.isEmpty()) {
            return new View(false, cause, DistillSource.LOCAL_ORDER, "", "");
        }
        var choice = policy.get().choose(candidates, context, bands(packed));
        if (choice.tactic().isEmpty()) {
            return new View(false, choice.ood(), choice.source(), "", choice.leafOrCell());
        }
        return new View(true, Optional.empty(), choice.source(), choice.tactic().get(), choice.leafOrCell());
    }

    /**
     * Band values packed by {@code ContextKey}.
     *
     * @param packed the fine key
     * @return seven bands
     */
    public static int[] bands(long packed) {
        return new int[] {(int) (packed & 3L), (int) ((packed >> 2) & 3L), (int) ((packed >> 4) & 3L),
                (int) ((packed >> 6) & 1L), (int) ((packed >> 7) & 3L), (int) ((packed >> 9) & 3L),
                (int) ((packed >> 11) & 7L)};
    }

    /**
     * What one capture may delegate.
     *
     * @param covered whether the cell is covered
     * @param ood the cause when it is not
     * @param source the student source
     * @param tactic the student tactic, empty when none
     * @param leaf the cell or leaf id
     */
    public record View(boolean covered, Optional<OodCause> ood, DistillSource source, String tactic, String leaf) {
    }
}
