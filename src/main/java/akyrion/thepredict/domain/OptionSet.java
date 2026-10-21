package akyrion.thepredict.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pure rule of M16.1 step 1: removes tactics identical in everything and caps the options of one question, never
 * removing the tactic of the current intent ({@code keep}), so the hysteresis of M10.5 still compares against it.
 * Stateless, reads no clock, safe on any thread.
 *
 * <p>Two tactics are identical in everything when they have the same movement, the same description and either no
 * choice or typed choices whose purposes are {@link SkillPurpose#semanticallyEqual semantically equal}; an untyped
 * choice never merges with another option (M19.2), because nothing tells what it does. Of each group of
 * identical tactics the first in the order received survives, unless another member is {@code keep}.
 */
public final class OptionSet {
    /** Default of {@code decision.maxOptionsPerQuestion}: the only place of this default (C13). */
    public static final int DEFAULT_MAX_OPTIONS = 8;
    /** Smallest accepted {@code decision.maxOptionsPerQuestion}. */
    public static final int MIN_MAX_OPTIONS = 2;
    /** Largest accepted {@code decision.maxOptionsPerQuestion}. */
    public static final int MAX_MAX_OPTIONS = 32;

    private OptionSet() {
    }

    /**
     * Removes the tactics identical in everything to an earlier one, without a tactic to keep.
     *
     * @param ordered options by descending priority and then by ID
     * @param profile profile that declares them
     * @return the distinct options in the same order
     */
    public static List<TacticId> distinct(List<TacticId> ordered, TacticalProfile profile) {
        return distinct(ordered, profile, null, false);
    }

    /**
     * Removes the tactics identical in everything to an earlier one; the survivor of a group that holds {@code keep}
     * is {@code keep}.
     *
     * @param ordered options by descending priority and then by ID
     * @param profile profile that declares them
     * @param keep tactic of the current intent
     * @return the distinct options in the same order
     */
    public static List<TacticId> distinct(List<TacticId> ordered, TacticalProfile profile, TacticId keep) {
        return distinct(ordered, profile, Objects.requireNonNull(keep, "keep"), true);
    }

    /**
     * Caps the options without a tactic to keep.
     *
     * @param distinct distinct options in order
     * @param maxOptions most options, {@code >= 2}
     * @return the first {@code maxOptions} options
     */
    public static List<TacticId> capped(List<TacticId> distinct, int maxOptions) {
        Objects.requireNonNull(distinct, "distinct");
        DomainChecks.requireRange(maxOptions, MIN_MAX_OPTIONS, Integer.MAX_VALUE, "maxOptions");
        return List.copyOf(distinct.subList(0, Math.min(distinct.size(), maxOptions)));
    }

    /**
     * Caps the options, always keeping {@code keep} when it is one of them: when the cut would drop it, it replaces the
     * last option kept and stays after the others, in its original relative order.
     *
     * @param distinct distinct options in order
     * @param maxOptions most options, {@code >= 2}
     * @param keep tactic of the current intent
     * @return at most {@code maxOptions} options
     */
    public static List<TacticId> capped(List<TacticId> distinct, int maxOptions, TacticId keep) {
        Objects.requireNonNull(keep, "keep");
        var capped = capped(distinct, maxOptions);
        if (capped.contains(keep) || !distinct.contains(keep)) {
            return capped;
        }
        var kept = new ArrayList<>(capped.subList(0, capped.size() - 1));
        kept.add(keep);
        return List.copyOf(kept);
    }

    private static List<TacticId> distinct(List<TacticId> ordered, TacticalProfile profile, TacticId keep,
                                           boolean keeping) {
        Objects.requireNonNull(ordered, "ordered");
        Objects.requireNonNull(profile, "profile");
        var survivors = new ArrayList<TacticId>(ordered.size());
        for (var candidate : ordered) {
            var twin = -1;
            for (var index = 0; index < survivors.size() && twin < 0; index++) {
                if (identical(profile, survivors.get(index), candidate)) {
                    twin = index;
                }
            }
            if (twin < 0) {
                survivors.add(candidate);
            } else if (keeping && candidate.equals(keep)) {
                survivors.set(twin, candidate);
            }
        }
        return List.copyOf(survivors);
    }

    /**
     * Tells whether two declared tactics are identical in everything: same movement, same description and either no
     * choice or typed choices with semantically equal purposes; an untyped choice is never identical to another.
     *
     * @param profile profile that declares both
     * @param first a tactic
     * @param second another tactic
     * @return whether Jev could not tell them apart in any detail
     * @throws IllegalArgumentException if a tactic is not declared
     */
    public static boolean identical(TacticalProfile profile, TacticId first, TacticId second) {
        Objects.requireNonNull(profile, "profile");
        var a = profile.tactic(first).orElseThrow(() -> new IllegalArgumentException("undeclared tactic " + first));
        var b = profile.tactic(second).orElseThrow(() -> new IllegalArgumentException("undeclared tactic " + second));
        if (a.movement() != b.movement() || !a.description().equals(b.description())) {
            return false;
        }
        if (a.choice().isEmpty() || b.choice().isEmpty()) {
            return a.choice().isEmpty() && b.choice().isEmpty();
        }
        var purposeA = profile.choices().get(a.choice().orElseThrow()).purpose();
        var purposeB = profile.choices().get(b.choice().orElseThrow()).purpose();
        return purposeA.isPresent() && purposeB.isPresent() && purposeA.get().semanticallyEqual(purposeB.get());
    }
}
