package akyrion.thepredict.adapter.server.context;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure reads of the capture: the party count of M22.1 (SP-26) and the cooldown horizon (SP-21). */
class CaptureReadsTest {
    @Test
    void onlyLivingSurvivalOrAdventurePlayersCountInTheParty() {
        assertTrue(CombatCapture.countsForParty(player(GameMode.SURVIVAL, false)));
        assertTrue(CombatCapture.countsForParty(player(GameMode.ADVENTURE, false)));
        assertFalse(CombatCapture.countsForParty(player(GameMode.SPECTATOR, false)));
        assertFalse(CombatCapture.countsForParty(player(GameMode.CREATIVE, false)));
        assertFalse(CombatCapture.countsForParty(player(GameMode.SURVIVAL, true)));
    }

    @Test
    void theHorizonTakesAnUnknownTimerAsOutside() {
        assertTrue(FactsAssembler.withinHorizon(true, FactsAssembler.UNKNOWN_MILLIS, 10));
        assertFalse(FactsAssembler.withinHorizon(false, FactsAssembler.UNKNOWN_MILLIS, 10));
        assertFalse(FactsAssembler.withinHorizon(false, Long.MAX_VALUE, 10));
        assertTrue(FactsAssembler.withinHorizon(false, 500L, 10));
        assertFalse(FactsAssembler.withinHorizon(false, 501L, 10));
        assertFalse(FactsAssembler.withinHorizon(false, 0L, -1));
    }

    private static Player player(GameMode mode, boolean dead) {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getGameMode" -> mode;
                    case "isDead" -> dead;
                    case "isValid" -> !dead;
                    case "hashCode" -> 1;
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
