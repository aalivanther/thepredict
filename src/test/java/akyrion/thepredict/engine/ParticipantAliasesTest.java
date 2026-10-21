package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ParticipantAlias;
import akyrion.thepredict.domain.ParticipantKind;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static akyrion.thepredict.engine.EngineFixtures.player;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParticipantAliasesTest {
    private final Set<UUID> participants = new HashSet<>();
    private final ParticipantAliases aliases = new ParticipantAliases(participants::contains);

    @Test
    void thePlayersGetConsecutiveStableAliases() {
        assertEquals(new ParticipantAlias(1), aliases.aliasFor(player(1), ParticipantKind.PLAYER));
        assertEquals(new ParticipantAlias(2), aliases.aliasFor(player(2), ParticipantKind.PLAYER));
        assertEquals(new ParticipantAlias(1), aliases.aliasFor(player(1), ParticipantKind.PLAYER));
        assertEquals(Optional.of(new ParticipantAlias(2)), aliases.find(player(2)));
        assertTrue(aliases.find(player(3)).isEmpty());
        assertEquals(2, aliases.size());
    }

    @Test
    void beyondTheCapacityTheOldestFormerParticipantLosesItsAliasAndNoIndexIsReused() {
        participants.add(player(1));
        for (var index = 1; index <= ParticipantAliases.CAPACITY; index++) {
            aliases.aliasFor(player(index), ParticipantKind.PLAYER);
        }
        var newcomer = aliases.aliasFor(player(99), ParticipantKind.PLAYER);
        assertEquals(new ParticipantAlias(ParticipantAliases.CAPACITY + 1), newcomer);
        assertEquals(ParticipantAliases.CAPACITY, aliases.size());
        assertEquals(Optional.of(new ParticipantAlias(1)), aliases.find(player(1)));
        assertTrue(aliases.find(player(2)).isEmpty());
        assertEquals(new ParticipantAlias(ParticipantAliases.CAPACITY + 2), aliases.aliasFor(player(2), ParticipantKind.PLAYER));
    }

    @Test
    void whenEveryHolderIsAParticipantTheOldestAliasIsDropped() {
        for (var index = 1; index <= ParticipantAliases.CAPACITY; index++) {
            participants.add(player(index));
            aliases.aliasFor(player(index), ParticipantKind.PLAYER);
        }
        aliases.aliasFor(player(99), ParticipantKind.PLAYER);
        assertTrue(aliases.find(player(1)).isEmpty());
        assertEquals(ParticipantAliases.CAPACITY, aliases.size());
    }

    @Test
    void eachKindHasItsOwnCounterAndAnAliasNeverChangesKind() {
        assertEquals(new ParticipantAlias(ParticipantKind.PLAYER, 1), aliases.aliasFor(player(1), ParticipantKind.PLAYER));
        assertEquals(new ParticipantAlias(ParticipantKind.ENTITY, 1), aliases.aliasFor(player(2), ParticipantKind.ENTITY));
        assertEquals(new ParticipantAlias(ParticipantKind.ENTITY, 2), aliases.aliasFor(player(3), ParticipantKind.ENTITY));
        assertEquals(new ParticipantAlias(ParticipantKind.PLAYER, 2), aliases.aliasFor(player(4), ParticipantKind.PLAYER));
        assertEquals(new ParticipantAlias(ParticipantKind.ENTITY, 1), aliases.aliasFor(player(2), ParticipantKind.PLAYER),
                "a UUID keeps its alias whatever kind is asked");
        assertEquals(4, aliases.size());
    }

    @Test
    void theCapacityIsSharedByBothKindsAndTheEvictionRuleIsTheSame() {
        participants.add(player(1));
        for (var index = 1; index <= ParticipantAliases.CAPACITY; index++) {
            aliases.aliasFor(player(index), index % 2 == 0 ? ParticipantKind.ENTITY : ParticipantKind.PLAYER);
        }
        assertEquals(ParticipantAliases.CAPACITY, aliases.size());
        var newcomer = aliases.aliasFor(player(99), ParticipantKind.ENTITY);
        assertEquals(new ParticipantAlias(ParticipantKind.ENTITY, ParticipantAliases.CAPACITY / 2 + 1), newcomer);
        assertEquals(ParticipantAliases.CAPACITY, aliases.size());
        assertTrue(aliases.find(player(1)).isPresent(), "a current participant keeps its alias");
        assertTrue(aliases.find(player(2)).isEmpty(), "the oldest former participant loses its alias");
    }
}
