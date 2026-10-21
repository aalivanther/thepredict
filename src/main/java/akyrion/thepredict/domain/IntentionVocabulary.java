package akyrion.thepredict.domain;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;

/**
 * The closed, versioned vocabulary of the {@code Intention} line of a MythicMobs skill (M19.1, rule I14): reading a
 * line and writing the canonical line of a purpose. Pure and stateless; safe on any thread.
 *
 * <p>A line is a YAML list or a comma-separated text. Each primitive is trimmed and put in upper case, and {@code -} and
 * inner spaces count as {@code _}, so the line is read without regard to case. At most {@value #MAX_TOKENS} raw
 * primitives and {@value #MAX_CHARS} characters; a valid line has at most {@value #MAX_VALID_TOKENS} (3 roles, 1
 * delivery, 1 damage tier, 4 {@code WHEN_}, 4 {@code RISK_} and {@code IGNORES_SHIELD}), and the margin lets unknown or
 * repeated primitives through as warnings without invalidating a good line. Order matters only for the roles: the
 * first role is the primary one.
 *
 * <p>The rules are append-only: {@link #VERSION} grows when a primitive is added, a primitive never changes its meaning
 * or family, and a retired one stays in {@link #ALIASES} pointing at its substitute. So any line valid in v1 stays
 * valid in later versions, and no version is written in the line.
 */
public final class IntentionVocabulary {
    /** Version of the vocabulary. */
    public static final int VERSION = 2;
    /** Most raw primitives of a line, unknown and repeated ones included. */
    public static final int MAX_TOKENS = 16;
    /** Most primitives a valid line can hold. */
    public static final int MAX_VALID_TOKENS = 15;
    /** Most characters of a line, measured as its entries joined by commas. */
    public static final int MAX_CHARS = 400;
    /** Most roles of a line, as {@link SkillPurpose#MAX_ROLES}. */
    public static final int MAX_ROLES = SkillPurpose.MAX_ROLES;
    /** Retired name to its substitute; empty in v1. */
    public static final Map<String, IntentionToken> ALIASES = Map.of();

    private static final Map<String, IntentionToken> BY_NAME = byName();

    private IntentionVocabulary() {
    }

    /**
     * Reads a line with the aliases of this version. Never throws for any content.
     *
     * @param raw entries of the line, a YAML list or the comma-separated text already split; blank or {@code null}
     *        entries are skipped
     * @return {@code Absent} for an empty line, {@code Invalid} with its errors, or {@code Parsed} with its warnings
     */
    public static IntentionParse parse(List<String> raw) {
        return parse(raw, ALIASES);
    }

    /**
     * Reads a line with the given aliases, so a test can inject a retired name. Never throws for any content.
     *
     * @param raw entries of the line; blank or {@code null} entries are skipped
     * @param aliases retired name, already normalised, to its substitute
     * @return {@code Absent}, {@code Invalid} or {@code Parsed}
     */
    public static IntentionParse parse(List<String> raw, Map<String, IntentionToken> aliases) {
        Objects.requireNonNull(raw, "raw");
        Objects.requireNonNull(aliases, "aliases");
        var entries = new ArrayList<String>(raw.size());
        var chars = new StringJoiner(",");
        for (var entry : raw) {
            if (entry != null) {
                chars.add(entry);
                if (!entry.isBlank()) {
                    entries.add(entry);
                }
            }
        }
        if (entries.isEmpty()) {
            return new IntentionParse.Absent();
        }
        if (entries.size() > MAX_TOKENS || chars.length() > MAX_CHARS) {
            return new IntentionParse.Invalid(List.of(IntentionIssue.line(IntentionIssueKind.TOO_LONG)), List.of());
        }
        var warnings = new ArrayList<IntentionIssue>();
        var tokens = read(entries, aliases, warnings);
        var errors = errors(tokens);
        return errors.isEmpty() ? new IntentionParse.Parsed(draft(tokens), warnings, answerOf(tokens))
                : new IntentionParse.Invalid(errors, warnings);
    }

    /**
     * Normalises one primitive as the line is read: trimmed, in upper case, with {@code -} and runs of inner spaces as
     * {@code _}.
     *
     * @param raw one entry of the line
     * @return the normalised name
     */
    public static String normalise(String raw) {
        return Objects.requireNonNull(raw, "raw").trim().toUpperCase(Locale.ROOT).replace('-', '_')
                .replaceAll("\\s+", "_");
    }

    /**
     * What a line can express of a purpose: everything except reach, radius, wind-up, cooldown, measure and origin; a
     * damage tier {@code UNRATED} or {@code NONE} gives no {@code DAMAGE_} primitive.
     *
     * @param purpose a purpose
     * @return its draft
     */
    public static IntentionDraft draftOf(SkillPurpose purpose) {
        Objects.requireNonNull(purpose, "purpose");
        var tier = purpose.damage();
        var declared = tier == DamageTier.NONE || tier == DamageTier.UNRATED ? Optional.<DamageTier>empty()
                : Optional.of(tier);
        return new IntentionDraft(purpose.primary(), purpose.roles(), purpose.delivery(), declared, purpose.bestWhen(),
                purpose.risks(), purpose.ignoresShield());
    }

    /**
     * The primitives of a draft in canonical order: roles (primary first, then in the order of {@link SkillRole}),
     * delivery, damage, {@code WHEN_} and {@code RISK_} in the order of their enums, and {@code IGNORES_SHIELD}.
     *
     * @param draft a draft
     * @return the primitives
     */
    public static List<IntentionToken> render(IntentionDraft draft) {
        return List.copyOf(tokens(draft));
    }

    /**
     * The primitives of a draft followed by the {@code ANSWER_} primitive of its tell (vocabulary v2, I14), so a line
     * suggested for a choice whose tell declares an answer keeps it.
     *
     * @param draft a draft
     * @param answer the answer the tell declares
     * @return the primitives of {@link #render(IntentionDraft)} and then the answer
     */
    public static List<IntentionToken> render(IntentionDraft draft, TellAnswer answer) {
        var tokens = tokens(draft);
        tokens.add(IntentionToken.of(Objects.requireNonNull(answer, "answer")));
        return List.copyOf(tokens);
    }

    private static ArrayList<IntentionToken> tokens(IntentionDraft draft) {
        Objects.requireNonNull(draft, "draft");
        var tokens = new ArrayList<IntentionToken>(MAX_VALID_TOKENS);
        draft.orderedRoles().forEach(role -> tokens.add(IntentionToken.of(role)));
        tokens.add(IntentionToken.of(draft.delivery()));
        draft.declaredDamage().ifPresent(tier -> tokens.add(IntentionToken.of(tier)));
        for (var condition : UseCondition.values()) {
            if (draft.bestWhen().contains(condition)) {
                tokens.add(IntentionToken.of(condition));
            }
        }
        for (var risk : SkillRisk.values()) {
            if (draft.risks().contains(risk)) {
                tokens.add(IntentionToken.of(risk));
            }
        }
        if (draft.ignoresShield()) {
            tokens.add(IntentionToken.IGNORES_SHIELD);
        }
        return tokens;
    }

    /**
     * The text of a line, as the author writes it.
     *
     * @param tokens primitives in order
     * @return their names separated by {@code ", "}
     */
    public static String text(List<IntentionToken> tokens) {
        var joiner = new StringJoiner(", ");
        Objects.requireNonNull(tokens, "tokens").forEach(token -> joiner.add(token.name()));
        return joiner.toString();
    }

    /**
     * Facts of a purpose that the canonical line cannot carry: reach, radius and a wind-up above zero. Cooldown,
     * measure and origin stay off the line as well, and MythicMobs or the profile supply them; they are not repeated
     * here. An untyped choice has no purpose, so it has no line.
     *
     * @param purpose a purpose
     * @return the notes, in that order; empty when the line already says everything the vocabulary can say
     */
    public static List<String> omitted(SkillPurpose purpose) {
        Objects.requireNonNull(purpose, "purpose");
        var notes = new ArrayList<String>(3);
        if (purpose.reachBlocks().isPresent()) {
            var reach = number(purpose.reachBlocks().getAsDouble());
            notes.add("Reach " + reach + " has no Intention token: add targetwithin{d=" + reach
                    + "} to the skill's Conditions or keep Purpose in the profile");
        }
        if (purpose.radiusBlocks().isPresent()) {
            var radius = number(purpose.radiusBlocks().getAsDouble());
            notes.add("Radius " + radius + " has no Intention token: add a radius targeter {r=" + radius
                    + "} to the skill or keep Purpose in the profile");
        }
        if (purpose.windUpTicks() > 0) {
            notes.add("WindUp " + purpose.windUpTicks() + " has no Intention token: the delay of the skill supplies"
                    + " it, or keep Purpose in the profile");
        }
        return List.copyOf(notes);
    }

    /** One known primitive of the line with its position. */
    private record Read(IntentionToken token, int index) {
    }

    private static List<Read> read(List<String> entries, Map<String, IntentionToken> aliases,
                                   List<IntentionIssue> warnings) {
        var tokens = new ArrayList<Read>(entries.size());
        var seen = EnumSet.noneOf(IntentionToken.class);
        for (var index = 0; index < entries.size(); index++) {
            var name = normalise(entries.get(index));
            var token = known(name, index, aliases, warnings);
            if (token.isEmpty()) {
                continue;
            }
            if (!seen.add(token.get())) {
                warnings.add(new IntentionIssue(IntentionIssueKind.REPEATED_TOKEN, index, name));
                continue;
            }
            tokens.add(new Read(token.get(), index));
        }
        return tokens;
    }

    private static Optional<IntentionToken> known(String name, int index, Map<String, IntentionToken> aliases,
                                                  List<IntentionIssue> warnings) {
        var token = BY_NAME.get(name);
        if (token != null) {
            return Optional.of(token);
        }
        var substitute = aliases.get(name);
        if (substitute != null) {
            warnings.add(new IntentionIssue(IntentionIssueKind.ALIAS_USED, index, name));
            return Optional.of(substitute);
        }
        var future = name.startsWith(IntentionToken.WHEN_PREFIX) || name.startsWith(IntentionToken.RISK_PREFIX)
                || name.startsWith(IntentionToken.DAMAGE_PREFIX) || name.startsWith(IntentionToken.ANSWER_PREFIX);
        warnings.add(new IntentionIssue(future ? IntentionIssueKind.FUTURE_TOKEN : IntentionIssueKind.UNKNOWN_TOKEN,
                index, name));
        return Optional.empty();
    }

    private static List<IntentionIssue> errors(List<Read> tokens) {
        var byFamily = new EnumMap<IntentionToken.Family, List<Read>>(IntentionToken.Family.class);
        for (var read : tokens) {
            byFamily.computeIfAbsent(read.token().family(), family -> new ArrayList<>()).add(read);
        }
        var errors = new ArrayList<IntentionIssue>();
        var roles = byFamily.getOrDefault(IntentionToken.Family.ROLE, List.of());
        var deliveries = byFamily.getOrDefault(IntentionToken.Family.DELIVERY, List.of());
        if (roles.isEmpty()) {
            errors.add(IntentionIssue.line(IntentionIssueKind.NO_ROLE));
        }
        beyond(roles, MAX_ROLES, IntentionIssueKind.TOO_MANY_ROLES, errors);
        if (deliveries.isEmpty()) {
            errors.add(IntentionIssue.line(IntentionIssueKind.NO_DELIVERY));
        }
        beyond(deliveries, 1, IntentionIssueKind.MORE_THAN_ONE_DELIVERY, errors);
        beyond(byFamily.getOrDefault(IntentionToken.Family.DAMAGE, List.of()), 1,
                IntentionIssueKind.MORE_THAN_ONE_DAMAGE, errors);
        beyond(byFamily.getOrDefault(IntentionToken.Family.ANSWER, List.of()), 1,
                IntentionIssueKind.MORE_THAN_ONE_ANSWER, errors);
        beyond(byFamily.getOrDefault(IntentionToken.Family.BEST_WHEN, List.of()), SkillPurpose.MAX_BEST_WHEN,
                IntentionIssueKind.TOO_MANY_BEST_WHEN, errors);
        var risks = byFamily.getOrDefault(IntentionToken.Family.RISK, List.of());
        beyond(risks, SkillPurpose.MAX_RISKS, IntentionIssueKind.TOO_MANY_RISKS, errors);
        shieldContradiction(byFamily, risks, errors);
        return errors;
    }

    private static void shieldContradiction(Map<IntentionToken.Family, List<Read>> byFamily, List<Read> risks,
                                            List<IntentionIssue> errors) {
        if (byFamily.containsKey(IntentionToken.Family.SHIELD)
                && risks.stream().anyMatch(read -> read.token() == IntentionToken.RISK_BLOCKED_BY_SHIELD)) {
            var shield = byFamily.get(IntentionToken.Family.SHIELD).getFirst();
            errors.add(new IntentionIssue(IntentionIssueKind.SHIELD_CONTRADICTION, shield.index(),
                    shield.token().name()));
        }
    }

    private static void beyond(List<Read> reads, int maximum, IntentionIssueKind kind, List<IntentionIssue> errors) {
        if (reads.size() > maximum) {
            var first = reads.get(maximum);
            errors.add(new IntentionIssue(kind, first.index(), first.token().name()));
        }
    }

    private static IntentionDraft draft(List<Read> tokens) {
        SkillRole primary = null;
        var roles = EnumSet.noneOf(SkillRole.class);
        Delivery delivery = null;
        var damage = Optional.<DamageTier>empty();
        var bestWhen = EnumSet.noneOf(UseCondition.class);
        var risks = EnumSet.noneOf(SkillRisk.class);
        var ignoresShield = false;
        for (var read : tokens) {
            var token = read.token();
            switch (token.family()) {
                case ROLE -> roles.add(token.role().orElseThrow());
                case DELIVERY -> delivery = token.delivery().orElseThrow();
                case DAMAGE -> damage = token.damage();
                case BEST_WHEN -> bestWhen.add(token.condition().orElseThrow());
                case RISK -> risks.add(token.risk().orElseThrow());
                case SHIELD -> ignoresShield = true;
                case ANSWER -> {
                }
            }
            if (primary == null && token.family() == IntentionToken.Family.ROLE) {
                primary = token.role().orElseThrow();
            }
        }
        return new IntentionDraft(primary, roles, delivery, damage, bestWhen, risks, ignoresShield);
    }

    private static Optional<TellAnswer> answerOf(List<Read> tokens) {
        for (var read : tokens) {
            if (read.token().family() == IntentionToken.Family.ANSWER) {
                return read.token().answer();
            }
        }
        return Optional.empty();
    }

    private static String number(double blocks) {
        var whole = Math.rint(blocks);
        return whole == blocks ? Long.toString((long) whole) : Double.toString(blocks);
    }

    private static Map<String, IntentionToken> byName() {
        var names = new HashMap<String, IntentionToken>();
        for (var token : IntentionToken.values()) {
            names.put(token.name(), token);
        }
        return Map.copyOf(names);
    }

    /**
     * The primitives of every family, for the published table and its tests.
     *
     * @param family a family
     * @return its primitives in table order
     */
    public static Set<IntentionToken> family(IntentionToken.Family family) {
        Objects.requireNonNull(family, "family");
        var tokens = EnumSet.noneOf(IntentionToken.class);
        for (var token : IntentionToken.values()) {
            if (token.family() == family) {
                tokens.add(token);
            }
        }
        return tokens;
    }
}
