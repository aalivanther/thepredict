package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ActiveUseConditions;
import akyrion.thepredict.domain.CaptureContext;
import akyrion.thepredict.domain.CombatFacts;
import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.GroupBrief;
import akyrion.thepredict.domain.GroupPlan;
import akyrion.thepredict.domain.MobFacts;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.MobSighting;
import akyrion.thepredict.domain.ParticipantAlias;
import akyrion.thepredict.domain.ParticipantKind;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;

/**
 * Runs one {@link CombatReader#capture} for an encounter and keeps in the engine what the adapter must not hold
 * (M07.1, rule A3): it builds the {@link CaptureContext} from the encounter and its sessions, and applies what the
 * capture saw. Server thread only.
 *
 * <p>Every participant the capture perceives, a player or any other living entity (M17.1), gets its alias of its kind
 * through {@link Encounter#aliasFor}. After the capture, a
 * participant that is neither perceived nor the current target of a member leaves the participant index first, and
 * then the members' targets and the perceived participants join it, so a newly perceived one enters even when the
 * index was full of stale participants, and a target beyond every perception radius stays indexed: the next
 * {@link EncounterRegistry#assign} keeps the encounter instead of forming a new one.
 * Each sighting updates the target and distance of its session, and a member seen in another world changes world
 * through {@link SessionLifecycle#changedWorld} (DT-21 alternative). The facts of each mob are kept in its session
 * and the whole facts of the last capture in the encounter, for {@code /thepredict inspect} (M12.2).
 * The capture time is measured with {@link ServerClock#nanoTime()} and recorded as
 * {@link DecisionMetrics.Span#CAPTURE} (M09.6).
 */
public final class EncounterCapture {
    private final CombatReader reader;
    private final SessionRegistry sessions;
    private final EncounterRegistry encounters;
    private final SessionLifecycle lifecycle;
    private final ServerClock clock;
    private final DecisionMetrics metrics;
    private GroupBriefSource groups;

    /**
     * Creates the capture step.
     *
     * @param reader combat reader port
     * @param sessions sessions whose target, distance and facts are updated
     * @param encounters encounters and their participant index
     * @param lifecycle lifecycle, for members seen in another world
     * @param clock server clock, to measure the capture
     * @param metrics metrics where each capture time is recorded
     */
    public EncounterCapture(CombatReader reader, SessionRegistry sessions, EncounterRegistry encounters,
                            SessionLifecycle lifecycle, ServerClock clock, DecisionMetrics metrics) {
        this.reader = Objects.requireNonNull(reader, "reader");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.encounters = Objects.requireNonNull(encounters, "encounters");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    /**
     * Briefs to stamp on the next capture. Absent until the group step is wired.
     *
     * @param source the director
     */
    public void hearGroup(GroupBriefSource source) {
        groups = Objects.requireNonNull(source, "source");
    }

    /**
     * Captures an open encounter.
     *
     * @param id encounter to capture
     * @param allowPathfinding mobs allowed to compute a path in this capture; the caller bounds it
     * @return the facts, or empty when the encounter is closed, has no members or no member could be read
     */
    public Optional<CombatFacts> capture(EncounterId id, Set<MobKey> allowPathfinding) {
        Objects.requireNonNull(allowPathfinding, "allowPathfinding");
        var found = encounters.find(id).filter(encounter -> encounter.memberCount() > 0);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        var encounter = found.get();
        var seen = new LinkedHashSet<UUID>();
        var sightings = new ArrayList<MobSighting>();
        var context = context(encounter, allowPathfinding, seen, sightings);
        var start = clock.nanoTime();
        var facts = reader.capture(id, encounter.members(), context);
        var end = clock.nanoTime();
        metrics.record(DecisionMetrics.Span.CAPTURE, end - start, end);
        sightings.forEach(sighting -> apply(encounter, sighting));
        if (facts.isEmpty()) {
            return facts;
        }
        return Optional.of(keep(encounter, facts.get(), seen));
    }

    private CaptureContext context(Encounter encounter, Set<MobKey> allowed, Set<UUID> seen, List<MobSighting> sightings) {
        var distances = new HashMap<MobKey, Double>();
        var unreachable = new HashSet<MobKey>();
        for (var member : encounter.memberSessions()) {
            member.lastTargetDistanceBlocks().ifPresent(distance -> distances.put(member.key(), distance));
            if (member.lastMobFacts().flatMap(MobFacts::target).filter(target -> !target.reachable()).isPresent()) {
                unreachable.add(member.key());
            }
        }
        BiFunction<UUID, ParticipantKind, ParticipantAlias> aliasFor = (participantId, kind) -> {
            seen.add(participantId);
            return encounter.aliasFor(participantId, kind);
        };
        return new CaptureContext(aliasFor, allowed, distances, encounter.recentEvents(), encounter.worldId(),
                unreachable, sightings::add);
    }

    private void apply(Encounter encounter, MobSighting sighting) {
        var session = sessions.find(sighting.mob());
        if (session.isEmpty()) {
            return;
        }
        if (!sighting.worldId().equals(encounter.worldId())) {
            lifecycle.changedWorld(sighting.mob().entityId());
            return;
        }
        if (sighting.targetId().isPresent()) {
            session.get().recordTarget(sighting.targetId().get(), sighting.targetDistanceBlocks().getAsDouble());
        } else {
            session.get().loseTarget();
        }
    }

    private CombatFacts keep(Encounter encounter, CombatFacts facts, Set<UUID> seen) {
        var stamped = stamp(encounter, facts);
        for (var mob : stamped.mobs()) {
            sessions.find(mob.mob()).ifPresent(session -> {
                session.facts(mob, stamped.captureTick());
                session.noteConditions(ActiveUseConditions.of(mob, stamped.participants()));
            });
        }
        encounter.lastFacts(stamped);
        if (encounter.isClosed()) {
            return facts;
        }
        noteRelations(encounter, facts);
        var targets = currentTargets(encounter);
        for (var participant : encounter.participants()) {
            if (!seen.contains(participant) && !targets.contains(participant)) {
                encounters.removeParticipant(encounter.id(), participant);
            }
        }
        targets.forEach(participantId -> encounters.addParticipant(encounter.id(), participantId));
        seen.forEach(participantId -> encounters.addParticipant(encounter.id(), participantId));
        return stamped;
    }

    private CombatFacts stamp(Encounter encounter, CombatFacts facts) {
        if (groups == null) {
            return facts;
        }
        var brief = groups.brief(encounter.id());
        if (brief == null || brief == GroupBrief.NONE) {
            return facts;
        }
        encounter.noteGroupQuestion(true);
        if (brief.asksPlan()) {
            var plans = new ArrayList<GroupPlan>();
            for (var name : brief.candidates()) {
                GroupPlan.parse(name).ifPresent(plans::add);
            }
            encounter.offerGroupCapture(facts.captureTick(), true, plans);
        }
        return facts.withGroup(facts.relations(), brief);
    }

    /** Targets the members hold after this capture's sightings, perceived or not. */
    private void noteRelations(Encounter encounter, CombatFacts facts) {
        var inside = new HashSet<UUID>();
        for (var member : encounter.memberSessions()) {
            inside.add(member.key().entityId());
        }
        for (var mob : facts.mobs()) {
            sessions.find(mob.mob()).ifPresent(session -> {
                var relations = reader.relations(mob.mob(), inside);
                session.noteRelations(relations.parent().orElse(null), relations.owner().orElse(null),
                        relations.hasChildren());
                var table = reader.threatTable(mob.mob());
                var top = table.map(akyrion.thepredict.domain.ThreatTableState::topThreat).orElse(0.0);
                var current = session.lastTargetId().orElse(null);
                var onTarget = current == null ? 0.0 : reader.threat(mob.mob(), current);
                session.noteThreatTable(reader.threatTableGroup(mob.mob()), reader.inheritsThreatTable(mob.mob()),
                        top, onTarget);
                noteSamples(encounter, session, mob.mob());
            });
        }
    }

    private void noteSamples(Encounter encounter, MobSession session, akyrion.thepredict.domain.MobKey mob) {
        var who = new UUID[16];
        var points = new double[16];
        var count = 0;
        for (var participant : encounter.participants()) {
            if (count >= who.length) {
                break;
            }
            who[count] = participant;
            points[count] = reader.threat(mob, participant);
            count++;
        }
        session.noteThreatSamples(who, points, count);
    }

    private static Set<UUID> currentTargets(Encounter encounter) {
        var targets = new LinkedHashSet<UUID>();
        for (var member : encounter.memberSessions()) {
            member.lastTargetId().ifPresent(targets::add);
        }
        return targets;
    }
}
