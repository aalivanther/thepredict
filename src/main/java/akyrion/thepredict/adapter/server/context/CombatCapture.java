package akyrion.thepredict.adapter.server.context;

import akyrion.thepredict.domain.CaptureContext;
import akyrion.thepredict.domain.DifficultyPreset;
import akyrion.thepredict.domain.CombatFacts;
import akyrion.thepredict.domain.GroupRelations;
import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.MobProbe;
import akyrion.thepredict.domain.MobSighting;
import akyrion.thepredict.domain.ThreatTableState;
import akyrion.thepredict.engine.CombatReader;
import akyrion.thepredict.engine.IncomingProjectiles;
import akyrion.thepredict.engine.ServerClock;
import io.lumine.mythic.core.skills.SkillCondition;
import org.bukkit.GameRules;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/**
 * {@link CombatReader} over Paper and MythicMobs (M07.1, M17): builds {@code CombatFacts} from what the members could
 * perceive, with no mutable state of its own (rule A3) and no name, UUID or text in the result (rule I6). The
 * participants are the living entities the participant policy of each member's profile admits (M17.2); with the
 * default policy they are the players of 0.0.1. Server thread only (rule T1); the result is immutable and may cross
 * threads (rule A4).
 *
 * <p>Cost per capture (rule A5): at most {@link CombatFacts#MAX_MOBS} members are read, one bounded {@code
 * getNearbyPlayers} query per member within its perception radius (or, only when its policy admits entities and
 * perceives non-targets, one {@code getNearbyLivingEntities} query and at most {@code maxCandidatesPerMember} extra
 * entities), at most {@link CombatFacts#MAX_PARTICIPANTS} participants read, one line of sight per mob-participant pair
 * actually used, and a new path only for the mobs the engine allows. A member that is dead, invalid, unbound or has no
 * valid maximum health is left out without an exception; one in another world is reported through {@code sightings} and
 * left out. Every method throws {@link IllegalStateException} off the server thread, which makes rule T1 checkable
 * (C7).
 */
public final class CombatCapture implements CombatReader {
    /** Radius of the party count of M22.1, squared: players within 48 blocks of the member. */
    static final double PARTY_RADIUS_SQUARED = 48.0 * 48.0;

    private final MemberSource members;
    private final EntityReads reads;
    private final Function<UUID, Optional<Entity>> entities;
    private final NativeContextEvaluator contexts;
    private final BiPredicate<SkillCondition, LivingEntity> conditionHolds;
    private final ServerClock clock;
    private final BooleanSupplier serverThread;
    private final double searchRadiusBlocks;
    private final ParticipantLookup lookup;
    private final IncomingProjectiles incoming = new IncomingProjectiles();
    private akyrion.thepredict.adapter.server.zone.ZoneRegistry zones;
    private akyrion.thepredict.adapter.server.zone.ZoneSense sense;
    private akyrion.thepredict.adapter.server.patterns.KinematicSampler kinematics;
    private double strafeBlocks = akyrion.thepredict.domain.PatternSettings.DEFAULT.strafeBlocks();
    private int maxFactsPerParticipant = akyrion.thepredict.domain.PatternSettings.DEFAULT.maxFactsPerParticipant();
    private int maxFactsPerRequest = akyrion.thepredict.domain.PatternSettings.DEFAULT.maxFactsPerRequest();
    private Function<MobKey, java.util.Set<akyrion.thepredict.domain.EnemyPattern>> patternFacts = key -> java.util.Set.of();

    /**
     * Creates the capture.
     *
     * @param members MythicMobs reads of the member mobs
     * @param reads Paper reads of attributes, items, paths and combat trackers
     * @param entities finds a loaded entity by UUID ({@code Bukkit::getEntity}), for {@link #inCombat}
     * @param contexts evaluator of the profile contexts
     * @param conditionHolds evaluates one native condition on the mob ({@code SkillCondition.evaluateEntity})
     * @param clock server clock, for the capture tick and time
     * @param serverThread {@code Bukkit::isPrimaryThread}
     * @param searchRadiusBlocks {@code decision.searchRadiusBlocks}, the global cap on the perception radius
     * @param lookup native identities read by the participant policy (M17.2)
     */
    public CombatCapture(MemberSource members, EntityReads reads, Function<UUID, Optional<Entity>> entities,
                         NativeContextEvaluator contexts, BiPredicate<SkillCondition, LivingEntity> conditionHolds,
                         ServerClock clock, BooleanSupplier serverThread, double searchRadiusBlocks,
                         ParticipantLookup lookup) {
        this.members = Objects.requireNonNull(members, "members");
        this.reads = Objects.requireNonNull(reads, "reads");
        this.entities = Objects.requireNonNull(entities, "entities");
        this.contexts = Objects.requireNonNull(contexts, "contexts");
        this.conditionHolds = Objects.requireNonNull(conditionHolds, "conditionHolds");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.serverThread = Objects.requireNonNull(serverThread, "serverThread");
        if (!(searchRadiusBlocks > 0.0 && Double.isFinite(searchRadiusBlocks))) {
            throw new IllegalArgumentException("searchRadiusBlocks must be positive and finite, was " + searchRadiusBlocks);
        }
        this.searchRadiusBlocks = searchRadiusBlocks;
        this.lookup = Objects.requireNonNull(lookup, "lookup");
    }

    /**
     * Shots the launch listener and this reader share.
     *
     * @return the shared ring
     */
    public IncomingProjectiles incoming() {
        return incoming;
    }

    /**
     * Attaches the zone used by {@link #applyZone} and by the retreat fact.
     *
     * @param zones the registry
     */
    public void useZone(akyrion.thepredict.adapter.server.zone.ZoneRegistry zones) {
        this.zones = Objects.requireNonNull(zones, "zones");
        this.sense = new akyrion.thepredict.adapter.server.zone.ZoneSense(zones, entities);
    }

    /**
     * Patterns already filtered by mode. Off and observe pass an empty set.
     *
     * @param patternFacts patterns of one member
     */
    public void usePatternFacts(Function<MobKey, java.util.Set<akyrion.thepredict.domain.EnemyPattern>> patternFacts) {
        this.patternFacts = Objects.requireNonNull(patternFacts, "patternFacts");
    }

    /**
     * Patterns of one participant that travel to Jev, {@code patterns.maxFactsPerParticipant} (SP-20). The delivered
     * default applies until the root passes the configured value.
     *
     * @param cap facts per participant, {@code 0} to {@code 3}
     */
    public void useFactCap(int cap) {
        this.maxFactsPerParticipant = cap;
    }

    /**
     * Pattern facts of one whole request, {@code patterns.maxFactsPerRequest} (M26.7, DOC-09). The delivered default
     * applies until the root passes the configured value.
     *
     * @param cap facts per request, {@code 0} to {@code 6}
     */
    public void useRequestFactCap(int cap) {
        this.maxFactsPerRequest = cap;
    }

    /**
     * Attaches the kinematic book. The capture does not own the stamp.
     *
     * @param book the engine book
     * @param strafe minimum movement that counts as a strafe
     */
    public void useKinematics(akyrion.thepredict.engine.KinematicBook book, double strafe) {
        this.kinematics = new akyrion.thepredict.adapter.server.patterns.KinematicSampler(
                Objects.requireNonNull(book, "book"));
        this.strafeBlocks = strafe;
    }

    @Override
    public long enemyKinematics(UUID participant, long previousStamp, UUID witness) {
        requireServerThread();
        if (kinematics == null) {
            return 0L;
        }
        var found = entities.apply(participant).orElse(null);
        if (!(found instanceof org.bukkit.entity.Player player)) {
            return previousStamp;
        }
        var seen = entities.apply(witness).orElse(null);
        var present = seen instanceof org.bukkit.entity.LivingEntity && seen.getWorld() == player.getWorld();
        var reader = new akyrion.thepredict.adapter.server.patterns.EnemyKinematicsReader(player.getX(), player.getZ(),
                player.getYaw(), player.getFallDistance(), player.isSprinting(), player.isInvisible(),
                present ? seen.getX() : 0.0, present ? seen.getZ() : 0.0, present, strafeBlocks);
        return kinematics.sample(participant, reader, (int) clock.currentTick());
    }

    @Override
    public long applyZone(akyrion.thepredict.engine.MobSession session, long signals) {
        requireServerThread();
        if (sense == null) {
            return signals;
        }
        return sense.apply(session, signals);
    }

    @Override
    public long tickSignals(MobKey mob, java.util.UUID expectedTarget) {
        requireServerThread();
        return TickSignalReader.read(entities, incoming, clock.currentTick(), mob, expectedTarget);
    }

    @Override
    public Optional<String> authorStance(MobKey mob) {
        requireServerThread();
        return members.authorStance(mob.entityId());
    }

    @Override
    public boolean playerTarget(java.util.UUID entityId) {
        requireServerThread();
        return entities.apply(entityId).orElse(null) instanceof org.bukkit.entity.Player;
    }

    @Override
    public Optional<CombatFacts> capture(EncounterId encounter, List<MobKey> keys, CaptureContext context) {
        Objects.requireNonNull(encounter, "encounter");
        Objects.requireNonNull(context, "context");
        requireServerThread();
        var memberIds = new HashSet<UUID>(Math.min(keys.size(), CombatFacts.MAX_MOBS) * 2);
        keys.forEach(key -> memberIds.add(key.entityId()));
        var live = new ArrayList<LiveMember>(Math.min(keys.size(), CombatFacts.MAX_MOBS));
        for (var key : keys) {
            if (live.size() >= CombatFacts.MAX_MOBS) {
                break;
            }
            resolve(key, context, memberIds).ifPresent(live::add);
        }
        if (live.isEmpty()) {
            return Optional.empty();
        }
        var assembler = new FactsAssembler(members, reads, contexts, conditionHolds, context, live, searchRadiusBlocks,
                new FactsAssembler.Participants(memberIds, lookup));
        assembler.usePatternFacts(patternFacts, maxFactsPerParticipant, maxFactsPerRequest);
        if (kinematics != null) {
            assembler.useKinematics(kinematics, (int) clock.currentTick(), strafeBlocks);
        }
        if (zones != null) {
            assembler.noRetreat(entity -> akyrion.thepredict.adapter.server.zone.ZoneSense.retreatMissing(zones,
                    encounter, entity));
        }
        var built = assembler.facts(encounter, clock.currentTick(), clock.nanoTime());
        return Optional.of(built.withGroup(relationsOf(live, memberIds), built.brief()));
    }

    @Override
    public akyrion.thepredict.domain.GroupRelations relations(MobKey mob, Set<UUID> inside) {
        requireServerThread();
        return members.relations(mob.entityId(), inside);
    }

    @Override
    public int threatTableGroup(MobKey mob) {
        requireServerThread();
        return members.threatTableGroup(mob.entityId());
    }

    @Override
    public boolean inheritsThreatTable(MobKey mob) {
        requireServerThread();
        return members.inheritsThreatTable(mob.entityId());
    }

    private GroupRelations relationsOf(List<LiveMember> live, Set<UUID> inside) {
        Optional<UUID> parent = Optional.empty();
        Optional<UUID> owner = Optional.empty();
        var children = false;
        for (var member : live) {
            var relations = members.relations(member.key().entityId(), inside);
            if (parent.isEmpty()) {
                parent = relations.parent();
            }
            if (owner.isEmpty()) {
                owner = relations.owner();
            }
            children = children || relations.hasChildren();
        }
        return new GroupRelations(parent, owner, children);
    }

    @Override
    public Optional<MobProbe> probe(MobKey key) {
        requireServerThread();
        var member = members.member(key.entityId());
        if (member.isEmpty()) {
            return Optional.empty();
        }
        var entity = member.get().entity();
        var maxHealth = reads.maxHealth(entity);
        if (!Bands.isValidMaxHealth(maxHealth)) {
            return Optional.empty();
        }
        var profile = member.get().profile().profile();
        var health = Bands.health(entity.getHealth(), entity.getAbsorptionAmount(), maxHealth, profile.health());
        var worldId = entity.getWorld().getUID();
        var policy = member.get().profile().participants();
        var target = members.target(key.entityId()).filter(found -> policy.admits(found, entity, Set.of(), lookup));
        if (target.isEmpty()) {
            return Optional.of(new MobProbe(key, worldId, health, Optional.empty(), Optional.empty()));
        }
        var distanceSquared = entity.getLocation().distanceSquared(target.get().getLocation());
        return Optional.of(new MobProbe(key, worldId, health, Optional.of(target.get().getUniqueId()),
                Optional.of(Bands.distanceSquared(distanceSquared, profile.movement()))));
    }

    @Override
    public boolean withinFollowRange(MobKey mob, MobKey member) {
        requireServerThread();
        var self = members.member(mob.entityId());
        var other = members.member(member.entityId());
        if (self.isEmpty() || other.isEmpty()) {
            return false;
        }
        var entity = self.get().entity();
        var peer = other.get().entity();
        if (!entity.getWorld().getUID().equals(peer.getWorld().getUID())) {
            return false;
        }
        var radius = Perception.radiusBlocks(reads.followRangeBlocks(entity), searchRadiusBlocks);
        return Perception.withinRadius(entity.getLocation().distanceSquared(peer.getLocation()), radius)
                && !rivals(self.get(), other.get());
    }

    /**
     * Two members are rivals when the policy of one admits the other as a participant (which already excludes the same
     * side, M17.6); with the default policy no mob is ever admitted, so nothing more is read.
     */
    private boolean rivals(MemberSource.Member first, MemberSource.Member second) {
        return first.profile().participants().admits(second.entity(), first.entity(), Set.of(), lookup)
                || second.profile().participants().admits(first.entity(), second.entity(), Set.of(), lookup);
    }

    @Override
    public boolean inCombat(UUID entityId) {
        requireServerThread();
        var entity = entities.apply(entityId);
        return entity.isPresent() && entity.get() instanceof LivingEntity living
                && (reads.trackerInCombat(living) || members.threatInCombat(entityId));
    }

    @Override
    public boolean isActive(UUID entityId) {
        requireServerThread();
        return members.isActive(entityId);
    }

    @Override
    public double threat(MobKey mob, UUID participant) {
        requireServerThread();
        return members.threat(mob.entityId(), participant);
    }

    @Override
    public Optional<ThreatTableState> threatTable(MobKey key) {
        requireServerThread();
        return members.threatTable(key.entityId());
    }

    private void requireServerThread() {
        if (!serverThread.getAsBoolean()) {
            throw new IllegalStateException("CombatCapture must run on the server thread");
        }
    }

    /**
     * Reads one member, reports its sighting, and keeps it only in the encounter's world with a valid health. Its
     * target counts only when the policy of its profile admits it; a member of the encounter never does (M17.5, M17.6).
     */
    private Optional<LiveMember> resolve(MobKey key, CaptureContext context, Set<UUID> memberIds) {
        var member = members.member(key.entityId());
        if (member.isEmpty()) {
            return Optional.empty();
        }
        var entity = member.get().entity();
        var worldId = entity.getWorld().getUID();
        var policy = member.get().profile().participants();
        var target = members.target(key.entityId()).filter(found -> policy.admits(found, entity, memberIds, lookup));
        var distance = target.map(found -> entity.getLocation().distance(found.getLocation()));
        context.sightings().accept(new MobSighting(key, worldId, target.map(Entity::getUniqueId),
                distance.map(OptionalDouble::of).orElseGet(OptionalDouble::empty)));
        var maxHealth = reads.maxHealth(entity);
        if (!worldId.equals(context.worldId()) || !Bands.isValidMaxHealth(maxHealth)) {
            return Optional.empty();
        }
        return Optional.of(new LiveMember(key, entity, member.get().profile(), maxHealth, target, distance.orElse(0.0)));
    }

    @Override
    public OptionalDouble targetSecondsOverride(MobKey mob) {
        Objects.requireNonNull(mob, "mob");
        requireServerThread();
        return members.numberVariable(mob.entityId(), "predictor_target_seconds");
    }

    @Override
    public Optional<DifficultyPreset> difficultyOverride(MobKey mob) {
        Objects.requireNonNull(mob, "mob");
        requireServerThread();
        var text = members.textVariable(mob.entityId(), "predictor_difficulty");
        if (text.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(DifficultyPreset.parse(text.get()));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<DifficultyPreset> declaredPreset(MobKey mob) {
        Objects.requireNonNull(mob, "mob");
        requireServerThread();
        return members.member(mob.entityId()).flatMap(member -> member.profile().declaredPreset());
    }

    @Override
    public boolean declaredEntropy(MobKey mob) {
        Objects.requireNonNull(mob, "mob");
        requireServerThread();
        return members.member(mob.entityId()).map(member -> member.profile().declaredEntropy()).orElse(false);
    }

    @Override
    public boolean declaredTraining(MobKey mob) {
        Objects.requireNonNull(mob, "mob");
        requireServerThread();
        return members.member(mob.entityId()).map(member -> member.profile().declaredTraining()).orElse(false);
    }

    @Override
    public String worldDifficulty(MobKey mob) {
        Objects.requireNonNull(mob, "mob");
        requireServerThread();
        return members.member(mob.entityId()).map(member -> member.entity().getWorld().getDifficulty().name())
                .orElse("NORMAL");
    }

    @Override
    public boolean naturalRegeneration(MobKey mob) {
        Objects.requireNonNull(mob, "mob");
        requireServerThread();
        return members.member(mob.entityId()).map(member -> Boolean.TRUE.equals(
                member.entity().getWorld().getGameRuleValue(GameRules.NATURAL_HEALTH_REGENERATION))).orElse(true);
    }

    @Override
    public String worldName(MobKey mob) {
        Objects.requireNonNull(mob, "mob");
        requireServerThread();
        return members.member(mob.entityId()).map(member -> member.entity().getWorld().getName()).orElse("");
    }

    @Override
    public int partySize(MobKey mob) {
        Objects.requireNonNull(mob, "mob");
        requireServerThread();
        var member = members.member(mob.entityId()).orElse(null);
        if (member == null) {
            return 1;
        }
        var origin = member.entity().getLocation();
        var count = 0;
        for (var player : member.entity().getWorld().getPlayers()) {
            if (countsForParty(player) && player.getLocation().distanceSquared(origin) <= PARTY_RADIUS_SQUARED) {
                count++;
            }
        }
        return Math.max(1, count);
    }

    /**
     * Whether a player counts in the party of M22.1: alive and in a game mode a mob may fight (survival or adventure,
     * the modes {@link ParticipantPolicy#DEFAULT} admits). A spectator or a creative administrator watching a boss
     * does not raise the difficulty (SP-26).
     *
     * @param player a player of the mob's world
     * @return whether it is counted
     */
    static boolean countsForParty(org.bukkit.entity.Player player) {
        if (player.isDead() || !player.isValid()) {
            return false;
        }
        var mode = player.getGameMode();
        return mode == org.bukkit.GameMode.SURVIVAL || mode == org.bukkit.GameMode.ADVENTURE;
    }
}
