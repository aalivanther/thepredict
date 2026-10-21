package akyrion.thepredict.adapter.server.context;

import akyrion.thepredict.adapter.server.context.EntityDoubles.FakeEntity;
import akyrion.thepredict.domain.CombatFacts;
import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.ParticipantAlias;
import akyrion.thepredict.domain.ParticipantKind;
import org.bukkit.GameMode;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.SpawnCategory;
import org.bukkit.entity.Zombie;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static akyrion.thepredict.adapter.server.context.CaptureDoubles.mobId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Perception and targets of any living entity (M17.3, M17.5, M17.6) through the real capture with doubles: the queries
 * the default policy makes are those of 0.0.1, an admitted entity target enters with its species, the nearby query of
 * entities is capped, and two mobs that admit each other are rivals.
 */
class EntityCaptureTest {
    private static final EncounterId ENCOUNTER = new EncounterId(1);

    private final CaptureDoubles doubles = new CaptureDoubles();

    private static MobKey key(int value) {
        return new MobKey(mobId(value), 0);
    }

    private FakeEntity zombie(int value, double x, double z) {
        var zombie = new FakeEntity(new UUID(0x2AL, value), Zombie.class, EntityType.ZOMBIE, SpawnCategory.MONSTER,
                doubles.world, x, z);
        doubles.world.living.add(zombie.entity);
        return zombie;
    }

    private CombatFacts capture(MobKey... keys) {
        return doubles.capture().capture(ENCOUNTER, List.of(keys), doubles.context()).orElseThrow();
    }

    @Test
    void withTheDefaultPolicyOnlyThePlayersQueryIsMadeAndANearbyZombieIsNeverRead() {
        doubles.mob(1, 0, 0);
        doubles.player(1, 3, 0);
        var zombie = zombie(1, 2, 0);
        doubles.source.targets.put(mobId(1), zombie.entity);
        var facts = capture(key(1));
        assertTrue(facts.mobs().getFirst().target().isEmpty(), "a zombie target is no target, as in 0.0.1");
        assertEquals(List.of("player_1"), facts.participants().stream().map(p -> p.alias().text()).toList());
        assertEquals(1, doubles.world.calls.stream().filter(call -> call.startsWith("getNearbyPlayers")).count());
        assertTrue(doubles.world.calls.stream().noneMatch(call -> call.startsWith("getNearbyLivingEntities")));
        assertEquals(List.of(), zombie.reads);
    }

    @Test
    void anAdmittedZombieTargetIsAParticipantWithItsSpeciesAndOtherZombiesAreNotPerceived() {
        doubles.mob(1, 0, 0, CaptureDoubles.bound(EntityDoubles.policy(List.of("kind:MONSTER"), false, 8)));
        var target = zombie(1, 2, 0);
        zombie(2, 3, 0);
        doubles.player(1, 4, 0);
        doubles.source.targets.put(mobId(1), target.entity);
        var facts = capture(key(1));
        var relation = facts.mobs().getFirst().target().orElseThrow();
        assertEquals(new ParticipantAlias(ParticipantKind.ENTITY, 1), relation.target());
        assertEquals(List.of("entity_1 zombie", "player_1 -"), facts.participants().stream().map(participant ->
                participant.alias().text() + " " + participant.species().orElse("-")).toList());
        assertTrue(doubles.world.calls.stream().noneMatch(call -> call.startsWith("getNearbyLivingEntities")),
                "perceiveNonTargets is off: the players' query of 0.0.1");
        assertEquals(Optional.of(target.id), doubles.sightings.getFirst().targetId());
    }

    @Test
    void perceivingNonTargetsMakesOneLivingQueryAndKeepsOnlyTheNearestCandidates() {
        doubles.mob(1, 0, 0, CaptureDoubles.bound(EntityDoubles.policy(List.of("kind:MONSTER"), true, 2)));
        doubles.player(1, 9, 0);
        var zombies = new ArrayList<FakeEntity>();
        for (var index = 5; index >= 1; index--) {
            zombies.addFirst(zombie(index, index + 1, 0));
        }
        var facts = capture(key(1));
        assertEquals(List.of("getNearbyLivingEntities (0.0,64.0,0.0) 16.0"), doubles.world.calls.stream()
                .filter(call -> call.startsWith("getNearby")).toList());
        var entities = facts.participants().stream().filter(p -> p.alias().kind() == ParticipantKind.ENTITY).count();
        assertEquals(2, entities, "maxCandidatesPerMember");
        assertEquals(3, facts.participants().size(), "the player is not capped");
        var perceived = doubles.aliases.entrySet().stream().filter(entry -> entry.getValue().kind()
                == ParticipantKind.ENTITY).map(Map.Entry::getKey).collect(Collectors.toSet());
        assertEquals(Set.of(zombies.get(0).id, zombies.get(1).id), perceived,
                "the two nearest zombies, at 2 and 3 blocks, although the query returned the farthest first");
    }

    @Test
    void playersAndEntitiesTogetherStayWithinSixteenParticipants() {
        doubles.mob(1, 0, 0, CaptureDoubles.bound(EntityDoubles.policy(List.of("kind:LIVING"), true, 16)));
        for (var index = 1; index <= 12; index++) {
            doubles.player(index, 1 + index * 0.5, 1);
            zombie(index, 1 + index * 0.5, -1);
        }
        var facts = capture(key(1));
        assertEquals(CombatFacts.MAX_PARTICIPANTS, facts.participants().size());
    }

    @Test
    void eachLineOfSightIsComputedOncePerPairWhateverTheKind() {
        var mob = doubles.mob(1, 0, 0, CaptureDoubles.bound(EntityDoubles.policy(List.of("kind:MONSTER"), true, 8)));
        var target = zombie(1, 2, 0);
        doubles.source.targets.put(mobId(1), target.entity);
        capture(key(1));
        assertEquals(1, doubles.world.calls.stream().filter(call -> call.startsWith("hasLineOfSight")).count());
        assertEquals(1, mob.sightChecks);
    }

    @Test
    void aCreativePlayerIsNoTargetAndAnEntityInAnotherWorldIsDiscarded() {
        doubles.mob(1, 0, 0, CaptureDoubles.bound(EntityDoubles.policy(List.of("kind:MONSTER"), false, 8)));
        var creative = doubles.player(1, 2, 0);
        creative.mode = GameMode.CREATIVE;
        doubles.source.targets.put(mobId(1), creative.player);
        assertTrue(capture(key(1)).mobs().getFirst().target().isEmpty());
        var away = zombie(1, 2, 0);
        away.world = new CaptureDoubles.FakeWorld(CaptureDoubles.OTHER_WORLD_ID);
        var actor = doubles.source.members.get(mobId(1)).entity();
        var policy = doubles.source.members.get(mobId(1)).profile().participants();
        assertEquals(Optional.empty(), MythicMemberSource.choose(away.entity, () -> null, actor,
                candidate -> policy.admits(candidate, actor, Set.of(), ParticipantLookup.NONE)));
    }

    @Test
    void aPlayerHolderDecidesAsIn001AndARefusedEntityHolderLetsTheMobTargetDecide() {
        var actor = doubles.mob(1, 0, 0).entity;
        var creative = doubles.player(1, 2, 0);
        creative.mode = GameMode.CREATIVE;
        var survival = doubles.player(2, 3, 0);
        var reads = new AtomicInteger();
        var holder = MythicMemberSource.choose(creative.player, () -> {
            reads.incrementAndGet();
            return survival.player;
        }, actor, candidate -> false);
        assertEquals(Optional.of(creative.player), holder, "the capture then refuses it by game mode, as in 0.0.1");
        assertEquals(0, reads.get(), "the mob's own target is read only when the holder does not decide");
        var zombie = zombie(1, 2, 0);
        assertEquals(Optional.<LivingEntity>of(survival.player), MythicMemberSource.choose(zombie.entity,
                () -> survival.player, actor, candidate -> false));
        assertEquals(Optional.<LivingEntity>of(zombie.entity), MythicMemberSource.choose(zombie.entity,
                () -> survival.player, actor, candidate -> true));
    }

    @Test
    void twoMobsWhosePoliciesAdmitEachOtherAreRivalsUnlessTheyShareAFaction() {
        var living = CaptureDoubles.bound(EntityDoubles.policy(List.of("kind:LIVING"), false, 8));
        doubles.mob(1, 0, 0, living);
        doubles.mob(2, 3, 0, living);
        assertFalse(doubles.capture().withinFollowRange(key(1), key(2)), "rivals never share an encounter");
        var lookup = new EntityDoubles.Lookup();
        lookup.factions.put(mobId(1), "red");
        lookup.factions.put(mobId(2), "Red");
        doubles.lookup = lookup;
        assertTrue(doubles.capture().withinFollowRange(key(1), key(2)), "allies of one faction do");
    }
}
