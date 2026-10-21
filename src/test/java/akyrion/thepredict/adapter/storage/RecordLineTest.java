package akyrion.thepredict.adapter.storage;

import akyrion.thepredict.domain.ActionStance;
import akyrion.thepredict.domain.BaseStance;
import akyrion.thepredict.domain.CombatStance;
import akyrion.thepredict.domain.DangerClass;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.RecordBodies;
import akyrion.thepredict.domain.RecordLevel;
import akyrion.thepredict.domain.ShortWindUp;
import akyrion.thepredict.domain.StanceCause;
import akyrion.thepredict.domain.StanceChange;
import akyrion.thepredict.domain.StanceNativeMode;
import akyrion.thepredict.domain.TelegraphEnd;
import akyrion.thepredict.domain.TelegraphResult;
import akyrion.thepredict.domain.TelegraphSummary;
import akyrion.thepredict.domain.TellAnswer;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecordLineTest {
    @Test
    void aLineIsValidJsonWithTheEnvelopeEscapesAndNullForNonFiniteNumbers() {
        var fields = new LinkedHashMap<String, String>();
        fields.put("note", JsonText.quote("say \"hi\" \\ end\n\u0001"));
        fields.put("effect", JsonText.number(Double.NaN));
        fields.put("limit", JsonText.number(Double.POSITIVE_INFINITY));
        var line = RecordLine.format(new RecordEvent("planned", "20261006T120000Z-0a1b", 7L, 8L, 9L, "arm-a",
                fields));
        assertFalse(line.contains(": "));
        var object = JsonParser.parseString(line).getAsJsonObject();
        assertEquals(1, object.get("v").getAsInt());
        assertEquals("planned", object.get("type").getAsString());
        assertEquals("20261006T120000Z-0a1b", object.get("run").getAsString());
        assertEquals(7L, object.get("t_ms").getAsLong());
        assertEquals(8L, object.get("t_nanos").getAsLong());
        assertEquals(9L, object.get("tick").getAsLong());
        assertEquals("arm-a", object.get("tag").getAsString());
        assertEquals("say \"hi\" \\ end\n\u0001", object.get("note").getAsString());
        assertTrue(object.get("effect").isJsonNull());
        assertTrue(object.get("limit").isJsonNull());
    }

    @Test
    void fightAndPressureLinesKeepVersionOne() {
        var fight = new LinkedHashMap<String, String>();
        fight.put("event", JsonText.quote("ENGAGED"));
        fight.put("elapsed_ticks", "11");
        fight.put("rung", "8");
        var fightLine = RecordLine.format(new RecordEvent("fight", "run", 1L, 2L, 3L, "m22", fight));
        var fightObject = JsonParser.parseString(fightLine).getAsJsonObject();
        assertEquals(1, fightObject.get("v").getAsInt());
        assertEquals("fight", fightObject.get("type").getAsString());
        assertEquals("ENGAGED", fightObject.get("event").getAsString());
        var pressure = new LinkedHashMap<String, String>();
        pressure.put("from", "8");
        pressure.put("to", "7");
        pressure.put("cause", JsonText.quote("IN_FIGHT"));
        var pressureLine = RecordLine.format(new RecordEvent("pressure", "run", 1L, 2L, 3L, "m22", pressure));
        var pressureObject = JsonParser.parseString(pressureLine).getAsJsonObject();
        assertEquals("pressure", pressureObject.get("type").getAsString());
        assertEquals(7, pressureObject.get("to").getAsInt());
    }

    @Test
    void stanceAndTelegraphLinesKeepVersionOneAndOmitTheRetiredCue() {
        var stance = RecordLine.format(new RecordEvent("stance", "run", 1L, 2L, 3L, "m23", stanceFields()));
        var stanceObject = JsonParser.parseString(stance).getAsJsonObject();
        assertEquals(1, stanceObject.get("v").getAsInt());
        assertEquals("stance", stanceObject.get("type").getAsString());
        assertEquals("WINDUP", stanceObject.get("to").getAsString());
        assertEquals("TELEGRAPH_START", stanceObject.get("cause").getAsString());
        assertFalse(stanceObject.has("default_cue"));
        var telegraph = RecordLine.format(new RecordEvent("telegraph", "run", 1L, 2L, 4L, "m23", telegraphFields()));
        var telegraphObject = JsonParser.parseString(telegraph).getAsJsonObject();
        assertEquals(1, telegraphObject.get("v").getAsInt());
        assertEquals("telegraph", telegraphObject.get("type").getAsString());
        assertEquals("SEVERE", telegraphObject.get("danger").getAsString());
        assertEquals("LANDED", telegraphObject.get("result").getAsString());
        assertEquals("lead", telegraphObject.get("short_windup").getAsString());
        assertFalse(telegraphObject.has("default_cue"));
    }

    @Test
    void decisionsRecordATelegraphAndFullAlsoRecordsAStance() {
        var mob = new MobKey(UUID.fromString("00000000-0000-0000-0000-000000000017"), 0);
        var decisions = new ArrayList<RecordEvent>();
        var decisionTap = tap(RecordLevel.DECISIONS, decisions);
        decisionTap.stanceChanged(mob, stanceChange());
        decisionTap.telegraphClosed(mob, summary());
        assertEquals(0, decisions.stream().filter(event -> event.type().equals("stance")).count());
        assertEquals(1, decisions.stream().filter(event -> event.type().equals("telegraph")).count());
        var full = new ArrayList<RecordEvent>();
        var fullTap = tap(RecordLevel.FULL, full);
        fullTap.stanceChanged(mob, stanceChange());
        fullTap.telegraphClosed(mob, summary());
        assertEquals(1, full.stream().filter(event -> event.type().equals("stance")).count());
        assertEquals(1, full.stream().filter(event -> event.type().equals("telegraph")).count());
        assertFalse(full.stream().filter(event -> event.type().equals("telegraph"))
                .findFirst().orElseThrow().fields().containsKey("default_cue"));
    }

    private static RecordTap tap(RecordLevel level, ArrayList<RecordEvent> events) {
        return new RecordTap(level, RecordBodies.NONE, "run", Clock.systemUTC(), () -> 3L, () -> 4L, events::add);
    }

    private static LinkedHashMap<String, String> stanceFields() {
        var fields = new LinkedHashMap<String, String>();
        fields.put("to", JsonText.quote("WINDUP"));
        fields.put("cause", JsonText.quote("TELEGRAPH_START"));
        return fields;
    }

    private static LinkedHashMap<String, String> telegraphFields() {
        var fields = new LinkedHashMap<String, String>();
        fields.put("danger", JsonText.quote("SEVERE"));
        fields.put("result", JsonText.quote("LANDED"));
        fields.put("short_windup", JsonText.quote("lead"));
        return fields;
    }

    private static StanceChange stanceChange() {
        return new StanceChange(CombatStance.NEUTRAL, CombatStance.WINDUP, BaseStance.NEUTRAL, ActionStance.WINDUP,
                false, StanceCause.TELEGRAPH_START, 4L, StanceNativeMode.HYBRID, "none", true, false);
    }

    private static TelegraphSummary summary() {
        return new TelegraphSummary(0, "slam", TelegraphEnd.RELEASED, DangerClass.SEVERE, TellAnswer.BACK_OFF, false,
                TelegraphResult.LANDED, "guardian", "player-1", "player", 10L, 28, 0, 28, 28, ShortWindUp.LEAD, false,
                0.1, 20, false, 0.0, 0, 8);
    }
}
