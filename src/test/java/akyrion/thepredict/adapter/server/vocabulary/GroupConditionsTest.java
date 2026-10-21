package akyrion.thepredict.adapter.server.vocabulary;

import akyrion.thepredict.AllocationProbe;
import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.GroupPlan;
import akyrion.thepredict.domain.GroupRole;
import akyrion.thepredict.domain.IntentView;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TellAnswer;
import akyrion.thepredict.engine.IntentBoard;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static akyrion.thepredict.adapter.server.vocabulary.VocabularyFixtures.clockAt;
import static akyrion.thepredict.adapter.server.vocabulary.VocabularyFixtures.view;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code predictorrole}, {@code predictorplan} and {@code predictortoken} follow A6 like the other conditions: true
 * only while the view controls the mob (SA-16), and without allocating (SA-15).
 */
class GroupConditionsTest {
    private static final UUID ENTITY = UUID.fromString("00000000-0000-0000-0000-000000000051");

    private final IntentBoard board = new IntentBoard();

    @Test
    void theGroupConditionsAreFalseOnceTheViewStopsControllingTheMob() {
        var role = new PredictorRoleCondition("flank", board, clockAt(10));
        var plan = new PredictorPlanCondition("surround_target", board, clockAt(10));
        var token = new PredictorTokenCondition("melee", board, clockAt(10));
        board.publish(new MobKey(ENTITY, 0), grouped(view(ControlLevel.JEV, Movement.FLANK, new TacticId("flank"), 100)));
        assertTrue(role.test(ENTITY));
        assertTrue(plan.test(ENTITY));
        assertTrue(token.test(ENTITY));
        board.publish(new MobKey(ENTITY, 0), grouped(view(ControlLevel.BASE, Movement.NONE, TacticId.NONE, 100)));
        assertFalse(role.test(ENTITY), "a BASE view keeps the role but no longer controls the mob");
        assertFalse(plan.test(ENTITY));
        assertFalse(token.test(ENTITY));
        board.publish(new MobKey(ENTITY, 0), grouped(view(ControlLevel.JEV, Movement.FLANK, new TacticId("flank"), 5)));
        assertFalse(role.test(ENTITY), "an expired deadline is no control either");
        assertFalse(new PredictorRoleCondition("pressure", board, clockAt(10)).test(ENTITY));
    }

    @Test
    void evaluatingTheGroupConditionsAndPlaceholdersAllocatesNothing() {
        var role = new PredictorRoleCondition("flank", board, clockAt(10));
        var plan = new PredictorPlanCondition("surround_target", board, clockAt(10));
        var grouped = grouped(view(ControlLevel.JEV, Movement.FLANK, new TacticId("flank"), 100))
                .withPosture(Movement.FLANK, akyrion.thepredict.domain.CombatStance.PRESSING, false, (byte) 50,
                        TellAnswer.BLOCK.ordinal(), 40L);
        board.publish(new MobKey(ENTITY, 0), grouped);
        role.test(ENTITY);
        var bytes = AllocationProbe.bytesAllocatedBy(() -> {
            for (var round = 0; round < 64; round++) {
                role.test(ENTITY);
                plan.test(ENTITY);
                PredictorPlaceholders.format(PredictorPlaceholders.Field.ANSWER, grouped, 10);
                PredictorPlaceholders.format(PredictorPlaceholders.Field.ROLE, grouped, 10);
                PredictorPlaceholders.format(PredictorPlaceholders.Field.PLAN, grouped, 10);
            }
        });
        assertTrue(bytes < AllocationProbe.TOLERANCE_BYTES, bytes + " bytes");
    }

    private static IntentView grouped(IntentView view) {
        return view.withGroup(GroupRole.FLANK.ordinal(), GroupPlan.SURROUND_TARGET.ordinal(), 1, 3, 0);
    }
}
