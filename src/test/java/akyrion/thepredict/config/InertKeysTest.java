package akyrion.thepredict.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * DOC-09: a key the build accepts and does not read warns once at load when its value is not the delivered one; the
 * shipped file, which writes every key, warns nothing, and a key this build reads never warns.
 */
class InertKeysTest {
    @Test
    void theShippedFileAndAnEmptyFileWarnNothing() throws Exception {
        var shipped = new PluginConfigurationLoader().load(Path.of("src/main/resources/AIConfig/config.yml"));
        assertEquals(List.of(), InertKeys.of(shipped));
        assertEquals(List.of(), InertKeys.of(parse("schemaVersion: 2\n")));
    }

    @Test
    void eachChangedInertKeyWarnsOnceWithItsPath() throws Exception {
        var settings = parse("""
                schemaVersion: 2
                decision:
                  candidateDiversity: true
                records:
                  patternSummaryBinary: true
                group:
                  idleWarnTicks: 120
                  factOnTicks: 12
                  factOffTicks: 50
                  maxFacts: 2
                patterns:
                  hearingBlocks: 8
                  maxWatchedPerEncounter: 2
                  maxCounterDelayTicks: 10
                learning:
                  maxContextKeysPerProfile: 256
                  maxOpenWindowsPerSession: 16
                  calibration:
                    minDecisions: 60
                    driftReferenceWindows: 400
                autoconfig:
                  initialConfigs: 27
                  initialPairs: 10
                  eta: 2
                  maxPairs: 4000
                  maxMinutes: 30
                """);
        var expected = List.of("decision.candidateDiversity", "records.patternSummaryBinary", "group.idleWarnTicks",
                "group.factOnTicks", "group.factOffTicks", "group.maxFacts", "patterns.hearingBlocks",
                "patterns.maxWatchedPerEncounter", "patterns.maxCounterDelayTicks", "learning.maxContextKeysPerProfile",
                "learning.maxOpenWindowsPerSession", "learning.calibration.minDecisions",
                "learning.calibration.driftReferenceWindows", "autoconfig.initialConfigs", "autoconfig.initialPairs",
                "autoconfig.eta", "autoconfig.maxPairs", "autoconfig.maxMinutes");
        assertEquals(expected.stream().map(path -> "AIConfig/config.yml: " + path + ": has no effect in this build")
                .toList(), InertKeys.of(settings).stream().map(ConfigProblem::toString).toList());
    }

    @Test
    void theKeysThisBuildReadsAndTheDifficultyKeysNeverWarn() throws Exception {
        var settings = parse("""
                schemaVersion: 2
                group:
                  maxSquads: 2
                  lkpForgetTicks: 100
                patterns:
                  maxReadsPerTick: 8
                  maxExploitShare: 0.3
                  maxFactsPerRequest: 2
                difficulty:
                  maxTrackedFights: 2
                """);
        assertEquals(List.of(), InertKeys.of(settings));
    }

    private static PluginSettings parse(String text) throws Exception {
        var yaml = new YamlConfiguration();
        yaml.loadFromString(text);
        return new PluginConfigurationLoader().parse(yaml);
    }
}
