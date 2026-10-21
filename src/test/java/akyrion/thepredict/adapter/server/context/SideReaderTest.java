package akyrion.thepredict.adapter.server.context;

import akyrion.thepredict.adapter.server.context.EntityDoubles.FakeEntity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.SpawnCategory;
import org.bukkit.entity.Zombie;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Sides of rule I13 (M17.2): the MythicMobs faction, then the scoreboard team, never for players. */
class SideReaderTest {
    private final CaptureDoubles doubles = new CaptureDoubles();
    private final EntityDoubles.Lookup lookup = new EntityDoubles.Lookup();
    private final FakeEntity red = zombie(1);
    private final FakeEntity blue = zombie(2);

    private FakeEntity zombie(int value) {
        return new FakeEntity(new UUID(0x51DEL, value), Zombie.class, EntityType.ZOMBIE, SpawnCategory.MONSTER,
                doubles.world, value, 0);
    }

    @Test
    void twoMobsWithFactionsEqualIgnoringCaseAreOneSide() {
        lookup.factions.put(red.id, "Duel_Red");
        lookup.factions.put(blue.id, "duel_red");
        assertTrue(SideReader.sameSide(red.entity, blue.entity, lookup));
        lookup.factions.put(blue.id, "duel_blue");
        assertFalse(SideReader.sameSide(red.entity, blue.entity, lookup));
    }

    @Test
    void withoutASharedFactionTheTeamOfTheMainScoreboardDecides() {
        lookup.factions.put(red.id, "red");
        lookup.factions.put(blue.id, "blue");
        lookup.teams.put(red.id, "Arena");
        lookup.teams.put(blue.id, "ARENA");
        assertTrue(SideReader.sameSide(red.entity, blue.entity, lookup));
        lookup.teams.remove(blue.id);
        assertFalse(SideReader.sameSide(red.entity, blue.entity, lookup));
        assertFalse(SideReader.sameSide(zombie(3).entity, zombie(4).entity, lookup), "no faction and no team");
    }

    @Test
    void sidesNeverApplyToPlayersEvenOnTheSameTeam() {
        var player = doubles.player(1, 0, 0);
        lookup.teams.put(player.id, "arena");
        lookup.teams.put(red.id, "arena");
        assertFalse(SideReader.sameSide(player.player, red.entity, lookup));
        assertFalse(SideReader.sameSide(red.entity, player.player, lookup));
    }
}
