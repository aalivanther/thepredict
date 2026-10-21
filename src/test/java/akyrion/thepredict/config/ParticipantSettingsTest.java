package akyrion.thepredict.config;

import akyrion.thepredict.domain.ParticipantRules;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.SpawnCategory;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The optional {@code participants} section of {@code config.yml} (M17.2): defaults of 0.0.1 and strict syntax. */
class ParticipantSettingsTest {
    private final PluginConfigurationLoader loader = new PluginConfigurationLoader();

    private static YamlConfiguration yaml(String text) throws InvalidConfigurationException {
        var configuration = new YamlConfiguration();
        configuration.loadFromString("schemaVersion: 2\n" + text);
        return configuration;
    }

    private ParticipantSettings read(String text) throws InvalidConfigurationException {
        return loader.parse(yaml(text)).participants();
    }

    private String problem(String text) {
        return assertThrows(ConfigurationException.class, () -> read(text)).toProblem(PluginConfigurationLoader.FILE)
                .toString();
    }

    @Test
    void anAbsentSectionAndTheBundledSectionAreBothTheBehaviorOf001() throws Exception {
        assertEquals(ParticipantSettings.standard(), read(""));
        assertEquals(ParticipantRules.DEFAULT, ParticipantSettings.standard().global());
        assertTrue(ParticipantSettings.standard().profilesMayAllow());
        assertEquals(8, ParticipantSettings.standard().maxCandidatesPerMember());
        assertEquals(ParticipantSettings.standard(), read("""
                participants:
                  playerGameModes: [SURVIVAL, ADVENTURE]
                  entities:
                    allow: []
                    deny: []
                    profilesMayAllow: true
                    perceiveNonTargets: false
                    maxCandidatesPerMember: 8
                """));
    }

    @Test
    void everyKeyIsReadWithItsSelectorsInOrder() throws Exception {
        var settings = read("""
                participants:
                  playerGameModes: [survival]
                  entities:
                    allow: ['type:minecraft:zombie', 'tag:mipack:duelistas', 'category:monster', 'kind:ENEMY',
                            'mythic:*', 'mythic:DuelBlue', 'faction:duel_blue', 'team:red', 'tagged:arena', predictor]
                    deny: ['type:villager']
                    profilesMayAllow: false
                    perceiveNonTargets: true
                    maxCandidatesPerMember: 16
                """);
        assertEquals(Set.of("SURVIVAL"), settings.global().playerGameModes());
        assertEquals(10, settings.global().entityAllow().size());
        assertEquals("predictor", settings.global().entityAllow().getLast());
        assertEquals(List.of("type:villager"), settings.global().entityDeny());
        assertFalse(settings.profilesMayAllow());
        assertTrue(settings.global().perceiveNonTargets());
        assertEquals(16, settings.maxCandidatesPerMember());
        assertTrue(read("participants:\n  playerGameModes: []\n").global().playerGameModes().isEmpty());
    }

    @Test
    void aWrongSelectorOrValueNamesTheFileAndTheFullPath() {
        assertEquals("AIConfig/config.yml: participants.entities.allow[1]: has the unknown prefix 'species'; use type, tag,"
                + " category, kind, mythic, faction, team, tagged or predictor",
                problem("participants:\n  entities:\n    allow: ['kind:MOB', 'species:zombie']\n"));
        assertTrue(problem("participants:\n  entities:\n    deny: ['category:MONSTERS']\n")
                .startsWith("AIConfig/config.yml: participants.entities.deny[0]: has an invalid value for category"));
        assertTrue(problem("participants:\n  entities:\n    allow: ['type:Zombie!']\n")
                .contains("participants.entities.allow[0]"));
        assertTrue(problem("participants:\n  entities:\n    allow: ['faction:blue team']\n")
                .contains("has an invalid value for faction"));
        assertTrue(problem("participants:\n  entities:\n    allow: [zombie]\n").contains("must be predictor or"));
        assertEquals("AIConfig/config.yml: participants.playerGameModes[0]: must be SURVIVAL, ADVENTURE, CREATIVE or SPECTATOR,"
                + " was 'HARDCORE'", problem("participants:\n  playerGameModes: [HARDCORE]\n"));
        assertEquals("AIConfig/config.yml: participants.entities.maxCandidatesPerMember: must be between 1 and 16, was 17",
                problem("participants:\n  entities:\n    maxCandidatesPerMember: 17\n"));
        assertEquals("AIConfig/config.yml: participants.entities.shared: is not a known field",
                problem("participants:\n  entities:\n    shared: true\n"));
    }

    @Test
    void atMostSixteenSelectorsPerList() {
        var list = String.join(", ", IntStream.range(0, 17).mapToObj(index -> "'tagged:t" + index + "'")
                .toList());
        assertEquals("AIConfig/config.yml: participants.entities.allow: must have at most 16 selectors, had 17",
                problem("participants:\n  entities:\n    allow: [" + list + "]\n"));
    }

    @Test
    void theSpawnCategoriesCopiedAsTextMatchThoseOfPaper() {
        assertEquals(Arrays.stream(SpawnCategory.values()).map(Enum::name).toList(),
                ParticipantsParser.SPAWN_CATEGORIES);
    }
}
