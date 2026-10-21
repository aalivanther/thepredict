package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.MobProbe;
import akyrion.thepredict.domain.NativeLevelMode;
import akyrion.thepredict.domain.TacticalIntent;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Entry point of the outcome windows of M16.8 (principle A9): the listeners report damage dealt and taken and deaths,
 * like {@link ThreatEnrichment}, and the tactical execution opens a window on each adopted intent, feeds it the casts
 * and the probes, and closes it when the intent ends or after {@value OutcomeWindow#OUTCOME_WINDOW_TICKS} ticks. A
 * closed window goes to {@link OutcomeMetrics}, to the ring of its session for {@code inspect} and, with
 * {@code decision.logDecisions}, to one rate-limited line {@code Outcome <level> <tactic> dealt= taken= reached= casts=
 * hits=} (rule C8). Server thread only (rules T1, T6): the listeners move to it first. The engine never reads
 * entities: the health fractions come computed from the adapter (rule D2). Lookups are O(1) by UUID; the death of a
 * participant visits only the members of its encounters, through the participant index of {@link EncounterRegistry}.
 */
public final class OutcomeEvents {
    private final SessionRegistry sessions;
    private final EncounterRegistry encounters;
    private final OutcomeMetrics metrics;
    private final DecisionMetrics decisionMetrics;
    private ExecutionLog log = new ExecutionLog(line -> {
    }, false);
    private DecisionTap tap = DecisionTap.NONE;
    private CombatActions actions = QuietActions.INSTANCE;
    private final long[] poiseStep = new long[5];
    private BiConsumer<String, OutcomeWindow.Summary> aggregates = (profile, summary) -> {
    };
    private BiConsumer<String, CalibrationBinDelta> bins = (profile, delta) -> {
    };
    private SpawnLevelBook spawns = new SpawnLevelBook();

    /**
     * Creates the entry point.
     *
     * @param sessions sessions, to find the member of each event
     * @param encounters encounters, to find the members that faced a participant
     * @param metrics totals of the closed windows
     * @param decisionMetrics counters, for the effective contingencies (M16.6)
     */
    public OutcomeEvents(SessionRegistry sessions, EncounterRegistry encounters, OutcomeMetrics metrics,
                         DecisionMetrics decisionMetrics) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.encounters = Objects.requireNonNull(encounters, "encounters");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.decisionMetrics = Objects.requireNonNull(decisionMetrics, "decisionMetrics");
    }

    /**
     * Receives each closed window as a profile id and a summary.
     *
     * @param aggregates the listener
     */
    public void hearAggregate(BiConsumer<String, OutcomeWindow.Summary> aggregates) {
        this.aggregates = Objects.requireNonNull(aggregates, "aggregates");
    }

    /**
     * Receives one calibration bin when a Jev window closes with a positive effect (M22.6).
     *
     * @param bins the listener
     */
    public void hearBins(BiConsumer<String, CalibrationBinDelta> bins) {
        this.bins = Objects.requireNonNull(bins, "bins");
    }

    /**
     * Remembers a spawn step after an unreachable fight (M22.12).
     *
     * @param book the book shared with the lifecycle listener
     */
    public void hearSpawns(SpawnLevelBook book) {
        this.spawns = Objects.requireNonNull(book, "book");
    }

    /**
     * A member damaged someone.
     *
     * @param mobId entity of the member that dealt the damage
     * @param victimId entity damaged
     * @param healthFraction final damage over the victim's maximum health, {@code >= 0}
     * @param tick server tick
     */
    public void dealt(UUID mobId, UUID victimId, double healthFraction, long tick) {
        Objects.requireNonNull(victimId, "victimId");
        sessions.find(Objects.requireNonNull(mobId, "mobId")).ifPresent(session -> {
            var target = session.lastTargetId().orElse(null);
            var clock = session.difficulty().clock();
            var continuing = clock.fighting(victimId);
            var after = continuing ? Math.max(0.0, clock.healthRemaining() - healthFraction)
                    : Math.max(0.0, 1.0 - healthFraction);
            if (target != null) {
                clock.dealt(tick, victimId, target, healthFraction, after).ifPresent(session.difficulty()::noteFight);
            }
            if (target != null && victimId.equals(target)) {
                session.ownImpacts().noteDealt(target, healthFraction);
                session.difficulty().noteHit(tick, healthFraction);
                session.noteGroupHit(victimId, tick, healthFraction >= 1.0);
                session.noteMotivatedHit(tick);
                session.pendingExploit().flush(tap, session.key());
            }
            session.outcomes().open().ifPresent(window -> window.dealt(victimId, healthFraction, tick));
            session.tellResults().noteDealt(victimId, healthFraction, tick);
            PostureRecords.flush(session, tick, tap);
        });
    }

    /**
     * A member took damage.
     *
     * @param mobId entity of the member
     * @param healthFraction final damage over the member's maximum health, {@code >= 0}
     * @param tick server tick
     */
    public void taken(UUID mobId, double healthFraction, long tick) {
        taken(mobId, healthFraction, 0.0, tick);
    }

    /**
     * A member took damage of any cause. The max health is stored on the fight clock.
     *
     * @param mobId entity of the member
     * @param healthFraction final damage over the member's maximum health, {@code >= 0}
     * @param maxHealth the member's maximum health
     * @param tick server tick
     */
    public void taken(UUID mobId, double healthFraction, double maxHealth, long tick) {
        sessions.find(Objects.requireNonNull(mobId, "mobId")).ifPresent(session -> {
            session.difficulty().clock().noteMaxHealth(maxHealth);
            session.outcomes().open().ifPresent(window -> window.taken(healthFraction));
            noteDamage(session, healthFraction, tick);
        });
    }

    /**
     * A member took damage from a known attacker. The open window is fed either way. Poise and the punish mark do not
     * need a window (M23.7).
     *
     * @param mobId entity of the member
     * @param attackerId causing entity
     * @param healthFraction final damage over the member's maximum health, {@code >= 0}
     * @param tick server tick
     */
    public void takenFrom(UUID mobId, UUID attackerId, double healthFraction, long tick) {
        takenFrom(mobId, attackerId, healthFraction, 0.0, tick);
    }

    /**
     * A member took damage from a known attacker. The open window is fed either way. The max health weights the share.
     *
     * @param mobId entity of the member
     * @param attackerId causing entity
     * @param healthFraction final damage over the member's maximum health, {@code >= 0}
     * @param maxHealth the member's maximum health
     * @param tick server tick
     */
    public void takenFrom(UUID mobId, UUID attackerId, double healthFraction, double maxHealth, long tick) {
        Objects.requireNonNull(attackerId, "attackerId");
        sessions.find(Objects.requireNonNull(mobId, "mobId")).ifPresent(session -> {
            session.difficulty().clock().noteMaxHealth(maxHealth);
            session.outcomes().open().ifPresent(window -> window.taken(healthFraction));
            noteDamage(session, healthFraction, tick);
            var target = session.lastTargetId().orElse(null);
            if (target != null) {
                session.difficulty().clock().takenFrom(tick, attackerId, target, healthFraction)
                        .ifPresent(session.difficulty()::noteFight);
            }
            if (target != null && attackerId.equals(target)) {
                session.noteGroupHit(attackerId, tick, false);
            }
            session.tellResults().notePunish(attackerId, healthFraction, tick);
            var encounter = session.encounter().flatMap(encounters::find);
            var rival = encounter.isPresent() && encounter.get().isParticipant(attackerId)
                    && !attackerId.equals(mobId);
            PoiseWatch.onDamage(session, attackerId, healthFraction, tick, rival, actions, poiseStep);
            StanceClock.flushConflict(session, tick, log, decisionMetrics);
            PostureRecords.flush(session, tick, tap);
        });
    }

    /**
     * A blocked heavy or severe hit of a member fills poise when the profile has it.
     *
     * @param mobId entity of the member
     * @param tick server tick
     */
    public void blocked(UUID mobId, long tick) {
        sessions.find(Objects.requireNonNull(mobId, "mobId")).ifPresent(session -> {
            session.tellResults().noteBlocked(tick);
            PoiseWatch.onBlocked(session, tick, actions, poiseStep);
            PostureRecords.flush(session, tick, tap);
        });
    }

    /**
     * Shares the cast port so a poise break can signal.
     *
     * @param combatActions production or test actions
     */
    public void bind(CombatActions combatActions) {
        actions = Objects.requireNonNull(combatActions, "combatActions");
    }

    /**
     * A member died: its window closes with its death. An O(1) lookup by UUID; any other entity is ignored.
     *
     * @param entityId entity that died
     * @param tick server tick
     */
    public void died(UUID entityId, long tick) {
        sessions.find(Objects.requireNonNull(entityId, "entityId")).ifPresent(member -> {
            member.outcomes().open().ifPresent(OutcomeWindow::mobDied);
            var settings = member.profile().difficulty();
            member.difficulty().closeAs(akyrion.thepredict.domain.FightEventKind.MOB_DIED, tick, settings.minRung(),
                    settings.maxRung(), settings.learn(), member.profile().id());
            noteSpawn(member);
            close(member, tick);
        });
    }

    /**
     * A participant died. For every member of its encounters: the member notes the death of that participant, and
     * whether it was fighting it, for the memory of M27; an open window whose target it was notes the death of the
     * target; a fight clock that is open against it closes as {@code TARGET_DIED} with or without such a window,
     * because a window lasts at most {@value OutcomeWindow#OUTCOME_WINDOW_TICKS} ticks and a fight does not; and the
     * group clock marks the death of that player. Only those members are visited, through the participant index.
     *
     * @param worldId world of the participant
     * @param participantId participant that died, a player or any other living entity
     */
    public void participantDied(UUID worldId, UUID participantId) {
        Objects.requireNonNull(participantId, "participantId");
        for (var encounter : encounters.ofParticipant(Objects.requireNonNull(worldId, "worldId"), participantId)) {
            for (var member : encounter.memberSessions()) {
                targetDied(member, participantId);
            }
        }
    }

    /**
     * The death of a participant seen by one member. The close keeps the tick of the last dealt hit, as before.
     *
     * @param member a member of an encounter of the participant
     * @param participantId participant that died
     */
    private void targetDied(MobSession member, UUID participantId) {
        var window = member.outcomes().open().filter(open -> open.targetId().filter(participantId::equals).isPresent());
        window.ifPresent(OutcomeWindow::targetDied);
        member.groupClock().ifPresent(group -> group.playerDied(participantId));
        var clock = member.difficulty().clock();
        var fought = clock.fighting(participantId)
                || clock.open() && member.lastTargetId().filter(participantId::equals).isPresent();
        member.noteParticipantDeath(participantId, fought || window.isPresent());
        if (window.isEmpty() && !fought) {
            return;
        }
        var settings = member.profile().difficulty();
        var dealtTick = clock.lastDealtTick();
        member.difficulty().closeAs(akyrion.thepredict.domain.FightEventKind.TARGET_DIED,
                dealtTick == Long.MIN_VALUE ? 0L : dealtTick, settings.minRung(), settings.maxRung(), settings.learn(),
                member.profile().id());
        noteSpawn(member);
    }

    /**
     * Tells whether a member has an open window, so a listener computes a health fraction only when it counts: an O(1)
     * lookup by UUID.
     *
     * @param mobId entity of a possible member
     * @return whether its session has an open outcome window
     */
    public boolean tracks(UUID mobId) {
        return sessions.find(Objects.requireNonNull(mobId, "mobId")).flatMap(session -> session.outcomes().open())
                .isPresent();
    }

    /**
     * Whether the entity is a member, with or without an open window (M23.7).
     *
     * @param mobId entity
     * @return whether a session exists
     */
    public boolean tracksMember(UUID mobId) {
        return sessions.find(Objects.requireNonNull(mobId, "mobId")).isPresent();
    }

    /**
     * Returns the totals of the closed windows.
     *
     * @return live totals, read on the server thread
     */
    public OutcomeMetrics metrics() {
        return metrics;
    }

    /** Shares the execution log, so outcome lines and decision lines have one limit. */
    void logTo(ExecutionLog executionLog) {
        log = Objects.requireNonNull(executionLog, "executionLog");
    }

    /** Tells the duel recorder of each closed window (M18.4); set once when the execution is wired. */
    void tapTo(DecisionTap decisionTap) {
        tap = Objects.requireNonNull(decisionTap, "decisionTap");
    }

    /**
     * Closes the window of the previous intent and opens one for a newly adopted intent of level {@code JEV},
     * {@code ALTERNATIVE} or {@code LOCAL}.
     */
    void adopted(MobSession session, TacticalIntent intent, long tick, boolean contingency) {
        close(session, tick);
        if (intent.level() != ControlLevel.BASE) {
            session.outcomes().open(new OutcomeWindow(intent.level(), intent.tactic(), intent.movement(), tick,
                    session.lastTargetId(), contingency, intent.confidence()));
            session.learning().noteWindow(WindowCredit.live(session).text(), session.learning().damage().rate(tick));
            session.learning().noteWindowLeader(intent.level() == ControlLevel.JEV && !contingency);
        }
    }

    /** Counts a cast in the open window. */
    void cast(MobSession session, boolean damaging, long tick) {
        cast(session, damaging, tick, false);
    }

    /**
     * Counts a cast in the open window.
     *
     * @param session the session
     * @param damaging whether the choice deals damage
     * @param tick current tick
     * @param ready whether the cast is a candidate of {@code ANY_READY}
     */
    void cast(MobSession session, boolean damaging, long tick, boolean ready) {
        session.outcomes().open().ifPresent(window -> window.cast(damaging, tick, ready));
    }

    /** Feeds the distance band of a probe and closes a window that reached its longest duration. */
    void probed(MobSession session, MobProbe probe, long tick) {
        var open = session.outcomes().open();
        if (open.isEmpty()) {
            return;
        }
        open.get().probed(probe.targetDistance());
        if (open.get().due(tick)) {
            close(session, tick);
        }
    }

    /**
     * Closes the open window of a session whose intent ended. The credit row carries whether a player takes part in
     * the encounter, as the capture reads it, and the wall time of the adapter (M28.2, LD-08).
     */
    void close(MobSession session, long tick) {
        var open = session.outcomes().open();
        var contingency = open.map(OutcomeWindow::contingency).orElse(false);
        var confidence = open.map(OutcomeWindow::confidence).orElse(0.0);
        session.outcomes().close(tick).ifPresent(summary -> {
            WindowCredit.record(session, summary, playersPresent(session), encounters.epochMs());
            metrics.record(summary);
            tap.outcomeClosed(session.key(), summary);
            aggregates.accept(session.profile().id(), summary);
            if (CalibrationBins.counts(summary.level()) && summary.effect() > 0.0) {
                bins.accept(session.profile().id(), new CalibrationBinDelta("jev", "choice", "effect_positive",
                        CalibrationBins.bin(confidence), 1L, confidence, 1.0, 0L));
            }
            if (contingency && summary.effect() > 0.0) {
                decisionMetrics.add(DecisionMetrics.Counter.CONTINGENCIES_EFFECTIVE, 1);
            }
            log.decision(tick, () -> "Outcome " + summary.level() + " " + summary.tactic().value() + " dealt="
                    + decimal(summary.dealt()) + " taken=" + decimal(summary.taken()) + " reached="
                    + (summary.reached() ? "yes" : "no") + " casts=" + summary.casts() + " hits=" + summary.hits());
        });
    }

    /** Whether a player is a participant of the last capture of the session's encounter; no encounter, no player. */
    private boolean playersPresent(MobSession session) {
        var encounter = session.encounter();
        if (encounter.isEmpty()) {
            return false;
        }
        var found = encounters.find(encounter.get());
        return found.isPresent() && CaptureArm.players(found.get());
    }

    /**
     * Damage taken on the ring of the last 60 ticks (M28.2, LD-08): one slot per server tick, so the rate before a
     * window is a fraction of health per tick, the unit of the damage taken during it.
     */
    private static void noteDamage(MobSession session, double fraction, long tick) {
        session.learning().damage().add(tick, fraction);
    }

    private void noteSpawn(MobSession member) {
        var settings = member.profile().difficulty();
        if (!member.difficulty().nativeAllowed() || settings.nativeLevel() != NativeLevelMode.SPAWN) {
            return;
        }
        var delta = member.difficulty().spawnDelta();
        if (delta != 0) {
            spawns.note(member.profile().id(), delta);
        }
    }

    private static String decimal(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
