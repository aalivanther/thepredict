package akyrion.thepredict.adapter.server.binding;

import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrainSpeciesTest {
    @Test
    void recognizesEveryBrainSpeciesOfThePlanInAnyNamingForm() {
        for (var species : new String[] {"ALLAY", "ARMADILLO", "AXOLOTL", "CAMEL", "FROG", "TADPOLE", "GOAT",
                "COPPER_GOLEM", "HAPPY_GHAST", "NAUTILUS", "ZOMBIE_NAUTILUS", "SNIFFER", "ZOGLIN", "BREEZE", "CREAKING",
                "HOGLIN", "PIGLIN", "PIGLIN_BRUTE", "WARDEN", "VILLAGER"}) {
            assertTrue(BrainSpecies.contains(species), species);
        }
        assertTrue(BrainSpecies.contains("piglin_brute"));
        assertTrue(BrainSpecies.contains("PiglinBrute"));
        assertTrue(BrainSpecies.contains("BABY_HOGLIN"));
    }

    @Test
    void goalSelectorSpeciesAreNotBrainSpecies() {
        for (var species : new String[] {"ZOMBIE", "SKELETON", "PILLAGER", "ZOMBIFIED_PIGLIN", "WITHER_SKELETON", ""}) {
            assertFalse(BrainSpecies.contains(species), species);
        }
    }

    @Test
    void everyPlanNameResolvesToAPaperEntityTypeInTheList() {
        var names = new String[] {"ALLAY", "ARMADILLO", "AXOLOTL", "CAMEL", "FROG", "TADPOLE", "GOAT", "COPPER_GOLEM",
                "HAPPY_GHAST", "NAUTILUS", "ZOMBIE_NAUTILUS", "SNIFFER", "ZOGLIN", "BREEZE", "CREAKING", "HOGLIN",
                "PIGLIN", "PIGLIN_BRUTE", "WARDEN", "VILLAGER"};
        for (var name : names) {
            assertTrue(BrainSpecies.contains(EntityType.valueOf(name)), name);
        }
        assertEquals(names.length, BrainSpecies.SPECIES.size());
    }

    @Test
    void liveGoalSelectorTypesAreNotBrainSpecies() {
        for (var type : new EntityType[] {EntityType.ZOMBIE, EntityType.SKELETON, EntityType.ZOMBIFIED_PIGLIN,
                EntityType.PILLAGER, EntityType.WITHER_SKELETON}) {
            assertFalse(BrainSpecies.contains(type), type.name());
        }
    }
}
