package akyrion.thepredict.engine.duel;

import akyrion.thepredict.adapter.server.duel.DuelFile;
import akyrion.thepredict.adapter.server.duel.DuelJoin;
import akyrion.thepredict.adapter.server.duel.DuelTestKit;
import akyrion.thepredict.adapter.server.duel.DuelWriter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The matrix of the simulator (M18.7): its skill table is the one of the duel pack, its aggregates are sound, and one
 * full session of the script (80 rounds without a key and 30 with the oracle), recorded by the real recorder of M18.4,
 * fits within the limits, writes every file and joins every request by its key.
 */
class DuelMatrixTest {
    private static final Path MOBS = Path.of("DocumentacionParaDesarrollar/Implementacion-0.0.1/paquete-pruebas/"
            + "archivos/13-duelo/plugins/MythicMobs/Mobs/ThePredictDuel.yml");

    @TempDir
    Path root;

    @Test
    void theSkillTableIsTheOneOfTheDuelPack() {
        var skills = YamlConfiguration.loadConfiguration(DuelTestKit.PACK_SKILLS.toFile());
        for (var skill : DuelSkill.values()) {
            var id = skill.id;
            assertEquals(skill.cooldownTicks, Math.round(skills.getDouble(id + ".Cooldown") * 20), id);
            var lines = skills.getStringList(id + ".Skills");
            assertEquals(skill.damage, number(lines, "damage\\{a=([0-9.]+)\\}"), id);
            assertEquals(skill.delayTicks, number(lines, "^delay ([0-9]+)"), id);
            assertEquals(skill.shield, number(lines, "shield\\{amount=([0-9.]+)"), id);
            assertEquals(skill.heal, number(lines, "heal\\{a=([0-9.]+)\\}"), id);
            var conditions = skills.getStringList(id + ".Conditions");
            assertEquals(skill.withinBlocks, number(conditions, "targetwithin\\{d=([0-9.]+)\\} true"), id);
            assertEquals(skill.healthBelow >= 1 ? 0 : skill.healthBelow * 100, number(conditions,
                    "healthpercent\\{p=<([0-9.]+)%\\} true"), id);
        }
        var base = YamlConfiguration.loadConfiguration(MOBS.toFile());
        assertEquals(DuelWorld.PUNCH_DAMAGE, base.getDouble("DuelBase.Damage"), "the vanilla hit of DuelBase");
        assertEquals(DuelWorld.MAX_HEALTH, base.getDouble("DuelBase.Health"));
        var mobs = base.getStringList("DuelBase.Skills");
        for (var rule : DuelSkill.RULES) {
            var name = "DuelRule" + rule.skill().id.substring("Duel".length());
            var conditions = skills.getStringList(name + ".Conditions");
            assertEquals("predictorcontrol false", conditions.getFirst(), name);
            assertEquals(rule.withinBlocks(), number(conditions, "targetwithin\\{d=([0-9.]+)\\} true"), name);
            assertEquals(rule.notWithinBlocks(), number(conditions, "targetwithin\\{d=([0-9.]+)\\} false"), name);
            assertEquals(rule.healthBelow() >= 1 ? 0 : rule.healthBelow() * 100, number(conditions,
                    "healthpercent\\{p=<([0-9.]+)%\\} true"), name);
            assertTrue(skills.getStringList(name + ".Skills").getFirst().startsWith("skill{s=" + rule.skill().id + "}"),
                    name);
            assertTrue(mobs.contains("skill{s=" + name + "} @self ~onTimer:" + rule.timerTicks()), name);
        }
        assertEquals(DuelSkill.RULES.stream().map(rule -> "DuelRule" + rule.skill().id.substring(4)).toList(),
                mobs.stream().filter(line -> line.contains("DuelRule")).map(line -> line.substring(8,
                        line.indexOf('}'))).toList(), "the rules keep the order of DuelBase");
    }

    @Test
    void theAggregatesCountEveryRoundAndTheWilsonIntervalHoldsTheScore() {
        var matrix = DuelMatrix.run(List.of(DuelSimulator.Arm.D, DuelSimulator.Arm.A), 8, new DuelSimulator.Mode.None());
        for (var summary : matrix.values()) {
            assertEquals(8, summary.rounds());
            assertEquals(8, summary.redWins() + summary.blueWins() + summary.draws());
            assertTrue(summary.wilson()[0] <= summary.redScore() && summary.redScore() <= summary.wilson()[1]);
            assertTrue(summary.ticks().minimum() <= summary.ticks().mean()
                    && summary.ticks().mean() <= summary.ticks().maximum());
        }
        var interval = DuelMatrix.wilson(15, 20);
        assertTrue(interval[0] > 0.5, "15 of 20 excludes 50 %: " + interval[0]);
        assertTrue(DuelMatrix.wilson(14, 20)[0] < 0.5, "14 of 20 does not");
        assertTrue(DuelMatrix.markdown("t", matrix).contains("| D | 8 |"));
    }

    @Test
    void theClassicArmNeverCountsATokenBecauseTokensBelongToTheRoundThatSentThem() {
        var matrix = DuelMatrix.run(List.of(DuelSimulator.Arm.A, DuelSimulator.Arm.B, DuelSimulator.Arm.C,
                DuelSimulator.Arm.D), 200, new DuelSimulator.Mode.Oracle(0.85, 20260929L));
        var classic = matrix.get(DuelSimulator.Arm.D);
        assertEquals(0, classic.requests());
        assertEquals(0, classic.tokens(), "a late answer of arm C once added 700 tokens to arm D");
        for (var summary : matrix.values()) {
            assertEquals(summary.requests() == 0, summary.tokens() == 0, summary.arm().name());
        }
    }

    @Test
    void aFullSessionFitsTheLimitsWritesEveryFileAndJoinsEveryRequest() throws IOException {
        var local = DuelSessionRun.folder(root.resolve("local"), 80, List.of(DuelSimulator.Arm.A, DuelSimulator.Arm.B,
                DuelSimulator.Arm.C, DuelSimulator.Arm.D), new DuelSimulator.Mode.None(), "local-test");
        var keyed = DuelSessionRun.folder(root.resolve("oracle"), 30, List.of(DuelSimulator.Arm.A,
                DuelSimulator.Arm.B, DuelSimulator.Arm.C), new DuelSimulator.Mode.Oracle(0.85, 11L), "oracle-test");
        var total = 0L;
        for (var folder : List.of(local, keyed)) {
            for (var file : DuelFile.values()) {
                var size = Files.size(folder.resolve(file.fileName()));
                assertTrue(size < DuelWriter.MAX_FILE_BYTES, file.fileName());
                total += size;
            }
            assertTrue(Files.readString(folder.resolve("summary.md")).contains("- stopped_by: command"));
            assertTrue(Files.readString(folder.resolve("summary.md")).contains("rows dropped (queue full): 0;"));
        }
        assertTrue(total < DuelWriter.MAX_RECORDING_BYTES);
        assertEquals(81, Files.readAllLines(local.resolve("rounds.csv")).size());
        assertEquals(31, Files.readAllLines(keyed.resolve("rounds.csv")).size());
        var requests = DuelJoin.join(Files.readAllLines(keyed.resolve("decisions.jsonl")));
        var sent = requests.stream().filter(request -> request.lines().getFirst().get("dispatch").getAsString()
                .equals("SENT")).toList();
        assertTrue(sent.size() > 100, "requests: " + sent.size());
        for (var request : sent) {
            var types = request.types();
            assertEquals(List.of("planned", "body", "result"), types.subList(0, 3), types.toString());
            assertTrue(types.subList(3, types.size()).stream().allMatch("acceptance"::equals), types.toString());
        }
        assertTrue(Files.readAllLines(local.resolve("decisions.jsonl")).stream()
                .anyMatch(line -> line.startsWith("{\"type\":\"local\"")));
    }

    private static double number(List<String> lines, String regex) {
        var pattern = Pattern.compile(regex);
        for (var line : lines) {
            var matcher = pattern.matcher(line.strip());
            if (matcher.find()) {
                return Double.parseDouble(matcher.group(1));
            }
        }
        return 0;
    }
}
