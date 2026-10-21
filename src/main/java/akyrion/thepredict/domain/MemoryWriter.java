package akyrion.thepredict.domain;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Writes version 1 of {@link PlayerMemory}. Profiles past the cap or past {@link PlayerMemoryCodec#MAX_BYTES} are
 * dropped. The style is never trimmed.
 */
final class MemoryWriter {
    private MemoryWriter() {
    }

    static EncodeResult write(PlayerMemory memory, int maxProfiles) {
        var kept = keep(memory.profiles(), maxProfiles);
        var evicted = memory.profiles().size() - kept.size();
        var bytes = bytes(memory, kept);
        while (bytes.length > PlayerMemoryCodec.MAX_BYTES && !kept.isEmpty()) {
            kept.remove(kept.size() - 1);
            evicted++;
            bytes = bytes(memory, kept);
        }
        return new EncodeResult(bytes, evicted);
    }

    private static byte[] bytes(PlayerMemory memory, List<ProfileMemory> profiles) {
        var out = new ByteArrayOutputStream();
        header(out, memory);
        MemoryBytes.varint(out, memory.firstEpochDay());
        MemoryBytes.varint(out, memory.lastEpochDay());
        MemoryBytes.varint(out, memory.fights());
        mark(out, memory.lastKiller());
        mark(out, memory.lastVictim());
        memory.style().ifPresent(style -> style(out, style));
        MemoryBytes.u8(out, profiles.size());
        profiles.forEach(profile -> profile(out, profile));
        return out.toByteArray();
    }

    private static void header(ByteArrayOutputStream out, PlayerMemory memory) {
        var flags = 0;
        if (memory.style().isPresent()) {
            flags |= 1;
        }
        if (memory.lastKiller().isPresent()) {
            flags |= 2;
        }
        if (memory.lastVictim().isPresent()) {
            flags |= 4;
        }
        MemoryBytes.u8(out, 1);
        MemoryBytes.u8(out, StyleMemory.ACTIONS);
        MemoryBytes.u8(out, StyleMemory.PATTERNS);
        MemoryBytes.u8(out, flags);
    }

    private static void mark(ByteArrayOutputStream out, java.util.Optional<NarrativeMemory> mark) {
        mark.ifPresent(value -> {
            MemoryBytes.u32(out, value.hash());
            MemoryBytes.varint(out, value.epochDay());
        });
    }

    private static void style(ByteArrayOutputStream out, StyleMemory style) {
        MemoryBytes.f16(out, style.weight());
        MemoryBytes.u32(out, style.mask());
        for (var pattern = 0; pattern < StyleMemory.PATTERNS; pattern++) {
            if ((style.mask() & (1 << pattern)) != 0) {
                MemoryBytes.f16(out, style.success(pattern));
                MemoryBytes.f16(out, style.failure(pattern));
                MemoryBytes.u8(out, style.cusum(pattern));
            }
        }
        for (var action : style.actions()) {
            MemoryBytes.f16(out, action);
        }
        var keys = style.transitionKeys();
        var weights = style.transitionWeights();
        MemoryBytes.u8(out, keys.length);
        MemoryBytes.f16(out, style.transitionTotal());
        for (var index = 0; index < keys.length; index++) {
            MemoryBytes.u8(out, keys[index]);
            MemoryBytes.f16(out, weights[index]);
        }
        histograms(out, style);
        MemoryBytes.f16(out, style.reactionWeight());
        MemoryBytes.f16(out, style.reactionMean());
        MemoryBytes.f16(out, style.reactionM2());
        var counts = style.episodeCounts();
        var seconds = style.episodeSeconds();
        for (var index = 0; index < StyleMemory.EPISODES; index++) {
            MemoryBytes.f16(out, counts[index]);
            MemoryBytes.f16(out, seconds[index]);
        }
    }

    private static void histograms(ByteArrayOutputStream out, StyleMemory style) {
        for (var index = 0; index < StyleMemory.HISTOGRAMS; index++) {
            MemoryBytes.f16(out, style.histogramMass(index));
            for (var cell : style.histogramShape(index)) {
                out.write(cell & 0xFF);
            }
        }
    }

    private static void profile(ByteArrayOutputStream out, ProfileMemory profile) {
        MemoryBytes.u32(out, profile.hash());
        MemoryBytes.varint(out, profile.fights());
        MemoryBytes.varint(out, profile.wins());
        MemoryBytes.varint(out, profile.losses());
        MemoryBytes.varint(out, profile.disengaged());
        MemoryBytes.u8(out, profile.lastOutcome().ordinal());
        MemoryBytes.varint(out, profile.lastEpochDay());
        MemoryBytes.f16(out, profile.ttkWeight());
        MemoryBytes.f16(out, profile.ttkMean());
        MemoryBytes.f16(out, profile.ttkM2());
        for (var rung : profile.rungs()) {
            MemoryBytes.u8(out, rung);
        }
        MemoryBytes.u8(out, profile.counters().size());
        for (var counter : profile.counters()) {
            MemoryBytes.u8(out, counter.pattern());
            MemoryBytes.f16(out, counter.landed());
            MemoryBytes.f16(out, counter.tries());
        }
    }

    private static List<ProfileMemory> keep(List<ProfileMemory> profiles, int maxProfiles) {
        var ordered = new ArrayList<>(profiles);
        ordered.sort(Comparator.comparingLong(ProfileMemory::lastEpochDay).reversed()
                .thenComparingInt(ProfileMemory::hash));
        if (ordered.size() > maxProfiles) {
            return new ArrayList<>(ordered.subList(0, maxProfiles));
        }
        return ordered;
    }
}
