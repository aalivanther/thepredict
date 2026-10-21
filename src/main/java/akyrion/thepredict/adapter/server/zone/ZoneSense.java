package akyrion.thepredict.adapter.server.zone;

import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.Feasibility;
import akyrion.thepredict.domain.HazardPush;
import akyrion.thepredict.domain.PushDanger;
import akyrion.thepredict.domain.SlotAssignment;
import akyrion.thepredict.domain.SlotGeometry;
import akyrion.thepredict.domain.SurroundSlots;
import akyrion.thepredict.domain.TickSignals;
import akyrion.thepredict.domain.ZoneFact;
import akyrion.thepredict.domain.ZoneMode;
import akyrion.thepredict.domain.ZonePick;
import akyrion.thepredict.domain.ZoneView;
import akyrion.thepredict.engine.MobSession;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Writes the effective zone mode, the four push fields and the tell feasibility before a cast (M24.5, M24.6).
 * The server thread only. A missing map, or {@link ZoneMode#OFF}, stores {@link PushDanger#UNKNOWN}. Each sample
 * notes the positions of the mob and of its target in the registry, which anchors on their centroid in its step
 * (M24.1, SP-02); the sense itself never anchors.
 */
public final class ZoneSense {
    /** Rings of the surround slots, in the order of the mask: inner, outer, ranged. */
    private static final int RINGS = 3;
    /** Slots per ring in the mask. */
    private static final int MASK_STRIDE = 16;
    private static final int[] SLOT_COUNTS = {SurroundSlots.INNER, SurroundSlots.OUTER, SurroundSlots.RANGED};
    private static final int[] SLOT_EAST = new int[RINGS * MASK_STRIDE];
    private static final int[] SLOT_SOUTH = new int[RINGS * MASK_STRIDE];

    static {
        for (var ring = 0; ring < RINGS; ring++) {
            for (var angle = 0; angle < SLOT_COUNTS[ring]; angle++) {
                var packed = (ring << 8) | angle;
                SLOT_EAST[ring * MASK_STRIDE + angle] = (int) Math.round(SlotGeometry.east(packed));
                SLOT_SOUTH[ring * MASK_STRIDE + angle] = (int) Math.round(SlotGeometry.south(packed));
            }
        }
    }

    private final ZoneRegistry zones;
    private final Function<UUID, Optional<Entity>> entities;

    /**
     * Creates the sense.
     *
     * @param zones the registry
     * @param entities loaded entity by id
     */
    public ZoneSense(ZoneRegistry zones, Function<UUID, Optional<Entity>> entities) {
        this.zones = zones;
        this.entities = entities;
    }

    /**
     * Samples one mob. Query failures stay {@link PushDanger#UNKNOWN} and do not change a decision. The session
     * receives every limit of the caps (E-12, EFO-12) and the effective mode is the minimum of the global and the
     * requested one (E-7).
     *
     * @param session the mob
     * @param signals the packed reading
     * @return the signals with the four push fields replaced
     */
    public long apply(MobSession session, long signals) {
        var caps = zones.caps();
        session.zoneLimits(caps);
        var settings = session.profile().zone();
        var effective = settings.effective(caps.mode());
        var encounter = session.encounter().orElse(null);
        var living = living(session.key().entityId());
        var target = living == null ? null : target(session, living);
        if (encounter != null && living != null) {
            zones.ensureOpen(encounter, settings, arenaRadius(session));
            zones.noteCadence(encounter, session.cadence());
            zones.notePosition(encounter, living.getWorld(), blockX(living), blockY(living), blockZ(living));
            if (target != null) {
                zones.notePosition(encounter, target.getWorld(), blockX(target), blockY(target), blockZ(target));
            }
        }
        var map = encounter == null ? null : zones.map(encounter).orElse(null);
        if (effective == ZoneMode.OFF || map == null || map.epoch() <= 0 || living == null) {
            quiet(session, effective);
            return withPushes(signals, PushDanger.UNKNOWN);
        }
        return read(session, signals, effective, map, living, target);
    }

    /**
     * Whether the retreat query found no cell. Unknown and deferred do not count.
     *
     * @param zones the registry
     * @param encounter the encounter
     * @param entity the mob
     * @return {@code true} only for {@link ZonePick#NONE}
     */
    public static boolean retreatMissing(ZoneRegistry zones, EncounterId encounter, LivingEntity entity) {
        var map = zones.map(encounter);
        if (map.isEmpty() || map.get().epoch() <= 0) {
            return false;
        }
        var view = map.get();
        var away = away(entity);
        var pick = zones.queries().retreat(view, blockX(entity) - view.originX(),
                blockZ(entity) - view.originZ(), away[0], away[1], 6);
        return ZonePick.state(pick) == ZonePick.NONE;
    }

    /** {@code Arena.Radius} of the profile rounded up, or {@code 0} without an arena (M24.1). */
    static int arenaRadius(MobSession session) {
        var arena = session.profile().movement().arenaRadiusBlocks();
        return arena.isPresent() ? (int) Math.ceil(arena.getAsDouble()) : 0;
    }

    private long read(MobSession session, long signals, ZoneMode effective, ZoneView view, LivingEntity living,
                      LivingEntity target) {
        var mobX = blockX(living) - view.originX();
        var mobZ = blockZ(living) - view.originZ();
        var tx = target == null ? mobX : blockX(target) - view.originX();
        var tz = target == null ? mobZ : blockZ(target) - view.originZ();
        var alongX = Integer.signum(tx - mobX);
        var alongZ = Integer.signum(tz - mobZ);
        if (alongX == 0 && alongZ == 0) {
            alongX = 1;
        }
        var caps = zones.caps();
        var queries = zones.queries();
        var health = (int) living.getHealth();
        var targetHealth = target == null ? health : (int) target.getHealth();
        var mobNear = queries.push(view, mobX, mobZ, alongX, alongZ, caps.knockbackDepthBlocks(), health);
        var mobFar = queries.push(view, mobX, mobZ, -alongX, -alongZ, caps.strongPushDepthBlocks(), health);
        var targetNear = queries.push(view, tx, tz, alongX, alongZ, caps.knockbackDepthBlocks(), targetHealth);
        var targetFar = queries.push(view, tx, tz, -alongX, -alongZ, caps.strongPushDepthBlocks(), targetHealth);
        var askedX = mobX;
        var askedZ = mobZ;
        var targetX = tx;
        var targetZ = tz;
        session.answerProbe(answer -> queries.feasible(view, answer, askedX, askedZ, targetX, targetZ, 3));
        session.zoneReading(effective, targetNear.worse(targetFar), Feasibility.UNKNOWN);
        var guard = effective == ZoneMode.GUARD || effective == ZoneMode.FULL;
        var exploit = session.profile().zone().hazardPush() == HazardPush.EXPLOIT && !session.playerTarget();
        session.zoneFlags(guard, guard && (session.entropyActive() || exploit));
        session.noteZoneBits(bits(mobFar, targetNear, targetFar));
        var look = target == null ? 0 : Math.floorMod(Math.round(target.getYaw()), 360);
        noteSlots(session, view, mobX, mobZ, targetX, targetZ, look);
        return withPushes(signals, mobNear, mobFar, targetNear, targetFar);
    }

    private static long bits(PushDanger mobFar, PushDanger targetNear, PushDanger targetFar) {
        var bits = 0L;
        if (mobFar == PushDanger.LETHAL) {
            bits |= 1L << ZoneFact.EDGE_BEHIND.ordinal();
        }
        if (targetNear == PushDanger.LETHAL || targetFar == PushDanger.LETHAL) {
            bits |= 1L << ZoneFact.TARGET_NEAR_DROP.ordinal();
        }
        return bits;
    }

    private static void quiet(MobSession session, ZoneMode effective) {
        var mode = effective == null ? ZoneMode.OFF : effective;
        session.answerProbe(answer -> Feasibility.UNKNOWN);
        session.zoneReading(mode, PushDanger.UNKNOWN, Feasibility.UNKNOWN);
        session.zoneFlags(false, false);
        session.noteZoneBits(0L);
        session.noteSlotMask(-1L);
    }

    /** Surround slots the terrain allows, from the precomputed offsets of each ring (SP-19). */
    private static void noteSlots(MobSession session, ZoneView view, int mobX, int mobZ, int targetX, int targetZ,
                                  int look) {
        session.noteAnchor(mobX, mobZ, targetX, targetZ, look);
        if (view.epoch() == 0) {
            session.noteSlotMask(-1L);
            return;
        }
        var mask = 0L;
        for (var ring = 0; ring < RINGS; ring++) {
            for (var angle = 0; angle < SLOT_COUNTS[ring]; angle++) {
                var offset = ring * MASK_STRIDE + angle;
                var columnX = view.originX() + targetX + SLOT_EAST[offset];
                var columnZ = view.originZ() + targetZ + SLOT_SOUTH[offset];
                if (SlotAssignment.columnAllowed(view, columnX, columnZ, 2)) {
                    mask |= 1L << offset;
                }
            }
        }
        session.noteSlotMask(mask);
        var slot = session.groupSlotIndex();
        if (slot >= 0) {
            var dx = (mobX - targetX) - SlotGeometry.east(slot);
            var dz = (mobZ - targetZ) - SlotGeometry.south(slot);
            session.noteSlotFar(Math.hypot(dx, dz) > 1.5);
        }
    }

    private LivingEntity living(UUID id) {
        var found = entities.apply(id).orElse(null);
        return found instanceof LivingEntity living ? living : null;
    }

    /** The remembered target when it is alive and in the mob's world (SP-27); otherwise the native target. */
    private LivingEntity target(MobSession session, LivingEntity living) {
        var id = session.lastTargetId().orElse(null);
        if (id != null) {
            var found = living(id);
            if (found != null && !found.isDead() && found.getWorld() == living.getWorld()) {
                return found;
            }
        }
        var fallback = living instanceof Mob mob ? mob.getTarget() : null;
        return fallback != null && fallback.getWorld() == living.getWorld() ? fallback : null;
    }

    private static int blockX(Entity entity) {
        return (int) Math.floor(entity.getX());
    }

    private static int blockY(Entity entity) {
        return (int) Math.floor(entity.getY());
    }

    private static int blockZ(Entity entity) {
        return (int) Math.floor(entity.getZ());
    }

    private static int[] away(LivingEntity entity) {
        var threat = entity instanceof Mob mob ? mob.getTarget() : null;
        if (threat == null) {
            return new int[] {0, -1};
        }
        var awayX = Integer.signum(blockX(entity) - blockX(threat));
        var awayZ = Integer.signum(blockZ(entity) - blockZ(threat));
        if (awayX == 0 && awayZ == 0) {
            awayZ = -1;
        }
        return new int[] {awayX, awayZ};
    }

    private static long withPushes(long signals, PushDanger danger) {
        var code = danger.ordinal();
        return withPushes(signals, code, code, code, code);
    }

    private static long withPushes(long signals, PushDanger mobNear, PushDanger mobFar, PushDanger targetNear,
                                   PushDanger targetFar) {
        return withPushes(signals, mobNear.ordinal(), mobFar.ordinal(), targetNear.ordinal(), targetFar.ordinal());
    }

    private static long withPushes(long signals, int mobNear, int mobFar, int targetNear, int targetFar) {
        return TickSignals.pack(signals, TickSignals.gapQuarters(signals), TickSignals.octant(signals),
                TickSignals.facingError(signals), TickSignals.hittableTicks(signals),
                TickSignals.mobHealthPercent(signals), TickSignals.targetHealthPercent(signals),
                mobNear, mobFar, targetNear, targetFar);
    }
}
