package akyrion.thepredict.engine;

import akyrion.thepredict.AllocationProbe;
import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.IntentView;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.TacticId;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntentBoardTest {
    private static final UUID ENTITY = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private static IntentView local(int generation, Movement movement) {
        return new IntentView(generation, ControlLevel.LOCAL, movement, new TacticId("debug_" + movement.name().toLowerCase(Locale.ROOT)), 1f, Long.MAX_VALUE);
    }

    @Test
    void unknownEntityReadsTheSharedAbsentView() {
        assertSame(IntentView.ABSENT, new IntentBoard().current(ENTITY));
    }

    @Test
    void publishedViewIsReadBackAndClearRestoresAbsent() {
        var board = new IntentBoard();
        var view = local(2, Movement.RETREAT);
        board.publish(new MobKey(ENTITY, 2), view);
        assertSame(view, board.current(ENTITY));
        board.clear(ENTITY);
        assertSame(IntentView.ABSENT, board.current(ENTITY));
    }

    @Test
    void viewOfAnotherGenerationIsRejected() {
        var board = new IntentBoard();
        assertThrows(IllegalArgumentException.class, () -> board.publish(new MobKey(ENTITY, 1), local(0, Movement.HOLD)));
        assertSame(IntentView.ABSENT, board.current(ENTITY));
    }

    @Test
    void clearAllRemovesEveryView() {
        var board = new IntentBoard();
        board.publish(new MobKey(ENTITY, 0), local(0, Movement.HOLD));
        board.publish(new MobKey(UUID.randomUUID(), 0), local(0, Movement.APPROACH));
        board.clearAll();
        assertEquals(0, board.size());
        assertSame(IntentView.ABSENT, board.current(ENTITY));
    }

    @Test
    void readingAPublishedOrMissingViewAllocatesNothing() {
        var board = new IntentBoard();
        var missing = UUID.fromString("00000000-0000-0000-0000-0000000000ff");
        board.publish(new MobKey(ENTITY, 0), local(0, Movement.HOLD));
        var published = AllocationProbe.bytesAllocatedBy(() -> board.current(ENTITY));
        var absent = AllocationProbe.bytesAllocatedBy(() -> board.current(missing));
        assertTrue(published < AllocationProbe.TOLERANCE_BYTES, published + " bytes");
        assertTrue(absent < AllocationProbe.TOLERANCE_BYTES, absent + " bytes");
    }

    @Test
    void notingTheSameSteeringAgainAllocatesNothingAndANewSeedReplacesIt() {
        var board = new IntentBoard();
        var movement = new akyrion.thepredict.domain.MovementSettings(2.5, 5, 9, 16, 10, 1.2, 1.15,
                java.util.OptionalDouble.empty());
        board.noteSteer(ENTITY, 7L, movement);
        var bytes = AllocationProbe.bytesAllocatedBy(() -> board.noteSteer(ENTITY, 7L, movement));
        assertTrue(bytes < AllocationProbe.TOLERANCE_BYTES, bytes + " bytes");
        board.noteSteer(ENTITY, 8L, movement);
        assertEquals(java.util.OptionalLong.of(8L), board.steerSeed(ENTITY));
        assertEquals(movement, board.steerMovement(ENTITY).orElseThrow());
    }

    @Test
    void clearAllMakesEveryViewStopControllingOnTheNextRead() {
        var board = new IntentBoard();
        var other = UUID.fromString("00000000-0000-0000-0000-000000000009");
        board.publish(new MobKey(ENTITY, 0), local(0, Movement.HOLD));
        board.publish(new MobKey(other, 3), local(3, Movement.RETREAT));
        assertTrue(board.current(other).controls(0));
        board.clearAll();
        assertFalse(board.current(ENTITY).controls(0));
        assertFalse(board.current(other).controls(0));
    }

    @Test
    void concurrentReadersOnlySeeCompleteViewsWhileTheWriterPublishes() throws Exception {
        var board = new IntentBoard();
        var key = new MobKey(ENTITY, 0);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(4);
        try {
            var readers = new ArrayList<Future<Integer>>();
            for (var reader = 0; reader < 3; reader++) {
                readers.add(executor.submit(() -> {
                    start.await();
                    var seen = 0;
                    for (var read = 0; read < 20_000; read++) {
                        var view = board.current(ENTITY);
                        assertTrue(view == IntentView.ABSENT || view.tactic().value().startsWith("debug_"));
                        seen++;
                    }
                    return seen;
                }));
            }
            var writer = executor.submit(() -> {
                start.await();
                for (var write = 0; write < 20_000; write++) {
                    board.publish(key, local(0, write % 2 == 0 ? Movement.HOLD : Movement.RETREAT));
                    if (write % 7 == 0) {
                        board.clear(ENTITY);
                    }
                }
                return null;
            });
            start.countDown();
            writer.get(30, TimeUnit.SECONDS);
            for (var reader : readers) {
                assertEquals(20_000, reader.get(30, TimeUnit.SECONDS));
            }
        } finally {
            executor.shutdownNow();
        }
    }
}
