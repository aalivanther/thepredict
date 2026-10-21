package akyrion.thepredict.adapter.server.execution;

import akyrion.thepredict.domain.CueRequest;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.TellAnswer;
import io.lumine.mythic.api.skills.Skill;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A cue is only the MythicMobs signal, and only when the mob already handles it (M23.5). */
class CueSignalTest {
    private static final MobKey MOB = new MobKey(new UUID(0x23L, 1), 0);

    @Test
    void withoutAHandlerThereIsNoSignalAndNoGesture() {
        var signals = new ArrayList<String>();
        var gestures = new ArrayList<String>();
        adapter(false, signals, gestures).cue(MOB, CueRequest.tell(TellAnswer.BLOCK));
        assertTrue(signals.isEmpty());
        assertTrue(gestures.isEmpty());
    }

    @Test
    void withAHandlerOnlyTheNamedSignalIsSent() {
        var signals = new ArrayList<String>();
        var gestures = new ArrayList<String>();
        adapter(true, signals, gestures).cue(MOB, CueRequest.tell(TellAnswer.DODGE));
        adapter(true, signals, gestures).cue(MOB, CueRequest.stance(
                akyrion.thepredict.domain.CombatStance.PRESSING));
        assertEquals(List.of("PREDICTOR_TELL_DODGE", "PREDICTOR_STANCE_PRESSING"), signals);
        assertTrue(gestures.isEmpty());
    }

    private static CombatActionsAdapter adapter(boolean handles, List<String> signals, List<String> gestures) {
        CastSource source = new CastSource() {
            @Override
            public Optional<LivingEntity> caster(UUID mobId) {
                return Optional.empty();
            }

            @Override
            public Optional<LivingEntity> target(UUID mobId) {
                return Optional.empty();
            }

            @Override
            public boolean onCooldown(UUID mobId, Skill skill) {
                return false;
            }

            @Override
            public boolean conditionsHold(UUID mobId, Skill skill) {
                return true;
            }

            @Override
            public boolean conditionsHoldAt(UUID mobId, Skill skill, LivingEntity target) {
                return true;
            }

            @Override
            public boolean execute(UUID mobId, Skill skill) {
                gestures.add("execute");
                return true;
            }

            @Override
            public boolean executeAt(UUID mobId, Skill skill, LivingEntity target) {
                gestures.add("executeAt");
                return true;
            }

            @Override
            public boolean handlesSignal(UUID mobId, String signal) {
                return handles;
            }

            @Override
            public boolean signal(UUID mobId, String signal) {
                signals.add(signal);
                return true;
            }

            @Override
            public boolean changeThreat(UUID mobId, LivingEntity participant, double amount) {
                return false;
            }
        };
        return new CombatActionsAdapter(source, id -> Optional.empty(), entity -> java.util.OptionalDouble.empty(),
                entity -> false, id -> Optional.empty(), id -> Optional.empty(), () -> true);
    }
}
