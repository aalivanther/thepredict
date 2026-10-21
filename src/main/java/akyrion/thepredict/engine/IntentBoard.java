package akyrion.thepredict.engine;

import akyrion.thepredict.domain.IntentView;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.MovementSettings;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe board of the current {@link IntentView} of each mob session.
 *
 * <p>The server thread publishes and clears views; MythicMobs conditions and placeholders read them
 * from any thread (rule T3). Safe publication comes from the concurrent map and the immutable view.
 * Bounded by the number of sessions: one view per mob.
 */
public final class IntentBoard {
    private final Map<UUID, IntentView> views = new ConcurrentHashMap<>();
    private final Map<UUID, Long> nativeUntil = new HashMap<>();
    private final Map<UUID, SteerNote> steering = new HashMap<>();
    private int waypointRefreshTicks = 5;
    private long microPending;
    private long microMissing;
    private volatile long publishedTick;

    /** Creates an empty board; every mob reads {@link IntentView#ABSENT}. */
    public IntentBoard() {
    }

    /**
     * Publishes the view of a session, replacing the previous one. Server thread only.
     *
     * @param key session identity
     * @param view view to publish; its generation must match the key
     * @throws IllegalArgumentException if the generations differ
     */
    public void publish(MobKey key, IntentView view) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(view, "view");
        if (view.generation() != key.generation()) {
            throw new IllegalArgumentException("view generation " + view.generation()
                    + " does not match session generation " + key.generation());
        }
        views.put(key.entityId(), view);
    }

    /**
     * Returns the current view without allocating. Safe on any thread.
     *
     * @param entityId entity UUID
     * @return the published view, or {@link IntentView#ABSENT} when the mob has no session
     */
    public IntentView current(UUID entityId) {
        return views.getOrDefault(entityId, IntentView.ABSENT);
    }

    /**
     * Removes the view of one mob; its conditions become false on their next evaluation.
     * Server thread only.
     *
     * @param entityId entity UUID
     */
    public void clear(UUID entityId) {
        views.remove(Objects.requireNonNull(entityId, "entityId"));
        nativeUntil.remove(entityId);
        steering.remove(entityId);
    }

    /** Removes every view; part of the shutdown order (rule T7). Server thread only. */
    public void clearAll() {
        views.clear();
        nativeUntil.clear();
        steering.clear();
        microPending = 0;
        microMissing = 0;
    }

    /**
     * Ticks between orbit waypoints for every micro goal (M20.5). The coordinator publishes the profile value, 2 to 40.
     * Server thread only.
     *
     * @param ticks the configured refresh
     */
    public void noteWaypointRefresh(int ticks) {
        waypointRefreshTicks = ticks;
    }

    /**
     * Ticks between orbit waypoints. Five until the coordinator publishes the profile.
     *
     * @return the refresh
     */
    public int waypointRefreshTicks() {
        return waypointRefreshTicks;
    }

    /**
     * Remembers the session seed and the movement band the micro goal steers with. Server thread only. The tick layer
     * calls this on every visit, so a note that did not change is kept and nothing is allocated (EFO-18, C12).
     *
     * @param entityId mob
     * @param seed session seed
     * @param movement band, speeds and arena of the profile
     */
    public void noteSteer(UUID entityId, long seed, MovementSettings movement) {
        Objects.requireNonNull(entityId, "entityId");
        Objects.requireNonNull(movement, "movement");
        var note = steering.get(entityId);
        if (note != null && note.seed == seed && note.movement.equals(movement)) {
            return;
        }
        steering.put(entityId, new SteerNote(seed, movement));
    }

    /**
     * Session seed published for the micro goal.
     *
     * @param entityId mob
     * @return the seed, empty before the tick layer notes one
     */
    public OptionalLong steerSeed(UUID entityId) {
        var note = steering.get(entityId);
        return note == null ? OptionalLong.empty() : OptionalLong.of(note.seed);
    }

    /**
     * Movement settings published for the micro goal.
     *
     * @param entityId mob
     * @return the band, empty before the tick layer notes one
     */
    public Optional<MovementSettings> steerMovement(UUID entityId) {
        var note = steering.get(entityId);
        return note == null ? Optional.empty() : Optional.of(note.movement);
    }

    private record SteerNote(long seed, MovementSettings movement) {
    }

    /**
     * Keeps native movement until {@code untilTick}, after two micro path failures (M20.5). Server thread only.
     *
     * @param entityId mob
     * @param untilTick first tick on which steering may publish a micro again
     */
    public void holdMicroNative(UUID entityId, long untilTick) {
        nativeUntil.put(Objects.requireNonNull(entityId, "entityId"), untilTick);
    }

    /**
     * Tick until which the micro goal asked for native movement.
     *
     * @param entityId mob
     * @return the tick, or {@link Long#MIN_VALUE} when none was asked
     */
    public long microNativeUntil(UUID entityId) {
        var until = nativeUntil.get(entityId);
        return until == null ? Long.MIN_VALUE : until;
    }

    /** Counts one tick of an accepted path that is not visible yet. Server thread only. */
    public void noteMicroPending() {
        microPending++;
    }

    /** Counts one path failure. Server thread only. */
    public void noteMicroMissing() {
        microMissing++;
    }

    /**
     * Pending-path ticks since the last drain, then zero. Server thread only.
     *
     * @return the count that was drained
     */
    public long drainMicroPending() {
        var value = microPending;
        microPending = 0;
        return value;
    }

    /**
     * Path failures since the last drain, then zero. Server thread only.
     *
     * @return the count that was drained
     */
    public long drainMicroMissing() {
        var value = microMissing;
        microMissing = 0;
        return value;
    }

    /**
     * Publishes the server tick. The coordinator calls this once per tick, before any of its steps, so a reader on any
     * thread takes the tick from the board instead of asking the server (rule T3, SA-10). Server thread only.
     *
     * @param tick the server tick
     */
    public void publishTick(long tick) {
        publishedTick = tick;
    }

    /**
     * The server tick the coordinator published last. Safe on any thread (a volatile read, no allocation). Zero before
     * the first tick. A reader that runs earlier in the same server tick than the coordinator reads the previous tick.
     *
     * @return the published tick
     */
    public long publishedTick() {
        return publishedTick;
    }

    /**
     * Counts the published views, for diagnostics.
     *
     * @return number of mobs with a view
     */
    public int size() {
        return views.size();
    }
}
