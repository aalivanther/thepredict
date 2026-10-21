package akyrion.thepredict.adapter.server.goals;

import com.destroystokyo.paper.entity.ai.GoalKey;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Mob;
import org.bukkit.plugin.Plugin;

import java.util.Objects;

/**
 * Keys of ThePredict's Paper goals (rule I10): {@code <plugin>:hold}, {@code :flank}, {@code :owner} and
 * {@code :micro}.
 *
 * @param hold key of {@link HoldGoal}
 * @param flank key of {@link FlankGoal}
 * @param owner key of {@link OwnerMarkerGoal}
 * @param micro key of {@link MicroGoal}
 */
public record GoalKeys(GoalKey<Mob> hold, GoalKey<Mob> flank, GoalKey<Mob> owner, GoalKey<Mob> micro) {
    /** Rejects missing keys. */
    public GoalKeys {
        Objects.requireNonNull(hold, "hold");
        Objects.requireNonNull(flank, "flank");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(micro, "micro");
    }

    /**
     * Whether a key is one of ThePredict's four goals, so a count of MythicMobs goals or a removal never forgets one.
     *
     * @param key key of a goal found on a mob
     * @return whether the goal is ThePredict's
     */
    public boolean owns(GoalKey<Mob> key) {
        return key.equals(hold) || key.equals(flank) || key.equals(owner) || key.equals(micro);
    }

    /**
     * Builds the keys in the plugin's namespace.
     *
     * @param plugin ThePredict plugin
     * @return the four goal keys
     */
    public static GoalKeys forPlugin(Plugin plugin) {
        return new GoalKeys(GoalKey.of(Mob.class, new NamespacedKey(plugin, "hold")),
                GoalKey.of(Mob.class, new NamespacedKey(plugin, "flank")),
                GoalKey.of(Mob.class, new NamespacedKey(plugin, "owner")),
                GoalKey.of(Mob.class, new NamespacedKey(plugin, "micro")));
    }
}
