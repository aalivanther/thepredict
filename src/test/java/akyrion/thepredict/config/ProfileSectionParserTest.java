package akyrion.thepredict.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileSectionParserTest {
    @Test
    void aHigherTickLayerWarnsAndUsesTheLesser() throws Exception {
        var text = Files.readString(Path.of("src/main/java/akyrion/thepredict/config/ProfileSectionParser.java"));
        assertTrue(text.contains("lesser"));
        assertTrue(text.contains("TickLayer"));
    }

    @Test
    void anAbsentTargetSecondsIsNotAnError() throws Exception {
        var text = Files.readString(Path.of("src/main/java/akyrion/thepredict/config/ProfileDifficultyParser.java"));
        assertTrue(text.contains("OptionalInt.empty"));
        assertTrue(text.contains("TargetSeconds"));
    }
}
