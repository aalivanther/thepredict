package akyrion.thepredict.adapter.server.vocabulary;

import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.engine.ContextSignals;
import akyrion.thepredict.engine.IntentBoard;
import io.lumine.mythic.bukkit.events.MythicConditionLoadEvent;
import io.lumine.mythic.bukkit.events.MythicMechanicLoadEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static akyrion.thepredict.adapter.server.vocabulary.VocabularyFixtures.clockAt;
import static akyrion.thepredict.adapter.server.vocabulary.VocabularyFixtures.line;
import static akyrion.thepredict.adapter.server.vocabulary.VocabularyFixtures.view;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VocabularyRegistrarTest {
    private static final UUID ENTITY = UUID.fromString("00000000-0000-0000-0000-000000000041");

    private final IntentBoard board = new IntentBoard();
    private final ContextSignals signals = new ContextSignals();
    private final List<String> info = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();
    private final VocabularyRegistrar registrar = new VocabularyRegistrar(board, signals, clockAt(0), info::add,
            warnings::add);

    private MythicConditionLoadEvent condition(String name, Map<String, String> arguments) {
        var event = new MythicConditionLoadEvent(null, name, "", line(arguments));
        registrar.onConditionLoad(event);
        return event;
    }

    private MythicMechanicLoadEvent mechanic(String name) {
        var event = new MythicMechanicLoadEvent(null, name, line(Map.of()));
        registrar.onMechanicLoad(event);
        return event;
    }

    @Test
    void namesMatchWithAnyCapitalization() {
        assertTrue(VocabularyRegistrar.isNamed("predictormove", PredictorMoveCondition.NAME));
        assertTrue(VocabularyRegistrar.isNamed("PredictorMove", PredictorMoveCondition.NAME));
        assertTrue(VocabularyRegistrar.isNamed("PREDICTORCONTROL", PredictorControlCondition.NAME));
        assertTrue(VocabularyRegistrar.isNamed("predictorDecide", PredictorDecideMechanic.NAME));
    }

    @Test
    void otherNamesAndMissingNamesDoNotMatch() {
        assertFalse(VocabularyRegistrar.isNamed("predictormoves", PredictorMoveCondition.NAME));
        assertFalse(VocabularyRegistrar.isNamed("move", PredictorMoveCondition.NAME));
        assertFalse(VocabularyRegistrar.isNamed(null, PredictorMoveCondition.NAME));
        assertEquals(List.of("predictorcontrol", "predictormove", "predictortactic", "predictorstance",
                "predictorzone", "predictorzonefact", "predictorrole", "predictorplan", "predictortoken",
                "predictorpattern", "predictordecide"), VocabularyRegistrar.NAMES);
    }

    @Test
    void eachConditionNameRegistersItsCondition() {
        assertInstanceOf(PredictorControlCondition.class, condition("PREDICTORCONTROL", Map.of()).getCondition().orElseThrow());
        assertInstanceOf(PredictorMoveCondition.class, condition("PredictorMove", Map.of("m", "HOLD")).getCondition()
                .orElseThrow());
        assertInstanceOf(PredictorTacticCondition.class, condition("predictortactic", Map.of("t", "hold_line"))
                .getCondition().orElseThrow());
        assertTrue(condition("PredictorProbe", Map.of("label", "flee_mc")).getCondition().isEmpty());
        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    @Test
    void registeredConditionsReadTheSharedBoard() {
        var control = (PredictorControlCondition) condition("predictorcontrol", Map.of()).getCondition().orElseThrow();
        var move = (PredictorMoveCondition) condition("predictormove", Map.of("movement", "retreat")).getCondition()
                .orElseThrow();
        board.publish(new MobKey(ENTITY, 0), view(ControlLevel.JEV, Movement.RETREAT, new TacticId("fall_back"), 10));
        assertTrue(control.test(ENTITY));
        assertTrue(move.test(ENTITY));
    }

    @Test
    void foreignNamesAreLeftToOtherPlugins() {
        assertTrue(condition("isplayer", Map.of()).getCondition().isEmpty());
        assertTrue(condition(null, Map.of()).getCondition().isEmpty());
        assertTrue(mechanic("message").getMechanic().isEmpty());
        assertTrue(info.isEmpty());
    }

    @Test
    void invalidArgumentsRegisterAnAlwaysFalseConditionAndWarnOncePerWave() {
        var first = condition("predictormove", Map.of("m", "sideways")).getCondition().orElseThrow();
        condition("PREDICTORMOVE", Map.of("m", "sideways"));
        assertFalse(((PredictorMoveCondition) first).isValid());
        assertEquals(List.of("predictormove: movement 'sideways' is not one of APPROACH, KEEP_DISTANCE, RETREAT, HOLD, "
                + "FLANK or NONE; the condition is always false"), warnings);
    }

    @Test
    void reservedNoneTacticIsRegisteredAsInvalidWithOneWarning() {
        var registered = condition("predictortactic", Map.of("t", "none")).getCondition().orElseThrow();
        condition("PredictorTactic", Map.of("tactic", "none"));
        assertFalse(((PredictorTacticCondition) registered).isValid());
        assertEquals(List.of("predictortactic: tactic 'none' is reserved for views without a tactic, which never "
                + "control a mob; the condition is always false"), warnings);
    }

    @Test
    void zoneAndPatternConditionsWithAnUnknownNameWarnLikeTheOtherEight() {
        var zone = (PredictorZoneCondition) condition("predictorzone", Map.of("f", "near_voi")).getCondition()
                .orElseThrow();
        var pattern = (PredictorPatternCondition) condition("PredictorPattern", Map.of("p", "bogus")).getCondition()
                .orElseThrow();
        condition("predictorzonefact", Map.of("fact", "near_voi"));
        assertFalse(zone.matches(view(ControlLevel.JEV, Movement.HOLD, new TacticId("hold_line"), 10)));
        assertFalse(pattern.matches(view(ControlLevel.JEV, Movement.HOLD, new TacticId("hold_line"), 10)));
        assertEquals(List.of("predictorzone: fact is not an exposed zone fact; the condition is always false",
                "predictorpattern: pattern is not an enemy pattern; the condition is always false",
                "predictorzonefact: fact is not an exposed zone fact; the condition is always false"), warnings);
        condition("predictorzone", Map.of("f", "cover_near"));
        condition("predictorpattern", Map.of("p", "spams_clicks"));
        assertEquals(3, warnings.size(), "a valid argument warns nothing");
    }

    @Test
    void warningsNeverEchoTextThatIsNotAPlainName() {
        condition("predictortactic", Map.of("t", "sk-live/0123456789"));
        assertEquals(1, warnings.size());
        assertFalse(warnings.getFirst().contains("0123456789"), warnings.getFirst());
    }

    @Test
    void mechanicNamesRegisterDecideOnlyNowThatTheProbeIsRetired() {
        var decide = assertInstanceOf(PredictorDecideMechanic.class, mechanic("PredictorDecide").getMechanic().orElseThrow());
        assertTrue(mechanic("predictorprobe").getMechanic().isEmpty());
        decide.cast(VocabularyFixtures.metadata(VocabularyFixtures.caster(VocabularyFixtures.entity(ENTITY))));
        assertEquals(1, signals.pendingCount());
    }

    @Test
    void resolutionsLeaveOneEvidenceLinePerSpellingAndThread() {
        condition("predictormove", Map.of("m", "hold"));
        condition("predictormove", Map.of("m", "retreat"));
        condition("PredictorMove", Map.of("m", "hold"));
        mechanic("predictordecide");
        var thread = Thread.currentThread().getName();
        assertEquals(List.of("MythicMobs resolved condition predictormove on thread " + thread,
                "MythicMobs resolved condition PredictorMove on thread " + thread,
                "MythicMobs resolved mechanic predictordecide on thread " + thread), info);
    }
}
