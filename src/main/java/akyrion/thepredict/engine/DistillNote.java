package akyrion.thepredict.engine;

import akyrion.thepredict.domain.distill.DistillMode;
import akyrion.thepredict.domain.distill.DistillSettings;
import akyrion.thepredict.engine.distill.DistillTrace;

/**
 * The distill object and the pattern facts of one planned line (M29.4).
 */
public final class DistillNote {
    private DistillNote() {
    }

    /**
     * One planned note.
     *
     * @param distill the object, empty when the mode is off
     * @param patternFacts the facts array, empty when collection is off
     */
    public record Note(String distill, String patternFacts) {
    }

    /**
     * Reads the traces the capture stored.
     *
     * @param settings the section in force
     * @param encounter the encounter
     * @return the note
     */
    public static Note planned(DistillSettings settings, Encounter encounter) {
        if (settings == null) {
            return new Note("", "");
        }
        var delegated = false;
        var trace = DistillTrace.EMPTY;
        var pattern = "";
        for (var session : encounter.memberSessions()) {
            delegated = delegated || session.learning().captureDelegated();
            var found = session.learning().distillSession().trace();
            if (trace == DistillTrace.EMPTY || found.delegated()) {
                trace = found;
            }
            var facts = session.learning().distillSession().patternFactsJson();
            if (pattern.isEmpty() && !facts.isEmpty()) {
                pattern = facts;
            }
        }
        var distill = settings.mode() == DistillMode.OFF ? "" : trace.withDelegated(delegated).json();
        if (!settings.collect()) {
            pattern = "";
        }
        return new Note(distill, pattern);
    }
}
