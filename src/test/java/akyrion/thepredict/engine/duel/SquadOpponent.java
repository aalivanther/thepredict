package akyrion.thepredict.engine.duel;

import akyrion.thepredict.eval.SyntheticPlayer;

/**
 * Rivals of M25.12. {@code classic-tuned} reads the frozen cell {@code delay=10}, {@code band=5},
 * {@code ratio=0.30}, {@code guard=true}. {@code local-best} is each member alone, with group mode off.
 */
final class SquadOpponent {
    enum Kind {
        CLASSIC, CLASSIC_TUNED, LOCAL_BEST, NONE, MIRROR, SWORD, SHIELD_FLANKER, KITER_ARCHER, TOWER, CRIT_JUMPER
    }

    final Kind kind;
    final double blockChance;
    final int reactionTicks;

    private SquadOpponent(Kind kind, double blockChance, int reactionTicks) {
        this.kind = kind;
        this.blockChance = blockChance;
        this.reactionTicks = reactionTicks;
    }

    static SquadOpponent of(String name) {
        return switch (name) {
            case "none" -> new SquadOpponent(Kind.NONE, 0, 0);
            case "classic" -> new SquadOpponent(Kind.CLASSIC, 0, 0);
            case "classic-tuned" -> new SquadOpponent(Kind.CLASSIC_TUNED, 0, 0);
            case "local-best" -> new SquadOpponent(Kind.LOCAL_BEST, 0, 0);
            case "mirror" -> new SquadOpponent(Kind.MIRROR, 0, 0);
            case "sword" -> sword();
            case "shield-flanker" -> synthetic(Kind.SHIELD_FLANKER, name);
            case "kiter-archer" -> synthetic(Kind.KITER_ARCHER, name);
            case "tower" -> synthetic(Kind.TOWER, name);
            case "crit-jumper" -> synthetic(Kind.CRIT_JUMPER, name);
            default -> throw new IllegalArgumentException(name);
        };
    }

    private static SquadOpponent sword() {
        var player = new SyntheticPlayer("shield-flanker", 0.5);
        return new SquadOpponent(Kind.SWORD, player.blockChance(), player.reactionTicks());
    }

    private static SquadOpponent synthetic(Kind kind, String name) {
        var player = new SyntheticPlayer(name, 0.5);
        return new SquadOpponent(kind, player.blockChance(), player.reactionTicks());
    }

    /** Moves one player body for this rival. */
    void movePlayer(SkirmishWorld world, SkirmishWorld.Fighter player, SkirmishWorld.Fighter threat) {
        var distance = world.distance(player, threat);
        switch (kind) {
            case KITER_ARCHER -> kite(world, player, threat, distance);
            case TOWER -> {
                if (distance > 6) {
                    world.moveToward(player, threat, 6, true);
                }
            }
            case SHIELD_FLANKER -> orbit(player, threat);
            case CRIT_JUMPER, SWORD -> world.moveToward(player, threat, 2, true);
            default -> world.moveToward(player, threat, 2, true);
        }
    }

    private static void kite(SkirmishWorld world, SkirmishWorld.Fighter player, SkirmishWorld.Fighter threat,
                             double distance) {
        if (distance < 8) {
            world.moveToward(player, threat, 0, false);
        } else if (distance > 12) {
            world.moveToward(player, threat, 12, true);
        }
    }

    private static void orbit(SkirmishWorld.Fighter player, SkirmishWorld.Fighter threat) {
        var dx = player.x - threat.x;
        var dz = player.z - threat.z;
        var distance = Math.hypot(dx, dz);
        if (distance < 1.0e-6) {
            player.x += DuelWorld.SPEED;
            return;
        }
        player.x += -dz / distance * DuelWorld.SPEED;
        player.z += dx / distance * DuelWorld.SPEED;
    }

    /**
     * The skill a mob would cast this tick, or null when none is ready. Tuned rules use period 10, band 5,
     * a heal ratio of 0.30 and a reactive guard.
     *
     * @param world the arena
     * @param mob the caster
     * @param target its rival
     * @param tick current tick
     * @param seed fight seed
     * @param mobs whether this side is the coordinated side; local-best ignores the group
     * @return the skill, or null
     */
    DuelSkill offer(SkirmishWorld world, SkirmishWorld.Fighter mob, SkirmishWorld.Fighter target, long tick,
                    long seed, boolean mobs) {
        if (kind == Kind.LOCAL_BEST && mobs) {
            return best(world, mob, target, tick);
        }
        if (mob.healer) {
            var ally = woundedAlly(world, mob);
            if (ally != null && mob.ready(DuelSkill.HEAL, tick) && world.distance(mob, ally) <= 10) {
                return DuelSkill.HEAL;
            }
        }
        if (kind == Kind.CLASSIC_TUNED && mob.ready(DuelSkill.GUARD, tick)
                && world.distance(mob, target) < 3 && mob.healthRatio() < 0.6) {
            return DuelSkill.GUARD;
        }
        return rule(world, mob, target, tick, seed);
    }

    private DuelSkill rule(SkirmishWorld world, SkirmishWorld.Fighter mob, SkirmishWorld.Fighter target, long tick,
                           long seed) {
        var tuned = kind == Kind.CLASSIC_TUNED;
        var distance = world.distance(mob, target);
        for (var index = 0; index < DuelSkill.RULES.size(); index++) {
            var rule = DuelSkill.RULES.get(index);
            var period = tuned ? 10 : rule.timerTicks();
            var phase = Math.floorMod(mix(seed, index), period);
            if ((tick + phase) % period != 0 || !mob.ready(rule.skill(), tick)) {
                continue;
            }
            if (holds(mob, rule, distance, tuned)) {
                return rule.skill();
            }
        }
        return null;
    }

    private static boolean holds(SkirmishWorld.Fighter mob, DuelSkill.Rule rule, double distance, boolean tuned) {
        var within = tuned && rule.withinBlocks() > 0 ? 5 : rule.withinBlocks();
        var heal = tuned && rule.skill() == DuelSkill.HEAL ? 0.30 : rule.healthBelow();
        return (within == 0 || distance < within)
                && (rule.notWithinBlocks() == 0 || distance >= rule.notWithinBlocks())
                && (heal >= 1.0 || mob.healthRatio() < heal);
    }

    private static DuelSkill best(SkirmishWorld world, SkirmishWorld.Fighter mob, SkirmishWorld.Fighter target,
                                  long tick) {
        DuelSkill chosen = null;
        var distance = world.distance(mob, target);
        for (var skill : DuelSkill.values()) {
            if (!mob.ready(skill, tick) || skill == DuelSkill.HEAL || skill == DuelSkill.GUARD) {
                continue;
            }
            if (skill.withinBlocks > 0 && distance >= skill.withinBlocks) {
                continue;
            }
            if (chosen == null || skill.damage > chosen.damage) {
                chosen = skill;
            }
        }
        return chosen;
    }

    private static SkirmishWorld.Fighter woundedAlly(SkirmishWorld world, SkirmishWorld.Fighter healer) {
        for (var body : world.bodies) {
            if (!body.player && body != healer && body.health > 0 && body.healthRatio() < 0.5) {
                return body;
            }
        }
        return null;
    }

    static SkirmishWorld.Fighter healTarget(SkirmishWorld world, SkirmishWorld.Fighter healer) {
        var ally = woundedAlly(world, healer);
        return ally == null ? healer : ally;
    }

    private static long mix(long seed, int index) {
        var mixed = seed * 0x9E3779B97F4A7C15L ^ (index + 1L) * 0xBF58476D1CE4E5B9L;
        mixed ^= mixed >>> 30;
        return mixed & Long.MAX_VALUE;
    }

    /** Whether this shown tell gives the player time to answer. */
    boolean avoids(long seed, long tick, int caster, int shown) {
        if (blockChance <= 0 || shown < reactionTicks) {
            return false;
        }
        var mixed = seed * 0x9E3779B97F4A7C15L ^ tick * 0xBF58476D1CE4E5B9L ^ (caster + 1L);
        mixed ^= mixed >>> 30;
        mixed *= 0xBF58476D1CE4E5B9L;
        mixed ^= mixed >>> 27;
        var unit = (mixed >>> 11) * 0x1.0p-53;
        return unit < blockChance;
    }
}
