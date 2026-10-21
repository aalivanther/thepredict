package akyrion.thepredict.engine.duel;

import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.MovementSettings;

/**
 * Movement of the fair round (§7.2 of the simulator audit, SIM-04, SIM-09). A {@link Steer} is the movement goal a
 * body runs this tick; both sides pick theirs from the same picture and {@link #advance} moves them together. A
 * ThePredict intent steers as the goals of {@code GoalLineGenerator} do on a server: {@code APPROACH} is
 * {@code moveTowardsTargetConditional} down to the melee reach and then the Paper {@code hold}, {@code KEEP_DISTANCE}
 * is {@code fleeConditional} below the inner edge and {@code moveWithinConditional} beyond the outer one at the
 * approach speed, {@code RETREAT} is {@code fleeConditional} at the retreat speed. A classic steers with its native
 * goals ({@link ClassicDuelist}). With {@link DuelPhysics#LEGACY} a step is the length of M18.7, cut so it does not
 * overshoot; otherwise the body turns, accelerates and slides with the friction of the ground. Pure functions of the
 * world.
 */
final class Motion {
    private Motion() {
    }

    /**
     * The movement goal of one tick.
     *
     * @param kind where it goes
     * @param speed the speed modifier of the goal
     * @param limit the distance to the rival where the goal stops: the least for {@code TOWARD}, the most for
     *     {@code AWAY}
     */
    record Steer(Kind kind, double speed, double limit) {
        /** Where a goal goes. */
        enum Kind {
            /** No goal moves the body. */
            HOLD,
            /** Toward the rival while farther than the limit. */
            TOWARD,
            /** Away from the rival while closer than the limit. */
            AWAY
        }

        /** No movement. */
        static final Steer HOLD = new Steer(Kind.HOLD, 0, 0);

        static Steer toward(double speed, double limit) {
            return new Steer(Kind.TOWARD, speed, limit);
        }

        static Steer away(double speed, double limit) {
            return new Steer(Kind.AWAY, speed, limit);
        }

        /** Whether the goal wants to move at this distance. */
        boolean active(double distance) {
            return kind == Kind.TOWARD ? distance > limit : kind == Kind.AWAY && distance < limit;
        }
    }

    /**
     * The goal of a ThePredict intent, as the native goals of the server run it.
     *
     * @param world the picture of the tick
     * @param movement movement of the intent
     * @param settings movement settings of the profile
     * @return the goal
     */
    static Steer intent(DuelWorld world, Movement movement, MovementSettings settings) {
        var distance = world.distance();
        return switch (movement) {
            case APPROACH, FLANK -> Steer.toward(settings.approachSpeed(), settings.meleeReachBlocks());
            case KEEP_DISTANCE -> distance < settings.bandInnerBlocks()
                    ? Steer.away(settings.approachSpeed(), settings.bandInnerBlocks())
                    : distance > settings.bandOuterBlocks() ? Steer.toward(settings.approachSpeed(),
                    settings.bandOuterBlocks()) : Steer.HOLD;
            case RETREAT -> Steer.away(settings.retreatSpeed(), settings.retreatDistanceBlocks());
            case HOLD, NONE -> Steer.HOLD;
        };
    }

    /**
     * Keeps the goal a body selected unless this tick selects goals again ({@link DuelPhysics#goalSwitchTicks}), on
     * the parity of the body: Paper selects goals when {@code tickCount + id} is even, so two mobs need not select on
     * the same tick.
     *
     * @param world the world
     * @param body the body
     * @param wanted the goal it would select now
     * @param index tick of the round
     */
    static void select(DuelWorld world, DuelWorld.Body body, Steer wanted, int index) {
        if (selects(world, body, index)) {
            body.goal = wanted;
        }
    }

    /** Whether a body selects its goals on this tick. */
    static boolean selects(DuelWorld world, DuelWorld.Body body, int index) {
        return (index + body.goalPhase) % world.physics.goalSwitchTicks() == 0;
    }

    /**
     * Moves both bodies one tick together, each by the goal it selected, from the positions before either moved; then
     * separates two overlapping boxes.
     *
     * @param world the world
     */
    static void advance(DuelWorld world) {
        var distance = world.distance();
        var redX = world.blue.x - world.red.x;
        var redZ = world.blue.z - world.red.z;
        move(world, world.red, redX, redZ, distance);
        move(world, world.blue, -redX, -redZ, distance);
        separate(world);
    }

    private static void move(DuelWorld world, DuelWorld.Body body, double towardX, double towardZ, double distance) {
        if (body.flightTicks > 0) {
            body.x += body.flightX;
            body.z += body.flightZ;
            body.flightTicks--;
            world.arena.keepInside(body);
            return;
        }
        var goal = body.goal;
        var active = goal.active(distance) && distance > 1.0e-9;
        var sign = goal.kind() == Steer.Kind.AWAY ? -1.0 : 1.0;
        var headingX = active ? sign * towardX / distance : 0.0;
        var headingZ = active ? sign * towardZ / distance : 0.0;
        if (world.physics.legacy() || world.physics.groundFriction() == 0) {
            var room = goal.kind() == Steer.Kind.TOWARD ? distance - goal.limit() : Double.MAX_VALUE;
            var length = active ? Math.min(world.physics.step(goal.speed()), room) : 0.0;
            body.x += headingX * length;
            body.z += headingZ * length;
        } else {
            slide(world.physics, body, active, headingX, headingZ, goal.speed());
        }
        world.arena.keepInside(body);
    }

    private static void slide(DuelPhysics physics, DuelWorld.Body body, boolean active, double headingX,
                              double headingZ, double speed) {
        var inputX = 0.0;
        var inputZ = 0.0;
        if (active) {
            turn(physics, body, Math.atan2(headingZ, headingX));
            var input = physics.input(speed);
            inputX = Math.cos(body.yaw) * input;
            inputZ = Math.sin(body.yaw) * input;
        }
        var stepX = body.vx + inputX;
        var stepZ = body.vz + inputZ;
        body.x += stepX;
        body.z += stepZ;
        body.vx = stepX * physics.groundFriction();
        body.vz = stepZ * physics.groundFriction();
    }

    private static void turn(DuelPhysics physics, DuelWorld.Body body, double wanted) {
        if (Double.isNaN(body.yaw)) {
            body.yaw = wanted;
            return;
        }
        var delta = Math.IEEEremainder(wanted - body.yaw, 2 * Math.PI);
        var most = Math.toRadians(physics.maxTurnDegreesPerTick());
        body.yaw += Math.max(-most, Math.min(most, delta));
    }

    private static void separate(DuelWorld world) {
        var dx = world.blue.x - world.red.x;
        var dz = world.blue.z - world.red.z;
        var distance = Math.hypot(dx, dz);
        if (distance >= DuelWorld.WIDTH) {
            return;
        }
        var nx = distance < 1.0e-9 ? 1.0 : dx / distance;
        var nz = distance < 1.0e-9 ? 0.0 : dz / distance;
        var half = (DuelWorld.WIDTH - distance) / 2;
        world.red.x -= nx * half;
        world.red.z -= nz * half;
        world.blue.x += nx * half;
        world.blue.z += nz * half;
        world.arena.keepInside(world.red);
        world.arena.keepInside(world.blue);
    }

    /**
     * A leap toward the target: the landing point is {@value DuelWorld#LEAP_LANDING} blocks from it on the line
     * between them. In the world of M18.7 the caster is there at once; otherwise it flies there in
     * {@link DuelPhysics#leapFlightTicks} equal steps.
     *
     * @param world the world
     * @param caster who leaps
     */
    static void leap(DuelWorld world, DuelWorld.Body caster) {
        var target = world.rival(caster);
        var distance = world.distance();
        if (distance <= DuelWorld.LEAP_LANDING) {
            return;
        }
        var landX = target.x + (caster.x - target.x) * DuelWorld.LEAP_LANDING / distance;
        var landZ = target.z + (caster.z - target.z) * DuelWorld.LEAP_LANDING / distance;
        var flight = world.physics.leapFlightTicks();
        if (flight == 0) {
            caster.x = landX;
            caster.z = landZ;
            return;
        }
        caster.flightTicks = flight;
        caster.flightX = (landX - caster.x) / flight;
        caster.flightZ = (landZ - caster.z) / flight;
        caster.vx = 0;
        caster.vz = 0;
    }

    /**
     * Pushes the victim of a damaging hit {@link DuelPhysics#knockbackBlocks} away from the attacker.
     *
     * @param world the world
     * @param attacker who hit
     * @param victim who was hit
     */
    static void knockBack(DuelWorld world, DuelWorld.Body attacker, DuelWorld.Body victim) {
        var push = world.physics.knockbackBlocks();
        var dx = victim.x - attacker.x;
        var dz = victim.z - attacker.z;
        var distance = Math.hypot(dx, dz);
        if (push <= 0 || distance < 1.0e-9) {
            return;
        }
        victim.x += dx / distance * push;
        victim.z += dz / distance * push;
        world.arena.keepInside(victim);
    }

    /**
     * Turns a body to face its rival, as a mob that spawns looking at its target.
     *
     * @param world the world
     * @param body the body
     */
    static void face(DuelWorld world, DuelWorld.Body body) {
        var rival = world.rival(body);
        body.yaw = Math.atan2(rival.z - body.z, rival.x - body.x);
    }
}
