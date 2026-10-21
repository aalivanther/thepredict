package akyrion.thepredict.engine.duel;

import akyrion.thepredict.domain.ArmorBand;
import akyrion.thepredict.domain.CaptureContext;
import akyrion.thepredict.domain.ChoiceId;
import akyrion.thepredict.domain.CombatFacts;
import akyrion.thepredict.domain.Delivery;
import akyrion.thepredict.domain.Eligibility;
import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.MobFacts;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.MobProbe;
import akyrion.thepredict.domain.MobSighting;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.ParticipantFacts;
import akyrion.thepredict.domain.ParticipantKind;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.domain.TargetRelation;
import akyrion.thepredict.domain.ThreatTableState;
import akyrion.thepredict.domain.Trend;
import akyrion.thepredict.engine.CombatReader;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;

/**
 * The combat reader of the simulator over the {@link DuelWorld}: bands, never grouped, the rival as participant
 * {@code entity_1} of species {@code zombie}. With {@link TickRules#LEGACY} the trend is always steady, the rival
 * always visible and never out of reach, as in M18.7. With {@link TickRules#serverPorts} it reads as
 * {@code TargetRelationReader} does on a server (SIM-11): the trend of an entity from its velocity projected on the
 * line to the mob, corrected by a change of more than 1 block of the distance at the previous capture (the rule of
 * {@code TargetTrend}, copied here because the adapter is package-private); the line of sight through the pillars; and
 * the reach of the rival, the vanilla melee check of a zombie or the melee reach of a bound rival.
 */
final class DuelReader implements CombatReader {
    /** Horizontal speed above which an entity counts as moving, {@code TargetTrend.MOVING_BLOCKS_PER_TICK}. */
    static final double MOVING_BLOCKS_PER_TICK = 0.05;
    /** Change of distance that overrides the velocity, {@code TargetTrend.DISTANCE_CHANGE_BLOCKS}. */
    static final double DISTANCE_CHANGE_BLOCKS = 1.0;

    private final DuelEngine engine;

    DuelReader(DuelEngine engine) {
        this.engine = engine;
    }

    @Override
    public Optional<CombatFacts> capture(EncounterId encounter, List<MobKey> members, CaptureContext context) {
        var mobs = new ArrayList<MobFacts>();
        var participants = new LinkedHashMap<UUID, ParticipantFacts>();
        for (var member : members) {
            var profile = engine.profiles.get(member.entityId());
            if (profile == null || !engine.alive(member.entityId())) {
                continue;
            }
            var body = engine.body(member.entityId());
            var rival = engine.world.rival(body);
            var sees = engine.perceives(body.id);
            context.sightings().accept(new MobSighting(member, DuelEngine.WORLD, sees ? Optional.of(rival.id)
                    : Optional.empty(), sees ? OptionalDouble.of(engine.world.distance()) : OptionalDouble.empty()));
            var health = engine.health(body, profile);
            if (!sees) {
                mobs.add(new MobFacts(member, health, Optional.empty(), engine.contexts(profile, health,
                        Optional.empty(), body), Set.of(), Set.of(), Set.of()));
                continue;
            }
            var alias = context.aliasFor().apply(rival.id, ParticipantKind.ENTITY);
            mobs.add(seen(member, profile, body, rival, alias, context));
            participants.putIfAbsent(rival.id, new ParticipantFacts(alias, engine.health(rival, profile),
                    ArmorBand.LIGHT, Set.of(), 1, Optional.of("zombie")));
        }
        return mobs.isEmpty() ? Optional.empty() : Optional.of(new CombatFacts(encounter, engine.clock.tick,
                engine.clock.nanos, mobs, List.copyOf(participants.values()), context.recentEvents(), 0));
    }

    private MobFacts seen(MobKey member, TacticalProfile profile, DuelWorld.Body body, DuelWorld.Body rival,
                          akyrion.thepredict.domain.ParticipantAlias alias, CaptureContext context) {
        var band = engine.band(profile);
        var health = engine.health(body, profile);
        var active = engine.contexts(profile, health, Optional.of(band), body);
        var out = new LinkedHashSet<ChoiceId>();
        for (var choice : profile.choices().values()) {
            if (engine.outOfReach(choice)) {
                out.add(choice.id());
            }
        }
        var movements = EnumSet.allOf(Movement.class);
        movements.remove(Movement.FLANK);
        var horizon = profile.timing().readyHorizonTicks();
        var eligible = Eligibility.eligible(profile, active, engine.ready(profile, body, 0), movements, out);
        var soon = horizon == 0 ? eligible : Eligibility.eligible(profile, active, engine.ready(profile, body,
                horizon), movements, out);
        var whenReady = Eligibility.eligible(profile, active, profile.choices().keySet(), movements, out);
        return new MobFacts(member, health, Optional.of(relation(member, body, rival, alias, band, context)), active,
                eligible, Set.of(), out, java.util.Map.of(), whenReady, soon);
    }

    private TargetRelation relation(MobKey member, DuelWorld.Body body, DuelWorld.Body rival,
                                    akyrion.thepredict.domain.ParticipantAlias alias,
                                    akyrion.thepredict.domain.DistanceBand band, CaptureContext context) {
        if (!engine.rules.serverPorts()) {
            return new TargetRelation(alias, band, Trend.STEADY, true, true, false, false, false);
        }
        var world = engine.world;
        var distance = world.distance();
        var trend = trend(body, rival, world.physics);
        var previous = context.previousTargetDistanceBlocks().get(member);
        if (previous != null) {
            trend = corrected(trend, distance, previous);
        }
        return new TargetRelation(alias, band, trend, true, world.lineOfSight(), outsideReach(rival, distance),
                false, false);
    }

    /**
     * The trend of an entity from its velocity, as {@code TargetTrend.trendFromVelocity}: the last step of the rival,
     * times the friction that leaves its velocity after the move.
     */
    static Trend trend(DuelWorld.Body mob, DuelWorld.Body rival, DuelPhysics physics) {
        var towardsX = mob.x - rival.x;
        var towardsZ = mob.z - rival.z;
        var length = Math.hypot(towardsX, towardsZ);
        if (length < 1.0E-5) {
            return Trend.STEADY;
        }
        var keep = physics.groundFriction() > 0 ? physics.groundFriction() : 1.0;
        var velocityX = (rival.x - rival.previousX) * keep;
        var velocityZ = (rival.z - rival.previousZ) * keep;
        var along = (velocityX * towardsX + velocityZ * towardsZ) / length;
        var sideways = Math.abs(velocityX * towardsZ - velocityZ * towardsX) / length;
        if (sideways > MOVING_BLOCKS_PER_TICK && sideways > Math.abs(along)) {
            return Trend.STRAFING;
        }
        if (along > MOVING_BLOCKS_PER_TICK) {
            return Trend.CLOSING;
        }
        return along < -MOVING_BLOCKS_PER_TICK ? Trend.BACKING_AWAY : Trend.STEADY;
    }

    /** {@code TargetTrend.correctedByDistance}. */
    static Trend corrected(Trend fromVelocity, double currentBlocks, double previousBlocks) {
        if (currentBlocks < previousBlocks - DISTANCE_CHANGE_BLOCKS) {
            return Trend.CLOSING;
        }
        return currentBlocks > previousBlocks + DISTANCE_CHANGE_BLOCKS ? Trend.BACKING_AWAY : fromVelocity;
    }

    /**
     * Whether the mob stands outside the melee reach of its rival ({@code TargetReach}): a zombie by the vanilla check
     * ({@value DuelWorld#VANILLA_REACH} blocks between positions here), a rival bound to ThePredict by the gap against
     * the reach of its melee choices.
     */
    private boolean outsideReach(DuelWorld.Body rival, double distance) {
        var bound = engine.profiles.get(rival.id);
        if (bound == null) {
            return distance > DuelWorld.VANILLA_REACH;
        }
        var reach = 0.0;
        for (var choice : bound.choices().values()) {
            var purpose = choice.purpose();
            if (purpose.isPresent() && purpose.get().delivery() == Delivery.MELEE) {
                reach = Math.max(reach, purpose.get().effectiveReachBlocks().orElse(0));
            }
        }
        return reach > 0 && engine.world.gap() > reach;
    }

    /**
     * The per-tick reading of a server ({@link DuelSignals}) with {@link TickRules#serverPorts}; otherwise only
     * {@code PRESENT}, as in M18.7 (E-8).
     */
    @Override
    public long tickSignals(MobKey mob, UUID expectedTarget) {
        java.util.Objects.requireNonNull(expectedTarget, "expectedTarget");
        if (!engine.rules.serverPorts()) {
            return akyrion.thepredict.domain.TickSignals.PRESENT;
        }
        if (!engine.alive(mob.entityId())) {
            return 0L;
        }
        return DuelSignals.read(engine.world, engine.body(mob.entityId()), expectedTarget, engine.clock.tick);
    }

    @Override
    public Optional<MobProbe> probe(MobKey mob) {
        var profile = engine.profiles.get(mob.entityId());
        if (profile == null || !engine.alive(mob.entityId())) {
            return Optional.empty();
        }
        var body = engine.body(mob.entityId());
        var sees = engine.perceives(body.id);
        return Optional.of(new MobProbe(mob, DuelEngine.WORLD, engine.health(body, profile),
                sees ? Optional.of(engine.world.rival(body).id) : Optional.empty(),
                sees ? Optional.of(engine.band(profile)) : Optional.empty()));
    }

    @Override
    public boolean withinFollowRange(MobKey mob, MobKey member) {
        return false;
    }

    @Override
    public boolean inCombat(UUID entityId) {
        return true;
    }

    @Override
    public boolean isActive(UUID entityId) {
        return engine.alive(entityId);
    }

    @Override
    public Optional<ThreatTableState> threatTable(MobKey mob) {
        return Optional.of(ThreatTableState.DISABLED);
    }
}
