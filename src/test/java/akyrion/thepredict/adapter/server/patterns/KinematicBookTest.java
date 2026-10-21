package akyrion.thepredict.adapter.server.patterns;

import akyrion.thepredict.domain.EnemyKinematics;
import akyrion.thepredict.engine.KinematicBook;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KinematicBookTest {
    @Test
    void theEngineKeepsTheStampAndTheTrendMovesWhenPatternsAreOff() {
        var book = new KinematicBook();
        var enemy = new UUID(4, 5);
        var sampler = new KinematicSampler(book);
        var first = new EnemyKinematicsReader(10.0, 10.0, 0.0f, 0.0, false, false, 12.0, 10.0, true, 0.15);
        var opened = sampler.sample(enemy, first, 1);
        assertTrue(EnemyKinematics.valid(opened));
        assertEquals(0, EnemyKinematics.lateral(opened));
        assertEquals(0, EnemyKinematics.radial(opened));
        assertEquals(opened, sampler.sample(enemy, first, 1));
        var moved = new EnemyKinematicsReader(11.0, 10.0, 0.0f, 0.0, false, false, 12.0, 10.0, true, 0.15);
        var word = sampler.sample(enemy, moved, 3);
        assertEquals(word, book.word(enemy));
        assertTrue(EnemyKinematics.lateral(word) != 0 || EnemyKinematics.radial(word) != 0, Long.toHexString(word));
        assertEquals(word, sampler.sample(enemy, moved, 3));
    }
}
