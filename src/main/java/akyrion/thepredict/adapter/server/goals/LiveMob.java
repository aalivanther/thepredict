package akyrion.thepredict.adapter.server.goals;

import io.lumine.mythic.api.mobs.MythicMob;
import org.bukkit.entity.Mob;

import java.util.Objects;

/**
 * A resolved bound mob: its Paper entity, its MythicMobs type and how to give it back its MythicMobs goals.
 * Package-private; built on the server thread by {@link GoalOwnership} and by tests with doubles.
 *
 * @param entity live Paper mob
 * @param type MythicMobs mob type
 * @param restoreMythicGoals reapplies the mob's {@code AIGoalSelectors}
 */
record LiveMob(Mob entity, MythicMob type, Runnable restoreMythicGoals) {
    LiveMob {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(restoreMythicGoals, "restoreMythicGoals");
    }
}
