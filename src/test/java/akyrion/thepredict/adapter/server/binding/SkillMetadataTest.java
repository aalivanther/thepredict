package akyrion.thepredict.adapter.server.binding;

import akyrion.thepredict.admin.InspectFormatter;
import akyrion.thepredict.config.FirstRunFiles;
import akyrion.thepredict.config.ProfileLoader;
import akyrion.thepredict.domain.ChoiceId;
import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.CooldownClass;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.MovementProgress;
import akyrion.thepredict.domain.PredictValue;
import akyrion.thepredict.domain.SessionState;
import akyrion.thepredict.domain.SkillPurpose;
import akyrion.thepredict.engine.SessionReport;
import io.lumine.mythic.api.skills.placeholders.PlaceholderDuration;
import io.lumine.mythic.bukkit.utils.numbers.Duration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillMetadataTest {
    @Test
    void theCooldownClassFollowsTheThresholdsInSeconds() {
        assertEquals(CooldownClass.NONE, classOf(0));
        assertEquals(CooldownClass.SHORT, classOf(3));
        assertEquals(CooldownClass.MEDIUM, classOf(3.05));
        assertEquals(CooldownClass.MEDIUM, classOf(10));
        assertEquals(CooldownClass.LONG, classOf(10.05));
        assertEquals(CooldownClass.SHORT, SkillMetadata.classOf(0.05));
    }

    @Test
    void withoutTheCooldownKeyTheSkillHasNoCooldownButTheCompatibilityCdCounts() {
        assertEquals(CooldownClass.NONE, SkillMetadata.cooldownClass(false, null, 0));
        assertEquals(CooldownClass.SHORT, SkillMetadata.cooldownClass(false, null, 60));
        assertEquals(CooldownClass.MEDIUM, SkillMetadata.cooldownClass(false, null, 61));
    }

    @Test
    void theCompatibilityCdCanCrossAThreshold() {
        assertEquals(CooldownClass.SHORT, SkillMetadata.cooldownClass(true, fixed(Duration.ofSeconds(2)), 20));
        assertEquals(CooldownClass.MEDIUM, SkillMetadata.cooldownClass(true, fixed(Duration.ofSeconds(2)), 21));
        assertEquals(CooldownClass.LONG, SkillMetadata.cooldownClass(true, fixed(Duration.ofSeconds(8)), 41));
    }

    @Test
    void aCooldownWithoutAFixedReadableValueIsUnknown() {
        assertEquals(CooldownClass.UNKNOWN, SkillMetadata.cooldownClass(true, null, 0));
        assertEquals(CooldownClass.UNKNOWN, SkillMetadata.cooldownClass(true, placeholder(false, true,
                Duration.ofSeconds(2)), 0));
        assertEquals(CooldownClass.UNKNOWN, SkillMetadata.cooldownClass(true, placeholder(true, false,
                Duration.ofSeconds(2)), 0));
        assertEquals(CooldownClass.UNKNOWN, SkillMetadata.cooldownClass(true, placeholder(true, true, null), 0));
    }

    @Test
    void aSkillThatIsNotARegularMythicSkillHasAnUnknownCooldown() {
        var skill = MythicFixtures.skill("Strike", Map.of("Cooldown", "2"));
        assertEquals(CooldownClass.UNKNOWN, SkillMetadata.cooldownClass(skill, 0));
        assertEquals(CooldownClass.UNKNOWN, SkillMetadata.cooldownClass(MythicFixtures.skill("Bare", Map.of()), 0));
    }

    @Test
    void aVanillaMeleeChoiceIsShortUntilItsCdReachesSixtyTicks() {
        assertEquals(CooldownClass.SHORT, SkillMetadata.vanillaCooldownClass(0));
        assertEquals(CooldownClass.SHORT, SkillMetadata.vanillaCooldownClass(59));
        assertEquals(CooldownClass.MEDIUM, SkillMetadata.vanillaCooldownClass(60));
    }

    @Test
    void predictValuesMayBeAbsentOnlyThroughTheOptionalReader() {
        assertEquals(Set.of(), SkillMetadata.predictValuesOrEmpty(MythicFixtures.skill("Bare", Map.of())));
        assertEquals(Set.of(), SkillMetadata.predictValuesOrEmpty(MythicFixtures.skill("Empty",
                Map.of("PredictValues", List.of()))));
        assertEquals(Set.of(PredictValue.MELEE_ATTACK, PredictValue.IGNORES_SHIELD), SkillMetadata.predictValuesOrEmpty(
                MythicFixtures.skill("Text", Map.of("PredictValues", "melee_attack, IGNORES_SHIELD"))));
        assertEquals(Set.of(), SkillMetadata.predictValuesOrEmpty(
                MythicFixtures.skill("Typo", Map.of("PredictValues", List.of("MELE_ATTACK")))));
    }

    private static CooldownClass classOf(double seconds) {
        return SkillMetadata.cooldownClass(true, fixed(Duration.ofSeconds(seconds)), 0);
    }

    private static PlaceholderDuration fixed(Duration value) {
        return placeholder(true, true, value);
    }

    private static PlaceholderDuration placeholder(boolean present, boolean fixed, Duration value) {
        return (PlaceholderDuration) Proxy.newProxyInstance(PlaceholderDuration.class.getClassLoader(),
                new Class<?>[] {PlaceholderDuration.class}, (proxy, method, arguments) -> switch (method.getName()) {
                    case "isPresent" -> present;
                    case "isStatic" -> fixed;
                    case "get" -> {
                        if (arguments != null && arguments.length > 0) {
                            throw new UnsupportedOperationException("only get() is read");
                        }
                        yield value;
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    @Test
    void theExampleSkillsGiveTheInspectLineOfPackStepFifteenOne(@TempDir Path folder) throws Exception {
        new FirstRunFiles().installMissing(new akyrion.thepredict.config.ConfigLocations(
                folder.resolve("MythicMobs"), folder));
        var skills = YamlConfiguration.loadConfiguration(folder.resolve(
                "examples/MythicMobs/Skills/ThePredictGuardianSkills.yml").toFile());
        var mob = YamlConfiguration.loadConfiguration(folder.resolve(
                "examples/MythicMobs/Mobs/ThePredictGuardian.yml").toFile());
        mob.set("ThePredictGuardian.Predictor", true);
        var loaded = new ProfileLoader().load(folder.resolve("examples/AI"));
        var bound = new MythicProfileBinder(MythicYaml.definitions(List.of(skills, mob))).bind(loaded.profiles());
        assertEquals(List.of(
                "BindingProblem[severity=WARNING, profileId=Optional[thepredict_guardian], path=thepredict_guardian.Choices.melee, message=skill 'ThePredictGuardianMelee' telegraph has no visual (or audio) cue; ThePredict adds none: add a ~onSignal:PREDICTOR_TELL_BLOCK handler]",
                "BindingProblem[severity=WARNING, profileId=Optional[thepredict_guardian], path=thepredict_guardian.Choices.slam, message=skill 'ThePredictGuardianSlam' telegraph has no visual (or audio) cue; ThePredict adds none: add a ~onSignal:PREDICTOR_TELL_BACK_OFF handler]"),
                bound.problems().stream().map(Object::toString).toList());
        var profile = bound.profiles().get("ThePredictGuardian").profile();
        var purposes = new LinkedHashMap<ChoiceId, Optional<SkillPurpose>>();
        for (var choice : profile.choices().values()) {
            var seconds = Double.parseDouble(skills.getString(choice.mythicSkillId().orElseThrow() + ".Cooldown"));
            purposes.put(choice.id(), Optional.of(choice.typedPurpose().withCooldown(SkillMetadata.cooldownClass(true,
                    fixed(Duration.ofSeconds((long) seconds)), choice.extraCooldownTicks()))));
        }
        var report = new SessionReport(new MobKey(new UUID(1, 1), 0), "ThePredictGuardian", profile.id(),
                SessionState.ACTIVE, ControlLevel.BASE, Optional.empty(), Optional.empty(), 0, 0, false,
                OptionalLong.empty(), List.of(), new MovementProgress(false, false, false, OptionalDouble.empty()),
                Optional.empty(), Optional.empty(), 0, List.of(), 0, Optional.empty(), 0.0, Optional.empty(),
                Optional.empty(), purposes, List.of());
        var expected = "Skills: melee MELEE reach 2.50 BOX_GAP wind-up 5 cooldown SHORT skill, ranged HITSCAN"
                + " reach 16.00 POSITIONS_STRICT wind-up 10 needs sight cooldown SHORT skill, defense SELF wind-up 0"
                + " cooldown MEDIUM skill, slam AREA_AROUND_SELF radius 3.00 wind-up 15 cooldown MEDIUM skill";
        var lines = new InspectFormatter().format(report).stream()
                .map(line -> PlainTextComponentSerializer.plainText().serialize(line)).toList();
        assertTrue(lines.stream().anyMatch(line -> line.endsWith(expected)), String.join("\n", lines));
        assertTrue(Files.readString(Path.of("DocumentacionParaDesarrollar/Implementacion-0.0.1/paquete-pruebas/"
                + "Guia-de-pruebas-por-sesiones.md")).contains("`" + expected + "`"));
    }
}
