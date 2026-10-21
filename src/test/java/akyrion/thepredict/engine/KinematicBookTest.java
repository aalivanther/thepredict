package akyrion.thepredict.engine;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A full book replaces its oldest insertion, one after another (EFO-23). */
class KinematicBookTest {
    private static UUID enemy(int value) {
        return new UUID(0x4B, value);
    }

    @Test
    void aFullBookReplacesTheOldestInsertionInTurn() {
        var book = new KinematicBook();
        for (var value = 0; value < KinematicBook.CAPACITY; value++) {
            book.store(enemy(value), 100L + value, value);
        }
        book.store(enemy(64), 164L, 64);
        assertEquals(0L, book.word(enemy(0)), "the first insertion made room");
        assertEquals(164L, book.word(enemy(64)));
        book.store(enemy(65), 165L, 65);
        assertEquals(164L, book.word(enemy(64)), "the 65th enemy keeps its word when the 66th arrives");
        assertEquals(0L, book.word(enemy(1)), "the second oldest made room for the 66th");
        assertEquals(102L, book.word(enemy(2)));
        assertEquals(65, book.tick(enemy(65)));
        book.store(enemy(64), 200L, 70);
        assertEquals(200L, book.word(enemy(64)), "a known enemy is updated in place");
        assertEquals(102L, book.word(enemy(2)), "updating a known enemy evicts nobody");
    }

    @Test
    void aForgottenEnemyFreesItsSlotSoTheNextOneEvictsNobody() {
        var book = new KinematicBook();
        for (var value = 0; value < KinematicBook.CAPACITY; value++) {
            book.store(enemy(value), 100L + value, value);
        }
        book.forget(enemy(30));
        assertEquals(0L, book.word(enemy(30)));
        assertEquals(-1, book.tick(enemy(30)));
        assertEquals(KinematicBook.CAPACITY - 1, book.size());
        book.store(enemy(64), 164L, 64);
        for (var value = 0; value < KinematicBook.CAPACITY; value++) {
            if (value != 30) {
                assertEquals(100L + value, book.word(enemy(value)), "SP-05: enemy " + value + " kept its word");
            }
        }
        assertEquals(164L, book.word(enemy(64)));
        assertEquals(KinematicBook.CAPACITY, book.size());
        book.forget(enemy(99));
        assertEquals(KinematicBook.CAPACITY, book.size(), "an unknown enemy changes nothing");
    }

    @Test
    void twoNewEnemiesOfAFullBookKeepTheirWordAndTickWhileTheyAlternate() {
        var book = new KinematicBook();
        for (var value = 0; value < KinematicBook.CAPACITY; value++) {
            book.store(enemy(value), 100L + value, value);
        }
        for (var sample = 0; sample < 10; sample++) {
            var tick = 100 + sample;
            book.store(sample % 2 == 0 ? enemy(200) : enemy(201), 1000L + sample, tick);
            if (sample > 0) {
                var other = sample % 2 == 0 ? enemy(201) : enemy(200);
                assertEquals(1000L + sample - 1, book.word(other), "sample " + sample);
                assertEquals(tick - 1, book.tick(other));
            }
        }
    }
}
