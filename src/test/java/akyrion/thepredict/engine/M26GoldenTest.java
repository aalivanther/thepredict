package akyrion.thepredict.engine;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;

class M26GoldenTest {
    @Test
    void frozenReferencesStayByteStableUnderOffAndObserve() throws Exception {
        var copy = Path.of("src/test/resources/golden/m26");
        var live = Path.of("src/test/resources/golden");
        var count = 0;
        try (var walk = Files.walk(copy)) {
            for (var path : walk.filter(Files::isRegularFile).toList()) {
                var relative = copy.relativize(path);
                var original = live.resolve(relative);
                assertTrue(Files.isRegularFile(original), relative.toString());
                var left = Files.readAllBytes(original);
                var right = Files.readAllBytes(path);
                assertTrue(Arrays.equals(left, right), relative.toString());
                count++;
            }
        }
        assertTrue(count > 10);
    }
}
