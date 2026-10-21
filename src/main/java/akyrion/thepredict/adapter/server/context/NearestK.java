package akyrion.thepredict.adapter.server.context;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

/**
 * Selects the nearest bounded sample without sorting every candidate. Moved unchanged from {@code adapter.paper}
 * (M07); package-private again since M09 retired the legacy snapshot reader.
 */
final class NearestK {
    private NearestK() {
    }

    /**
     * Keeps a maximum heap of the best candidates, then orders only the retained sample.
     *
     * @param <T> candidate type
     * @param candidates candidates in input order
     * @param limit maximum number kept, at least 1
     * @param isEligible filter applied before measuring
     * @param squaredDistance squared distance of a candidate; non-finite or negative values are skipped
     * @return at most {@code limit} candidates, nearest first, ties in input order
     */
    static <T> List<T> select(Iterable<T> candidates, int limit,
                              Predicate<T> isEligible, ToDoubleFunction<T> squaredDistance) {
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(isEligible, "isEligible");
        Objects.requireNonNull(squaredDistance, "squaredDistance");
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive");
        }
        Comparator<Ranked<T>> nearestFirst = Comparator.comparingDouble(Ranked<T>::distanceSquared)
                .thenComparingLong(Ranked<T>::order);
        var farthestFirst = new PriorityQueue<Ranked<T>>(Math.min(limit, 16), nearestFirst.reversed());
        long order = 0;
        for (var candidate : candidates) {
            var distanceSquared = isEligible.test(candidate) ? squaredDistance.applyAsDouble(candidate) : Double.NaN;
            if (Double.isFinite(distanceSquared) && distanceSquared >= 0) {
                keep(farthestFirst, new Ranked<>(candidate, distanceSquared, order), limit, nearestFirst);
            }
            order++;
        }
        var nearest = new ArrayList<>(farthestFirst);
        nearest.sort(nearestFirst);
        return nearest.stream().map(Ranked::value).toList();
    }

    /** Adds a candidate while the heap has room, or replaces the farthest one when it is nearer. */
    private static <T> void keep(PriorityQueue<Ranked<T>> farthestFirst, Ranked<T> ranked, int limit,
                                 Comparator<Ranked<T>> nearestFirst) {
        if (farthestFirst.size() < limit) {
            farthestFirst.add(ranked);
        } else if (nearestFirst.compare(ranked, farthestFirst.peek()) < 0) {
            farthestFirst.remove();
            farthestFirst.add(ranked);
        }
    }

    /** Stores one candidate's computed squared distance and stable input order. */
    private record Ranked<T>(T value, double distanceSquared, long order) {
    }
}
