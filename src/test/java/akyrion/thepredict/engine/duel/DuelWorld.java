package akyrion.thepredict.engine.duel;

import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.MovementSettings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The flat arena of the simulator (M18.7): two zombies in a circle, positions in {@code x} and {@code z}, a box 0.6
 * wide, a vanilla hit every 20 ticks within 1.43 blocks between positions (the vanilla reach of a zombie, M17), 10
 * ticks of invulnerability after a hit that deals damage, the absorption of {@code shield} and a leap that puts the
 * caster 2 blocks from its target. No reinforcements, as with {@code spawn_mobs false}. Deterministic: nothing here is
 * random.
 *
 * <p>With {@link DuelPhysics#LEGACY}, {@link TickRules#LEGACY} and {@link Arena#OPEN18} (the three-argument
 * constructor) it is exactly the world of M18.7, whose bytes the golden files keep: 60 health, 0.23 blocks per tick, a
 * vanilla hit of 0 that changes nothing (DT-40 e asks the server), a leap at once, effects in cast order. The other
 * knobs (§7.2 of the simulator audit) move the bodies through {@link Motion}, queue the casts of one tick and commit them
 * in a drawn order, land the effects of one tick together, apply the vanilla rule of invulnerability, knock back and
 * fly a leap, and block movement and sight with pillars.
 */
class DuelWorld {
    static final double MAX_HEALTH = 60;
    static final double PUNCH_DAMAGE = 0;
    static final double SPEED = 0.23;
    static final double ARENA_RADIUS = 18;
    static final double WIDTH = 0.6;
    static final double VANILLA_REACH = 1.43;
    static final double STOP_DISTANCE = 1.2;
    static final int INVULNERABLE_TICKS = 10;
    static final int PUNCH_TICKS = 20;
    static final double LEAP_LANDING = 2;

    /** Side of a duelist. */
    enum Side {
        RED, BLUE;

        Side rival() {
            return this == RED ? BLUE : RED;
        }
    }

    /** One duelist. Skirmish fighters extend it. */
    static class Body {
        final UUID id;
        final Side side;
        final double maxHealth;
        double x;
        double z;
        double health;
        double absorption;
        long invulnerableUntil = Long.MIN_VALUE;
        long nextPunch;
        double lastHurt;
        double previousX;
        double previousZ;
        double vx;
        double vz;
        double yaw = Double.NaN;
        int flightTicks;
        double flightX;
        double flightZ;
        long damagedTick = Long.MIN_VALUE;
        long lostToInvulnerability;
        int goalPhase;
        Motion.Steer goal = Motion.Steer.HOLD;
        private final long[] readyAt = new long[DuelSkill.MAX_SLOTS];

        Body(UUID id, Side side, double x, double z) {
            this(id, side, x, z, MAX_HEALTH);
        }

        Body(UUID id, Side side, double x, double z, double maxHealth) {
            this.id = id;
            this.side = side;
            this.x = x;
            this.z = z;
            this.maxHealth = maxHealth;
            this.health = maxHealth;
            this.previousX = x;
            this.previousZ = z;
            Arrays.fill(readyAt, Long.MIN_VALUE);
        }

        boolean ready(DuelSkill skill, long tick) {
            return readyAt[skill.ordinal()] <= tick;
        }

        /** The tick from which a skill is ready again. */
        long readyAt(DuelSkill skill) {
            return readyAt[skill.ordinal()];
        }

        void cooldown(DuelSkill skill, long until) {
            readyAt[skill.ordinal()] = until;
        }

        double healthRatio() {
            return Math.max(0, health) / maxHealth;
        }

        /** Copies the whole state of another body of the same id. */
        void copyFrom(Body other) {
            x = other.x;
            z = other.z;
            health = other.health;
            absorption = other.absorption;
            invulnerableUntil = other.invulnerableUntil;
            nextPunch = other.nextPunch;
            lastHurt = other.lastHurt;
            previousX = other.previousX;
            previousZ = other.previousZ;
            vx = other.vx;
            vz = other.vz;
            yaw = other.yaw;
            flightTicks = other.flightTicks;
            flightX = other.flightX;
            flightZ = other.flightZ;
            damagedTick = other.damagedTick;
            goalPhase = other.goalPhase;
            goal = other.goal;
            System.arraycopy(other.readyAt, 0, readyAt, 0, readyAt.length);
        }
    }

    /**
     * A hit that landed: attacker, victim, damage and the skill, empty for a vanilla hit.
     *
     * @param lost health the victim lost: the damage less what the absorption took
     */
    record Hit(Body attacker, Body victim, double damage, Optional<DuelSkill> skill, long tick, double lost) {
        Hit(Body attacker, Body victim, double damage, Optional<DuelSkill> skill, long tick) {
            this(attacker, victim, damage, skill, tick, damage);
        }
    }

    /** One effect that lands at {@code tick}: a skill, or a vanilla hit of {@code punch} damage when empty. */
    private record Effect(long tick, Body caster, Optional<DuelSkill> skill, double punch) {
    }

    final Body red;
    final Body blue;
    final DuelPhysics physics;
    final TickRules rules;
    final Arena arena;
    final double punchDamage;
    private final List<Effect> pending = new ArrayList<>();
    private final List<Effect> queued = new ArrayList<>();
    private final List<Effect> due = new ArrayList<>();
    private final Consumer<Hit> hits;

    DuelWorld(Body red, Body blue, Consumer<Hit> hits) {
        this(red, blue, hits, DuelPhysics.LEGACY, TickRules.LEGACY, Arena.OPEN18, PUNCH_DAMAGE);
    }

    DuelWorld(Body red, Body blue, Consumer<Hit> hits, DuelPhysics physics, TickRules rules, Arena arena,
              double punchDamage) {
        this.red = red;
        this.blue = blue;
        this.hits = hits;
        this.physics = physics;
        this.rules = rules;
        this.arena = arena;
        this.punchDamage = punchDamage;
    }

    /**
     * A world with copies of both bodies and of every effect in flight, whose hits go nowhere: what the lookahead
     * oracle plays forward (§7.5).
     *
     * @return the copy
     */
    DuelWorld copy() {
        var twinRed = new Body(red.id, red.side, red.x, red.z, red.maxHealth);
        var twinBlue = new Body(blue.id, blue.side, blue.x, blue.z, blue.maxHealth);
        twinRed.copyFrom(red);
        twinBlue.copyFrom(blue);
        var world = new DuelWorld(twinRed, twinBlue, hit -> {
        }, physics, rules, arena, punchDamage);
        copyEffects(pending, world.pending, world);
        copyEffects(queued, world.queued, world);
        return world;
    }

    private void copyEffects(List<Effect> from, List<Effect> to, DuelWorld world) {
        for (var effect : from) {
            to.add(new Effect(effect.tick(), world.body(effect.caster().side), effect.skill(), effect.punch()));
        }
    }

    Body body(Side side) {
        return side == Side.RED ? red : blue;
    }

    Body rival(Body body) {
        return body(body.side.rival());
    }

    double distance() {
        return Math.hypot(red.x - blue.x, red.z - blue.z);
    }

    double gap() {
        return Math.max(0, distance() - WIDTH);
    }

    /** Whether no pillar stands between the two duelists. */
    boolean lineOfSight() {
        return arena.sight(red.x, red.z, blue.x, blue.z);
    }

    /** Moves a duelist one tick according to a movement and its settings, in the world of M18.7. */
    void move(Body mover, Movement movement, MovementSettings settings) {
        var distance = distance();
        switch (movement) {
            case APPROACH, FLANK -> step(mover, Math.min(SPEED * settings.approachSpeed(), distance - STOP_DISTANCE),
                    true);
            case KEEP_DISTANCE -> {
                if (distance < settings.bandInnerBlocks()) {
                    step(mover, Math.min(SPEED, settings.bandInnerBlocks() - distance), false);
                } else if (distance > settings.bandOuterBlocks()) {
                    step(mover, Math.min(SPEED, distance - settings.bandOuterBlocks()), true);
                }
            }
            case RETREAT -> {
                if (distance < settings.retreatDistanceBlocks()) {
                    step(mover, SPEED * settings.retreatSpeed(), false);
                }
            }
            case HOLD, NONE -> {
            }
        }
    }

    /** The meleeAttack goal of vanilla in the world of M18.7: approach and hit every 20 ticks within reach. */
    void meleeAttack(Body attacker, long tick) {
        step(attacker, Math.min(SPEED, distance() - STOP_DISTANCE), true);
        punch(attacker, tick);
    }

    /**
     * A vanilla hit if the rival is within reach and the attack is ready; returns whether it was swung. With
     * simultaneous rules the hit lands with the effects of this tick.
     */
    boolean punch(Body attacker, long tick) {
        if (distance() > VANILLA_REACH || tick < attacker.nextPunch) {
            return false;
        }
        attacker.nextPunch = tick + PUNCH_TICKS;
        if (rules.simultaneous()) {
            queued.add(new Effect(tick, attacker, Optional.empty(), punchDamage));
        } else {
            damage(attacker, rival(attacker), punchDamage, Optional.empty(), tick);
        }
        return true;
    }

    /**
     * The vanilla hit of a ThePredict choice on a server ({@code Mob.attack}, {@code CombatActionsAdapter}): no attack
     * cooldown of its own and the reach the engine checked; it lands with the effects of this tick.
     */
    void attack(Body attacker, long tick) {
        if (rules.simultaneous()) {
            queued.add(new Effect(tick, attacker, Optional.empty(), punchDamage));
        } else {
            damage(attacker, rival(attacker), punchDamage, Optional.empty(), tick);
        }
    }

    /** Whether the own conditions of a skill hold now: its distance, health and sight conditions. */
    boolean conditions(Body caster, DuelSkill skill) {
        return (skill.withinBlocks == 0 || distance() < skill.withinBlocks)
                && (skill.healthBelow >= 1.0 || caster.healthRatio() < skill.healthBelow)
                && (!skill.needsSight() || lineOfSight());
    }

    /** Whether a cast of {@code caster} is still winding up at {@code tick}: its effect has not landed. */
    boolean windingUp(Body caster, long tick) {
        for (var effect : pending) {
            if (effect.caster() == caster && effect.tick() > tick) {
                return true;
            }
        }
        for (var effect : queued) {
            if (effect.caster() == caster && effect.skill().isPresent() && effect.tick() > tick) {
                return true;
            }
        }
        return false;
    }

    /**
     * Casts a skill: its cooldown starts, a leap moves the caster, and its effect lands after its delay. With
     * simultaneous rules the cast waits in a queue for {@link #commit}.
     */
    void cast(Body caster, DuelSkill skill, long tick) {
        caster.cooldown(skill, tick + skill.cooldownTicks);
        if (rules.simultaneous()) {
            queued.add(new Effect(tick + skill.delayTicks, caster, Optional.of(skill), 0));
            return;
        }
        if (skill.leaps()) {
            Motion.leap(this, caster);
        }
        pending.add(new Effect(tick + skill.delayTicks, caster, Optional.of(skill), 0));
        step(tick);
    }

    /**
     * Commits the casts and hits queued this tick, red first or blue first: leaps take off and every effect goes in
     * flight. Nothing lands here.
     *
     * @param tick current tick
     * @param redFirst whether the casts of red commit before those of blue
     */
    void commit(long tick, boolean redFirst) {
        var first = redFirst ? Side.RED : Side.BLUE;
        commitSide(first);
        commitSide(first.rival());
        queued.clear();
    }

    private void commitSide(Side side) {
        for (var effect : queued) {
            if (effect.caster().side != side) {
                continue;
            }
            if (effect.skill().isPresent() && effect.skill().get().leaps()) {
                Motion.leap(this, effect.caster());
            }
            pending.add(effect);
        }
    }

    /** Applies the effects due at this tick: in cast order, or all together with simultaneous rules. */
    void step(long tick) {
        due.clear();
        for (var iterator = pending.iterator(); iterator.hasNext(); ) {
            var effect = iterator.next();
            if (effect.tick() <= tick) {
                due.add(effect);
                iterator.remove();
            }
        }
        if (rules.simultaneous()) {
            landTogether(tick);
            return;
        }
        for (var effect : due) {
            var caster = effect.caster();
            if (caster.health <= 0) {
                continue;
            }
            var skill = effect.skill().orElseThrow();
            if (skill.damage > 0) {
                damage(caster, rival(caster), skill.damage, effect.skill(), tick);
            }
            caster.absorption = Math.min(skill.shield > 0 ? skill.shield : caster.absorption,
                    caster.absorption + skill.shield);
            caster.health = Math.min(caster.maxHealth, caster.health + skill.heal);
        }
    }

    /**
     * The effects of one tick land together (SIM-03): every caster alive before the tick deals its damage, so two
     * lethal hits kill both; then the shields and heals of the casters still alive.
     */
    private void landTogether(long tick) {
        var redAlive = red.health > 0;
        var blueAlive = blue.health > 0;
        for (var effect : due) {
            var caster = effect.caster();
            if (!(caster == red ? redAlive : blueAlive)) {
                continue;
            }
            var amount = effect.skill().map(skill -> skill.damage).orElse(effect.punch());
            if (amount > 0) {
                damage(caster, rival(caster), amount, effect.skill(), tick);
            }
        }
        for (var effect : due) {
            var caster = effect.caster();
            if (caster.health <= 0 || effect.skill().isEmpty()) {
                continue;
            }
            var skill = effect.skill().get();
            caster.absorption = Math.min(skill.shield > 0 ? skill.shield : caster.absorption,
                    caster.absorption + skill.shield);
            caster.health = Math.min(caster.maxHealth, caster.health + skill.heal);
        }
    }

    /** Whether either duelist died. */
    boolean over() {
        return red.health <= 0 || blue.health <= 0;
    }

    /** The side that won: the one still alive; empty when both died in the same tick. */
    Optional<Side> winner() {
        if (red.health <= 0 && blue.health <= 0) {
            return Optional.empty();
        }
        return Optional.of(red.health <= 0 ? Side.BLUE : Side.RED);
    }

    /**
     * Applies one damaging hit. Amounts of zero do not start invulnerability. In the world of M18.7 a hit inside the
     * window is ignored; with the vanilla rule it deals only what beats the last hit and does not reopen the window,
     * and a hit that deals damage knocks the victim back.
     */
    protected void damage(Body attacker, Body victim, double amount, Optional<DuelSkill> skill, long tick) {
        if (!physics.vanillaInvulnerability()) {
            if (amount <= 0 || tick < victim.invulnerableUntil || victim.health <= 0) {
                if (amount > 0 && victim.health > 0) {
                    attacker.lostToInvulnerability++;
                }
                return;
            }
            victim.invulnerableUntil = tick + INVULNERABLE_TICKS;
            land(attacker, victim, amount, amount, skill, tick);
            return;
        }
        if (amount <= 0 || victim.health <= 0) {
            return;
        }
        var inWindow = tick < victim.invulnerableUntil;
        if (inWindow && amount <= victim.lastHurt) {
            attacker.lostToInvulnerability++;
            return;
        }
        var dealt = inWindow ? amount - victim.lastHurt : amount;
        if (!inWindow) {
            victim.invulnerableUntil = tick + INVULNERABLE_TICKS;
        }
        victim.lastHurt = amount;
        land(attacker, victim, dealt, dealt, skill, tick);
        Motion.knockBack(this, attacker, victim);
    }

    private void land(Body attacker, Body victim, double dealt, double reported, Optional<DuelSkill> skill,
                      long tick) {
        var absorbed = Math.min(victim.absorption, dealt);
        victim.absorption -= absorbed;
        victim.health -= dealt - absorbed;
        victim.damagedTick = tick;
        hits.accept(new Hit(attacker, victim, reported, skill, tick, dealt - absorbed));
    }

    private void step(Body mover, double length, boolean toward) {
        travel(mover, rival(mover), length, toward);
    }

    /**
     * Moves {@code mover} toward or away from {@code target} and keeps it inside the arena.
     *
     * @param mover the body that moves
     * @param target the body it moves relative to
     * @param length blocks to move
     * @param toward whether the step closes the gap
     */
    protected void travel(Body mover, Body target, double length, boolean toward) {
        if (length <= 0) {
            return;
        }
        var dx = target.x - mover.x;
        var dz = target.z - mover.z;
        var distance = Math.hypot(dx, dz);
        if (distance == 0) {
            return;
        }
        var sign = toward ? 1 : -1;
        mover.x += sign * dx / distance * length;
        mover.z += sign * dz / distance * length;
        var fromCenter = Math.hypot(mover.x, mover.z);
        if (fromCenter > ARENA_RADIUS) {
            mover.x *= ARENA_RADIUS / fromCenter;
            mover.z *= ARENA_RADIUS / fromCenter;
        }
    }
}
