package akyrion.thepredict.engine.duel;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * A classic MythicMobs configuration of one kit (§7.3 of the simulator audit, SIM-01, SIM-02): the rules
 * {@code DuelRule*} that cast its skills and the native goals that move it. Every name it writes was verified with
 * {@code javap} on {@code MythicMobsPremium-5.13.1-SNAPSHOT.jar}: the triggers {@code ~onTimer:N} (with
 * {@code Clock.Main: 1}) and {@code ~onDamaged} ({@code SkillTriggers.DAMAGED}); the conditions {@code targetWithin},
 * {@code healthpercent}, {@code skillOnCooldown} and {@code hasAura}; the mechanics {@code skill} and {@code aura}
 * ({@code auraName}, {@code duration}); the goals {@code meleeAttack} and {@code meleeAttackConditional}
 * ({@code speed}, {@code mc}), {@code fleeConditional} ({@code distance}, {@code speed}, {@code mc}) and
 * {@code moveWithinConditional} ({@code range}, {@code speed}, {@code mc}); {@code predictorcontrol} is the condition of
 * ThePredict. {@link #toMythicYaml} writes a mob and its rule skills that MythicMobs can load next to the kit's skills.
 *
 * @param id name in the report
 * @param kit the skills it casts
 * @param rules one rule per cast skill, in evaluation order
 * @param goals how it moves
 */
record ClassicConfig(String id, Kit kit, List<Rule> rules, Goals goals) {
    /** What fires a rule. */
    enum Trigger {
        /** {@code ~onTimer:N} on the global clock. */
        TIMER,
        /** {@code ~onDamaged}: a hit that took health or absorption. */
        ON_DAMAGED
    }

    /**
     * One {@code DuelRule*} line with native conditions only.
     *
     * @param skill the skill it casts
     * @param trigger what fires it
     * @param timerTicks the timer, ignored for {@code ON_DAMAGED}
     * @param withinBlocks {@code targetwithin{d=…} true}, 0 for none
     * @param notWithinBlocks {@code targetwithin{d=…} false}, 0 for none
     * @param healthBelow {@code healthpercent{p=<…%} true} as a ratio, 1 for none
     * @param healthAbove {@code healthpercent{p=>…%} true} as a ratio, 0 for none
     * @param awaitWindow whether a damaging cast waits while an aura put by its own earlier hits says its impact would
     *     land inside the target's window of invulnerability ({@code hasAura} … {@code false})
     */
    record Rule(DuelSkill skill, Trigger trigger, int timerTicks, double withinBlocks, double notWithinBlocks,
                double healthBelow, double healthAbove, boolean awaitWindow) {
        /**
         * Validates the rule.
         *
         * @throws IllegalArgumentException when a number is out of its range
         */
        public Rule {
            Objects.requireNonNull(skill, "skill");
            Objects.requireNonNull(trigger, "trigger");
            if (timerTicks < 1 || withinBlocks < 0 || notWithinBlocks < 0 || !(healthBelow > 0 && healthBelow <= 1)
                    || healthAbove < 0 || healthAbove >= healthBelow) {
                throw new IllegalArgumentException("classic rule out of range for " + skill.id);
            }
        }
    }

    /**
     * The native goals of a classic, in priority order: flee to heal, the band while the strike cools down, then the
     * melee attack.
     *
     * @param meleeSpeed {@code speed} of {@code meleeAttack}
     * @param fleeBelowHealth flee to heal below this health ratio, 0 for never
     * @param fleeDistance {@code distance} of that {@code fleeConditional}
     * @param fleeSpeed {@code speed} of that {@code fleeConditional}
     * @param bandInner inner edge of the band, 0 for no band
     * @param bandOuter outer edge of the band
     * @param bandSpeed {@code speed} of the band goals
     */
    record Goals(double meleeSpeed, double fleeBelowHealth, double fleeDistance, double fleeSpeed, double bandInner,
                 double bandOuter, double bandSpeed) {
        /** {@code meleeAttack} at 1, as {@code DuelBase}. */
        static final Goals PACK = new Goals(1.0, 0, 0, 0, 0, 0, 0);

        /**
         * Validates the goals.
         *
         * @throws IllegalArgumentException when a number is out of its range
         */
        public Goals {
            if (!(meleeSpeed > 0) || fleeBelowHealth < 0 || fleeBelowHealth >= 1 || bandInner < 0
                    || bandInner > 0 && !(bandOuter > bandInner && bandSpeed > 0)
                    || fleeBelowHealth > 0 && !(fleeDistance > 0 && fleeSpeed > 0)) {
                throw new IllegalArgumentException("classic goals out of range");
            }
        }

        /** Whether the classic keeps a band while its strike cools down. */
        boolean band() {
            return bandInner > 0;
        }
    }

    /**
     * Validates the configuration.
     *
     * @throws IllegalArgumentException when a rule casts a skill of another kit
     */
    ClassicConfig {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kit, "kit");
        Objects.requireNonNull(goals, "goals");
        rules = List.copyOf(rules);
        for (var rule : rules) {
            if (!kit.skills().contains(rule.skill())) {
                throw new IllegalArgumentException(rule.skill().id + " is not a skill of " + kit.id());
            }
        }
    }

    /** The same configuration under another name. */
    ClassicConfig named(String name) {
        return new ClassicConfig(name, kit, rules, goals);
    }

    /**
     * The rules of {@code DuelBase} for the duel pack, and the same shape for another kit: heal on its own condition,
     * guard within 3 blocks below 60 %, strike every 10 ticks, leap and bolt from 4 blocks, every other timer 20, and
     * {@code meleeAttack} at 1.
     *
     * @param kit the kit
     * @return the pack classic of that kit
     */
    static ClassicConfig pack(Kit kit) {
        var rules = new ArrayList<Rule>();
        if (kit == Kit.K1) {
            for (var rule : DuelSkill.RULES) {
                rules.add(new Rule(rule.skill(), Trigger.TIMER, rule.timerTicks(), rule.withinBlocks(),
                        rule.notWithinBlocks(), rule.healthBelow(), 0, false));
            }
            return new ClassicConfig("pack", kit, rules, Goals.PACK);
        }
        for (var role : List.of(Kit.Role.HEAL, Kit.Role.GUARD, Kit.Role.STRIKE, Kit.Role.LEAP, Kit.Role.BOLT)) {
            kit.skill(role).ifPresent(skill -> rules.add(packRule(role, skill)));
        }
        return new ClassicConfig("pack", kit, rules, Goals.PACK);
    }

    private static Rule packRule(Kit.Role role, DuelSkill skill) {
        return switch (role) {
            case HEAL -> new Rule(skill, Trigger.TIMER, 20, 0, 0, skill.healthBelow, 0, false);
            case GUARD -> new Rule(skill, Trigger.TIMER, 20, 3, 0, 0.6, 0, false);
            case STRIKE -> new Rule(skill, Trigger.TIMER, 10, skill.withinBlocks, 0, 1.0, 0, false);
            case LEAP, BOLT -> new Rule(skill, Trigger.TIMER, 20, skill.withinBlocks, 4, 1.0, 0, false);
        };
    }

    /**
     * A zombie of vanilla: no rules, {@code meleeAttack} at 1.
     *
     * @param kit the kit, whose vanilla hit it deals
     * @return the vanilla classic
     */
    static ClassicConfig vanilla(Kit kit) {
        return new ClassicConfig("vanilla", kit, List.of(), Goals.PACK);
    }

    /**
     * The skill of the melee strike, which the band goals watch with {@code skillOnCooldown}.
     *
     * @return the strike, or empty for a kit without one
     */
    java.util.Optional<DuelSkill> strike() {
        return kit.skill(Kit.Role.STRIKE);
    }

    /** A short stable code of the configuration, enough to rebuild it with {@link ClassicSpace#parse}. */
    String code() {
        var out = new StringBuilder(kit.id());
        for (var rule : rules) {
            out.append(String.format(Locale.ROOT, "|%s:%s%d:%s-%s:%s-%s:%s", rule.skill().id,
                    rule.trigger() == Trigger.TIMER ? "t" : "d", rule.timerTicks(), number(rule.notWithinBlocks()),
                    number(rule.withinBlocks()), number(rule.healthAbove()), number(rule.healthBelow()),
                    rule.awaitWindow() ? "a" : "-"));
        }
        out.append(String.format(Locale.ROOT, "|goals:%s:%s:%s:%s:%s:%s:%s", number(goals.meleeSpeed()),
                number(goals.fleeBelowHealth()), number(goals.fleeDistance()), number(goals.fleeSpeed()),
                number(goals.bandInner()), number(goals.bandOuter()), number(goals.bandSpeed())));
        return out.toString();
    }

    /** One line for a report. */
    String describe() {
        var out = new StringBuilder();
        for (var rule : rules) {
            if (!out.isEmpty()) {
                out.append("; ");
            }
            out.append(rule.skill().id).append(rule.trigger() == Trigger.TIMER ? " ~onTimer:" + rule.timerTicks()
                    : " ~onDamaged");
            if (rule.notWithinBlocks() > 0) {
                out.append(" ≥").append(number(rule.notWithinBlocks()));
            }
            if (rule.withinBlocks() > 0) {
                out.append(" <").append(number(rule.withinBlocks()));
            }
            if (rule.healthBelow() < 1) {
                out.append(" vida<").append(percent(rule.healthBelow()));
            }
            if (rule.healthAbove() > 0) {
                out.append(" vida>").append(percent(rule.healthAbove()));
            }
            if (rule.awaitWindow()) {
                out.append(" aura");
            }
        }
        return out.append("; ").append(ClassicYaml.goalLines(this).stream().skip(1)
                .map(line -> line.substring(line.indexOf(' ') + 1)).reduce((left, right) -> left + ", " + right)
                .orElse("sin metas")).toString();
    }

    /** The MythicMobs files of this classic: one mob and its rule skills. */
    String toMythicYaml() {
        return ClassicYaml.write(this);
    }

    /** SHA-256 of {@link #toMythicYaml()}, the fingerprint a frozen rival is checked against. */
    String sha256() {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(toMythicYaml().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    static String number(double value) {
        return java.math.BigDecimal.valueOf(value).setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros()
                .toPlainString();
    }

    static String percent(double ratio) {
        return number(ratio * 100) + "%";
    }
}
