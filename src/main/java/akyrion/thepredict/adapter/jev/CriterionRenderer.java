package akyrion.thepredict.adapter.jev;

import akyrion.thepredict.adapter.jev.OptionTextDedup.Discriminator;
import akyrion.thepredict.domain.Delivery;
import akyrion.thepredict.domain.MovementSettings;
import akyrion.thepredict.domain.OptionContext.FitReason;
import akyrion.thepredict.domain.OptionContext.OptionFit;
import akyrion.thepredict.domain.QuestionDetail;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.SkillPurpose;
import akyrion.thepredict.domain.Tactic;
import akyrion.thepredict.domain.TacticalProfile;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Writes the criterion of one option at a {@link QuestionDetail} (M16.1, M16.2): the exact text the body carries, so
 * the deduplication compares what Jev reads. Only fixed phrases and the author description: no number and no choice
 * name reaches the model (rules I4, I5). Pure and thread-safe.
 *
 * <p>{@code COMPACT} is one text, {@code <movement>; <skill>} and at most one {@code poor fit now} phrase, of at most
 * {@value #COMPACT_CHARS} characters; over it the wind-up, the damage and the {@code poor fit now} phrase are dropped in
 * that order. An option in a collision carries discriminators ({@code note:}, {@code reach:}, {@code best when}) and may
 * grow to {@value #COLLISION_CHARS}; a discriminator is never dropped, and the note excerpt shrinks down to
 * {@value #MIN_EXCERPT_CHARS} characters if needed. {@code STANDARD} and {@code FULL} are one JSON object with the same
 * possible keys in every option, in a fixed order, the empty ones left out: {@code movement}, {@code skill},
 * {@code best_when}, {@code risks}, {@code fits_now} and {@code poor_fit_now} (only {@code FULL}) and {@code note}; they
 * already say everything a discriminator would add, so they ignore discriminators.
 */
final class CriterionRenderer {
    /** Longest {@code COMPACT} criterion without a collision. */
    static final int COMPACT_CHARS = 120;
    /** Longest {@code COMPACT} criterion that carries a discriminator. */
    static final int COLLISION_CHARS = 160;
    /** Characters of the note excerpt, cut at a space. */
    static final int EXCERPT_CHARS = 40;
    /** Shortest note excerpt when an option in a collision must shrink. */
    static final int MIN_EXCERPT_CHARS = 12;

    private CriterionRenderer() {
    }

    /**
     * The final text of one criterion.
     *
     * @param text in {@code COMPACT} the criterion itself; in {@code STANDARD} and {@code FULL} the compact JSON of its
     *             object
     */
    record RenderedCriterion(String text) {
        RenderedCriterion {
            Objects.requireNonNull(text, "text");
        }

        /** The value the body carries: the object of {@code STANDARD} and {@code FULL}, else the text itself. */
        JsonElement json() {
            return text.startsWith("{") ? JsonParser.parseString(text) : new JsonPrimitive(text);
        }
    }

    /**
     * Renders one option.
     *
     * @param tactic option
     * @param profile profile that declares it
     * @param detail detail of the question
     * @param fit what favours and advises against it now, from {@code OptionContext}
     * @param discriminators discriminators added by the deduplication; only {@code COMPACT} writes them
     * @return the final text
     */
    static RenderedCriterion render(Tactic tactic, TacticalProfile profile, QuestionDetail detail, OptionFit fit,
                                    Set<Discriminator> discriminators) {
        var choice = tactic.choice().map(profile.choices()::get);
        return new RenderedCriterion(switch (detail) {
            case COMPACT -> compact(tactic, choice, profile.movement(), fit, discriminators);
            case STANDARD -> structured(tactic, choice, profile.movement(), Optional.empty()).toString();
            case FULL -> structured(tactic, choice, profile.movement(), Optional.of(fit)).toString();
        });
    }

    /**
     * The {@code movement} and {@code skill} of an option, as the {@code tactic} object of a fit check (M16.5).
     *
     * @param tactic option
     * @param profile profile that declares it
     * @return the object, without what fits now
     */
    static JsonObject movementAndSkill(Tactic tactic, TacticalProfile profile) {
        var json = new JsonObject();
        json.addProperty("movement", SemanticPhrases.movement(tactic.movement()));
        tactic.choice().map(profile.choices()::get).flatMap(SkillChoice::purpose)
                .ifPresent(purpose -> json.addProperty("skill", skill(purpose, profile.movement())));
        return json;
    }

    /**
     * The skill phrase of {@code STANDARD} and {@code FULL}: delivery and roles, reach, area, wind-up (only written by
     * the author), damage and cooldown, without best-when conditions nor risks (M15.7).
     */
    static String skill(SkillPurpose purpose, MovementSettings movement) {
        var roles = new ArrayList<String>(purpose.roles().size());
        purpose.orderedRoles().forEach(role -> roles.add(SemanticPhrases.role(role)));
        var pieces = new ArrayList<String>(6);
        pieces.add(SemanticPhrases.delivery(purpose.delivery()) + " " + String.join(" and ", roles));
        purpose.reachBlocks().ifPresent(reach -> pieces.add(SemanticPhrases.reach(reach, movement)));
        purpose.radiusBlocks().ifPresent(radius -> pieces.add(SemanticPhrases.area(radius)));
        if (purpose.authored()) {
            pieces.add(SemanticPhrases.windUp(purpose.windUpTicks()));
        }
        pieces.add(SemanticPhrases.damage(purpose.damage()));
        pieces.add(SemanticPhrases.cooldown(purpose.cooldown()));
        pieces.removeIf(String::isEmpty);
        return String.join(", ", pieces);
    }

    private static JsonObject structured(Tactic tactic, Optional<SkillChoice> choice, MovementSettings movement,
                                         Optional<OptionFit> fit) {
        var json = new JsonObject();
        json.addProperty("movement", SemanticPhrases.movement(tactic.movement()));
        choice.flatMap(SkillChoice::purpose).ifPresent(purpose -> {
            json.addProperty("skill", skill(purpose, movement));
            var bestWhen = new ArrayList<String>();
            ordered(purpose.bestWhen()).forEach(condition -> bestWhen.add(SemanticPhrases.useCondition(condition)));
            put(json, "best_when", bestWhen);
            var risks = new ArrayList<String>();
            ordered(purpose.risks()).forEach(risk -> risks.add(SemanticPhrases.risk(risk)));
            if (purpose.ignoresShield()) {
                risks.add(SemanticPhrases.ignoresShield());
            }
            put(json, "risks", risks);
        });
        fit.ifPresent(found -> {
            put(json, "fits_now", phrases(found.fits()));
            put(json, "poor_fit_now", phrases(found.poorFits()));
        });
        tactic.description().ifPresent(note -> json.addProperty("note", note));
        return json;
    }

    private static String compact(Tactic tactic, Optional<SkillChoice> choice, MovementSettings movement, OptionFit fit,
                                  Set<Discriminator> discriminators) {
        var purpose = choice.flatMap(SkillChoice::purpose);
        var suffixes = suffixes(tactic, purpose, movement, discriminators);
        var limit = suffixes.isEmpty() ? COMPACT_CHARS : COLLISION_CHARS;
        var poor = fit.poorFits().stream().map(SemanticPhrases::poorFitShort).filter(phrase -> !phrase.isEmpty())
                .findFirst().orElse("");
        var windUp = purpose.filter(SkillPurpose::authored).map(found -> SemanticPhrases.windUpShort(found.windUpTicks()))
                .orElse("");
        var damage = purpose.map(found -> SemanticPhrases.damageShort(found.damage())).orElse("");
        var text = compose(tactic, purpose, damage, windUp, poor, suffixes);
        if (text.length() > limit) {
            text = compose(tactic, purpose, damage, "", poor, suffixes);
        }
        if (text.length() > limit) {
            text = compose(tactic, purpose, "", "", poor, suffixes);
        }
        if (text.length() > limit) {
            text = compose(tactic, purpose, "", "", "", suffixes);
        }
        for (var excerpt = EXCERPT_CHARS - 1; text.length() > limit && excerpt >= MIN_EXCERPT_CHARS; excerpt--) {
            text = compose(tactic, purpose, "", "", "", suffixes(tactic, purpose, movement, discriminators, excerpt));
        }
        return text;
    }

    private static String compose(Tactic tactic, Optional<SkillPurpose> purpose, String damage, String windUp,
                                  String poor, List<String> suffixes) {
        var parts = new ArrayList<String>(6);
        parts.add(SemanticPhrases.movementShort(tactic.movement()));
        purpose.ifPresent(found -> {
            var pieces = new ArrayList<String>(3);
            pieces.add(skillShort(found));
            pieces.add(damage);
            pieces.add(windUp);
            pieces.removeIf(String::isEmpty);
            parts.add(String.join(", ", pieces));
        });
        if (!poor.isEmpty()) {
            parts.add("poor fit now: " + poor);
        }
        parts.addAll(suffixes);
        return String.join("; ", parts);
    }

    /** The delivery and the main role when it is not {@code DAMAGE}; a skill on the caster says only its role. */
    private static String skillShort(SkillPurpose purpose) {
        var role = SemanticPhrases.roleShort(purpose.primary());
        if (role.isEmpty()) {
            return SemanticPhrases.deliveryShort(purpose.delivery());
        }
        return purpose.delivery() == Delivery.SELF ? role
                : SemanticPhrases.deliveryShort(purpose.delivery()) + " " + role;
    }

    private static List<String> suffixes(Tactic tactic, Optional<SkillPurpose> purpose, MovementSettings movement,
                                         Set<Discriminator> discriminators) {
        return suffixes(tactic, purpose, movement, discriminators, EXCERPT_CHARS);
    }

    private static List<String> suffixes(Tactic tactic, Optional<SkillPurpose> purpose, MovementSettings movement,
                                         Set<Discriminator> discriminators, int excerptChars) {
        var suffixes = new ArrayList<String>(3);
        for (var discriminator : ordered(discriminators)) {
            switch (discriminator) {
                case NOTE_EXCERPT -> tactic.description()
                        .ifPresent(note -> suffixes.add("note: " + excerpt(note, excerptChars)));
                case REACH_BAND -> purpose.flatMap(found -> {
                    var reach = found.effectiveReachBlocks();
                    return reach.isPresent() ? Optional.of(reach.getAsDouble()) : Optional.<Double>empty();
                }).ifPresent(reach -> suffixes.add("reach: " + SemanticPhrases.reachShort(reach, movement)));
                case BEST_WHEN -> purpose.flatMap(found -> ordered(found.bestWhen()).stream().findFirst())
                        .ifPresent(condition -> suffixes.add("best when " + SemanticPhrases.useConditionShort(condition)));
            }
        }
        return suffixes;
    }

    /**
     * The first characters of an author description: the whole text when it fits, else cut at the last word boundary
     * within {@code maxChars} (or at {@code maxChars} when the first word is longer).
     */
    static String excerpt(String description, int maxChars) {
        var text = description.strip();
        if (text.length() <= maxChars) {
            return text;
        }
        var cut = text.substring(0, maxChars);
        if (text.charAt(maxChars) == ' ') {
            return cut.strip();
        }
        var space = cut.lastIndexOf(' ');
        return (space > 0 ? cut.substring(0, space) : cut).strip();
    }

    private static List<String> phrases(List<FitReason> reasons) {
        var phrases = new ArrayList<String>(reasons.size());
        reasons.forEach(reason -> phrases.add(SemanticPhrases.fitReason(reason)));
        return phrases;
    }

    private static void put(JsonObject json, String key, List<String> values) {
        if (!values.isEmpty()) {
            var array = new JsonArray(values.size());
            values.forEach(array::add);
            json.add(key, array);
        }
    }

    private static <E extends Enum<E>> List<E> ordered(Set<E> values) {
        return values.isEmpty() ? List.of() : List.copyOf(EnumSet.copyOf(values));
    }
}
