package akyrion.thepredict.domain.learn;

import java.util.List;
import java.util.Objects;

/**
 * A value artifact in canonical form (M28.9, rules C23 and I21, format {@value #FORMAT}). Names are interned: a cell
 * stores the index of its profile in {@code profiles} and of its tactic in {@code tactics}, both sorted and without
 * repeats, so two names never share a key. The fine context is {@link ContextKey#packed()}, the coarse context is
 * {@link ContextKey#coarseCode()}, and levels 1 and 0 store 0. Level 0 is cross-profile and stores profile index 0.
 * The constructor checks all of this, so a read file and a fresh build obey the same contract.
 *
 * @param day build day: epoch days of the newest row read
 * @param batch batch the build read through
 * @param spec reward spec
 * @param contextVersion context key version, 2
 * @param sources training source tokens, sorted
 * @param halfLifeDays week half-life of the build
 * @param weeksKept weeks the build retained
 * @param kappaMillis κ of levels 0, 1, 2 and 3, in thousandths
 * @param profiles profile ids, sorted
 * @param tactics tactic ids, sorted
 * @param cells cells ordered by profile, tactic, level and context
 */
public record ValueArtifact(int day, int batch, String spec, int contextVersion, List<String> sources,
                            int halfLifeDays, int weeksKept, List<Integer> kappaMillis, List<String> profiles,
                            List<String> tactics, List<ArtifactFormat.ValueCell> cells) {
    /** Format version written in the header. */
    public static final int FORMAT = 2;
    /** Levels of the hierarchy, 0 to 3. */
    public static final int LEVELS = 4;

    /**
     * Copies the lists and checks the canonical form.
     *
     * @throws IllegalArgumentException when a name table is not sorted, a cell points outside a table, a level is
     *         out of range, or the cells are not in canonical order without repeats
     */
    public ValueArtifact {
        Objects.requireNonNull(spec, "spec");
        sources = List.copyOf(sources);
        kappaMillis = List.copyOf(kappaMillis);
        profiles = List.copyOf(profiles);
        tactics = List.copyOf(tactics);
        cells = List.copyOf(cells);
        if (kappaMillis.size() != LEVELS) {
            throw new IllegalArgumentException("value artifact needs one kappa per level");
        }
        requireSorted(sources, "sources");
        requireSorted(profiles, "profiles");
        requireSorted(tactics, "tactics");
        ArtifactFormat.ValueCell previous = null;
        for (var cell : cells) {
            requireCell(cell, profiles.size(), tactics.size());
            if (previous != null && compare(previous, cell) >= 0) {
                throw new IllegalArgumentException("value cells must be in canonical order without repeats");
            }
            previous = cell;
        }
    }

    /**
     * Canonical order of two cells: profile, tactic, level, context.
     *
     * @param left first cell
     * @param right second cell
     * @return negative, zero or positive
     */
    public static int compare(ArtifactFormat.ValueCell left, ArtifactFormat.ValueCell right) {
        var order = Integer.compare(left.profile(), right.profile());
        if (order == 0) {
            order = Integer.compare(left.tactic(), right.tactic());
        }
        if (order == 0) {
            order = Integer.compare(left.level(), right.level());
        }
        return order != 0 ? order : Long.compare(left.context(), right.context());
    }

    private static void requireSorted(List<String> names, String what) {
        for (var index = 1; index < names.size(); index++) {
            if (names.get(index - 1).compareTo(names.get(index)) >= 0) {
                throw new IllegalArgumentException("value artifact " + what + " must be sorted without repeats");
            }
        }
    }

    private static void requireCell(ArtifactFormat.ValueCell cell, int profileCount, int tacticCount) {
        if (cell.level() < 0 || cell.level() >= LEVELS) {
            throw new IllegalArgumentException("value cell level out of range");
        }
        var profileOk = cell.level() == 0 ? cell.profile() == 0 : cell.profile() >= 0 && cell.profile() < profileCount;
        if (!profileOk || cell.tactic() < 0 || cell.tactic() >= tacticCount) {
            throw new IllegalArgumentException("value cell points outside the name tables");
        }
        var contextOk = cell.level() >= 2 ? cell.context() >= 0 : cell.context() == 0L;
        if (!contextOk || cell.nEff() < 0 || cell.jevSeen() < 0 || cell.jevAgree() < 0
                || cell.jevAgree() > cell.jevSeen() || cell.sdMillis() < 0) {
            throw new IllegalArgumentException("value cell counts are inconsistent");
        }
    }
}
