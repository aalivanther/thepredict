package akyrion.thepredict.engine;

import akyrion.thepredict.domain.AttackPool;
import akyrion.thepredict.domain.BenchPolicy;
import akyrion.thepredict.domain.ChoiceId;
import akyrion.thepredict.domain.CombatFacts;
import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.CoordinationPolicy;
import akyrion.thepredict.domain.CueRequest;
import akyrion.thepredict.domain.DangerClass;
import akyrion.thepredict.domain.Delivery;
import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.GroupBrief;
import akyrion.thepredict.domain.GroupCaps;
import akyrion.thepredict.domain.GroupFairnessReason;
import akyrion.thepredict.domain.GroupMode;
import akyrion.thepredict.domain.GroupPlan;
import akyrion.thepredict.domain.GroupQuestions;
import akyrion.thepredict.domain.GroupRelations;
import akyrion.thepredict.adapter.server.context.GroupRelationReader;
import akyrion.thepredict.domain.GroupRole;
import akyrion.thepredict.domain.GroupSettings;
import akyrion.thepredict.domain.HealthBand;
import akyrion.thepredict.domain.Hungarian;
import akyrion.thepredict.domain.ImpactForecast;
import akyrion.thepredict.domain.LeaderDeath;
import akyrion.thepredict.domain.MobFacts;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.PlanQuestion;
import akyrion.thepredict.domain.RegroupPolicy;
import akyrion.thepredict.domain.RoleCapabilities;
import akyrion.thepredict.domain.SharedTableAssignment;
import akyrion.thepredict.domain.SlotAssignment;
import akyrion.thepredict.domain.SlotGeometry;
import akyrion.thepredict.domain.SquadPolicy;
import akyrion.thepredict.domain.SurroundSlots;
import akyrion.thepredict.domain.TacticalIntent;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TelegraphEnd;
import akyrion.thepredict.domain.TellAnswer;
import akyrion.thepredict.domain.ZoneColumn;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Closes the M25 audit points that a unit can see without a server. */
class GroupCorrectionTest {
    @Test
    void closingATellFollowsTheEndAndEvictsOnlyOldPastImpacts() {
        var ledger = new AttackLedger();
        var tells = new TellLedger();
        var first = body(1);
        var second = body(2);
        ledger.bind(new MobSession[] {first, second}, new int[] {0, 1}, 2);
        assertTrue(ledger.grant(0, 0, AttackPool.MELEE, 5, 4, 1).isEmpty());
        ledger.lock(0, 0, AttackPool.MELEE);
        assertTrue(ledger.reserve(0, 0, 5, 5, 10, 6).reason().isEmpty());
        ledger.closeImpact(0, 0, TelegraphEnd.FEINTED, 5);
        tells.closeEnd(0, DangerClass.SEVERE, TelegraphEnd.FEINTED, 5, 5, 10);
        assertFalse(ledger.holding(0, 0, AttackPool.MELEE));
        assertTrue(ledger.reserve(0, 1, 5, 6, 10, 6).reason().isEmpty(), "a feint is not a past impact");

        assertTrue(ledger.grant(0, 0, AttackPool.MELEE, 20, 4, 1).isEmpty());
        ledger.lock(0, 0, AttackPool.MELEE);
        assertTrue(ledger.reserve(0, 0, 20, 20, 10, 6).reason().isEmpty());
        ledger.closeImpact(0, 0, TelegraphEnd.WHIFFED, 20);
        assertFalse(ledger.holding(0, 0, AttackPool.MELEE));
        assertEquals(GroupFairnessReason.IMPACT_GAP, ledger.reserve(0, 1, 20, 21, 10, 6).reason().orElseThrow());

        assertTrue(ledger.reserve(0, 0, 50, 50, 10, 6).reason().isEmpty());
        first.ledgerWaitUntilTick(0);
        second.ledgerWaitUntilTick(0);
        ledger.closeImpact(0, 0, TelegraphEnd.INTERRUPTED, 50);
        assertEquals(0, first.ledgerWaitUntilTick());
        assertEquals(50, second.ledgerWaitUntilTick());
        assertTrue(ledger.reserve(0, 1, 50, 50, 10, 6).reason().isEmpty(), "a no-impact end deletes the ring");
        tells.open(0, DangerClass.SEVERE, TellAnswer.DODGE, 70, false);
        tells.closeEnd(0, DangerClass.SEVERE, TelegraphEnd.INTERRUPTED, 70, 70, 10);
        assertTrue(tells.admit(0, DangerClass.MEDIUM, TellAnswer.DODGE, 70, 70, 10, 2, true, false).isEmpty());
        tells.open(0, DangerClass.SEVERE, TellAnswer.DODGE, 90, false);
        tells.closeEnd(0, DangerClass.SEVERE, TelegraphEnd.WHIFFED, 90, 90, 10);
        assertEquals(GroupFairnessReason.BIG_IMPACT_SPACING,
                tells.admit(0, DangerClass.MEDIUM, TellAnswer.DODGE, 95, 90, 10, 2, true, false).orElseThrow());
        tells.open(0, DangerClass.SEVERE, TellAnswer.DODGE, 120, false);
        tells.closeEnd(0, DangerClass.SEVERE, TelegraphEnd.FEINTED, 120, 120, 10);
        assertTrue(tells.admit(0, DangerClass.SEVERE, TellAnswer.DODGE, 120, 120, 10, 2, true, false).isEmpty());

        assertTrue(ledger.grant(0, 0, AttackPool.MELEE, 40, 4, 1).isEmpty());
        ledger.lock(0, 0, AttackPool.MELEE);
        ledger.reserve(0, 0, 40, 40, 10, 6);
        ledger.closeImpact(0, 0, TelegraphEnd.RELEASED, 40);
        assertFalse(ledger.holding(0, 0, AttackPool.MELEE));
        assertEquals(GroupFairnessReason.IMPACT_GAP, ledger.reserve(0, 1, 40, 41, 10, 6).reason().orElseThrow());

        var live = new AttackLedger();
        assertTrue(live.reserve(0, 0, 5, 5, 10, 0).reason().isEmpty());
        assertEquals(GroupFairnessReason.IMPACT_GAP, live.reserve(0, 1, 5, 100, 10, 6).reason().orElseThrow());
        var past = new AttackLedger();
        assertTrue(past.reserve(0, 0, 5, 5, 10, 0).reason().isEmpty());
        past.closeImpact(0, 0, TelegraphEnd.WHIFFED, 5);
        assertTrue(past.reserve(0, 1, 5, 100, 10, 6).reason().isEmpty());
    }

    @Test
    void flightIsCeilAndAFreeBenchRecordsWithoutDenying() {
        assertEquals(3, ImpactForecast.at(0, 0, Delivery.PROJECTILE, 9, 3, 0).tick());
        assertEquals(4, ImpactForecast.at(0, 0, Delivery.PROJECTILE, 9.1, 3, 0).tick());
        assertEquals(10, ImpactForecast.at(10, 0, Delivery.MELEE, 9, 3, 0).tick());
        assertThrows(NullPointerException.class, () -> ImpactForecast.at(0, 0, null, 1, 1, 0));
        var ledger = new AttackLedger();
        assertTrue(ledger.reserve(0, 0, 10, 0, 10, 6, 2).reason().isEmpty());
        assertEquals(GroupFairnessReason.IMPACT_GAP, ledger.reserve(0, 1, 19, 0, 10, 6, 2).reason().orElseThrow());
        assertTrue(ledger.reserve(0, 1, 20, 0, 10, 6, 2).reason().isEmpty());

        var session = body(3);
        session.playerTarget(false);
        session.recordTarget(EngineFixtures.player(1), 9);
        session.groupCopy().sight(0);
        var attack = new AttackLedger();
        session.groupBooks(new GroupBooks(attack, new TellLedger(), GroupCaps.DEFAULT, GroupMode.FAIR, 0, 0,
                BenchPolicy.FREE, false, false, 2, 6, false));
        assertTrue(GroupCastGate.admit(session, projectile(9), 0, false).isEmpty());
        assertEquals(9.0, session.projectileBlocksPerTick(), 0.0);
        assertFalse(session.projectileFlightAssumed());
        assertEquals(1L, session.groupTellImpact());
        assertEquals(GroupFairnessReason.IMPACT_GAP, attack.reserve(0, 1, 1, 0, 10, 6, 0).reason().orElseThrow());
        assertTrue(GroupCastGate.admit(session, projectile(0), 30, false).isEmpty());
        assertEquals(0.0, session.projectileBlocksPerTick(), 0.0);
        assertTrue(session.projectileFlightAssumed());
        assertEquals(33L, session.groupTellImpact());
        assertEquals(GroupFairnessReason.IMPACT_GAP, attack.reserve(0, 1, 33, 30, 10, 6, 0).reason().orElseThrow());
    }

    @Test
    void anUntoldCastMarksPastAndAMythicRejectionDeletesThatTickOnly() {
        var session = body(9);
        session.playerTarget(true);
        session.recordTarget(EngineFixtures.player(1), 4);
        session.groupCopy().sight(1);
        var attack = new AttackLedger();
        session.groupBooks(new GroupBooks(attack, new TellLedger(), GroupCaps.DEFAULT, GroupMode.FAIR, 0, 0,
                BenchPolicy.FAIR, false, false, 4, 6, false));
        var choice = medium();
        assertTrue(GroupCastGate.admit(session, choice, 1, false).isEmpty());
        assertTrue(attack.reserve(0, 0, 80, 1, 10, 0).reason().isEmpty());
        GroupTellClose.untold(session, akyrion.thepredict.domain.ExecutionOutcome.CAST, 1);
        assertEquals(2, attack.size(0));
        attack.closeImpact(0, 0, TelegraphEnd.FEINTED, 1, session.groupTellImpact());
        assertEquals(2, attack.size(0), "a landed untold cast stays past");
        assertTrue(GroupCastGate.admit(session, choice, 12, false).isEmpty());
        GroupTellClose.untold(session, akyrion.thepredict.domain.ExecutionOutcome.REJECTED_BY_MYTHICMOBS, 12);
        assertEquals(2, attack.size(0));
        var heard = body(10);
        heard.playerTarget(true);
        heard.recordTarget(EngineFixtures.player(1), 4);
        heard.groupCopy().sight(0);
        heard.groupBooks(new GroupBooks(new AttackLedger(), new TellLedger(), GroupCaps.DEFAULT, GroupMode.FAIR, 0, 0,
                BenchPolicy.FAIR, false, false, 4, 6, false));
        assertEquals(GroupFairnessReason.SECOND_HAND, GroupCastGate.admit(heard, choice, 1, false).orElseThrow());
        assertEquals(0, heard.groupBooks().attack().size(0));
        var entropy = body(11);
        entropy.playerTarget(true);
        entropy.recordTarget(EngineFixtures.player(1), 4);
        entropy.groupCopy().sight(0);
        var entropyLedger = new AttackLedger();
        entropy.groupBooks(new GroupBooks(entropyLedger, new TellLedger(), GroupCaps.DEFAULT, GroupMode.FAIR, 0, 0,
                BenchPolicy.FAIR, true, false, 4, 6, false));
        assertTrue(GroupCastGate.admit(entropy, choice, 1, false).isEmpty());
        assertEquals(1, entropyLedger.size(0));
    }

    @Test
    void playerIndexesAreStableAndALeaveCancelsOnlyUnlockedTokens() {
        var world = new Lab(GroupMode.FAIR);
        var left = world.member(1, 10, EngineFixtures.player(1));
        var right = world.member(2, 1, EngineFixtures.player(1));
        world.step(0);
        assertEquals(0, left.groupBooks().playerIndex());
        assertEquals(0, right.groupBooks().playerIndex());
        right.recordTarget(EngineFixtures.player(2), 4);
        world.step(1);
        assertEquals(0, left.groupBooks().playerIndex());
        assertEquals(1, right.groupBooks().playerIndex());
        right.loseTarget();
        world.step(2);
        assertEquals(-1, right.groupBooks().playerIndex());
        var attack = left.groupBooks().attack();
        var leftSeat = left.groupBooks().memberIndex();
        var rightSeat = right.groupBooks().memberIndex();
        assertTrue(attack.grant(0, leftSeat, AttackPool.MELEE, 2, 4, 1).isEmpty());
        attack.lock(0, leftSeat, AttackPool.MELEE);
        assertTrue(attack.grant(0, rightSeat, AttackPool.MELEE, 2, 4, 1).isEmpty());
        world.registry.leave(right);
        world.step(3);
        assertTrue(attack.holding(0, leftSeat, AttackPool.MELEE));
        assertFalse(attack.holding(0, rightSeat, AttackPool.MELEE));
    }

    @Test
    void aClearSightBitRefusesMediumAndAllowsLight() {
        var world = new Lab(GroupMode.FAIR);
        var caster = world.member(1, 10, EngineFixtures.player(1));
        world.member(2, 1, EngineFixtures.player(1));
        world.step(0);
        caster.playerTarget(true);
        caster.facts(new MobFacts(caster.key(), HealthBand.HEALTHY, Optional.of(relation(false)), Set.of(), Set.of(),
                Set.of(), Set.of()));
        world.step(1);
        assertFalse(caster.groupCopy().seesFirstHand());
        assertEquals(GroupFairnessReason.SECOND_HAND, GroupCastGate.admit(caster, medium(), 1, true).orElseThrow());
        assertTrue(GroupCastGate.admit(caster, light(), 30, false).isEmpty());
        caster.facts(new MobFacts(caster.key(), HealthBand.HEALTHY, Optional.of(relation(true)), Set.of(), Set.of(),
                Set.of(), Set.of()));
        world.step(2);
        assertTrue(caster.groupCopy().seesFirstHand());
        assertTrue(GroupCastGate.admit(caster, medium(), 40, true).isEmpty());
    }

    @Test
    void captureDepositsTheRealEpochAndG0UsesThatEpoch() {
        var sessions = new EngineFixtures.Sessions();
        var metrics = new DecisionMetrics(sessions.clock::currentTick);
        var director = new GroupDirector(sessions.encounters, sessions.clock, metrics, caps(GroupMode.FULL),
                sessions.agenda);
        var leader = sessions.registry.open(EngineFixtures.id(1), profile(80, GroupMode.FULL));
        var ally = sessions.registry.open(EngineFixtures.id(2), profile(10, GroupMode.FULL));
        leader.recordTarget(EngineFixtures.player(1), 4);
        ally.recordTarget(EngineFixtures.player(1), 4);
        sessions.encounters.assign(leader, EngineFixtures.WORLD, EngineFixtures.player(1), key -> true);
        sessions.encounters.assign(ally, EngineFixtures.WORLD, EngineFixtures.player(1), key -> true);
        sessions.registry.open(EngineFixtures.id(3), profile(1, GroupMode.FULL));
        director.step(0, 1_000_000_000L);
        var encounter = sessions.encounters.open().iterator().next();
        var third = sessions.registry.find(EngineFixtures.id(3)).orElseThrow();
        third.recordTarget(EngineFixtures.player(1), 4);
        sessions.encounters.assign(third, EngineFixtures.WORLD, EngineFixtures.player(1), key -> true);
        director.step(20, 1_000_000_000L);
        var epoch = leader.groupCopy().membershipEpoch();
        assertTrue(epoch > 1);
        var brief = director.brief(encounter.id());
        assertTrue(brief.asksPlan());
        assertEquals(epoch, brief.membershipEpoch());
        var capture = new EncounterCapture(reader(leader.key(), encounter.id()), sessions.registry,
                sessions.encounters, sessions.lifecycle, sessions.clock, metrics);
        capture.hearGroup(director);
        var facts = capture.capture(encounter.id(), Set.of()).orElseThrow();
        assertEquals(80.0, leader.tableTop());
        assertEquals(25.0, leader.tableOnTarget());
        assertTrue(leader.threatSampleCount() > 0);
        assertEquals(epoch, facts.brief().membershipEpoch());
        assertTrue(facts.brief().candidates().size() >= 2);
        assertTrue(facts.brief().candidates().contains(GroupPlan.PRESS_MAIN_TARGET.option()));
        assertTrue(facts.brief().asksPlan());
    }

    @Test
    void slotsUseHungarianValiditySeparationAndTheGuardLine() {
        assertEquals(180, SlotAssignment.guardAngle(180, 0, false));
        assertNotEquals(180, SlotAssignment.guardAngle(180, 90, false));
        assertTrue(SurroundSlots.inExitSector(180, 90));
        assertTrue(SlotAssignment.columnAllowed(null, 0, 0, 2));
        var open = ZoneColumn.pack(ZoneColumn.FLOOR, 64, 3, 0, 0);
        assertFalse(SlotAssignment.columnAllowed(view(1, open, (short) 1, (short) 8), 0, 0, 2));
        assertTrue(SlotAssignment.columnAllowed(view(1, open, (short) 8, (short) 8), 0, 0, 2));
        var valid = new boolean[48];
        for (var index = 0; index < valid.length; index++) {
            valid[index] = true;
        }
        var assigned = new int[2];
        var roles = new GroupRole[] {GroupRole.GUARD, GroupRole.PRESSURE};
        SlotAssignment.assign(roles, new int[] {-1, -1}, valid, 0, SlotAssignment.guardAngle(180, 90, false),
                new Hungarian(24), assigned);
        assertTrue(assigned[0] >= 0 && assigned[1] >= 0);
        assertNotEquals(assigned[0], assigned[1]);
        assertFalse(SlotGeometry.closerThanContact(assigned[0], assigned[1]));
    }

    @Test
    void arbitrationStealsOnlyAnUnlockedTokenPastTheMargin() {
        var world = new Lab(caps(GroupMode.COORDINATE, 1, 1, 1));
        var strong = world.member(1, 80, EngineFixtures.player(1), new RoleCapabilities(500, 0, 0, 0, 0, 400));
        var weak = world.member(2, 1, EngineFixtures.player(1), RoleCapabilities.NONE, listedSlam());
        world.encounter().offerGroupCapture(1, true, List.of(GroupPlan.PRESS_MAIN_TARGET, GroupPlan.SURROUND_TARGET));
        world.step(0);
        assertEquals(GroupRole.PRESSURE, strong.groupCopy().role().orElseThrow());
        var attack = strong.groupBooks().attack();
        var weakSeat = weak.groupBooks().memberIndex();
        assertTrue(attack.grant(0, weakSeat, AttackPool.MELEE, 0, 1, 1).isEmpty());
        attack.lock(0, weakSeat, AttackPool.MELEE);
        strong.groupCopy().askMelee();
        world.step(1);
        assertTrue(attack.holding(0, weakSeat, AttackPool.MELEE));
        assertFalse(attack.holding(0, strong.groupBooks().memberIndex(), AttackPool.MELEE));
        attack.closeImpact(0, weakSeat, TelegraphEnd.RELEASED, 1);
        assertTrue(attack.grant(0, weakSeat, AttackPool.MELEE, 1, 1, 1).isEmpty());
        strong.groupCopy().askMelee();
        world.step(2);
        assertFalse(attack.holding(0, weakSeat, AttackPool.MELEE));
        assertTrue(attack.holding(0, strong.groupBooks().memberIndex(), AttackPool.MELEE));
    }

    @Test
    void anEncounterThatFailsDropsToFairAndTheNextOneIsStillCoordinated() {
        var world = new Lab(GroupMode.COORDINATE);
        var failing = world.member(1, 90, EngineFixtures.player(1), new RoleCapabilities(500, 400, 0, 0, 0, 400));
        world.member(2, 1, EngineFixtures.player(1), RoleCapabilities.NONE);
        var other = world.member(3, 90, EngineFixtures.player(2), new RoleCapabilities(500, 400, 0, 0, 0, 400));
        world.member(4, 1, EngineFixtures.player(2), RoleCapabilities.NONE);
        assertEquals(2, world.registry.open().size(), "one encounter per player");
        var heard = new Heard();
        world.director.useCues(new Heard() {
            @Override
            public void cue(MobKey mob, CueRequest request) {
                if (mob.equals(failing.key())) {
                    throw new IllegalStateException("the cue port failed");
                }
                heard.cue(mob, request);
            }
        });
        for (var encounter : world.registry.open()) {
            encounter.offerGroupCapture(1, true, List.of(GroupPlan.PRESS_MAIN_TARGET, GroupPlan.SURROUND_TARGET));
        }
        world.step(0);
        assertEquals(1, world.metrics.total(DecisionMetrics.Counter.STEP_FAILURES));
        assertTrue(other.groupCopy().role().isPresent(), "the second encounter is still coordinated");
        assertTrue(heard.signals.contains(CueRequest.plan(GroupPlan.PRESS_MAIN_TARGET).signal()));
        world.step(1);
        assertEquals(GroupMode.FAIR, failing.groupBooks().mode(), "the failed encounter dropped to fair");
        assertTrue(failing.groupCopy().role().isEmpty());
    }

    @Test
    void aMemberWithoutASeatGetsNoRoleAndTheOthersKeepThePlan() {
        var world = new Lab(GroupMode.COORDINATE);
        var members = new ArrayList<MobSession>();
        members.add(world.member(1, 90, EngineFixtures.player(1), new RoleCapabilities(500, 400, 0, 0, 0, 400)));
        for (var id = 2; id <= EncounterRegistry.MAX_MEMBERS; id++) {
            members.add(world.member(id, 1, EngineFixtures.player(1), RoleCapabilities.NONE));
        }
        world.encounter().offerGroupCapture(1, true, List.of(GroupPlan.PRESS_MAIN_TARGET, GroupPlan.SURROUND_TARGET));
        world.step(0);
        assertEquals(GroupPlan.PRESS_MAIN_TARGET, world.director.plan(world.encounter().id()));
        var leaving = members.getLast();
        var seat = leaving.groupBooks().memberIndex();
        var attack = leaving.groupBooks().attack();
        assertTrue(attack.grant(0, seat, AttackPool.MELEE, 0, 16, 1).isEmpty());
        attack.lock(0, seat, AttackPool.MELEE);
        world.registry.leave(leaving);
        var newcomer = world.member(EncounterRegistry.MAX_MEMBERS + 1, 1, EngineFixtures.player(1),
                RoleCapabilities.NONE);
        assertEquals(EncounterRegistry.MAX_MEMBERS, world.encounter().memberCount());
        world.step(GroupCaps.DEFAULT.replanPeriodTicks());
        assertEquals(0, world.metrics.total(DecisionMetrics.Counter.STEP_FAILURES), "no member without a seat breaks it");
        assertEquals(GroupPlan.PRESS_MAIN_TARGET, world.director.plan(world.encounter().id()),
                "the plan stays in force for the seated members");
        assertTrue(newcomer.groupCopy().role().isEmpty(), "a member without a seat gets no role");
        assertTrue(members.getFirst().groupCopy().role().isPresent());
    }

    @Test
    void eachMemberTakesTheSlotOnTheSideItStandsOn() {
        var east = slotSides(6, -6);
        assertTrue(east[0] > 0 && east[1] < 0, "the member east of the player takes an east slot: " + east[0] + ", "
                + east[1]);
        var west = slotSides(-6, 6);
        assertTrue(west[0] < 0 && west[1] > 0, "and the other way round: " + west[0] + ", " + west[1]);
    }

    /** The east offset of the slot each of two members gets when they stand at these east offsets of the player. */
    private static double[] slotSides(int firstEast, int secondEast) {
        var world = new Lab(GroupMode.COORDINATE);
        var first = world.member(1, 90, EngineFixtures.player(1), new RoleCapabilities(500, 400, 0, 0, 0, 400));
        var second = world.member(2, 1, EngineFixtures.player(1), new RoleCapabilities(500, 400, 0, 0, 0, 400));
        first.noteAnchor(100 + firstEast, 100, 100, 100, 0);
        second.noteAnchor(100 + secondEast, 100, 100, 100, 0);
        world.encounter().offerGroupCapture(1, true, List.of(GroupPlan.SURROUND_TARGET, GroupPlan.PRESS_MAIN_TARGET));
        world.step(0);
        assertTrue(first.groupSlotIndex() >= 0 && second.groupSlotIndex() >= 0, "both members have a slot");
        return new double[] {SlotGeometry.east(first.groupSlotIndex()), SlotGeometry.east(second.groupSlotIndex())};
    }

    @Test
    void aTokenTheLedgerReleasedIsNotPublishedAgain() {
        var world = new Lab(GroupMode.COORDINATE);
        var idle = world.member(1, 90, EngineFixtures.player(1), new RoleCapabilities(500, 400, 0, 0, 0, 400));
        world.member(2, 1, EngineFixtures.player(1), RoleCapabilities.NONE);
        world.step(0);
        var books = idle.groupBooks();
        var attack = books.attack();
        var seat = books.memberIndex();
        assertTrue(attack.grant(0, seat, AttackPool.MELEE, 0, 4, 1).isEmpty());
        idle.groupCopy().token(AttackPool.MELEE);
        world.step(1);
        assertTrue(idle.groupCopy().holdsMelee(), "the ledger still holds the token");
        var timeout = books.caps().tokenIdleTimeoutTicks();
        assertFalse(attack.holds(0, seat, AttackPool.MELEE, timeout + 1, timeout), "idle past the timeout");
        assertFalse(attack.holding(0, seat, AttackPool.MELEE));
        world.step(timeout + 2);
        assertFalse(idle.groupCopy().holdsMelee(), "the copy follows the ledger, so micro does not enter for it");
        assertFalse(idle.groupCopy().holdsToken());
    }

    @Test
    void theGroupClockDisengagesAfterTheConfiguredGap() {
        var world = new Lab(GroupMode.COORDINATE);
        var gap = 400;
        var defaults = akyrion.thepredict.domain.DifficultyGlobals.DEFAULT;
        world.director.useDifficulty(new akyrion.thepredict.domain.DifficultyGlobals(defaults.defaultMode(),
                defaults.defaultPreset(), defaults.presets(), defaults.worlds(), defaults.adjustIntervalTicks(),
                defaults.toleranceRatio(), defaults.deadbandRatio(), defaults.learningGain(),
                defaults.projectedTtkCapRatio(), defaults.dampingReversals(), defaults.estimatorHalfLifeTicks(),
                defaults.minEvidenceTicks(), gap, defaults.maxTrackedFights(), defaults.nativeLevelAllowed()));
        var hitter = world.member(1, 90, EngineFixtures.player(1), new RoleCapabilities(500, 400, 0, 0, 0, 400));
        world.member(2, 1, EngineFixtures.player(1), RoleCapabilities.NONE);
        world.step(1);
        hitter.noteGroupHit(EngineFixtures.player(1), 1, false);
        var clock = hitter.groupClock().orElseThrow();
        assertTrue(clock.open());
        world.step(1 + defaults.disengageTicks() + 1);
        assertTrue(clock.open(), "the delivered gap of " + defaults.disengageTicks() + " ticks is not the one in use");
        world.step(1 + gap);
        assertFalse(clock.open(), "difficulty.disengageTicks closes the group clock as it closes the clock of a mob");
    }

    @Test
    void cuesFireOnceAndInspectReadsTheCopy() {
        var world = new Lab(GroupMode.COORDINATE);
        var heard = new Heard();
        world.director.useCues(heard);
        var leader = world.member(1, 90, EngineFixtures.player(1), new RoleCapabilities(500, 400, 0, 0, 0, 400));
        world.member(2, 1, EngineFixtures.player(1), RoleCapabilities.NONE);
        world.encounter().offerGroupCapture(1, true, List.of(GroupPlan.PRESS_MAIN_TARGET, GroupPlan.SURROUND_TARGET));
        world.step(0);
        assertTrue(heard.signals.contains(CueRequest.plan(GroupPlan.PRESS_MAIN_TARGET).signal()));
        var role = leader.groupCopy().role().orElseThrow();
        assertTrue(heard.signals.contains(CueRequest.role(role).signal()));
        var before = heard.signals.size();
        world.step(1);
        assertEquals(before, heard.signals.size());
        var lines = GroupInspect.lines(leader);
        assertTrue(lines.contains("membershipEpoch " + leader.groupCopy().membershipEpoch()));
        assertTrue(lines.contains("role " + role.name().toLowerCase(java.util.Locale.ROOT)));
        assertTrue(lines.contains("plan " + GroupPlan.PRESS_MAIN_TARGET.option()));
        assertTrue(lines.contains("origin local"), "candidate 0 of the capture: " + lines);
        world.encounter().offerGroupAnswer(GroupPlan.SURROUND_TARGET, 1, leader.groupCopy().membershipEpoch());
        world.step(2);
        var answered = GroupInspect.lines(leader);
        assertTrue(answered.contains("plan " + GroupPlan.SURROUND_TARGET.option()), answered.toString());
        assertTrue(answered.contains("origin jev"), "the plan Jev answered is not shown as local: " + answered);
    }

    @Test
    void squadsFollowNamesAndParentage() {
        var ids = SquadPolicy.assign(new String[] {"vanguard", "", "vanguard"}, new boolean[] {true, false, false},
                new int[] {-1, 0, -1}, new int[] {-1, -1, -1});
        assertEquals(ids[0], ids[2]);
        assertEquals(ids[0], ids[1]);
    }

    @Test
    void aStaleCopyDropsToFairAndKeepsTheBooks() {
        var clock = new BlowClock();
        var agenda = new Agenda();
        var registry = new EncounterRegistry(agenda, clock, new SessionMetrics());
        var metrics = new DecisionMetrics(clock::currentTick);
        var director = new GroupDirector(registry, clock, metrics, caps(GroupMode.COORDINATE, 1, 1, 1), agenda);
        var pairs = new Pair[8];
        for (var index = 0; index < pairs.length; index++) {
            pairs[index] = pair(registry, 1 + index * 2, EngineFixtures.player(index + 1));
            registry.find(pairs[index].leader.encounter().orElseThrow()).orElseThrow().offerGroupCapture(1, true,
                    List.of(GroupPlan.PRESS_MAIN_TARGET, GroupPlan.SURROUND_TARGET));
        }
        for (var tick = 0; tick < 3; tick++) {
            clock.tick = tick;
            clock.blowAt = Integer.MAX_VALUE;
            director.step(tick, Long.MAX_VALUE);
        }
        var sample = pairs[7].leader;
        assertEquals(GroupMode.COORDINATE, sample.groupBooks().mode());
        var ledger = sample.groupBooks().attack();
        var staleTicks = 0;
        for (var tick = 20; tick <= 22; tick++) {
            if (tick == 20) {
                registry.find(sample.encounter().orElseThrow()).orElseThrow().offerGroupCapture(2, true,
                        List.of(GroupPlan.SURROUND_TARGET, GroupPlan.PRESS_MAIN_TARGET));
            }
            clock.tick = tick;
            clock.calls = 0;
            clock.blowAt = tick == 20 ? 3 : 2;
            director.step(tick, 1);
            staleTicks++;
        }
        assertNotNull(sample.groupBooks());
        assertSame(ledger, sample.groupBooks().attack());
        assertEquals(GroupMode.FAIR, sample.groupBooks().mode());
        assertEquals(GroupMode.FAIR, sample.groupCopy().mode());
        assertEquals(staleTicks * 2L, metrics.total(DecisionMetrics.GroupCounter.GROUP_COPY_STALE_TICKS));
        assertEquals(GroupMode.COORDINATE, pairs[0].leader.groupBooks().mode());
    }

    @Test
    void relationsKeepOnlyIdentitiesInsideTheEncounter() {
        var parent = UUID.randomUUID();
        var child = UUID.randomUUID();
        var outside = UUID.randomUUID();
        var read = GroupRelationReader.read(parent, outside, List.of(child, outside), Set.of(parent, child));
        assertEquals(Optional.of(parent), read.parent());
        assertTrue(read.owner().isEmpty());
        assertTrue(read.hasChildren());
        var dropped = GroupRelationReader.read(outside, outside, List.of(outside), Set.of(parent));
        assertTrue(dropped.parent().isEmpty());
        assertFalse(dropped.hasChildren());
    }

    @Test
    void sixteenPlayerClocksStayOnAnEmptySubject() {
        var clock = new GroupFightClock();
        clock.noteMembers(4);
        assertFalse(clock.open());
        var players = new UUID[16];
        for (var index = 0; index < 16; index++) {
            players[index] = EngineFixtures.player(index + 1);
            assertEquals(Optional.of(akyrion.thepredict.domain.FightEventKind.ENGAGED),
                    clock.noteHit(index, players[index], 10, false, "guardian_ai"));
        }
        assertEquals(16, clock.clocks());
        assertEquals(-1, clock.evictQuietest());
        assertEquals(Optional.of(akyrion.thepredict.domain.FightEventKind.TARGET_DIED),
                clock.noteHit(0, players[0], 11, true, "guardian_ai"));
        var quietTicks = akyrion.thepredict.domain.DifficultyGlobals.DEFAULT.disengageTicks();
        for (var index = 1; index < 16; index++) {
            assertEquals(Optional.empty(), clock.noteHit(index, players[index], 11 + quietTicks, false, "guardian_ai"));
        }
        assertEquals(1, clock.quiet(11 + quietTicks, quietTicks));
        var freed = clock.evictQuietest();
        assertTrue(freed >= 0);
        assertEquals(akyrion.thepredict.domain.FightEventKind.EVICTED, clock.last(freed).orElseThrow());
        assertEquals(Optional.of(akyrion.thepredict.domain.FightEventKind.ENGAGED),
                clock.noteHit(freed, EngineFixtures.player(40), 12, false, "guardian_ai"));
        assertEquals(16, clock.clocks());
        clock.noteMembers(0);
        assertEquals(Optional.of(akyrion.thepredict.domain.FightEventKind.GROUP_WIPED), clock.wiped(13));
        var key = clock.key("world");
        assertTrue(key.subject().isEmpty());
        assertEquals(akyrion.thepredict.domain.DifficultySection.GROUP, key.section());
        assertTrue(clock.summary().isPresent());
        assertTrue(clock.summaryLine().contains("GROUP_WIPED"));
    }

    @Test
    void oneAssignmentPerTableAndTwoWaveRetreatsStay() {
        var clipped = SharedTableAssignment.magnitude(100, 0, 5, 10);
        assertEquals(10.0, clipped);
        var writers = SharedTableAssignment.writers(new int[] {7, 7, 0}, new boolean[] {false, true, false});
        assertEquals(0, writers[0]);
        assertEquals(-1, writers[1]);
        assertEquals(-1, writers[2]);
        assertEquals(2, SharedTableAssignment.unavailable(new int[] {7, 7, 0}, new boolean[] {false, true, false}));
        var world = new Lab(GroupMode.COORDINATE);
        var applied = new ArrayList<Double>();
        world.director.useAssignment((encounter, member, participant, magnitude, tick) -> applied.add(magnitude));
        var leader = world.member(1, 80, EngineFixtures.player(1), new RoleCapabilities(500, 0, 0, 0, 0, 400));
        var child = world.member(2, 1, EngineFixtures.player(1), RoleCapabilities.NONE);
        leader.noteThreatTable(7, false, 100, 0);
        child.noteThreatTable(7, true, 100, 0);
        world.encounter().offerGroupCapture(1, true, List.of(GroupPlan.PRESS_MAIN_TARGET, GroupPlan.SURROUND_TARGET));
        world.step(0);
        assertEquals(List.of(10.0), applied);
        var strong = UUID.randomUUID();
        var weak = UUID.randomUUID();
        var aimed = new java.util.ArrayList<UUID>();
        var focusWorld = new Lab(GroupMode.COORDINATE);
        focusWorld.director.useAssignment((encounter, member, participant, magnitude, tick) -> aimed.add(participant));
        var press = focusWorld.member(3, 80, strong, new RoleCapabilities(500, 0, 0, 0, 0, 400));
        focusWorld.member(4, 1, strong, RoleCapabilities.NONE);
        press.noteThreatTable(7, false, 100, 1);
        press.noteThreatSamples(new UUID[] {strong, weak}, new double[] {40.0, 1.0}, 2);
        focusWorld.encounter().offerGroupCapture(1, true, List.of(GroupPlan.PRESS_MAIN_TARGET,
                GroupPlan.HARASS_FROM_RANGE));
        var strongAlias = focusWorld.encounter().aliasFor(strong, akyrion.thepredict.domain.ParticipantKind.PLAYER);
        var weakAlias = focusWorld.encounter().aliasFor(weak, akyrion.thepredict.domain.ParticipantKind.PLAYER);
        focusWorld.encounter().lastFacts(new CombatFacts(focusWorld.encounter().id(), 1, 0, List.of(),
                List.of(new akyrion.thepredict.domain.ParticipantFacts(strongAlias, HealthBand.HEALTHY,
                                akyrion.thepredict.domain.ArmorBand.LIGHT, Set.of(), 1),
                        new akyrion.thepredict.domain.ParticipantFacts(weakAlias, HealthBand.CRITICAL,
                                akyrion.thepredict.domain.ArmorBand.LIGHT, Set.of(), 2)),
                List.of(), 0));
        focusWorld.step(1);
        assertEquals(strong, aimed.getLast());
        press.difficulty().resolve(1, true, 0, 0, 8, null, akyrion.thepredict.domain.DifficultyResolution.ABSENT,
                null, null, null, Boolean.TRUE, false, akyrion.thepredict.domain.DifficultyResolution.ABSENT);
        assertTrue(press.entropyActive());
        focusWorld.step(2);
        assertEquals(weak, aimed.getLast());
        var stolen = new AttackLedger();
        assertTrue(stolen.grant(0, 0, AttackPool.MELEE, 1, 2, 1).isEmpty());
        assertTrue(stolen.grant(0, 1, AttackPool.MELEE, 1, 2, 1).isEmpty());
        assertTrue(stolen.stealExcess(0, AttackPool.MELEE, 1));
        assertEquals(akyrion.thepredict.domain.TokenEnd.STOLEN, stolen.lastClosed());
        assertEquals(GroupFairnessReason.CAPACITY_LOWERED, stolen.lastFairness());
        assertTrue(world.metrics.total(DecisionMetrics.GroupCounter.ASSIGNMENT_UNAVAILABLE) >= 1);
        var health = new int[] {3, 1, 3, 1};
        assertFalse(RegroupPolicy.retreats(0, health, 0));
        assertTrue(RegroupPolicy.retreats(1, health, 0));
        assertTrue(RegroupPolicy.retreats(0, health, RegroupPolicy.WAVE_TICKS));
        assertFalse(RegroupPolicy.retreats(1, health, RegroupPolicy.WAVE_TICKS));
        var keep = new boolean[] {true, false, true, false};
        var result = CoordinationPolicy.limitRetreats(retreating(4), 4, 0, 5, 0, keep);
        assertEquals(Movement.RETREAT, result.get(0).orElseThrow().movement());
        assertEquals(Movement.RETREAT, result.get(2).orElseThrow().movement());
        assertTrue(RegroupPolicy.finished(3, 0));
        assertTrue(RegroupPolicy.finished(20, RegroupPolicy.DONE_TICKS));
    }

    private static List<CoordinationPolicy.Candidate> retreating(int members) {
        var tactic = new TacticId("defend_self");
        var press = new TacticId("press_attack");
        var found = new ArrayList<CoordinationPolicy.Candidate>();
        for (var index = 0; index < members; index++) {
            var intent = new TacticalIntent(new MobKey(new UUID(7, index), 0), tactic, Movement.RETREAT,
                    Optional.empty(), 0.5, List.of(press), Map.of(), ControlLevel.JEV, 0, 1_000, false);
            found.add(new CoordinationPolicy.Candidate(intent, Optional.empty(), Set.of(tactic, press),
                    EngineFixtures.profile("Guardian")));
        }
        return found;
    }

    private static akyrion.thepredict.domain.TargetRelation relation(boolean visible) {
        return new akyrion.thepredict.domain.TargetRelation(new akyrion.thepredict.domain.ParticipantAlias(1),
                akyrion.thepredict.domain.DistanceBand.MELEE, akyrion.thepredict.domain.Trend.STEADY, true, visible,
                false, true, false);
    }

    private static akyrion.thepredict.domain.SkillChoice projectile(double blocksPerTick) {
        var purpose = new akyrion.thepredict.domain.SkillPurpose(akyrion.thepredict.domain.SkillRole.DAMAGE,
                Set.of(akyrion.thepredict.domain.SkillRole.DAMAGE), Delivery.PROJECTILE,
                java.util.OptionalDouble.empty(), java.util.OptionalDouble.empty(), 0,
                akyrion.thepredict.domain.CooldownClass.SHORT, akyrion.thepredict.domain.DamageTier.HIGH, Set.of(),
                Set.of(), true, akyrion.thepredict.domain.PurposeOrigin.DECLARED);
        var choice = akyrion.thepredict.domain.SkillChoice.typed(new ChoiceId(blocksPerTick > 0 ? "fast" : "unread"),
                "Bolt", Set.of(), true, 0, purpose);
        if (blocksPerTick > 0) {
            return choice.withNativeFacts(akyrion.thepredict.domain.NativeSkillFacts.NONE.withProjectileSpeed(
                    blocksPerTick));
        }
        return choice.withNativeFacts(akyrion.thepredict.domain.NativeSkillFacts.NONE);
    }

    private static akyrion.thepredict.domain.SkillChoice medium() {
        return told(DangerClass.MEDIUM, TellAnswer.DODGE, 8);
    }

    private static akyrion.thepredict.domain.SkillChoice light() {
        return told(DangerClass.LIGHT, TellAnswer.BACK_OFF, 0);
    }

    private static akyrion.thepredict.domain.SkillChoice told(DangerClass danger, TellAnswer answer, int wind) {
        var purpose = new akyrion.thepredict.domain.SkillPurpose(akyrion.thepredict.domain.SkillRole.DAMAGE,
                Set.of(akyrion.thepredict.domain.SkillRole.DAMAGE), Delivery.MELEE, java.util.OptionalDouble.of(3),
                java.util.OptionalDouble.empty(), wind, akyrion.thepredict.domain.CooldownClass.SHORT,
                danger == DangerClass.LIGHT ? akyrion.thepredict.domain.DamageTier.LOW
                        : akyrion.thepredict.domain.DamageTier.MEDIUM,
                Set.of(), Set.of(), true, akyrion.thepredict.domain.PurposeOrigin.DECLARED);
        var name = danger.name().toLowerCase(java.util.Locale.ROOT);
        return akyrion.thepredict.domain.SkillChoice.typed(new ChoiceId(name), name, Set.of(), true,
                0, purpose).withTelegraph(new akyrion.thepredict.domain.ChoiceTelegraph(Optional.of(danger),
                Optional.of(answer), OptionalInt.empty()));
    }

    private static MobSession body(int id) {
        return new MobSession(new MobKey(EngineFixtures.id(id), 0), EngineFixtures.profile("Guardian"));
    }

    private static CombatReader reader(MobKey key, EncounterId encounter) {
        return new CombatReader() {
            @Override
            public java.util.Optional<CombatFacts> capture(EncounterId id, List<MobKey> members,
                                                           akyrion.thepredict.domain.CaptureContext context) {
                assertEquals(encounter, id);
                return Optional.of(new CombatFacts(id, 20, 0, List.of(new MobFacts(key, HealthBand.HEALTHY,
                        Optional.empty(), Set.of(), Set.of(), Set.of(), Set.of())), List.of(), List.of(), 0));
            }

            @Override
            public java.util.Optional<akyrion.thepredict.domain.MobProbe> probe(MobKey mob) {
                return Optional.empty();
            }

            @Override
            public boolean inCombat(UUID entityId) {
                return false;
            }

            @Override
            public boolean withinFollowRange(MobKey mob, MobKey member) {
                return false;
            }

            @Override
            public boolean isActive(UUID entityId) {
                return true;
            }

            @Override
            public java.util.Optional<akyrion.thepredict.domain.ThreatTableState> threatTable(MobKey mob) {
                return Optional.of(new akyrion.thepredict.domain.ThreatTableState(true, 1, 80.0, 80.0));
            }

            @Override
            public double threat(MobKey mob, UUID participant) {
                return 25.0;
            }
        };
    }

    private static akyrion.thepredict.domain.ZoneView view(int epoch, long column, short hazard, short edge) {
        return new akyrion.thepredict.domain.ZoneView() {
            public int width() {
                return 1;
            }

            public int originX() {
                return 0;
            }

            public int originZ() {
                return 0;
            }

            public int anchorY() {
                return 64;
            }

            public long column(int index) {
                return column;
            }

            public short hazardThirds(int index) {
                return hazard;
            }

            public short edgeThirds(int index) {
                return edge;
            }

            public short clearanceThirds(int index) {
                return 8;
            }

            public int epoch() {
                return epoch;
            }

            public int maxSafeDrop() {
                return 3;
            }
        };
    }

    private static TacticalProfile profile(int priority, GroupMode mode) {
        return profile(priority, mode, new RoleCapabilities(500, 400, 0, 0, 0, 400));
    }

    private static TacticalProfile profile(int priority, GroupMode mode, RoleCapabilities capabilities) {
        return profile(priority, mode, capabilities, new LinkedHashMap<>());
    }

    private static akyrion.thepredict.domain.SkillChoice listedSlam() {
        return akyrion.thepredict.domain.SkillChoice.typed(new ChoiceId("slam"), "Slam", Set.of(), true, 0,
                new akyrion.thepredict.domain.SkillPurpose(akyrion.thepredict.domain.SkillRole.DAMAGE,
                        Set.of(akyrion.thepredict.domain.SkillRole.DAMAGE), Delivery.MELEE,
                        java.util.OptionalDouble.of(3), java.util.OptionalDouble.empty(), 8,
                        akyrion.thepredict.domain.CooldownClass.SHORT,
                        akyrion.thepredict.domain.DamageTier.HIGH, Set.of(), Set.of(), true,
                        akyrion.thepredict.domain.PurposeOrigin.DECLARED));
    }

    private static TacticalProfile profile(int priority, GroupMode mode, RoleCapabilities capabilities,
                                          Map<ChoiceId, akyrion.thepredict.domain.SkillChoice> choices) {
        var base = EngineFixtures.profile(priority > 50 ? "Leader" : "Minion");
        var group = new GroupSettings(Optional.of(mode), "", priority, 1, Set.of(), LeaderDeath.SUCCEED,
                BenchPolicy.FREE, Optional.empty(), PlanQuestion.ON_CHANGE, capabilities);
        return new TacticalProfile(base.id(), base.mythicMobId(), base.objective(), base.tactics(),
                choices, base.contexts(), base.movement(), base.health(), base.threat(), base.baseAi(),
                base.timing(), base.participants(), base.difficulty(), base.stances(), base.telegraph(), base.poise(),
                base.zone(), group);
    }

    private static GroupCaps caps(GroupMode mode) {
        return caps(mode, GroupCaps.DEFAULT.maxMeleeAttackersPerPlayer(), GroupCaps.DEFAULT.arbitrationPeriodTicks(),
                GroupCaps.DEFAULT.maxReplansPerTick());
    }

    private static GroupCaps caps(GroupMode mode, int melee, int arbitration, int replans) {
        var base = GroupCaps.DEFAULT;
        return new GroupCaps(mode, base.maxSquads(), base.brokenTicks(), arbitration, base.replanPeriodTicks(),
                replans, base.maxMicrosPerTick(), melee, base.maxRangedAttackersPerPlayer(),
                base.minImpactGapTicks(), base.tokenIdleTimeoutTicks(), base.stealMarginMillis(),
                base.idleWarnTicks(), base.roleSwitchMarginMillis(), base.exitSectorDegrees(), base.lkpForgetTicks(),
                base.maxMediumTellsPerPlayer(), base.bigImpactSpacingTicks(), base.leaderReserveTicks(),
                base.projectileFlightMarginTicks(), base.maxPlanCandidates(), base.minPlanTicks(),
                base.planHorizonTicks(), base.assignmentThreatMargin(), base.factOnTicks(), base.factOffTicks(),
                base.maxFacts());
    }

    private static Pair pair(EncounterRegistry registry, int id, UUID target) {
        var leader = member(registry, id, 80, target, new RoleCapabilities(500, 400, 0, 0, 0, 400));
        var ally = new MobSession(new MobKey(EngineFixtures.id(id + 1), 0),
                profile(1, GroupMode.COORDINATE, RoleCapabilities.NONE));
        registry.assign(ally, EngineFixtures.WORLD, target, key -> true);
        ally.recordTarget(target, 4);
        return new Pair(leader, ally);
    }

    private static MobSession member(EncounterRegistry registry, int id, int priority, UUID target,
                                    RoleCapabilities capabilities) {
        var session = new MobSession(new MobKey(EngineFixtures.id(id), 0),
                profile(priority, GroupMode.COORDINATE, capabilities));
        registry.assign(session, EngineFixtures.WORLD, target, key -> false);
        session.recordTarget(target, 4);
        return session;
    }

    private record Pair(MobSession leader, MobSession ally) {
    }

    private static class Heard implements CombatActions {
        final List<String> signals = new ArrayList<>();

        public akyrion.thepredict.domain.ExecutionOutcome cast(MobKey mob,
                akyrion.thepredict.domain.SkillChoice choice, Movement movement) {
            return akyrion.thepredict.domain.ExecutionOutcome.NOT_ELIGIBLE;
        }

        public boolean ready(MobKey mob, akyrion.thepredict.domain.SkillChoice choice) {
            return false;
        }

        public void signalTactic(MobKey mob, TacticId tactic) {
        }

        public void addThreat(MobKey mob, UUID participantId, double amount) {
        }

        public void cue(MobKey mob, CueRequest request) {
            signals.add(request.signal());
        }
    }

    private static final class BlowClock implements akyrion.thepredict.engine.ServerClock {
        long tick;
        int calls;
        int blowAt = Integer.MAX_VALUE;

        public long currentTick() {
            return tick;
        }

        public long nanoTime() {
            calls++;
            return calls >= blowAt ? 1_000_000_000L : 0L;
        }
    }

    private static final class Lab {
        final EngineFixtures.TestClock clock = new EngineFixtures.TestClock();
        final Agenda agenda = new Agenda();
        final EncounterRegistry registry = new EncounterRegistry(agenda, clock, new SessionMetrics());
        final DecisionMetrics metrics = new DecisionMetrics(clock::currentTick);
        final GroupDirector director;

        Lab(GroupMode mode) {
            this(caps(mode));
        }

        Lab(GroupCaps caps) {
            director = new GroupDirector(registry, clock, metrics, caps, agenda);
        }

        MobSession member(int id, int priority, UUID target) {
            return member(id, priority, target, new RoleCapabilities(500, 400, 0, 0, 0, 400));
        }

        MobSession member(int id, int priority, UUID target, RoleCapabilities capabilities) {
            return member(id, priority, target, capabilities, null);
        }

        MobSession member(int id, int priority, UUID target, RoleCapabilities capabilities,
                          akyrion.thepredict.domain.SkillChoice listed) {
            var choices = new LinkedHashMap<ChoiceId, akyrion.thepredict.domain.SkillChoice>();
            if (listed != null) {
                choices.put(listed.id(), listed);
            }
            var session = new MobSession(new MobKey(EngineFixtures.id(id), 0),
                    profile(priority, GroupMode.COORDINATE, capabilities, choices));
            registry.assign(session, EngineFixtures.WORLD, target, key -> true);
            session.recordTarget(target, 4);
            return session;
        }

        Encounter encounter() {
            return registry.open().iterator().next();
        }

        void step(long tick) {
            clock.tick = tick;
            director.step(tick, 1_000_000_000L);
        }
    }
}
