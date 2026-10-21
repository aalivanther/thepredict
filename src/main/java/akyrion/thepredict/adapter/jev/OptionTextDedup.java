package akyrion.thepredict.adapter.jev;

import akyrion.thepredict.adapter.jev.CriterionRenderer.RenderedCriterion;
import akyrion.thepredict.domain.TacticId;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * Deduplication of the options of one question by their final text (M16.1 step 2): only whoever writes the text knows
 * whether two options read the same, so it compares the exact output of {@link CriterionRenderer}, after the length
 * cap. Pure and thread-safe.
 *
 * <p>Iterative and bounded: the first rendering, then at most one pass per {@link Discriminator}, so at most
 * {@value #MAX_PASSES} renderings per option. Each pass gives every member of a group of identical texts the next
 * discriminator that changes its text, in the order {@code NOTE_EXCERPT}, {@code REACH_BAND}, {@code BEST_WHEN}; one
 * that does not apply is skipped without spending a pass. Options that still read the same are indistinguishable to
 * Jev: the first by priority, or {@code keep}, is sent and the others are merged into it.
 */
final class OptionTextDedup {
    /** Most renderings of one option: the first and one per discriminator. */
    static final int MAX_PASSES = 1 + Discriminator.values().length;

    private OptionTextDedup() {
    }

    /** What the deduplication may add to a colliding compact criterion, in this order. */
    enum Discriminator {
        /** {@code note: <excerpt>}, the start of the author description. */
        NOTE_EXCERPT,
        /** {@code reach: melee|short|ranged|far}, the band of the reach or of the radius around the caster. */
        REACH_BAND,
        /** {@code best when <condition>}, the first declared {@code BestWhen}. */
        BEST_WHEN
    }

    /**
     * The options that are sent and their texts.
     *
     * @param sent options sent, in the order received
     * @param texts final text of each option sent
     * @param mergedInto the option sent in place of each merged one
     */
    record Result(List<TacticId> sent, Map<TacticId, RenderedCriterion> texts, Map<TacticId, TacticId> mergedInto) {
        Result {
            sent = List.copyOf(sent);
            texts = Map.copyOf(texts);
            mergedInto = Map.copyOf(mergedInto);
        }
    }

    /**
     * Renders and deduplicates the options of one question.
     *
     * @param options options by descending priority
     * @param keep tactic of the current intent, which survives any merge
     * @param render the renderer of one option with a set of discriminators
     * @return the options sent, their texts and the merges
     */
    static Result apply(List<TacticId> options, Optional<TacticId> keep,
                        BiFunction<TacticId, Set<Discriminator>, RenderedCriterion> render) {
        Objects.requireNonNull(keep, "keep");
        var added = new HashMap<TacticId, Set<Discriminator>>();
        var tried = new HashMap<TacticId, Integer>();
        var texts = new LinkedHashMap<TacticId, RenderedCriterion>();
        for (var option : options) {
            added.put(option, EnumSet.noneOf(Discriminator.class));
            tried.put(option, 0);
            texts.put(option, render.apply(option, added.get(option)));
        }
        for (var pass = 1; pass < MAX_PASSES; pass++) {
            var colliding = colliding(texts);
            if (colliding.isEmpty()) {
                break;
            }
            for (var option : colliding) {
                discriminate(option, added, tried, texts, render);
            }
        }
        return merged(options, keep, texts);
    }

    /** Gives an option the next discriminator that changes its text, skipping those that do not apply. */
    private static void discriminate(TacticId option, Map<TacticId, Set<Discriminator>> added,
                                     Map<TacticId, Integer> tried, Map<TacticId, RenderedCriterion> texts,
                                     BiFunction<TacticId, Set<Discriminator>, RenderedCriterion> render) {
        var discriminators = Discriminator.values();
        while (tried.get(option) < discriminators.length) {
            var candidate = EnumSet.noneOf(Discriminator.class);
            candidate.addAll(added.get(option));
            candidate.add(discriminators[tried.get(option)]);
            tried.put(option, tried.get(option) + 1);
            var text = render.apply(option, candidate);
            if (!text.equals(texts.get(option))) {
                added.put(option, candidate);
                texts.put(option, text);
                return;
            }
        }
    }

    private static List<TacticId> colliding(Map<TacticId, RenderedCriterion> texts) {
        var counts = new HashMap<RenderedCriterion, Integer>();
        texts.values().forEach(text -> counts.merge(text, 1, Integer::sum));
        var colliding = new ArrayList<TacticId>();
        texts.forEach((option, text) -> {
            if (counts.get(text) > 1) {
                colliding.add(option);
            }
        });
        return colliding;
    }

    private static Result merged(List<TacticId> options, Optional<TacticId> keep,
                                 Map<TacticId, RenderedCriterion> texts) {
        var survivorOf = new LinkedHashMap<RenderedCriterion, TacticId>();
        for (var option : options) {
            var text = texts.get(option);
            var survivor = survivorOf.get(text);
            if (survivor == null || keep.filter(option::equals).isPresent()) {
                survivorOf.put(text, option);
            }
        }
        var sent = new ArrayList<TacticId>();
        var sentTexts = new LinkedHashMap<TacticId, RenderedCriterion>();
        var mergedInto = new LinkedHashMap<TacticId, TacticId>();
        for (var option : options) {
            var survivor = survivorOf.get(texts.get(option));
            if (survivor.equals(option)) {
                sent.add(option);
                sentTexts.put(option, texts.get(option));
            } else {
                mergedInto.put(option, survivor);
            }
        }
        return new Result(sent, sentTexts, mergedInto);
    }
}
