package akyrion.thepredict.engine;

import akyrion.thepredict.domain.DifficultySample;
import akyrion.thepredict.domain.DifficultySection;
import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.FightOutcome;
import akyrion.thepredict.domain.GroupMode;
import akyrion.thepredict.domain.MemoryDecoded;
import akyrion.thepredict.domain.MemoryMode;
import akyrion.thepredict.domain.MemorySettings;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.PartyBand;
import akyrion.thepredict.domain.PlayerMemory;
import akyrion.thepredict.domain.PlayerMemoryCodec;
import akyrion.thepredict.domain.ProfileHash;
import akyrion.thepredict.domain.StyleMemory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryAdmissionViewTest {
    @Test
    void aPendingDigestChangesTheNextSeed() {
        var subject = new UUID(3, 3);
        var host = host();
        var bytes = load(host, subject);
        var cold = host.admit(encounter(41, subject), subject, 10);
        assertEquals(0.0, cold.seedTrialsTotal(), 0.0);
        assertEquals(8, cold.startRung());
        var style = style();
        host.useStyles(id -> id.equals(subject) ? Optional.of(style) : Optional.empty());
        host.closeEncounter(encounter(41, subject), 20, GroupMode.FAIR);
        host.digests().add(digest(subject));
        var preview = host.cache().view(subject, List.of(), host.digests().pending(subject), Optional.of(style),
                settings());
        assertTrue(preview.fights() > 0);
        assertTrue(preview.style().isPresent());
        var next = host.admit(encounter(42, subject), subject, 10);
        assertTrue(next.seedTrialsTotal() > 0.0);
        assertTrue(next.startRung() < cold.startRung());
        assertEquals(0, fights(host, subject, bytes));
    }

    @Test
    void anUnconfirmedBatchChangesTheNextSeedAndAnAppliedBatchDoesNot() {
        var subject = new UUID(4, 4);
        var aggregates = aggregates();
        var host = host(aggregates);
        var bytes = load(host, subject);
        var cold = host.admit(encounter(51, subject), subject, 10);
        host.useStyles(id -> id.equals(subject) ? Optional.of(style()) : Optional.empty());
        host.closeEncounter(encounter(51, subject), 20, GroupMode.FAIR);
        host.digests().add(digest(subject));
        host.drain(List.of(), 2L);
        assertEquals(0, host.digests().subjects());
        assertTrue(aggregates.flush(3L, AggregateCache.FLUSH_KEYS).isPresent());
        assertTrue(aggregates.inFlight() > 0);
        var next = host.admit(encounter(52, subject), subject, 10);
        assertTrue(next.seedTrialsTotal() > cold.seedTrialsTotal());
        assertTrue(next.startRung() < cold.startRung());
        assertEquals(0, fights(host, subject, bytes));
        var batch = aggregates.batches().getFirst().batchId();
        host.cache().loaded(subject, bytes, true, false, 3L, batch);
        var hidden = host.cache().view(subject, aggregates.batches(), List.of(), Optional.empty(), settings());
        assertEquals(0, hidden.fights());
        assertTrue(hidden.style().isEmpty());
    }

    private static int fights(MemoryHost host, UUID subject, byte[] bytes) {
        var decoded = PlayerMemoryCodec.decode(host.cache().slot(subject).orElseThrow().record().orElse(bytes));
        assertTrue(decoded instanceof MemoryDecoded.Ready);
        return (int) ((MemoryDecoded.Ready) decoded).memory().fights();
    }

    private static MemoryHost host() {
        return host(aggregates());
    }

    private static AggregateCache aggregates() {
        return new AggregateCache(accepting(), new StorageMetrics(), 4096, 100, 4, new Inbox<>(8));
    }

    private static MemoryHost host(AggregateCache aggregates) {
        var host = new MemoryHost(settings(), new MemoryCounters(), aggregates, DecisionTap.NONE, new MemoryHost.Presence() {
            @Override
            public boolean online(UUID subject) {
                return true;
            }

            @Override
            public boolean exempt(UUID subject) {
                return false;
            }
        }, () -> 86_400_000L);
        host.drain(List.of(), 1L);
        return host;
    }

    private static byte[] load(MemoryHost host, UUID subject) {
        var bytes = PlayerMemoryCodec.encode(PlayerMemory.empty(), 8).bytes();
        host.cache().loaded(subject, bytes, true, false, 1L);
        return bytes;
    }

    private static Encounter encounter(long id, UUID subject) {
        var encounter = new Encounter(new EncounterId(id), new UUID(0, id), 0);
        encounter.addMember(new MobSession(new MobKey(new UUID(id, 9), 0), EngineFixtures.profile("boss")));
        encounter.addParticipant(subject);
        return encounter;
    }

    private static FightDigest digest(UUID subject) {
        return new FightDigest(subject, ProfileHash.of("boss_ai"), FightOutcome.WIN, PartyBand.ONE,
                DifficultySection.SOLO, Optional.of(new DifficultySample(1f, 0f)), 86_400_000L, false);
    }

    private static StyleMemory style() {
        var successes = new float[StyleMemory.PATTERNS];
        var failures = new float[StyleMemory.PATTERNS];
        successes[0] = 8f;
        failures[0] = 2f;
        return new StyleMemory(1f, 1, successes, failures, new int[StyleMemory.PATTERNS],
                new float[StyleMemory.ACTIONS], 0f, new int[0], new float[0], new float[StyleMemory.HISTOGRAMS],
                new byte[StyleMemory.HISTOGRAMS][StyleMemory.BINS], 0f, 0f, 0f, new float[StyleMemory.EPISODES],
                new float[StyleMemory.EPISODES]);
    }

    private static MemorySettings settings() {
        return new MemorySettings(MemoryMode.SEED, 0.9, 30, 4, 0.25, 6, 4, 3, 3, 0.2, 8, 2, 60, 512, 16, 10, 60, 8,
                1024, 64, 180, 1_000_000, true, "sqlite");
    }

    private static AggregateStore accepting() {
        return new AggregateStore() {
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
        };
    }
}
