package akyrion.thepredict.engine.duel;

import akyrion.thepredict.domain.MovementSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

/**
 * The legal space of classic configurations of one kit (§7.3 of the simulator audit), every value a construction of
 * MythicMobs 5.13.1 verified with {@code javap}. The dimensions of §7.3 are kept and widened where the native language
 * allows more, so the search can only get stronger: the timer adds {@code ~onTimer:1} ({@code Clock.Main: 1}); the
 * guard adds a timer at any distance and a floor of health ({@code healthpercent{p=>30%}}, the critical band of
 * ThePredict); and every damaging rule may wait for the window of its own hits with an aura ({@code hasAura}), the
 * hardest classic of the integrator's probe; the flee to heal starts below the heal's own threshold, 50 % for the
 * duel pack. A dimension whose skill the kit lacks collapses, and values that the
 * skill's own conditions make equal collapse into one ({@code healthpercent{p=<70%}} on a heal that needs 50 %).
 * Configurations are numbered in mixed radix, the first dimension fastest; the rules keep the order of {@code DuelBase}:
 * heal, guard, strike, leap, bolt.
 */
final class ClassicSpace {
    static final int[] TIMERS = {1, 2, 5, 10, 20};
    static final double[] RANGED_MINIMUMS = {0, 3, 4};
    static final double BOLT_SHORT_MAXIMUM = 8;
    static final double[] HEALS = {0.3, 0.5, 0.7};
    static final double[] CHASES = {1.0, 1.15, 1.2};
    /** Flee to heal: never, below the heal's own threshold (50 % without a heal), below 35 %. */
    static final double[] FLEES = {0, 0.5, 0.35};
    static final double FLEE_DISTANCE = 8;
    static final double FLEE_SPEED = 1.2;
    static final double BAND_INNER = 5;
    static final double BAND_OUTER = 9;
    static final double BAND_SPEED = 1.15;
    static final double GUARD_REACH = 3;
    static final double GUARD_FLOOR = 0.3;

    /** When the guard rule fires. */
    enum Guard {
        /** On the timer with the target within 3 blocks, as {@code DuelRuleGuard}. */
        MELEE_TIMER,
        /** On the timer at any distance. */
        ANY_TIMER,
        /** On {@code ~onDamaged}. */
        ON_DAMAGED
    }

    /** The health condition of the guard rule. */
    enum GuardHealth {
        NONE(1.0, 0), BELOW_80(0.8, 0), BELOW_60(0.6, 0), ABOVE_30(1.0, GUARD_FLOOR);

        final double below;
        final double above;

        GuardHealth(double below, double above) {
            this.below = below;
            this.above = above;
        }
    }

    private final Kit kit;
    private final int[] radix;
    private final double[] heals;
    private final double[] boltMaxima;
    private final double[] flees;

    ClassicSpace(Kit kit) {
        this.kit = kit;
        this.heals = kit.skill(Kit.Role.HEAL).map(ClassicSpace::heals).orElse(new double[] {1.0});
        this.boltMaxima = kit.skill(Kit.Role.BOLT).map(bolt -> bolt.withinBlocks > BOLT_SHORT_MAXIMUM
                ? new double[] {bolt.withinBlocks, BOLT_SHORT_MAXIMUM} : new double[] {bolt.withinBlocks})
                .orElse(new double[] {0});
        this.flees = new double[] {FLEES[0], kit.skill(Kit.Role.HEAL).map(heal -> Math.min(heal.healthBelow, 0.95))
                .orElse(FLEES[1]), FLEES[2]};
        var bolt = kit.skill(Kit.Role.BOLT).isPresent();
        var guard = kit.skill(Kit.Role.GUARD).isPresent();
        var strike = kit.skill(Kit.Role.STRIKE).isPresent();
        radix = new int[] {TIMERS.length, bolt ? RANGED_MINIMUMS.length : 1, boltMaxima.length,
                kit.skill(Kit.Role.LEAP).isPresent() ? RANGED_MINIMUMS.length : 1, guard ? Guard.values().length : 1,
                guard ? GuardHealth.values().length : 1, heals.length, CHASES.length, FLEES.length, strike ? 2 : 1,
                damaging(kit) ? 2 : 1};
    }

    private static double[] heals(DuelSkill heal) {
        var distinct = new TreeSet<Double>();
        for (var value : HEALS) {
            distinct.add(Math.min(value, heal.healthBelow));
        }
        return distinct.stream().mapToDouble(Double::doubleValue).toArray();
    }

    private static boolean damaging(Kit kit) {
        for (var skill : kit.skills()) {
            if (skill.damage > 0) {
                return true;
            }
        }
        return false;
    }

    /** The kit of this space. */
    Kit kit() {
        return kit;
    }

    /** How many configurations the space holds. */
    int size() {
        var size = 1;
        for (var value : radix) {
            size *= value;
        }
        return size;
    }

    /**
     * One configuration of the space.
     *
     * @param index its number, within {@code [0, size())}
     * @return the configuration, named after its number
     */
    ClassicConfig config(int index) {
        var digit = new int[radix.length];
        var rest = index;
        for (var at = 0; at < radix.length; at++) {
            digit[at] = rest % radix[at];
            rest /= radix[at];
        }
        if (rest != 0 || index < 0) {
            throw new IllegalArgumentException("index out of the space: " + index);
        }
        return build("cfg-" + index, digit);
    }

    /** The digits of a configuration, one per dimension. */
    int[] digits(int index) {
        var digit = new int[radix.length];
        var rest = index;
        for (var at = 0; at < radix.length; at++) {
            digit[at] = rest % radix[at];
            rest /= radix[at];
        }
        return digit;
    }

    /** The number of a configuration from its digits. */
    int index(int[] digit) {
        var index = 0;
        for (var at = radix.length - 1; at >= 0; at--) {
            index = index * radix[at] + digit[at];
        }
        return index;
    }

    /** The size of each dimension. */
    int[] radix() {
        return radix.clone();
    }

    /**
     * The number of a configuration of this space given by its rules and goals, whatever its name.
     *
     * @param config a configuration of the kit
     * @return its number, or empty when a value lies outside the space
     */
    java.util.OptionalInt indexOf(ClassicConfig config) {
        var code = config.code();
        for (var index = 0; index < size(); index++) {
            if (config(index).code().equals(code)) {
                return java.util.OptionalInt.of(index);
            }
        }
        return java.util.OptionalInt.empty();
    }

    private ClassicConfig build(String name, int[] digit) {
        var timer = TIMERS[digit[0]];
        var await = digit[10] == 1;
        var rules = new ArrayList<ClassicConfig.Rule>();
        kit.skill(Kit.Role.HEAL).ifPresent(heal -> rules.add(new ClassicConfig.Rule(heal,
                ClassicConfig.Trigger.TIMER, timer, 0, 0, heals[digit[6]], 0, false)));
        kit.skill(Kit.Role.GUARD).ifPresent(guard -> rules.add(guardRule(guard, timer,
                Guard.values()[digit[4]], GuardHealth.values()[digit[5]])));
        kit.skill(Kit.Role.STRIKE).ifPresent(strike -> rules.add(new ClassicConfig.Rule(strike,
                ClassicConfig.Trigger.TIMER, timer, strike.withinBlocks, 0, 1.0, 0, await)));
        kit.skill(Kit.Role.LEAP).ifPresent(leap -> rules.add(new ClassicConfig.Rule(leap,
                ClassicConfig.Trigger.TIMER, timer, leap.withinBlocks, RANGED_MINIMUMS[digit[3]], 1.0, 0, await)));
        kit.skill(Kit.Role.BOLT).ifPresent(bolt -> rules.add(new ClassicConfig.Rule(bolt,
                ClassicConfig.Trigger.TIMER, timer, boltMaxima[digit[2]], RANGED_MINIMUMS[digit[1]], 1.0, 0, await)));
        var flee = flees[digit[8]];
        var band = digit[9] == 1;
        var goals = new ClassicConfig.Goals(CHASES[digit[7]], flee, flee > 0 ? FLEE_DISTANCE : 0,
                flee > 0 ? FLEE_SPEED : 0, band ? BAND_INNER : 0, band ? BAND_OUTER : 0, band ? BAND_SPEED : 0);
        return new ClassicConfig(name, kit, rules, goals);
    }

    private static ClassicConfig.Rule guardRule(DuelSkill guard, int timer, Guard trigger, GuardHealth health) {
        return new ClassicConfig.Rule(guard, trigger == Guard.ON_DAMAGED ? ClassicConfig.Trigger.ON_DAMAGED
                : ClassicConfig.Trigger.TIMER, timer, trigger == Guard.MELEE_TIMER ? GUARD_REACH : 0, 0, health.below,
                health.above, false);
    }

    /**
     * The classic with the cast windows and the speeds of ThePredict's profile (SIM-01, SIM-04): every rule on
     * {@code ~onTimer:1} like the per-tick layer, the skills' own windows, the guard at any distance above the critical
     * band, flee to heal at the retreat distance and speed when the kit heals, the band of the profile at the approach
     * speed while the strike cools down when the kit has a bolt, the chase at the approach speed, and the aura that
     * waits for the window of its own hits as {@code ImpactTiming} makes ThePredict wait.
     *
     * @param kit the kit
     * @param movement the movement settings of ThePredict's profile
     * @return the classic {@code legal-equal}
     */
    static ClassicConfig legalEqual(Kit kit, MovementSettings movement) {
        var rules = new ArrayList<ClassicConfig.Rule>();
        kit.skill(Kit.Role.HEAL).ifPresent(heal -> rules.add(new ClassicConfig.Rule(heal,
                ClassicConfig.Trigger.TIMER, 1, 0, 0, heal.healthBelow, 0, false)));
        kit.skill(Kit.Role.GUARD).ifPresent(guard -> rules.add(new ClassicConfig.Rule(guard,
                ClassicConfig.Trigger.TIMER, 1, 0, 0, 1.0, GUARD_FLOOR, false)));
        for (var role : List.of(Kit.Role.STRIKE, Kit.Role.LEAP, Kit.Role.BOLT)) {
            kit.skill(role).ifPresent(skill -> rules.add(new ClassicConfig.Rule(skill, ClassicConfig.Trigger.TIMER,
                    1, skill.withinBlocks, 0, 1.0, 0, true)));
        }
        var heal = kit.skill(Kit.Role.HEAL);
        var flee = heal.map(skill -> skill.healthBelow).orElse(0.0);
        var band = kit.skill(Kit.Role.BOLT).isPresent() && kit.skill(Kit.Role.STRIKE).isPresent();
        var goals = new ClassicConfig.Goals(movement.approachSpeed(), flee >= 1 ? 0 : flee,
                flee > 0 && flee < 1 ? movement.retreatDistanceBlocks() : 0,
                flee > 0 && flee < 1 ? movement.retreatSpeed() : 0, band ? movement.bandInnerBlocks() : 0,
                band ? movement.bandOuterBlocks() : 0, band ? movement.approachSpeed() : 0);
        return new ClassicConfig("legal-equal", kit, rules, goals);
    }

    /**
     * Rebuilds a configuration from its {@link ClassicConfig#code}.
     *
     * @param kit the kit
     * @param name the name it gets
     * @param code the code
     * @return the configuration
     * @throws IllegalArgumentException when the code does not belong to the kit
     */
    static ClassicConfig parse(Kit kit, String name, String code) {
        var parts = code.split("\\|");
        if (!parts[0].equals(kit.id())) {
            throw new IllegalArgumentException("code of " + parts[0] + ", not " + kit.id());
        }
        var rules = new ArrayList<ClassicConfig.Rule>();
        for (var at = 1; at < parts.length - 1; at++) {
            rules.add(rule(kit, parts[at].split(":")));
        }
        var goals = parts[parts.length - 1].split(":");
        return new ClassicConfig(name, kit, rules, new ClassicConfig.Goals(number(goals[1]), number(goals[2]),
                number(goals[3]), number(goals[4]), number(goals[5]), number(goals[6]), number(goals[7])));
    }

    private static ClassicConfig.Rule rule(Kit kit, String[] field) {
        var skill = kit.skill(field[0]).orElseThrow(() -> new IllegalArgumentException("unknown skill " + field[0]));
        var trigger = field[1].charAt(0) == 't' ? ClassicConfig.Trigger.TIMER : ClassicConfig.Trigger.ON_DAMAGED;
        var distance = field[2].split("-");
        var health = field[3].split("-");
        return new ClassicConfig.Rule(skill, trigger, Integer.parseInt(field[1].substring(1)), number(distance[1]),
                number(distance[0]), number(health[1]), number(health[0]), "a".equals(field[4]));
    }

    private static double number(String text) {
        return Double.parseDouble(text);
    }

    /** One line of the space for a report. */
    String describe() {
        return String.format(Locale.ROOT, "%d configuraciones (%s)", size(), java.util.Arrays.toString(radix));
    }
}
