package akyrion.thepredict.adapter.server.binding;

import akyrion.thepredict.domain.DisplacementKind;
import akyrion.thepredict.domain.DisplacementOrigin;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A real damage line with pk is none. Throw stays. preventknockback is not the knockback mechanic. */
class DisplacementReaderTest {
    @Test
    void damageWithPkIsNoneAndThrowSurvivesTheBrake() {
        var unread = DisplacementReader.read(true, false, "pull\nthrow");
        assertEquals(DisplacementKind.KNOCKBACK, unread.target());
        assertEquals(DisplacementOrigin.ASSUMED, unread.origin());
        var stopped = DisplacementReader.read(true, true, "projectile{pk=true}");
        assertEquals(DisplacementKind.NONE, stopped.target());
        assertEquals(DisplacementOrigin.READ, stopped.origin());
        var projectile = DisplacementReader.read(true, true, "projectile");
        assertEquals(DisplacementKind.KNOCKBACK, projectile.target());
        assertEquals(DisplacementOrigin.ASSUMED, projectile.origin());
        assertEquals(DisplacementKind.STRONG_PUSH, DisplacementReader.read(true, true, "throw{pk=true}").target());
        assertEquals(DisplacementKind.PULL, DisplacementReader.read(true, true, "pull{pk=true}").target());
        assertEquals(DisplacementKind.STRONG_PUSH,
                DisplacementReader.read(true, true, "explosion{pk=true}").target());
        assertEquals(DisplacementKind.STRONG_PUSH, DisplacementReader.read(true, true, "throw\npull").target());
        var brake = DisplacementReader.read(true, true, "preventknockback");
        assertEquals(DisplacementKind.NONE, brake.target());
        assertEquals(DisplacementOrigin.READ, brake.origin());
        assertEquals(DisplacementKind.NONE, DisplacementReader.read(true, true, "damage{a=5;pk=true}").target());
        var open = DisplacementReader.read(true, true, "damage{a=5}");
        assertEquals(DisplacementKind.KNOCKBACK, open.target());
        assertEquals(DisplacementOrigin.READ, open.origin());
        assertEquals(DisplacementKind.NONE,
                DisplacementReader.read(true, true, "damage{a=5}\npreventknockback").target());
        assertEquals(DisplacementKind.STRONG_PUSH,
                DisplacementReader.read(true, true, "throw{velocity=6;pk=true}").target());
    }
}
