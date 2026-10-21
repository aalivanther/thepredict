package akyrion.thepredict.adapter.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LockTest {
    @TempDir
    Path directory;

    @Test
    void aSecondLockWaitsUntilTheFirstCloses() throws Exception {
        var first = RecordsLock.tryAcquire(directory);
        assertTrue(first.isPresent());
        var second = RecordsLock.tryAcquire(directory);
        assertTrue(second.isEmpty());
        first.get().close();
        var third = RecordsLock.tryAcquire(directory);
        assertTrue(third.isPresent());
        third.get().close();
    }
}
