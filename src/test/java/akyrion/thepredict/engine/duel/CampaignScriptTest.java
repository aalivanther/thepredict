package akyrion.thepredict.engine.duel;

import akyrion.thepredict.domain.PlayerMemory;
import akyrion.thepredict.domain.PlayerMemoryCodec;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampaignScriptTest {
    @Test
    void tenThousandCampaignsKeepTheirRulesWithoutSqlite() {
        var store = new InMemoryMemoryStore();
        var stableHits = 0;
        for (var index = 0; index < 10_000; index++) {
            var script = CampaignScripts.stable(0.5, 0.8);
            var style = script.styleAt(index % 8, 0, index);
            if (style.equals("spam_clicks")) {
                stableHits++;
            }
            if (index == 0) {
                var bytes = PlayerMemoryCodec.encode(PlayerMemory.empty(), 8).bytes();
                var subject = new UUID(index, 1);
                store.save(subject, bytes);
                assertTrue(java.util.Arrays.equals(bytes, store.load(subject).orElseThrow()));
            }
        }
        assertEquals(10_000, stableHits);
        assertEquals(1, store.size());
        var adapt = CampaignScripts.stable(0.4, 0.9).adaptsAfterExploited(3, "strafe");
        assertEquals("spam_clicks", adapt.styleAt(2, 2, 0));
        assertEquals("strafe", adapt.styleAt(4, 3, 0));
        var switched = CampaignScripts.stable(0.4, 0.9).switchAt(5);
        assertEquals("switched", switched.styleAt(5, 0, 0));
        assertEquals("inverted", CampaignScripts.invert(0.4).styleAt(1, 0, 0));
        assertEquals("diluted", CampaignScripts.dilute(0.4).styleAt(1, 0, 0));
        var drifted = CampaignScripts.stable(0.4, 0.9).drift();
        assertEquals("spam_clicks-3", drifted.styleAt(3, 0, 0));
        var returned = CampaignScripts.stable(0.4, 0.9).invert().returnsAfter(10);
        assertEquals("spam_clicks", returned.styleAt(3, 0, 10));
        var grown = CampaignScripts.stable(0.4, 0.9).skillGrowth(0.7);
        assertEquals(1.0, grown.grownSkill(), 0.0);
        assertTrue(grown.player().reactionTicks() >= 0);
    }
}
