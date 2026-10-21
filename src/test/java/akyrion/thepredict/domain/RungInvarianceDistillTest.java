package akyrion.thepredict.domain;

import akyrion.thepredict.adapter.jev.FairQuestion;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** A rung changes levers and does not enter the question sent to Jev (A10). */
class RungInvarianceDistillTest {
    @Test
    void aProfilePinnedToRungZeroOrRungEightAsksTheSameQuestion() {
        var guardian = DomainFixtures.guardian();
        var options = List.of(new TacticId("press_attack"), new TacticId("hold_ground"));
        var body = FairQuestion.json(Situation.NOW, options, guardian);
        for (var rung = 0; rung <= 8; rung += 8) {
            var pinned = new DifficultySettings(java.util.Optional.of(DifficultyMode.PLAYER), OptionalInt.of(300),
                    OptionalDouble.of(0.2), rung, rung, OptionalInt.of(rung), false, false, NativeLevelMode.OFF);
            assertEquals(body, FairQuestion.json(Situation.NOW, options, withDifficulty(guardian, pinned)),
                    "rung " + rung);
        }
        assertFalse(body.contains("distill"));
        assertFalse(body.contains("rung"));
        assertNotEquals(PressureLadder.levers(0, 0, 5, true).reactionDelayTicks(),
                PressureLadder.levers(8, 0, 5, true).reactionDelayTicks());
    }

    private static TacticalProfile withDifficulty(TacticalProfile base, DifficultySettings difficulty) {
        return new TacticalProfile(base.id(), base.mythicMobId(), base.objective(), base.tactics(), base.choices(),
                base.contexts(), base.movement(), base.health(), base.threat(), base.baseAi(), base.timing(),
                base.participants(), difficulty, base.stances(), base.telegraph(), base.poise(), base.zone(),
                base.group(), base.patterns(), base.memory(), base.decision(), base.distillMode());
    }
}
