package akyrion.thepredict.engine;

import akyrion.thepredict.domain.EnemyAction;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PZ-04: a forgotten ring frees its slot and keeps the probe chain of the rings after it whole. */
class PatternInboxTest {
    @Test
    void forgettingTheHeadOfACollisionChainKeepsTheOthersAndTheirEvents() {
        var inbox = new PatternInbox();
        var head = new UUID(0, 5);
        var second = new UUID(0, 5 + PatternInbox.SLOTS);
        var third = new UUID(0, 5 + 2 * PatternInbox.SLOTS);
        inbox.admit(head);
        inbox.admit(second);
        inbox.admit(third);
        inbox.offer(second, EnemyAction.STRIKE.ordinal(), 1);
        inbox.offer(third, EnemyAction.STRIKE.ordinal(), 1);
        inbox.offer(third, EnemyAction.STRIKE.ordinal(), 2);
        inbox.sampledTick(third, 9);
        inbox.forget(head);
        assertFalse(inbox.participates(head));
        assertTrue(inbox.participates(second));
        assertTrue(inbox.participates(third));
        assertEquals(1, inbox.size(second));
        assertEquals(2, inbox.size(third));
        assertEquals(9, inbox.sampledTick(third), "the state of a ring moves with it");
        inbox.forget(second);
        assertTrue(inbox.participates(third));
        assertEquals(2, inbox.size(third));
        assertEquals(1, inbox.live());
    }

    @Test
    void aFullTableAdmitsAgainOnceRingsAreForgotten() {
        var inbox = new PatternInbox();
        for (var index = 0; index < PatternInbox.SLOTS; index++) {
            inbox.admit(new UUID(7, index));
        }
        var late = new UUID(8, 1);
        inbox.admit(late);
        assertFalse(inbox.participates(late), "a full table admits nothing");
        assertEquals(PatternInbox.SLOTS / 2, inbox.forgetUnless(participant -> participant.getLeastSignificantBits() % 2
                == 0));
        inbox.admit(late);
        assertTrue(inbox.participates(late));
        for (var index = 0; index < PatternInbox.SLOTS; index += 2) {
            assertTrue(inbox.participates(new UUID(7, index)), "kept ring " + index);
        }
    }
}
