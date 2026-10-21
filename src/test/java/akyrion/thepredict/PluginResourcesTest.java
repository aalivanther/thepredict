package akyrion.thepredict;

import akyrion.thepredict.config.FirstRunFiles;
import akyrion.thepredict.config.ProfileCatalog;
import akyrion.thepredict.config.ProfileLoader;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginDescriptionFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginResourcesTest {
    @Test
    void declaresPaperVersionMythicDependencyTheCommandAndItsTwoPermissions() throws Exception {
        try (var descriptor = getClass().getResourceAsStream("/plugin.yml")) {
            assertNotNull(descriptor);
            var plugin = new PluginDescriptionFile(descriptor);
            assertEquals("26.2", plugin.getAPIVersion());
            assertTrue(plugin.getDepend().contains("MythicMobs"));
            var command = plugin.getCommands().get("thepredict");
            assertEquals("thepredict.admin", command.get("permission"));
            assertEquals("/thepredict [status|check|inspect <nearest|uuid>|intention suggest [profile]|records|eval|zone|memory|learn|distill|autoconfig|debug|duel]",
                    command.get("usage"));
            var permissions = plugin.getPermissions();
            assertEquals(PermissionDefault.OP, permissions.stream().filter(permission -> permission.getName()
                    .equals("thepredict.admin")).findFirst().orElseThrow().getDefault());
            assertEquals(PermissionDefault.FALSE, permissions.stream().filter(permission -> permission.getName()
                    .equals("thepredict.debug")).findFirst().orElseThrow().getDefault());
            assertTrue(plugin.getDescription().contains("Jev"));
        }
    }

    @Test
    void bundlesSetupFilesForAnEmptyInstallation() throws Exception {
        var configuration = text("/AIConfig/config.yml");
        assertTrue(configuration.contains("schemaVersion: 2"));
        assertFalse(configuration.lines().anyMatch(line -> !line.isEmpty() && !Character.isWhitespace(line.charAt(0))
                && line.trim().startsWith("provider:")));
        assertTrue(configuration.contains("provider: typesafe"));
        assertTrue(configuration.lines().anyMatch(line -> line.trim().equals("apiKey: ''")));
        assertTrue(configuration.contains("model: 'jev-1.13.0'"));
        var profile = text("/AIConfig/Profiles/example.yml.example");
        assertTrue(profile.contains("schemaVersion: 2"));
        assertTrue(profile.contains("Tactics:"));
        assertTrue(text("/AIConfig/Profiles/README.txt").contains("TYPESAFE_API_KEY"));
    }

    @Test
    void theExampleMobStartsInactiveWithFollowRangeAThreatTableAndNoGovernedGoals() throws Exception {
        var mob = yaml("/examples/MythicMobs/Mobs/ThePredictGuardian.yml").getConfigurationSection("ThePredictGuardian");
        assertNotNull(mob);
        assertEquals("ZOMBIE", mob.getString("Type"));
        assertFalse(mob.getBoolean("Predictor", true));
        assertEquals(32, mob.getInt("Options.FollowRange"));
        assertFalse(mob.isSet("Options.AsyncPathfinding"), "only if M01 validates it (DT-10)");
        assertTrue(mob.getBoolean("Modules.ThreatTable"));
        assertFalse(mob.isSet("AIGoalSelectors"));
        assertEquals(List.of("damage{a=2} @target ~onAttack",
                "setstance{stance=pressing} @self ~onSignal:PREDICTOR_STANCE_PRESSING",
                "setstance{stance=guarded} @self ~onSignal:PREDICTOR_STANCE_GUARDED",
                "setstance{stance=evasive} @self ~onSignal:PREDICTOR_STANCE_EVASIVE"), mob.getStringList("Skills"));
    }

    @Test
    void theExampleSkillsHaveValuesANativeCooldownRangeSightAndNoHealAtFullHealth() throws Exception {
        var skills = yaml("/examples/MythicMobs/Skills/ThePredictGuardianSkills.yml");
        assertEquals(List.of("ThePredictGuardianMelee", "ThePredictGuardianRanged", "ThePredictGuardianDefense",
                "ThePredictGuardianSlam"), List.copyOf(skills.getKeys(false)));
        for (var skill : skills.getKeys(false)) {
            assertFalse(skills.getStringList(skill + ".PredictValues").isEmpty(), skill);
            assertTrue(skills.getInt(skill + ".Cooldown") > 0, skill);
        }
        assertEquals(List.of("targetwithin{d=16} true"), skills.getStringList("ThePredictGuardianRanged.Conditions"));
        assertEquals(List.of("lineofsight true"), skills.getStringList("ThePredictGuardianRanged.TargetConditions"));
        assertEquals(List.of("healthpercent{p=<90%} true"), skills.getStringList("ThePredictGuardianDefense.Conditions"));
        assertEquals(List.of("effect:particles{p=cloud;a=30;hs=1.5;vs=0.1;y=0.2} @self", "delay 15",
                "damage{a=5} @PlayersInRadius{r=3}", "throw{velocity=6;velocityY=1} @PlayersInRadius{r=3}"),
                skills.getStringList("ThePredictGuardianSlam.Skills"), "the slam hits players only (M15.9)");
    }

    @Test
    void theExampleProfileLeavesPurposeToTheSkillIntentionAndKeepsTheSlamTactic() throws Exception {
        var skills = yaml("/examples/MythicMobs/Skills/ThePredictGuardianSkills.yml");
        assertEquals("DAMAGE, MELEE, DAMAGE_MEDIUM, WHEN_TARGET_IN_MELEE",
                skills.getString("ThePredictGuardianMelee.Intention"));
        assertEquals("DAMAGE, HITSCAN, DAMAGE_LOW, WHEN_TARGET_FLEEING, WHEN_TARGET_AT_RANGE,"
                + " RISK_NEEDS_LINE_OF_SIGHT", skills.getString("ThePredictGuardianRanged.Intention"));
        assertEquals("SUSTAIN, SELF, WHEN_SELF_LOW_HEALTH", skills.getString("ThePredictGuardianDefense.Intention"));
        assertEquals("CONTROL, DAMAGE, AREA_AROUND_SELF, DAMAGE_MEDIUM, WHEN_ENEMIES_GROUPED, WHEN_TARGET_IN_MELEE,"
                + " RISK_ROOTS_CASTER", skills.getString("ThePredictGuardianSlam.Intention"));
        var profile = yaml("/examples/AI/ThePredictGuardian.yml").getConfigurationSection("thepredict_guardian");
        assertNotNull(profile);
        assertEquals(List.of("melee", "ranged", "defense", "slam"),
                List.copyOf(profile.getConfigurationSection("Choices").getKeys(false)));
        for (var choice : profile.getConfigurationSection("Choices").getKeys(false)) {
            assertFalse(profile.isConfigurationSection("Choices." + choice + ".Purpose"), choice);
        }
        assertEquals("APPROACH", profile.getString("Tactics.close_in_and_slam.Movement"));
        assertEquals("slam", profile.getString("Tactics.close_in_and_slam.Choice"));
        assertEquals(65, profile.getInt("Tactics.close_in_and_slam.Priority"));
        assertFalse(profile.isSet("Tactics.close_in_and_slam.Description"));
    }

    @Test
    void theReadmesAskForTheIntentionLineAndNotForPredictValues() throws Exception {
        var profiles = text("/AIConfig/Profiles/README.txt");
        assertTrue(profiles.contains("Add an Intention line to each referenced MythicMobs skill"), profiles);
        assertFalse(profiles.contains("Add PredictValues"), profiles);
        assertTrue(profiles.contains("PredictValues are no longer read"), profiles);
        var example = text("/examples/README.txt");
        assertTrue(example.contains("Each skill of the example declares its Intention line"), example);
        assertFalse(example.contains("declares its Purpose"), example);
    }

    @Test
    void theExampleGuideExplainsCopyActivationReloadStatusAndInspect() throws Exception {
        var guide = text("/examples/README.txt");
        for (var step : List.of("plugins/MythicMobs/Mobs/", "plugins/MythicMobs/Skills/",
                "plugins/MythicMobs/AIConfig/Profiles/",
                "Predictor: true", "/mm reload", "/thepredict status", "/thepredict inspect nearest")) {
            assertTrue(guide.contains(step), step);
        }
        assertFalse(guide.contains("not active in this build"));
    }

    @Test
    void bundlesTheMigrationReportTemplate() throws Exception {
        var text = text("/migration/report-template.md");
        assertTrue(text.contains("{{files}}") && text.contains("{{decisions}}") && text.contains("{{manual}}"));
    }

    private String text(String resource) throws Exception {
        try (var stream = getClass().getResourceAsStream(resource)) {
            assertNotNull(stream, resource);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private YamlConfiguration yaml(String resource) throws Exception {
        try (var stream = getClass().getResourceAsStream(resource)) {
            assertNotNull(stream, resource);
            return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
    }

    @Test
    void theExampleGuideQuotesTheRealCatalogLinesBeforeAndAfterPredictorIsSet(@TempDir Path folder) throws Exception {
        new FirstRunFiles().installMissing(new akyrion.thepredict.config.ConfigLocations(
                folder.resolve("MythicMobs"), folder));
        var profiles = new ProfileLoader().load(folder.resolve("examples/AI")).profiles();
        var inactive = ProfileCatalog.empty().next(Map.of(), List.of()).summary(1).replace("revision 1", "revision N");
        var active = ProfileCatalog.empty().next(profiles, List.of()).summary(1).replace("revision 1", "revision N");
        var guide = text("/examples/README.txt");
        assertTrue(guide.contains("Profiles: " + inactive), inactive);
        assertTrue(guide.contains("Profiles: " + active), active);
        assertTrue(inactive.contains("1 profiles read, 0 active") && active.contains("1 profiles read, 1 active"));
    }
}
