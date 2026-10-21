package akyrion.thepredict.domain;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Optional;

/**
 * Reads version 1. A short buffer or an impossible length becomes {@link MemoryDecoded.Corrupt}. A newer version or
 * vocabulary becomes {@link MemoryDecoded.Newer}. Nothing is thrown to the caller.
 */
final class MemoryReader {
    private final ByteBuffer buffer;

    private MemoryReader(byte[] bytes) {
        buffer = ByteBuffer.wrap(bytes);
    }

    static MemoryDecoded read(byte[] bytes) {
        try {
            return new MemoryReader(bytes).decode();
        } catch (RuntimeException exception) {
            return new MemoryDecoded.Corrupt(exception.getClass().getSimpleName());
        }
    }

    private MemoryDecoded decode() {
        var version = u8();
        var actions = u8();
        var patterns = u8();
        var flags = u8();
        if (version > 1 || actions > StyleMemory.ACTIONS || patterns > StyleMemory.PATTERNS) {
            return new MemoryDecoded.Newer(version);
        }
        if (version != 1 || actions < 1 || patterns < 1) {
            return new MemoryDecoded.Corrupt("header");
        }
        var first = varint();
        var last = varint();
        var fights = varint();
        var killer = (flags & 2) != 0 ? Optional.of(mark()) : Optional.<NarrativeMemory>empty();
        var victim = (flags & 4) != 0 ? Optional.of(mark()) : Optional.<NarrativeMemory>empty();
        var style = (flags & 1) != 0 ? Optional.of(style(actions, patterns)) : Optional.<StyleMemory>empty();
        var count = u8();
        var profiles = new ArrayList<ProfileMemory>(count);
        for (var index = 0; index < count; index++) {
            profiles.add(profile());
        }
        if (buffer.hasRemaining()) {
            return new MemoryDecoded.Corrupt("trailing");
        }
        return new MemoryDecoded.Ready(new PlayerMemory(first, last, fights, killer, victim, style, profiles));
    }

    private NarrativeMemory mark() {
        return new NarrativeMemory(u32(), varint());
    }

    private StyleMemory style(int actionVocab, int patternVocab) {
        var weight = f16();
        var mask = u32();
        if ((mask & ~((1 << patternVocab) - 1)) != 0) {
            throw new IllegalArgumentException("mask");
        }
        var success = new float[StyleMemory.PATTERNS];
        var failure = new float[StyleMemory.PATTERNS];
        var cusum = new int[StyleMemory.PATTERNS];
        for (var pattern = 0; pattern < patternVocab; pattern++) {
            if ((mask & (1 << pattern)) != 0) {
                success[pattern] = f16();
                failure[pattern] = f16();
                cusum[pattern] = u8();
            }
        }
        var actions = new float[StyleMemory.ACTIONS];
        for (var index = 0; index < actionVocab; index++) {
            actions[index] = f16();
        }
        var kept = u8();
        if (kept > StyleMemory.TOP) {
            throw new IllegalArgumentException("transitions");
        }
        var total = f16();
        var keys = new int[kept];
        var weights = new float[kept];
        for (var index = 0; index < kept; index++) {
            keys[index] = u8();
            weights[index] = f16();
        }
        var mass = new float[StyleMemory.HISTOGRAMS];
        var shape = new byte[StyleMemory.HISTOGRAMS][StyleMemory.BINS];
        for (var index = 0; index < StyleMemory.HISTOGRAMS; index++) {
            mass[index] = f16();
            buffer.get(shape[index]);
        }
        var reactionWeight = f16();
        var reactionMean = f16();
        var reactionM2 = f16();
        var counts = new float[StyleMemory.EPISODES];
        var seconds = new float[StyleMemory.EPISODES];
        for (var index = 0; index < StyleMemory.EPISODES; index++) {
            counts[index] = f16();
            seconds[index] = f16();
        }
        return new StyleMemory(weight, mask, success, failure, cusum, actions, total, keys, weights, mass, shape,
                reactionWeight, reactionMean, reactionM2, counts, seconds);
    }

    private ProfileMemory profile() {
        var hash = u32();
        var fights = varint();
        var wins = varint();
        var losses = varint();
        var disengaged = varint();
        var outcome = FightOutcome.fromCode(u8());
        var day = varint();
        var weight = f16();
        var mean = f16();
        var moment = f16();
        var rungs = new int[ProfileMemory.RUNGS];
        for (var index = 0; index < rungs.length; index++) {
            rungs[index] = u8();
        }
        var counters = u8();
        if (counters > 2) {
            throw new IllegalArgumentException("counters");
        }
        var list = new ArrayList<CounterMemory>(counters);
        for (var index = 0; index < counters; index++) {
            list.add(new CounterMemory(u8(), f16(), f16()));
        }
        return new ProfileMemory(hash, fights, wins, losses, disengaged, outcome, day, weight, mean, moment, rungs,
                list);
    }

    private int u8() {
        return buffer.get() & 0xFF;
    }

    private int u32() {
        return buffer.getInt();
    }

    private float f16() {
        var bits = (u8() << 8) | u8();
        var value = Float.float16ToFloat((short) bits);
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException("float16");
        }
        return value;
    }

    private long varint() {
        var value = 0L;
        var shift = 0;
        for (var step = 0; step < 10; step++) {
            var bite = u8();
            value |= (long) (bite & 0x7F) << shift;
            if ((bite & 0x80) == 0) {
                return value;
            }
            shift += 7;
        }
        throw new IllegalArgumentException("varint");
    }
}
