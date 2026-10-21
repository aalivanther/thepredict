package akyrion.thepredict.engine.duel;

import akyrion.thepredict.domain.GroupFairnessReason;
import akyrion.thepredict.domain.GroupMode;
import akyrion.thepredict.domain.GroupPlan;
import akyrion.thepredict.domain.GroupRole;
import akyrion.thepredict.domain.Hungarian;
import akyrion.thepredict.domain.ReleaseOutcome;
import akyrion.thepredict.domain.RoleAssignment;
import akyrion.thepredict.domain.RoleCapabilities;
import akyrion.thepredict.domain.SlotAssignment;
import akyrion.thepredict.domain.SlotGeometry;
import akyrion.thepredict.domain.SurroundSlots;
import akyrion.thepredict.engine.AttackLedger;
import akyrion.thepredict.engine.SkirmishCast;
import akyrion.thepredict.engine.TellLedger;
import akyrion.thepredict.engine.duel.SkirmishWorld.Fighter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * One skirmish on {@link SkirmishWorld}. Tells open through {@link SkirmishCast}. A missed band is reported by the
 * caller and does not change a default.
 */
final class SkirmishFight {
    private SkirmishFight() {
    }

    /**
     * Plays one fight.
     *
     * @param fight scenario
     * @param arm group arm
     * @param seed fight seed
     * @param flip whether the arena is reflected
     * @param rival frozen rival
     * @param melee melee weight cap
     * @param gap impact gap
     * @param horizon last tick, exclusive
     * @param record whether the transcript and the pair counts are kept
     * @return the measured fight
     */
    static SkirmishSimulator.Measured play(SkirmishSimulator.Fight fight, SkirmishSimulator.Arm arm, long seed,
                                           boolean flip, SquadOpponent rival, int melee, int gap, int horizon,
                                           boolean record) {
        return play(fight, arm, seed, flip, rival, melee, gap, horizon, record, ExploitWatch.idle());
    }

    /**
     * Plays one fight. {@code watch} is idle on the record arm; on the seed arm it observes the scripted habit and its
     * pattern may become active, but nothing in the fight reads it: the memory of M27 acts only through the engine,
     * which this skirmish does not run, so both arms play the same fight (SIM-05). The transcript flag does not move
     * {@code deathTick} either.
     *
     * @param fight scenario
     * @param arm group arm
     * @param seed fight seed
     * @param flip whether the arena is reflected
     * @param rival frozen rival
     * @param melee melee weight cap
     * @param gap impact gap
     * @param horizon last tick, exclusive
     * @param record whether the transcript and the pair counts are kept
     * @param watch scripted habit of this arm
     * @return the measured fight
     */
    static SkirmishSimulator.Measured play(SkirmishSimulator.Fight fight, SkirmishSimulator.Arm arm, long seed,
                                           boolean flip, SquadOpponent rival, int melee, int gap, int horizon,
                                           boolean record, ExploitWatch watch) {
        var started = System.nanoTime();
        var impacts = new ArrayList<Integer>();
        var owners = new ArrayList<Integer>();
        var victims = new ArrayList<Integer>();
        var reserved = new ArrayList<Integer>();
        var reservedOwners = new ArrayList<Integer>();
        var reservedVictims = new ArrayList<Integer>();
        var text = new StringBuilder();
        var tally = new Tally();
        var world = new SkirmishWorld(SkirmishWorld.place(fight.mobs(), fight.players(), fight.healer(), flip),
                hit -> {
                    noteHit(hit, impacts, owners, victims, text, record);
                    noteSquadWound(hit, tally);
                });
        var plan = arm == SkirmishSimulator.Arm.FULL
                ? PlanOracle.choose(world, rival, seed, fight.mobs()) : GroupPlan.PRESS_MAIN_TARGET;
        var sessions = arm == SkirmishSimulator.Arm.OFF ? null : sessions(world, seed);
        var held = new Held[world.bodies.size()];
        var roles = new GroupRole[world.bodies.size()];
        var attack = new AttackLedger();
        var tells = new TellLedger();
        Arrays.fill(roles, GroupRole.PRESSURE);
        if (arm == SkirmishSimulator.Arm.COORDINATE || arm == SkirmishSimulator.Arm.FULL) {
            assignSlots(world);
        }
        var death = -1;
        for (var tick = 0; tick < horizon; tick++) {
            world.land(tick);
            if (!world.living(true)) {
                death = tick;
                break;
            }
            if (world.mobsAlive() == 0) {
                break;
            }
            if (tick % 100 == 0) {
                roles(world, roles, arm, tally);
            }
            healWatch(world, roles, tally);
            watch.observe(seed, tick);
            step(world, arm, rival, seed, melee, gap, tick, plan, sessions, held, attack, tells, roles, text, tally,
                    record, reserved, reservedOwners, reservedVictims);
            world.separate();
            if (record) {
                geometry(world, arm, tally);
            }
            if (!world.living(true)) {
                death = tick;
                break;
            }
        }
        return measured(text, impacts, reserved, reservedOwners, reservedVictims, gap, tally, death,
                System.nanoTime() - started);
    }

    private static void noteHit(DuelWorld.Hit hit, List<Integer> impacts, List<Integer> owners,
                                List<Integer> victims, StringBuilder text, boolean record) {
        if (hit.skill().isEmpty() || !(hit.attacker() instanceof Fighter attacker)
                || !(hit.victim() instanceof Fighter victim)) {
            return;
        }
        impacts.add((int) hit.tick());
        owners.add(attacker.index);
        victims.add(victim.index);
        if (record) {
            text.append(hit.tick()).append(',').append(attacker.player ? 1 : 0).append(',').append(attacker.index)
                    .append(',').append(hit.skill().get().id).append(",hit\n");
        }
    }

    private static void noteSquadWound(DuelWorld.Hit hit, Tally tally) {
        if (!(hit.attacker() instanceof Fighter attacker) || !(hit.victim() instanceof Fighter victim)) {
            return;
        }
        if (attacker.player && !victim.player && hit.damage() > 0) {
            tally.squadWounds++;
        }
    }

    private static akyrion.thepredict.engine.MobSession[] sessions(SkirmishWorld world, long seed) {
        var sessions = new akyrion.thepredict.engine.MobSession[world.bodies.size()];
        for (var body : world.bodies) {
            var session = SkirmishCast.session(body.id, SkirmishKit.profile(body.weight), body.id, !body.player);
            SkirmishCast.reseed(session, seed);
            sessions[body.index] = session;
        }
        return sessions;
    }

    private static void step(SkirmishWorld world, SkirmishSimulator.Arm arm, SquadOpponent rival, long seed,
                             int melee, int gap, int tick, GroupPlan plan,
                             akyrion.thepredict.engine.MobSession[] sessions, Held[] held, AttackLedger attack,
                             TellLedger tells, GroupRole[] roles, StringBuilder text, Tally tally, boolean record,
                             List<Integer> reserved, List<Integer> reservedOwners, List<Integer> reservedVictims) {
        for (var body : world.bodies) {
            if (body.health <= 0) {
                continue;
            }
            var threat = world.nearestRival(body);
            if (threat == null) {
                continue;
            }
            if (body.player) {
                stepPlayer(world, body, threat, arm, rival, seed, melee, gap, tick, sessions, held, attack, tells,
                        text, tally, record, reserved, reservedOwners, reservedVictims);
                continue;
            }
            moveMob(world, body, threat, arm, plan);
            act(world, body, threat, arm, rival, seed, melee, gap, tick, sessions, held, attack, tells, text, tally,
                    record, reserved, reservedOwners, reservedVictims);
        }
    }

    private static void stepPlayer(SkirmishWorld world, Fighter body, Fighter threat, SkirmishSimulator.Arm arm,
                                   SquadOpponent rival, long seed, int melee, int gap, int tick,
                                   akyrion.thepredict.engine.MobSession[] sessions, Held[] held, AttackLedger attack,
                                   TellLedger tells, StringBuilder text, Tally tally, boolean record,
                                   List<Integer> reserved, List<Integer> reservedOwners,
                                   List<Integer> reservedVictims) {
        if (rival.kind == SquadOpponent.Kind.MIRROR) {
            rival.movePlayer(world, body, threat);
            act(world, body, threat, arm, rival, seed, melee, gap, tick, sessions, held, attack, tells, text, tally,
                    record, reserved, reservedOwners, reservedVictims);
            return;
        }
        if (!squadFight(world)) {
            rival.movePlayer(world, body, threat);
            return;
        }
        var victim = squadMate(world, body);
        if (victim == null) {
            rival.movePlayer(world, body, threat);
            return;
        }
        rival.movePlayer(world, body, victim);
        var skill = rival.offer(world, body, victim, tick, seed, false);
        if (skill == null || skill.damage <= 0) {
            return;
        }
        launch(world, body, victim, skill, tick, rival, seed, 0, tally, text, record);
    }

    private static boolean squadFight(SkirmishWorld world) {
        for (var body : world.bodies) {
            if (body.healer) {
                return true;
            }
        }
        return false;
    }

    private static Fighter squadMate(SkirmishWorld world, Fighter player) {
        var healer = livingHealer(world);
        var near = nearestSquad(world, player, healer, true);
        return near != null ? near : nearestSquad(world, player, healer, false);
    }

    private static Fighter livingHealer(SkirmishWorld world) {
        for (var body : world.bodies) {
            if (body.healer && body.health > 0) {
                return body;
            }
        }
        return null;
    }

    private static Fighter nearestSquad(SkirmishWorld world, Fighter player, Fighter healer, boolean inReach) {
        Fighter chosen = null;
        var best = Double.MAX_VALUE;
        for (var body : world.bodies) {
            if (body.player || body.health <= 0 || body == healer) {
                continue;
            }
            if (inReach && healer != null && world.distance(healer, body) > 10) {
                continue;
            }
            var distance = world.distance(player, body);
            if (distance < best) {
                best = distance;
                chosen = body;
            }
        }
        return chosen;
    }

    private static void act(SkirmishWorld world, Fighter body, Fighter threat, SkirmishSimulator.Arm arm,
                            SquadOpponent rival, long seed, int melee, int gap, int tick,
                            akyrion.thepredict.engine.MobSession[] sessions, Held[] held, AttackLedger attack,
                            TellLedger tells, StringBuilder text, Tally tally, boolean record,
                            List<Integer> reserved, List<Integer> reservedOwners, List<Integer> reservedVictims) {
        var session = sessions == null ? null : sessions[body.index];
        if (session != null && SkirmishCast.due(session, tick)) {
            release(world, body, rival, seed, tick, session, held, tally, text, record, reserved, reservedOwners,
                    reservedVictims);
        }
        if (session != null && SkirmishCast.telling(session)) {
            return;
        }
        var skill = rival.offer(world, body, threat, tick, seed, !body.player);
        if (skill == null) {
            return;
        }
        var target = skill == DuelSkill.HEAL ? SquadOpponent.healTarget(world, body) : threat;
        if (arm == SkirmishSimulator.Arm.OFF || session == null) {
            launch(world, body, target, skill, tick, rival, seed, 0, tally, text, record);
            return;
        }
        var playerIndex = target.player ? target.index - mobs(world) : -1;
        SkirmishCast.bind(session, attack, tells, body.index, mode(arm), melee, gap, playerIndex);
        var opening = SkirmishCast.open(session, SkirmishKit.choice(skill), target.id, world.distance(body, target),
                tick);
        if (opening.denial().isPresent()) {
            deny(opening.denial().get(), tally, text, tick, body, skill, record);
            return;
        }
        var impact = skill.damage > 0 && playerIndex >= 0 ? (int) SkirmishCast.impact(session) : -1;
        if (impact >= 0) {
            keepReserve(reserved, reservedOwners, reservedVictims, impact, body.index, target.index);
        }
        if (opening.held()) {
            held[body.index] = new Held(skill, target.index, SkirmishCast.shown(session), impact);
            return;
        }
        var shown = opening.untold() ? 0 : SkirmishCast.shown(session);
        launch(world, body, target, skill, tick, rival, seed, shown, tally, text, record);
        if (opening.untold()) {
            SkirmishCast.settle(session, tick);
        } else {
            SkirmishCast.after(session, tick);
        }
    }

    private static void release(SkirmishWorld world, Fighter body, SquadOpponent rival, long seed, int tick,
                                akyrion.thepredict.engine.MobSession session, Held[] held, Tally tally,
                                StringBuilder text, boolean record, List<Integer> reserved,
                                List<Integer> reservedOwners, List<Integer> reservedVictims) {
        var outcome = SkirmishCast.release(session, tick, SkirmishKit.ACTIONS);
        if (SkirmishCast.telling(session)) {
            return;
        }
        var waiting = held[body.index];
        held[body.index] = null;
        if (waiting != null && waiting.impact >= 0 && outcome != ReleaseOutcome.RELEASED) {
            dropReserve(reserved, reservedOwners, reservedVictims, waiting.impact, body.index);
        }
        if (outcome == ReleaseOutcome.RELEASED && waiting != null && world.bodies.get(waiting.target).health > 0) {
            launch(world, body, world.bodies.get(waiting.target), waiting.skill, tick, rival, seed, waiting.shown,
                    tally, text, record);
        }
    }

    private static void keepReserve(List<Integer> ticks, List<Integer> owners, List<Integer> victims, int impact,
                                    int owner, int victim) {
        ticks.add(impact);
        owners.add(owner);
        victims.add(victim);
    }

    private static void dropReserve(List<Integer> ticks, List<Integer> owners, List<Integer> victims, int impact,
                                    int owner) {
        for (var index = ticks.size() - 1; index >= 0; index--) {
            if (ticks.get(index) == impact && owners.get(index) == owner) {
                ticks.remove(index);
                owners.remove(index);
                victims.remove(index);
                return;
            }
        }
    }

    private static void launch(SkirmishWorld world, Fighter body, Fighter target, DuelSkill skill, int tick,
                               SquadOpponent rival, long seed, int shown, Tally tally, StringBuilder text,
                               boolean record) {
        var onWounded = skill == DuelSkill.HEAL && body.healer && target != body && !target.player
                && target.health > 0 && target.healthRatio() < 0.5;
        world.cast(body, target, skill, tick, rival.avoids(seed, tick, body.index, shown));
        if (skill == DuelSkill.HEAL && body.healer) {
            tally.heals++;
            if (onWounded) {
                tally.healsOnWounded++;
            }
        }
        if (record) {
            text.append(tick).append(',').append(body.player ? 1 : 0).append(',').append(body.index).append(',')
                    .append(skill.id).append(",cast\n");
        }
    }

    private static void deny(GroupFairnessReason reason, Tally tally, StringBuilder text, int tick, Fighter body,
                             DuelSkill skill, boolean record) {
        tally.denials++;
        if (reason == GroupFairnessReason.SECOND_HAND) {
            tally.secondHand++;
        }
        if (reason == GroupFairnessReason.ANSWER_CONFLICT) {
            tally.answerConflict++;
        }
        if (record) {
            text.append(tick).append(',').append(body.player ? 1 : 0).append(',').append(body.index).append(',')
                    .append(skill.id).append(",denied\n");
        }
    }

    private static void moveMob(SkirmishWorld world, Fighter mob, Fighter target, SkirmishSimulator.Arm arm,
                                GroupPlan plan) {
        if (arm == SkirmishSimulator.Arm.FULL) {
            movePlan(world, mob, target, plan);
            return;
        }
        if ((arm == SkirmishSimulator.Arm.COORDINATE) && mob.slot >= 0) {
            var anchor = player(world);
            world.moveToward(mob, anchor.x + SlotGeometry.east(mob.slot), anchor.z + SlotGeometry.south(mob.slot),
                    0.3);
            return;
        }
        world.moveToward(mob, target, 2, true);
    }

    private static void movePlan(SkirmishWorld world, Fighter mob, Fighter target, GroupPlan plan) {
        if (mob.slot >= 0 && (plan == GroupPlan.PRESS_MAIN_TARGET || plan == GroupPlan.SURROUND_TARGET
                || plan == GroupPlan.SCREEN_THE_LEADER)) {
            var anchor = player(world);
            world.moveToward(mob, anchor.x + SlotGeometry.east(mob.slot), anchor.z + SlotGeometry.south(mob.slot),
                    0.3);
            return;
        }
        switch (plan) {
            case HARASS_FROM_RANGE -> harass(world, mob, target);
            case FALL_BACK_AND_REGROUP, SEARCH_LAST_KNOWN -> world.moveToward(mob, target, 0, false);
            default -> world.moveToward(mob, target, 2, true);
        }
    }

    private static void harass(SkirmishWorld world, Fighter mob, Fighter target) {
        var distance = world.distance(mob, target);
        if (distance < 8) {
            world.moveToward(mob, target, 0, false);
        } else if (distance > 12) {
            world.moveToward(mob, target, 12, true);
        }
    }

    private static void roles(SkirmishWorld world, GroupRole[] roles, SkirmishSimulator.Arm arm, Tally tally) {
        if (arm != SkirmishSimulator.Arm.COORDINATE && arm != SkirmishSimulator.Arm.FULL) {
            return;
        }
        var mobs = mobs(world);
        if (mobs < 2) {
            return;
        }
        var capabilities = new RoleCapabilities[mobs];
        var distance = new int[mobs][GroupRole.values().length];
        var current = new GroupRole[mobs];
        var assigned = new GroupRole[mobs];
        var cursor = 0;
        for (var body : world.bodies) {
            if (body.player) {
                continue;
            }
            capabilities[cursor] = body.healer ? new RoleCapabilities(0, 0, 0, 800, 0, 400)
                    : new RoleCapabilities(500, 200, body.index == 1 ? 400 : 0, 0, 0, 400);
            current[cursor] = roles[body.index];
            cursor++;
        }
        try {
            RoleAssignment.assign(capabilities, distance, current, 150, new Hungarian(mobs), assigned);
        } catch (IllegalStateException exception) {
            return;
        }
        var changes = 0;
        cursor = 0;
        for (var body : world.bodies) {
            if (body.player) {
                continue;
            }
            if (roles[body.index] != assigned[cursor]) {
                changes++;
            }
            roles[body.index] = assigned[cursor];
            cursor++;
        }
        tally.roleSamples++;
        if (tally.roleSamples > 1) {
            tally.roleChanges = Math.max(tally.roleChanges, changes);
        }
    }

    private static void assignSlots(SkirmishWorld world) {
        var mobs = mobs(world);
        if (mobs < 2) {
            return;
        }
        var roles = new GroupRole[mobs];
        var current = new int[mobs];
        var valid = new boolean[48];
        Arrays.fill(roles, GroupRole.PRESSURE);
        Arrays.fill(current, -1);
        Arrays.fill(valid, true);
        for (var ring = 0; ring < 3; ring++) {
            var angles = ring == 2 ? SurroundSlots.RANGED : ring == 0 ? SurroundSlots.INNER : SurroundSlots.OUTER;
            for (var angle = 0; angle < angles; angle++) {
                if (SurroundSlots.inExitSector(SurroundSlots.angleDegrees(angle, angles), 90)) {
                    valid[ring * 16 + angle] = false;
                }
            }
        }
        var assigned = new int[mobs];
        try {
            SlotAssignment.assign(roles, current, valid, 0, SlotAssignment.guardAngle(180, 90, false),
                    new Hungarian(24), assigned);
        } catch (IllegalStateException exception) {
            return;
        }
        var cursor = 0;
        for (var body : world.bodies) {
            if (!body.player) {
                body.slot = assigned[cursor++];
            }
        }
    }

    private static void geometry(SkirmishWorld world, SkirmishSimulator.Arm arm, Tally tally) {
        var mobs = new ArrayList<Fighter>();
        for (var body : world.bodies) {
            if (!body.player && body.health > 0) {
                mobs.add(body);
            }
        }
        for (var left = 0; left < mobs.size(); left++) {
            for (var right = left + 1; right < mobs.size(); right++) {
                if (world.distance(mobs.get(left), mobs.get(right)) < 1.0) {
                    tally.clump++;
                }
            }
        }
        if (arm != SkirmishSimulator.Arm.COORDINATE && arm != SkirmishSimulator.Arm.FULL) {
            return;
        }
        var anchor = player(world);
        if (anchor == null) {
            return;
        }
        tally.sectorTicks += mobs.size();
        for (var mob : mobs) {
            var degrees = Math.floorMod((int) Math.round(Math.toDegrees(Math.atan2(mob.z - anchor.z,
                    mob.x - anchor.x))), 360);
            if (!SurroundSlots.inExitSector(degrees, 90)) {
                tally.sectorFree++;
            }
        }
    }

    private static void healWatch(SkirmishWorld world, GroupRole[] roles, Tally tally) {
        Fighter healer = null;
        for (var body : world.bodies) {
            if (body.healer && body.health > 0) {
                healer = body;
            }
        }
        if (healer == null) {
            return;
        }
        tally.healerTicks++;
        if (roles[healer.index] == GroupRole.SUPPORT) {
            tally.support++;
        }
        for (var body : world.bodies) {
            if (!body.player && body != healer && body.health > 0 && body.healthRatio() < 0.5
                    && world.distance(healer, body) <= 10) {
                tally.healChances++;
                return;
            }
        }
    }

    private static SkirmishSimulator.Measured measured(StringBuilder text, List<Integer> impacts,
                                                      List<Integer> reserved, List<Integer> reservedOwners,
                                                      List<Integer> reservedVictims, int gap, Tally tally, int death,
                                                      long nanos) {
        return new SkirmishSimulator.Measured(text.toString(), impacts.size(), tally.denials,
                violations(reserved, reservedOwners, reservedVictims, gap), triples(impacts),
                percentile(windows(impacts, horizonOf(impacts)), 99),
                tally.clump, tally.sectorFree, tally.sectorTicks, tally.support, tally.healerTicks, tally.heals,
                tally.healChances, tally.roleChanges, tally.roleSamples, nanos, tally.secondHand, tally.answerConflict,
                death, tally.healsOnWounded, tally.squadWounds);
    }

    private static int violations(List<Integer> impacts, List<Integer> owners, List<Integer> victims, int gap) {
        var found = 0;
        for (var left = 0; left < impacts.size(); left++) {
            for (var right = left + 1; right < impacts.size(); right++) {
                if (owners.get(left).equals(owners.get(right)) || !victims.get(left).equals(victims.get(right))) {
                    continue;
                }
                if (Math.abs(impacts.get(left) - impacts.get(right)) < gap) {
                    found++;
                }
            }
        }
        return found;
    }

    private static int triples(List<Integer> impacts) {
        var found = 0;
        for (var index = 0; index < impacts.size(); index++) {
            var near = 0;
            for (var other = 0; other < impacts.size(); other++) {
                if (other != index && Math.abs(impacts.get(index) - impacts.get(other)) <= 5) {
                    near++;
                }
            }
            if (near >= 2) {
                found++;
            }
        }
        return found;
    }

    private static int horizonOf(List<Integer> impacts) {
        var horizon = 20;
        for (var impact : impacts) {
            horizon = Math.max(horizon, impact + 1);
        }
        return horizon;
    }

    private static int[] windows(List<Integer> impacts, int horizon) {
        var counts = new int[Math.max(1, horizon / 20)];
        for (var impact : impacts) {
            if (impact >= 0) {
                counts[Math.min(counts.length - 1, impact / 20)]++;
            }
        }
        return counts;
    }

    private static int percentile(int[] values, int percent) {
        var copy = values.clone();
        Arrays.sort(copy);
        return copy[Math.min(copy.length - 1, copy.length * percent / 100)];
    }

    private static GroupMode mode(SkirmishSimulator.Arm arm) {
        return switch (arm) {
            case OFF -> GroupMode.OFF;
            case FAIR -> GroupMode.FAIR;
            case COORDINATE -> GroupMode.COORDINATE;
            case FULL -> GroupMode.FULL;
        };
    }

    private static int mobs(SkirmishWorld world) {
        var count = 0;
        for (var body : world.bodies) {
            if (!body.player) {
                count++;
            }
        }
        return count;
    }

    private static Fighter player(SkirmishWorld world) {
        for (var body : world.bodies) {
            if (body.player && body.health > 0) {
                return body;
            }
        }
        return world.bodies.getLast();
    }

    private record Held(DuelSkill skill, int target, int shown, int impact) {
    }

    private static final class Tally {
        int denials;
        int secondHand;
        int answerConflict;
        int clump;
        int sectorFree;
        int sectorTicks;
        int support;
        int healerTicks;
        int heals;
        int healsOnWounded;
        int squadWounds;
        int healChances;
        int roleChanges;
        int roleSamples;
    }
}
