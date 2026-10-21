package akyrion.thepredict.config;

import akyrion.thepredict.domain.DifficultyGlobals;
import akyrion.thepredict.domain.GroupCaps;
import akyrion.thepredict.domain.MemorySettings;
import akyrion.thepredict.domain.PatternSettings;
import akyrion.thepredict.domain.StanceSettings;
import akyrion.thepredict.domain.TelegraphSettings;
import akyrion.thepredict.domain.ZoneCaps;
import akyrion.thepredict.domain.distill.AutoconfigSettings;
import akyrion.thepredict.domain.distill.DistillSettings;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every value a {@code domain} record refuses is a {@link ConfigurationException} with the full YAML leaf (CAR-01,
 * CAR-13), a bare {@code off} is the word {@code off} on every mode key (CAR-14), and an empty section is exactly the
 * {@code DEFAULT} of its record (CAR-15).
 */
class ConfigurationLeafErrorsTest {
    private static final String BARE_OFF = """
            schemaVersion: 2
            zone: { mode: off }
            group: { mode: off }
            patterns: { mode: off, airSwings: on }
            memory: { mode: off }
            distill: { mode: off }
            decision: { pruning: off }
            learning: { voiSkip: off, jevHint: off, groupPlans: off }
            autoconfig: { autoApply: off }
            telegraph:
              againstPlayers: off
              againstEntities: off
              minTicks: {}
              answerTicks: {}
            stance: { nativeMode: off }
            """;

    private final PluginConfigurationLoader loader = new PluginConfigurationLoader();

    @Test
    void domainRangeFailuresNameTheFileAndTheLeafInsteadOfEscaping() {
        assertEquals("AIConfig/config.yml: difficulty.toleranceRatio: must be within [0.05, 0.50], was 0.9",
                problem("schemaVersion: 2\ndifficulty:\n  toleranceRatio: 0.9\n"));
        assertEquals("AIConfig/config.yml: difficulty.presets.normal.targetMinutes: must be within [1, 60], was 0",
                problem("schemaVersion: 2\ndifficulty:\n  presets:\n    normal:\n      targetMinutes: 0\n"));
        assertEquals("AIConfig/config.yml: stance.nativeMode: must be hybrid|read|write|off",
                problem("schemaVersion: 2\nstance:\n  nativeMode: wirte\n"));
        assertEquals("AIConfig/config.yml: stance.minDwellTicks: must be within [4, 200], was 2",
                problem("schemaVersion: 2\nstance:\n  minDwellTicks: 2\n"));
        assertEquals("AIConfig/config.yml: telegraph.minTicks.heavy: must be within [10, 40], was 50",
                problem("schemaVersion: 2\ntelegraph:\n  minTicks: { heavy: 50 }\n  answerTicks: {}\n"));
        assertEquals("AIConfig/config.yml: telegraph.answerTicks.block: must be within [0, 10], was 11",
                problem("schemaVersion: 2\ntelegraph:\n  minTicks: {}\n  answerTicks: { block: 11 }\n"));
        assertEquals("AIConfig/config.yml: decision.minimumCalibratedConfidence: must be between 0.5 and 0.99, was 0.3",
                problem("schemaVersion: 2\ndecision:\n  minimumCalibratedConfidence: 0.3\n"));
        assertEquals("AIConfig/config.yml: decision.minimumCalibratedConfidence: must be between 0.5 and 0.99, was NaN",
                problem("schemaVersion: 2\ndecision:\n  minimumCalibratedConfidence: .nan\n"));
        assertEquals("AIConfig/config.yml: records.directory: is not a valid path on this system",
                problem("schemaVersion: 2\nrecords:\n  directory: \"rec\\0ords\"\n"));
    }

    @Test
    void preciseErrorsAreNotWrappedAgainAndSectionRecordsKeepTheirDottedPath() {
        assertEquals("AIConfig/config.yml: autoconfig.eta: must be an integer",
                problem("schemaVersion: 2\nautoconfig:\n  eta: '3'\n"));
        assertEquals("AIConfig/config.yml: autoconfig.eta: is outside its range",
                problem("schemaVersion: 2\nautoconfig:\n  eta: 0\n"));
        assertEquals("AIConfig/config.yml: zone.maxZones: must be an integer",
                problem("schemaVersion: 2\nzone:\n  maxZones: many\n"));
        assertTrue(problem("schemaVersion: 2\nzone:\n  maxZones: 0\n").startsWith("AIConfig/config.yml: zone.maxZones: "));
        assertTrue(problem("schemaVersion: 2\ngroup:\n  maxSquads: 0\n").startsWith("AIConfig/config.yml: group.maxSquads: "));
        assertTrue(problem("schemaVersion: 2\npatterns:\n  minTrials: 0\n")
                .startsWith("AIConfig/config.yml: patterns.minTrials: "));
        assertTrue(problem("schemaVersion: 2\nmemory:\n  halfLifeDays: 0\n")
                .startsWith("AIConfig/config.yml: memory.halfLifeDays: "));
        assertEquals("AIConfig/config.yml: memory.backend: must be sqlite",
                problem("schemaVersion: 2\nmemory:\n  backend: postgres\n"));
        assertEquals("AIConfig/config.yml: distill.mode: must be off|shadow|assist|autonomous",
                problem("schemaVersion: 2\ndistill:\n  mode: loud\n"));
        assertEquals("AIConfig/config.yml: zone.mode: must be off|observe|guard|full",
                problem("schemaVersion: 2\nzone:\n  mode: loud\n"));
    }

    @Test
    void aBareOffIsAcceptedOnEveryModeKey() throws Exception {
        var settings = loader.parse(yaml(BARE_OFF));
        assertEquals("OFF", settings.zone().mode().name());
        assertEquals("OFF", settings.group().mode().name());
        assertEquals("OFF", settings.patterns().mode().name());
        assertEquals("ON", settings.patterns().airSwings().name());
        assertEquals("OFF", settings.memory().mode().name());
        assertEquals("OFF", settings.distill().mode().name());
        assertEquals("OFF", settings.decision().pruning().name());
        assertEquals("off", settings.learning().voiSkip());
        assertEquals("off", settings.learning().jevHint());
        assertEquals("off", settings.autoconfig().autoApply());
        assertFalse(settings.telegraph().againstPlayers());
        assertEquals("OFF", settings.stance().nativeMode().name());
        assertEquals("AIConfig/config.yml: decision.tickLayer.mode: removed; use casts",
                problem("schemaVersion: 2\ndecision:\n  tickLayer: { mode: off }\n"));
    }

    @Test
    void anEmptySectionIsExactlyTheDefaultOfItsRecord() throws Exception {
        var settings = loader.parse(yaml("""
                schemaVersion: 2
                telegraph: { minTicks: {}, answerTicks: {} }
                stance: {}
                difficulty: {}
                zone: {}
                group: {}
                patterns: {}
                memory: {}
                distill: {}
                autoconfig: {}
                """));
        assertEquals(TelegraphSettings.DEFAULT, settings.telegraph());
        assertEquals(StanceSettings.DEFAULT, settings.stance());
        assertEquals(DifficultyGlobals.DEFAULT, settings.difficulty());
        assertEquals(ZoneCaps.DEFAULT, settings.zone());
        assertEquals(GroupCaps.DEFAULT, settings.group());
        assertEquals(PatternSettings.DEFAULT, settings.patterns());
        assertEquals(MemorySettings.DEFAULT, settings.memory());
        assertEquals(DistillSettings.DEFAULT, settings.distill());
        assertEquals(AutoconfigSettings.DEFAULT, settings.autoconfig());
        assertEquals(StorageSettings.standard(), loader.parse(yaml("schemaVersion: 2\nrecords: {}\nstorage: {}\n"))
                .storage());
    }

    @Test
    void aKeyInTheModelFieldIsRefusedAtLoadWithoutShowingIt() throws Exception {
        var secret = "sk-live-PASTED_SECRET_0123";
        var inFile = assertThrows(ConfigurationException.class, () -> loader.parse(yaml("schemaVersion: 2\njev:\n"
                + "  model: '" + secret + "'\n  apiKey: '" + secret + "'\n")));
        assertEquals("jev.model", inFile.path());
        assertFalse(inFile.getMessage().contains("PASTED"), inFile.getMessage());
        var swapped = assertThrows(ConfigurationException.class, () -> loader.parse(yaml("schemaVersion: 2\njev:\n"
                + "  model: 'my-key-text'\n  apiKey: 'jev-1.13.0'\n")));
        assertEquals("jev.model", swapped.path());
        var fromEnvironment = assertThrows(ConfigurationException.class, () -> loader.parse(
                yaml("schemaVersion: 2\njev:\n  model: '" + secret + "'\n"),
                name -> name.equals("TYPESAFE_API_KEY") ? secret : null));
        assertEquals("jev.model", fromEnvironment.path());
        assertFalse(fromEnvironment.getMessage().contains("PASTED"), fromEnvironment.getMessage());
        var consistent = loader.parse(yaml("schemaVersion: 2\njev:\n  model: 'jev-1.13.0'\n"),
                name -> name.equals("TYPESAFE_API_KEY") ? secret : null);
        assertEquals("jev-1.13.0", consistent.jev().model());
        for (var text : List.of("schemaVersion: 2\njev:\n  model: 'jev-1.13.0'\n  apiKey: 'jev-1.13.0'\n")) {
            assertEquals("jev.model", assertThrows(ConfigurationException.class, () -> loader.parse(yaml(text))).path());
        }
    }

    private String problem(String text) {
        var error = assertThrows(ConfigurationException.class, () -> loader.parse(yaml(text)));
        return error.toProblem(PluginConfigurationLoader.FILE).toString();
    }

    private static YamlConfiguration yaml(String text) throws InvalidConfigurationException {
        var configuration = new YamlConfiguration();
        configuration.loadFromString(text);
        return configuration;
    }
}
