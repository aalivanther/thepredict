package akyrion.thepredict.adapter.server.patterns;

import akyrion.thepredict.engine.KinematicBook;

import java.util.UUID;

/**
 * Samples a kinematic word into the book the engine owns (M26.1, errata E26-8). The same tick returns the word
 * already stored, so a second reader does not wipe the delta. The first sample, whose previous word is {@code 0},
 * carries no movement.
 */
public final class KinematicSampler {
    private final KinematicBook book;

    /**
     * Samples into one book.
     *
     * @param book the engine book
     */
    public KinematicSampler(KinematicBook book) {
        this.book = book;
    }

    /**
     * Reads one sample.
     *
     * @param participant the enemy
     * @param reader the numbers the caller already took from the entity
     * @param tick the server tick
     * @return the word now stored
     */
    public long sample(UUID participant, EnemyKinematicsReader reader, int tick) {
        if (book.tick(participant) == tick) {
            return book.word(participant);
        }
        var previousTick = book.tick(participant);
        var gap = previousTick < 0 ? 100 : Math.max(1, tick - previousTick);
        var word = reader.read(book.word(participant), gap);
        book.store(participant, word, tick);
        return word;
    }
}
