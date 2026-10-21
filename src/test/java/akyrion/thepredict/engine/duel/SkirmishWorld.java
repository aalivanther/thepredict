package akyrion.thepredict.engine.duel;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * N-on-M duel on the M24.9 arena (M25.12). Two bodies closer than {@link #WIDTH} separate by
 * {@link #SEPARATION} blocks per tick. Effects land through the same damage, absorption and heal as {@link DuelWorld}.
 */
class SkirmishWorld extends DuelWorld {
    /** Blocks a crowded pair separates in one tick. */
    static final double SEPARATION = 0.05;

    final List<Fighter> bodies;
    private final List<Pending> pending = new ArrayList<>();

    /** One body of the skirmish. */
    static final class Fighter extends Body {
        final int index;
        final int weight;
        final boolean healer;
        final boolean player;
        int slot = -1;

        Fighter(UUID id, Side side, double x, double z, int index, int weight, boolean healer, boolean player) {
            super(id, side, x, z);
            this.index = index;
            this.weight = weight;
            this.healer = healer;
            this.player = player;
        }

        Fighter copy() {
            var twin = new Fighter(id, side, x, z, index, weight, healer, player);
            twin.copyFrom(this);
            twin.slot = slot;
            return twin;
        }
    }

    private record Pending(long tick, int caster, int target, DuelSkill skill, boolean avoided) {
    }

    SkirmishWorld(List<Fighter> bodies, Consumer<Hit> hits) {
        super(bodies.getFirst(), bodies.get(Math.min(1, bodies.size() - 1)), hits);
        this.bodies = bodies;
    }

    /** A world with the same bodies and effects. */
    SkirmishWorld copy() {
        var twins = new ArrayList<Fighter>(bodies.size());
        for (var body : bodies) {
            twins.add(body.copy());
        }
        var world = new SkirmishWorld(twins, hit -> {
        });
        for (var effect : pending) {
            world.pending.add(effect);
        }
        return world;
    }

    static List<Fighter> place(int mobs, int players, boolean healer, boolean flip) {
        var bodies = new ArrayList<Fighter>(mobs + players);
        for (var index = 0; index < mobs; index++) {
            var angle = 2 * Math.PI * index / mobs;
            var weight = healer && index == 0 ? 2 : 1;
            var body = new Fighter(new UUID(0x25L, index + 1L), Side.RED, Math.cos(angle) * 6, Math.sin(angle) * 6,
                    index, weight, healer && index == mobs - 1, false);
            bodies.add(body);
        }
        for (var index = 0; index < players; index++) {
            var angle = 2 * Math.PI * index / Math.max(1, players);
            bodies.add(new Fighter(new UUID(0x26L, index + 1L), Side.BLUE, Math.cos(angle) * 1.5,
                    Math.sin(angle) * 1.5, mobs + index, 1, false, true));
        }
        if (flip) {
            for (var body : bodies) {
                body.x = -body.x;
                body.z = -body.z;
            }
        }
        return bodies;
    }

    /** Pushes overlapping bodies apart by {@link #SEPARATION} and keeps them in the arena. */
    void separate() {
        for (var left = 0; left < bodies.size(); left++) {
            for (var right = left + 1; right < bodies.size(); right++) {
                push(bodies.get(left), bodies.get(right));
            }
        }
        for (var body : bodies) {
            var radius = Math.hypot(body.x, body.z);
            if (radius > ARENA_RADIUS) {
                body.x *= ARENA_RADIUS / radius;
                body.z *= ARENA_RADIUS / radius;
            }
        }
    }

    private static void push(Fighter left, Fighter right) {
        var dx = right.x - left.x;
        var dz = right.z - left.z;
        var distance = Math.hypot(dx, dz);
        if (distance >= WIDTH) {
            return;
        }
        var nx = distance < 1.0e-6 ? 1.0 : dx / distance;
        var nz = distance < 1.0e-6 ? 0.0 : dz / distance;
        var half = SEPARATION / 2.0;
        left.x -= nx * half;
        left.z -= nz * half;
        right.x += nx * half;
        right.z += nz * half;
    }

    void moveToward(Fighter mover, double x, double z, double stop) {
        var dx = x - mover.x;
        var dz = z - mover.z;
        var distance = Math.hypot(dx, dz);
        if (distance <= stop) {
            return;
        }
        var scale = Math.min(SPEED, distance - stop) / distance;
        mover.x += dx * scale;
        mover.z += dz * scale;
    }

    void moveToward(Fighter mover, Fighter target, double stop, boolean toward) {
        var distance = distance(mover, target);
        var length = toward ? Math.min(SPEED, Math.max(0, distance - stop)) : SPEED;
        travel(mover, target, length, toward);
    }

    double distance(Fighter left, Fighter right) {
        return Math.hypot(left.x - right.x, left.z - right.z);
    }

    /**
     * Casts a pack skill. A leap moves now. Damage, the shield and the heal land after the pack delay.
     *
     * @param caster the caster
     * @param target who a damaging skill hits
     * @param skill the pack skill
     * @param tick the cast tick
     * @param avoided whether the target's answer negates the damage
     */
    void cast(Fighter caster, Fighter target, DuelSkill skill, long tick, boolean avoided) {
        caster.cooldown(skill, tick + skill.cooldownTicks);
        if (skill == DuelSkill.LEAP) {
            var distance = distance(caster, target);
            if (distance > LEAP_LANDING) {
                caster.x = target.x + (caster.x - target.x) * LEAP_LANDING / distance;
                caster.z = target.z + (caster.z - target.z) * LEAP_LANDING / distance;
            }
        }
        pending.add(new Pending(tick + skill.delayTicks, caster.index, target.index, skill, avoided));
        land(tick);
    }

    /** Applies effects whose land tick has arrived. A heal of {@code DuelHeal} is 6, not a clock remainder. */
    void land(long tick) {
        var due = new ArrayList<Pending>();
        pending.removeIf(effect -> {
            if (effect.tick() > tick) {
                return false;
            }
            due.add(effect);
            return true;
        });
        for (var effect : due) {
            var caster = bodies.get(effect.caster());
            var target = bodies.get(effect.target());
            if (caster.health <= 0) {
                continue;
            }
            var skill = effect.skill();
            if (skill.damage > 0 && !effect.avoided()) {
                damage(caster, target, skill.damage, Optional.of(skill), tick);
            }
            if (skill == DuelSkill.HEAL) {
                target.health = Math.min(MAX_HEALTH, target.health + skill.heal);
            } else {
                caster.absorption = Math.min(skill.shield > 0 ? skill.shield : caster.absorption,
                        caster.absorption + skill.shield);
                caster.health = Math.min(MAX_HEALTH, caster.health + skill.heal);
            }
        }
    }

    /** The closest living body of the other side. */
    Fighter nearestRival(Fighter from) {
        Fighter best = null;
        var bestDistance = Double.MAX_VALUE;
        for (var body : bodies) {
            if (body.side == from.side || body.health <= 0) {
                continue;
            }
            var distance = distance(from, body);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = body;
            }
        }
        return best;
    }

    boolean living(boolean player) {
        for (var body : bodies) {
            if (body.player == player && body.health > 0) {
                return true;
            }
        }
        return false;
    }

    int mobsAlive() {
        var count = 0;
        for (var body : bodies) {
            if (!body.player && body.health > 0) {
                count++;
            }
        }
        return count;
    }
}
