package akyrion.thepredict.adapter.storage;

import java.io.IOException;

/**
 * Why an administrator command was refused before it changed anything. The text carries no secret, so the writer
 * hands it to the administrator as it is instead of the class name.
 */
class AdminRefusal extends IOException {
    private static final long serialVersionUID = 1L;

    /**
     * Creates the refusal.
     *
     * @param message the reason
     */
    AdminRefusal(String message) {
        super(message);
    }
}
