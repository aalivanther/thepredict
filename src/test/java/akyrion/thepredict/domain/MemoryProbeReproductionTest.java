package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryProbeReproductionTest {
    @Test
    void sizesAreExactAndForgettingMatchesTheProbeWithinTenPercent() {
        var worst = saturated(1_000_000L);
        var encoded = PlayerMemoryCodec.encode(worst, 8);
        assertEquals(680, encoded.bytes().length);
        var again = PlayerMemoryCodec.encode(roundTrip(encoded.bytes()), 8);
        assertTrue(java.util.Arrays.equals(encoded.bytes(), again.bytes()));
        var huge = saturated(268_435_455L);
        assertEquals(713, PlayerMemoryCodec.encode(huge, 8).bytes().length);
        assertEquals(0.5, PlayerMemoryMath.tau(30, 30), 0.05);
        var broken = PlayerMemoryMath.update(20, 2, 0, 0, 20, 0.9, 4, 0.25, 0.6);
        assertTrue(broken.breaks() <= 4);
        assertTrue(broken.successes() <= 20);
    }

    private static PlayerMemory roundTrip(byte[] bytes) {
        var decoded = PlayerMemoryCodec.decode(bytes);
        if (decoded instanceof MemoryDecoded.Ready ready) {
            return ready.memory();
        }
        throw new AssertionError(decoded);
    }

    private static PlayerMemory saturated(long count) {
        var profiles = new java.util.ArrayList<ProfileMemory>();
        for (var index = 0; index < 8; index++) {
            var counters = java.util.List.of(new CounterMemory(24, 1f, 1f), new CounterMemory(24, 1f, 1f));
            profiles.add(new ProfileMemory(index, count, count, count, count, FightOutcome.OTHER, 20900, 1f, 1f, 1f,
                    new int[] {255, 255, 255, 255, 255, 255}, counters));
        }
        return new PlayerMemory(20900, 20900, count, Optional.of(new NarrativeMemory(1, 20900)),
                Optional.of(new NarrativeMemory(2, 20900)), Optional.of(style()), profiles);
    }

    private static StyleMemory style() {
        var success = new float[25];
        var failure = new float[25];
        java.util.Arrays.fill(success, 1f);
        java.util.Arrays.fill(failure, 1f);
        var actions = new float[16];
        java.util.Arrays.fill(actions, 1f);
        var keys = new int[8];
        var weights = new float[8];
        java.util.Arrays.fill(keys, 255);
        java.util.Arrays.fill(weights, 1f);
        var mass = new float[4];
        java.util.Arrays.fill(mass, 1f);
        var shape = new byte[4][16];
        for (var row : shape) {
            java.util.Arrays.fill(row, (byte) 255);
        }
        var episodes = new float[10];
        java.util.Arrays.fill(episodes, 1f);
        return new StyleMemory(1f, (1 << 25) - 1, success, failure, new int[25], actions, 1f, keys, weights, mass,
                shape, 1f, 1f, 1f, episodes, episodes);
    }
}
