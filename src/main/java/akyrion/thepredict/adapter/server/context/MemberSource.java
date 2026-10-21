package akyrion.thepredict.adapter.server.context;

import akyrion.thepredict.adapter.server.binding.BoundProfile;
import akyrion.thepredict.domain.GroupRelations;
import akyrion.thepredict.domain.ThreatTableState;
import io.lumine.mythic.api.skills.Skill;
import org.bukkit.entity.LivingEntity;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;

/**
 * What {@link CombatCapture} reads from MythicMobs about a member mob, kept apart so the capture is tested with
 * doubles (rule P4). The live implementation is {@link MythicMemberSource}. Server thread only; every lookup is
 * O(1) by UUID.
 */
public interface MemberSource {
    /**
     * A member with an active MythicMobs instance, a valid living entity and a bound profile.
     *
     * @param entity living entity of the mob
     * @param profile profile bound to its MythicMobs type in the current catalog
     */
    record Member(LivingEntity entity, BoundProfile profile) {
        /**
         * Validates the components.
         *
         * @throws NullPointerException if a component is {@code null}
         */
        public Member {
            Objects.requireNonNull(entity, "entity");
            Objects.requireNonNull(profile, "profile");
        }
    }

    /**
     * Resolves a member.
     *
     * @param mobId UUID of the mob
     * @return the member, empty when the instance is inactive, the entity invalid or dead, or no profile is bound
     */
    Optional<Member> member(UUID mobId);

    /**
     * Author stance ({@code ActiveMob.getStance}). Empty when the mob is absent or the stance is blank.
     * The default reads nothing.
     *
     * @param mobId UUID of the mob
     * @return the stance, or empty
     */
    default Optional<String> authorStance(UUID mobId) {
        Objects.requireNonNull(mobId, "mobId");
        return Optional.empty();
    }

    /**
     * Reads a mob variable as text. Empty when the variable is absent. The default reads nothing.
     *
     * @param mobId UUID of the mob
     * @param key variable name
     * @return the text, or empty
     */
    default Optional<String> textVariable(UUID mobId, String key) {
        Objects.requireNonNull(mobId, "mobId");
        Objects.requireNonNull(key, "key");
        return Optional.empty();
    }

    /**
     * Reads a mob variable as a number. Empty when the variable is absent. The default reads nothing.
     *
     * @param mobId UUID of the mob
     * @param key variable name
     * @return the number, or empty
     */
    default OptionalDouble numberVariable(UUID mobId, String key) {
        Objects.requireNonNull(mobId, "mobId");
        Objects.requireNonNull(key, "key");
        return OptionalDouble.empty();
    }

    /**
     * Finds the target the way the capture of 0.0.1 did, for any living entity (M17.5): the top threat holder of the
     * MythicMobs threat table if the mob has one, otherwise {@code Mob#getTarget()}. A player decides as in 0.0.1 when
     * it is valid, alive and in the mob's world (its game mode is checked by the capture); any other living entity only
     * when the participant policy of the mob's profile admits it and it is in the mob's world.
     *
     * @param mobId UUID of the mob
     * @return the target, if any
     */
    Optional<LivingEntity> target(UUID mobId);

    /**
     * Reads the MythicMobs threat of a participant on a mob.
     *
     * @param mobId UUID of the mob
     * @param participantId UUID of the participant
     * @return threat, {@code 0} without a threat table or entry
     */
    double threat(UUID mobId, UUID participantId);

    /**
     * Tells whether a skill is on its native MythicMobs cooldown for the mob.
     *
     * @param mobId UUID of the mob
     * @param skill loaded skill of a choice
     * @return {@code true} while on cooldown, or when the mob is not active
     */
    boolean onCooldown(UUID mobId, Skill skill);

    /**
     * Remaining native cooldown in milliseconds. {@code 0} when a charge remains. {@link Long#MAX_VALUE} when
     * MythicMobs has no pending recharge. Empty when the skill is not an {@code AbstractSkill} or this source does not
     * read the timer (the capture then treats a cooling skill as outside every horizon).
     *
     * @param mobId UUID of the mob
     * @param skill loaded skill of a choice
     * @return milliseconds, or empty
     */
    default OptionalLong remainingMillis(UUID mobId, Skill skill) {
        Objects.requireNonNull(mobId, "mobId");
        Objects.requireNonNull(skill, "skill");
        return OptionalLong.empty();
    }

    /**
     * Tells whether the mob's MythicMobs threat table has an entry, its native combat state.
     *
     * @param mobId UUID of the mob
     * @return {@code true} if the mob has a non-empty threat table
     */
    boolean threatInCombat(UUID mobId);

    /**
     * Tells whether the mob has an active MythicMobs instance with a valid, living entity (M06.6).
     *
     * @param mobId UUID of the mob
     * @return {@code true} if active
     */
    boolean isActive(UUID mobId);

    /**
     * Reads the state of the mob's MythicMobs threat table, with no player identity (M12.2).
     *
     * @param mobId UUID of the mob
     * @return the state, {@link ThreatTableState#DISABLED} without a table; empty when the mob is not active
     */
    Optional<ThreatTableState> threatTable(UUID mobId);

    /**
     * Parent, owner and children inside the encounter. The default reads nothing.
     *
     * @param mobId UUID of the mob
     * @param inside identities of the encounter
     * @return the relations
     */
    default GroupRelations relations(UUID mobId, Set<UUID> inside) {
        Objects.requireNonNull(mobId, "mobId");
        Objects.requireNonNull(inside, "inside");
        return GroupRelations.NONE;
    }

    /**
     * Identity of the threat table object. {@code 0} without a table.
     *
     * @param mobId UUID of the mob
     * @return the identity
     */
    default int threatTableGroup(UUID mobId) {
        Objects.requireNonNull(mobId, "mobId");
        return 0;
    }

    /**
     * Whether the mob shares its parent's threat table.
     *
     * @param mobId UUID of the mob
     * @return whether a child with {@code inheritthreattable} must not be reassigned
     */
    default boolean inheritsThreatTable(UUID mobId) {
        Objects.requireNonNull(mobId, "mobId");
        return false;
    }
}
