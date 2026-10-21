package akyrion.thepredict.admin;

/**
 * Text of {@code /thepredict zone} (M24.8, errata E24-6). The permission is {@code thepredict.admin}, the same one
 * {@code inspect} uses. A live map is not printed until a zone is ready.
 */
public final class ZoneDump {
    /** The line the command prints when no ready zone is attached to the sender. */
    public static final String LINE = "No ready zone. Effective mode is the minimum of zone.mode and Zone.Mode. "
            + "Permission: thepredict.admin.";

    private ZoneDump() {
    }
}
