package akyrion.thepredict.engine;

import akyrion.thepredict.AllocationProbe;
import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.GroupMode;
import akyrion.thepredict.domain.MemoryMode;
import akyrion.thepredict.domain.MemorySettings;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.PlayerMemory;
import akyrion.thepredict.domain.PlayerMemoryCodec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The close of an encounter reaches every player admitted to it (EFO-13), and the per-tick drain reads only the loads
 * still pending (EFO-14).
 */
class MemoryHostCloseTest {
    private final UUID first = new UUID(7, 1);
    private final UUID second = new UUID(7, 2);

    private static MemorySettings settings() {
        return new MemorySettings(MemoryMode.RECORD, 0.9, 30, 4, 0.25, 6, 4, 3, 3, 0.2, 8, 2, 60, 512, 16, 10, 60, 8,
                1024, 64, 180, 1_000_000, true, "sqlite");
    }

    private static MemoryHost host() {
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
        return new MemoryHost(settings(), new MemoryCounters(), aggregates, DecisionTap.NONE,
                new MemoryHost.Presence() {
                    @Override
                    public boolean online(UUID subject) {
                        return true;
                    }

                    @Override
                    public boolean exempt(UUID subject) {
                        return false;
                    }
                }, () -> 86_400_000L);
    }

    /** An encounter whose boss fought both players: it dealt damage, so its clock fought. */
    private Encounter fought(long id) {
        var encounter = new Encounter(new EncounterId(id), new UUID(0, id), 0);
        var boss = new MobSession(new MobKey(new UUID(id, 9), 0), EngineFixtures.profile("boss"));
        encounter.addMember(boss);
        encounter.addParticipant(first);
        encounter.addParticipant(second);
        boss.difficulty().clock().dealt(10, first, first, 0.3, 0.7);
        boss.difficulty().clock().quiet(500, 200);
        return encounter;
    }

    @Test
    void aPlayerDroppedBeforeTheCloseStillGetsItsDigestAndLeavesNoAdmission() {
        var host = host();
        var encounter = fought(61);
        host.admit(encounter, first, 10);
        host.admit(encounter, second, 10);
        assertEquals(2, host.openAdmissions());
        encounter.removeParticipant(second);
        host.closeEncounter(encounter, 600, GroupMode.OFF);
        assertEquals(0, host.openAdmissions(), "no admission outlives its encounter");
        assertTrue(host.digests().pending(first).size() > 0);
        assertTrue(host.digests().pending(second).size() > 0, "the player who left before the close has a digest");
    }

    @Test
    void manyClosedEncountersLeaveNothingBehind() {
        var host = host();
        for (var id = 100L; id < 400L; id++) {
            var encounter = fought(id);
            host.admit(encounter, first, 10);
            host.admit(encounter, second, 10);
            encounter.removeParticipant(first);
            host.closeEncounter(encounter, 600, GroupMode.OFF);
        }
        assertEquals(0, host.openAdmissions());
    }

    @Test
    void aDrainWithAFullCacheAndNothingPendingReadsNothingAndAllocatesNothing() {
        var host = host();
        var bytes = PlayerMemoryCodec.encode(PlayerMemory.empty(), 8).bytes();
        for (var subject = 0; subject < settings().cacheSubjects(); subject++) {
            host.cache().loaded(new UUID(8, subject), bytes, true, false, 1L);
        }
        host.cache().beginLoad(new UUID(9, 1), 0L);
        assertEquals(1, host.cache().pendingLoads());
        host.drain(List.of(), (settings().loadTimeoutSeconds() + 1) * 1_000_000_000L);
        assertEquals(0, host.cache().pendingLoads(), "the timed-out load failed and left the pending set");
        assertEquals(akyrion.thepredict.domain.MemoryEntryState.FAILED,
                host.cache().slot(new UUID(9, 1)).orElseThrow().state());
        var none = List.<PrefetchHint>of();
        var bytesAllocated = AllocationProbe.bytesAllocatedBy(() -> host.drain(none, 5_000_000_000_000L));
        assertTrue(bytesAllocated < AllocationProbe.TOLERANCE_BYTES, bytesAllocated + " bytes");
    }
}
