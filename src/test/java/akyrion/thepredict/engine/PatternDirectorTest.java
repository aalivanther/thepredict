package akyrion.thepredict.engine;

import akyrion.thepredict.domain.AirSwingPolicy;
import akyrion.thepredict.domain.BenchPolicy;
import akyrion.thepredict.domain.CaptureContext;
import akyrion.thepredict.domain.CombatFacts;
import akyrion.thepredict.domain.DistanceBand;
import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.EnemyAction;
import akyrion.thepredict.domain.EnemyPattern;
import akyrion.thepredict.domain.EnemyKinematics;
import akyrion.thepredict.domain.HealthBand;
import akyrion.thepredict.domain.MobFacts;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.MobProbe;
import akyrion.thepredict.domain.ParticipantAlias;
import akyrion.thepredict.domain.ParticipantKind;
import akyrion.thepredict.domain.PatternMode;
import akyrion.thepredict.domain.PatternProfile;
import akyrion.thepredict.domain.PatternSettings;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.domain.TargetRelation;
import akyrion.thepredict.domain.ThreatTableState;
import akyrion.thepredict.domain.TickSignals;
import akyrion.thepredict.domain.Trend;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static akyrion.thepredict.engine.EngineFixtures.WORLD;
import static akyrion.thepredict.engine.EngineFixtures.far;
import static akyrion.thepredict.engine.EngineFixtures.player;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PZ-04, PZ-17, E-2 and E-9: the models are the encounter's, rings come with arrivals, the walk reaches every session
 * and facts keep hysteresis.
 */
class PatternDirectorTest {
    private final EngineFixtures.Sessions fixture = new EngineFixtures.Sessions();
    private final PatternInbox inbox = new PatternInbox();
    private final KinematicBook book = new KinematicBook();

    @Test
    void fortyTargetsOfFortyEncountersEachKeepTheirOwnEvidence() {
        var director = director(PatternMode.OBSERVE, () -> 0L);
        var sessions = new ArrayList<MobSession>();
        for (var index = 1; index <= 40; index++) {
            var session = fight(index, player(index));
            strikes(player(index), 12, 0);
            director.visit(session, 12);
            assertNotEquals(0, session.patternMask(), "target " + index);
            sessions.add(session);
        }
        for (var index = 0; index < sessions.size(); index++) {
            director.visit(sessions.get(index), 13);
            assertNotEquals(0, sessions.get(index).patternMask(), "target " + (index + 1) + " lost its evidence");
        }
        assertEquals(40, director.encountersTracked());
    }

    @Test
    void aClosedEncounterLeavesNoModelAndTheNextFightStartsEmpty() {
        var director = director(PatternMode.OBSERVE, () -> 0L);
        var enemy = player(7);
        var session = fight(1, enemy);
        strikes(enemy, 12, 0);
        director.visit(session, 12);
        assertNotEquals(0, session.patternMask());
        var encounter = fixture.encounters.find(session.encounter().orElseThrow()).orElseThrow();
        director.close(encounter);
        assertTrue(director.modelOf(enemy).isEmpty());
        assertFalse(inbox.participates(enemy), "no other encounter keeps the ring of the participant");
        assertEquals(0, director.encountersTracked());
        var next = fight(2, enemy);
        director.visit(next, 40);
        assertEquals(0, next.patternMask(), "the habits of the closed fight are not inherited");
    }

    @Test
    void aParticipantOfTwoEncountersFeedsBothModelsWithEveryToken() {
        var director = director(PatternMode.OBSERVE, () -> 0L);
        var enemy = player(3);
        var first = fight(1, enemy);
        var second = fight(2, enemy);
        director.visit(second, 1);
        strikes(enemy, 12, 2);
        director.visit(first, 14);
        director.visit(second, 14);
        assertNotEquals(0, first.patternMask());
        assertNotEquals(0, second.patternMask(), "the tokens the first encounter read reached the second model");
        var closing = fixture.encounters.find(first.encounter().orElseThrow()).orElseThrow();
        director.close(closing);
        assertTrue(inbox.participates(enemy), "the other open encounter keeps the ring");
        assertTrue(director.modelOf(enemy).isPresent());
    }

    @Test
    void aParticipantGetsItsRingWhenItArrivesWithoutAScanOfEveryEncounter() {
        director(PatternMode.OBSERVE, () -> 0L);
        var session = fight(1, player(1));
        var bystander = player(2);
        assertFalse(inbox.participates(bystander));
        assertTrue(fixture.encounters.addParticipant(session.encounter().orElseThrow(), bystander));
        assertTrue(inbox.participates(bystander), "the arrival admits the ring before any pattern step runs");
        inbox.offer(bystander, EnemyAction.STRIKE.ordinal(), 1);
        assertEquals(1, inbox.size(bystander));
    }

    @Test
    void ringsOfActorsThatLeftEveryEncounterAreSweptBeforeTheTableFills() {
        var director = director(PatternMode.OBSERVE, () -> 0L);
        for (var index = 0; index < PatternInbox.SLOTS; index++) {
            inbox.admit(new UUID(42, index));
        }
        director.step(1L, 1_000_000L);
        var late = new UUID(43, 1);
        inbox.admit(late);
        assertTrue(inbox.participates(late));
        inbox.offer(late, EnemyAction.STRIKE.ordinal(), 2);
        assertEquals(1, inbox.size(late));
    }

    @Test
    void everyActiveSessionIsVisitedWithoutAFixedCapOfSixtyFour() {
        var director = director(PatternMode.OBSERVE, () -> 0L);
        var sessions = new ArrayList<MobSession>();
        for (var index = 1; index <= 100; index++) {
            sessions.add(fixture.open(index));
        }
        director.step(5L, 1_000_000_000L);
        for (var session : sessions) {
            assertEquals(5L, session.patternSeenTick(), session.key().toString());
        }
    }

    @Test
    void aSpentBudgetResumesWhereThePreviousTickStopped() {
        var clock = new long[] {0L};
        var director = director(PatternMode.OBSERVE, () -> clock[0] += 10L);
        var sessions = new ArrayList<MobSession>();
        for (var index = 1; index <= 100; index++) {
            sessions.add(fixture.open(index));
        }
        for (var tick = 1L; tick <= 20L; tick++) {
            director.step(tick, 300L);
        }
        for (var session : sessions) {
            assertTrue(session.patternSeenTick() > 0, session.key() + " was never visited");
        }
    }

    @Test
    void theProfileModeLowersTheGlobalModeAndItsPolicyHoldsTheQuotaAgainstEntities() {
        var director = director(PatternMode.BIAS, () -> 0L);
        var strict = new PatternProfile(Optional.of(PatternMode.OBSERVE), BenchPolicy.FAIR, true);
        var session = fixture.registry.open(EngineFixtures.id(9), withPatterns(strict));
        fixture.encounters.assign(session, WORLD, player(9), far());
        session.recordTarget(player(9), 3.0);
        session.tickSignals(TickSignals.PRESENT);
        director.visit(session, 1);
        assertEquals(PatternMode.OBSERVE, session.patternMode());
        assertTrue(PatternCast.share(session) < 1.0, "a fair profile keeps the quota against an entity");
        var free = fixture.registry.open(EngineFixtures.id(10), EngineFixtures.profile("Guardian"));
        director.visit(free, 1);
        assertEquals(PatternMode.BIAS, free.patternMode());
        assertEquals(1.0, PatternCast.share(free), 0.0, "free against an entity uses the whole window");
    }

    @Test
    void inBiasAFactShowsOnlyAfterItsHysteresisWhileObserveShowsTheRawMask() {
        var director = director(PatternMode.BIAS, () -> 0L);
        var enemy = player(5);
        var session = fight(1, enemy);
        strikes(enemy, 12, 0);
        director.visit(session, 12);
        assertNotEquals(0, director.modelOf(enemy).orElseThrow().activeMask());
        assertEquals(0, session.patternMask(), "a fact needs factOnTicks of activity before it is shown");
        var on = PatternSettings.DEFAULT.factOnTicks();
        for (var tick = 13; tick < 13 + on; tick++) {
            strikes(enemy, 1, tick);
            director.visit(session, tick);
        }
        assertEquals(director.modelOf(enemy).orElseThrow().activeMask(), session.patternMask());
    }

    @Test
    void theVisitPublishesTheShownMaskOnTheBoardWithinTheTick() {
        var director = director(PatternMode.BIAS, () -> 0L);
        var enemy = player(6);
        var session = fight(1, enemy);
        var on = PatternSettings.DEFAULT.factOnTicks();
        for (var tick = 0; tick <= 12 + on; tick++) {
            strikes(enemy, 1, tick);
            director.visit(session, tick);
        }
        assertNotEquals(0, session.patternMask());
        var view = fixture.board.current(session.key().entityId());
        assertEquals(session.patternMask(), view.targetPatternMask(), "the vocabulary reads the mask of this visit");
        assertEquals(session.patternForecast(), view.forecast());
    }

    @Test
    void theStepStoresTheVarietyCapTheLocalPickReads() {
        var one = new PatternSettings(PatternMode.OBSERVE, 16, 2, 4, 16, 0.15, AirSwingPolicy.OBSERVE, 6, 20, 20, 32,
                0.98, 0.98, 0.05, 8, 3, 0.05, 4.0, 0.4, 2, 60, 0.5, 20, 400, 3, 300, 1, 40, 80, 2, 4);
        var director = director(PatternMode.BIAS, () -> 0L, one);
        var session = fight(1, player(4));
        director.visit(session, 1);
        assertEquals(1, session.patternVariety(), "DAL-04: the cap of this step, not the delivered default of 2");
    }

    @Test
    void theWiringRejectsANullInsteadOfHidingIt() {
        var director = director(PatternMode.OBSERVE, () -> 0L);
        assertThrows(NullPointerException.class, () -> director.useTap(null));
        assertThrows(NullPointerException.class, () -> director.useReader(null));
        assertThrows(NullPointerException.class, () -> director.useDiag(null));
        assertThrows(NullPointerException.class, () -> new PatternDiag().flush(null));
    }

    @Test
    void aMemberThatDoesNotSeeItsTargetIsNoWitnessAndOffersNoMovementOrShieldEdge() {
        var witnesses = new ArrayList<UUID>();
        var director = director(PatternMode.OBSERVE, () -> 0L);
        director.useReader(strafing(witnesses, false));
        var enemy = player(8);
        var session = fight(1, enemy);
        session.facts(capture(session, enemy, false));
        shieldToggles(director, session);
        assertFalse(witnesses.isEmpty());
        assertTrue(witnesses.stream().allMatch(new UUID(0, 0)::equals), "SP-04: no witness " + witnesses);
        assertEquals("none", director.modelOf(enemy).orElseThrow().tokens());
    }

    @Test
    void aMemberThatSeesItsTargetWitnessesItsStrafeAndItsShieldEdges() {
        var witnesses = new ArrayList<UUID>();
        var director = director(PatternMode.OBSERVE, () -> 0L);
        director.useReader(strafing(witnesses, false));
        var enemy = player(8);
        var session = fight(1, enemy);
        session.facts(capture(session, enemy, true));
        shieldToggles(director, session);
        assertTrue(witnesses.stream().allMatch(session.key().entityId()::equals), witnesses.toString());
        var tokens = director.modelOf(enemy).orElseThrow().tokens();
        assertTrue(tokens.contains("strafe_left") && tokens.contains("shield_up") && tokens.contains("shield_down"),
                tokens);
    }

    @Test
    void anInvisibleEnemyOffersNoMovementOrShieldEdgeEvenToAMemberThatSeesIt() {
        var director = director(PatternMode.OBSERVE, () -> 0L);
        director.useReader(strafing(new ArrayList<>(), true));
        var enemy = player(8);
        var session = fight(1, enemy);
        session.facts(capture(session, enemy, true));
        shieldToggles(director, session);
        assertEquals("none", director.modelOf(enemy).orElseThrow().tokens());
    }

    @Test
    void theLastCloseOfAnEnemyAndItsQuitForgetItsKinematicWord() {
        var director = director(PatternMode.OBSERVE, () -> 0L);
        director.useReader(strafing(new ArrayList<>(), false));
        var enemy = player(8);
        var first = fight(1, enemy);
        var second = fight(2, enemy);
        first.facts(capture(first, enemy, true));
        second.facts(capture(second, enemy, true));
        fixture.encounters.hearCloses(director::close);
        director.visit(first, 0);
        director.visit(second, 0);
        assertNotEquals(0L, book.word(enemy));
        fixture.encounters.leave(first);
        assertNotEquals(0L, book.word(enemy), "the other open encounter still watches the enemy");
        fixture.encounters.leave(second);
        assertEquals(0L, book.word(enemy), "SP-05: no encounter keeps the enemy, so its slot is free");
        assertEquals(-1, book.tick(enemy));
        var other = player(9);
        var third = fight(3, other);
        third.facts(capture(third, other, true));
        director.visit(third, 5);
        assertNotEquals(0L, book.word(other));
        director.forget(other);
        assertEquals(0L, book.word(other), "a player who quit leaves no word");
        assertFalse(inbox.participates(other));
    }

    @Test
    void anActivationSignalsOnceTheMembersThatTargetTheEnemyAndOnlyWhereTheModeShowsPatterns() {
        var heard = new ArrayList<String>();
        var director = director(PatternMode.BIAS, () -> 0L);
        director.useCues(cues(heard));
        var enemy = player(5);
        var first = fight(1, enemy);
        var second = fixture.open(2);
        fixture.encounters.assign(second, WORLD, enemy, key -> true);
        second.recordTarget(enemy, 3.0);
        var third = fixture.open(3);
        fixture.encounters.assign(third, WORLD, enemy, key -> true);
        var other = player(6);
        assertTrue(fixture.encounters.addParticipant(third.encounter().orElseThrow(), other));
        third.recordTarget(other, 3.0);
        assertEquals(first.encounter(), third.encounter());
        strikes(enemy, 12, 0);
        director.visit(first, 12);
        var active = director.modelOf(enemy).orElseThrow().activeMask();
        assertNotEquals(0, active);
        var expected = new ArrayList<String>();
        for (var pattern : EnemyPattern.values()) {
            if ((active & pattern.bit()) != 0) {
                expected.add(first.key().entityId() + " PREDICTOR_PATTERN_" + pattern.name());
                expected.add(second.key().entityId() + " PREDICTOR_PATTERN_" + pattern.name());
            }
        }
        assertEquals(expected, heard, "DOC-01: one signal per member that targets the enemy, none for the third");
        strikes(enemy, 12, 13);
        director.visit(first, 25);
        assertEquals(expected, heard, "a pattern that stays active sends no second signal");
    }

    @Test
    void observeSendsNoPatternSignalEvenWhenAPatternIsActive() {
        var heard = new ArrayList<String>();
        var director = director(PatternMode.OBSERVE, () -> 0L);
        director.useCues(cues(heard));
        var enemy = player(7);
        var session = fight(1, enemy);
        strikes(enemy, 12, 0);
        director.visit(session, 12);
        assertNotEquals(0, director.modelOf(enemy).orElseThrow().activeMask());
        assertEquals(List.of(), heard);
    }

    @Test
    void theQuotaOfAFairSessionIsTheConfiguredMaxExploitShare() {
        var director = director(PatternMode.BIAS, () -> 0L, settings(PatternSettings.DEFAULT.maxReadsPerTick(), 0.2));
        var fair = new PatternProfile(Optional.empty(), BenchPolicy.FAIR, true);
        var session = fixture.registry.open(EngineFixtures.id(9), withPatterns(fair));
        fixture.encounters.assign(session, WORLD, player(9), far());
        session.recordTarget(player(9), 3.0);
        session.tickSignals(TickSignals.PRESENT);
        assertEquals(PatternSettings.DEFAULT.maxExploitShare(), session.patternQuota(), 0.0);
        director.visit(session, 1);
        assertEquals(0.2, session.patternQuota(), 0.0, "DOC-06: patterns.maxExploitShare, not a fixed 0.5");
        assertEquals(Math.min(0.2, session.levers().exploitShare()), PatternCast.share(session), 0.0);
    }

    @Test
    void theKinematicReadsOfOneTickStopAtMaxReadsPerTickAndTheRestWait() {
        var witnesses = new ArrayList<UUID>();
        var metrics = new DecisionMetrics(() -> 0L);
        var director = director(PatternMode.OBSERVE, () -> 0L, settings(1, PatternSettings.DEFAULT.maxExploitShare()),
                metrics);
        director.useReader(strafing(witnesses, false));
        fight(1, player(1));
        fight(2, player(2));
        director.step(1L, 1_000_000_000L);
        assertEquals(1, witnesses.size(), "DOC-09: a tick capped at one read reads one enemy");
        assertEquals(1L, metrics.total(DecisionMetrics.PatternCounter.PATTERN_READS_DEFERRED));
        director.step(2L, 1_000_000_000L);
        assertEquals(2, witnesses.size(), "the deferred read comes on the next tick");
        assertNotEquals(0L, book.word(player(1)));
        assertNotEquals(0L, book.word(player(2)));
    }

    /** The delivered limits with another read cap and another quota. */
    private static PatternSettings settings(int maxReadsPerTick, double maxExploitShare) {
        var d = PatternSettings.DEFAULT;
        return new PatternSettings(d.mode(), d.hearingBlocks(), d.sampleTicks(), d.maxWatchedPerEncounter(),
                maxReadsPerTick, d.strafeBlocks(), d.airSwings(), d.mergeTicks(), d.idleTicks(), d.maxTokensPerSecond(),
                d.order2Contexts(), d.forgetting(), d.evidenceForgetting(), d.confidenceQuantile(), d.minTrials(),
                d.persistUpdates(), d.exitMargin(), d.changepointH(), d.forecastMinAccuracy(), d.histogramBinTicks(),
                d.maxMicrosPerTick(), maxExploitShare, d.maxCounterDelayTicks(), d.exploitWindowTicks(),
                d.restAfterSuccesses(), d.restTicks(), d.maxExploitedPatterns(), d.factOnTicks(), d.factOffTicks(),
                d.maxFactsPerParticipant(), d.maxFactsPerRequest());
    }

    /** A cue port that writes the mob and the signal of each cue. */
    private static CombatActions cues(List<String> heard) {
        return new CombatActions() {
            @Override
            public akyrion.thepredict.domain.ExecutionOutcome cast(MobKey mob,
                    akyrion.thepredict.domain.SkillChoice choice, akyrion.thepredict.domain.Movement movement) {
                return akyrion.thepredict.domain.ExecutionOutcome.NOT_ELIGIBLE;
            }

            @Override
            public boolean ready(MobKey mob, akyrion.thepredict.domain.SkillChoice choice) {
                return false;
            }

            @Override
            public void signalTactic(MobKey mob, akyrion.thepredict.domain.TacticId tactic) {
            }

            @Override
            public void addThreat(MobKey mob, UUID participantId, double amount) {
            }

            @Override
            public void cue(MobKey mob, akyrion.thepredict.domain.CueRequest request) {
                heard.add(mob.entityId() + " " + request.signal());
            }
        };
    }

    /** Visits once per sample period, raising and lowering the shield of the target on alternate visits. */
    private static void shieldToggles(PatternDirector director, MobSession session) {
        var period = Math.max(1, PatternSettings.DEFAULT.sampleTicks());
        for (var visit = 0; visit < 8; visit++) {
            var blocking = visit % 2 == 1 ? 1L << TickSignals.TARGET_BLOCKING : 0L;
            session.tickSignals(TickSignals.PRESENT | blocking);
            director.visit(session, (long) visit * period);
        }
    }

    /** The last capture of a session against {@code enemy}, whose target is seen or not. */
    private static MobFacts capture(MobSession session, UUID enemy, boolean visible) {
        var relation = new TargetRelation(new ParticipantAlias(ParticipantKind.PLAYER, 1), DistanceBand.MELEE,
                Trend.STEADY, true, visible, false, false, false);
        return new MobFacts(session.key(), HealthBand.HEALTHY, Optional.of(relation), Set.of(), Set.of(), Set.of(),
                Set.of());
    }

    /** A reader whose enemy strafes to the left on every sample, and invisible when asked; it keeps each witness. */
    private static CombatReader strafing(List<UUID> witnesses, boolean invisible) {
        var word = 1L | (long) EnemyKinematics.LEFT_OR_CLOSING << EnemyKinematics.LATERAL_SHIFT
                | (invisible ? 1L << EnemyKinematics.INVISIBLE : 0L);
        return new CombatReader() {
            @Override
            public long enemyKinematics(UUID participant, long previousStamp, UUID witness) {
                witnesses.add(witness);
                return word;
            }

            @Override
            public Optional<CombatFacts> capture(EncounterId encounter, List<MobKey> members, CaptureContext context) {
                return Optional.empty();
            }

            @Override
            public Optional<MobProbe> probe(MobKey mob) {
                return Optional.empty();
            }

            @Override
            public boolean inCombat(UUID entityId) {
                return true;
            }

            @Override
            public boolean withinFollowRange(MobKey mob, MobKey member) {
                return true;
            }

            @Override
            public boolean isActive(UUID entityId) {
                return true;
            }

            @Override
            public Optional<ThreatTableState> threatTable(MobKey mob) {
                return Optional.empty();
            }
        };
    }

    private PatternDirector director(PatternMode mode, java.util.function.LongSupplier clock) {
        return director(mode, clock, PatternSettings.DEFAULT);
    }

    private PatternDirector director(PatternMode mode, java.util.function.LongSupplier clock,
                                     PatternSettings settings) {
        return director(mode, clock, settings, new DecisionMetrics(() -> 0L));
    }

    private PatternDirector director(PatternMode mode, java.util.function.LongSupplier clock,
                                     PatternSettings settings, DecisionMetrics metrics) {
        var gate = new PatternModeGate();
        gate.offer(mode, true);
        var director = new PatternDirector(inbox, book, settings, gate, clock);
        director.bind(fixture.registry, fixture.encounters, metrics);
        return director;
    }

    /** A session in an encounter of its own against {@code enemy}. */
    private MobSession fight(int value, UUID enemy) {
        var session = fixture.open(value);
        fixture.encounters.assign(session, WORLD, enemy, far());
        session.recordTarget(enemy, 3.0);
        session.tickSignals(TickSignals.PRESENT);
        return session;
    }

    private void strikes(UUID enemy, int count, int tick) {
        inbox.admit(enemy);
        for (var index = 0; index < count; index++) {
            inbox.offer(enemy, EnemyAction.STRIKE_EARLY.ordinal(), tick + index);
        }
    }

    private static TacticalProfile withPatterns(PatternProfile patterns) {
        var base = EngineFixtures.profile("Guardian");
        return base.withOverlay(base.timing(), base.zone(), base.group(), patterns, base.decision(),
                base.distillMode());
    }
}
