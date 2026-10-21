package akyrion.thepredict.adapter.storage;

import akyrion.thepredict.domain.LearningSettings;
import akyrion.thepredict.domain.learn.ArtifactFormat;
import akyrion.thepredict.domain.learn.ValueArtifactFormat;
import akyrion.thepredict.domain.learn.ValueRollup;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

/**
 * {@code /thepredict learn build} as a writer job (M28.9, T1-ter). Each {@link #step} reads one keyset page of
 * {@code m28_value_stats} of at most {@code learning.buildChunkRows} rows into a {@link ValueRollup}, so a batch
 * waits at most one page. After the last page the rollup computes once, so many pages give the bytes of one, and the
 * {@code .tplv} format 2 is written atomically with the batch the build read through. One build runs at a time. Only
 * the storage thread calls this.
 */
final class LearnBuildJob {
    private final LearningSettings learning;
    private ValueRollup rollup;
    private String profile = "";
    private String cursor = "";
    private long commandId;
    private long batch;
    private long newestEpochMs;
    private boolean running;

    LearnBuildJob(LearningSettings learning) {
        this.learning = learning;
    }

    /**
     * Whether a build is in progress.
     *
     * @return {@code true} until the last step
     */
    boolean running() {
        return running;
    }

    /**
     * The command that started the build in progress.
     *
     * @return its id
     */
    long commandId() {
        return commandId;
    }

    /**
     * Starts a build. The batch is the last one applied when the build starts: every row it read was applied by then.
     *
     * @param id the command id the result answers
     * @param profileFilter profile id, empty for every profile
     * @param appliedBatch {@code runs.last_applied_batch} of this run
     */
    void start(long id, String profileFilter, long appliedBatch) {
        rollup = ValueRollup.of(learning);
        profile = profileFilter;
        cursor = "";
        commandId = id;
        batch = appliedBatch;
        newestEpochMs = 0L;
        running = true;
    }

    /** Drops the build in progress. */
    void stop() {
        running = false;
        rollup = null;
    }

    /**
     * Reads one page, or, after the last one, builds and installs the artifact.
     *
     * @param read the read connection
     * @param write the write connection
     * @param learnRoot the {@code learn} folder
     * @return the result text when the build is finished, otherwise empty
     * @throws IOException when the artifact or {@code active.json} cannot be written
     * @throws SQLException when a page or the artifact row fails
     * @throws IllegalArgumentException when the cells exceed {@code learning.maxEntries}
     */
    Optional<String> step(Connection read, Connection write, Path learnRoot) throws IOException, SQLException {
        if (!running) {
            return Optional.empty();
        }
        var page = ValueStatSql.page(read, profile, 0, learning.buildChunkRows(), cursor);
        rollup.add(page.rows());
        for (var row : page.rows()) {
            newestEpochMs = Math.max(newestEpochMs, row.epochMs());
        }
        cursor = page.nextCursor();
        if (!cursor.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(install(write, learnRoot));
        } finally {
            stop();
        }
    }

    private String install(Connection write, Path learnRoot) throws IOException, SQLException {
        var artifact = rollup.build((int) Math.min(Integer.MAX_VALUE, batch));
        var bytes = ValueArtifactFormat.write(artifact);
        var sha = ArtifactFormat.sha256(bytes);
        AtomicFiles.write(learnRoot.resolve("artifacts").resolve(sha + ".tplv"), bytes);
        ArtifactSql.insertIgnore(write, sha, "VALUE", bytes.length, artifact.cells().size(), artifact.batch(),
                newestEpochMs, "PENDING_VERDICT");
        var next = LearnActive.read(learnRoot.resolve("active.json")).withValue(sha);
        next.write(learnRoot.resolve("active.json"));
        ArtifactSql.prune(write, next.value(), next.previousValue());
        return "built " + sha + " from " + artifact.cells().size() + " cells, skipped " + rollup.skipped() + " rows";
    }
}
