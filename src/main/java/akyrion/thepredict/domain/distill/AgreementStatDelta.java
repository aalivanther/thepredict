package akyrion.thepredict.domain.distill;

import java.util.Objects;

/**
 * One upsert of {@code m29_agreement_stats} (M29.1, errata E29-1 applied). Agreement counters are a metric.
 * The coverage bound uses {@code n}, {@code sumDelta} and {@code sumDeltaSq}.
 *
 * @param artifact artifact fingerprint
 * @param profile profile id
 * @param regime regime key
 * @param context fine context text
 * @param week week index
 * @param n outcome comparisons
 * @param sumDelta sum of outcome differences
 * @param sumDeltaSq sum of squared differences
 * @param compared shadow comparisons
 * @param agreed shadow agreements
 * @param audited audit comparisons
 * @param auditAgreed audit agreements
 * @param epochMs wall time supplied by the adapter
 */
public record AgreementStatDelta(String artifact, String profile, String regime, String context, int week, int n,
                                 double sumDelta, double sumDeltaSq, int compared, int agreed, int audited,
                                 int auditAgreed, long epochMs) {
    /**
     * Checks the counters.
     */
    public AgreementStatDelta {
        Objects.requireNonNull(artifact, "artifact");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(regime, "regime");
        Objects.requireNonNull(context, "context");
        if (n < 0 || compared < 0 || agreed < 0 || audited < 0 || auditAgreed < 0) {
            throw new IllegalArgumentException("agreement counts");
        }
    }
}
