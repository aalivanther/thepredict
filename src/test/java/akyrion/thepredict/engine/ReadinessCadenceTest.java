package akyrion.thepredict.engine;

import akyrion.thepredict.domain.Answer;
import akyrion.thepredict.domain.ChoiceId;
import akyrion.thepredict.domain.ChoiceKind;
import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.PredictValue;
import akyrion.thepredict.domain.QuestionSpec;
import akyrion.thepredict.domain.ServiceState;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.Tactic;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.domain.TargetCue;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static akyrion.thepredict.engine.ExecutionFixture.FALL_BACK;
import static akyrion.thepredict.engine.ExecutionFixture.GUARD_BREAK;
import static akyrion.thepredict.engine.ExecutionFixture.PRESS;
import static akyrion.thepredict.engine.ExecutionFixture.STRIKE;
import static akyrion.thepredict.engine.ExecutionFixture.nowAndContingency;
import static akyrion.thepredict.engine.ExecutionFixture.prefer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real-time readiness of M10 (user decision of 2026-09-30, DT-28 revised): a skill fires at its own native
 * {@code Cooldown} in the local mode and with Jev renewing its tactic, at most once per epoch of its choice, and readiness
 * never switches the local tactic faster than the minimum hold.
 */
class ReadinessCadenceTest {
    private static final Function<QuestionSpec, Answer> PRESS_ON =
            nowAndContingency(prefer(PRESS, 0.9, 0.9), prefer(FALL_BACK, 0.7, 0.9));
    private static final int COOLDOWN = 30;
    /** A cooldown shorter than the life of a local intent (20 ticks in the fixture). */
    private static final int SHORT_COOLDOWN = 14;
    /** M19.4 asks for the native cooldown exactly; the reactive cycle is no longer a tolerance (EFO-28). */
    private static final int TOLERANCE = 0;
    private static final int MINIMUM_HOLD = 20;
    private static final int RUN_TICKS = 400;
    private static final ChoiceId SWING = new ChoiceId("swing");

    @Test
    void aLocalSkillWithAThirtyTickCooldownFiresEveryThirtyTicks() {
        localCadence(COOLDOWN);
    }

    @Test
    void aLocalSkillWithAnOddCooldownFiresOnItsExactTickToo() {
        localCadence(COOLDOWN + 1);
    }

    /**
     * PZ-11: entropy paces a change of tactic, not the re-arm of the same one, so it never reacts more slowly than the
     * normal mode (E22-4). The cooldown is shorter than the life of a local intent, so the choice comes back by its
     * re-arm and not by a new local intent.
     */
    @Test
    void underEntropyALocalSkillFiresAsOftenAsInTheNormalMode() {
        var normal = shortCooldown(false);
        var entropy = shortCooldown(true);
        assertTrue(SHORT_COOLDOWN < FallbackStep.localIntentTicks(entropy.session(1).profile().timing()));
        assertTrue(entropy.session(1).difficulty().entropy(), "the mob plays with entropy against its player");
        assertCadence(entropy.actions.castTicks(1), SHORT_COOLDOWN);
        assertEquals(normal.actions.castTicks(1), entropy.actions.castTicks(1),
                "the same casts on the same ticks as without entropy");
        assertEquals(ControlLevel.LOCAL, entropy.view(1).level());
    }

    private static ExecutionFixture shortCooldown(boolean entropy) {
        var fixture = local();
        if (entropy) {
            fixture.reader.players.add(EngineFixtures.player(1));
            fixture.reader.entropic.add(EngineFixtures.id(1));
        }
        fixture.actions.cooldowns.set(STRIKE, SHORT_COOLDOWN);
        fixture.run(RUN_TICKS);
        return fixture;
    }

    @Test
    void aSkillThatJevRenewsWithAThirtyTickCooldownFiresEveryThirtyTicks() {
        jevCadence(COOLDOWN);
    }

    @Test
    void aSkillThatJevRenewsWithAnOddCooldownFiresOnItsExactTickToo() {
        jevCadence(COOLDOWN + 1);
    }

    @Test
    void noEpochCastsTwiceInTheLocalModeOrWithJev() {
        var local = local();
        local.actions.cooldowns.setDefault(0);
        var perLocalEpoch = new HashMap<String, Long>();
        for (var tick = 0; tick < 300; tick++) {
            var before = local.actions.castTicks(1).size();
            local.run(1);
            perLocalEpoch.merge(epoch(local), (long) local.actions.castTicks(1).size() - before, Long::sum);
        }
        var jev = new ExecutionFixture();
        jev.actions.cooldowns.setDefault(0);
        engage(jev);
        jev.answer(0, PRESS_ON);
        var perJevEpoch = new HashMap<String, Long>();
        for (var tick = 0; tick < 300; tick++) {
            var before = jev.actions.castTicks(1).size();
            jev.runAnswering(1, PRESS_ON);
            perJevEpoch.merge(epoch(jev), (long) jev.actions.castTicks(1).size() - before, Long::sum);
        }
        assertTrue(perLocalEpoch.values().stream().allMatch(count -> count <= 1), perLocalEpoch.toString());
        assertTrue(perJevEpoch.values().stream().allMatch(count -> count <= 1), perJevEpoch.toString());
        assertTrue(local.actions.castTicks(1).size() > 100, "a skill without a cooldown may fire every reactive cycle");
        assertTrue(jev.actions.castTicks(1).size() > 100, "a skill without a cooldown may fire every reactive cycle");
    }

    @Test
    void anAnswerAloneNeverRecastsAConsumedChoice() {
        var fixture = new ExecutionFixture();
        fixture.actions.cooldowns.set(STRIKE, 200);
        engage(fixture);
        fixture.answer(0, PRESS_ON);
        fixture.run(1);
        assertEquals(1, fixture.actions.castTicks(1).size());
        fixture.runAnswering(150, PRESS_ON);
        assertTrue(fixture.client.requests.size() >= 5, "several answers renewed the tactic");
        assertEquals(1, fixture.actions.castTicks(1).size(), "no answer re-arms a choice whose skill is cooling down");
        assertEquals(ControlLevel.JEV, fixture.view(1).level());
        fixture.runAnswering(60, PRESS_ON);
        assertEquals(2, fixture.actions.castTicks(1).size(), "the skill fires again once its cooldown has passed");
    }

    @Test
    void readinessNeverSwitchesTheLocalTacticFasterThanTheMinimumHold() {
        var fixture = ExecutionFixture.starting(ServiceState.UNCONFIGURED);
        fixture.captureCooldowns();
        fixture.actions.cooldowns.set(STRIKE, COOLDOWN);
        fixture.actions.cooldowns.set(GUARD_BREAK, COOLDOWN);
        fixture.open(1, ExecutionFixture.shieldProfile());
        fixture.target(1, 1);
        fixture.reader.eligible.put(EngineFixtures.id(1), Set.of(ExecutionFixture.SHIELD_PRESS,
                ExecutionFixture.BREAK_GUARD));
        fixture.reader.cues.put(EngineFixtures.player(1), Set.of(TargetCue.BLOCKING));
        var switches = new ArrayList<Long>();
        for (var tick = 0; tick < RUN_TICKS; tick++) {
            var before = fixture.actions.signals(1).size();
            fixture.run(1);
            if (fixture.actions.signals(1).size() > before) {
                switches.add(fixture.clock.tick - 1);
            }
        }
        assertTrue(switches.size() >= 4, "both skills are used in turn: " + switches);
        for (var index = 1; index < switches.size(); index++) {
            assertTrue(switches.get(index) - switches.get(index - 1) >= MINIMUM_HOLD, switches.toString());
        }
        assertTrue(fixture.actions.castTicks(1).size() >= RUN_TICKS / MINIMUM_HOLD,
                "each hold casts its skill once, as both cool down in less than two holds: "
                        + fixture.actions.castTicks(1));
    }

    @Test
    void aSkillCoolingDownNeverMakesTheLocalTacticFlap() {
        var fixture = local();
        fixture.actions.cooldowns.set(STRIKE, 90);
        fixture.run(RUN_TICKS);
        assertEquals(List.of(PRESS.value()), fixture.actions.signals(1));
        assertEquals(ControlLevel.LOCAL, fixture.view(1).level());
        assertCadence(fixture.actions.castTicks(1), 90);
    }

    @Test
    void aVanillaHitKeepsTheVanillaAttackRhythmInTheLocalModeAndWithJev() {
        var local = ExecutionFixture.starting(ServiceState.UNCONFIGURED);
        local.captureCooldowns();
        local.open(1, punching());
        local.target(1, 1);
        local.run(300);
        assertTrue(local.actions.castTicks(1).size() >= 12, local.actions.castTicks(1).toString());
        assertCadence(local.actions.castTicks(1), IntentControl.VANILLA_MELEE_INTERVAL_TICKS);
        var jev = new ExecutionFixture();
        jev.ready();
        jev.open(1, punching());
        jev.target(1, 1);
        jev.runUntilRequest(1);
        jev.answer(0, PRESS_ON);
        jev.runAnswering(300, PRESS_ON);
        assertEquals(ControlLevel.JEV, jev.view(1).level());
        assertTrue(jev.actions.castTicks(1).size() >= 12, jev.actions.castTicks(1).toString());
        assertCadence(jev.actions.castTicks(1), IntentControl.VANILLA_MELEE_INTERVAL_TICKS);
    }

    /** The profile of the fixture whose {@code press_attack} throws a vanilla punch instead of a skill. */
    private static TacticalProfile punching() {
        var profile = ExecutionFixture.profile(ExecutionFixture.timing(1), 0);
        var tactics = new LinkedHashMap<>(profile.tactics());
        tactics.put(PRESS, new Tactic(PRESS, Movement.APPROACH, Optional.of(SWING), Optional.empty(), 50));
        var swing = new SkillChoice(SWING, ChoiceKind.VANILLA_MELEE, Optional.empty(),
                Set.of(PredictValue.MELEE_ATTACK), true, 0);
        return new TacticalProfile(profile.id(), profile.mythicMobId(), profile.objective(), tactics,
                Map.of(SWING, swing), profile.contexts(), profile.movement(), profile.health(), profile.threat(),
                profile.baseAi(), profile.timing(), profile.participants());
    }

    private static ExecutionFixture local() {
        var fixture = ExecutionFixture.starting(ServiceState.UNCONFIGURED);
        fixture.captureCooldowns();
        fixture.open(1);
        fixture.target(1, 1);
        return fixture;
    }

    /** A local skill with {@code cooldown} fires on every expiry of it and keeps the local tactic. */
    private static void localCadence(int cooldown) {
        var fixture = local();
        fixture.actions.cooldowns.set(STRIKE, cooldown);
        fixture.run(RUN_TICKS);
        var casts = fixture.actions.castTicks(1);
        assertTrue(casts.size() >= RUN_TICKS / cooldown - 1, casts.toString());
        assertCadence(casts, cooldown);
        assertEquals(ControlLevel.LOCAL, fixture.view(1).level());
        assertEquals(List.of(PRESS.value()), fixture.actions.signals(1),
                "the cooldown never hands the local tactic to a filler and back");
    }

    /** A skill that Jev renews with {@code cooldown} fires on every expiry of it and sends no new signal. */
    private static void jevCadence(int cooldown) {
        var fixture = new ExecutionFixture();
        fixture.actions.cooldowns.set(STRIKE, cooldown);
        engage(fixture);
        fixture.answer(0, PRESS_ON);
        fixture.run(1);
        var start = fixture.session(1).intent().orElseThrow().startTick();
        fixture.runAnswering(RUN_TICKS, PRESS_ON);
        var casts = fixture.actions.castTicks(1);
        assertTrue(casts.size() >= RUN_TICKS / cooldown - 1, casts.toString());
        assertCadence(casts, cooldown);
        assertEquals(ControlLevel.JEV, fixture.view(1).level());
        assertEquals(start, fixture.session(1).intent().orElseThrow().startTick(), "hysteresis and hold untouched");
        assertEquals(List.of(PRESS.value()), fixture.actions.signals(1), "a re-armed choice sends no new signal");
    }

    private static void engage(ExecutionFixture fixture) {
        fixture.ready();
        fixture.open(1);
        fixture.target(1, 1);
        fixture.runUntilRequest(1);
    }

    private static String epoch(ExecutionFixture fixture) {
        var session = fixture.session(1);
        return session.intent().map(intent -> intent.tactic().value() + "/" + intent.startTick()).orElse("-") + "/"
                + session.choiceEpoch();
    }

    private static void assertCadence(List<Long> casts) {
        assertCadence(casts, COOLDOWN);
    }

    private static void assertCadence(List<Long> casts, int cooldown) {
        assertTrue(casts.size() >= 2, casts.toString());
        for (var index = 1; index < casts.size(); index++) {
            var gap = casts.get(index) - casts.get(index - 1);
            assertTrue(gap >= cooldown && gap <= cooldown + TOLERANCE, "gap " + gap + " in " + casts);
        }
    }
}
