package akyrion.thepredict.engine;

import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.EnemyAction;
import akyrion.thepredict.domain.EnemyPattern;
import akyrion.thepredict.domain.EnemyTokenizer;
import akyrion.thepredict.domain.PatternCause;
import akyrion.thepredict.domain.PatternChange;
import akyrion.thepredict.domain.PatternSettings;
import akyrion.thepredict.domain.PatternState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The pattern models of the open encounters (M26.3, A14): one {@link PatternModels} per encounter, made the first time
 * one of its members is visited and dropped when it closes, plus one small set for sessions outside any encounter. A
 * token read once from a participant's ring reaches every model of that participant. Server thread only.
 */
final class PatternSets {
    private static final EnemyAction[] ACTIONS = EnemyAction.values();
    private static final EnemyPattern[] PATTERNS = EnemyPattern.values();

    private final PatternInbox inbox;
    private final KinematicBook book;
    private final PatternSettings settings;
    private final Map<EncounterId, PatternModels> byEncounter = new HashMap<>();
    private final List<PatternModels> sets = new ArrayList<>();
    private final PatternModels loose = new PatternModels(null, null);
    private final Predicate<UUID> keptOutside = participant -> keeps(participant, null);
    private EncounterRegistry encounters;
    private DecisionMetrics metrics;
    private DecisionTap tap = DecisionTap.NONE;
    private CombatActions cues;
    private java.util.function.Function<MobSession, akyrion.thepredict.domain.PatternMode> modes =
            MobSession::patternMode;
    private java.util.function.Consumer<List<PatternStatDelta>> statsSink = rows -> {
    };
    private java.util.function.LongSupplier epochMs = () -> 0L;

    /**
     * Sets with no encounter yet.
     *
     * @param inbox the rings of the participants
     * @param book the kinematic words, forgotten with the rings (SP-05)
     * @param settings pattern limits
     */
    PatternSets(PatternInbox inbox, KinematicBook book, PatternSettings settings) {
        this.inbox = inbox;
        this.book = book;
        this.settings = settings;
    }

    /**
     * Attaches the open encounters and the counters.
     *
     * @param encounters open encounters
     * @param metrics pattern counters
     */
    void bind(EncounterRegistry encounters, DecisionMetrics metrics) {
        this.encounters = encounters;
        this.metrics = metrics;
    }

    /**
     * Tap told of every change of a model and of the summaries at close.
     *
     * @param tap the tap
     */
    void useTap(DecisionTap tap) {
        this.tap = tap;
    }

    /**
     * The cue port and the effective mode of a member, for {@code PREDICTOR_PATTERN_<PATTERN>} (M26.11, DOC-01).
     *
     * @param actions the cue port
     * @param effective the effective pattern mode of a member
     */
    void useCues(CombatActions actions, java.util.function.Function<MobSession, akyrion.thepredict.domain.PatternMode>
            effective) {
        cues = actions;
        modes = effective;
    }

    /**
     * The set of the session's encounter, made the first time a member is visited. A session outside any encounter
     * models its target in the set of its own, bounded like any other.
     *
     * @param session the visited session
     * @param target its target
     * @return the set, or {@code null} when the target is not a participant of the encounter (A14)
     */
    PatternModels of(MobSession session, UUID target) {
        var id = session.encounter();
        if (id.isEmpty()) {
            return loose;
        }
        var models = byEncounter.get(id.get());
        if (models == null) {
            var open = encounters == null ? null : encounters.find(id.get()).orElse(null);
            models = new PatternModels(id.get(), open);
            byEncounter.put(id.get(), models);
            sets.add(models);
        }
        return models.admits(target) ? models : null;
    }

    /**
     * The model of an enemy: the oldest encounter that fights it first, then the set outside encounters.
     *
     * @param target the enemy
     * @return the model, or {@code null}
     */
    EnemyModel find(UUID target) {
        for (var index = 0; index < sets.size(); index++) {
            var models = sets.get(index);
            var slot = models.find(target);
            if (slot >= 0) {
                return models.model(slot);
            }
        }
        var slot = loose.find(target);
        return slot < 0 ? null : loose.model(slot);
    }

    /**
     * Where the {@code m26_pattern_stats} rows of each closing encounter go (M26.9, JS point 7).
     *
     * @param sink the aggregate cache in production
     * @param epoch wall time supplied by the adapter
     */
    void useStats(java.util.function.Consumer<List<PatternStatDelta>> sink, java.util.function.LongSupplier epoch) {
        statsSink = sink;
        epochMs = epoch;
    }

    /**
     * Hands the pattern rows of an encounter about to close to the sink; an encounter without models or without a
     * trial an observing member saw writes none.
     *
     * @param encounter the encounter about to close, its members still attached
     */
    void stats(Encounter encounter) {
        var models = byEncounter.get(encounter.id());
        if (models == null) {
            return;
        }
        var rows = PatternStatRows.of(encounter, models, epochMs.getAsLong());
        if (!rows.isEmpty()) {
            statsSink.accept(rows);
        }
    }

    /**
     * Writes one summary per enemy that already has a model in this encounter.
     *
     * @param encounter the encounter about to close
     */
    void summarize(Encounter encounter) {
        var models = byEncounter.get(encounter.id());
        if (models == null) {
            return;
        }
        for (var participant : encounter.participants()) {
            var slot = models.find(participant);
            if (slot >= 0) {
                tap.patternSummary(encounter.id(), participant, models.model(slot).summary());
            }
        }
    }

    /**
     * Drops the models of a closing encounter and the rings and kinematic words of its participants that no other
     * encounter keeps (SP-05).
     *
     * @param encounter the encounter about to close
     */
    void close(Encounter encounter) {
        var models = byEncounter.remove(encounter.id());
        if (models == null) {
            return;
        }
        sets.remove(models);
        for (var slot = 0; slot < models.size(); slot++) {
            var participant = models.participant(slot);
            if (!keeps(participant, encounter.id())) {
                inbox.forget(participant);
                book.forget(participant);
            }
        }
    }

    /**
     * Drops the models of encounters that closed unheard and sweeps the rings no one keeps before the table fills.
     * Rings are admitted when a participant arrives ({@link EncounterRegistry#hearArrivals}) and when a visited
     * session targets one, so no tick copies the participant sets of every encounter (C12).
     */
    void tidy() {
        if (encounters == null) {
            return;
        }
        if (byEncounter.size() > encounters.open().size()) {
            dropClosed();
        }
        if (inbox.live() > PatternInbox.SLOTS * 3 / 4) {
            inbox.forgetUnless(keptOutside);
        }
    }

    /**
     * Gives one token of the ring to every model of its participant, in every encounter that fights it.
     *
     * @param target the participant
     * @param token token ordinal
     * @param tick the server tick
     * @param spear whether the weapon was a spear
     */
    void feed(UUID target, int token, int tick, boolean spear) {
        for (var index = 0; index < sets.size(); index++) {
            feed(sets.get(index), target, token, tick, spear);
        }
        feed(loose, target, token, tick, spear);
    }

    /**
     * Learns the idle token of a tick whose ring had nothing new for one model.
     *
     * @param models the set
     * @param slot the model's slot
     * @param tick the server tick
     */
    void idle(PatternModels models, int slot, int tick) {
        var learned = models.model(slot);
        var idle = models.tokenizer(slot).offer(EnemyAction.IDLE, tick, true);
        if (idle.isPresent()) {
            learned.learn(idle.get(), settings);
        }
        emit(learned, models.participant(slot), models.encounter());
    }

    /**
     * Tells the tap of the last change of a model and, on an activation, signals the members of the encounter that
     * target that participant. A model outside any encounter changes silently.
     *
     * @param learned the model
     * @param target its participant
     * @param encounter its encounter, or {@code null}
     */
    void emit(EnemyModel learned, UUID target, EncounterId encounter) {
        var ordinal = learned.pollChange();
        var activated = learned.pollActivated();
        if (encounter == null) {
            return;
        }
        if (ordinal >= 0) {
            tap.patternChanged(encounter, target, new PatternChange(target, PATTERNS[ordinal],
                    PatternCause.values()[learned.changeCause()], PatternState.values()[learned.changeState()]));
        }
        if (activated != 0) {
            signal(encounter, target, activated);
        }
    }

    /**
     * Sends {@code PREDICTOR_PATTERN_<PATTERN>} for each activated pattern to each member of the encounter whose target
     * is the participant, where its mode shows the patterns ({@link akyrion.thepredict.domain.PatternSignals}). The
     * adapter drops a signal the mob does not handle. Rare: one call per activation.
     */
    private void signal(EncounterId id, UUID target, int activated) {
        if (cues == null || encounters == null) {
            return;
        }
        var open = encounters.find(id).orElse(null);
        if (open == null) {
            return;
        }
        for (var pattern : PATTERNS) {
            if ((activated & pattern.bit()) == 0) {
                continue;
            }
            for (var session : open.memberSessions()) {
                if (target.equals(session.lastTargetId().orElse(null))) {
                    akyrion.thepredict.domain.PatternSignals.of(pattern, modes.apply(session))
                            .ifPresent(request -> cues.cue(session.key(), request));
                }
            }
        }
    }

    /**
     * Encounters whose models are kept.
     *
     * @return the count
     */
    int tracked() {
        return byEncounter.size();
    }

    private void dropClosed() {
        var closed = new ArrayList<EncounterId>();
        for (var id : byEncounter.keySet()) {
            if (encounters.find(id).isEmpty()) {
                closed.add(id);
            }
        }
        for (var id : closed) {
            sets.remove(byEncounter.remove(id));
        }
    }

    /**
     * Whether a participant still needs its ring: an open encounter other than {@code closing}, a model of another
     * encounter or the set outside encounters keeps it.
     *
     * @param closing the encounter about to close, or {@code null}
     */
    private boolean keeps(UUID participant, EncounterId closing) {
        if (loose.find(participant) >= 0) {
            return true;
        }
        if (encounters != null) {
            for (var encounter : encounters.open()) {
                if (!encounter.id().equals(closing) && encounter.isParticipant(participant)) {
                    return true;
                }
            }
        }
        for (var index = 0; index < sets.size(); index++) {
            var models = sets.get(index);
            if (!models.encounter().equals(closing) && models.find(participant) >= 0) {
                return true;
            }
        }
        return false;
    }

    private void feed(PatternModels models, UUID target, int token, int tick, boolean spear) {
        var slot = models.find(target);
        if (slot >= 0) {
            keep(models.tokenizer(slot), models.model(slot), token, tick, spear);
            emit(models.model(slot), target, models.encounter());
        }
    }

    private void keep(EnemyTokenizer tokenizer, EnemyModel learned, int token, int tick, boolean spear) {
        var action = ACTIONS[token];
        var kept = tokenizer.offer(action, tick, true);
        if (kept.isPresent()) {
            learned.learn(kept.get(), settings, spear && kept.get() == action);
        }
        PatternSignals.strike(learned, action, tick);
        if (tokenizer.released().isPresent() && metrics != null) {
            metrics.add(DecisionMetrics.PatternCounter.PATTERN_TOKENS_DROPPED, 1);
        }
    }
}
