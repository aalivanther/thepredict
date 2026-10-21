package akyrion.thepredict.engine;

import akyrion.thepredict.domain.BenchPolicy;
import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.EnemyPattern;
import akyrion.thepredict.domain.FightOutcome;
import akyrion.thepredict.domain.GroupMode;
import akyrion.thepredict.domain.MemoryFact;
import akyrion.thepredict.domain.MemoryMode;
import akyrion.thepredict.domain.MemoryProfile;
import akyrion.thepredict.domain.MemorySettings;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.PatternMode;
import akyrion.thepredict.domain.PatternProfile;
import akyrion.thepredict.domain.PlayerMemory;
import akyrion.thepredict.domain.PlayerMemoryCodec;
import akyrion.thepredict.domain.ProfileHash;
import akyrion.thepredict.domain.ProfileMemory;
import akyrion.thepredict.domain.TacticalProfile;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * E-2: the engine reads the {@code Patterns} and {@code Memory} sections of the profile. {@code Patterns.Facts: false}
 * keeps pattern facts out of the body and of the signature; {@code Memory.Mode} lowers the mode of the admission, an
 * encounter whose profiles turned memory off writes no digest, and {@code Memory.Facts: false} drops only the phrase.
 */
class ProfileSectionsTest {
    private static final UUID PLAYER = new UUID(7, 1);
    private static final long TODAY = 10;

    @Test
    void withoutPatternFactsNothingIsAdmittedAndTheSignatureCarriesNoPatternBits() {
        var shown = patterned(true);
        var hidden = patterned(false);
        assertEquals(java.util.Set.of(EnemyPattern.SPAMS_CLICKS, EnemyPattern.CRIT_JUMPER), shown.admittedPatterns());
        assertTrue(hidden.admittedPatterns().isEmpty(), "Patterns.Facts: false keeps the facts out of the body");
        assertEquals(2, (LiveSignature.of(shown) >>> 19) & 3);
        assertEquals(0, (LiveSignature.of(hidden) >>> 19) & 3, "and out of the signature bits 19 and 20");
    }

    @Test
    void aProfileThatTurnsMemoryOffAdmitsOffAndWritesNoDigest() {
        var host = host(MemoryMode.RECORD);
        var inherits = fought(61, new MemoryProfile(Optional.empty(), true));
        assertEquals(MemoryMode.RECORD, host.admit(inherits, PLAYER, TODAY).mode());
        host.closeEncounter(inherits, 600, GroupMode.OFF);
        assertTrue(host.digests().pending(PLAYER).size() > 0);

        var quiet = host(MemoryMode.RECORD);
        var off = fought(62, new MemoryProfile(Optional.of(MemoryMode.OFF), true));
        assertEquals(MemoryMode.OFF, quiet.admit(off, PLAYER, TODAY).mode(), "the profile lowers the global mode");
        quiet.closeEncounter(off, 600, GroupMode.OFF);
        assertTrue(quiet.digests().pending(PLAYER).isEmpty(), "memory off writes nothing");
    }

    @Test
    void theLowestModeOfTheEncounterApplies() {
        var host = host(MemoryMode.FULL);
        load(host);
        var encounter = fought(71, new MemoryProfile(Optional.empty(), true),
                new MemoryProfile(Optional.of(MemoryMode.SEED), true));
        var admission = host.admit(encounter, PLAYER, TODAY);
        assertEquals(MemoryMode.SEED, admission.mode());
        assertTrue(admission.fact().isEmpty(), "seed sends no phrase");
    }

    @Test
    void withoutMemoryFactsTheAdmissionKeepsItsModeButNoPhraseAndOneMemberIsEnough() {
        var full = host(MemoryMode.FULL);
        load(full);
        var shown = full.admit(fought(81, new MemoryProfile(Optional.empty(), true)), PLAYER, TODAY);
        assertEquals(Optional.of(MemoryFact.LOST_LAST), shown.fact());

        var hidden = host(MemoryMode.FULL);
        load(hidden);
        var bare = hidden.admit(fought(82, new MemoryProfile(Optional.empty(), false)), PLAYER, TODAY);
        assertEquals(MemoryMode.FULL, bare.mode());
        assertTrue(bare.fact().isEmpty(), "Memory.Facts: false drops the phrase");

        var mixed = host(MemoryMode.FULL);
        load(mixed);
        var shared = mixed.admit(fought(83, new MemoryProfile(Optional.empty(), true),
                new MemoryProfile(Optional.empty(), false)), PLAYER, TODAY);
        assertTrue(shared.fact().isEmpty(), "the phrase needs every member of the encounter to allow it");
    }

    private static MobSession patterned(boolean facts) {
        var base = EngineFixtures.profile("Guardian");
        var session = new MobSession(new MobKey(new UUID(5, facts ? 1 : 2), 0),
                sections(base, new PatternProfile(Optional.empty(), BenchPolicy.FREE, facts), base.memory()));
        session.patternMode(PatternMode.FULL);
        session.publishPattern(EnemyPattern.SPAMS_CLICKS.bit() | EnemyPattern.CRIT_JUMPER.bit(), -1);
        return session;
    }

    private static TacticalProfile sections(TacticalProfile base, PatternProfile patterns, MemoryProfile memory) {
        return new TacticalProfile(base.id(), base.mythicMobId(), base.objective(), base.tactics(), base.choices(),
                base.contexts(), base.movement(), base.health(), base.threat(), base.baseAi(), base.timing(),
                base.participants(), base.difficulty(), base.stances(), base.telegraph(), base.poise(), base.zone(),
                base.group(), patterns, memory);
    }

    /** An encounter whose bosses fought the player, one boss per memory section, all of profile {@code boss_ai}. */
    private static Encounter fought(long id, MemoryProfile... memories) {
        var encounter = new Encounter(new EncounterId(id), new UUID(0, id), 0);
        var base = EngineFixtures.profile("boss");
        for (var index = 0; index < memories.length; index++) {
            var boss = new MobSession(new MobKey(new UUID(id, 9 + index), 0),
                    sections(base, base.patterns(), memories[index]));
            encounter.addMember(boss);
            boss.difficulty().clock().dealt(10, PLAYER, PLAYER, 0.3, 0.7);
            boss.difficulty().clock().quiet(500, 200);
        }
        encounter.addParticipant(PLAYER);
        return encounter;
    }

    /** A record whose last fight against {@code boss_ai} was a loss today. */
    private static void load(MemoryHost host) {
        var rungs = new int[ProfileMemory.RUNGS];
        Arrays.fill(rungs, ProfileMemory.NONE);
        var lost = new ProfileMemory(ProfileHash.of("boss_ai"), 1, 0, 1, 0, FightOutcome.LOSS, TODAY, 0f, 0f, 0f,
                rungs, List.of());
        var memory = new PlayerMemory(TODAY, TODAY, 1, Optional.empty(), Optional.empty(), Optional.empty(),
                List.of(lost));
        host.cache().loaded(PLAYER, PlayerMemoryCodec.encode(memory, 8).bytes(), true, false, 1L);
    }

    private static MemoryHost host(MemoryMode mode) {
        var aggregates = new AggregateCache(new AggregateStore() {
            @Override
            public StorageHealth health() {
                return new StorageHealth(StorageState.READY, "", 0, 0, 0);
            }

            @Override
            public boolean submit(AggregateBatch batch) {
                return true;
            }

            @Override
            public boolean request(AggregateQuery query) {
                return true;
            }
        }, new StorageMetrics(), 4096, 100, 4, new Inbox<>(8));
        var settings = new MemorySettings(mode, 0.9, 30, 4, 0.25, 6, 4, 3, 3, 0.2, 8, 2, 60, 512, 16, 10, 60, 8, 1024,
                64, 180, 1_000_000, true, "sqlite");
        var host = new MemoryHost(settings, new MemoryCounters(), aggregates, DecisionTap.NONE,
                new MemoryHost.Presence() {
                    @Override
                    public boolean online(UUID subject) {
                        return true;
                    }

                    @Override
                    public boolean exempt(UUID subject) {
                        return false;
                    }
                }, () -> TODAY * 86_400_000L);
        host.drain(List.of(), 1L);
        return host;
    }
}
