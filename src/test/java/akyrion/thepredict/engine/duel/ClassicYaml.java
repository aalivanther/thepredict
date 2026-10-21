package akyrion.thepredict.engine.duel;

import java.util.ArrayList;
import java.util.List;

/**
 * The MythicMobs files of a {@link ClassicConfig}: a mob with its {@code AIGoalSelectors} and one rule skill per cast,
 * in the style of {@code ThePredictDuel.yml} of the duel pack (each rule starts with {@code predictorcontrol false}, so a
 * ThePredict mob can use the same file as its base AI). The kit's own skills ({@code DuelStrike}…) are not written:
 * they are the shared skill file of the scenario. The aura of a rule that waits for its own window is one aura per
 * waiting skill, put on the target by every damaging cast with the duration that covers the impact and the 10 ticks
 * of invulnerability it opens, minus the delay of the waiting skill; the simulator keeps the longest window, which on
 * a server needs the aura not to be shortened by a later cast (PLAUSIBLE, to check in game). Pure.
 */
final class ClassicYaml {
    private ClassicYaml() {
    }

    /**
     * The goal lines, priority first, as MythicMobs reads them.
     *
     * @param config the classic
     * @return the lines
     */
    static List<String> goalLines(ClassicConfig config) {
        var goals = config.goals();
        var lines = new ArrayList<String>();
        lines.add("0 clear");
        if (goals.fleeBelowHealth() > 0) {
            lines.add("1 fleeConditional{distance=" + ClassicConfig.number(goals.fleeDistance()) + ";speed="
                    + ClassicConfig.number(goals.fleeSpeed()) + ";mc=[ - healthpercent{p=<"
                    + ClassicConfig.percent(goals.fleeBelowHealth()) + "} true ]}");
        }
        var strike = config.strike();
        if (goals.band() && strike.isPresent()) {
            var cooling = "mc=[ - skillOnCooldown{s=" + strike.get().id + "} true ]";
            lines.add("2 fleeConditional{distance=" + ClassicConfig.number(goals.bandInner()) + ";speed="
                    + ClassicConfig.number(goals.bandSpeed()) + ";" + cooling + "}");
            lines.add("3 moveWithinConditional{range=" + ClassicConfig.number(goals.bandOuter()) + ";speed="
                    + ClassicConfig.number(goals.bandSpeed()) + ";" + cooling + "}");
            lines.add("4 meleeAttackConditional{speed=" + ClassicConfig.number(goals.meleeSpeed()) + ";mc=[ - "
                    + "skillOnCooldown{s=" + strike.get().id + "} false ]}");
        } else {
            lines.add("4 meleeAttack{speed=" + ClassicConfig.number(goals.meleeSpeed()) + "}");
        }
        return lines;
    }

    /**
     * Both files of one classic.
     *
     * @param config the classic
     * @return the mob, then its rule skills
     */
    static String write(ClassicConfig config) {
        var mob = mobName(config);
        var out = new StringBuilder();
        out.append("# ThePredict bench v2: classic '").append(config.id()).append("' of kit ")
                .append(config.kit().id()).append(" (").append(config.kit().label()).append(").\n");
        out.append("# Mobs/").append(mob).append(".yml; the skills of the kit stay in their own file.\n");
        out.append(mob).append(":\n  Type: ZOMBIE\n  Display: 'Classic ").append(config.id()).append("'\n");
        out.append("  Health: 60\n  Damage: ").append(ClassicConfig.number(config.kit().vanillaDamage())).append('\n');
        out.append("  Options:\n    FollowRange: 32\n    PreventSunburn: true\n    Despawn: false\n");
        out.append("  Modules:\n    ThreatTable: false\n  AIGoalSelectors:\n");
        for (var line : goalLines(config)) {
            out.append("  - ").append(line).append('\n');
        }
        out.append("  Skills:\n");
        for (var rule : config.rules()) {
            out.append("  - skill{s=").append(ruleName(mob, rule)).append("} @self ")
                    .append(rule.trigger() == ClassicConfig.Trigger.TIMER ? "~onTimer:" + rule.timerTicks()
                            : "~onDamaged").append('\n');
        }
        out.append("# Skills/").append(mob).append("Rules.yml\n");
        for (var rule : config.rules()) {
            ruleSkill(out, config, mob, rule);
        }
        return out.toString();
    }

    private static void ruleSkill(StringBuilder out, ClassicConfig config, String mob, ClassicConfig.Rule rule) {
        out.append(ruleName(mob, rule)).append(":\n  Conditions:\n  - predictorcontrol false\n");
        if (rule.withinBlocks() > 0) {
            out.append("  - targetwithin{d=").append(ClassicConfig.number(rule.withinBlocks())).append("} true\n");
        }
        if (rule.notWithinBlocks() > 0) {
            out.append("  - targetwithin{d=").append(ClassicConfig.number(rule.notWithinBlocks())).append("} false\n");
        }
        if (rule.healthBelow() < 1) {
            out.append("  - healthpercent{p=<").append(ClassicConfig.percent(rule.healthBelow())).append("} true\n");
        }
        if (rule.healthAbove() > 0) {
            out.append("  - healthpercent{p=>").append(ClassicConfig.percent(rule.healthAbove())).append("} true\n");
        }
        if (rule.awaitWindow() && rule.skill().damage > 0) {
            out.append("  TargetConditions:\n  - hasaura{auraName=").append(windowName(mob, rule.skill()))
                    .append("} false\n");
        }
        out.append("  Skills:\n  - skill{s=").append(rule.skill().id).append("} ")
                .append(rule.skill().damage > 0 ? "@target" : "@self").append('\n');
        if (rule.skill().damage > 0) {
            auras(out, config, mob, rule);
        }
    }

    /** The auras a damaging cast puts on its target, one per rule that waits for its own window. */
    private static void auras(StringBuilder out, ClassicConfig config, String mob, ClassicConfig.Rule rule) {
        for (var other : config.rules()) {
            if (other.awaitWindow() && other.skill().damage > 0) {
                var duration = Math.max(1, rule.skill().delayTicks + DuelWorld.INVULNERABLE_TICKS
                        - other.skill().delayTicks);
                out.append("  - aura{auraName=").append(windowName(mob, other.skill())).append(";duration=")
                        .append(duration).append("} @target\n");
            }
        }
    }

    /** The mob name: letters, digits and underscores only. */
    static String mobName(ClassicConfig config) {
        return ("Bench_" + config.kit().id() + "_" + config.id()).replaceAll("[^A-Za-z0-9_]", "_");
    }

    private static String ruleName(String mob, ClassicConfig.Rule rule) {
        return mob + "_Rule" + rule.skill().id;
    }

    private static String windowName(String mob, DuelSkill skill) {
        return mob + "_Window" + skill.id;
    }
}
