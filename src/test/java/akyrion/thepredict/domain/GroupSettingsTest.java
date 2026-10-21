package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The group section of a profile: absent parts are empty values, never a {@code null} turned into one (rule C4). */
class GroupSettingsTest {
    @Test
    void theDefaultHasNoModeNoSquadAndNoQuestionSet() {
        assertEquals(Optional.empty(), GroupSettings.DEFAULT.mode());
        assertEquals("", GroupSettings.DEFAULT.squad());
        assertEquals(Optional.empty(), GroupSettings.DEFAULT.questions());
    }

    @Test
    void aNullComponentIsRejected() {
        assertThrows(NullPointerException.class, () -> settings(null, "", Optional.empty()));
        assertThrows(NullPointerException.class, () -> settings(Optional.empty(), null, Optional.empty()));
        assertThrows(NullPointerException.class, () -> settings(Optional.empty(), "", null));
    }

    @Test
    void aSquadMustBeAnIdentifier() {
        assertEquals("pack_a", settings(Optional.empty(), "pack_a", Optional.empty()).squad());
        assertThrows(IllegalArgumentException.class, () -> settings(Optional.empty(), "Pack A", Optional.empty()));
    }

    private static GroupSettings settings(Optional<GroupMode> mode, String squad, Optional<GroupQuestions> questions) {
        return new GroupSettings(mode, squad, 0, 1, Set.of(), LeaderDeath.SUCCEED, BenchPolicy.FREE, questions,
                PlanQuestion.ON_CHANGE, RoleCapabilities.NONE);
    }
}
