package akyrion.thepredict.engine;

import akyrion.thepredict.domain.GroupBrief;
import akyrion.thepredict.domain.GroupQuestions;
import akyrion.thepredict.domain.LearningSettings;
import akyrion.thepredict.domain.QuestionKey;
import akyrion.thepredict.domain.QuestionSpec;
import akyrion.thepredict.domain.Situation;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.distill.AgreementStatDelta;
import akyrion.thepredict.domain.BenchPolicy;
import akyrion.thepredict.domain.GroupCaps;
import akyrion.thepredict.domain.GroupMode;
import akyrion.thepredict.domain.GroupPlan;
import akyrion.thepredict.domain.GroupSettings;
import akyrion.thepredict.domain.LeaderDeath;
import akyrion.thepredict.domain.PlanQuestion;
import akyrion.thepredict.domain.RoleCapabilities;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.domain.distill.DemotionCause;
import akyrion.thepredict.domain.distill.DistillMode;
import akyrion.thepredict.domain.distill.DistillSettings;
import akyrion.thepredict.domain.distill.DistilledPolicy;
import akyrion.thepredict.engine.distill.DistillControl;

import akyrion.thepredict.engine.distill.DistillPublish;
import akyrion.thepredict.engine.distill.DistillRefresh;
import akyrion.thepredict.engine.distill.DistillRuntime;
import akyrion.thepredict.engine.distill.DistillVerdict;
import akyrion.thepredict.engine.learn.ValueTableBuilder;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Capture paths closed by the M29 audit. Publishing the index by hand is not the path under test. */
class M29CaptureClosureTest {
    @Test
    void aPublishedCellDelegatesAndAStaleMinuteDoesNot() {
        var fixture = new EngineFixtures.Sessions();
        var session = fixture.open(1);
        var text = WindowCredit.live(session).text();
        assertFalse(text.equals("0"));
        var runtime = publish(text);
        var encounter = fixture.encounters.assign(session, EngineFixtures.WORLD, EngineFixtures.player(1),
                EngineFixtures.near());
        var plan = plan(session);
        var settings = DistillSettings.DEFAULT.withMode(DistillMode.ASSIST);
        CaptureGate.apply(encounter, plan, 4L, LearningSettings.DEFAULT, DecisionTap.NONE, settings,
                akyrion.thepredict.engine.distill.TeachingBoard.NONE, runtime, 0L);
        CaptureGate.apply(encounter, plan, 8L, LearningSettings.DEFAULT, DecisionTap.NONE, settings,
                akyrion.thepredict.engine.distill.TeachingBoard.NONE, runtime, 0L);
        assertTrue(runtime.published());
        assertEquals(1, session.learning().delegated());
        assertEquals(text, session.learning().distillSession().trace().leaf());
        assertEquals("0", session.learning().contextText());
        var note = DistillNote.planned(settings, encounter);
        assertTrue(note.distill().contains("\"artifact\""));
        assertTrue(note.distill().contains("\"regime\""));
        assertTrue(note.distill().contains("\"leaf\""));
        assertTrue(note.distill().contains("\"student\""));
        assertTrue(note.distill().contains("\"source\""));
        assertTrue(note.distill().contains("\"covered\""));
        assertTrue(note.distill().contains("\"cell_n\""));
        assertTrue(note.distill().contains("\"cell_lb_millis\""));
        assertTrue(note.distill().contains("\"audit\""));
        assertTrue(note.distill().contains("\"delegated\":true"));
        var stale = fixture.open(2);
        var other = fixture.encounters.assign(stale, EngineFixtures.WORLD, EngineFixtures.player(2),
                EngineFixtures.near());
        CaptureGate.apply(other, plan(stale), 4L, LearningSettings.DEFAULT, DecisionTap.NONE, settings,
                akyrion.thepredict.engine.distill.TeachingBoard.NONE, runtime,
                61L);
        CaptureGate.apply(other, plan(stale), 8L, LearningSettings.DEFAULT, DecisionTap.NONE, settings,
                akyrion.thepredict.engine.distill.TeachingBoard.NONE, runtime,
                61L);
        assertEquals(0, stale.learning().delegated());
    }

    @Test
    void anAgreementPagePublishesBetweenTicksAndShadowEmitsADelta() {
        var runtime = new DistillRuntime();
        var policy = DistilledPolicy.table("Guardian", "0", List.of("alpha", "bravo"));
        DistillPublish.refresh(runtime, List.of(), policy, 0L, "", "outcome", true);
        var inbox = new Inbox<InboxEntry>(8);
        var batches = new ArrayList<AggregateBatch>();
        var cache = new AggregateCache(store(batches), new StorageMetrics(), 64, 1, 4, inbox);
        var control = new DistillControl(DistillMode.ASSIST, DistillMode.ASSIST);
        DistillRefresh.install(cache, runtime, () -> 0L, control);
        assertTrue(inbox.offerStorage(new StorageReply.AgreementStatsPage(List.of(covered("0")), 1L)));
        cache.drain(4);
        assertTrue(runtime.coverage().lookup("0").isPresent());
        assertEquals(50, runtime.coverage().lookup("0").orElseThrow().n());
        var fixture = new EngineFixtures.Sessions();
        var session = fixture.open(3);
        var encounter = fixture.encounters.assign(session, EngineFixtures.WORLD, EngineFixtures.player(3),
                EngineFixtures.near());
        var builder = new ValueTableBuilder();
        builder.add(1, akyrion.thepredict.engine.learn.LearnedLocalPick.tacticCode("alpha"), 3, 7L, 1.0, 0.01, 40, 1.0);
        builder.add(1, akyrion.thepredict.engine.learn.LearnedLocalPick.tacticCode("bravo"), 3, 7L, 0.2, 0.01, 40, 1.0);
        session.learning().activate(builder.build(), 1, 7L);
        fixture.encounters.useAgreements(cache::acceptAgreements);
        var text = WindowCredit.live(session).text();
        assertFalse(text.equals("0"));
        var livePolicy = DistilledPolicy.table("Guardian", text, List.of("bravo", "alpha"));
        DistillPublish.refresh(runtime, List.of(covered(text)), livePolicy, 0L, "", "outcome", true);
        var settings = DistillSettings.DEFAULT.withMode(DistillMode.SHADOW);
        var plan = plan(session);
        CaptureGate.apply(encounter, plan, 4L, LearningSettings.DEFAULT, DecisionTap.NONE, settings,
                akyrion.thepredict.engine.distill.TeachingBoard.NONE, runtime, 0L);
        WindowCredit.record(session, new OutcomeWindow.Summary(akyrion.thepredict.domain.ControlLevel.LOCAL,
                new TacticId("bravo"), akyrion.thepredict.domain.Movement.HOLD, 0, 1, 0, 0, false, 0, 0, false, false,
                0.4), false, 0L);
        cache.requestFlush();
        cache.flush(1L, AggregateCache.FLUSH_KEYS);
        assertFalse(batches.isEmpty());
        assertFalse(batches.getFirst().agreements().isEmpty());
        var row = batches.getFirst().agreements().getFirst();
        assertTrue(row.compared() >= 1);
        assertEquals(text, row.context());
        assertEquals(1, row.n());
        assertEquals(-0.8, row.sumDelta(), 1.0E-9);
        assertEquals(0.64, row.sumDeltaSq(), 1.0E-9);
    }

    @Test
    void aGroupBriefOtherThanNoneIsNotDelegated() {
        var fixture = new EngineFixtures.Sessions();
        var session = fixture.open(4);
        var runtime = publish(WindowCredit.live(session).text());
        var encounter = fixture.encounters.assign(session, EngineFixtures.WORLD, EngineFixtures.player(4),
                EngineFixtures.near());
        var capture = new EncounterCapture(reader(session.key(), encounter.id()), fixture.registry, fixture.encounters,
                fixture.lifecycle, fixture.clock, new DecisionMetrics(() -> 0L));
        capture.hearGroup(id -> new GroupBrief(GroupQuestions.MEMBERS, List.of("focus"), 2L, true));
        assertTrue(capture.capture(encounter.id(), java.util.Set.of()).isPresent());
        assertTrue(encounter.groupQuestion());
        var settings = DistillSettings.DEFAULT.withMode(DistillMode.ASSIST);
        CaptureGate.apply(encounter, plan(session), 4L, LearningSettings.DEFAULT, DecisionTap.NONE, settings,
                akyrion.thepredict.engine.distill.TeachingBoard.NONE,
                runtime, 0L);
        CaptureGate.apply(encounter, plan(session), 8L, LearningSettings.DEFAULT, DecisionTap.NONE, settings,
                akyrion.thepredict.engine.distill.TeachingBoard.NONE,
                runtime, 0L);
        assertEquals(0, session.learning().delegated());
        var note = DistillNote.planned(settings, encounter);
        assertTrue(note.distill().contains("\"delegated\":false"));
    }

    @Test
    void regimeChangesOnCaptureAndAGroupHoldsThePlanEpoch() {
        var lines = new ArrayList<String>();
        var tap = new DecisionTap() {
            @Override
            public void regimeChanged(akyrion.thepredict.domain.MobKey mob, String from, String to, String cause,
                                     int dwellTicks, String blockedBy) {
                lines.add(blockedBy);
            }
        };
        var settings = DistillSettings.DEFAULT.withMode(DistillMode.SHADOW);
        var alone = new EngineFixtures.Sessions();
        var session = alone.open(5);
        var encounter = alone.encounters.assign(session, EngineFixtures.WORLD, EngineFixtures.player(5),
                EngineFixtures.near());
        CaptureGate.apply(encounter, plan(session), 0L, LearningSettings.DEFAULT, tap, settings,
                akyrion.thepredict.engine.distill.TeachingBoard.NONE);
        CaptureGate.apply(encounter, plan(session), 40L, LearningSettings.DEFAULT, tap, settings,
                akyrion.thepredict.engine.distill.TeachingBoard.NONE);
        assertTrue(lines.contains(""));
        assertEquals(1, session.groupCopy().planEpoch());
        assertEquals(1, session.groupCopy().membershipEpoch());
        var grouped = new EngineFixtures.Sessions();
        var first = grouped.open(6);
        var second = grouped.open(7);
        var fight = grouped.encounters.assign(first, EngineFixtures.WORLD, EngineFixtures.player(6), key -> true);
        grouped.encounters.assign(second, EngineFixtures.WORLD, EngineFixtures.player(6), key -> true);
        var before = first.groupCopy().planEpoch();
        CaptureGate.apply(fight, plan(first), 10L, LearningSettings.DEFAULT, tap, settings,
                akyrion.thepredict.engine.distill.TeachingBoard.NONE);
        assertTrue(lines.contains("PLAN_HOLD"));
        assertEquals(before, first.groupCopy().planEpoch());
        assertEquals(0, first.learning().distillSession().selectorOrNull().switches());
        first.openTell(0, 10L, 8, 0, 0, false, EngineFixtures.player(6));
        lines.clear();
        CaptureGate.apply(fight, plan(first), 80L, LearningSettings.DEFAULT, tap, settings,
                akyrion.thepredict.engine.distill.TeachingBoard.NONE);
        assertTrue(lines.contains("TELEGRAPH"));
    }

    @Test
    void tenThousandZeroAuditsStayQuietAndTheVerdictIsNotFixedAtAssist() {
        var fixture = new EngineFixtures.Sessions();
        var session = fixture.open(8);
        var encounter = fixture.encounters.assign(session, EngineFixtures.WORLD, EngineFixtures.player(8),
                EngineFixtures.near());
        var settings = DistillSettings.DEFAULT.withMode(DistillMode.SHADOW);
        var plan = plan(session);
        for (var index = 0; index < 10_000; index++) {
            CaptureGate.apply(encounter, plan, 4L, LearningSettings.DEFAULT, DecisionTap.NONE, settings,
                    akyrion.thepredict.engine.distill.TeachingBoard.NONE);
        }
        assertEquals(DistillMode.AUTONOMOUS, session.learning().distillSession().governor().floor());
        assertTrue(session.learning().distillSession().governor().last().isEmpty());
        assertEquals(DistillMode.OFF, new DistillControl(DistillMode.ASSIST, DistillVerdict.ceiling("", false))
                .effective(DistillMode.ASSIST));
        assertEquals(DistillMode.SHADOW, new DistillControl(DistillMode.AUTONOMOUS, DistillVerdict.ceiling("G0", false))
                .effective(DistillMode.AUTONOMOUS));
        assertEquals(DistillMode.AUTONOMOUS, new DistillControl(DistillMode.AUTONOMOUS,
                DistillVerdict.ceiling("PASS", true)).effective(DistillMode.AUTONOMOUS));
        assertEquals(DistillMode.OFF, DistillSettings.DEFAULT.mode());
    }

    @Test
    void aPlanThatStartsAfterTick40DoesNotSwitchEarly() {
        var lines = new ArrayList<String>();
        var tap = new DecisionTap() {
            @Override
            public void regimeChanged(akyrion.thepredict.domain.MobKey mob, String from, String to, String cause,
                                     int dwellTicks, String blockedBy) {
                lines.add(blockedBy);
            }
        };
        var settings = DistillSettings.DEFAULT.withMode(DistillMode.SHADOW);
        var grouped = new EngineFixtures.Sessions();
        var first = grouped.registry.open(EngineFixtures.id(11), capable("Guardian", 80));
        var second = grouped.registry.open(EngineFixtures.id(12), capable("Guardian", 0));
        var fight = grouped.encounters.assign(first, EngineFixtures.WORLD, EngineFixtures.player(11), key -> true);
        grouped.encounters.assign(second, EngineFixtures.WORLD, EngineFixtures.player(11), key -> true);
        first.recordTarget(EngineFixtures.player(11), 4);
        second.recordTarget(EngineFixtures.player(11), 4);
        CaptureGate.apply(fight, plan(first), 25L, LearningSettings.DEFAULT, tap, settings,
                akyrion.thepredict.engine.distill.TeachingBoard.NONE);
        assertTrue(lines.contains("PLAN_HOLD"));
        assertEquals(0, first.learning().distillSession().selectorOrNull().switches());
        var caps = liveCaps(25, 70);
        var director = new GroupDirector(grouped.encounters, grouped.clock,
                new DecisionMetrics(grouped.clock::currentTick), caps, grouped.agenda);
        fight.offerGroupCapture(1L, true, List.of(GroupPlan.PRESS_MAIN_TARGET));
        director.step(50L, 1_000_000_000L);
        assertEquals(GroupPlan.PRESS_MAIN_TARGET, director.plan(fight.id()));
        assertEquals(50L, first.learning().distillSession().planStarted());
        assertEquals(50L, second.learning().distillSession().planStarted());
        var epoch = first.groupCopy().planEpoch();
        lines.clear();
        CaptureGate.apply(fight, plan(first), 100L, LearningSettings.DEFAULT, tap, settings,
                akyrion.thepredict.engine.distill.TeachingBoard.NONE);
        assertTrue(lines.contains("PLAN_HOLD"));
        assertEquals(0, first.learning().distillSession().selectorOrNull().switches());
        assertEquals(epoch, first.groupCopy().planEpoch());
        lines.clear();
        CaptureGate.apply(fight, plan(first), 120L, LearningSettings.DEFAULT, tap, settings,
                akyrion.thepredict.engine.distill.TeachingBoard.NONE);
        assertTrue(lines.contains("PLAN_HOLD"));
        assertEquals(0, first.learning().distillSession().selectorOrNull().switches());
        assertEquals(epoch, first.groupCopy().planEpoch());
    }

    @Test
    void aNegativeAuditDeltaThroughCaptureLowersTheFloor() {
        var fixture = new EngineFixtures.Sessions();
        fixture.encounters.useLearning(LearningSettings.DEFAULT, ignored -> {
        }, () -> 5L);
        fixture.encounters.useAgreements(ignored -> {
        });
        var session = fixture.open(13);
        var text = WindowCredit.live(session).text();
        assertFalse(text.equals("0"));
        var runtime = publish(text);
        var encounter = fixture.encounters.assign(session, EngineFixtures.WORLD, EngineFixtures.player(13),
                EngineFixtures.near());
        var settings = DistillSettings.DEFAULT.withMode(DistillMode.ASSIST);
        var plan = plan(session);
        for (var index = 0; index < 1_300; index++) {
            CaptureGate.apply(encounter, plan, 4L, LearningSettings.DEFAULT, DecisionTap.NONE, settings,
                    akyrion.thepredict.engine.distill.TeachingBoard.NONE,
                    runtime, 0L);
            var trace = session.learning().distillSession().trace();
            if (!trace.audit() && !trace.delegated()) {
                continue;
            }
            WindowCredit.record(session, new OutcomeWindow.Summary(akyrion.thepredict.domain.ControlLevel.LOCAL,
                    new TacticId("alpha"), akyrion.thepredict.domain.Movement.HOLD, 0, 1, 0, 0, false, 0, 0, false,
                    false, trace.audit() ? 0.05 : 0.0), false, 0L);
        }
        assertTrue(fixture.encounters.closeIfFinished(encounter.id(), ignored -> false));
        var governor = session.learning().distillSession().governor();
        assertEquals(DemotionCause.AUDIT_DRIFT, governor.last().orElseThrow());
        assertTrue(governor.floor().rank() < DistillMode.AUTONOMOUS.rank());
    }

    private static DistillRuntime publish(String text) {
        var runtime = new DistillRuntime();
        var policy = DistilledPolicy.table("Guardian", text, List.of("alpha", "bravo"));
        DistillPublish.refresh(runtime, List.of(covered(text)), policy, 0L, "", "outcome", true);
        return runtime;
    }

    private static AgreementStatDelta covered(String context) {
        return new AgreementStatDelta("outcome", "Guardian", "r1:ALL", context, 0, 50, 0.0, 0.0, 0, 0, 0, 0, 0L);
    }

    private static TacticalProfile capable(String name, int priority) {
        var base = EngineFixtures.profile(name);
        var group = new GroupSettings(Optional.empty(), "", priority, 1, Set.of(), LeaderDeath.SUCCEED,
                BenchPolicy.FREE, Optional.empty(), PlanQuestion.ON_CHANGE,
                new RoleCapabilities(500, 400, 0, 0, 0, 400));
        return new TacticalProfile(base.id(), base.mythicMobId(), base.objective(), base.tactics(), base.choices(),
                base.contexts(), base.movement(), base.health(), base.threat(), base.baseAi(), base.timing(),
                base.participants(), base.difficulty(), base.stances(), base.telegraph(), base.poise(), base.zone(),
                group);
    }

    private static GroupCaps liveCaps(int period, int minPlan) {
        var defaults = GroupCaps.DEFAULT;
        return new GroupCaps(GroupMode.COORDINATE, defaults.maxSquads(), defaults.brokenTicks(),
                defaults.arbitrationPeriodTicks(), period, defaults.maxReplansPerTick(), defaults.maxMicrosPerTick(),
                defaults.maxMeleeAttackersPerPlayer(), defaults.maxRangedAttackersPerPlayer(),
                defaults.minImpactGapTicks(), defaults.tokenIdleTimeoutTicks(), defaults.stealMarginMillis(),
                defaults.idleWarnTicks(), defaults.roleSwitchMarginMillis(), defaults.exitSectorDegrees(),
                defaults.lkpForgetTicks(), defaults.maxMediumTellsPerPlayer(), defaults.bigImpactSpacingTicks(),
                defaults.leaderReserveTicks(), defaults.projectileFlightMarginTicks(), defaults.maxPlanCandidates(),
                minPlan, defaults.planHorizonTicks(), defaults.assignmentThreatMargin(), defaults.factOnTicks(),
                defaults.factOffTicks(), defaults.maxFacts());
    }

    private static EncounterQuestions.Plan plan(MobSession session) {
        var options = List.of(new TacticId("alpha"), new TacticId("bravo"));
        var question = new QuestionSpec(QuestionKey.of(0), session.key(), Situation.NOW, options,
                Optional.of(options.getFirst()));
        return new EncounterQuestions.Plan(List.of(question), Map.of(session.key(), session.profile()), List.of());
    }

    private static AggregateStore store(List<AggregateBatch> batches) {
        return new AggregateStore() {
            @Override
            public StorageHealth health() {
                return new StorageHealth(StorageState.READY, "", 0, 0, 0);
            }

            @Override
            public boolean submit(AggregateBatch batch) {
                batches.add(batch);
                return true;
            }

            @Override
            public boolean request(AggregateQuery query) {
                return true;
            }
        };
    }

    private static CombatReader reader(akyrion.thepredict.domain.MobKey key,
                                      akyrion.thepredict.domain.EncounterId encounter) {
        return new CombatReader() {
            @Override
            public Optional<akyrion.thepredict.domain.CombatFacts> capture(akyrion.thepredict.domain.EncounterId id,
                    List<akyrion.thepredict.domain.MobKey> members,
                    akyrion.thepredict.domain.CaptureContext context) {
                return Optional.of(new akyrion.thepredict.domain.CombatFacts(encounter, 20, 0,
                        List.of(new akyrion.thepredict.domain.MobFacts(key, akyrion.thepredict.domain.HealthBand.HEALTHY,
                                Optional.empty(), java.util.Set.of(), java.util.Set.of(), java.util.Set.of(),
                                java.util.Set.of())),
                        List.of(), List.of(), 0));
            }

            @Override
            public Optional<akyrion.thepredict.domain.MobProbe> probe(akyrion.thepredict.domain.MobKey mob) {
                return Optional.empty();
            }

            @Override
            public boolean inCombat(java.util.UUID entityId) {
                return false;
            }

            @Override
            public boolean withinFollowRange(akyrion.thepredict.domain.MobKey mob,
                                             akyrion.thepredict.domain.MobKey member) {
                return true;
            }

            @Override
            public boolean isActive(java.util.UUID entityId) {
                return true;
            }

            @Override
            public Optional<akyrion.thepredict.domain.ThreatTableState> threatTable(
                    akyrion.thepredict.domain.MobKey mob) {
                return Optional.empty();
            }
        };
    }
}
