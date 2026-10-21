package akyrion.thepredict.engine;

import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static akyrion.thepredict.engine.LoadFixture.WORLD_A;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The property of M17.6 under the load of M13: with mobs of ThePredict fighting each other in two factions, no entity
 * is ever a member and a participant of the same encounter, and every encounter holds one side only (rule I13).
 */
class EntityEncounterLoadTest {
    private static final int MOBS = 20;

    @Test
    void noEncounterEverHasAnEntityAsMemberAndAsParticipantAndEachHoldsOneSide() {
        try (var fixture = new LoadFixture()) {
            for (var index = 1; index <= MOBS; index++) {
                fixture.arena.factions.put(LoadFixture.mob(index), index % 2 == 1 ? "red" : "blue");
                fixture.arena.mobs.put(LoadFixture.mob(index), new LoadFixture.Body(WORLD_A, index, index % 3));
            }
            for (var index = 1; index <= MOBS; index++) {
                fixture.arena.targets.put(LoadFixture.mob(index), LoadFixture.mob(rival(index, 0)));
                fixture.sessions.lifecycle.discovered(LoadFixture.mob(index), fixture.profile);
            }
            fixture.ready();
            var checked = new long[1];
            fixture.run(LoadScenarios.MINUTE_TICKS, tick -> {
                for (var index = 1; index <= MOBS; index++) {
                    if ((tick + index) % 20 == 0) {
                        fixture.signals.offer(LoadFixture.mob(index));
                    }
                    if (tick % 200 == 0 && tick > 0 && index % 3 == 0) {
                        fixture.arena.targets.put(LoadFixture.mob(index), LoadFixture.mob(rival(index, tick / 200)));
                    }
                }
                checked[0] += check(fixture);
            });
            assertTrue(checked[0] > 0, "encounters were formed and checked");
            assertTrue(fixture.sessions.encounters.size() >= 2, "at least one encounter per side");
            assertFalse(fixture.jev.requests.isEmpty());
        }
    }

    /** A mob of the other faction: the odd mobs are red, the even ones blue. */
    private static int rival(int index, int shift) {
        var candidate = index % 2 == 1 ? index + 1 + 2 * shift : index - 1 - 2 * shift;
        return Math.floorMod(candidate - 1, MOBS) + 1;
    }

    private static int check(LoadFixture fixture) {
        var checked = 0;
        var seen = new HashSet<Encounter>();
        for (var session : fixture.sessions.registry.active()) {
            var encounter = session.encounter().flatMap(fixture.sessions.encounters::find);
            if (encounter.isEmpty() || !seen.add(encounter.get())) {
                continue;
            }
            var sides = new HashSet<String>();
            for (var member : encounter.get().members()) {
                assertFalse(encounter.get().isParticipant(member.entityId()), "member and participant at once");
                sides.add(fixture.arena.factions.get(member.entityId()));
            }
            assertEquals(1, sides.size(), "one side per encounter");
            checked++;
        }
        return checked;
    }
}
