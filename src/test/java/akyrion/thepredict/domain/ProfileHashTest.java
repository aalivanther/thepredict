package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileHashTest {
    @Test
    void aBirthdaySearchFindsACollisionAndBothNamesAreNamed() {
        var seen = new HashMap<Integer, String>();
        String left = "";
        String right = "";
        for (var index = 0; index < 200_000 && left.isEmpty(); index++) {
            var name = "n" + String.format("%08x", index * 0x9E3779B1);
            var hash = ProfileHash.of(name);
            var previous = seen.putIfAbsent(hash, name);
            if (previous != null) {
                left = previous;
                right = name;
            }
        }
        assertTrue(!left.isEmpty(), "no collision in the birthday search");
        assertNotEquals(left, right);
        assertEqualsHash(left, right);
    }

    private static void assertEqualsHash(String left, String right) {
        org.junit.jupiter.api.Assertions.assertEquals(ProfileHash.of(left), ProfileHash.of(right));
    }
}
