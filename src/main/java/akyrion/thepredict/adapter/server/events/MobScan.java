package akyrion.thepredict.adapter.server.events;

import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.engine.SessionLifecycle;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Finds the bound mobs that already exist when the plugin enables or MythicMobs reloads (M06.2): it copies the
 * UUIDs of the active MythicMobs instances once and opens the sessions of the bound ones at
 * {@link #MOBS_PER_TICK} mobs per tick (rule A5). Also resolves single mobs for the spawn and chunk-load
 * listeners. Server thread only.
 *
 * <p>"Bound" means that the current catalog has a profile for the MythicMobs type; the binder of M03 only binds
 * types with {@code Predictor: true}.
 */
public final class MobScan {
    /** Maximum mobs resolved per tick. */
    public static final int MOBS_PER_TICK = 32;

    private final SessionLifecycle lifecycle;
    private final MobDirectory mobs;
    private final Function<String, Optional<TacticalProfile>> boundProfiles;
    private final ArrayDeque<UUID> pending = new ArrayDeque<>();

    /**
     * Creates an idle scan.
     *
     * @param lifecycle sessions to open
     * @param mobs MythicMobs mob manager
     * @param boundProfiles profile of a MythicMobs mob ID in the current catalog
     */
    public MobScan(SessionLifecycle lifecycle, MobDirectory mobs, Function<String, Optional<TacticalProfile>> boundProfiles) {
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.mobs = Objects.requireNonNull(mobs, "mobs");
        this.boundProfiles = Objects.requireNonNull(boundProfiles, "boundProfiles");
    }

    /** Starts a new scan over the current active instances, replacing an unfinished one. */
    public void start() {
        pending.clear();
        pending.addAll(mobs.activeMobIds());
    }

    /**
     * Resolves the next mobs of the scan.
     *
     * @return number of mobs resolved in this tick, at most {@link #MOBS_PER_TICK}
     */
    public int tick() {
        var resolved = 0;
        while (resolved < MOBS_PER_TICK && !pending.isEmpty()) {
            discover(pending.pollFirst());
            resolved++;
        }
        return resolved;
    }

    /**
     * Opens or resumes the session of one mob if its MythicMobs instance is active, its entity valid and alive, and
     * its type bound. The validity check avoids ghost sessions, for example between a chunk unload and the moment
     * MythicMobs marks the instance inactive.
     *
     * @param entityId UUID of the entity
     * @return {@code true} if a session is open for the mob afterwards
     */
    public boolean discover(UUID entityId) {
        if (!mobs.isActive(entityId)) {
            return false;
        }
        var profile = mobs.mobTypeOf(entityId).flatMap(boundProfiles);
        profile.ifPresent(bound -> lifecycle.discovered(entityId, bound));
        return profile.isPresent();
    }

    /**
     * Counts the mobs still to resolve.
     *
     * @return pending mobs of the scan
     */
    public int remaining() {
        return pending.size();
    }
}
