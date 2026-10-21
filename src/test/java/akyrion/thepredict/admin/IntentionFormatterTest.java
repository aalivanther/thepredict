package akyrion.thepredict.admin;

import akyrion.thepredict.domain.ChoiceId;
import akyrion.thepredict.domain.ChoiceKind;
import akyrion.thepredict.domain.ChoiceTelegraph;
import akyrion.thepredict.domain.TellAnswer;
import akyrion.thepredict.domain.CooldownClass;
import akyrion.thepredict.domain.DamageTier;
import akyrion.thepredict.domain.DecisionTiming;
import akyrion.thepredict.domain.Delivery;
import akyrion.thepredict.domain.HealthThresholds;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.MovementSettings;
import akyrion.thepredict.domain.PurposeOrigin;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.SkillPurpose;
import akyrion.thepredict.domain.SkillRole;
import akyrion.thepredict.domain.Tactic;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.domain.ThreatWeights;
import akyrion.thepredict.engine.DuelRecording;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** M19.8: {@code /thepredict intention suggest} prints the canonical line and never writes a MythicMobs file. */
class IntentionFormatterTest {
    private static final String REACH = "Reach 12 has no Intention token: add targetwithin{d=12} to the skill's"
            + " Conditions or keep Purpose in the profile";

    private final IntentionFormatter formatter = new IntentionFormatter();

    @Test
    void printsTheCanonicalLineAndTheReachThatDoesNotFit() {
        var lines = text(formatter.format(List.of("suggest", "guardian_ai"), List.of(profile("guardian_ai",
                melee(12, 0), untyped("poke")))));
        assertEquals(List.of("[ThePredict] guardian_ai",
                "[ThePredict] melee: DAMAGE, MELEE, DAMAGE_MEDIUM",
                "[ThePredict] melee: " + REACH,
                "[ThePredict] poke: untyped; no Intention line"), lines);
    }

    @Test
    void theAnswerOfTheTellStaysOnTheSuggestedLine() {
        var told = melee(2, 0).withTelegraph(new ChoiceTelegraph(Optional.empty(), Optional.of(TellAnswer.DODGE),
                OptionalInt.empty()));
        var lines = text(formatter.format(List.of("suggest"), List.of(profile("guardian_ai", told))));
        assertTrue(lines.contains("[ThePredict] melee: DAMAGE, MELEE, DAMAGE_MEDIUM, ANSWER_DODGE"), lines.toString());
    }

    @Test
    void aRadiusAndAWindUpAreNamedWhenTheyDoNotFit() {
        var lines = text(formatter.format(List.of("SUGGEST", "slam_ai"), List.of(profile("slam_ai", slam()))));
        assertTrue(lines.contains("[ThePredict] slam: CONTROL, AREA_AROUND_SELF"), lines.toString());
        assertTrue(lines.contains("[ThePredict] slam: Radius 3 has no Intention token: add a radius targeter {r=3}"
                + " to the skill or keep Purpose in the profile"), lines.toString());
        assertTrue(lines.contains("[ThePredict] slam: WindUp 15 has no Intention token: the delay of the skill"
                + " supplies it, or keep Purpose in the profile"), lines.toString());
        assertTrue(lines.stream().noneMatch(line -> line.contains("Reach ")), lines.toString());
    }

    @Test
    void anUnknownProfileAndAMissingCatalogAreRefusals() {
        assertEquals(List.of("[ThePredict] No bound profile 'missing'."),
                text(formatter.format(List.of("suggest", "missing"), List.of(profile("guardian_ai", untyped("poke"))))));
        assertEquals(List.of("[ThePredict] No bound profile."), text(formatter.format(List.of("suggest"), List.of())));
        assertEquals(List.of("[ThePredict] Unknown intention action 'write'. Usage: /thepredict intention suggest"
                + " [profile]"), text(formatter.format(List.of("write"), List.of())));
        assertEquals(List.of("[ThePredict] Usage: /thepredict intention suggest [profile]"),
                text(formatter.format(List.of(), List.of())));
        assertEquals(List.of("[ThePredict] Usage: /thepredict intention suggest [profile]"),
                text(formatter.format(List.of("suggest", "guardian_ai", "extra"), List.of())));
    }

    @Test
    void profilesAreListedInIdOrderAndCompletedById() {
        var later = profile("zeta_ai", untyped("poke"));
        var earlier = profile("alpha_ai", untyped("jab"));
        var lines = text(formatter.format(List.of("suggest"), List.of(later, earlier)));
        assertEquals("[ThePredict] alpha_ai", lines.getFirst());
        assertTrue(lines.indexOf("[ThePredict] alpha_ai") < lines.indexOf("[ThePredict] zeta_ai"));
        assertEquals(List.of("alpha_ai", "zeta_ai"), formatter.profileIds(List.of(later, earlier)));
    }

    @Test
    void theCommandPrintsTheSuggestionAndDoesNotWriteAFile() throws Exception {
        var messages = new ArrayList<String>();
        var sender = (CommandSender) Proxy.newProxyInstance(ConsoleCommandSender.class.getClassLoader(),
                new Class<?>[] {ConsoleCommandSender.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("sendMessage") && arguments[0] instanceof Component line) {
                        messages.add(PlainTextComponentSerializer.plainText().serialize(line));
                        return null;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        var command = new ThePredictCommand(AdminFixtures::setup, unavailable(), DuelRecording.UNAVAILABLE,
                name -> Optional.empty(), () -> List.of(profile("guardian_ai", melee(12, 0))));
        assertTrue(command.onCommand(sender, null, "thepredict", new String[] {"intention", "suggest"}));
        assertTrue(messages.contains("[ThePredict] melee: " + REACH), messages.toString());
        assertEquals(List.of("guardian_ai"), command.onTabComplete(sender, null, "thepredict",
                new String[] {"intention", "suggest", "gu"}));
        for (var source : List.of(
                Path.of("src/main/java/akyrion/thepredict/admin/IntentionFormatter.java"),
                Path.of("src/main/java/akyrion/thepredict/admin/ThePredictCommand.java"))) {
            var text = Files.readString(source);
            assertFalse(text.contains("Files.write") || text.contains("FileWriter") || text.contains("FileOutputStream")
                    || text.contains("newBufferedWriter"), source.toString());
        }
    }

    private static List<String> text(List<Component> lines) {
        return lines.stream().map(line -> PlainTextComponentSerializer.plainText().serialize(line)).toList();
    }

    private static CommandServices unavailable() {
        return (CommandServices) Proxy.newProxyInstance(CommandServices.class.getClassLoader(),
                new Class<?>[] {CommandServices.class}, (proxy, method, arguments) -> {
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private static SkillChoice melee(double reach, int windUpTicks) {
        return SkillChoice.typed(new ChoiceId("melee"), "GuardianMelee", Set.of(), true, 0,
                new SkillPurpose(SkillRole.DAMAGE, Set.of(SkillRole.DAMAGE), Delivery.MELEE, OptionalDouble.of(reach),
                        OptionalDouble.empty(), windUpTicks, CooldownClass.SHORT, DamageTier.MEDIUM, Set.of(), Set.of(),
                        false, PurposeOrigin.DECLARED));
    }

    private static SkillChoice slam() {
        return SkillChoice.typed(new ChoiceId("slam"), "GuardianSlam", Set.of(), false, 0,
                new SkillPurpose(SkillRole.CONTROL, Set.of(SkillRole.CONTROL), Delivery.AREA_AROUND_SELF,
                        OptionalDouble.empty(), OptionalDouble.of(3), 15, CooldownClass.MEDIUM, DamageTier.NONE,
                        Set.of(), Set.of(), false, PurposeOrigin.DECLARED));
    }

    private static SkillChoice untyped(String id) {
        return new SkillChoice(new ChoiceId(id), ChoiceKind.MYTHIC_SKILL, Optional.of("Skill_" + id), Set.of(), true,
                0);
    }

    private static TacticalProfile profile(String id, SkillChoice... choices) {
        var tactics = new LinkedHashMap<TacticId, Tactic>();
        var choiceMap = new LinkedHashMap<ChoiceId, SkillChoice>();
        var hold = new Tactic(new TacticId("hold_ground"), Movement.HOLD, Optional.empty(), Optional.empty(), 10);
        tactics.put(hold.id(), hold);
        for (var choice : choices) {
            choiceMap.put(choice.id(), choice);
            var tactic = new Tactic(new TacticId("use_" + choice.id().value()), Movement.APPROACH,
                    Optional.of(choice.id()), Optional.empty(), 20);
            tactics.put(tactic.id(), tactic);
        }
        return new TacticalProfile(id, "Guardian", "Win", tactics, choiceMap, List.of(), MovementSettings.DEFAULTS,
                new HealthThresholds(0.3, 0.6), new ThreatWeights(1, 1, 1, 0.5, 10), List.of(),
                new DecisionTiming(40, 20, 100, 0.5, 0.2, 2));
    }
}
