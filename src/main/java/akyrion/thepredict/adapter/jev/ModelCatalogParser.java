package akyrion.thepredict.adapter.jev;

import akyrion.thepredict.domain.DecisionResult;
import akyrion.thepredict.domain.ServiceCheck;
import akyrion.thepredict.domain.ServiceState;
import com.google.gson.JsonArray;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reads a {@code ModelMetadataList} from {@code GET /v1/models} and turns the check into a
 * {@link ServiceCheck} (M08.6). Only {@code models[].name} is read; other fields are tolerated. Pure and
 * thread-safe.
 */
final class ModelCatalogParser {
    /** Detail when the configured model is listed. */
    static final String LISTED = "model listed; this check does not prove inference or combat quality";
    /** Detail when a versioned model is absent from the list, which TypeSafe allows (plan §6, point 2). */
    static final String UNVERIFIED = "credential valid; model unverified until first inference";

    private static final Pattern VERSIONED = Pattern.compile("^[a-z0-9-]+-\\d+\\.\\d+\\.\\d+$");

    private ModelCatalogParser() {
    }

    /**
     * Parses the listed model names.
     *
     * @param body response body
     * @return the names in the order listed
     * @throws SystemOneProtocolException if the body is not a model list
     */
    static Set<String> names(String body) {
        var root = StrictJson.parseObject(body, "model list");
        if (!(root.get("models") instanceof JsonArray models)) {
            throw new SystemOneProtocolException("model list must contain a models array");
        }
        var names = new LinkedHashSet<String>();
        for (var model : models) {
            names.add(StrictJson.text(StrictJson.object(model, "models[]"), "name", "models[].name"));
        }
        return names;
    }

    /**
     * Interprets a successful model list for the configured model.
     *
     * @param body response body
     * @param model configured model ID
     * @return {@code READY}, {@code INVALID_MODEL} or, for an unreadable list, {@code PAUSED}
     */
    static ServiceCheck interpret(String body, String model) {
        try {
            if (names(body).contains(model)) {
                return new ServiceCheck(ServiceState.READY, LISTED);
            }
        } catch (SystemOneProtocolException exception) {
            return new ServiceCheck(ServiceState.PAUSED, "invalid model list: " + exception.getMessage());
        }
        return VERSIONED.matcher(model).matches() ? new ServiceCheck(ServiceState.READY, UNVERIFIED)
                : new ServiceCheck(ServiceState.INVALID_MODEL, "the configured model is not listed and is not a"
                        + " versioned model ID");
    }

    /**
     * Interprets a failed check.
     *
     * @param status HTTP status other than 200
     * @param failure the same status classified for decisions
     * @return {@code INVALID_CREDENTIALS} for 401 and 403, otherwise {@code PAUSED}
     */
    static ServiceCheck interpret(int status, DecisionResult.Failed failure) {
        var state = status == 401 || status == 403 ? ServiceState.INVALID_CREDENTIALS : ServiceState.PAUSED;
        return new ServiceCheck(state, failure.detail());
    }

    /**
     * Interprets a check that failed before a status arrived: network, timeout or local error.
     *
     * @param failure classified failure
     * @return {@code PAUSED} with the failure detail
     */
    static ServiceCheck interpret(DecisionResult.Failed failure) {
        return new ServiceCheck(ServiceState.PAUSED, failure.detail());
    }
}
