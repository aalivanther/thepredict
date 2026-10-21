package akyrion.thepredict.adapter.server.zone;

import org.bukkit.Material;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZoneColumnReaderTest {
    @Test
    void anUnloadedColumnIsNotRead() {
        var calls = new ArrayList<String>();
        assertTrue(new ZoneColumnReader().type(world(false, calls), 3, 64, 4).isEmpty());
        assertEquals(List.of("isChunkLoaded"), calls);
        assertFalse(calls.contains("getType"));
    }

    @Test
    void aLoadedColumnReturnsTheType() {
        var calls = new ArrayList<String>();
        assertEquals(Material.STONE, new ZoneColumnReader().type(world(true, calls), 3, 64, 4).orElseThrow());
        assertEquals(List.of("isChunkLoaded", "getType"), calls);
    }

    private static World world(boolean loaded, List<String> calls) {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[] {World.class},
                (proxy, method, args) -> {
                    calls.add(method.getName());
                    return switch (method.getName()) {
                        case "isChunkLoaded" -> loaded;
                        case "getType" -> {
                            if (!loaded) {
                                throw new AssertionError("unloaded column was read");
                            }
                            yield Material.STONE;
                        }
                        default -> {
                            if (method.getReturnType() == boolean.class) {
                                yield false;
                            }
                            if (method.getReturnType() == int.class) {
                                yield 0;
                            }
                            yield null;
                        }
                    };
                });
    }
}
