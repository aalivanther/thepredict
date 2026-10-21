package akyrion.thepredict.domain.learn;

import akyrion.thepredict.domain.LearningSettings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Legacy cell codes of a value build (M28.3, errata E28-9 applied). The statistics are those of {@link ValueRollup};
 * only the keys differ. A legacy key holds the 10-bit hash of the profile id and the 8-bit hash of the tactic id,
 * which the legacy lookups compute; the context is the lossless {@link ContextKey} code. Hashes can collide, so a
 * name whose hash is shared with another name of the same build is dropped with all its cells: two tactics never
 * read one cell. The lossless path is {@link ValueRollup} with {@link ValueArtifactFormat}.
 */
public final class ValueHierarchy {
    private ValueHierarchy() {
    }

    /**
     * Legacy entry point: builds every level of {@code rows} under the default {@code learning} limits and returns
     * the cells with legacy codes, in canonical order of the build.
     *
     * @param rows value rows of one build
     * @return the cells
     */
    public static List<ArtifactFormat.ValueCell> cells(List<ValueStatDelta> rows) {
        var rollup = ValueRollup.of(LearningSettings.DEFAULT);
        rollup.add(rows);
        return legacy(rollup.build(0));
    }

    /**
     * The cells of an artifact under legacy codes. Cells of a colliding name are left out.
     *
     * @param artifact a lossless artifact
     * @return the cells with legacy codes
     */
    static List<ArtifactFormat.ValueCell> legacy(ValueArtifact artifact) {
        var profiles = codes(artifact.profiles(), ValueHierarchy::profileCode);
        var tactics = codes(artifact.tactics(), ValueHierarchy::tacticCode);
        var cells = new ArrayList<ArtifactFormat.ValueCell>(artifact.cells().size());
        for (var cell : artifact.cells()) {
            var profile = cell.level() == 0 ? 0 : profiles[cell.profile()];
            var tactic = tactics[cell.tactic()];
            if (profile < 0 || tactic < 0) {
                continue;
            }
            cells.add(new ArtifactFormat.ValueCell(profile, tactic, cell.level(), cell.context(), cell.valueMillis(),
                    cell.sdMillis(), cell.nEff(), cell.jevSeen(), cell.jevAgree(), cell.deltaMillis()));
        }
        return List.copyOf(cells);
    }

    static int profileCode(String profile) {
        return profile.hashCode() & 0x3ff;
    }

    static int tacticCode(String id) {
        var hash = 0;
        for (var index = 0; index < id.length(); index++) {
            hash = hash * 31 + id.charAt(index);
        }
        return hash & 0xff;
    }

    private static int[] codes(List<String> names, ToIntFunction<String> hash) {
        var counts = new HashMap<Integer, Integer>();
        for (var name : names) {
            counts.merge(hash.applyAsInt(name), 1, Integer::sum);
        }
        var codes = new int[names.size()];
        for (var index = 0; index < names.size(); index++) {
            var code = hash.applyAsInt(names.get(index));
            codes[index] = counts.get(code) > 1 ? -1 : code;
        }
        return codes;
    }
}
