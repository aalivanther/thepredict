package akyrion.thepredict.adapter.server.binding;

import akyrion.thepredict.config.ProfileLoader;
import akyrion.thepredict.domain.ChoiceId;
import akyrion.thepredict.domain.ChoiceKind;
import akyrion.thepredict.domain.ContextId;
import akyrion.thepredict.domain.CooldownClass;
import akyrion.thepredict.domain.PredictValue;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.TacticalProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BoundProfileTest {
    @TempDir
    Path directory;

    @Test
    void removingNoContextReturnsTheSameBinding() throws Exception {
        var bound = bound();

        assertSame(bound, bound.withoutContexts(Set.of()));
    }

    @Test
    void theHandlersOfTheMobSurviveEveryCopyAndDefaultToNone() throws Exception {
        var bound = bound();
        assertSame(MobCueHandlers.NONE, bound.cueHandlers(), "a mob without ~onSignal lines");
        var handlers = MobCueHandlers.read(List.of("sound{s=x} @self ~onSignal:PREDICTOR_TELL_BLOCK"));
        var listening = new BoundProfile(bound.profile(), bound.skills(), bound.conditions(), bound.entityType(),
                bound.brainSpecies(), bound.participants(), bound.declaredPreset(), bound.declaredEntropy(),
                bound.declaredTraining(), handlers);
        assertSame(handlers, listening.withoutContexts(Set.of(new ContextId("wounded"))).cueHandlers());
        assertSame(handlers, listening.withProfile(bound.profile().withContexts(List.of())).cueHandlers());
    }

    @Test
    void removedContextsDisappearFromTheProfileAndTheConditions() throws Exception {
        var reduced = bound().withoutContexts(Set.of(new ContextId("wounded")));

        assertEquals(List.of(new ContextId("idle")), reduced.profile().contexts().stream().map(context -> context.id()).toList());
        assertEquals(Map.of(), reduced.conditions());
        assertEquals(bound().profile().tactics(), reduced.profile().tactics());
    }

    @Test
    void everyBoundChoiceHasAResolvedPurpose() throws Exception {
        var profile = bound().profile();
        var untyped = new SkillChoice(new ChoiceId("strike"), ChoiceKind.MYTHIC_SKILL, Optional.of("Strike"),
                Set.of(), true, 0);
        var skill = MythicFixtures.skill("Strike", Map.of());
        new BoundProfile(with(profile, untyped), Map.of(untyped.id(), skill), Map.of(), "ZOMBIE", false);
        var missing = new SkillChoice(new ChoiceId("strike"), ChoiceKind.MYTHIC_SKILL, Optional.of("Strike"),
                Set.of(PredictValue.MELEE_ATTACK), true, 0);
        assertEquals("skill choice strike has no loaded skill", assertThrows(IllegalArgumentException.class,
                () -> new BoundProfile(with(profile, missing), Map.of(), Map.of(), "ZOMBIE", false)).getMessage());
        var unresolved = new SkillChoice(new ChoiceId("punch"), ChoiceKind.VANILLA_MELEE, Optional.empty(), Set.of(),
                true, 0);
        assertEquals("choice punch has cooldown class UNKNOWN instead of SHORT", assertThrows(
                IllegalArgumentException.class, () -> new BoundProfile(with(profile, unresolved), Map.of(), Map.of(),
                        "ZOMBIE", false)).getMessage());
        new BoundProfile(with(profile, missing), Map.of(missing.id(), skill), Map.of(), "ZOMBIE", false);
        new BoundProfile(with(profile, unresolved.withPurpose(unresolved.typedPurpose().withCooldown(CooldownClass.SHORT))),
                Map.of(), Map.of(), "ZOMBIE", false);
    }

    private static TacticalProfile with(TacticalProfile profile, SkillChoice choice) {
        return new TacticalProfile(profile.id(), profile.mythicMobId(), profile.objective(), profile.tactics(),
                Map.of(choice.id(), choice), profile.contexts(), profile.movement(), profile.health(), profile.threat(),
                profile.baseAi(), profile.timing());
    }

    private BoundProfile bound() throws Exception {
        Files.writeString(directory.resolve("guardian.yml"), """
                schemaVersion: 2
                guardian_ai:
                  MythicID: Guardian
                  Objective: Survive
                  Tactics:
                    hold: { Movement: HOLD }
                  Contexts:
                    wounded: { Conditions: ['healthpercent{p=<30%}'], Tactics: [hold] }
                    idle: { Priority: 10, Tactics: [hold] }
                """);
        var profiles = new ProfileLoader().load(directory).profiles();
        var definitions = MythicFixtures.definitions().add(MythicFixtures.mob("Guardian", "ZOMBIE", Map.of("Predictor", true)));
        return new MythicProfileBinder(definitions).bind(profiles).profiles().get("Guardian");
    }
}
