package akyrion.thepredict.domain.distill;

/**
 * Where a distilled pick came from (M29.2). Errata E29-3 applied: {@link #LOCAL_ORDER} is the fourth value.
 */
public enum DistillSource {
    /** A fine cell with enough offers. */
    TABLE,
    /** A leaf of the preference tree. */
    TREE,
    /** SPIBB moved the order off the local plan. */
    VALUE_OVERRIDE,
    /** The local order was kept because the value gap was not large enough. */
    LOCAL_ORDER
}
