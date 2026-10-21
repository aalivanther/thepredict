package akyrion.thepredict.adapter.server.context;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The shield of any living entity (M17.4, DT-38): raised with the active item, blocking after its delay. */
class BlockingTest {
    @Test
    void theDelayOfTheComponentIsRoundedToTicksAsVanillaAndZeroBlocksAtOnce() {
        assertEquals(0, Blocking.delayTicks(0.0f));
        assertEquals(5, Blocking.delayTicks(0.25f));
        assertEquals(5, Blocking.delayTicks(0.26f), "5.2 ticks round to 5, as BlocksAttacks.blockDelayTicks");
        assertEquals(6, Blocking.delayTicks(0.28f), "5.6 ticks round to 6");
        assertEquals(Math.round(0.275f * 20f), Blocking.delayTicks(0.275f));
        assertEquals(20, Blocking.delayTicks(1.0f));
        assertEquals(0, Blocking.delayTicks(-1.0f));
        assertEquals(0, Blocking.delayTicks(Float.NaN));
    }

    @Test
    void aRaisedItemBlocksOnceItsUseReachesTheDelay() {
        assertEquals(new Blocking.BlockState(true, false), Blocking.state(true, 0.25f, 4));
        assertEquals(new Blocking.BlockState(true, true), Blocking.state(true, 0.25f, 5));
        assertEquals(new Blocking.BlockState(true, true), Blocking.state(true, 0.0f, 0));
        assertEquals(new Blocking.BlockState(false, false), Blocking.state(false, 0.0f, 40));
    }

    @Test
    void anEntityWithoutAnActiveItemNeitherRaisesNorBlocksAndTheObsoleteApiIsNeverRead() {
        var zombie = (LivingEntity) Proxy.newProxyInstance(LivingEntity.class.getClassLoader(),
                new Class<?>[] {LivingEntity.class}, (proxy, method, arguments) -> switch (method.getName()) {
                    case "hasActiveItem" -> false;
                    case "getActiveItem" -> null;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        assertEquals(new Blocking.BlockState(false, false), Blocking.of(zombie));
        assertFalse(Blocking.blocking(zombie));
    }

    @Test
    void aHumanBlocksWhenIsBlockingSaysSoAsIn001() {
        var doubles = new HumanDouble(true);
        assertTrue(Blocking.blocking(doubles.player));
        assertFalse(Blocking.blocking(new HumanDouble(false).player));
    }

    /** A player that answers only {@code isBlocking}: a human is judged by it alone. */
    private static final class HumanDouble {
        final Player player;

        HumanDouble(boolean blocking) {
            player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class},
                    (proxy, method, arguments) -> switch (method.getName()) {
                        case "isBlocking" -> blocking;
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }
    }
}
