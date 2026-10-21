package akyrion.thepredict.adapter.storage;

import java.util.ArrayDeque;
import java.util.List;

/**
 * Operation words of the writer loop, for the ordering tests of M21.2. A writer built for a release keeps none:
 * {@link #OFF} drops every word and its list stays empty. A test opts in with {@link #bounded(int)}, which keeps
 * only the newest words, so a trace never grows with the run (A5). Thread-safe.
 */
final class WriterTrace {
    /** Most words an opt-in trace may keep. */
    static final int MAX_WORDS = 4096;
    /** The trace of a release: it keeps nothing. */
    static final WriterTrace OFF = new WriterTrace(0);

    private final int capacity;
    private final ArrayDeque<String> words;

    private WriterTrace(int capacity) {
        this.capacity = capacity;
        this.words = new ArrayDeque<>(capacity);
    }

    /**
     * A trace that keeps the newest {@code capacity} words and drops the oldest first.
     *
     * @param capacity words kept, 1 to {@link #MAX_WORDS}
     * @return the trace
     * @throws IllegalArgumentException when the capacity is out of range
     */
    static WriterTrace bounded(int capacity) {
        if (capacity < 1 || capacity > MAX_WORDS) {
            throw new IllegalArgumentException("trace capacity must be within [1, " + MAX_WORDS + "]");
        }
        return new WriterTrace(capacity);
    }

    /**
     * Notes one operation. Does nothing on {@link #OFF}.
     *
     * @param word the operation, such as {@code read}, {@code batch} or {@code event}
     */
    void note(String word) {
        if (capacity == 0) {
            return;
        }
        synchronized (words) {
            if (words.size() == capacity) {
                words.removeFirst();
            }
            words.addLast(word);
        }
    }

    /**
     * The words kept, oldest first.
     *
     * @return a copy; empty for {@link #OFF}
     */
    List<String> words() {
        if (capacity == 0) {
            return List.of();
        }
        synchronized (words) {
            return List.copyOf(words);
        }
    }
}
