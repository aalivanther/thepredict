package akyrion.thepredict.config;

import akyrion.thepredict.domain.HealthThresholds;
import akyrion.thepredict.domain.MovementSettings;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProfileDefaultsTest {
    @Test
    void standardValuesMatchTheSchemaTableAndTheDomainDefaults() {
        var standard = ProfileDefaults.standard();

        assertEquals(new ProfileDefaults(2, 20, 40, 0.3, 0.6, 2.5, 5, 9, 16, 10, 1.2, 1.15), standard);
        assertEquals(new MovementSettings(2.5, 5, 9, 16, 10, 1.2, 1.15, OptionalDouble.empty()), standard.movement());
        assertEquals(new HealthThresholds(0.3, 0.6), standard.health());
    }

    @Test
    void readsGlobalDefaultsAndKeepsTheOthers() throws Exception {
        var defaults = read("minimumDecisionIntervalTicks: 30\n  meleeReachBlocks: 3");

        assertEquals(30, defaults.minimumDecisionIntervalTicks());
        assertEquals(3.0, defaults.meleeReachBlocks());
        assertEquals(40, defaults.maximumIntentDurationTicks());
    }

    @Test
    void tickRangesRejectValuesJustOutside() {
        assertBounds("observationIntervalTicks", 1, 20);
        assertBounds("minimumDecisionIntervalTicks", 10, 40);
        assertBounds("maximumIntentDurationTicks", 20, 400);
        assertEquals("profileDefaults.minimumDecisionIntervalTicks",
                assertThrows(ConfigurationException.class, () -> read("minimumDecisionIntervalTicks: 201")).path());
    }

    @Test
    void intentMustLastAtLeastTheMinimumDecisionInterval() {
        var error = assertThrows(ConfigurationException.class,
                () -> read("minimumDecisionIntervalTicks: 50\n  maximumIntentDurationTicks: 49"));
        assertEquals("profileDefaults.maximumIntentDurationTicks", error.path());
        assertDoesNotThrow(() -> read("minimumDecisionIntervalTicks: 50\n  maximumIntentDurationTicks: 50"));
    }

    @Test
    void healthRatiosMustBeOrderedInsideZeroAndOne() {
        assertEquals("profileDefaults.criticalHealthRatio", assertThrows(ConfigurationException.class,
                () -> read("criticalHealthRatio: 0")).path());
        assertEquals("profileDefaults.woundedHealthRatio", assertThrows(ConfigurationException.class,
                () -> read("criticalHealthRatio: 0.6")).path());
        assertEquals("profileDefaults.woundedHealthRatio", assertThrows(ConfigurationException.class,
                () -> read("woundedHealthRatio: 1.0")).path());
    }

    @Test
    void distancesMustBeOrderedFromMeleeToRangedReach() {
        assertEquals("profileDefaults.meleeReachBlocks", assertThrows(ConfigurationException.class,
                () -> read("meleeReachBlocks: 0")).path());
        assertEquals("profileDefaults.bandInnerBlocks", assertThrows(ConfigurationException.class,
                () -> read("meleeReachBlocks: 5")).path());
        assertEquals("profileDefaults.bandOuterBlocks", assertThrows(ConfigurationException.class,
                () -> read("bandOuterBlocks: 5")).path());
        assertEquals("profileDefaults.rangedReachBlocks", assertThrows(ConfigurationException.class,
                () -> read("rangedReachBlocks: 8.9")).path());
        assertDoesNotThrow(() -> read("rangedReachBlocks: 9"));
        assertEquals("profileDefaults.retreatDistanceBlocks", assertThrows(ConfigurationException.class,
                () -> read("retreatDistanceBlocks: -1")).path());
    }

    @Test
    void speedsStayBetweenATenthAndThreeTimes() {
        for (var key : new String[] {"retreatSpeed", "approachSpeed"}) {
            assertDoesNotThrow(() -> read(key + ": 0.1"));
            assertDoesNotThrow(() -> read(key + ": 3"));
            assertEquals("profileDefaults." + key, assertThrows(ConfigurationException.class, () -> read(key + ": 0.09")).path());
            assertEquals("profileDefaults." + key, assertThrows(ConfigurationException.class, () -> read(key + ": 3.01")).path());
        }
    }

    private static void assertBounds(String key, int minimum, int maximum) {
        assertDoesNotThrow(() -> read(key + ": " + minimum), key);
        assertDoesNotThrow(() -> read(key + ": " + maximum), key);
        assertEquals("profileDefaults." + key, assertThrows(ConfigurationException.class,
                () -> read(key + ": " + (minimum - 1))).path());
    }

    private static ProfileDefaults read(String entries) throws Exception {
        var configuration = new YamlConfiguration();
        configuration.loadFromString("profileDefaults:\n  " + entries + "\n");
        return ProfileDefaults.from(configuration);
    }
}
