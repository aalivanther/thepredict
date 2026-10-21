package akyrion.thepredict.domain;

/** Where an answer came from (M21.7). */
public enum AnswerSource {
    /** The service answered this request. */
    LIVE,
    /** The answer was copied from the response cache. */
    CACHE
}
