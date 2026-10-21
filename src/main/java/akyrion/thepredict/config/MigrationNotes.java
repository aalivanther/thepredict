package akyrion.thepredict.config;

import java.util.ArrayList;
import java.util.List;

/** Collects the conversion decisions and the points that need manual review while planning a migration. */
final class MigrationNotes {
    private final List<String> decisions = new ArrayList<>();
    private final List<String> manual = new ArrayList<>();

    /** Records an automatic conversion decision. */
    void decision(String text) {
        decisions.add(text);
    }

    /** Records a point the administrator must correct by hand. */
    void manual(String text) {
        manual.add(text);
    }

    /** Returns the decisions in the order they were made. */
    List<String> decisions() {
        return List.copyOf(decisions);
    }

    /** Returns the points that need manual review. */
    List<String> manual() {
        return List.copyOf(manual);
    }
}
