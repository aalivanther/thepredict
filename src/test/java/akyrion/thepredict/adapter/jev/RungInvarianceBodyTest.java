package akyrion.thepredict.adapter.jev;

import akyrion.thepredict.domain.DecisionRequest;
import akyrion.thepredict.domain.DifficultyMode;
import akyrion.thepredict.domain.DifficultySettings;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.NativeLevelMode;
import akyrion.thepredict.domain.TacticalProfile;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * JS-15 and A10: with the encoder production uses, the same facts give the same body bytes whatever the difficulty of
 * the profile, from rung 0 to rung 8 and with or without the loops that move it.
 */
class RungInvarianceBodyTest {
    private final RequestEncoder encoder = new RequestEncoder("jev-1.13.0", new SemanticEncoder(12_000));

    @Test
    void aProfilePinnedToAnyRungSendsTheSameBytes() {
        var base = JevFixtures.request();
        var body = encoder.encode(base);
        for (var rung = 0; rung <= 8; rung++) {
            for (var loops : new boolean[] {false, true}) {
                var pinned = new DifficultySettings(Optional.of(DifficultyMode.PLAYER), OptionalInt.of(120),
                        OptionalDouble.of(0.2), rung, rung, OptionalInt.of(rung), loops, loops, NativeLevelMode.OFF);
                assertEquals(body, encoder.encode(withDifficulty(base, pinned)), "rung " + rung + " loops " + loops);
            }
        }
        assertFalse(body.contains("rung") || body.contains("difficulty"));
    }

    private static DecisionRequest withDifficulty(DecisionRequest request, DifficultySettings difficulty) {
        var profiles = new LinkedHashMap<MobKey, TacticalProfile>();
        request.profiles().forEach((mob, base) -> profiles.put(mob, new TacticalProfile(base.id(), base.mythicMobId(),
                base.objective(), base.tactics(), base.choices(), base.contexts(), base.movement(), base.health(),
                base.threat(), base.baseAi(), base.timing(), base.participants(), difficulty, base.stances(),
                base.telegraph(), base.poise(), base.zone(), base.group(), base.patterns(), base.memory(),
                base.decision(), base.distillMode())));
        return new DecisionRequest(request.encounter(), request.sequence(), request.facts(), request.questions(),
                profiles, request.fitChecks());
    }
}
