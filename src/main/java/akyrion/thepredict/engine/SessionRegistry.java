package akyrion.thepredict.engine;

import akyrion.thepredict.domain.FallbackReason;
import akyrion.thepredict.domain.IntentView;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.SessionState;
import akyrion.thepredict.domain.TacticalProfile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * One session per bound mob, keyed by entity UUID and never by the integer entity ID (rules I1, A3, M06.1).
 * Server thread only (rule T1). No artificial limit: the number of sessions is the number of bound mobs.
 *
 * <p>Every operation that changes a generation or a state updates {@link IntentBoard} in the same call: it
 * publishes {@link IntentView#base(int)} when a session opens, resumes or advances while active, and removes the
 * view when a session suspends or closes, so a condition never reads a generation that no longer exists
 * (rule A6). A closed session is removed from the map, its encounter, the probe ring and the installation queue, so
 * nothing in {@code engine} keeps it; a later session of the same UUID starts again at generation 0. Every active
 * session whose goal set is pending waits in the installation queue of the coordinator (M09.1 step 5).
 */
public final class SessionRegistry {
    private final Map<UUID, MobSession> sessions = new LinkedHashMap<>();
    /** The sessions of the map in opening order. The walks of every tick read it by index, with no iterator (C12). */
    private final List<MobSession> opened = new ArrayList<>();
    private final IntentBoard board;
    private final EncounterRegistry encounters;
    private final Agenda agenda;
    private final InstallQueue installs = new InstallQueue();
    private long baseSeed;
    private akyrion.thepredict.domain.DifficultyGlobals difficulty = akyrion.thepredict.domain.DifficultyGlobals.DEFAULT;
    private DifficultyStats difficultyStats = DifficultyStats.NONE;
    private akyrion.thepredict.domain.CandidateScoring candidateScoring =
            akyrion.thepredict.domain.CandidateScoring.DEFAULT;

    /**
     * Creates an empty registry.
     *
     * @param board board where the view of each session is published
     * @param encounters encounters a session leaves when it suspends, closes or changes world
     * @param agenda agenda whose probe ring holds the active sessions
     */
    public SessionRegistry(IntentBoard board, EncounterRegistry encounters, Agenda agenda) {
        this.board = Objects.requireNonNull(board, "board");
        this.encounters = Objects.requireNonNull(encounters, "encounters");
        this.agenda = Objects.requireNonNull(agenda, "agenda");
    }

    /**
     * Opens the session of a mob: generation 0 the first time; a suspended session resumes with the given
     * profile and the next generation; an active one is returned unchanged. The new session is {@code ACTIVE}
     * with {@code installPending}.
     *
     * @param entityId UUID of the mob
     * @param profile profile bound to its type
     * @return the active session
     */
    public MobSession open(UUID entityId, TacticalProfile profile) {
        Objects.requireNonNull(profile, "profile");
        var existing = sessions.get(Objects.requireNonNull(entityId, "entityId"));
        if (existing != null) {
            return existing.state() == SessionState.SUSPENDED ? activate(existing, profile) : existing;
        }
        var session = new MobSession(new MobKey(entityId, 0), profile);
        session.useBoard(board);
        session.reseed(baseSeed);
        session.difficulty().globals(difficulty);
        session.difficulty().stats(difficultyStats);
        session.candidateScoring(candidateScoring);
        sessions.put(entityId, session);
        opened.add(session);
        agenda.addProbe(session);
        installs.add(entityId);
        board.publish(session.key(), IntentView.base(0));
        return session;
    }

    /**
     * Suspends an active session (chunk unloaded): it leaves its encounter and its view is removed.
     * Does nothing for an unknown or already suspended mob.
     *
     * @param entityId UUID of the mob
     */
    public void suspend(UUID entityId) {
        var session = sessions.get(entityId);
        if (session == null || session.state() != SessionState.ACTIVE) {
            return;
        }
        encounters.leave(session);
        session.invalidateReady();
        session.state(SessionState.SUSPENDED);
        agenda.removeProbe(entityId);
        installs.remove(entityId);
        board.clear(entityId);
    }

    /**
     * Resumes a suspended session with the next generation, {@code ACTIVE} and {@code installPending}.
     *
     * @param entityId UUID of the mob
     * @return the resumed session
     * @throws NoSuchElementException if the mob has no session
     * @throws IllegalStateException if the session is not suspended
     */
    public MobSession resume(UUID entityId) {
        var session = sessions.get(entityId);
        if (session == null) {
            throw new NoSuchElementException("no session for " + entityId);
        }
        if (session.state() != SessionState.SUSPENDED) {
            throw new IllegalStateException("only a suspended session resumes, " + session.key() + " is " + session.state());
        }
        return activate(session, session.profile());
    }

    /**
     * Advances the generation without changing the state (world change); the encounter is kept. Like every
     * invalidation of M20.9, the micro goes back to {@code NATIVE} and the reaction slots and cooldowns are dropped.
     *
     * @param entityId UUID of the mob
     * @return the session with its new key, empty for an unknown mob
     */
    public Optional<MobSession> advanceGeneration(UUID entityId) {
        return Optional.ofNullable(sessions.get(entityId)).map(session -> advance(session, session.profile()));
    }

    /**
     * Advances the generation with the profile of a new catalog without changing the state (reload), with the same
     * invalidation of M20.9.
     *
     * @param entityId UUID of the mob
     * @param profile profile bound to its type after the reload
     * @return the session with its new key, empty for an unknown mob
     */
    public Optional<MobSession> advanceGeneration(UUID entityId, TacticalProfile profile) {
        Objects.requireNonNull(profile, "profile");
        return Optional.ofNullable(sessions.get(entityId)).map(session -> advance(session, profile));
    }

    /**
     * Takes a session out of its encounter without changing its generation or state (world change).
     *
     * @param entityId UUID of the mob
     */
    public void leaveEncounter(UUID entityId) {
        var session = sessions.get(entityId);
        if (session != null) {
            encounters.leave(session);
        }
    }

    /**
     * Closes a session: {@code CLOSED}, out of its encounter, view removed and forgotten.
     *
     * @param entityId UUID of the mob
     * @param reason cause recorded on the session
     * @return the key of the closed session, empty if the mob had none
     */
    public Optional<MobKey> close(UUID entityId, FallbackReason reason) {
        Objects.requireNonNull(reason, "reason");
        var session = sessions.remove(entityId);
        if (session == null) {
            return Optional.empty();
        }
        opened.remove(session);
        encounters.leave(session);
        session.invalidateReady();
        session.state(SessionState.CLOSED);
        session.fallback(reason);
        agenda.removeProbe(entityId);
        installs.remove(entityId);
        board.clear(entityId);
        return Optional.of(session.key());
    }

    /**
     * Finds the session of a mob.
     *
     * @param entityId UUID of the mob
     * @return the active or suspended session
     */
    public Optional<MobSession> find(UUID entityId) {
        return Optional.ofNullable(sessions.get(entityId));
    }

    /**
     * Finds the session of an exact incarnation; an answer for an older generation finds nothing.
     *
     * @param key identity with generation
     * @return the session only if its current generation is the same
     */
    public Optional<MobSession> find(MobKey key) {
        return find(key.entityId()).filter(session -> session.key().equals(key));
    }

    /**
     * Returns a copy of the active sessions in opening order.
     *
     * @return active sessions
     */
    public List<MobSession> active() {
        var active = new ArrayList<MobSession>();
        activeInto(active);
        return Collections.unmodifiableList(active);
    }

    /**
     * Appends the active sessions, in opening order, to a list the caller reuses, so the per-tick walk allocates
     * nothing once the list has grown (rule C12).
     *
     * @param into the list; it is not cleared
     */
    public void activeInto(List<MobSession> into) {
        for (var index = 0; index < opened.size(); index++) {
            var session = opened.get(index);
            if (session.state() == SessionState.ACTIVE) {
                into.add(session);
            }
        }
    }

    /**
     * Copies active sessions into the buffer without a stream.
     *
     * @param into the buffer
     * @return how many were copied
     */
    public int copyActive(MobSession[] into) {
        var count = 0;
        for (var index = 0; index < opened.size(); index++) {
            var session = opened.get(index);
            if (session.state() != SessionState.ACTIVE) {
                continue;
            }
            if (count == into.length) {
                break;
            }
            into[count++] = session;
        }
        return count;
    }

    /**
     * Returns a copy of every session, active or suspended, in opening order.
     *
     * @return all sessions
     */
    public List<MobSession> all() {
        return List.copyOf(sessions.values());
    }

    /**
     * Counts the sessions in a state.
     *
     * @param state {@code ACTIVE} or {@code SUSPENDED}; a closed session is not kept
     * @return number of sessions in that state
     */
    public int count(SessionState state) {
        var count = 0;
        for (var index = 0; index < opened.size(); index++) {
            if (opened.get(index).state() == state) {
                count++;
            }
        }
        return count;
    }

    /** Returns the queue of active sessions whose goals are still to be installed (M09.1 step 5). */
    InstallQueue installs() {
        return installs;
    }

    /**
     * Installs the difficulty section. Later sessions copy it; sessions already open are updated.
     *
     * @param globals the section
     */
    public void difficulty(akyrion.thepredict.domain.DifficultyGlobals globals) {
        difficulty = java.util.Objects.requireNonNull(globals, "globals");
        for (var session : sessions.values()) {
            session.difficulty().globals(difficulty);
        }
    }

    /**
     * Installs where the fights the slow loop of M22 judges are written (M22.6, JS point 7). Later sessions copy it;
     * sessions already open are updated. {@link DifficultyStats#NONE} drops the rows.
     *
     * @param stats the sink, with the wall time of the adapter
     */
    public void difficultyStats(DifficultyStats stats) {
        difficultyStats = java.util.Objects.requireNonNull(stats, "stats");
        for (var session : sessions.values()) {
            session.difficulty().stats(difficultyStats);
        }
    }

    /**
     * Installs how candidates are scored. Later sessions copy it; sessions already open are updated.
     *
     * @param scoring priority or utility
     */
    public void candidateScoring(akyrion.thepredict.domain.CandidateScoring scoring) {
        candidateScoring = java.util.Objects.requireNonNull(scoring, "scoring");
        for (var session : sessions.values()) {
            session.candidateScoring(candidateScoring);
        }
    }

    /**
     * Sets the base seed of {@link akyrion.thepredict.domain.StableRandom} for sessions opened from now on.
     *
     * @param seed the seed
     */
    public void baseSeed(long seed) {
        baseSeed = seed;
    }

    private MobSession activate(MobSession session, TacticalProfile profile) {
        session.invalidateReady();
        session.nextGeneration(profile);
        session.reseed(baseSeed);
        session.state(SessionState.ACTIVE);
        agenda.addProbe(session);
        installs.add(session.key().entityId());
        board.publish(session.key(), IntentView.base(session.key().generation()));
        return session;
    }

    private MobSession advance(MobSession session, TacticalProfile profile) {
        session.invalidateReady();
        session.nextGeneration(profile);
        session.reseed(baseSeed);
        if (session.state() == SessionState.ACTIVE) {
            installs.add(session.key().entityId());
            board.publish(session.key(), IntentView.base(session.key().generation()));
        }
        return session;
    }
}
