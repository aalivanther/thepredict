package akyrion.thepredict.engine.learn;

import akyrion.thepredict.domain.learn.LearnCause;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LearnedArtifactsTest {
    @Test
    void benchAcceptsPassSimOnlyAndPlayersRequirePass() {
        var artifacts = new LearnedArtifacts();
        assertEquals(Optional.of(LearnCause.UNVERIFIED_ARTIFACT), artifacts.activate("abc", "FAIL", false));
        assertEquals(LearnedArtifacts.State.UNVERIFIED_ARTIFACT, artifacts.state());
        assertTrue(artifacts.activate("abc", "PASS_SIM_ONLY", false).isEmpty());
        assertEquals(LearnedArtifacts.State.ACTIVE, artifacts.state());
        assertEquals(Optional.of(LearnCause.UNVERIFIED_ARTIFACT), artifacts.activate("def", "PASS_SIM_ONLY", true));
        artifacts.pending("ghi");
        assertEquals(LearnedArtifacts.State.PENDING_VERDICT, artifacts.state());
        assertTrue(artifacts.activate("ghi", "PASS", true).isEmpty());
        artifacts.degrade();
        assertEquals(LearnedArtifacts.State.DEGRADED, artifacts.state());
        artifacts.rollback();
        artifacts.disable();
        assertEquals(LearnedArtifacts.State.NONE, artifacts.state());
        var artifact = java.nio.file.Path.of("records").resolveSibling("learn").resolve("active.json").toAbsolutePath();
        var warning = LearnedArtifacts.startupWarning(true, artifact);
        assertTrue(warning.orElseThrow().contains("active.json"));
        assertTrue(warning.orElseThrow().contains(artifact.toString()));
        assertTrue(LearnedArtifacts.startupWarning(false, artifact).isEmpty());
    }

    @Test
    void aRejectedCandidateNeverBecomesActiveThroughRollbacks() {
        var artifacts = new LearnedArtifacts();
        assertTrue(artifacts.activate("A", "PASS", true).isEmpty());
        artifacts.pending("B");
        assertEquals("A", artifacts.active());
        assertEquals("B", artifacts.candidate());
        assertEquals(Optional.of(LearnCause.UNVERIFIED_ARTIFACT), artifacts.activate("B", "FAIL", true));
        assertEquals("A", artifacts.active());
        assertEquals("PASS", artifacts.verdict());
        assertEquals(LearnedArtifacts.State.ACTIVE, artifacts.state());
        artifacts.rollback();
        artifacts.rollback();
        assertEquals("A", artifacts.active());
        assertEquals("PASS", artifacts.verdict());
        assertEquals(LearnedArtifacts.State.ACTIVE, artifacts.state());
    }

    @Test
    void rollbackKeepsEachVerdictWithItsArtifact() {
        var artifacts = new LearnedArtifacts();
        assertTrue(artifacts.activate("bench", "PASS_SIM_ONLY", false).isEmpty());
        assertTrue(artifacts.activate("full", "PASS", true).isEmpty());
        artifacts.rollback();
        assertEquals("bench", artifacts.active());
        assertEquals("PASS_SIM_ONLY", artifacts.verdict());
        assertEquals(LearnedArtifacts.State.ACTIVE, artifacts.state());
        artifacts.rollback();
        assertEquals("full", artifacts.active());
        assertEquals("PASS", artifacts.verdict());
    }
}
