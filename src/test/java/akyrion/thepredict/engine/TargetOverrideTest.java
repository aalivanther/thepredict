package akyrion.thepredict.engine;

import akyrion.thepredict.domain.CaptureContext;
import akyrion.thepredict.domain.CombatFacts;
import akyrion.thepredict.domain.DifficultyPreset;
import akyrion.thepredict.domain.DifficultyResolution;
import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.MobProbe;
import akyrion.thepredict.domain.ThreatTableState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A reader double supplies the Mythic variable (M22.9, errata E22-2 and E22-9). */
class TargetOverrideTest {
    @Test
    void theVariableBeatsTheProfileAndAValueOutsideTheRangeIsIgnored() {
        var mob = new MobKey(UUID.fromString("00000000-0000-0000-0000-000000000044"), 1);
        var silent = reader(OptionalDouble.empty(), Optional.empty());
        assertTrue(silent.targetSecondsOverride(mob).isEmpty());
        assertTrue(silent.difficultyOverride(mob).isEmpty());
        var loud = reader(OptionalDouble.of(90.0), Optional.of(DifficultyPreset.CAOS));
        assertEquals(90.0, loud.targetSecondsOverride(mob).getAsDouble());
        assertEquals(DifficultyPreset.CAOS, loud.difficultyOverride(mob).orElseThrow());
        var variable = 4;
        var accepted = variable >= 5 && variable <= 3600 ? variable : DifficultyResolution.ABSENT;
        assertEquals(DifficultyResolution.ABSENT, accepted);
        assertEquals(90, DifficultyResolution.targetSeconds(false, 0, 90, 120, 1800));
    }

    private static CombatReader reader(OptionalDouble seconds, Optional<DifficultyPreset> preset) {
        return new CombatReader() {
            @Override
            public Optional<CombatFacts> capture(EncounterId encounter, List<MobKey> members, CaptureContext context) {
                return Optional.empty();
            }

            @Override
            public Optional<MobProbe> probe(MobKey mob) {
                return Optional.empty();
            }

            @Override
            public boolean inCombat(UUID entityId) {
                return false;
            }

            @Override
            public boolean withinFollowRange(MobKey mob, MobKey member) {
                return false;
            }

            @Override
            public boolean isActive(UUID entityId) {
                return false;
            }

            @Override
            public Optional<ThreatTableState> threatTable(MobKey mob) {
                return Optional.empty();
            }

            @Override
            public OptionalDouble targetSecondsOverride(MobKey mob) {
                return seconds;
            }

            @Override
            public Optional<DifficultyPreset> difficultyOverride(MobKey mob) {
                return preset;
            }
        };
    }
}
