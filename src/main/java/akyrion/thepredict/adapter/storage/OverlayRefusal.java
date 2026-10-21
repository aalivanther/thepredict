package akyrion.thepredict.adapter.storage;

/**
 * Why {@code autoconfig apply} wrote nothing (M29.8): a code such as {@code OVERLAY_STALE}, then the reason. The text
 * carries no secret, so the writer hands it to the administrator as it is instead of the class name.
 */
final class OverlayRefusal extends AdminRefusal {
    private static final long serialVersionUID = 1L;

    /**
     * Creates the refusal.
     *
     * @param message code and reason
     */
    OverlayRefusal(String message) {
        super(message);
    }
}
