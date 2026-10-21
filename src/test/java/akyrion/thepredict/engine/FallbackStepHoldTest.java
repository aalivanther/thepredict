package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ChoiceId;
import akyrion.thepredict.domain.ChoiceKind;
import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.PredictValue;
import akyrion.thepredict.domain.ServiceState;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.Tactic;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TacticalProfile;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The minimum hold keeps the current local tactic, not an idle mob (EFO-05): while readiness prefers another tactic
 * that the hold does not let in yet, the skill of the current tactic still fires at its own cooldown. Untyped skills
 * and {@code INTENT_ONLY}, so no {@code ANY_READY} candidate can fill the gap.
 */
class FallbackStepHoldTest {
    private static final TacticId QUICK = new TacticId("quick_cuts");
    private static final TacticId HEAVY = new TacticId("heavy_blow");
    private static final ChoiceId SLASH = new ChoiceId("slash");
    private static final ChoiceId CRUSH = new ChoiceId("crush");
    private static final int SLASH_COOLDOWN = 10;
    private static final int CRUSH_COOLDOWN = 30;

    private static TacticalProfile profile() {
        var base = ExecutionFixture.profile(ExecutionFixture.timing(1), 0);
        var tactics = new LinkedHashMap<TacticId, Tactic>();
        tactics.put(QUICK, new Tactic(QUICK, Movement.APPROACH, Optional.of(SLASH), Optional.empty(), 50));
        tactics.put(HEAVY, new Tactic(HEAVY, Movement.APPROACH, Optional.of(CRUSH), Optional.empty(), 80));
        var choices = new LinkedHashMap<ChoiceId, SkillChoice>();
        choices.put(SLASH, new SkillChoice(SLASH, ChoiceKind.MYTHIC_SKILL, Optional.of("TPV_Slash"),
                Set.of(PredictValue.MELEE_ATTACK), true, 0));
        choices.put(CRUSH, new SkillChoice(CRUSH, ChoiceKind.MYTHIC_SKILL, Optional.of("TPV_Crush"),
                Set.of(PredictValue.MELEE_ATTACK), true, 0));
        return new TacticalProfile(base.id(), base.mythicMobId(), base.objective(), tactics, choices, base.contexts(),
                base.movement(), base.health(), base.threat(), base.baseAi(), base.timing(), base.participants());
    }

    @Test
    void theHeldTacticCastsItsSkillAgainOnceItsCooldownPasses() {
        var fixture = ExecutionFixture.starting(ServiceState.UNCONFIGURED);
        fixture.captureCooldowns();
        fixture.actions.cooldowns.set(SLASH, SLASH_COOLDOWN);
        fixture.actions.cooldowns.set(CRUSH, CRUSH_COOLDOWN);
        var session = fixture.open(1, profile());
        fixture.target(1, 1);
        fixture.reader.eligible.put(EngineFixtures.id(1), Set.of(QUICK, HEAVY));
        var hold = session.profile().timing().minimumDecisionIntervalTicks();
        var runs = new ArrayList<int[]>();
        TacticId current = null;
        for (var tick = 0; tick < 400; tick++) {
            var before = fixture.actions.castTicks(1).size();
            fixture.run(1);
            var tactic = session.intent().filter(intent -> intent.level() == ControlLevel.LOCAL)
                    .map(intent -> intent.tactic()).orElse(null);
            var cast = fixture.actions.castTicks(1).size() - before;
            if (runs.isEmpty() || !Objects.equals(tactic, current)) {
                runs.add(new int[] {tactic == null ? -1 : tactic.equals(QUICK) ? 0 : 1, 0, 0});
                current = tactic;
            }
            var run = runs.getLast();
            run[1]++;
            run[2] += cast;
        }
        var heldQuick = runs.subList(0, runs.size() - 1).stream().filter(run -> run[0] == 0 && run[1] >= hold)
                .toList();
        assertTrue(heldQuick.size() >= 4, "the two tactics take turns: " + describe(runs));
        for (var run : heldQuick) {
            assertTrue(run[2] >= hold / SLASH_COOLDOWN, "a held tactic casts at its own cooldown: " + describe(runs));
        }
        for (var run : runs.subList(1, runs.size() - 1)) {
            assertTrue(run[0] < 0 || run[1] >= hold, "readiness never switches faster than the hold: "
                    + describe(runs));
        }
        assertEquals(ControlLevel.LOCAL, fixture.view(1).level());
    }

    private static String describe(List<int[]> runs) {
        var text = new StringBuilder();
        for (var run : runs) {
            text.append(run[0] < 0 ? "-" : run[0] == 0 ? "Q" : "H").append(run[1]).append('/').append(run[2])
                    .append(' ');
        }
        return text.toString();
    }
}
