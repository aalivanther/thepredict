package akyrion.thepredict.adapter.server.zone;

import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.ZoneSettings;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ZoneBlockWatchTest {
    /** SP-14: lava from a bucket marks its column; SP-16: a block far above the band marks nothing. */
    @Test
    void anEmptiedBucketMarksTheColumnAndABlockOutsideTheBandDoesNot() {
        var registry = new ZoneRegistry(new ZoneWorker(1), () -> 0L);
        try {
            var id = new EncounterId(40);
            var world = world();
            registry.open(id, List.of(), ZoneSettings.DEFAULT);
            registry.anchor(id, world, 0, 0, 64);
            var watch = new ZoneBlockWatch(registry, () -> 5L);
            watch.onBucketEmpty(bucket(block(world, 3, 64, 3)));
            assertEquals(1, registry.dirtyCount(id));
            watch.onBucketEmpty(bucket(block(world, 4, 200, 3)));
            assertEquals(1, registry.dirtyCount(id));
            var drained = block(world, 5, 60, 3);
            watch.onBucketFill(new PlayerBucketFillEvent(player(), drained, drained, BlockFace.UP, Material.BUCKET,
                    null, EquipmentSlot.HAND));
            assertEquals(2, registry.dirtyCount(id));
        } finally {
            registry.closeWorker();
        }
    }

    /** SP-14: a dispenser marks its column and its four horizontal neighbours; its facing is block data (C19). */
    @Test
    void aDispenserMarksItsColumnAndItsFourHorizontalNeighbours() {
        var registry = new ZoneRegistry(new ZoneWorker(1), () -> 0L);
        try {
            var id = new EncounterId(41);
            var world = world();
            registry.open(id, List.of(), ZoneSettings.DEFAULT);
            registry.anchor(id, world, 0, 0, 64);
            var watch = new ZoneBlockWatch(registry, () -> 5L);
            watch.onDispense(new BlockDispenseEvent(block(world, 3, 64, 3), null, new Vector()));
            assertEquals(5, registry.dirtyCount(id), "the dispenser column and the four columns beside it");
        } finally {
            registry.closeWorker();
        }
    }

    private static PlayerBucketEmptyEvent bucket(Block block) {
        return new PlayerBucketEmptyEvent(player(), block, block, BlockFace.UP, Material.LAVA_BUCKET, null,
                EquipmentSlot.HAND);
    }

    private static Player player() {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "hashCode" -> 1;
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static Block block(World world, int x, int y, int z) {
        return (Block) Proxy.newProxyInstance(Block.class.getClassLoader(), new Class<?>[] {Block.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getWorld" -> world;
                    case "getX" -> x;
                    case "getY" -> y;
                    case "getZ" -> z;
                    case "getRelative" -> neighbour(world, x, y, z, args);
                    case "hashCode" -> x * 31 + z;
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static Block neighbour(World world, int x, int y, int z, Object[] args) {
        if (args.length != 1 || !(args[0] instanceof BlockFace face)) {
            throw new UnsupportedOperationException("getRelative");
        }
        return block(world, x + face.getModX(), y + face.getModY(), z + face.getModZ());
    }

    private static World world() {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[] {World.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getMinHeight" -> -64;
                    case "isChunkLoaded" -> false;
                    case "hashCode" -> 7;
                    case "equals" -> proxy == args[0];
                    case "toString" -> "world";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
