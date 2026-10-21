package akyrion.thepredict.domain.distill;

import java.util.Optional;

/**
 * One distilled pick (M29.2).
 *
 * @param tactic the tactic, empty when none of the order is a candidate
 * @param source where it came from
 * @param leafOrCell cell text or leaf id
 * @param rank rank inside the order, 0 best
 * @param strengthMillis Luce strength in thousandths
 * @param path inspect text
 * @param ood why it was not covered, or empty
 */
public record DistilledChoice(Optional<String> tactic, DistillSource source, String leafOrCell, int rank,
                              int strengthMillis, String path, Optional<OodCause> ood) {
    /**
     * A pick.
     *
     * @param tactic the tactic
     * @param source the source
     * @param leafOrCell cell or leaf
     * @param rank rank
     * @param strengthMillis strength
     * @param path inspect path
     * @return the choice
     */
    public static DistilledChoice picked(String tactic, DistillSource source, String leafOrCell, int rank,
                                         int strengthMillis, String path) {
        return new DistilledChoice(Optional.of(tactic), source, leafOrCell, rank, strengthMillis, path,
                Optional.empty());
    }

    /**
     * An out-of-coverage result. The student does not decide.
     *
     * @param cause the cause
     * @return the choice
     */
    public static DistilledChoice outside(OodCause cause) {
        return new DistilledChoice(Optional.empty(), DistillSource.LOCAL_ORDER, "", 0, 0, "", Optional.of(cause));
    }
}
