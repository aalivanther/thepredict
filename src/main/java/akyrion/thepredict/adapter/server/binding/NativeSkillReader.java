package akyrion.thepredict.adapter.server.binding;

import akyrion.thepredict.domain.CueChannels;
import akyrion.thepredict.domain.HitShape;
import akyrion.thepredict.domain.NativeSkillFacts;
import akyrion.thepredict.domain.SkillPurpose;
import io.lumine.mythic.core.config.MythicLineConfigImpl;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Reads what MythicMobs already enforces for a skill into {@link NativeSkillFacts} (M19.3), once when the profile
 * binds, never in a tick. Only the top level is read: {@code skill{s=…}}, {@code randomskill}, projectiles and auras
 * are not followed; a skill with such a line counts as able to deal damage and its displacement is the assumed
 * knockback of A12 ({@code DisplacementOrigin.ASSUMED}), with a note for the author. Line options are parsed with
 * the public parser of MythicMobs ({@code MythicLineConfigImpl.of}); no reflection (rule C11). Stateless; server
 * thread only, like the binder.
 *
 * <p>The wind-up adds, in order, the ticks of each {@code delay} mechanic and the {@code delay=} option of each cosmetic
 * mechanic ({@code effect:*}, {@code particles}, {@code sound}, {@code message}), up to the first mechanic that is not
 * cosmetic, which adds its own {@code delay=} option; it is capped at {@value SkillPurpose#MAX_WIND_UP_TICKS} ticks with
 * a note, and is empty when a delay is a placeholder or a range. {@code repeat} does not change it. The reach of
 * {@code targetwithin} is read only when its distance is a number; the radius of a radius targeter defaults to
 * {@value #DEFAULT_RADIUS_BLOCKS} and the range of {@code @EntitiesInCone} to {@value #DEFAULT_CONE_RANGE_BLOCKS}, as in
 * MythicMobs.
 */
final class NativeSkillReader {
    /** Default {@code r} of the MythicMobs radius targeters. */
    static final double DEFAULT_RADIUS_BLOCKS = 5.0;
    /** Default {@code range} of {@code @EntitiesInCone}. */
    static final double DEFAULT_CONE_RANGE_BLOCKS = 16.0;
    /** Type names of cosmetic mechanics besides the {@code effect:} family; compared in lower case. */
    static final Set<String> COSMETIC_TYPES = Set.of("particles", "sound", "message", "delay");

    private static final String[] DISTANCE = {"d", "distance"};
    private static final String[] RADIUS = {"r", "radius"};
    private static final String[] CONE_RANGE = {"range", "r"};
    private static final String[] IGNORE_SHIELD = {"ignoreshield", "is", "ignore_shield"};
    private static final String[] IGNORE_INVULNERABILITY = {"ignoreinvulnerability", "ignoreinvulnerable",
            "ignore_invulnerability", "ii"};

    private NativeSkillReader() {
    }

    /**
     * The facts of a skill and what the author should know about them.
     *
     * @param facts the facts read, {@link NativeSkillFacts#NONE} when nothing was read
     * @param read whether the skill is a metaskill whose facts were read; only then are they compared with a purpose
     * @param notes one English sentence per datum left empty or capped, naming the skill
     */
    record Reading(NativeSkillFacts facts, boolean read, List<String> notes) {
        /** The reading of a skill that is not a metaskill: nothing read. */
        static final Reading UNREAD = new Reading(NativeSkillFacts.NONE, false, List.of());

        Reading {
            Objects.requireNonNull(facts, "facts");
            notes = List.copyOf(notes);
        }
    }

    /** Reads one metaskill. */
    static Reading read(String skillId, NativeSkillView view) {
        Objects.requireNonNull(skillId, "skillId");
        var notes = new ArrayList<String>();
        var mechanics = view.mechanics();
        var damaging = mechanics.stream().filter(NativeSkillView.Line::damaging).toList();
        var nested = mechanics.stream().anyMatch(NativeSkillView.Line::nested);
        if (nested) {
            notes.add("skill '" + skillId + "': it runs nested skills, projectiles or auras that ThePredict does not"
                    + " follow; it may deal damage and a knockback is assumed");
        }
        var movement = DisplacementReader.read(!damaging.isEmpty() || nested, !nested, lines(mechanics));
        var facts = new NativeSkillFacts(cooldown(view), Math.max(1, view.charges()), mode(view),
                windUp(skillId, view, mechanics, notes), targetWithin(skillId, view, notes),
                firstDistance(mechanics, NativeSkillView.Targeter.RADIUS, RADIUS, DEFAULT_RADIUS_BLOCKS,
                        SkillPurpose.MAX_RADIUS_BLOCKS),
                firstDistance(mechanics, NativeSkillView.Targeter.CONE, CONE_RANGE, DEFAULT_CONE_RANGE_BLOCKS,
                        SkillPurpose.MAX_REACH_BLOCKS),
                damaging.stream().anyMatch(line -> flag(line, IGNORE_SHIELD)),
                !damaging.isEmpty() && damaging.stream().allMatch(line -> flag(line, IGNORE_INVULNERABILITY)),
                lineOfSight(view.conditions()) || lineOfSight(view.targetConditions()), !damaging.isEmpty() || nested,
                recovery(skillId, view, mechanics, notes), cueSignature(mechanics), cueChannels(mechanics),
                hitShape(mechanics), movement.target(), movement.self(), movement.origin());
        facts = facts.withTargetsAllies(targetsAllies(mechanics));
        var speed = projectileSpeed(mechanics);
        if (speed.isPresent()) {
            facts = facts.withProjectileSpeed(speed.getAsDouble());
        }
        return new Reading(facts, true, notes);
    }

    /**
     * The native cooldown in seconds: the static value; without {@code Cooldown} and with {@code CooldownMode: NONE}
     * MythicMobs uses 1 s (R5); without a declared {@code Cooldown} otherwise the value stays empty.
     */
    private static OptionalDouble cooldown(NativeSkillView view) {
        if (!view.cooldownDeclared()) {
            return "NONE".equals(mode(view)) ? OptionalDouble.of(1.0) : OptionalDouble.empty();
        }
        return view.staticCooldownSeconds();
    }

    private static boolean targetsAllies(List<NativeSkillView.Line> mechanics) {
        for (var line : mechanics) {
            var text = line.line().toLowerCase(Locale.ROOT);
            if (text.contains("@parent") || text.contains("@children") || text.contains("@siblings")
                    || text.contains("@owner") || text.contains("@mobsinradius")
                    || text.contains("@mobsnearorigin") || text.contains("@entitiesnearby")) {
                return true;
            }
        }
        return false;
    }

    private static String lines(java.util.List<NativeSkillView.Line> mechanics) {
        var text = new StringBuilder();
        for (var line : mechanics) {
            text.append(line.line().toLowerCase(java.util.Locale.ROOT)).append('\n');
        }
        return text.toString();
    }

    private static String mode(NativeSkillView view) {
        var mode = view.cooldownMode();
        return mode == null || mode.isBlank() ? NativeSkillFacts.SEQUENTIAL : mode.toUpperCase(Locale.ROOT);
    }

    private static OptionalInt windUp(String skillId, NativeSkillView view, List<NativeSkillView.Line> mechanics,
                                      List<String> notes) {
        var total = 0;
        for (var mechanic : mechanics) {
            var type = mechanic.typeName().toLowerCase(Locale.ROOT);
            if (type.isBlank()) {
                notes.add("skill '" + skillId + "': a mechanic type is not recognized; ThePredict does not know its"
                        + " wind-up");
                return OptionalInt.empty();
            }
            var ticks = "delay".equals(type) ? delayMechanicTicks(view, mechanic) : delayOption(view, mechanic);
            if (ticks.isEmpty()) {
                notes.add("skill '" + skillId + "': a delay is a placeholder or a range; ThePredict does not know its"
                        + " wind-up");
                return OptionalInt.empty();
            }
            total = (int) Math.min((long) total + ticks.getAsInt(), Integer.MAX_VALUE);
            if (!cosmetic(type)) {
                break;
            }
        }
        if (total > SkillPurpose.MAX_WIND_UP_TICKS) {
            notes.add("skill '" + skillId + "': its delays add " + total + " ticks; ThePredict caps the wind-up at "
                    + SkillPurpose.MAX_WIND_UP_TICKS);
            total = SkillPurpose.MAX_WIND_UP_TICKS;
        }
        return OptionalInt.of(total);
    }

    /**
     * Delays after the last mechanic that is not cosmetic. Empty when there is none or a delay cannot be read.
     */
    private static OptionalInt recovery(String skillId, NativeSkillView view, List<NativeSkillView.Line> mechanics,
                                        List<String> notes) {
        var last = -1;
        for (var index = 0; index < mechanics.size(); index++) {
            var type = mechanics.get(index).typeName().toLowerCase(Locale.ROOT);
            if (!type.isBlank() && !cosmetic(type)) {
                last = index;
            }
        }
        if (last < 0) {
            return OptionalInt.empty();
        }
        var total = 0;
        var saw = false;
        for (var index = last + 1; index < mechanics.size(); index++) {
            var mechanic = mechanics.get(index);
            var type = mechanic.typeName().toLowerCase(Locale.ROOT);
            OptionalInt ticks;
            if ("delay".equals(type)) {
                ticks = delayMechanicTicks(view, mechanic);
            } else if (MythicLineConfigImpl.of(withoutTargeter(mechanic.line())).getString("delay") != null) {
                ticks = delayOption(view, mechanic);
            } else {
                continue;
            }
            if (ticks.isEmpty()) {
                notes.add("skill '" + skillId + "': a delay is a placeholder or a range; ThePredict does not know its"
                        + " recovery");
                return OptionalInt.empty();
            }
            saw = true;
            total = (int) Math.min((long) total + ticks.getAsInt(), Integer.MAX_VALUE);
        }
        if (!saw) {
            return OptionalInt.empty();
        }
        if (total > SkillPurpose.MAX_WIND_UP_TICKS) {
            notes.add("skill '" + skillId + "': its trailing delays add " + total
                    + " ticks; ThePredict caps the recovery at " + SkillPurpose.MAX_WIND_UP_TICKS);
            total = SkillPurpose.MAX_WIND_UP_TICKS;
        }
        return OptionalInt.of(total);
    }

    /** Stable hash of the cosmetic prefix. {@code 0} when that prefix has no sound or particle. */
    private static int cueSignature(List<NativeSkillView.Line> mechanics) {
        var hash = 1;
        var any = false;
        for (var mechanic : mechanics) {
            var type = mechanic.typeName().toLowerCase(Locale.ROOT);
            if (type.isBlank() || !cosmetic(type)) {
                break;
            }
            if ("delay".equals(type) || "message".equals(type)) {
                continue;
            }
            var key = cueKey(mechanic.line());
            any = true;
            hash = 31 * hash + type.hashCode();
            hash = 31 * hash + key.hashCode();
        }
        return any ? hash : 0;
    }

    private static String cueKey(String line) {
        var config = MythicLineConfigImpl.of(withoutTargeter(line));
        for (var name : new String[] {"particle", "p", "sound", "s"}) {
            var value = config.getString(name);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static int cueChannels(List<NativeSkillView.Line> mechanics) {
        var bits = 0;
        for (var mechanic : mechanics) {
            var type = mechanic.typeName().toLowerCase(Locale.ROOT);
            if (type.isBlank() || !cosmetic(type)) {
                break;
            }
            if (type.equals("sound") || type.equals("effect:sound")) {
                bits |= CueChannels.AUDIO;
            }
            if (type.equals("particles") || type.startsWith("effect:particles") || type.contains("glow")
                    || type.equals("swingarm") || type.equals("playanimation") || type.equals("setpose")
                    || type.equals("state") || type.equals("defaultstate")) {
                bits |= CueChannels.VISUAL;
            }
        }
        return bits;
    }

    /**
     * Declared speed of the first projectile, missile or shoot mechanic. Empty when the line has no finite
     * positive {@code velocity} or {@code v}. The number is stored as blocks per tick.
     */
    private static OptionalDouble projectileSpeed(List<NativeSkillView.Line> mechanics) {
        for (var mechanic : mechanics) {
            var type = mechanic.typeName().toLowerCase(Locale.ROOT);
            if (!(type.contains("projectile") || type.equals("shoot") || type.equals("missile"))) {
                continue;
            }
            var text = MythicLineConfigImpl.of(withoutTargeter(mechanic.line())).getString(new String[] {"velocity", "v"});
            var parsed = capped(number(text), 64.0);
            return parsed.isPresent() && parsed.getAsDouble() > 0.0 ? parsed : OptionalDouble.empty();
        }
        return OptionalDouble.empty();
    }

    /** Shape of the first hit: a damaging mechanic, or a nested one that may hit (a projectile, a sub-skill). */
    private static HitShape hitShape(List<NativeSkillView.Line> mechanics) {
        for (var mechanic : mechanics) {
            if (!mechanic.damaging() && !mechanic.nested()) {
                continue;
            }
            var type = mechanic.typeName().toLowerCase(Locale.ROOT);
            if (type.contains("projectile") || type.equals("shoot") || type.equals("missile")) {
                return HitShape.PROJECTILE;
            }
            if (mechanic.targeter() == NativeSkillView.Targeter.RADIUS) {
                return HitShape.AREA;
            }
            if (mechanic.targeter() == NativeSkillView.Targeter.CONE) {
                return HitShape.CONE;
            }
            var line = mechanic.line().toLowerCase(Locale.ROOT);
            if (line.contains("@target") || line.contains("@trigger")) {
                return HitShape.SINGLE_TARGET;
            }
            return HitShape.UNKNOWN;
        }
        return HitShape.UNKNOWN;
    }

    /** Whether a type name is cosmetic: {@code effect:*} or one of {@link #COSMETIC_TYPES}. */
    static boolean cosmetic(String typeName) {
        var type = typeName.toLowerCase(Locale.ROOT);
        return type.startsWith("effect:") || COSMETIC_TYPES.contains(type);
    }

    /** Ticks of a {@code delay} mechanic: its second argument ({@code delay 5}) or {@code ticks=}. */
    private static OptionalInt delayMechanicTicks(NativeSkillView view, NativeSkillView.Line mechanic) {
        var line = withoutTargeter(mechanic.line()).trim();
        var parts = line.split("\\s+");
        if (parts.length >= 2 && !parts[0].contains("{")) {
            return view.ticks(parts[1]);
        }
        var ticks = MythicLineConfigImpl.of(line).getString(new String[] {"ticks", "t"});
        return ticks == null ? OptionalInt.of(0) : view.ticks(ticks);
    }

    /** The {@code delay=} option every mechanic accepts; 0 without it. */
    private static OptionalInt delayOption(NativeSkillView view, NativeSkillView.Line mechanic) {
        var delay = MythicLineConfigImpl.of(withoutTargeter(mechanic.line())).getString("delay");
        return delay == null ? OptionalInt.of(0) : view.ticks(delay);
    }

    private static OptionalDouble targetWithin(String skillId, NativeSkillView view, List<String> notes) {
        for (var condition : view.conditions()) {
            if (!condition.required() || !"targetwithin".equalsIgnoreCase(condition.typeName())) {
                continue;
            }
            var text = MythicLineConfigImpl.of(condition.line()).getString(DISTANCE);
            var blocks = number(text);
            if (blocks.isEmpty()) {
                notes.add("skill '" + skillId + "': targetwithin{d} is a placeholder; ThePredict does not check its"
                        + " reach");
            }
            return capped(blocks, SkillPurpose.MAX_REACH_BLOCKS);
        }
        return OptionalDouble.empty();
    }

    private static OptionalDouble firstDistance(List<NativeSkillView.Line> mechanics, NativeSkillView.Targeter kind,
                                                String[] keys, double fallback, double maximum) {
        for (var mechanic : mechanics) {
            if (cosmetic(mechanic.typeName()) || mechanic.targeter() != kind) {
                continue;
            }
            var targeter = targeterText(mechanic.line());
            var text = targeter.isEmpty() ? null : MythicLineConfigImpl.of(targeter).getString(keys);
            return capped(text == null ? OptionalDouble.of(fallback) : number(text), maximum);
        }
        return OptionalDouble.empty();
    }

    private static boolean flag(NativeSkillView.Line mechanic, String[] keys) {
        return MythicLineConfigImpl.of(withoutTargeter(mechanic.line())).getBoolean(keys, false);
    }

    private static boolean lineOfSight(List<NativeSkillView.Line> conditions) {
        return conditions.stream().anyMatch(line -> "lineofsight".equalsIgnoreCase(line.typeName()));
    }

    /** The mechanic part of a line, before its targeter ({@code damage{a=6} @target} gives {@code damage{a=6}}). */
    static String withoutTargeter(String line) {
        var at = targeterStart(line);
        return at < 0 ? line : line.substring(0, at);
    }

    /** The targeter of a line without its {@code @} ({@code PlayersInRadius{r=3}}), empty when it has none. */
    static String targeterText(String line) {
        var at = targeterStart(line);
        if (at < 0) {
            return "";
        }
        var rest = line.substring(at + 1).trim();
        var space = rest.indexOf(' ');
        var brace = rest.indexOf('{');
        return space >= 0 && (brace < 0 || space < brace) ? rest.substring(0, space) : closed(rest);
    }

    private static String closed(String rest) {
        var depth = 0;
        for (var index = 0; index < rest.length(); index++) {
            var character = rest.charAt(index);
            depth += character == '{' ? 1 : character == '}' ? -1 : 0;
            if (character == '}' && depth == 0) {
                return rest.substring(0, index + 1);
            }
        }
        return rest;
    }

    private static int targeterStart(String line) {
        var depth = 0;
        for (var index = 0; index < line.length(); index++) {
            var character = line.charAt(index);
            depth += character == '{' ? 1 : character == '}' ? -1 : 0;
            if (character == '@' && depth == 0) {
                return index;
            }
        }
        return -1;
    }

    private static OptionalDouble number(String text) {
        if (text == null) {
            return OptionalDouble.empty();
        }
        try {
            var value = Double.parseDouble(text.trim());
            return Double.isFinite(value) ? OptionalDouble.of(value) : OptionalDouble.empty();
        } catch (NumberFormatException exception) {
            return OptionalDouble.empty();
        }
    }

    private static OptionalDouble capped(OptionalDouble blocks, double maximum) {
        if (blocks.isEmpty() || blocks.getAsDouble() <= 0.0) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(Math.min(blocks.getAsDouble(), maximum));
    }
}
