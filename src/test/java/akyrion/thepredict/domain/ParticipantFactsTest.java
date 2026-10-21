package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParticipantFactsTest {
    @Test
    void threatRankStartsAtOne() {
        assertEquals(1, new ParticipantFacts(new ParticipantAlias(1), HealthBand.HEALTHY, ArmorBand.HEAVY,
                Set.of(), 1).threatRank());
        assertTrue(assertThrows(IllegalArgumentException.class, () -> new ParticipantFacts(new ParticipantAlias(1),
                HealthBand.HEALTHY, ArmorBand.HEAVY, Set.of(), 0)).getMessage().contains("threatRank"));
    }

    @Test
    void cuesAreCopied() {
        var cues = EnumSet.of(TargetCue.BLOCKING);
        var facts = new ParticipantFacts(new ParticipantAlias(2), HealthBand.WOUNDED, ArmorBand.LIGHT, cues, 1);
        cues.add(TargetCue.GLIDING);
        assertEquals(Set.of(TargetCue.BLOCKING), facts.cues());
    }

    @Test
    void anEntityCarriesItsSpeciesAndAPlayerNeverDoes() {
        var zombie = new ParticipantFacts(new ParticipantAlias(ParticipantKind.ENTITY, 1), HealthBand.HEALTHY,
                ArmorBand.LIGHT, Set.of(), 1, Optional.of("wither_skeleton"));
        assertEquals(Optional.of("wither_skeleton"), zombie.species());
        assertEquals(Optional.empty(), new ParticipantFacts(new ParticipantAlias(1), HealthBand.HEALTHY,
                ArmorBand.LIGHT, Set.of(), 1).species());
        assertThrows(IllegalArgumentException.class, () -> new ParticipantFacts(new ParticipantAlias(1),
                HealthBand.HEALTHY, ArmorBand.LIGHT, Set.of(), 1, Optional.of("zombie")));
        assertThrows(IllegalArgumentException.class, () -> new ParticipantFacts(new ParticipantAlias(
                ParticipantKind.ENTITY, 1), HealthBand.HEALTHY, ArmorBand.LIGHT, Set.of(), 1));
    }

    @Test
    void theSpeciesIsTheKeyOfAnEntityTypeWithoutNamespace() {
        var entity = new ParticipantAlias(ParticipantKind.ENTITY, 2);
        for (var invalid : new String[] {"minecraft:zombie", "Zombie", "", "zombie villager", "a".repeat(41)}) {
            assertThrows(IllegalArgumentException.class, () -> new ParticipantFacts(entity, HealthBand.HEALTHY,
                    ArmorBand.LIGHT, Set.of(), 1, Optional.of(invalid)), invalid);
        }
        assertEquals(Optional.of("a".repeat(40)), new ParticipantFacts(entity, HealthBand.HEALTHY, ArmorBand.LIGHT,
                Set.of(), 1, Optional.of("a".repeat(40))).species());
    }
}
