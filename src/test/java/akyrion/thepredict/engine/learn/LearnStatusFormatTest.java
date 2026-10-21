package akyrion.thepredict.engine.learn;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LearnStatusFormatTest {
    @Test
    void theLineNamesTheStateAndTheSwitch() {
        var line = LearnStatus.line(LearnedArtifacts.State.NONE, "", "", true, "bench");
        assertTrue(line.contains("none"));
        assertTrue(line.contains("collect true"));
        assertTrue(line.contains("scope bench"));
    }

    @Test
    void theLiveLineCarriesTheLoadedCollectionAndScope() {
        var settings = akyrion.thepredict.domain.LearningSettings.DEFAULT;
        assertTrue(LearnStatus.line(settings).endsWith(" collect " + settings.collect() + " scope "
                + settings.scope()), LearnStatus.line(settings));
    }
}
