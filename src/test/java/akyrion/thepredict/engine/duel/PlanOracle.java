package akyrion.thepredict.engine.duel;

import akyrion.thepredict.domain.GroupPlan;

/**
 * Clones the world and plays each possible plan for 40 ticks against the frozen rival (M25.12).
 * The plan with the best effect is the one that removes the most rival health net of friendly health.
 */
final class PlanOracle {
    static final int HORIZON = 40;

    private PlanOracle() {
    }

    /**
     * Picks a plan.
     *
     * @param world the live world
     * @param rival the frozen rival
     * @param seed the fight seed
     * @param mobs how many mobs the plan needs
     * @return the plan with the best effect, or press when none is possible
     */
    static GroupPlan choose(SkirmishWorld world, SquadOpponent rival, long seed, int mobs) {
        GroupPlan best = null;
        var bestEffect = Double.NEGATIVE_INFINITY;
        for (var plan : GroupPlan.values()) {
            if (!possible(plan, mobs, world)) {
                continue;
            }
            var effect = effect(world.copy(), plan, rival, seed);
            if (effect > bestEffect) {
                bestEffect = effect;
                best = plan;
            }
        }
        return best == null ? GroupPlan.PRESS_MAIN_TARGET : best;
    }

    private static boolean possible(GroupPlan plan, int mobs, SkirmishWorld world) {
        var players = 0;
        for (var body : world.bodies) {
            if (body.player && body.health > 0) {
                players++;
            }
        }
        return switch (plan) {
            case PRESS_MAIN_TARGET, HARASS_FROM_RANGE -> mobs >= 2;
            case SURROUND_TARGET -> mobs >= 3;
            case SPLIT_TARGETS -> mobs >= 4 && players >= 2;
            case SCREEN_THE_LEADER -> mobs >= 2;
            case FALL_BACK_AND_REGROUP, SEARCH_LAST_KNOWN -> mobs >= 2;
        };
    }

    private static double effect(SkirmishWorld world, GroupPlan plan, SquadOpponent rival, long seed) {
        var players = health(world, true);
        var mobs = health(world, false);
        for (var tick = 0; tick < HORIZON; tick++) {
            if (!world.living(true) || world.mobsAlive() == 0) {
                break;
            }
            world.land(tick);
            step(world, plan, rival, seed, tick);
            world.separate();
        }
        return (players - health(world, true)) - (mobs - health(world, false));
    }

    private static void step(SkirmishWorld world, GroupPlan plan, SquadOpponent rival, long seed, long tick) {
        for (var body : world.bodies) {
            if (body.health <= 0) {
                continue;
            }
            var threat = world.nearestRival(body);
            if (threat == null) {
                continue;
            }
            if (body.player) {
                rival.movePlayer(world, body, threat);
                continue;
            }
            moveMob(world, body, threat, plan);
            var skill = rival.offer(world, body, threat, tick, seed, true);
            if (skill != null) {
                var target = skill == DuelSkill.HEAL ? SquadOpponent.healTarget(world, body) : threat;
                world.cast(body, target, skill, tick, false);
            }
        }
    }

    private static void moveMob(SkirmishWorld world, SkirmishWorld.Fighter mob, SkirmishWorld.Fighter target,
                                GroupPlan plan) {
        switch (plan) {
            case HARASS_FROM_RANGE -> {
                var distance = world.distance(mob, target);
                if (distance < 8) {
                    world.moveToward(mob, target, 0, false);
                } else if (distance > 12) {
                    world.moveToward(mob, target, 12, true);
                }
            }
            case FALL_BACK_AND_REGROUP, SEARCH_LAST_KNOWN -> world.moveToward(mob, target, 0, false);
            default -> world.moveToward(mob, target, 2, true);
        }
    }

    private static double health(SkirmishWorld world, boolean player) {
        var sum = 0.0;
        for (var body : world.bodies) {
            if (body.player == player && body.health > 0) {
                sum += body.health;
            }
        }
        return sum;
    }
}
