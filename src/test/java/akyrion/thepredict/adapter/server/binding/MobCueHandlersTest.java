package akyrion.thepredict.adapter.server.binding;

import akyrion.thepredict.domain.CueChannels;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Cue bits come only from the lines that listen for the signal (M23.5). */
class MobCueHandlersTest {
    @Test
    void soundAndParticleOnTheSignalSetBothChannels() {
        var lines = List.of("sound{s=tell} @self ~onSignal:PREDICTOR_TELL_BACK_OFF",
                "particle{p=cloud} @self ~onSignal:PREDICTOR_TELL_BACK_OFF",
                "sound{s=other} @self ~onSignal:PREDICTOR_TELL_BLOCK");
        assertEquals(CueChannels.AUDIO | CueChannels.VISUAL,
                MobCueHandlers.channels(lines, "PREDICTOR_TELL_BACK_OFF"));
        assertEquals(CueChannels.AUDIO, MobCueHandlers.channels(lines, "PREDICTOR_TELL_BLOCK"));
        assertEquals(0, MobCueHandlers.channels(lines, "PREDICTOR_TELL_DODGE"));
    }

    @Test
    void aHandlerIsReadByTheExactNameMythicMobsKeys() {
        var handlers = MobCueHandlers.read(List.of("sound{s=tell} @self ~onSignal:PREDICTOR_TELL_BLOCK",
                "skill{s=Stun} @self ~onSignal:PREDICTOR_STANCE_STAGGERED;x=1",
                "damage{a=1} @target ~onAttack"));
        assertTrue(handlers.handles("PREDICTOR_TELL_BLOCK"));
        assertTrue(handlers.handles("PREDICTOR_STANCE_STAGGERED"), "the name ends where the MythicMobs pattern ends");
        assertFalse(handlers.handles("PREDICTOR_TELL_DODGE"));
        assertFalse(handlers.handles("predictor_tell_block"), "MythicMobs matches the signal case-sensitively");
    }

    @Test
    void aGenericHandlerListensForEverySignalAndAMobWithoutLinesForNone() {
        var generic = MobCueHandlers.read(List.of("message{m=hi} @self ~onSignal"));
        assertTrue(generic.handles("PREDICTOR_TELL_BLOCK"));
        assertTrue(generic.handles("PREDICTOR_PLAN_SURROUND_TARGET"));
        assertSame(MobCueHandlers.NONE, MobCueHandlers.read(List.of("damage{a=1} @target ~onAttack")));
        assertSame(MobCueHandlers.NONE, MobCueHandlers.read(List.of()));
        assertFalse(MobCueHandlers.NONE.handles("PREDICTOR_TELL_BLOCK"));
    }
}
