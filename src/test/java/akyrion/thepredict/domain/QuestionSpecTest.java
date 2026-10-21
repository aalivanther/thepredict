package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static akyrion.thepredict.domain.DomainFixtures.MOB;
import static akyrion.thepredict.domain.DomainFixtures.tactic;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestionSpecTest {
    private static List<TacticId> options(int count) {
        var options = new ArrayList<TacticId>();
        for (var index = 0; index < count; index++) {
            options.add(tactic("tactic_" + index));
        }
        return options;
    }

    private static QuestionSpec question(List<TacticId> options) {
        return new QuestionSpec(QuestionKey.of(0), MOB, Situation.NOW, options);
    }

    @Test
    void twoAndTwoHundredFiftyFiveOptionsAreAccepted() {
        assertEquals(2, question(options(2)).options().size());
        assertEquals(255, question(options(255)).options().size());
    }

    @Test
    void oneAndTwoHundredFiftySixOptionsAreRejected() {
        assertTrue(assertThrows(IllegalArgumentException.class, () -> question(options(1))).getMessage().contains("options"));
        assertThrows(IllegalArgumentException.class, () -> question(options(256)));
    }

    @Test
    void duplicateOptionsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> question(List.of(tactic("hold_ground"), tactic("hold_ground"))));
    }

    @Test
    void optionsAreCopied() {
        var options = new ArrayList<>(options(3));
        var question = question(options);
        options.clear();
        assertEquals(3, question.options().size());
    }

    @Test
    void theTacticToKeepMustBeAnOptionAndSurvivesOnlyAmongTheOptionsSent() {
        var options = options(3);
        var spec = new QuestionSpec(QuestionKey.of(0), MOB, Situation.NOW, options, Optional.of(options.get(2)));
        assertEquals(Optional.of(options.get(2)), spec.keep());
        assertEquals(Optional.empty(), question(options).keep());
        assertThrows(IllegalArgumentException.class, () -> new QuestionSpec(QuestionKey.of(0), MOB, Situation.NOW,
                options, Optional.of(tactic("absent"))));
        assertEquals(Optional.empty(), spec.withOptions(options.subList(0, 2)).keep());
        assertEquals(Optional.of(options.get(2)), spec.withOptions(options.subList(1, 3)).keep());
    }
}
