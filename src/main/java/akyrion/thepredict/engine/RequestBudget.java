package akyrion.thepredict.engine;

import akyrion.thepredict.domain.EncounterId;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

/**
 * Requests and input tokens allowed by the Jev account, split between server, worlds and encounters (M09.3).
 * Server thread only. One instance per plugin start: reloading profiles never recreates it, so a reload cannot
 * reset a window to evade a limit (plan §7).
 *
 * <p>Each window is a ring of 60 one-second buckets, so memory is fixed and every operation touches at most 60
 * buckets per world; a world is forgotten once nothing is in flight there and its window is empty (rule A5). A request is reserved before it is sent and keeps counting in its window even when it fails or its
 * answer arrives late. The tokens of a request are estimated from the size of its prepared body (M16.1):
 * {@code ceil(bodyChars / cpt)}, where {@code cpt} (characters per token) starts at {@value #INITIAL_CHARS_PER_TOKEN}
 * and learns from every answer, {@code cpt += }{@value #LEARNING_RATE}{@code  × (bodyChars / input_tokens − cpt)},
 * bounded to [{@value #MIN_CHARS_PER_TOKEN}, {@value #MAX_CHARS_PER_TOKEN}]. The learned value never moves the fixed
 * 1.5 of the size guard of {@code adapter.jev}, the prudent bound for the limits of Jev.
 */
public final class RequestBudget {
    /**
     * Public price of Jev 1.13 per million input tokens, in USD, used only for estimates: {@code /thepredict status} and
     * the {@code summary.md} of a duel recording (plan §6; the one place of this value, rule C13).
     */
    public static final double USD_PER_MILLION_INPUT_TOKENS = 0.042;
    /** Characters per token before the first answer: the ratio of the size guard, below every measured value (M13). */
    public static final double INITIAL_CHARS_PER_TOKEN = 1.5;
    /** Lowest learned characters per token. */
    public static final double MIN_CHARS_PER_TOKEN = 1.3;
    /** Highest learned characters per token. */
    public static final double MAX_CHARS_PER_TOKEN = 2.0;
    /** Weight of each observed answer in the moving average of the characters per token. */
    public static final double LEARNING_RATE = 0.2;

    private static final long SECOND_NANOS = 1_000_000_000L;

    private final EngineSettings settings;
    private final MinuteWindow requests = new MinuteWindow();
    private final MinuteWindow tokens = new MinuteWindow();
    private final Map<UUID, WorldUsage> worlds = new HashMap<>();
    private final Set<EncounterId> reserved = new HashSet<>();
    private double charsPerToken = INITIAL_CHARS_PER_TOKEN;

    /**
     * Creates an empty budget.
     *
     * @param settings limits of the account share
     */
    public RequestBudget(EngineSettings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    /**
     * Estimates the input tokens of a prepared body with the learned characters per token.
     *
     * @param bodyChars characters of the body, {@code >= 0}
     * @return {@code ceil(bodyChars / cpt)}
     */
    public int tokensFor(int bodyChars) {
        if (bodyChars < 0) {
            throw new IllegalArgumentException("bodyChars must be >= 0, was " + bodyChars);
        }
        return (int) Math.min(Integer.MAX_VALUE, Math.ceil(bodyChars / charsPerToken));
    }

    /**
     * Reserves one request if every limit allows it, checked in this order: requests in flight of the server,
     * requests of the server and of the world in the last 60 s, requests in flight of the world, and estimated
     * tokens in the last 60 s. An encounter that already holds a reservation is refused.
     *
     * @param worldId world of the encounter
     * @param encounter encounter that sends
     * @param estimatedTokens estimated input tokens, {@code >= 0}
     * @param nowNanos monotonic time of the send
     * @return {@code true} if the request was reserved and counted
     */
    public boolean tryReserve(UUID worldId, EncounterId encounter, int estimatedTokens, long nowNanos) {
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(encounter, "encounter");
        var second = Math.floorDiv(nowNanos, SECOND_NANOS);
        forgetIdleWorlds(second);
        var world = worlds.computeIfAbsent(worldId, id -> new WorldUsage());
        if (reserved.contains(encounter) || reserved.size() >= settings.maxInFlightRequests()
                || requests.sum(second) >= settings.maxRequestsPerMinute()
                || world.requests.sum(second) >= settings.maxRequestsPerMinutePerWorld()
                || world.inFlight >= settings.maxInFlightRequestsPerWorld()
                || tokens.sum(second) + Math.max(0, estimatedTokens) > settings.maxInputTokensPerMinute()) {
            return false;
        }
        reserved.add(encounter);
        world.inFlight++;
        requests.add(second, 1);
        world.requests.add(second, 1);
        tokens.add(second, Math.max(0, estimatedTokens));
        return true;
    }

    /**
     * Tells whether the request windows still leave room, for the fallback chain (M10.6): the requests of the server
     * and of the world and the estimated tokens of the last 60 s are below their limits. The limits on requests in
     * flight are momentary and do not count as an exhausted budget.
     *
     * @param worldId world of the session
     * @param nowNanos monotonic time now
     * @return {@code false} once a window is full
     */
    public boolean available(UUID worldId, long nowNanos) {
        Objects.requireNonNull(worldId, "worldId");
        var second = Math.floorDiv(nowNanos, SECOND_NANOS);
        var world = worlds.get(worldId);
        return requests.sum(second) < settings.maxRequestsPerMinute()
                && tokens.sum(second) < settings.maxInputTokensPerMinute()
                && (world == null || world.requests.sum(second) < settings.maxRequestsPerMinutePerWorld());
    }

    /**
     * Frees the in-flight slot of a request and, with the real token count, corrects the bucket where it was sent
     * and teaches the characters per token from the size of its body. The request keeps counting in its windows. Called once per request.
     *
     * @param request the request as it was sent
     * @param actualTokens input tokens charged, empty when unknown (a failure)
     * @param nowNanos monotonic time now
     */
    public void complete(InFlightRequest request, OptionalInt actualTokens, long nowNanos) {
        Objects.requireNonNull(actualTokens, "actualTokens");
        if (!reserved.remove(request.encounter())) {
            return;
        }
        var world = worlds.get(request.worldId());
        if (world != null && world.inFlight > 0) {
            world.inFlight--;
        }
        if (actualTokens.isPresent()) {
            var sent = Math.floorDiv(request.sentAtNanos(), SECOND_NANOS);
            tokens.correct(sent, Math.floorDiv(nowNanos, SECOND_NANOS), actualTokens.getAsInt() - request.estimatedTokens());
            learn(request.bodyChars(), actualTokens.getAsInt());
        }
    }

    /**
     * Counts the requests of the server in the last 60 s, for diagnostics (M12).
     *
     * @param nowNanos monotonic time now
     * @return requests sent in the window
     */
    public long requestsLastMinute(long nowNanos) {
        return requests.sum(Math.floorDiv(nowNanos, SECOND_NANOS));
    }

    /**
     * Counts the input tokens of the last 60 s: real where known, estimated otherwise (M12).
     *
     * @param nowNanos monotonic time now
     * @return tokens in the window
     */
    public long tokensLastMinute(long nowNanos) {
        return tokens.sum(Math.floorDiv(nowNanos, SECOND_NANOS));
    }

    /**
     * Counts the requests in flight of the server.
     *
     * @return reserved requests not yet completed
     */
    public int inFlight() {
        return reserved.size();
    }

    /**
     * Returns the learned characters per token.
     *
     * @return current estimate, within [{@value #MIN_CHARS_PER_TOKEN}, {@value #MAX_CHARS_PER_TOKEN}]
     */
    public double charsPerToken() {
        return charsPerToken;
    }

    /**
     * Counts the requests of one world in the last 60 s, for diagnostics (M12.1).
     *
     * @param worldId world
     * @param nowNanos monotonic time now
     * @return requests sent from that world in the window; {@code 0} for a world without usage
     */
    public long requestsLastMinute(UUID worldId, long nowNanos) {
        var world = worlds.get(Objects.requireNonNull(worldId, "worldId"));
        return world == null ? 0 : world.requests.sum(Math.floorDiv(nowNanos, SECOND_NANOS));
    }

    /**
     * Counts the requests in flight of one world, for diagnostics (M12.1).
     *
     * @param worldId world
     * @return requests of that world not yet completed
     */
    public int inFlight(UUID worldId) {
        var world = worlds.get(Objects.requireNonNull(worldId, "worldId"));
        return world == null ? 0 : world.inFlight;
    }

    /** Counts the worlds with usage still tracked, for tests of the bound on memory. */
    int trackedWorlds() {
        return worlds.size();
    }

    /** Forgets every world with nothing in flight and an empty window, so worlds that come and go do not pile up. */
    private void forgetIdleWorlds(long second) {
        worlds.values().removeIf(world -> world.inFlight == 0 && world.requests.sum(second) == 0);
    }

    private void learn(int bodyChars, int actualTokens) {
        if (bodyChars <= 0 || actualTokens <= 0) {
            return;
        }
        var learned = charsPerToken + LEARNING_RATE * ((double) bodyChars / actualTokens - charsPerToken);
        charsPerToken = Math.clamp(learned, MIN_CHARS_PER_TOKEN, MAX_CHARS_PER_TOKEN);
    }

    /** Requests of one world: its window and its requests in flight. */
    private static final class WorldUsage {
        private final MinuteWindow requests = new MinuteWindow();
        private int inFlight;
    }

    /**
     * A ring of 60 one-second buckets; a bucket stamped with an older second is reset before use. The sum of the last
     * second asked is kept until a bucket changes, so the fallback chain, which asks on every reactive cycle of every
     * local session, walks the 60 buckets at most once per second and window instead of on every call.
     */
    private static final class MinuteWindow {
        private static final int SECONDS = 60;
        private static final long EMPTY = Long.MIN_VALUE;

        private final long[] stamps = new long[SECONDS];
        private final long[] values = new long[SECONDS];
        private long summedSecond = EMPTY;
        private long summed;

        private MinuteWindow() {
            Arrays.fill(stamps, EMPTY);
        }

        private void add(long second, long amount) {
            var index = (int) Math.floorMod(second, SECONDS);
            if (stamps[index] != second) {
                stamps[index] = second;
                values[index] = 0;
            }
            values[index] += amount;
            summedSecond = EMPTY;
        }

        private long sum(long second) {
            if (second == summedSecond) {
                return summed;
            }
            var total = 0L;
            for (var index = 0; index < SECONDS; index++) {
                if (live(stamps[index], second)) {
                    total += values[index];
                }
            }
            summedSecond = second;
            summed = total;
            return total;
        }

        private void correct(long sentSecond, long nowSecond, long delta) {
            var index = (int) Math.floorMod(sentSecond, SECONDS);
            if (stamps[index] == sentSecond && live(sentSecond, nowSecond)) {
                values[index] = Math.max(0, values[index] + delta);
                summedSecond = EMPTY;
            }
        }

        private static boolean live(long stamp, long second) {
            return stamp != EMPTY && stamp <= second && second - stamp < SECONDS;
        }
    }
}
