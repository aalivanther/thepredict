package akyrion.thepredict.adapter.server.binding;

import akyrion.thepredict.domain.DisplacementKind;
import akyrion.thepredict.domain.DisplacementOrigin;
import io.lumine.mythic.core.config.MythicLineConfigImpl;

import java.util.Locale;

/**
 * Reads how a metaskill moves a body (M24.6). Each mechanic is one configuration line. {@code pk}, {@code pkb} and
 * {@code preventknockback} cancel the knockback of {@code damage} and the assumed knockback of an unclassified hit.
 * Throw, leap, explosion and pull stay. The strongest target class then wins: strong push, knockback, pull, none.
 * A damaging skill whose branches were not all read is knockback with origin {@link DisplacementOrigin#ASSUMED}.
 */
public final class DisplacementReader {
    private static final String[] BRAKE = {"preventknockback", "pkb", "pk"};

    /**
     * One displacement reading.
     *
     * @param target how the target moves
     * @param self how the caster moves
     * @param origin whether the reading was complete, declared or assumed
     */
    public record Reading(DisplacementKind target, DisplacementKind self, DisplacementOrigin origin) {
    }

    private DisplacementReader() {
    }

    /**
     * Classifies configuration lines, one mechanic per line.
     *
     * @param damaging whether some mechanic deals damage
     * @param allRead whether every branch was read
     * @param lines lower-case configuration lines separated by newlines
     * @return the reading
     */
    public static Reading read(boolean damaging, boolean allRead, String lines) {
        if (!damaging) {
            return new Reading(DisplacementKind.NONE, DisplacementKind.NONE, DisplacementOrigin.READ);
        }
        if (!allRead) {
            return new Reading(DisplacementKind.KNOCKBACK, DisplacementKind.NONE, DisplacementOrigin.ASSUMED);
        }
        return classify(lines == null ? "" : lines);
    }

    private static Reading classify(String lines) {
        var push = false;
        var pull = false;
        var explicitKnock = false;
        var damageKnock = false;
        var brake = false;
        var self = DisplacementKind.NONE;
        for (var raw : lines.split("\n", -1)) {
            var line = raw.trim().toLowerCase(Locale.ROOT);
            if (line.isEmpty()) {
                continue;
            }
            var type = typeOf(line);
            brake |= brakes(type, line);
            push |= strong(type);
            pull |= type.equals("pull") || type.equals("forcepull");
            explicitKnock |= type.equals("knockback");
            damageKnock |= type.equals("damage") && !stopped(line);
            self = selfOf(type, self);
        }
        return result(push, explicitKnock, damageKnock, pull, brake, self);
    }

    private static Reading result(boolean push, boolean explicitKnock, boolean damageKnock, boolean pull,
                                  boolean brake, DisplacementKind self) {
        var target = DisplacementKind.NONE;
        if (push) {
            target = DisplacementKind.STRONG_PUSH;
        } else if (explicitKnock || damageKnock && !brake) {
            target = DisplacementKind.KNOCKBACK;
        } else if (pull) {
            target = DisplacementKind.PULL;
        }
        if (target != DisplacementKind.NONE) {
            return new Reading(target, self, DisplacementOrigin.READ);
        }
        if (self != DisplacementKind.NONE) {
            return new Reading(DisplacementKind.NONE, self, DisplacementOrigin.READ);
        }
        if (brake || damageKnock) {
            return new Reading(DisplacementKind.NONE, DisplacementKind.NONE, DisplacementOrigin.READ);
        }
        return new Reading(DisplacementKind.KNOCKBACK, DisplacementKind.NONE, DisplacementOrigin.ASSUMED);
    }

    private static String typeOf(String line) {
        var body = NativeSkillReader.withoutTargeter(line).trim();
        var end = body.length();
        for (var index = 0; index < body.length(); index++) {
            var symbol = body.charAt(index);
            if (symbol == '{' || symbol == ' ' || symbol == '@') {
                end = index;
                break;
            }
        }
        return body.substring(0, end);
    }

    private static boolean strong(String type) {
        return type.equals("throw") || type.equals("leap") || type.equals("explosion") || type.equals("velocity")
                || type.equals("directionalvelocity");
    }

    private static boolean brakes(String type, String line) {
        return type.equals("preventknockback") || type.equals("pkb") || stopped(line);
    }

    private static boolean stopped(String line) {
        if (line.indexOf('{') < 0) {
            return false;
        }
        return MythicLineConfigImpl.of(NativeSkillReader.withoutTargeter(line)).getBoolean(BRAKE, false);
    }

    private static DisplacementKind selfOf(String type, DisplacementKind current) {
        if (current == DisplacementKind.SELF_BACKWARD || type.equals("disengage")) {
            return DisplacementKind.SELF_BACKWARD;
        }
        if (type.contains("forward")) {
            return DisplacementKind.SELF_FORWARD;
        }
        return current;
    }
}
