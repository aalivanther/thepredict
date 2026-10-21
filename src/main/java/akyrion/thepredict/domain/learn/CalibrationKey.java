package akyrion.thepredict.domain.learn;

import akyrion.thepredict.domain.QuestionDetail;

import java.util.Objects;

/**
 * One calibrator cell (M28.4). The artifact stores the fit, not a single {@code p*} (errata E28-6 applied).
 *
 * @param model model name
 * @param detail question detail
 * @param kind question kind
 * @param target label target
 * @param options option band
 */
public record CalibrationKey(String model, QuestionDetail detail, QuestionKind kind, CalibrationTarget target,
                             OptionsBand options) {
    /**
     * Validates the key.
     */
    public CalibrationKey {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(detail, "detail");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(options, "options");
    }
}
