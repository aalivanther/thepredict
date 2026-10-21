package akyrion.thepredict.adapter.server.context;

import akyrion.thepredict.adapter.server.context.EntityDoubles.FakeEntity;
import akyrion.thepredict.domain.ParticipantKind;
import akyrion.thepredict.domain.ParticipantRules;
import org.bukkit.GameMode;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Cow;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Slime;
import org.bukkit.entity.SpawnCategory;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Zombie;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The participant policy of M17.2 with doubles of entities (rule P4): each native selector, the order of the rules,
 * the sides, the profile rules against the global ones, the warnings of unresolved selectors, and the defaults of
 * 0.0.1, which admit exactly the players the eligibility check of 0.0.1 admitted and read nothing of other entities.
 */
class ParticipantPolicyTest {
    private final CaptureDoubles doubles = new CaptureDoubles();
    private final EntityDoubles.Lookup lookup = new EntityDoubles.Lookup();
    private final EntityDoubles.Registry registry = new EntityDoubles.Registry();
    private final List<String> warnings = new ArrayList<>();
    private final FakeEntity mob = entity(1, Zombie.class, EntityType.ZOMBIE, SpawnCategory.MONSTER);

    private FakeEntity entity(int value, Class<? extends LivingEntity> kind, EntityType type, SpawnCategory category) {
        return new FakeEntity(new UUID(0x2AL, value), kind, type, category, doubles.world, value, 0);
    }

    private ParticipantPolicy policy(List<String> allow, List<String> deny) {
        return policy(ParticipantDefaults.STANDARD, new ParticipantRules(Set.of("SURVIVAL", "ADVENTURE"), allow, deny,
                false));
    }

    private ParticipantPolicy policy(ParticipantDefaults defaults, ParticipantRules profile) {
        var global = ParticipantPolicy.GlobalSelectors.compile(defaults, registry, (path, reason) ->
                warnings.add(path + ": " + reason));
        return ParticipantPolicy.compile(defaults, global, profile, registry, (path, reason) ->
                warnings.add(path + ": " + reason));
    }

    private boolean admits(ParticipantPolicy policy, FakeEntity candidate) {
        return policy.admits(candidate.entity, mob.entity, Set.of(), lookup);
    }

    /** The eligibility check of 0.0.1, kept here to prove the defaults never change it. */
    private static boolean eligibleIn001(CaptureDoubles.FakePlayer player) {
        var mode = player.mode;
        return player.valid && !player.dead && (mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE);
    }

    @Test
    void theDefaultPolicyAdmitsExactlyThePlayersOf001AndReadsNothingOfAnotherEntity() {
        var player = doubles.player(1, 3, 0);
        for (var mode : GameMode.values()) {
            for (var dead : new boolean[] {false, true}) {
                for (var valid : new boolean[] {true, false}) {
                    player.mode = mode;
                    player.dead = dead;
                    player.valid = valid;
                    assertEquals(eligibleIn001(player), ParticipantPolicy.DEFAULT.admits(player.player, mob.entity,
                            Set.of(), lookup), mode + " dead " + dead + " valid " + valid);
                }
            }
        }
        var zombie = entity(2, Zombie.class, EntityType.ZOMBIE, SpawnCategory.MONSTER);
        assertFalse(ParticipantPolicy.DEFAULT.admits(zombie.entity, mob.entity, Set.of(), lookup));
        assertFalse(ParticipantPolicy.DEFAULT.admitsCheap(zombie.entity));
        assertEquals(List.of(), zombie.reads, "the default policy never reads a non-player");
        assertEquals(0, lookup.reads);
        assertFalse(ParticipantPolicy.DEFAULT.admitsEntities());
        assertFalse(ParticipantPolicy.DEFAULT.perceivesEntities());
        assertEquals(ParticipantPolicy.DEFAULT.admits(player.player, mob.entity, Set.of(), lookup),
                policy(List.of(), List.of()).admits(player.player, mob.entity, Set.of(), lookup));
    }

    @Test
    void theTypeTagCategoryAndKindSelectorsMatchWhatTheyNameAndNothingElse() {
        registry.types.put("minecraft:zombie", EntityType.ZOMBIE);
        registry.tags.put("mipack:duelistas", Set.of(EntityType.SKELETON));
        var zombie = entity(2, Zombie.class, EntityType.ZOMBIE, SpawnCategory.MONSTER);
        var cow = entity(3, Cow.class, EntityType.COW, SpawnCategory.ANIMAL);
        var slime = entity(4, Slime.class, EntityType.SLIME, SpawnCategory.MONSTER);
        var villager = entity(5, Villager.class, EntityType.VILLAGER, SpawnCategory.MISC);
        var skeleton = entity(6, Zombie.class, EntityType.SKELETON, SpawnCategory.MONSTER);
        assertTrue(admits(policy(List.of("type:zombie"), List.of()), zombie));
        assertFalse(admits(policy(List.of("type:minecraft:zombie"), List.of()), cow));
        assertTrue(admits(policy(List.of("tag:mipack:duelistas"), List.of()), skeleton));
        assertFalse(admits(policy(List.of("tag:mipack:duelistas"), List.of()), zombie));
        assertTrue(admits(policy(List.of("category:animal"), List.of()), cow));
        assertFalse(admits(policy(List.of("category:ANIMAL"), List.of()), zombie));
        assertTrue(admits(policy(List.of("kind:ENEMY"), List.of()), slime), "a slime is an Enemy");
        assertFalse(admits(policy(List.of("kind:MONSTER"), List.of()), slime), "but not a Monster");
        assertTrue(admits(policy(List.of("kind:ANIMAL"), List.of()), cow));
        assertTrue(admits(policy(List.of("kind:MOB"), List.of()), villager));
        assertFalse(admits(policy(List.of("kind:ENEMY"), List.of()), villager));
        assertTrue(admits(policy(List.of("kind:LIVING"), List.of()), villager));
        assertEquals(List.of(), warnings);
    }

    @Test
    void theMythicFactionTeamTaggedAndPredictorSelectorsMatchWhatTheyName() {
        var guard = entity(2, Zombie.class, EntityType.ZOMBIE, SpawnCategory.MONSTER);
        var other = entity(3, Zombie.class, EntityType.ZOMBIE, SpawnCategory.MONSTER);
        lookup.types.put(guard.id, "DuelBlue");
        lookup.factions.put(guard.id, "Duel_Blue");
        lookup.teams.put(guard.id, "Red");
        lookup.bound.put(guard.id, CaptureDoubles.bound(List.of(), false));
        guard.tags.add("arena");
        assertTrue(admits(policy(List.of("mythic:*"), List.of()), guard));
        assertFalse(admits(policy(List.of("mythic:*"), List.of()), other));
        assertTrue(admits(policy(List.of("mythic:DuelBlue"), List.of()), guard));
        assertFalse(admits(policy(List.of("mythic:duelblue"), List.of()), guard), "MythicMobs IDs are exact (I7)");
        assertTrue(admits(policy(List.of("faction:duel_blue"), List.of()), guard), "factions ignore case");
        assertTrue(admits(policy(List.of("team:RED"), List.of()), guard), "teams ignore case");
        assertFalse(admits(policy(List.of("team:blue"), List.of()), guard));
        assertTrue(admits(policy(List.of("tagged:arena"), List.of()), guard));
        assertFalse(admits(policy(List.of("tagged:Arena"), List.of()), guard), "scoreboard tags are exact");
        assertTrue(admits(policy(List.of("predictor"), List.of()), guard));
        assertFalse(admits(policy(List.of("predictor"), List.of()), other));
    }

    @Test
    void theRulesDecideInOrderAndNothingButTheGameModeAppliesToPlayers() {
        var zombie = entity(2, Zombie.class, EntityType.ZOMBIE, SpawnCategory.MONSTER);
        var living = policy(List.of("kind:LIVING"), List.of("type:zombie"));
        registry.types.put("minecraft:zombie", EntityType.ZOMBIE);
        var denied = policy(List.of("kind:LIVING"), List.of("type:zombie"));
        assertFalse(admits(denied, zombie), "a deny wins over an allow");
        lookup.factions.put(mob.id, "red");
        lookup.factions.put(zombie.id, "RED");
        assertFalse(admits(living, zombie), "the same side wins over an allow");
        lookup.factions.put(zombie.id, "blue");
        lookup.teams.put(mob.id, "arena");
        lookup.teams.put(zombie.id, "Arena");
        assertFalse(admits(living, zombie), "a shared team is a side too, after the factions");
        lookup.teams.remove(zombie.id);
        assertTrue(admits(living, zombie));
        assertFalse(living.admits(mob.entity, mob.entity, Set.of(), lookup), "never the mob itself");
        assertFalse(living.admits(zombie.entity, mob.entity, Set.of(zombie.id), lookup), "never a member");
        zombie.dead = true;
        assertFalse(admits(living, zombie));
        var stand = entity(3, ArmorStand.class, EntityType.ARMOR_STAND, SpawnCategory.MISC);
        assertFalse(admits(living, stand), "an armor stand never takes part");
        var player = doubles.player(1, 3, 0);
        lookup.teams.put(player.id, "arena");
        assertTrue(denied.admits(player.player, mob.entity, Set.of(), lookup), "no selector or side for players");
        player.mode = GameMode.CREATIVE;
        assertFalse(living.admits(player.player, mob.entity, Set.of(), lookup));
    }

    @Test
    void theProfileAddsToTheGlobalListsUnlessProfilesMayNotAllow() {
        var zombie = entity(2, Zombie.class, EntityType.ZOMBIE, SpawnCategory.MONSTER);
        var cow = entity(3, Cow.class, EntityType.COW, SpawnCategory.ANIMAL);
        var global = new ParticipantDefaults(new ParticipantRules(Set.of("SURVIVAL"), List.of("kind:ANIMAL"),
                List.of(), true), true, 3);
        var profile = new ParticipantRules(Set.of(), List.of("kind:MONSTER"), List.of(), true);
        var both = policy(global, profile);
        assertTrue(admits(both, zombie));
        assertTrue(admits(both, cow));
        assertTrue(both.perceivesEntities());
        assertEquals(3, both.maxCandidatesPerMember());
        var player = doubles.player(1, 3, 0);
        assertFalse(both.admits(player.player, mob.entity, Set.of(), lookup), "PlayerGameModes [] never admits");
        var closed = policy(new ParticipantDefaults(global.global(), false, 3), profile);
        assertFalse(admits(closed, zombie), "the Allow of the profile is ignored");
        assertTrue(admits(closed, cow));
    }

    @Test
    void theCheapTestLetsThroughWhatAdmitsCouldAcceptAndStopsTheRest() {
        var zombie = entity(2, Zombie.class, EntityType.ZOMBIE, SpawnCategory.MONSTER);
        var cow = entity(3, Cow.class, EntityType.COW, SpawnCategory.ANIMAL);
        var stand = entity(4, ArmorStand.class, EntityType.ARMOR_STAND, SpawnCategory.MISC);
        var monsters = policy(List.of("kind:MONSTER"), List.of("category:ANIMAL"));
        assertTrue(monsters.admitsCheap(zombie.entity));
        assertFalse(monsters.admitsCheap(cow.entity));
        assertFalse(monsters.admitsCheap(stand.entity));
        assertTrue(monsters.admitsCheap(doubles.player(1, 0, 0).player), "players are decided by admits");
        assertTrue(policy(List.of("faction:blue"), List.of()).admitsCheap(cow.entity), "a lookup selector must look");
        assertFalse(monsters.perceivesEntities(), "perceiveNonTargets is off");
    }

    @Test
    void aSelectorThatResolvesToNothingIsAWarningWithItsPathAndIsIgnored() {
        registry.types.put("minecraft:arrow", EntityType.ARROW);
        registry.teams.addAll(Set.of("Red", "RED"));
        var global = new ParticipantDefaults(new ParticipantRules(Set.of("SURVIVAL"), List.of("type:minecraft:dragon",
                "type:arrow"), List.of("tag:mipack:none"), false), true, 8);
        var profile = new ParticipantRules(Set.of(), List.of("team:red", "kind:MOB"), List.of(), false);
        var compiled = policy(global, profile);
        assertEquals(List.of("participants.entities.allow[0]: unknown entity type 'minecraft:dragon' is ignored",
                "participants.entities.allow[1]: entity type 'arrow' is not a living entity and is ignored",
                "participants.entities.deny[0]: unknown entity type tag 'mipack:none' is ignored; after"
                        + " /minecraft:reload run /mm reload",
                "Participants.Entities.Allow[0]: team 'red' matches 2 teams that differ only in case; all of them"
                        + " take part"), warnings);
        assertTrue(compiled.admitsEntities());
    }

    @Test
    void theKindOfAParticipantIsPlayerOnlyForPlayers() {
        assertEquals(ParticipantKind.PLAYER, ParticipantPolicy.kind(doubles.player(1, 0, 0).player));
        assertEquals(ParticipantKind.ENTITY, ParticipantPolicy.kind(mob.entity));
    }

    @Test
    void anAllowedEntityThatIsInvalidOrDeadIsNeverAdmitted() {
        var policy = policy(List.of("kind:MONSTER"), List.of());
        var zombie = entity(2, Zombie.class, EntityType.ZOMBIE, SpawnCategory.MONSTER);
        assertTrue(admits(policy, zombie));
        zombie.valid = false;
        assertFalse(admits(policy, zombie), "unloaded or removed");
        zombie.valid = true;
        zombie.dead = true;
        assertFalse(admits(policy, zombie), "dead");
    }
}
