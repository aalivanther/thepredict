package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerMemoryCodecTest {
    private static final long DAY = 20_900L;

    @Test
    void worstCaseIs680AndSaturatedIs713() {
        var ordinary = PlayerMemoryCodec.encode(worst(1_000_000L), 8);
        var saturated = PlayerMemoryCodec.encode(worst((1L << 28) - 1), 8);
        assertEquals(680, ordinary.bytes().length);
        assertEquals(0, ordinary.profilesEvicted());
        assertEquals(713, saturated.bytes().length);
        assertInstanceOf(MemoryDecoded.Ready.class, PlayerMemoryCodec.decode(ordinary.bytes()));
        var again = PlayerMemoryCodec.encode(
                ((MemoryDecoded.Ready) PlayerMemoryCodec.decode(ordinary.bytes())).memory(), 8);
        assertEquals(ordinary.bytes().length, again.bytes().length);
        assertTrue(java.util.Arrays.equals(ordinary.bytes(), again.bytes()));
    }

    @Test
    void newerAndCorruptDoNotThrow() {
        assertInstanceOf(MemoryDecoded.Newer.class, PlayerMemoryCodec.decode(new byte[] {2, 16, 25, 0}));
        assertInstanceOf(MemoryDecoded.Newer.class, PlayerMemoryCodec.decode(new byte[] {1, 17, 25, 0}));
        assertInstanceOf(MemoryDecoded.Corrupt.class, PlayerMemoryCodec.decode(new byte[] {1}));
        assertInstanceOf(MemoryDecoded.Corrupt.class, PlayerMemoryCodec.decode(new byte[0]));
    }

    @Test
    void aRecordLongerThanTheCapIsCorruptBeforeItIsRead() {
        var bytes = PlayerMemoryCodec.encode(worst(1_000_000L), 8).bytes();
        assertInstanceOf(MemoryDecoded.Ready.class, PlayerMemoryCodec.decode(bytes));
        var longer = java.util.Arrays.copyOf(bytes, PlayerMemoryCodec.MAX_BYTES + 1);
        var corrupt = assertInstanceOf(MemoryDecoded.Corrupt.class, PlayerMemoryCodec.decode(longer));
        assertEquals("size", corrupt.cause());
    }

    @Test
    void aFloat16ThatIsNotFiniteIsCorrupt() {
        var success = new float[StyleMemory.PATTERNS];
        success[0] = 1f;
        var style = new StyleMemory(1f, 1, success, new float[StyleMemory.PATTERNS], new int[StyleMemory.PATTERNS],
                new float[StyleMemory.ACTIONS], 0f, new int[0], new float[0], new float[StyleMemory.HISTOGRAMS],
                new byte[StyleMemory.HISTOGRAMS][StyleMemory.BINS], 0f, 0f, 0f, new float[StyleMemory.EPISODES],
                new float[StyleMemory.EPISODES]);
        var memory = new PlayerMemory(1, 1, 1, Optional.empty(), Optional.empty(), Optional.of(style), List.of());
        var bytes = PlayerMemoryCodec.encode(memory, 8).bytes();
        assertInstanceOf(MemoryDecoded.Ready.class, PlayerMemoryCodec.decode(bytes));
        var at = 4 + 3 + 2 + 4;
        assertEquals(Float.floatToFloat16(1f) & 0xFFFF, ((bytes[at] & 0xFF) << 8) | (bytes[at + 1] & 0xFF));
        for (var bits : new int[] {0x7C00, 0xFC00, 0x7E00}) {
            var patched = bytes.clone();
            patched[at] = (byte) (bits >>> 8);
            patched[at + 1] = (byte) bits;
            assertInstanceOf(MemoryDecoded.Corrupt.class, PlayerMemoryCodec.decode(patched), Integer.toHexString(bits));
        }
    }

    @Test
    void profilesPastTheCapAreEvictedAndStyleStays() {
        var profiles = new ArrayList<ProfileMemory>();
        for (var index = 0; index < 12; index++) {
            profiles.add(profile(index, 1_000_000L, DAY - index));
        }
        var memory = new PlayerMemory(DAY, DAY, 1_000_000L, Optional.of(new NarrativeMemory(1, DAY)),
                Optional.of(new NarrativeMemory(2, DAY)), Optional.of(style()), profiles);
        var encoded = PlayerMemoryCodec.encode(memory, 8);
        assertTrue(encoded.profilesEvicted() >= 4);
        var ready = assertInstanceOf(MemoryDecoded.Ready.class, PlayerMemoryCodec.decode(encoded.bytes()));
        assertEquals(8, ready.memory().profiles().size());
        assertTrue(ready.memory().style().isPresent());
        assertTrue(encoded.bytes().length <= PlayerMemoryCodec.MAX_BYTES);
    }

    private static PlayerMemory worst(long count) {
        var profiles = new ArrayList<ProfileMemory>();
        for (var index = 0; index < 8; index++) {
            profiles.add(profile(index, count, DAY));
        }
        return new PlayerMemory(DAY, DAY, count, Optional.of(new NarrativeMemory(1, DAY)),
                Optional.of(new NarrativeMemory(2, DAY)), Optional.of(style()), profiles);
    }

    private static ProfileMemory profile(int hash, long count, long day) {
        var rungs = new int[ProfileMemory.RUNGS];
        java.util.Arrays.fill(rungs, ProfileMemory.NONE);
        return new ProfileMemory(hash, count, count, count, count, FightOutcome.OTHER, day, 1f, 1f, 1f, rungs,
                List.of(new CounterMemory(24, 1f, 1f), new CounterMemory(24, 1f, 1f)));
    }

    private static StyleMemory style() {
        var success = new float[StyleMemory.PATTERNS];
        var failure = new float[StyleMemory.PATTERNS];
        var cusum = new int[StyleMemory.PATTERNS];
        java.util.Arrays.fill(success, 1f);
        java.util.Arrays.fill(failure, 1f);
        java.util.Arrays.fill(cusum, 1);
        var actions = new float[StyleMemory.ACTIONS];
        java.util.Arrays.fill(actions, 1f);
        var keys = new int[StyleMemory.TOP];
        var weights = new float[StyleMemory.TOP];
        java.util.Arrays.fill(keys, 255);
        java.util.Arrays.fill(weights, 1f);
        var mass = new float[StyleMemory.HISTOGRAMS];
        var shape = new byte[StyleMemory.HISTOGRAMS][StyleMemory.BINS];
        java.util.Arrays.fill(mass, 1f);
        for (var row : shape) {
            java.util.Arrays.fill(row, (byte) 255);
        }
        var episodes = new float[StyleMemory.EPISODES];
        java.util.Arrays.fill(episodes, 1f);
        return new StyleMemory(1f, (1 << StyleMemory.PATTERNS) - 1, success, failure, cusum, actions, 1f, keys,
                weights, mass, shape, 1f, 1f, 1f, episodes, episodes);
    }
}
