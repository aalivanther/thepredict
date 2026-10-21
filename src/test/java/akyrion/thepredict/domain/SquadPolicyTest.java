package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/** DOC-09: at most {@code group.maxSquads} squads are kept, in order; the members of a later squad join squad 0. */
class SquadPolicyTest {
    @Test
    void theSquadsPastTheCapJoinTheRemainder() {
        var declared = new String[] {"vanguard", "archers", "archers", "", "guards"};
        var children = new boolean[5];
        var none = new int[] {-1, -1, -1, -1, -1};
        assertArrayEquals(new int[] {1, 2, 2, 0, 3}, SquadPolicy.assign(declared, children, none, none));
        assertArrayEquals(new int[] {1, 2, 2, 0, 3}, SquadPolicy.assign(declared, children, none, none, 3));
        assertArrayEquals(new int[] {1, 0, 0, 0, 0}, SquadPolicy.assign(declared, children, none, none, 1));
        assertArrayEquals(new int[] {1, 2, 2, 0, 0}, SquadPolicy.assign(declared, children, none, none, 2));
    }
}
