package akyrion.thepredict.domain;

/**
 * Which ladder section a fight uses (M25, errata E25-5). One member uses {@link #SOLO}.
 */
public enum DifficultySection {
    /** The solo columns, identical to ladder v3. */
    SOLO,
    /** The group columns. */
    GROUP
}
