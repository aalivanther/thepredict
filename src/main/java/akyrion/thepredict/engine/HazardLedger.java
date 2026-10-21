package akyrion.thepredict.engine;

import akyrion.thepredict.domain.DisplacementKind;
import akyrion.thepredict.domain.HazardOutcome;
import akyrion.thepredict.domain.HazardRecord;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Pushes waiting to be attributed (M24.10). The cap is {@link #CAP}. The oldest push leaves when the ring is full.
 * A push inside the window becomes {@link HazardOutcome#PUSHED_INTO_HAZARD}; a member with no push is
 * {@link HazardOutcome#SELF_HAZARD}.
 */
public final class HazardLedger {
    /** Maximum pending pushes. */
    public static final int CAP = 256;

    private final UUID[] victim = new UUID[CAP];
    private final UUID[] hitter = new UUID[CAP];
    private final long[] when = new long[CAP];
    private final DisplacementKind[] kind = new DisplacementKind[CAP];
    private int next;
    private int size;

    /** Creates an empty ledger. */
    public HazardLedger() {
    }

    /**
     * Remembers one push. A null hitter is a cast whose cause is only the caster stored by the caller as the hitter.
     *
     * @param victim the entity that moved
     * @param hitter the entity that pushed, or null
     * @param kind the displacement
     * @param tick the server tick
     */
    public void note(UUID victim, UUID hitter, DisplacementKind kind, long tick) {
        this.victim[next] = Objects.requireNonNull(victim, "victim");
        this.hitter[next] = hitter;
        this.kind[next] = Objects.requireNonNull(kind, "kind");
        this.when[next] = tick;
        next = (next + 1) % CAP;
        if (size < CAP) {
            size++;
        }
    }

    /**
     * Attributes damage. The newest push of this victim inside the window wins.
     *
     * @param victim the damaged entity
     * @param damage damage taken, never negative
     * @param died whether the victim died on this tick
     * @param now the server tick
     * @param window ticks a push stays attributable
     * @param member whether the victim is a session member
     * @return the record, or empty when nobody in the fight was involved
     */
    public Optional<HazardRecord> attribute(UUID victim, int damage, boolean died, long now, int window,
                                            boolean member) {
        Objects.requireNonNull(victim, "victim");
        var safeDamage = Math.max(0, damage);
        var found = newest(victim, now, window);
        if (found >= 0) {
            var elapsed = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, now - when[found]));
            return Optional.of(new HazardRecord(safeDamage, died, elapsed, kind[found],
                    HazardOutcome.PUSHED_INTO_HAZARD));
        }
        if (member) {
            return Optional.of(new HazardRecord(safeDamage, died, 0, DisplacementKind.NONE, HazardOutcome.SELF_HAZARD));
        }
        return Optional.empty();
    }

    /**
     * The entity that pushed the victim inside the window, when one was recorded.
     *
     * @param victim the damaged entity
     * @param now the server tick
     * @param window ticks a push stays attributable
     * @return the hitter, or empty
     */
    public Optional<UUID> hitter(UUID victim, long now, int window) {
        var found = newest(Objects.requireNonNull(victim, "victim"), now, window);
        return found < 0 || hitter[found] == null ? Optional.empty() : Optional.of(hitter[found]);
    }

    private int newest(UUID victim, long now, int window) {
        var found = -1;
        var best = Long.MIN_VALUE;
        var start = size == CAP ? next : 0;
        for (var offset = 0; offset < size; offset++) {
            var index = (start + offset) % CAP;
            if (!victim.equals(this.victim[index])) {
                continue;
            }
            var age = now - when[index];
            if (age < 0 || age > window || when[index] < best) {
                continue;
            }
            best = when[index];
            found = index;
        }
        return found;
    }
}
