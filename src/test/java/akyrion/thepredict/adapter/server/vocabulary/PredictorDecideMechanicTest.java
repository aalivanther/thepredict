package akyrion.thepredict.adapter.server.vocabulary;

import akyrion.thepredict.engine.ContextSignals;
import io.lumine.mythic.api.skills.SkillResult;
import io.lumine.mythic.api.skills.ThreadSafetyLevel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static akyrion.thepredict.adapter.server.vocabulary.VocabularyFixtures.caster;
import static akyrion.thepredict.adapter.server.vocabulary.VocabularyFixtures.entity;
import static akyrion.thepredict.adapter.server.vocabulary.VocabularyFixtures.metadata;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PredictorDecideMechanicTest {
    private static final UUID ENTITY = UUID.fromString("00000000-0000-0000-0000-000000000021");

    @Test
    void castMarksTheCasterOnceUntilItIsDrained() {
        var signals = new ContextSignals();
        var mechanic = new PredictorDecideMechanic(signals);
        var data = metadata(caster(entity(ENTITY)));
        assertEquals(SkillResult.SUCCESS, mechanic.cast(data));
        assertEquals(SkillResult.SUCCESS, mechanic.cast(data));
        var drained = new ArrayList<UUID>();
        assertEquals(1, signals.drain(drained::add, 10));
        assertEquals(List.of(ENTITY), drained);
        mechanic.cast(data);
        assertEquals(1, signals.pendingCount());
    }

    @Test
    void missingMetadataCasterOrEntityStillSucceedsWithoutASignal() {
        var signals = new ContextSignals();
        var mechanic = new PredictorDecideMechanic(signals);
        assertEquals(SkillResult.SUCCESS, mechanic.cast(null));
        assertEquals(SkillResult.SUCCESS, mechanic.cast(metadata(null)));
        assertEquals(SkillResult.SUCCESS, mechanic.cast(metadata(caster(null))));
        assertEquals(SkillResult.SUCCESS, mechanic.cast(metadata(caster(entity(null)))));
        assertEquals(0, signals.pendingCount());
    }

    @Test
    void aFullQueueStillSucceedsAndTheDropIsVisible() {
        var signals = new ContextSignals();
        for (var index = 0; index < ContextSignals.CAPACITY; index++) {
            signals.offer(new UUID(9L, index));
        }
        var mechanic = new PredictorDecideMechanic(signals);
        assertEquals(SkillResult.SUCCESS, mechanic.cast(metadata(caster(entity(ENTITY)))));
        assertEquals(1, signals.droppedCount());
    }

    @Test
    void declaresThatItMayRunOnAnyThread() {
        assertEquals(ThreadSafetyLevel.EITHER, new PredictorDecideMechanic(new ContextSignals()).getThreadSafetyLevel());
    }
}
