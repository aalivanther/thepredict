package akyrion.thepredict.engine;

import akyrion.thepredict.domain.DifficultySection;
import akyrion.thepredict.domain.FightOutcome;
import akyrion.thepredict.domain.MemorySettings;
import akyrion.thepredict.domain.PartyBand;
import akyrion.thepredict.domain.PlayerMemory;
import akyrion.thepredict.domain.PressureCause;
import akyrion.thepredict.domain.ProfileHash;
import akyrion.thepredict.domain.ProfileMemory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubjectDifficultyPriorsTest {
    @Test
    void aMissingRungKeepsTheProfileAndAStoredRungMovesWithinTheOffset() {
        var settings = MemorySettings.DEFAULT;
        var cold = SubjectDifficultyPriors.opening(PlayerMemory.empty(), "boss", DifficultySection.SOLO, PartyBand.ONE,
                5, 10, settings);
        assertEquals(5, cold.rung());
        assertTrue(cold.cause().isEmpty());
        var rungs = new int[] {ProfileMemory.NONE, ProfileMemory.NONE, ProfileMemory.NONE, ProfileMemory.NONE,
                ProfileMemory.NONE, ProfileMemory.NONE};
        rungs[0] = 0;
        var row = new ProfileMemory(ProfileHash.of("boss"), 30, 10, 0, 0, FightOutcome.WIN, 10, 30f, 0f, 0f, rungs,
                List.of());
        var memory = new PlayerMemory(10, 10, 30, Optional.empty(), Optional.empty(), Optional.empty(), List.of(row));
        var pulled = SubjectDifficultyPriors.opening(memory, "boss", DifficultySection.SOLO, PartyBand.ONE, 5, 10,
                settings);
        assertTrue(pulled.rung() <= 5);
        assertTrue(pulled.rung() >= 5 - settings.maxRungOffset());
        assertEquals(Optional.of(PressureCause.START_PRIOR_SUBJECT), pulled.cause());
        var errorWithout = Math.abs(5 - 0);
        var errorWith = Math.abs(pulled.rung() - 0);
        assertTrue(errorWith < errorWithout);
    }

    @Test
    void aMixWithoutAnyWeightKeepsTheProfilePriorInsteadOfTheEasiestRung() {
        var d = MemorySettings.DEFAULT;
        var settings = new MemorySettings(d.mode(), d.fightDecay(), d.halfLifeDays(), d.crossFightH(),
                d.styleBreakFactor(), d.seedMaxTrials(), d.minFightTrials(), 0, d.maxRungOffset(),
                d.minDamageShareRatio(), d.maxProfilesPerPlayer(), d.maxFactsPerRequest(), d.factMaxAgeDays(),
                d.cacheSubjects(), d.maxHintsPerTick(), d.loadTimeoutSeconds(), d.refreshSeconds(),
                d.maxPendingDigestsPerSubject(), d.maxPendingSubjects(), d.maxSubjectsPerBatch(), d.retentionDays(),
                d.maxRows(), d.allowUnverified(), d.backend());
        var rungs = new int[] {ProfileMemory.NONE, ProfileMemory.NONE, ProfileMemory.NONE, ProfileMemory.NONE,
                ProfileMemory.NONE, ProfileMemory.NONE};
        rungs[0] = 8 * 16;
        var row = new ProfileMemory(ProfileHash.of("boss"), 30, 10, 0, 0, FightOutcome.WIN, 10, 0f, 0f, 0f, rungs,
                List.of());
        var memory = new PlayerMemory(10, 10, 30, Optional.empty(), Optional.empty(), Optional.empty(), List.of(row));
        var prior = SubjectDifficultyPriors.opening(memory, "boss", DifficultySection.SOLO, PartyBand.ONE, 5, 10,
                settings);
        assertEquals(5, prior.rung(), "0 / 0 is no evidence, not rung 0");
        assertTrue(prior.cause().isEmpty());
    }
}
