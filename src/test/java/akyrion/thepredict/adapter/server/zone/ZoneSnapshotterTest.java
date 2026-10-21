package akyrion.thepredict.adapter.server.zone;

import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZoneSnapshotterTest {
    @Test
    void anUnloadedChunkIsNotAskedFor() {
        var calls = new ArrayList<String>();
        var world = world(false, null, calls);
        assertTrue(new ZoneSnapshotter().capture(world, 1, 2).isEmpty());
        assertEquals(List.of("isChunkLoaded"), calls);
        assertFalse(calls.contains("getChunkAt"));
        assertFalse(calls.contains("getChunkAtAsync"));
    }

    @Test
    void aLoadedChunkIsSnapshottedWithThePlanFlags() {
        var calls = new ArrayList<String>();
        var snapshot = (ChunkSnapshot) Proxy.newProxyInstance(ChunkSnapshot.class.getClassLoader(),
                new Class<?>[] {ChunkSnapshot.class}, (proxy, method, args) -> value(method.getReturnType()));
        var chunk = (Chunk) Proxy.newProxyInstance(Chunk.class.getClassLoader(), new Class<?>[] {Chunk.class},
                (proxy, method, args) -> {
                    calls.add(method.getName());
                    if (method.getName().equals("getChunkSnapshot")) {
                        assertEquals(Boolean.TRUE, args[0]);
                        assertEquals(Boolean.FALSE, args[1]);
                        assertEquals(Boolean.FALSE, args[2]);
                        assertEquals(Boolean.FALSE, args[3]);
                        return snapshot;
                    }
                    return value(method.getReturnType());
                });
        assertTrue(new ZoneSnapshotter().capture(world(true, chunk, calls), 3, 4).isPresent());
        assertEquals(List.of("isChunkLoaded", "getChunkAt", "getChunkSnapshot"), calls);
    }

    private static World world(boolean loaded, Chunk chunk, List<String> calls) {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[] {World.class},
                (proxy, method, args) -> {
                    calls.add(method.getName());
                    return switch (method.getName()) {
                        case "isChunkLoaded" -> loaded;
                        case "getChunkAt" -> {
                            if (!loaded) {
                                throw new AssertionError("unloaded chunk was requested");
                            }
                            yield chunk;
                        }
                        case "getChunkAtAsync" -> throw new AssertionError("async chunk load");
                        default -> value(method.getReturnType());
                    };
                });
    }

    private static Object value(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == double.class) return 0.0;
        if (type == float.class) return 0f;
        if (type == short.class) return (short) 0;
        if (type == byte.class) return (byte) 0;
        if (type == char.class) return (char) 0;
        return null;
    }
}
