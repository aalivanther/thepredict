package akyrion.thepredict.domain;

import akyrion.thepredict.adapter.jev.FairQuestion;
import akyrion.thepredict.domain.distill.DistillMode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** No distill mode of the profile changes the bytes sent to Jev (A17, M29). */
class ObserveDistillParityTest {
    @Test
    void everyDistillModeAsksTheSameQuestionAndOmitsTheStudent() {
        var guardian = DomainFixtures.guardian();
        var options = List.of(new TacticId("press_attack"), new TacticId("hold_ground"));
        var body = FairQuestion.json(Situation.NOW, options, guardian);
        for (var mode : DistillMode.values()) {
            var profile = guardian.withOverlay(guardian.timing(), guardian.zone(), guardian.group(),
                    guardian.patterns(), guardian.decision(), Optional.of(mode));
            assertEquals(body, FairQuestion.json(Situation.NOW, options, profile), mode.name());
        }
        assertFalse(body.contains("distill"));
        assertFalse(body.contains("student"));
        assertFalse(body.contains("pattern_facts"));
    }
}
