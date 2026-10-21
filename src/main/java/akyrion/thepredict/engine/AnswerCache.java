package akyrion.thepredict.engine;

import akyrion.thepredict.domain.AnswerCacheMode;
import akyrion.thepredict.domain.DecisionResult;
import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.QuestionKey;
import akyrion.thepredict.domain.StableRandom;
import akyrion.thepredict.domain.TacticId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Exact response cache (M21.7). The key is {@code (model, bodyChars, body.hashCode())} and a hit also requires
 * {@code model.equals} and {@code body.equals}, so a hash collision never hits. {@code jev-latest} is never stored.
 * Validity is {@code tick - originCaptureTick <= ttl}. Revalidation uses {@link #REVALIDATE_SALT} (errata E21-4),
 * a documented constant, with {@link StableRandom}.
 */
public final class AnswerCache {
    /** Salt of the revalidation draw. Documented by errata E21-4; not a configuration key. */
    public static final int REVALIDATE_SALT = 21;
    /** Most characters kept. */
    public static final int MAX_CHARS = 8_000_000;
    private static final Pattern VERSIONED = Pattern.compile("(?:jev|kev)-\\d+\\.\\d+\\.\\d+");

    private final AnswerCacheMode mode;
    private final int ttlTicks;
    private final int maxEntries;
    private final double revalidateRatio;
    private final long seed;
    private final DecisionMetrics metrics;
    private final ShadowPending shadow;
    private final LinkedHashMap<Key, Entry> entries;
    private int characters;
    private String model = "";
    private String detail = "";
    private long catalogRevision;

    /**
     * Creates the cache.
     *
     * @param mode off, shadow or on
     * @param ttlTicks validity from the original capture
     * @param maxEntries entry cap
     * @param revalidateRatio fraction of hits that are also sent, 0 to 1
     * @param seed installation seed
     * @param shadowCap pending shadow comparisons
     * @param metrics cache counters
     */
    public AnswerCache(AnswerCacheMode mode, int ttlTicks, int maxEntries, double revalidateRatio, long seed,
                       int shadowCap, DecisionMetrics metrics) {
        this.mode = Objects.requireNonNull(mode, "mode");
        this.ttlTicks = ttlTicks;
        this.maxEntries = maxEntries;
        this.revalidateRatio = revalidateRatio;
        this.seed = seed;
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.shadow = new ShadowPending(shadowCap);
        this.entries = new LinkedHashMap<>(16, 0.75f, true);
    }

    /**
     * A cache that does nothing and counts nothing.
     *
     * @param metrics metrics that must not move
     * @return the cache
     */
    public static AnswerCache off(DecisionMetrics metrics) {
        return new AnswerCache(AnswerCacheMode.OFF, 100, 2048, 0.1, 0, 16, metrics);
    }

    /**
     * Whether lookups run.
     *
     * @return {@code false} for {@link AnswerCacheMode#OFF}
     */
    public boolean active() {
        return mode != AnswerCacheMode.OFF;
    }

    /**
     * The configured mode.
     *
     * @return the mode
     */
    public AnswerCacheMode mode() {
        return mode;
    }

    /**
     * The model last noted by {@link #noteContext}.
     *
     * @return the model, or empty before the first note
     */
    public String modelName() {
        return model;
    }

    /**
     * The question detail last noted by {@link #noteContext}.
     *
     * @return the detail word, or empty before the first note
     */
    public String questionDetail() {
        return detail;
    }

    /**
     * Pending shadow comparisons.
     *
     * @return the map
     */
    public ShadowPending shadow() {
        return shadow;
    }

    /** Drops every entry. */
    public void clear() {
        entries.clear();
        characters = 0;
    }

    /**
     * Drops every entry when the catalog, model or detail changed.
     *
     * @param revision catalog revision
     * @param modelName configured model
     * @param questionDetail question detail word
     */
    public void noteContext(long revision, String modelName, String questionDetail) {
        if (revision != catalogRevision || !modelName.equals(model) || !questionDetail.equals(detail)) {
            clear();
            catalogRevision = revision;
            model = modelName;
            detail = questionDetail;
        }
    }

    /**
     * Drops every entry when the catalog changed; the model and the detail stay as last noted (rule I11). The
     * dispatcher calls this before each capture.
     *
     * @param revision catalog revision
     */
    public void noteCatalog(long revision) {
        noteContext(revision, model, detail);
    }

    /**
     * Looks up a prepared body. {@link AnswerCacheMode#OFF} returns {@link Lookup.Off} and counts nothing.
     *
     * @param modelName model of the request
     * @param body prepared body
     * @param questions question keys of the new request
     * @param tick current tick
     * @return the lookup
     */
    public Lookup lookup(String modelName, String body, Set<QuestionKey> questions, long tick) {
        if (mode == AnswerCacheMode.OFF) {
            return new Lookup.Off();
        }
        metrics.cache(DecisionMetrics.CacheCounter.LOOKUPS, 1);
        if (!VERSIONED.matcher(modelName).matches()) {
            return new Lookup.Miss();
        }
        var found = entries.get(new Key(modelName, body.length(), body.hashCode()));
        if (found == null || !found.model.equals(modelName) || !found.body.equals(body)) {
            return new Lookup.Miss();
        }
        var age = tick - found.originTick;
        if (age > ttlTicks) {
            entries.remove(new Key(modelName, body.length(), body.hashCode()));
            characters -= found.body.length();
            metrics.cache(DecisionMetrics.CacheCounter.EXPIRED, 1);
            return new Lookup.Expired();
        }
        if (!found.questions.equals(questions)) {
            metrics.cache(DecisionMetrics.CacheCounter.KEY_MISMATCH, 1);
            return new Lookup.Mismatch();
        }
        metrics.cache(DecisionMetrics.CacheCounter.HITS, 1);
        var revalidate = mode == AnswerCacheMode.ON && revalidateRatio > 0
                && StableRandom.chance(StableRandom.draw(seed, tick, REVALIDATE_SALT), permille());
        return new Lookup.Found(found.answered, found.originTick, revalidate || mode == AnswerCacheMode.SHADOW);
    }

    /**
     * Stores a live answer. Ignored when off, when the model is not versioned, or when the answer failed.
     *
     * @param modelName model
     * @param body body that was sent
     * @param questions question keys
     * @param answered the live answer
     * @param originTick capture tick of the request
     */
    public void remember(String modelName, String body, Set<QuestionKey> questions, DecisionResult.Answered answered,
                         long originTick) {
        if (mode == AnswerCacheMode.OFF || !VERSIONED.matcher(modelName).matches() || answered.inputTokens() < 0) {
            return;
        }
        var key = new Key(modelName, body.length(), body.hashCode());
        var previous = entries.remove(key);
        if (previous != null) {
            characters -= previous.body.length();
        }
        while (!entries.isEmpty() && (entries.size() >= maxEntries || characters + body.length() > MAX_CHARS)) {
            var eldest = entries.entrySet().iterator().next();
            characters -= eldest.getValue().body.length();
            entries.remove(eldest.getKey());
        }
        if (characters + body.length() <= MAX_CHARS) {
            entries.put(key, new Entry(modelName, body, Set.copyOf(questions), answered, originTick));
            characters += body.length();
        }
    }

    /**
     * Notes a shadow or revalidation comparison for this sequence.
     *
     * @param encounter encounter
     * @param sequence sequence
     * @param tactic cached best tactic
     * @param confidence cached confidence
     * @param ageTicks age of the cached capture
     */
    public void noteShadow(EncounterId encounter, long sequence, TacticId tactic, double confidence, long ageTicks) {
        if (ageTicks > ttlTicks) {
            return;
        }
        if (!shadow.put(encounter, sequence, tactic, confidence, ageTicks)) {
            metrics.cache(DecisionMetrics.CacheCounter.SHADOW_SKIPPED_FULL, 1);
        }
    }

    /**
     * Compares a live answer with the shadow entry and removes it.
     *
     * @param encounter encounter
     * @param sequence sequence
     * @param best live best tactic
     * @param confidence live confidence
     * @return whether a comparison ran
     */
    public boolean compare(EncounterId encounter, long sequence, TacticId best, double confidence) {
        var pending = shadow.take(encounter, sequence);
        if (pending.isEmpty()) {
            return false;
        }
        var entry = pending.get();
        if (entry.ageTicks() > ttlTicks) {
            return false;
        }
        var agree = entry.tactic().equals(best);
        metrics.cache(agree ? DecisionMetrics.CacheCounter.AGREE : DecisionMetrics.CacheCounter.DISAGREE, 1);
        var bucket = entry.ageTicks() <= 20 ? DecisionMetrics.CacheCounter.AGE_0_20
                : entry.ageTicks() <= 60 ? DecisionMetrics.CacheCounter.AGE_21_60
                : DecisionMetrics.CacheCounter.AGE_61_100;
        metrics.cache(bucket, 1);
        return agree;
    }

    /**
     * Saved tokens of a cached answer: the original input tokens.
     *
     * @param answered the stored live answer
     * @return its input tokens
     */
    public static int savedTokens(DecisionResult.Answered answered) {
        return answered.inputTokens();
    }

    private int permille() {
        return (int) Math.round(revalidateRatio * 1000.0);
    }

    /** Result of {@link #lookup}. */
    public sealed interface Lookup permits Lookup.Off, Lookup.Miss, Lookup.Expired, Lookup.Mismatch, Lookup.Found {
        /** The cache is off. */
        record Off() implements Lookup {
        }

        /** No entry. */
        record Miss() implements Lookup {
        }

        /** The entry was older than the ttl and was removed. */
        record Expired() implements Lookup {
        }

        /** The body matched and the question keys did not. */
        record Mismatch() implements Lookup {
        }

        /**
         * A hit. {@code alsoSend} is true in shadow and on a revalidation draw.
         *
         * @param answered the stored answer
         * @param originTick capture tick of the stored answer
         * @param alsoSend whether the request is still sent
         */
        record Found(DecisionResult.Answered answered, long originTick, boolean alsoSend) implements Lookup {
        }
    }

    private record Key(String model, int bodyChars, int hash) {
    }

    private record Entry(String model, String body, Set<QuestionKey> questions, DecisionResult.Answered answered,
                         long originTick) {
    }

    /**
     * Best tactic and confidence of the first question, the one with the lowest key ({@code q0}), whatever the
     * iteration order of the answer map (rule C16).
     *
     * @param answered the answer
     * @return tactic and confidence
     */
    public static Optional<Map.Entry<TacticId, Double>> first(DecisionResult.Answered answered) {
        QuestionKey lowest = null;
        for (var key : answered.answers().keySet()) {
            if (lowest == null || key.value().length() < lowest.value().length()
                    || key.value().length() == lowest.value().length() && key.value().compareTo(lowest.value()) < 0) {
                lowest = key;
            }
        }
        if (lowest == null) {
            return Optional.empty();
        }
        var answer = answered.answers().get(lowest);
        return Optional.of(Map.entry(answer.best(), answer.confidence()));
    }
}
