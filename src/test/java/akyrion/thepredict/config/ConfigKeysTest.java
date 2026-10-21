package akyrion.thepredict.config;

import akyrion.thepredict.domain.CandidateScoring;
import akyrion.thepredict.domain.distill.DistillMode;
import akyrion.thepredict.domain.learn.ConfidenceGate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigKeysTest {
    @Test
    void bundledLearningKeepsTheDeliveryDefaults() {
        var learning = PluginSettings.standard().learning();
        assertTrue(learning.collect());
        assertEquals("bench", learning.scope());
        assertEquals(CandidateScoring.PRIORITY, learning.baseScoring());
        assertEquals("off", learning.voiSkip());
        assertEquals("off", learning.jevHint());
        assertEquals(0.10, learning.exploration().epsilon());
        assertEquals(ConfidenceGate.RAW, PluginSettings.standard().decision().confidenceGate());
        assertTrue(PluginSettings.standard().decision().calibratedMinimum().parity());
        var yaml = new org.bukkit.configuration.file.YamlConfiguration();
        yaml.set("schemaVersion", 2);
        yaml.createSection("learning").set("groupPlans", "on");
        assertEquals("learning.groupPlans", assertThrows(ConfigurationException.class,
                () -> LearningConfiguration.learning(yaml)).path());
        yaml.set("learning.groupPlans", "off");
        yaml.set("learning.exploration.policy", "jev_sample");
        assertThrows(ConfigurationException.class, () -> LearningConfiguration.learning(yaml));
    }

    @Test
    void bundledDistillKeepsTheDeliveryDefaults() {
        var distill = PluginSettings.standard().distill();
        var autoconfig = PluginSettings.standard().autoconfig();
        assertEquals(DistillMode.OFF, distill.mode());
        assertEquals("bench", distill.scope());
        assertTrue(distill.collect());
        assertEquals(8, distill.minCellOffers());
        assertEquals(6, distill.maxTreeDepth());
        assertEquals(64, distill.maxLeaves());
        assertEquals(20, distill.minLeafDecisions());
        assertEquals(2, distill.overrideZ());
        assertEquals(50, distill.minCellDecisions());
        assertEquals(0.02, distill.nonInferiorityMargin());
        assertEquals(10, distill.auditEvery());
        assertEquals(81, autoconfig.initialConfigs());
        assertEquals(3, autoconfig.eta());
        assertEquals("off", autoconfig.autoApply());
    }
}
