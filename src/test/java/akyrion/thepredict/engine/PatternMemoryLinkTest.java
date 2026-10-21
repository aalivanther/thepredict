package akyrion.thepredict.engine;

import akyrion.thepredict.domain.DifficultySample;
import akyrion.thepredict.domain.DifficultySection;
import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.EnemyAction;
import akyrion.thepredict.domain.EnemyProfileSummary;
import akyrion.thepredict.domain.FightOutcome;
import akyrion.thepredict.domain.GroupMode;
import akyrion.thepredict.domain.MemoryMode;
import akyrion.thepredict.domain.MemorySettings;
import akyrion.thepredict.domain.PartyBand;
import akyrion.thepredict.domain.PatternMode;
import akyrion.thepredict.domain.PatternSettings;
import akyrion.thepredict.domain.PlayerMemory;
import akyrion.thepredict.domain.PlayerMemoryCodec;
import akyrion.thepredict.domain.ProfileHash;
import akyrion.thepredict.domain.TickSignals;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static akyrion.thepredict.engine.EngineFixtures.WORLD;
import static akyrion.thepredict.engine.EngineFixtures.near;
import static akyrion.thepredict.engine.EngineFixtures.player;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** B1: the pattern director, the memory host and encounter closes are joined once both exist. */
class PatternMemoryLinkTest {
    private final EngineFixtures.Sessions fixture = new EngineFixtures.Sessions();
    private final List<UUID> summaries = new ArrayList<>();
    private final PatternInbox inbox = new PatternInbox();
    private final AggregateCache aggregates = new AggregateCache(accepting(), new StorageMetrics(), 4096, 100, 4,
            new Inbox<>(8));
    private final MemoryHost memory = host();

    @Test
    void aLinkedCloseWritesOneSummaryAndRecordsTheEnemyStyle() {
        var enemy = player(1);
        var patterns = director();
        fixture.encounters.hearCloses(encounter -> memory.closeEncounter(encounter, 20, GroupMode.FAIR));
        PatternMemoryLink.attach(patterns, memory, 4, fixture.encounters);
        fightAndClose(patterns, enemy);
        assertEquals(List.of(enemy), summaries);
        assertTrue(recordedStyle(enemy), "memory recorded an empty style");
    }

    @Test
    void withoutTheLinkTheCloseWritesNoSummaryAndRecordsNoStyle() {
        var enemy = player(1);
        var patterns = director();
        fixture.encounters.hearCloses(encounter -> memory.closeEncounter(encounter, 20, GroupMode.FAIR));
        fightAndClose(patterns, enemy);
        assertTrue(summaries.isEmpty());
        assertFalse(recordedStyle(enemy));
    }

    private PatternDirector director() {
        var gate = new PatternModeGate();
        gate.offer(PatternMode.OBSERVE, false);
        var tap = new DecisionTap() {
            @Override
            public void patternSummary(EncounterId encounter, UUID participant, EnemyProfileSummary summary) {
                summaries.add(participant);
            }
        };
        var director = new PatternDirector(inbox, new KinematicBook(), PatternSettings.DEFAULT, gate, () -> 0L);
        director.useTap(tap);
        return director;
    }

    private void fightAndClose(PatternDirector patterns, UUID enemy) {
        var session = fixture.open(1);
        var encounter = fixture.encounters.assign(session, WORLD, enemy, near());
        memory.cache().loaded(enemy, PlayerMemoryCodec.encode(PlayerMemory.empty(), 8).bytes(), true, false, 1L);
        memory.admit(encounter, enemy, 1);
        session.recordTarget(enemy, 3.0);
        session.tickSignals(TickSignals.PRESENT);
        inbox.admit(enemy);
        for (var index = 0; index < 12; index++) {
            inbox.offer(enemy, EnemyAction.STRIKE_EARLY.ordinal(), index);
        }
        patterns.visit(session, 12);
        assertTrue(patterns.modelOf(enemy).isPresent());
        session.loseTarget();
        assertTrue(fixture.encounters.closeIfFinished(encounter.id(), id -> false));
    }

    private boolean recordedStyle(UUID enemy) {
        memory.digests().add(new FightDigest(enemy, ProfileHash.of("boss_ai"), FightOutcome.WIN, PartyBand.ONE,
                DifficultySection.SOLO, Optional.of(new DifficultySample(1f, 0f)), 86_400_000L, false));
        memory.drain(List.of(), 2L);
        assertTrue(aggregates.flush(3L, AggregateCache.FLUSH_KEYS).isPresent());
        return memory.cache().view(enemy, aggregates.batches(), List.of(), Optional.empty(), settings()).style()
                .isPresent();
    }

    private MemoryHost host() {
        var host = new MemoryHost(settings(), new MemoryCounters(), aggregates, DecisionTap.NONE,
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
        host.drain(List.of(), 1L);
        return host;
    }

    private static MemorySettings settings() {
        return new MemorySettings(MemoryMode.RECORD, 0.9, 30, 4, 0.25, 6, 4, 3, 3, 0.2, 8, 2, 60, 512, 16, 10, 60, 8,
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
