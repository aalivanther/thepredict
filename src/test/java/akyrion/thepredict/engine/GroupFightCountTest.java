package akyrion.thepredict.engine;

import akyrion.thepredict.domain.BenchPolicy;
import akyrion.thepredict.domain.DifficultyGlobals;
import akyrion.thepredict.domain.DifficultySection;
import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.FightEventKind;
import akyrion.thepredict.domain.FightOutcome;
import akyrion.thepredict.domain.GroupCaps;
import akyrion.thepredict.domain.GroupMode;
import akyrion.thepredict.domain.GroupSettings;
import akyrion.thepredict.domain.LeaderDeath;
import akyrion.thepredict.domain.MemoryFact;
import akyrion.thepredict.domain.MemorySettings;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.PartyBand;
import akyrion.thepredict.domain.PlanQuestion;
import akyrion.thepredict.domain.PlayerMemory;
import akyrion.thepredict.domain.ProfileHash;
import akyrion.thepredict.domain.ProfileMemory;
import akyrion.thepredict.domain.RoleCapabilities;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.engine.duel.SkirmishSimulator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroupFightCountTest {
    private final UUID player = new UUID(8, 8);

    @Test
    void fiveOfOneProfileAreOneLossAndNotLostMany() {
        var members = new ArrayList<GroupFightClose.Member>();
        for (var index = 0; index < 5; index++) {
            members.add(new GroupFightClose.Member("boss", false, false, true, index == 0, 0.2, 20));
        }
        var closed = GroupFightClose.close(player, "boss", "boss", members, true, GroupMode.FAIR, PartyBand.ONE, 1,
                1f, 4f, 0.2);
        assertEquals(1, closed.digests().size());
        var memory = PlayerMemoryUpdater.apply(PlayerMemory.empty(), closed.digests(), 10);
        var profile = memory.profiles().getFirst();
        assertEquals(1, profile.fights());
        assertEquals(1, profile.losses());
        assertTrue(akyrion.thepredict.engine.MemoryFactSelector.select(memory, "boss", 10, 60).orElseThrow()
                != MemoryFact.LOST_MANY);
    }

    @Test
    void aBossAndFourMinionsCountTwiceAndOnlyTheAnchorGetsTheSample() {
        var members = new ArrayList<GroupFightClose.Member>();
        members.add(new GroupFightClose.Member("A", false, false, true, true, 0.4, 100));
        for (var index = 0; index < 4; index++) {
            members.add(new GroupFightClose.Member("B", false, true, false, false, 0.6, 20));
        }
        var closed = GroupFightClose.close(player, "A", "A", members, true, GroupMode.FAIR, PartyBand.ONE, 1, 2f, 5f,
                0.2);
        assertEquals(2, closed.digests().size());
        var anchor = ProfileHash.of("A");
        for (var digest : closed.digests()) {
            assertEquals(digest.profileHash() == anchor, digest.difficulty().isPresent());
            if (digest.profileHash() == anchor) {
                assertEquals(DifficultySection.GROUP, digest.section());
            }
        }
        var memory = PlayerMemoryUpdater.apply(PlayerMemory.empty(), closed.digests(), 3);
        assertEquals(1, memory.fights());
        assertEquals(2, memory.profiles().size());
    }

    @Test
    void belowFairEmitsNoSampleAndATieTakesTheSmallerHash() {
        var members = List.of(
                new GroupFightClose.Member("A", false, true, false, false, 0.5, 10),
                new GroupFightClose.Member("A", false, true, false, false, 0.5, 10),
                new GroupFightClose.Member("B", false, true, false, false, 0.5, 10),
                new GroupFightClose.Member("B", false, true, false, false, 0.5, 10));
        var off = GroupFightClose.close(player, "", "", members, false, GroupMode.OFF, PartyBand.ONE, 1, 1f, 4f, 0.2);
        assertTrue(off.digests().stream().allMatch(digest -> digest.difficulty().isEmpty()));
        var expected = Integer.compareUnsigned(ProfileHash.of("A"), ProfileHash.of("B")) < 0 ? "A" : "B";
        assertEquals(expected, GroupFightClose.anchor("", List.of("A", "A", "B", "B")));
        var moved = GroupFightClose.close(player, "A", "B", members, false, GroupMode.FAIR, PartyBand.ONE, 1, 1f, 4f,
                0.2);
        assertTrue(moved.anchorChanged());
        assertTrue(moved.digests().stream().filter(FightDigest::anchorChanged).allMatch(digest ->
                digest.profileHash() == ProfileHash.of("B") && digest.difficulty().isPresent()));
    }

    @Test
    void oneMobStaysOnSolo() {
        var closed = GroupFightClose.close(player, "solo", "solo", List.of(new GroupFightClose.Member("solo", false,
                true, false, false, 1, 10)), false, GroupMode.OFF, PartyBand.ONE, 1, 1f, 3f, 0.2);
        assertEquals(DifficultySection.SOLO, closed.digests().getFirst().section());
        assertEquals(FightOutcome.WIN, closed.digests().getFirst().outcome());
        var memory = PlayerMemoryUpdater.apply(PlayerMemory.empty(), closed.digests(), 1);
        assertEquals(ProfileMemory.NONE, memory.profiles().getFirst().rungs()[1]);
    }

    @Test
    void theGroupSampleUsesTheClockAndTheSkirmishCountsFairness() {
        var measured = SkirmishSimulator.run(new SkirmishSimulator.Fight("group", 3, 1, false, 80),
                SkirmishSimulator.Arm.FAIR, 27L);
        var off = SkirmishSimulator.run(new SkirmishSimulator.Fight("group", 3, 1, false, 80),
                SkirmishSimulator.Arm.OFF, 27L);
        assertEquals(0, measured.fairnessViolations());
        assertTrue(measured.impacts() != off.impacts() || measured.denials() != off.denials()
                || measured.transcript().length() != off.transcript().length());
        var encounter = new Encounter(new EncounterId(27), new UUID(1, 1), 0);
        var bossSession = new MobSession(new MobKey(new UUID(2, 1), 0), EngineFixtures.profile("boss"));
        var addSession = new MobSession(new MobKey(new UUID(2, 2), 0), EngineFixtures.profile("add"));
        encounter.addMember(bossSession);
        encounter.addMember(addSession);
        encounter.addParticipant(player);
        encounter.addParticipant(new UUID(9, 9));
        var clock = new GroupFightClock();
        clock.noteMembers(2);
        clock.begin("boss_ai", 2);
        clock.noteRung(4);
        clock.noteHit(0, player, 10, false, "boss_ai");
        clock.noteHit(0, player, 30, false, "boss_ai");
        bossSession.groupFight(clock, 0);
        addSession.groupFight(clock, 0);
        lead(bossSession, true);
        lead(addSession, false);
        var bossClock = bossSession.difficulty().clock();
        bossClock.takenFrom(10, player, player, 0.02);
        bossClock.takenFrom(50, player, player, 0.03);
        bossClock.mobDied();
        bossSession.notePatternExploit(0);
        var addClock = addSession.difficulty().clock();
        addClock.takenFrom(10, player, player, 0.4);
        addClock.mobDied();
        assertEquals(8, bossSession.difficulty().rung());
        assertTrue(bossClock.lnElapsed() != clock.lnElapsed(player));
        var notes = new MemoryFightNotes();
        var digests = notes.digests(encounter, player, GroupMode.FAIR, 1L, MemorySettings.DEFAULT);
        var boss = digests.stream().filter(digest -> digest.profileHash() == ProfileHash.of("boss_ai")).findFirst()
                .orElseThrow();
        assertEquals(DifficultySection.GROUP, boss.section());
        assertEquals(PartyBand.TWO_THREE, boss.band());
        assertEquals(FightOutcome.ASSIST, boss.outcome());
        assertEquals(clock.lnElapsed(player), boss.difficulty().orElseThrow().lnTtk(), 1e-6);
        assertEquals(clock.rung(), boss.difficulty().orElseThrow().startRungEstimate(), 0f);
        assertTrue(boss.counters().stream().anyMatch(counter -> counter.pattern() == 0 && counter.tries() > 0f));
        var add = digests.stream().filter(digest -> digest.profileHash() == ProfileHash.of("add_ai")).findFirst()
                .orElseThrow();
        assertTrue(add.difficulty().isEmpty());
        var below = notes.digests(encounter, player, GroupMode.OFF, 1L, MemorySettings.DEFAULT);
        assertTrue(below.stream().allMatch(digest -> digest.difficulty().isEmpty()
                && digest.section() == DifficultySection.GROUP));
        var folded = PlayerMemoryUpdater.apply(PlayerMemory.empty(), below, 5);
        var row = folded.profiles().stream().filter(profile -> profile.hash() == ProfileHash.of("boss_ai"))
                .findFirst().orElseThrow();
        for (var rung : row.rungs()) {
            assertEquals(ProfileMemory.NONE, rung);
        }
    }

    @Test
    void aPlayerDeathIsTheDeathTheMembersNotedAndNotATargetDiedOfTheClock() {
        var encounter = new Encounter(new EncounterId(29), new UUID(1, 2), 0);
        var session = new MobSession(new MobKey(new UUID(4, 1), 0), EngineFixtures.profile("boss"));
        encounter.addMember(session);
        encounter.addParticipant(player);
        var other = new UUID(9, 9);
        encounter.addParticipant(other);
        var clock = session.difficulty().clock();
        clock.dealt(10, player, player, 0.2, 0.5);
        session.noteParticipantDeath(player, true);
        clock.targetDied(30);
        assertFalse(clock.killingBlow());
        var digests = new MemoryFightNotes().digests(encounter, player, GroupMode.FAIR, 3L, MemorySettings.DEFAULT);
        assertEquals(FightOutcome.LOSS, digests.getFirst().outcome(), "the player died while the boss fought him");
        assertEquals(clock.lnElapsed(), digests.getFirst().difficulty().orElseThrow().lnTtk(), 1e-6);
        var bystander = new MemoryFightNotes().digests(encounter, other, GroupMode.FAIR, 3L, MemorySettings.DEFAULT);
        assertFalse(bystander.isEmpty());
        assertNotEquals(FightOutcome.LOSS, bystander.getFirst().outcome(),
                "the target that died was another player, so this one did not lose");
    }

    @Test
    void beginDoesNotReplaceTheLeaderWithTheFirstHitter() {
        var clock = new GroupFightClock();
        clock.noteMembers(2);
        clock.leaderChanged("leader_ai");
        clock.noteHit(0, player, 10, false, "minion_ai");
        assertEquals("leader_ai", clock.anchor());
        assertEquals("leader_ai", clock.openedAnchor());
        assertFalse(clock.anchorChanged());
        clock.leaderChanged("leader_ai");
        assertFalse(clock.anchorChanged());
        clock.leaderChanged("other_ai");
        assertTrue(clock.anchorChanged());
        assertEquals("leader_ai", clock.openedAnchor());
    }

    @Test
    void theSampleStaysOnTheLeaderWhenTheFirstHitIsAMinion() {
        var encounter = new Encounter(new EncounterId(35), new UUID(1, 5), 0);
        var leader = member(encounter, 6, 1, "leader");
        var minion = member(encounter, 6, 2, "minion");
        var second = member(encounter, 6, 3, "minion");
        var third = member(encounter, 6, 4, "minion");
        encounter.addParticipant(player);
        lead(leader, true);
        var clock = new GroupFightClock();
        clock.noteMembers(4);
        for (var session : List.of(minion, second, third, leader)) {
            session.groupFight(clock, 0);
        }
        clock.noteHit(0, player, 10, false, "minion_ai");
        clock.noteRung(3);
        hit(minion);
        hit(second);
        hit(third);
        hit(leader);
        var digests = new MemoryFightNotes().digests(encounter, player, GroupMode.FAIR, 6L, MemorySettings.DEFAULT);
        assertTrue(sampleOn(digests, "leader_ai"));
        assertFalse(sampleOn(digests, "minion_ai"));
        assertFalse(digests.stream().anyMatch(FightDigest::anchorChanged));
    }

    @Test
    void theShareWeightsMaxHealthAndStaysAnAssistAtTheDefaultFloor() {
        var encounter = new Encounter(new EncounterId(33), new UUID(1, 4), 0);
        var heavy = member(encounter, 5, 1, "tank");
        var light = member(encounter, 5, 2, "tank");
        encounter.addParticipant(player);
        var heavyClock = heavy.difficulty().clock();
        var lightClock = light.difficulty().clock();
        heavyClock.noteMaxHealth(100);
        lightClock.noteMaxHealth(10);
        heavyClock.takenFrom(10, player, player, 0.10);
        lightClock.takenFrom(10, player, player, 1);
        heavyClock.mobDied();
        lightClock.mobDied();
        assertEquals(100f, heavyClock.summary().orElseThrow().mobMaxHealth(), 0f);
        assertEquals(10f, lightClock.summary().orElseThrow().mobMaxHealth(), 0f);
        assertEquals(100, heavyClock.summary().orElseThrow().targetDamageSharePermille());
        assertEquals(1000, lightClock.summary().orElseThrow().targetDamageSharePermille());
        var win = new MemoryFightNotes().digests(encounter, player, GroupMode.FAIR, 5L, floor(0.10));
        var assist = new MemoryFightNotes().digests(encounter, player, GroupMode.FAIR, 5L, floor(0.20));
        assertEquals(FightOutcome.WIN, win.getFirst().outcome());
        assertEquals(FightOutcome.ASSIST, assist.getFirst().outcome());
    }

    @Test
    void aDisengageLeavesEveryRungMissing() {
        var encounter = new Encounter(new EncounterId(31), new UUID(1, 3), 0);
        var session = member(encounter, 4, 2, "boss");
        encounter.addParticipant(player);
        var clock = session.difficulty().clock();
        clock.takenFrom(10, player, player, 0.2);
        var quiet = DifficultyGlobals.DEFAULT.disengageTicks();
        assertEquals(Optional.of(FightEventKind.DISENGAGED), clock.quiet(10L + quiet, quiet));
        var digests = new MemoryFightNotes().digests(encounter, player, GroupMode.FAIR, 4L, MemorySettings.DEFAULT);
        assertTrue(digests.getFirst().difficulty().isEmpty());
        assertRungsMissing(PlayerMemoryUpdater.apply(PlayerMemory.empty(), digests, 1));
    }

    @Test
    void aMobDeathWithoutDealtDamageDropsTheSample() {
        var encounter = new Encounter(new EncounterId(32), new UUID(1, 7), 0);
        var session = member(encounter, 4, 3, "boss");
        encounter.addParticipant(player);
        var clock = session.difficulty().clock();
        clock.takenFrom(10, player, player, 0.0);
        clock.mobDied();
        assertTrue(clock.mobDiedWithoutEvidence());
        var digests = new MemoryFightNotes().digests(encounter, player, GroupMode.FAIR, 4L, MemorySettings.DEFAULT);
        assertTrue(digests.getFirst().difficulty().isEmpty());
    }

    @Test
    void anEvictedGroupClockDoesNotMoveTheRung() {
        var encounter = new Encounter(new EncounterId(43), new UUID(1, 8), 0);
        var boss = member(encounter, 9, 1, "boss");
        var add = member(encounter, 9, 2, "add");
        encounter.addParticipant(player);
        var clock = new GroupFightClock();
        clock.noteMembers(2);
        clock.noteRung(4);
        boss.groupFight(clock, 0);
        add.groupFight(clock, 0);
        lead(boss, true);
        clock.noteHit(0, player, 10, false, "add_ai");
        clock.noteHit(1, new UUID(9, 9), 40, false, "add_ai");
        var quiet = DifficultyGlobals.DEFAULT.disengageTicks();
        assertEquals(1, clock.quiet(10L + quiet, quiet));
        assertTrue(clock.evictQuietest() >= 0);
        clock.close();
        assertEquals(FightEventKind.EVICTED, clock.summary().orElseThrow().kind());
        hit(boss);
        hit(add);
        var digests = new MemoryFightNotes().digests(encounter, player, GroupMode.FAIR, 9L, MemorySettings.DEFAULT);
        assertTrue(digests.stream().allMatch(digest -> digest.difficulty().isEmpty()));
        assertRungsMissing(PlayerMemoryUpdater.apply(PlayerMemory.empty(), digests, 3));
    }

    @Test
    void aFairGlobalWithTheProfileOffEmitsNoGroupSample() {
        var encounter = new Encounter(new EncounterId(39), new UUID(1, 6), 0);
        var first = new MobSession(new MobKey(new UUID(8, 1), 0), offProfile("alpha"));
        var second = new MobSession(new MobKey(new UUID(8, 2), 0), offProfile("beta"));
        encounter.addMember(first);
        encounter.addMember(second);
        encounter.addParticipant(player);
        assertEquals(GroupMode.OFF, first.profile().group().effective(GroupMode.FAIR));
        assertTrue(first.groupClock().isEmpty());
        hit(first);
        hit(second);
        var digests = new MemoryFightNotes().digests(encounter, player, GroupMode.FAIR, 8L, MemorySettings.DEFAULT);
        assertTrue(digests.stream().allMatch(digest -> digest.difficulty().isEmpty()
                && digest.section() == DifficultySection.GROUP));
        assertRungsMissing(PlayerMemoryUpdater.apply(PlayerMemory.empty(), digests, 2));
    }

    private MobSession member(Encounter encounter, int key, int salt, String name) {
        var session = new MobSession(new MobKey(new UUID(key, salt), 0), EngineFixtures.profile(name));
        encounter.addMember(session);
        return session;
    }

    private static void lead(MobSession session, boolean leader) {
        session.groupBooks(new GroupBooks(new AttackLedger(), new TellLedger(), GroupCaps.DEFAULT, GroupMode.FAIR, 0,
                0, BenchPolicy.FREE, false, leader, 1, 1, false));
    }

    private void hit(MobSession session) {
        var clock = session.difficulty().clock();
        clock.noteMaxHealth(20);
        clock.takenFrom(10, player, player, 0.2);
        clock.mobDied();
    }

    private static boolean sampleOn(List<FightDigest> digests, String profile) {
        return digests.stream().filter(digest -> digest.profileHash() == ProfileHash.of(profile)).findFirst()
                .orElseThrow().difficulty().isPresent();
    }

    private static void assertRungsMissing(PlayerMemory memory) {
        for (var profile : memory.profiles()) {
            for (var rung : profile.rungs()) {
                assertEquals(ProfileMemory.NONE, rung);
            }
        }
    }

    private static MemorySettings floor(double share) {
        var base = MemorySettings.DEFAULT;
        return new MemorySettings(base.mode(), base.fightDecay(), base.halfLifeDays(), base.crossFightH(),
                base.styleBreakFactor(), base.seedMaxTrials(), base.minFightTrials(), base.rungPriorFights(),
                base.maxRungOffset(), share, base.maxProfilesPerPlayer(), base.maxFactsPerRequest(),
                base.factMaxAgeDays(), base.cacheSubjects(), base.maxHintsPerTick(), base.loadTimeoutSeconds(),
                base.refreshSeconds(), base.maxPendingDigestsPerSubject(), base.maxPendingSubjects(),
                base.maxSubjectsPerBatch(), base.retentionDays(), base.maxRows(), base.allowUnverified(),
                base.backend());
    }

    private static TacticalProfile offProfile(String name) {
        var base = EngineFixtures.profile(name);
        var group = new GroupSettings(Optional.of(GroupMode.OFF), "", 0, 1, Set.of(), LeaderDeath.SUCCEED,
                BenchPolicy.FREE, Optional.empty(), PlanQuestion.ON_CHANGE, RoleCapabilities.NONE);
        return new TacticalProfile(base.id(), base.mythicMobId(), base.objective(), base.tactics(), base.choices(),
                base.contexts(), base.movement(), base.health(), base.threat(), base.baseAi(), base.timing(),
                base.participants(), base.difficulty(), base.stances(), base.telegraph(), base.poise(), base.zone(),
                group);
    }
}
