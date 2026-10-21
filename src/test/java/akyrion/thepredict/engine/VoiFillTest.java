package akyrion.thepredict.engine;

import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.LearningSettings;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.engine.learn.LearnedArtifacts;
import akyrion.thepredict.engine.learn.LearnedLocalPick;
import akyrion.thepredict.engine.learn.ValueTableBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** I: the agreement of a value-of-information cell sums every tactic of the cell, not only the best one (M28.8). */
class VoiFillTest {
    @Test
    void theAgreementOfTheCellSumsEveryTacticItHolds() {
        var builder = new ValueTableBuilder();
        builder.add(1, LearnedLocalPick.tacticCode("keep"), 3, 7L, 1.0, 0.01, 40, 1.0, 10, 9, 0);
        builder.add(1, LearnedLocalPick.tacticCode("press"), 3, 7L, 0.0, 0.01, 40, 1.0, 20, 2, 0);
        var session = new MobSession(new MobKey(UUID.randomUUID(), 0), EngineFixtures.profile("boss"));
        session.learning().stage(builder.build(), LearnedArtifacts.State.ACTIVE, 1, 7L);
        session.learning().activateIfReady();
        var encounter = new Encounter(new EncounterId(1), new UUID(0, 1), 0);
        encounter.addMember(session);
        var filled = VoiFill.prepare(session, encounter, new EncounterQuestions.Plan(List.of(), Map.of(), List.of()),
                LearningSettings.DEFAULT, List.of(new TacticId("press"), new TacticId("keep")));
        assertEquals(new TacticId("keep"), filled.tactic());
        assertEquals(30, filled.seen(), "ten questions of keep and twenty of press");
        assertEquals(11, filled.agree());
        assertEquals(30, session.learning().voiSeen());
    }
}
