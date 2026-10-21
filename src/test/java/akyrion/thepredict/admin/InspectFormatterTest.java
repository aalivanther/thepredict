package akyrion.thepredict.admin;

import akyrion.thepredict.domain.ArmorBand;
import akyrion.thepredict.domain.GoalSummary;
import akyrion.thepredict.domain.HealthBand;
import akyrion.thepredict.domain.MovementProgress;
import akyrion.thepredict.domain.ParticipantAlias;
import akyrion.thepredict.domain.ParticipantFacts;
import akyrion.thepredict.domain.ParticipantKind;
import akyrion.thepredict.domain.ThreatTableState;
import akyrion.thepredict.engine.SessionReport;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InspectFormatterTest {
    private final InspectFormatter formatter = new InspectFormatter();

    @Test
    void anInspectionExplainsLevelIntentWhyGoalsMovementAndThreat() {
        var lines = AdminFixtures.plain(formatter.format(AdminFixtures.session(false,
                new ThreatTableState(true, 2, 35, 50))));
        var text = String.join("\n", lines);
        for (var expected : List.of("Mob 1a2b3c4d generation 3",
                "ThePredictGuardian, profile thepredict_guardian, ACTIVE, level JEV, encounter 5",
                "keep_distance_and_shoot (KEEP_DISTANCE, choice ranged), confidence 0.82, 17 ticks left, choice tried no",
                "alternatives keep_distance; contingencies IF_TARGET_IN_MELEE -> press_and_strike",
                "last fallback SERVICE_PAUSED; last cast NOT_ELIGIBLE; streaks low confidence 0, no progress 1",
                "installed for revision 3; 2 goals, running thepredict:hold",
                "movement goal running yes, path yes, path reaches target no, target 6.25 blocks",
                "table on, 2 entries, leader 35.00, total 50.00; added by ThePredict in the open windows 3.50",
                "Perception: health HEALTHY; target player_1 RANGED CLOSING, reachable yes, visible no, outside target"
                        + " reach no, flankable yes, grouped no; contexts [tracking_enemy]; eligible [keep_distance,"
                        + " keep_distance_and_shoot]; out of reach []; cues [NO_LINE_OF_SIGHT]",
                "Skills: melee MELEE reach 2.50 BOX_GAP wind-up 0 cooldown UNKNOWN inferred, ranged HITSCAN reach 16.00"
                        + " CENTRES wind-up 10 needs sight cooldown SHORT declared",
                "Target: player_1 health WOUNDED, armor HEAVY, threat rank 1, cues [RAISING_SHIELD]; perceived players 2;"
                        + " combat 14 s", "Events: MELEE_BLOCKED player_1 at tick 120",
                "Outcomes: JEV keep_distance_and_shoot dealt=0.12 taken=0.03 reached=yes casts=1 hits=1")) {
            assertTrue(text.contains(expected), expected + "\n" + text);
        }
        assertFalse(text.contains("[debug]"));
        assertFalse(text.contains(AdminFixtures.MOB.toString()), "the inspection shortens the UUID (I1)");
        assertEquals(13, lines.size());
    }

    @Test
    void aDebugIntentIsMarkedAndAMobWithoutThreatTableSaysTheEnrichedThreatIsInactive() {
        var text = String.join("\n", AdminFixtures.plain(formatter.format(AdminFixtures.session(true,
                ThreatTableState.DISABLED))));
        assertTrue(text.contains("choice tried no [debug]"), text);
        assertTrue(text.contains("no threat table (Modules.ThreatTable off): enriched threat inactive"), text);
    }

    @Test
    void theGoalListingShowsTheFullUuidOneGoalPerLineAndTheProgress() {
        var goals = new ArrayList<GoalSummary>();
        for (var index = 0; index < 40; index++) {
            goals.add(new GoalSummary("minecraft:goal_" + index, Set.of("MOVE", "LOOK"), index == 3));
        }
        var lines = AdminFixtures.plain(formatter.goals(AdminFixtures.MOB, -1, goals,
                new MovementProgress(false, false, false, OptionalDouble.empty())));
        assertEquals(42, lines.size());
        assertTrue(lines.getFirst().contains(AdminFixtures.MOB + " (no session): 40"), lines.getFirst());
        assertEquals("[ThePredict]   minecraft:goal_3 [LOOK, MOVE] running", lines.get(4));
        assertTrue(lines.getLast().endsWith("target none"), lines.getLast());
    }

    @Test
    void anEntityTargetIsShownWithItsSpecies() {
        var session = AdminFixtures.session(false, ThreatTableState.DISABLED);
        var zombie = new ParticipantFacts(new ParticipantAlias(ParticipantKind.ENTITY, 1), HealthBand.WOUNDED,
                ArmorBand.LIGHT, Set.of(), 1, Optional.of("zombie"));
        var report = new SessionReport(session.key(), session.mythicMobId(), session.profileId(), session.state(),
                session.level(), session.encounter(), session.intent(), session.lowConfidenceStreak(),
                session.noProgressStreak(), session.installPending(), session.installedRevision(), session.goals(),
                session.progress(), session.perception(), Optional.of(zombie), session.perceivedPlayers(),
                session.recentEvents(), session.combatSeconds(), session.threatTable(),
                session.threatAddedInWindowPoints(), session.lastFallbackReason(), session.lastOutcome(),
                session.purposes(), session.outcomes());
        var text = String.join("\n", AdminFixtures.plain(formatter.format(report)));
        assertTrue(text.contains("Target: entity_1 (zombie) health WOUNDED, armor LIGHT"), text);
    }
}
