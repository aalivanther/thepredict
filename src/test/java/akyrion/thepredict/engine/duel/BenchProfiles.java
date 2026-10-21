package akyrion.thepredict.engine.duel;

import akyrion.thepredict.config.ConfigProblem;
import akyrion.thepredict.config.ProfileLoader;
import akyrion.thepredict.domain.TacticalProfile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The ThePredict profiles of the bench (§7.4 of the simulator audit): written from a kit with the same choices,
 * purposes, tactics, contexts and movement as the duel pack, and loaded with the real {@link ProfileLoader}, so a kit
 * reaches the engine as an author's file would. For {@link Kit#K1} the text is the pack's profile
 * ({@code BenchProfilesTest} compares the loaded profiles). Two timings: {@link Timing#PACK}, the pack as it is
 * ({@code TickLayer: casts}, {@code Cadence: standard}), and {@link Timing#DEFAULTS}, the same file without a
 * {@code Timing} section, which is what a server gets by default.
 */
final class BenchProfiles {
    /** The timing of a bench profile. */
    enum Timing {
        /** {@code Timing: TickLayer: casts, Cadence: standard}, as the duel pack. */
        PACK("perfil del paquete"),
        /** No {@code Timing} section: the defaults of delivery. */
        DEFAULTS("defaults de entrega"),
        /**
         * The defaults of delivery with {@code retreat_and_heal} on {@code APPROACH}: a variant of the profile, not of
         * the product, for a sensitivity cell only.
         */
        HEAL_APPROACH("defaults de entrega con retreat_and_heal en APPROACH"),
        /**
         * The defaults of delivery with {@code Approach: { Speed: 1.2 }}, the most the classic may chase at, as an
         * expert author would write it (fase de efectividad E-4): a variant of the profile for a sensitivity only; the
         * default of delivery stays 1.15.
         */
        SPEED_PARITY("defaults de entrega con Approach.Speed 1.2");

        final String label;

        Timing(String label) {
            this.label = label;
        }
    }

    /** The chase speed of the sensitivity {@link Timing#SPEED_PARITY}: the fastest of the legal space. */
    static final double PARITY_SPEED = 1.2;

    private BenchProfiles() {
    }

    /**
     * The profile file of a kit.
     *
     * @param kit the kit
     * @param timing the timing
     * @return the YAML text of {@code DuelRed} and {@code DuelBlue}
     */
    static String yaml(Kit kit, Timing timing) {
        var out = new StringBuilder("schemaVersion: 2\n");
        side(out, kit, timing, "duel_red", "DuelRed", "duel_blue");
        side(out, kit, timing, "duel_blue", "DuelBlue", "duel_red");
        return out.toString();
    }

    private static void side(StringBuilder out, Kit kit, Timing timing, String id, String mythicId, String rival) {
        out.append(id).append(":\n  MythicID: ").append(mythicId).append("\n  TypeOfMythicID: MOB\n  Type: NORMAL\n")
                .append("  Objective: Defeat the rival duelist and survive\n  Participants:\n")
                .append("    PlayerGameModes: []\n    Entities:\n      Allow: ['faction:").append(rival)
                .append("']\n  Choices:\n");
        for (var skill : kit.skills()) {
            choice(out, skill);
        }
        if (kit.skills().isEmpty()) {
            out.append("    punch:\n      Kind: VANILLA_MELEE\n");
        }
        out.append("  Tactics:\n");
        for (var line : tactics(kit)) {
            var tactic = timing == Timing.HEAL_APPROACH ? line.replace("Movement: RETREAT, Choice: heal",
                    "Movement: APPROACH, Choice: heal") : line;
            out.append("    ").append(tactic).append('\n');
        }
        contexts(out, kit);
        out.append("  Movement:\n    Band: { Inner: 5, Outer: 9 }\n    RangedReach: ")
                .append(ClassicConfig.number(rangedReach(kit))).append("\n    Retreat: { Distance: 8, Speed: 1.2 }\n")
                .append("    Arena: { Radius: 18 }\n");
        if (timing == Timing.SPEED_PARITY) {
            out.append("    Approach: { Speed: ").append(ClassicConfig.number(PARITY_SPEED)).append(" }\n");
        }
        if (timing == Timing.PACK) {
            out.append("  Timing:\n    TickLayer: casts\n    Cadence: standard\n");
        }
    }

    private static void choice(StringBuilder out, DuelSkill skill) {
        var role = Kit.role(skill);
        out.append("    ").append(choiceName(role)).append(":\n      MythicID: ").append(skill.id)
                .append("\n      TypeOfMythicID: SKILL\n");
        if (role == Kit.Role.GUARD || role == Kit.Role.HEAL) {
            out.append("      RequiresTarget: false\n");
        }
        out.append("      Purpose:\n");
        switch (role) {
            case STRIKE -> purpose(out, "[DAMAGE]", "MELEE", skill.withinBlocks - 0.5, skill, "[TARGET_IN_MELEE]");
            case BOLT -> purpose(out, "[DAMAGE]", "HITSCAN", skill.withinBlocks, skill, "[TARGET_AT_RANGE]");
            case LEAP -> purpose(out, "[GAP_CLOSE, DAMAGE]", "HITSCAN", skill.withinBlocks, skill,
                    "[TARGET_FLEEING, TARGET_AT_RANGE]");
            case GUARD -> self(out, "[DEFENSE]", "[TARGET_IN_MELEE]");
            case HEAL -> self(out, "[SUSTAIN]", "[SELF_LOW_HEALTH]");
        }
        if (role == Kit.Role.BOLT) {
            out.append("        Risks: [NEEDS_LINE_OF_SIGHT]\n");
        }
    }

    private static void purpose(StringBuilder out, String roles, String delivery, double reach, DuelSkill skill,
                                String bestWhen) {
        out.append("        Roles: ").append(roles).append("\n        Delivery: ").append(delivery)
                .append("\n        Reach: ").append(ClassicConfig.number(reach)).append("\n        WindUp: ")
                .append(skill.delayTicks).append("\n        Damage: ").append(tier(skill.damage))
                .append("\n        BestWhen: ").append(bestWhen).append('\n');
    }

    private static void self(StringBuilder out, String roles, String bestWhen) {
        out.append("        Roles: ").append(roles).append("\n        Delivery: SELF\n        Damage: NONE\n")
                .append("        BestWhen: ").append(bestWhen).append('\n');
    }

    /** The damage tier of an amount: up to 3 low, up to 6 medium, above high, as the pack rates 2, 3 and 5. */
    static String tier(double damage) {
        return damage <= 3 ? "LOW" : damage <= 6 ? "MEDIUM" : "HIGH";
    }

    static String choiceName(Kit.Role role) {
        return role.name().toLowerCase(Locale.ROOT);
    }

    private static List<String> tactics(Kit kit) {
        var lines = new ArrayList<String>();
        for (var role : List.of(Kit.Role.STRIKE, Kit.Role.BOLT, Kit.Role.LEAP, Kit.Role.GUARD, Kit.Role.HEAL)) {
            if (kit.skill(role).isPresent()) {
                lines.add(tactic(role));
            }
        }
        if (kit.skills().isEmpty()) {
            lines.add("press_and_punch: { Movement: APPROACH, Choice: punch, Priority: 70 }");
        }
        lines.add("approach: { Movement: APPROACH, Priority: 10 }");
        return lines;
    }

    private static String tactic(Kit.Role role) {
        return switch (role) {
            case STRIKE -> "press_and_strike: { Movement: APPROACH, Choice: strike, Priority: 70 }";
            case BOLT -> "keep_distance_and_bolt: { Movement: KEEP_DISTANCE, Choice: bolt, Priority: 60 }";
            case LEAP -> "leap_in: { Movement: APPROACH, Choice: leap, Priority: 55 }";
            case GUARD -> "guard_up: { Movement: HOLD, Choice: guard, Priority: 50 }";
            case HEAL -> "retreat_and_heal: { Movement: RETREAT, Choice: heal, Priority: 80 }";
        };
    }

    private static String tacticName(Kit.Role role) {
        var line = tactic(role);
        return line.substring(0, line.indexOf(':'));
    }

    private static void contexts(StringBuilder out, Kit kit) {
        if (kit.skills().isEmpty()) {
            return;
        }
        var critical = new ArrayList<String>();
        var healthy = new ArrayList<String>();
        for (var role : List.of(Kit.Role.STRIKE, Kit.Role.BOLT, Kit.Role.LEAP, Kit.Role.GUARD, Kit.Role.HEAL)) {
            if (kit.skill(role).isEmpty()) {
                continue;
            }
            if (role == Kit.Role.HEAL || role == Kit.Role.BOLT) {
                critical.add(tacticName(role));
            }
            if (role != Kit.Role.HEAL) {
                healthy.add(tacticName(role));
            }
        }
        healthy.add("approach");
        critical.sort((left, right) -> left.equals("retreat_and_heal") ? -1 : right.equals("retreat_and_heal") ? 1 : 0);
        out.append("  Contexts:\n");
        if (!critical.isEmpty()) {
            out.append("    low_health_phase: { Priority: 90, Tactics: [").append(String.join(", ", critical))
                    .append("] }\n");
        }
        out.append("    healthy:\n      Priority: 80\n      Conditions: ['healthpercent{p=>50%} true']\n")
                .append("      Tactics: [").append(String.join(", ", healthy)).append("]\n");
    }

    private static double rangedReach(Kit kit) {
        return Math.max(14, kit.skill(Kit.Role.BOLT).map(bolt -> bolt.withinBlocks).orElse(0.0));
    }

    /**
     * Writes the profile of a kit into a folder and loads it with the real loader.
     *
     * @param kit the kit
     * @param timing the timing
     * @param folder an empty folder of its own
     * @return {@code DuelRed} and {@code DuelBlue}
     * @throws IllegalStateException when the loader reports an error or a warning
     */
    static Map<String, TacticalProfile> load(Kit kit, Timing timing, Path folder) {
        try {
            Files.createDirectories(folder);
            Files.writeString(folder.resolve("ThePredictBench.yml"), yaml(kit, timing), StandardCharsets.UTF_8);
            var result = new ProfileLoader().load(folder);
            var problems = result.problems().stream().map(ConfigProblem::toString).toList();
            if (!problems.isEmpty() || result.profiles().size() != 2) {
                throw new IllegalStateException("the bench profile of " + kit.id() + " does not load: " + problems);
            }
            return Map.copyOf(result.profiles());
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /**
     * SHA-256 of the profile text: the fingerprint an adversarial classic is searched against (§7.3).
     *
     * @param kit the kit
     * @param timing the timing
     * @return the fingerprint
     */
    static String sha256(Kit kit, Timing timing) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(yaml(kit, timing).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
