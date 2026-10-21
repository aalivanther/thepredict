package akyrion.thepredict.adapter.server.context;

import akyrion.thepredict.domain.CaptureContext;
import akyrion.thepredict.domain.ChoiceId;
import akyrion.thepredict.domain.CombatFacts;
import akyrion.thepredict.domain.ContextId;
import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.EventType;
import akyrion.thepredict.domain.EnemyPattern;
import akyrion.thepredict.domain.MobCue;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.MobFacts;
import akyrion.thepredict.domain.ZoneFact;
import akyrion.thepredict.domain.ParticipantAlias;
import akyrion.thepredict.domain.ParticipantFacts;
import akyrion.thepredict.domain.ParticipantKind;
import akyrion.thepredict.domain.Situation;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TargetRelation;
import io.lumine.mythic.core.skills.SkillCondition;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Builds the facts of one capture from its live members (M07.1–M07.8, M17.3). One instance per capture, on the server
 * thread; it keeps only per-capture tables (perceived participants, readings, aliases and lines of sight) and is
 * dropped with the capture.
 *
 * <p>Only the living entities a member's participant policy admits within its perception radius are participants
 * (M07.2, M17.2). A target within its mob's radius is chosen first, because {@code CombatFacts} requires every target
 * to be a participant; a target beyond every radius is not perceived, so its mob has no {@code TargetRelation} (the
 * engine still learns it from the sighting). The other participants come from {@code getNearbyPlayers}, as in 0.0.1,
 * unless the member's policy admits entities and perceives non-targets: then one {@code getNearbyLivingEntities} query
 * with the cheap test of the policy replaces it, and at most {@code maxCandidatesPerMember} of the nearest non-player
 * entities are kept ({@link NearestK}, without sorting all).
 *
 * <p>Participants get their alias of their kind through {@code CaptureContext.aliasFor} only once chosen, so the
 * engine indexes only what the mobs perceive; an entity also gets its species, the key of its {@code EntityType}
 * (M17.1). The health band of a participant uses the thresholds of the first member's profile, since the bands must be
 * one per participant.
 */
final class FactsAssembler {
    /** Ticks during which a {@code SHIELD_DISABLED} event bridges the cooldown the event sets after it fires. */
    static final long SHIELD_DISABLE_EVENT_TICKS = 20;

    private final MemberSource members;
    private final EntityReads reads;
    private final NativeContextEvaluator contexts;
    private final BiPredicate<SkillCondition, LivingEntity> conditionHolds;
    private final CaptureContext context;
    private final List<LiveMember> live;
    private final double searchRadiusBlocks;
    private final Perception perception = new Perception();
    private final TargetRelationReader relations;
    private final Participants participation;
    private Function<MobKey, Set<EnemyPattern>> patternFacts = key -> Set.of();
    private final Map<UUID, Double> distanceSquared = new LinkedHashMap<>();
    private final Map<UUID, LivingEntity> entities = new HashMap<>();
    private final Map<UUID, LiveMember> nearest = new HashMap<>();
    private final Map<UUID, ParticipantReading> readings = new HashMap<>();
    private final Map<UUID, ParticipantAlias> aliases = new HashMap<>();
    private final Map<UUID, Location> seenAt = new HashMap<>();
    private final Map<UUID, Location> positions = new HashMap<>();
    private Predicate<LivingEntity> noRetreat = entity -> false;
    private akyrion.thepredict.adapter.server.patterns.KinematicSampler sampler;
    private int sampleTick;
    private double strafeBlocks = akyrion.thepredict.domain.PatternSettings.DEFAULT.strafeBlocks();
    private int maxFactsPerParticipant = akyrion.thepredict.domain.PatternSettings.DEFAULT.maxFactsPerParticipant();
    private int maxFactsPerRequest = akyrion.thepredict.domain.PatternSettings.DEFAULT.maxFactsPerRequest();

    FactsAssembler(MemberSource members, EntityReads reads, NativeContextEvaluator contexts,
                   BiPredicate<SkillCondition, LivingEntity> conditionHolds, CaptureContext context,
                   List<LiveMember> live, double searchRadiusBlocks, Participants participation) {
        this.participation = participation;
        this.members = members;
        this.reads = reads;
        this.contexts = contexts;
        this.conditionHolds = conditionHolds;
        this.context = context;
        this.live = live;
        this.searchRadiusBlocks = searchRadiusBlocks;
        this.relations = new TargetRelationReader(reads);
    }

    /**
     * Q2. True only when the retreat query returned none.
     *
     * @param noRetreat the query
     */
    void noRetreat(Predicate<LivingEntity> noRetreat) {
        this.noRetreat = Objects.requireNonNull(noRetreat, "noRetreat");
    }

    /**
     * Facts admitted for a member. Empty keeps the body of a capture that has no pattern mode.
     *
     * @param patternFacts patterns of one member, already filtered by mode
     */
    void usePatternFacts(Function<MobKey, Set<EnemyPattern>> patternFacts) {
        this.patternFacts = Objects.requireNonNull(patternFacts, "patternFacts");
    }

    /**
     * Facts admitted for a member, cut at {@code patterns.maxFactsPerParticipant} (SP-20).
     *
     * @param patternFacts patterns of one member, already filtered by mode
     * @param cap facts per participant
     */
    void usePatternFacts(Function<MobKey, Set<EnemyPattern>> patternFacts, int cap) {
        usePatternFacts(patternFacts);
        this.maxFactsPerParticipant = cap;
    }

    /**
     * Facts admitted for a member, cut at {@code patterns.maxFactsPerParticipant} and, over the whole request, at
     * {@code patterns.maxFactsPerRequest} (M26.7, DOC-09).
     *
     * @param patternFacts patterns of one member, already filtered by mode
     * @param cap facts per participant
     * @param requestCap facts of the whole request
     */
    void usePatternFacts(Function<MobKey, Set<EnemyPattern>> patternFacts, int cap, int requestCap) {
        usePatternFacts(patternFacts, cap);
        this.maxFactsPerRequest = requestCap;
    }

    /**
     * Attaches the kinematic sampler over the engine book. Absent, the trend stays on a word of zero.
     *
     * @param sampler the sampler the capture owns
     * @param tick the capture tick
     * @param strafe minimum movement that counts
     */
    void useKinematics(akyrion.thepredict.adapter.server.patterns.KinematicSampler sampler, int tick, double strafe) {
        this.sampler = Objects.requireNonNull(sampler, "sampler");
        this.sampleTick = tick;
        this.strafeBlocks = strafe;
    }

    /** Builds the facts; the live list is never empty. */
    CombatFacts facts(EncounterId encounter, long tick, long nanos) {
        var chosen = choose();
        var participants = participants(chosen, tick);
        var mobs = new ArrayList<MobFacts>(live.size());
        live.forEach(member -> mobs.add(mobFacts(member)));
        return new CombatFacts(encounter, tick, nanos, mobs, participants, context.recentEvents(), combatSeconds());
    }

    private List<UUID> choose() {
        var targets = new ArrayList<UUID>();
        for (var member : live) {
            var radius = radius(member);
            member.target().filter(target -> member.targetDistanceBlocks() <= radius).ifPresent(target -> {
                targets.add(target.getUniqueId());
                offer(target, member.targetDistanceBlocks() * member.targetDistanceBlocks(), member);
            });
        }
        live.forEach(this::perceive);
        var chosen = new ArrayList<UUID>();
        for (var participantId : ParticipantSelection.select(targets, distanceSquared, CombatFacts.MAX_PARTICIPANTS)) {
            var entity = entities.get(participantId);
            var reading = reads.participant(entity);
            if (Bands.isValidMaxHealth(reading.maxHealth())) {
                readings.put(participantId, reading);
                aliases.put(participantId, context.aliasFor().apply(participantId, ParticipantPolicy.kind(entity)));
                chosen.add(participantId);
            }
        }
        return chosen;
    }

    private double radius(LiveMember member) {
        return Perception.radiusBlocks(reads.followRangeBlocks(member.entity()), searchRadiusBlocks);
    }

    private void perceive(LiveMember member) {
        var radius = radius(member);
        var mob = member.entity();
        var at = mob.getLocation();
        var policy = member.bound().participants();
        if (policy.perceivesEntities()) {
            perceiveEntities(member, policy, at, radius);
            return;
        }
        for (var player : mob.getWorld().getNearbyPlayers(at, radius)) {
            if (policy.admits(player, mob, participation.memberIds(), participation.lookup())) {
                seen(player, player.getLocation(), at, radius, member);
            }
        }
    }

    /**
     * One {@code getNearbyLivingEntities} query with the cheap test of the policy instead of the players' query: the
     * admitted players are all offered, and of the other entities only the {@code maxCandidatesPerMember} nearest.
     */
    private void perceiveEntities(LiveMember member, ParticipantPolicy policy, Location at, double radius) {
        var mob = member.entity();
        var candidates = new HashMap<UUID, LivingEntity>();
        var squared = new HashMap<UUID, Double>();
        for (var entity : mob.getWorld().getNearbyLivingEntities(at, radius, policy::admitsCheap)) {
            if (!policy.admits(entity, mob, participation.memberIds(), participation.lookup())) {
                continue;
            }
            var location = entity.getLocation();
            if (entity instanceof Player) {
                seen(entity, location, at, radius, member);
            } else if (Perception.withinRadius(location.distanceSquared(at), radius)) {
                candidates.put(entity.getUniqueId(), entity);
                squared.put(entity.getUniqueId(), location.distanceSquared(at));
            }
        }
        for (var id : NearestK.select(new ArrayList<>(candidates.keySet()), policy.maxCandidatesPerMember(),
                candidate -> true, squared::get)) {
            seen(candidates.get(id), candidates.get(id).getLocation(), at, radius, member);
        }
    }

    /** Offers a participant the member perceives within its radius, and keeps where it was seen. */
    private void seen(LivingEntity entity, Location location, Location at, double radius, LiveMember member) {
        var squared = location.distanceSquared(at);
        if (Perception.withinRadius(squared, radius)) {
            seenAt.put(entity.getUniqueId(), location);
            offer(entity, squared, member);
        }
    }

    private void offer(LivingEntity entity, double squared, LiveMember member) {
        var participantId = entity.getUniqueId();
        var known = distanceSquared.get(participantId);
        if (known == null || squared < known) {
            distanceSquared.put(participantId, squared);
            entities.put(participantId, entity);
            nearest.put(participantId, member);
        }
    }

    private List<ParticipantFacts> participants(List<UUID> chosen, long tick) {
        var ranks = ParticipantSelection.ranks(chosen, this::totalThreat, distanceSquared::get);
        var thresholds = live.getFirst().bound().profile().health();
        var facts = new ArrayList<ParticipantFacts>(chosen.size());
        var left = maxFactsPerRequest;
        for (var participantId : chosen) {
            var entity = entities.get(participantId);
            var reading = readings.get(participantId);
            var alias = aliases.get(participantId);
            var known = seenAt.get(participantId);
            positions.put(participantId, known != null ? known : entity.getLocation());
            var seen = perception.lineOfSight(nearest.get(participantId).entity(), entity);
            facts.add(new ParticipantFacts(alias,
                    Bands.health(reading.health(), reading.absorption(), reading.maxHealth(), thresholds),
                    Bands.armor(reading.armorPoints(), reading.toughnessPoints()),
                    TargetCueReader.cues(reading, seen, recentShieldDisable(alias, tick)), ranks.get(participantId),
                    alias.kind() == ParticipantKind.ENTITY ? Optional.of(species(entity)) : Optional.empty(),
                    patternsOf(participantId, Math.min(maxFactsPerParticipant, left))));
            left -= facts.getLast().patterns().size();
        }
        return facts;
    }

    /**
     * The patterns of one participant that travel to Jev: the union over the members that target it, cut at
     * {@code cap} in the declaration order of {@link EnemyPattern}, so the same admitted set gives the same facts on
     * every start (SP-20). The cap is the smaller of the facts per participant and what the request has left.
     */
    private Set<EnemyPattern> patternsOf(UUID participantId, int cap) {
        var admitted = EnumSet.noneOf(EnemyPattern.class);
        for (var member : live) {
            if (member.target().isEmpty() || !participantId.equals(member.target().get().getUniqueId())) {
                continue;
            }
            admitted.addAll(patternFacts.apply(member.key()));
        }
        if (admitted.isEmpty() || cap <= 0) {
            return Set.of();
        }
        var found = EnumSet.noneOf(EnemyPattern.class);
        for (var pattern : EnemyPattern.values()) {
            if (found.size() >= cap) {
                break;
            }
            if (admitted.contains(pattern)) {
                found.add(pattern);
            }
        }
        return Set.copyOf(found);
    }

    /**
     * Species of an entity for Jev: the key of its {@code EntityType} without namespace, never a custom name, a
     * {@code Display} or a MythicMobs ID (rule I6, M17.1).
     *
     * @param entity a participant that is not a player
     * @return for example {@code zombie} or {@code wither_skeleton}
     */
    static String species(LivingEntity entity) {
        return entity.getType().getKey().getKey();
    }

    private MobFacts mobFacts(LiveMember member) {
        var entity = member.entity();
        var profile = member.bound().profile();
        var health = Bands.health(entity.getHealth(), entity.getAbsorptionAmount(), member.maxHealth(), profile.health());
        var relation = member.target().filter(target -> aliases.containsKey(target.getUniqueId()))
                .map(target -> relation(member, target));
        var shortcuts = new ShortcutFacts(health, relation.map(TargetRelation::distance));
        var active = contexts.active(profile, id -> conditionsOf(member, id),
                condition -> conditionHolds.test(condition, entity), shortcuts);
        var outOfReach = relation.isPresent() ? outOfReach(member) : Set.<ChoiceId>of();
        var flankable = relation.map(TargetRelation::flankable).orElse(false);
        Predicate<ChoiceId> offCooldown = choice -> offCooldown(member, choice);
        var eligible = ReadyTactics.eligible(profile, active, member.bound().brainSpecies(), relation.isPresent(),
                flankable, offCooldown, outOfReach);
        var hypothetical = relation.isPresent() ? ReadyTactics.contingencyEligible(profile, active,
                member.bound().brainSpecies(), flankable, offCooldown, outOfReach, shortcuts)
                : Map.<Situation, Set<TacticId>>of();
        var whenReady = ReadyTactics.eligible(profile, active, member.bound().brainSpecies(), relation.isPresent(),
                flankable, choice -> member.bound().skills().get(choice) != null, outOfReach);
        var horizon = profile.timing().readyHorizonTicks();
        var soon = horizon == 0 ? eligible : ReadyTactics.eligible(profile, active, member.bound().brainSpecies(),
                relation.isPresent(), flankable, choice -> withinHorizon(member, choice, horizon), outOfReach);
        var built = new MobFacts(member.key(), health, relation, active, eligible, mobCues(member, relation.isPresent()),
                outOfReach, hypothetical, whenReady, soon);
        if (!noRetreat.test(entity)) {
            return built;
        }
        return built.withZone(Set.of(), true, 1L << ZoneFact.NO_SAFE_RETREAT.ordinal());
    }

    /** The remaining cooldown of a skill whose timer is unknown (C4: no {@code Optional} parameter, SP-21). */
    static final long UNKNOWN_MILLIS = -1L;

    /**
     * Whether a MythicMobs skill is ready inside the question horizon. A skill off cooldown counts as {@code 0}.
     * An unknown timer ({@link #UNKNOWN_MILLIS}) or {@link Long#MAX_VALUE} stays outside the horizon.
     */
    static boolean withinHorizon(boolean offCooldown, long remainingMillis, int horizon) {
        if (offCooldown || horizon < 0) {
            return offCooldown;
        }
        if (remainingMillis == UNKNOWN_MILLIS || remainingMillis == Long.MAX_VALUE) {
            return false;
        }
        var ticks = remainingMillis <= 0 ? 0 : (remainingMillis + 49) / 50;
        return ticks <= horizon;
    }

    private boolean withinHorizon(LiveMember member, ChoiceId choice, int horizon) {
        var skill = member.bound().skills().get(choice);
        if (skill == null) {
            return false;
        }
        var mobId = member.key().entityId();
        return withinHorizon(!members.onCooldown(mobId, skill),
                members.remainingMillis(mobId, skill).orElse(UNKNOWN_MILLIS), horizon);
    }

    /** Choices with a declared reach that does not get to the perceived target; boxes are read only when needed. */
    private Set<ChoiceId> outOfReach(LiveMember member) {
        var profile = member.bound().profile();
        if (!ReadyTactics.measuresReach(profile)) {
            return Set.of();
        }
        var distance = member.targetDistanceBlocks();
        return ReadyTactics.outOfReach(profile, member.entity().getBoundingBox(),
                member.target().orElseThrow().getBoundingBox(), distance * distance);
    }

    /** The member is a witness only with a line of sight (A14); the pair is memoized for this capture. */
    private long word(LivingEntity target, LivingEntity witness) {
        if (sampler == null || !(target instanceof Player player)) {
            return 0L;
        }
        var reader = new akyrion.thepredict.adapter.server.patterns.EnemyKinematicsReader(player.getX(), player.getZ(),
                player.getYaw(), player.getFallDistance(), player.isSprinting(), player.isInvisible(),
                witness.getX(), witness.getZ(), perception.lineOfSight(witness, player), strafeBlocks);
        return sampler.sample(player.getUniqueId(), reader, sampleTick);
    }

    private TargetRelation relation(LiveMember member, LivingEntity target) {
        var targetId = target.getUniqueId();
        return relations.read(member.key(), member.entity(), target, aliases.get(targetId),
                member.targetDistanceBlocks(), readings.get(targetId), member.bound().profile().movement(),
                perception, context, TargetRelationReader.grouped(targetId, positions),
                word(target, member.entity()));
    }

    private Set<MobCue> mobCues(LiveMember member, boolean perceivedTarget) {
        var entity = member.entity();
        var cues = EnumSet.noneOf(MobCue.class);
        if (entity.isInWater()) {
            cues.add(MobCue.IN_WATER);
        }
        if (entity.isInLava()) {
            cues.add(MobCue.IN_LAVA);
        }
        if (entity.isClimbing()) {
            cues.add(MobCue.CLIMBING);
        }
        if (perceivedTarget && !perception.lineOfSight(entity, member.target().orElseThrow())) {
            cues.add(MobCue.NO_LINE_OF_SIGHT);
        }
        return cues;
    }

    private List<SkillCondition> conditionsOf(LiveMember member, ContextId id) {
        var conditions = member.bound().conditions().get(id);
        return conditions == null ? List.of() : conditions.conditions();
    }

    private boolean offCooldown(LiveMember member, ChoiceId choice) {
        var skill = member.bound().skills().get(choice);
        return skill != null && !members.onCooldown(member.key().entityId(), skill);
    }

    private double totalThreat(UUID participantId) {
        var total = 0.0;
        for (var member : live) {
            total += members.threat(member.key().entityId(), participantId);
        }
        return total;
    }

    private boolean recentShieldDisable(ParticipantAlias alias, long tick) {
        for (var event : context.recentEvents()) {
            if (event.type() == EventType.SHIELD_DISABLED && event.participant().isPresent()
                    && event.participant().get().equals(alias)
                    && tick - event.tick() <= SHIELD_DISABLE_EVENT_TICKS) {
                return true;
            }
        }
        return false;
    }

    /**
     * What decides who takes part in one capture besides each member's policy.
     *
     * @param memberIds UUIDs of every member of the encounter, which never take part (rule I13)
     * @param lookup native identities read by the policies
     */
    record Participants(Set<UUID> memberIds, ParticipantLookup lookup) {
        Participants {
            memberIds = Set.copyOf(memberIds);
            Objects.requireNonNull(lookup, "lookup");
        }
    }

    private int combatSeconds() {
        var longest = 0;
        for (var member : live) {
            longest = Math.max(longest, reads.combatDurationTicks(member.entity()));
        }
        return CombatSummaryReader.combatSeconds(longest);
    }
}
