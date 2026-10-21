package akyrion.thepredict.engine;

import akyrion.thepredict.domain.CaptureContext;
import akyrion.thepredict.domain.CombatFacts;
import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.MobProbe;
import akyrion.thepredict.domain.ThreatTableState;
import akyrion.thepredict.domain.TickSignals;

import akyrion.thepredict.domain.DifficultyPreset;
import akyrion.thepredict.domain.GroupRelations;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;

/**
 * Port through which the engine reads combat from the server (00 §4, M07). Implemented by
 * {@code adapter.server.context.CombatCapture}; tests use doubles (rule P2). Every method runs on the server
 * thread only (rule T1) and never blocks.
 */
public interface CombatReader {
    /**
     * Captures the semantic state of one encounter from what its members could perceive (M07.1). The cost is
     * bounded: at most {@link CombatFacts#MAX_MOBS} members and {@link CombatFacts#MAX_PARTICIPANTS} participants are
     * read, one line of sight per pair and path computations only for the mobs the context allows.
     *
     * @param encounter encounter identity
     * @param members current keys of the member sessions
     * @param context aliases, pathfinding permission, previous distances, history, world and sighting receiver
     * @return the facts, or empty when no member could be read
     */
    Optional<CombatFacts> capture(EncounterId encounter, List<MobKey> members, CaptureContext context);

    /**
     * Cheap reading of one mob for the reactive layer (M07.6): world, health band, target and distance band, with
     * no line of sight, no path and no logging.
     *
     * @param mob mob session
     * @return the probe, or empty when the mob is not active or its entity is not valid
     */
    Optional<MobProbe> probe(MobKey mob);

    /**
     * Tells whether an entity is in combat by the vanilla combat tracker or, for a MythicMobs mob, by a non-empty
     * threat table (M06.4, M07.8).
     *
     * @param entityId UUID of a mob or a participant
     * @return {@code true} while the entity is in combat; {@code false} for an unknown entity
     */
    boolean inCombat(UUID entityId);

    /**
     * Tells whether another member mob is within the follow range of a mob and is not its rival: the predicate with
     * which {@link EncounterRegistry#assign} lets a mob join an existing encounter (M06.3, M09, M17.6). The range is
     * the mob's {@code FOLLOW_RANGE}, capped like its perception radius; one O(1) distance, no path and no line of
     * sight. Two mobs are rivals when the participant policy of one admits the other as a participant and they are not
     * of the same side (rule I13); with the default policy no mob is ever a participant, so no mob is a rival.
     *
     * @param mob mob that is being placed
     * @param member member of a candidate encounter
     * @return {@code true} if both are active, in the same world, the member is within range and they are not rivals
     */
    boolean withinFollowRange(MobKey mob, MobKey member);

    /**
     * Tells whether a mob has an active MythicMobs instance with a valid, living entity (M06.6).
     *
     * @param entityId UUID of the mob
     * @return {@code true} if the instance is registered and its entity is valid and alive
     */
    boolean isActive(UUID entityId);

    /**
     * Reads the native threat table of a mob for {@code /thepredict inspect} (M12.2): whether it exists, its size,
     * its leader's threat and its total, with no participant identity.
     *
     * @param mob mob session
     * @return the state, {@link ThreatTableState#DISABLED} without a table; empty when the mob is not active
     */
    Optional<ThreatTableState> threatTable(MobKey mob);

    /**
     * Threat this mob holds on one participant. The default is {@code 0} so a double that does not read MythicMobs
     * still compiles. Production reads {@code MythicMemberSource.threat}.
     *
     * @param mob mob session
     * @param participant participant identity
     * @return the threat, {@code 0} when the table has no entry
     */
    default double threat(MobKey mob, java.util.UUID participant) {
        java.util.Objects.requireNonNull(mob, "mob");
        java.util.Objects.requireNonNull(participant, "participant");
        return 0.0;
    }

    /**
     * One per-tick reading of a mob and its expected target (M20.2). The default is only {@link TickSignals#PRESENT},
     * so a double that does not sample the world still casts with {@code h = 0}. The production reader returns
     * {@code 0} when the mob is absent.
     *
     * @param mob mob session
     * @param expectedTarget target the session expects; never {@code null}
     * @return packed signals
     */
    default long tickSignals(MobKey mob, UUID expectedTarget) {
        java.util.Objects.requireNonNull(expectedTarget, "expectedTarget");
        return TickSignals.PRESENT;
    }

    /**
     * Writes the zone reading onto the session and returns the signals with the four push fields filled.
     * The default leaves the word unchanged. Called after the target kind is known and before a cast.
     *
     * @param session the mob whose zone is current
     * @param signals the packed reading of this tick
     * @return the signals the tick layer stores
     */
    default long applyZone(MobSession session, long signals) {
        java.util.Objects.requireNonNull(session, "session");
        return signals;
    }

    /**
     * Whether the expected target is a player (M20.5). Hover and punish-recharge are only against players. The
     * default is false, so a double that does not model players never hovers.
     *
     * @param entityId expected target
     * @return whether that entity is a player
     */
    default boolean playerTarget(UUID entityId) {
        java.util.Objects.requireNonNull(entityId, "entityId");
        return false;
    }

    /**
     * Mythic variable {@code predictor_target_seconds} (M22.11, errata E22-2). Empty when the variable is absent.
     * A value outside 5–3600 is ignored by the caller.
     *
     * @param mob mob session
     * @return the override, or empty
     */
    default OptionalDouble targetSecondsOverride(MobKey mob) {
        java.util.Objects.requireNonNull(mob, "mob");
        return OptionalDouble.empty();
    }

    /**
     * Mythic variable {@code predictor_difficulty} (M22.11, errata E22-9). Empty when the variable is absent.
     *
     * @param mob mob session
     * @return the preset, or empty
     */
    default Optional<DifficultyPreset> difficultyOverride(MobKey mob) {
        java.util.Objects.requireNonNull(mob, "mob");
        return Optional.empty();
    }

    /**
     * Preset declared on the mob file ({@code PredictorDifficulty}). Empty when the key is absent.
     *
     * @param mob mob session
     * @return the preset, or empty
     */
    default Optional<DifficultyPreset> declaredPreset(MobKey mob) {
        java.util.Objects.requireNonNull(mob, "mob");
        return Optional.empty();
    }

    /**
     * Whether the mob file sets {@code PredictorEntropy: true}.
     *
     * @param mob mob session
     * @return whether entropy is declared
     */
    default boolean declaredEntropy(MobKey mob) {
        java.util.Objects.requireNonNull(mob, "mob");
        return false;
    }

    /**
     * Whether the mob file sets {@code PredictorTraining: true}.
     *
     * @param mob mob session
     * @return whether training is declared
     */
    default boolean declaredTraining(MobKey mob) {
        java.util.Objects.requireNonNull(mob, "mob");
        return false;
    }

    /**
     * Author stance of the mob ({@code ActiveMob.getStance}), read on the probe in every mode except {@code off}
     * (M23.8). Empty when the mob is absent or has no stance. The default reads nothing.
     *
     * @param mob mob session
     * @return the stance, or empty
     */
    default Optional<String> authorStance(MobKey mob) {
        java.util.Objects.requireNonNull(mob, "mob");
        return Optional.empty();
    }

    /**
     * Vanilla world difficulty name of the mob, read only as context.
     *
     * @param mob mob session
     * @return {@code NORMAL} when the world is unknown
     */
    default String worldDifficulty(MobKey mob) {
        java.util.Objects.requireNonNull(mob, "mob");
        return "NORMAL";
    }

    /**
     * Whether the mob's world has natural regeneration. Read only as context.
     *
     * @param mob mob session
     * @return {@code true} when the world is unknown
     */
    default boolean naturalRegeneration(MobKey mob) {
        java.util.Objects.requireNonNull(mob, "mob");
        return true;
    }

    /**
     * Name of the mob's world, used to apply {@code difficulty.worlds}. Empty when the world is unknown.
     *
     * @param mob mob session
     * @return the world name, or empty
     */
    default String worldName(MobKey mob) {
        java.util.Objects.requireNonNull(mob, "mob");
        return "";
    }

    /**
     * Players near the mob at an adjustment, not on the per-tick path (M22.1).
     *
     * @param mob mob session
     * @return at least 1
     */
    default int partySize(MobKey mob) {
        java.util.Objects.requireNonNull(mob, "mob");
        return 1;
    }

    /**
     * Kinematic word of a participant as last sampled for a witness (M26.1). {@code 0} when this adapter has not
     * stored a sample. The engine keeps the long opaque. The default keeps captures on the previous steady reading.
     *
     * @param participant the enemy
     * @param previousStamp the word already stored, or {@code 0}
     * @param witness the member who sees the enemy, or the nil id
     * @return the packed word
     */
    default long enemyKinematics(java.util.UUID participant, long previousStamp, java.util.UUID witness) {
        java.util.Objects.requireNonNull(participant, "participant");
        java.util.Objects.requireNonNull(witness, "witness");
        return 0L;
    }

    /**
     * Parent, owner and children of one member that sit inside the encounter (M25). The default reads nothing.
     *
     * @param mob the member
     * @param inside identities of the encounter
     * @return the relations, or {@link GroupRelations#NONE}
     */
    default GroupRelations relations(MobKey mob, Set<UUID> inside) {
        java.util.Objects.requireNonNull(mob, "mob");
        java.util.Objects.requireNonNull(inside, "inside");
        return GroupRelations.NONE;
    }

    /**
     * Identity of the mob's threat table. {@code 0} when it has none. The same object shares one identity.
     *
     * @param mob the member
     * @return the identity, or 0
     */
    default int threatTableGroup(MobKey mob) {
        java.util.Objects.requireNonNull(mob, "mob");
        return 0;
    }

    /**
     * Whether this mob is a child on its summoner's threat table ({@code inheritthreattable}).
     *
     * @param mob the member
     * @return whether the child must not be reassigned
     */
    default boolean inheritsThreatTable(MobKey mob) {
        java.util.Objects.requireNonNull(mob, "mob");
        return false;
    }
}
