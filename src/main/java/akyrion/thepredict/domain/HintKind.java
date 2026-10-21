package akyrion.thepredict.domain;

/**
 * Why a subject was offered to the prefetch queue (M27.4). There is no admission kind (errata E27-6 applied).
 */
public enum HintKind {
    /** {@code AsyncPlayerPreLoginEvent} allowed the player. */
    PRE_LOGIN,
    /** The player joined, or was already online when the plugin enabled. */
    JOIN,
    /** The player left. */
    QUIT
}
