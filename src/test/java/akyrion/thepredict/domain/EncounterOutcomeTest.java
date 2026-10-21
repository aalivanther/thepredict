package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EncounterOutcomeTest {
    @Test
    void aWipedProfileWinsEvenWhenThePlayerDiesToAnother() {
        var boss = new EncounterOutcomePolicy.ClockClose(1, false, false, true, true, 0.4, 100);
        var minions = List.of(
                new EncounterOutcomePolicy.ClockClose(2, false, true, false, false, 0.6, 20),
                new EncounterOutcomePolicy.ClockClose(2, false, true, false, false, 0.6, 20),
                new EncounterOutcomePolicy.ClockClose(2, false, true, false, false, 0.6, 20),
                new EncounterOutcomePolicy.ClockClose(2, false, true, false, false, 0.6, 20));
        var clocks = new java.util.ArrayList<EncounterOutcomePolicy.ClockClose>();
        clocks.add(boss);
        clocks.addAll(minions);
        var assigned = EncounterOutcomePolicy.assign(true, clocks, 0.2);
        assertEquals(FightOutcome.LOSS, outcome(assigned, 1));
        assertEquals(FightOutcome.WIN, outcome(assigned, 2));
        assertEquals(1, assigned.lastKiller().orElseThrow());
        assertEquals(2, assigned.lastVictim().orElseThrow());
    }

    @Test
    void equalHealthSharesOf015And035Win() {
        var clocks = List.of(
                new EncounterOutcomePolicy.ClockClose(7, false, true, false, false, 0.15, 40),
                new EncounterOutcomePolicy.ClockClose(7, false, true, false, false, 0.35, 40));
        var assigned = EncounterOutcomePolicy.assign(false, clocks, 0.2);
        assertEquals(FightOutcome.WIN, outcome(assigned, 7));
    }

    private static FightOutcome outcome(EncounterOutcomePolicy.Assignment assigned, int hash) {
        return assigned.outcomes().stream().filter(row -> row.profileHash() == hash).findFirst().orElseThrow()
                .outcome();
    }
}
