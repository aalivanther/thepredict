package akyrion.thepredict.domain.learn;

import akyrion.thepredict.domain.LearningSettings;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.TreeSet;

/**
 * Builds a value artifact from {@code m28_value_stats} rows (M28.3, M28.9, rule C23). Pages may arrive in any number
 * and order: every accepted row is kept and the build computes once, so two pages give the same bytes as one. A row
 * is accepted only with the reward spec of this build, a source token in the training list (compared without case,
 * so {@code SIM} and {@code sim} are the same source), a week after week 0 and a version 2 fine context. Weeks more than {@code weeksKept} before the newest one are dropped; the rest are fused
 * per (profile, tactic, context) with the week weight {@code 2^(−7·age / halfLifeDays)}, in canonical order. The
 * artifact has one cell per key and level and interned names, so two tactics or two contexts never share a key.
 */
public final class ValueRollup {
    private static final long DAY_MS = 86_400_000L;
    private static final Comparator<ValueStatDelta> CANONICAL = Comparator
            .comparing((ValueStatDelta row) -> row.key().profile())
            .thenComparing(row -> row.key().tactic())
            .thenComparing(row -> row.key().context())
            .thenComparingInt(row -> row.key().week())
            .thenComparing(row -> row.key().source())
            .thenComparingInt(ValueStatDelta::windows)
            .thenComparingDouble(ValueStatDelta::rewardMean)
            .thenComparingDouble(ValueStatDelta::rewardM2)
            .thenComparingInt(ValueStatDelta::jevSeen)
            .thenComparingInt(ValueStatDelta::jevAgree)
            .thenComparingLong(ValueStatDelta::epochMs);

    private final List<String> sources;
    private final int halfLifeDays;
    private final int weeksKept;
    private final int maxEntries;
    private final ArrayList<ValueStatDelta> rows = new ArrayList<>();
    private int skipped;

    /**
     * Creates an empty build.
     *
     * @param sources training sources, {@code learning.trainingSources}
     * @param halfLifeDays {@code learning.halfLifeDays}
     * @param weeksKept {@code learning.weeksKept}
     * @param maxEntries {@code learning.maxEntries}
     */
    public ValueRollup(Collection<TrainingSource> sources, int halfLifeDays, int weeksKept, int maxEntries) {
        var tokens = new TreeSet<String>();
        for (var source : sources) {
            tokens.add(source.token());
        }
        this.sources = List.copyOf(tokens);
        if (halfLifeDays <= 0 || weeksKept <= 0 || maxEntries <= 0) {
            throw new IllegalArgumentException("value build limits must be positive");
        }
        this.halfLifeDays = halfLifeDays;
        this.weeksKept = weeksKept;
        this.maxEntries = maxEntries;
    }

    /**
     * A build with the limits of the {@code learning} section.
     *
     * @param settings the section
     * @return the build
     */
    public static ValueRollup of(LearningSettings settings) {
        return new ValueRollup(settings.trainingSources(), settings.halfLifeDays(), settings.weeksKept(),
                settings.maxEntries());
    }

    /**
     * Keeps the accepted rows of one page.
     *
     * @param page rows read from storage
     */
    public void add(List<ValueStatDelta> page) {
        for (var row : page) {
            if (accepts(row)) {
                rows.add(row);
            } else {
                skipped++;
            }
        }
    }

    /**
     * Rows refused so far: another spec, a source outside the list, week 0, or a context that is not version 2.
     *
     * @return the count
     */
    public int skipped() {
        return skipped;
    }

    /**
     * Computes the artifact. The day is the epoch day of the newest accepted row.
     *
     * @param batch batch the build read through
     * @return the canonical artifact
     * @throws IllegalArgumentException when the cells exceed {@code learning.maxEntries}
     */
    public ValueArtifact build(int batch) {
        var newest = 0;
        var epoch = 0L;
        for (var row : rows) {
            newest = Math.max(newest, row.key().week());
            epoch = Math.max(epoch, row.epochMs());
        }
        var retained = new ArrayList<ValueStatDelta>();
        for (var row : rows) {
            if (row.key().week() > newest - weeksKept) {
                retained.add(row);
            }
        }
        retained.sort(CANONICAL);
        var levels = ValueLevels.of(fuse(retained, newest));
        var day = epoch <= 0 ? 0 : (int) Math.min(Integer.MAX_VALUE, epoch / DAY_MS);
        return intern(levels, day, batch);
    }

    private boolean accepts(ValueStatDelta row) {
        Objects.requireNonNull(row, "row");
        return row.windows() > 0 && RewardSpec.ID.equals(row.key().spec()) && row.key().week() > 0
                && sources.contains(row.key().source().toLowerCase(Locale.ROOT))
                && ContextKey.parse(row.key().context()).isPresent();
    }

    private List<ValueLevels.Fine> fuse(List<ValueStatDelta> sorted, int newest) {
        var fines = new ArrayList<ValueLevels.Fine>();
        var start = 0;
        while (start < sorted.size()) {
            var end = start + 1;
            while (end < sorted.size() && sameCell(sorted.get(start), sorted.get(end))) {
                end++;
            }
            fines.add(fine(sorted.subList(start, end), newest));
            start = end;
        }
        return fines;
    }

    private ValueLevels.Fine fine(List<ValueStatDelta> group, int newest) {
        var windows = 0.0;
        var sum = 0.0;
        var seen = 0;
        var agree = 0;
        for (var row : group) {
            var weighted = row.windows() * ShrinkageMath.weekWeight(newest - row.key().week(), halfLifeDays);
            windows += weighted;
            sum += weighted * row.rewardMean();
            seen += row.jevSeen();
            agree += row.jevAgree();
        }
        var mean = sum / windows;
        var m2 = 0.0;
        for (var row : group) {
            var weight = ShrinkageMath.weekWeight(newest - row.key().week(), halfLifeDays);
            var gap = row.rewardMean() - mean;
            m2 += weight * (row.rewardM2() + row.windows() * gap * gap);
        }
        var key = group.getFirst().key();
        return new ValueLevels.Fine(key.profile(), key.tactic(), ContextKey.parse(key.context()).orElseThrow(),
                windows, mean, m2, seen, agree);
    }

    private static boolean sameCell(ValueStatDelta left, ValueStatDelta right) {
        return left.key().profile().equals(right.key().profile()) && left.key().tactic().equals(right.key().tactic())
                && left.key().context().equals(right.key().context());
    }

    private ValueArtifact intern(ValueLevels.Result levels, int day, int batch) {
        var profiles = new TreeSet<String>();
        var tactics = new TreeSet<String>();
        for (var cell : levels.cells()) {
            if (cell.level() > 0) {
                profiles.add(cell.profile());
            }
            tactics.add(cell.tactic());
        }
        var profileList = List.copyOf(profiles);
        var tacticList = List.copyOf(tactics);
        var cells = new ArrayList<ArtifactFormat.ValueCell>(levels.cells().size());
        for (var cell : levels.cells()) {
            var profile = cell.level() == 0 ? 0 : Collections.binarySearch(profileList, cell.profile());
            cells.add(new ArtifactFormat.ValueCell(profile, Collections.binarySearch(tacticList, cell.tactic()),
                    cell.level(), cell.context(), cell.valueMillis(), cell.sdMillis(), cell.nEff(), cell.seen(),
                    cell.agree(), cell.deltaMillis()));
        }
        if (cells.size() > maxEntries) {
            throw new IllegalArgumentException("learning.maxEntries exceeded");
        }
        cells.sort(ValueArtifact::compare);
        var kappas = new ArrayList<Integer>(ValueArtifact.LEVELS);
        for (var kappa : levels.kappa()) {
            kappas.add((int) Math.round(kappa * 1000.0));
        }
        return new ValueArtifact(day, batch, RewardSpec.ID, 2, sources, halfLifeDays, weeksKept, kappas, profileList,
                tacticList, cells);
    }
}
