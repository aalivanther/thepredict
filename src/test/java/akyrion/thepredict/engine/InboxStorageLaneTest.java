package akyrion.thepredict.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InboxStorageLaneTest {
    @Test
    void theStorageLaneDoesNotUseTheHttpCapacity() {
        var inbox = new Inbox<InboxEntry>(1);
        assertTrue(inbox.offer(new StorageReply.QueryFailed("http", 1L)));
        assertTrue(!inbox.offer(new StorageReply.QueryFailed("http", 2L)));
        assertEquals(1, inbox.rejected());
        assertTrue(inbox.offerStorage(new StorageReply.BatchApplied(1, 1L)));
        assertEquals(0, inbox.storageRejected());
        assertEquals(1, inbox.drainStorage(4, reply -> {
        }));
        assertEquals(1, inbox.size());
    }
}
