package akyrion.thepredict.engine;

import akyrion.thepredict.domain.learn.LearnCause;
import akyrion.thepredict.domain.QuestionKey;
import akyrion.thepredict.domain.QuestionSpec;
import akyrion.thepredict.domain.Situation;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.distill.DistillSettings;
import akyrion.thepredict.domain.learn.ExploreArm;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A player stops exploration unless the mob file sets PredictorTraining. */
class ExploreNeverAgainstPlayersTest {
    @Test
    void aJoinedPlayerClearsExplorationAndTrainingIsTheOnlyException() {
        var stopped = open(false);
        assertEquals(Optional.of(LearnCause.PLAYER_JOINED), stopped.learning().skipCause());
        assertEquals(ExploreArm.NONE, stopped.learning().explore());
        var training = open(true);
        assertEquals(Optional.empty(), training.learning().skipCause());
    }

    private static MobSession open(boolean training) {
        var fixture = new EngineFixtures.Sessions();
        var session = fixture.open(1);
        var encounter = fixture.encounters.assign(session, EngineFixtures.WORLD, EngineFixtures.player(1),
                EngineFixtures.near());
        session.learning().markTraining(training);
        session.learning().playerJoined(true);
        var options = List.of(new TacticId("press_attack"), new TacticId("hold_ground"));
        var question = new QuestionSpec(QuestionKey.of(0), session.key(), Situation.NOW, options, Optional.of(options.getFirst()));
        var plan = new EncounterQuestions.Plan(List.of(question), Map.of(session.key(), session.profile()), List.of());
        CaptureGate.apply(encounter, plan, 4L, null, DecisionTap.NONE, DistillSettings.DEFAULT,
                akyrion.thepredict.engine.distill.TeachingBoard.NONE);
        return session;
    }
}
