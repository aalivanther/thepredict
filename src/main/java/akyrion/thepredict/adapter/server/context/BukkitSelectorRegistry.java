package akyrion.thepredict.adapter.server.context;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Tag;
import org.bukkit.entity.EntityType;
import org.bukkit.scoreboard.Team;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * {@link SelectorRegistry} over Bukkit (M17.2), read only when a profile binds with an entity selector. Evidence
 * (javap, {@code paper-api} 26.2): {@code Registry.ENTITY_TYPE}, {@code Bukkit.getTag(String, NamespacedKey, Class)}
 * with {@code Tag.REGISTRY_ENTITY_TYPES}, {@code Tag#getValues()}, {@code Scoreboard#getTeams()}. Server thread only.
 */
public final class BukkitSelectorRegistry implements SelectorRegistry {
    /** Creates the registry; it holds no state. */
    public BukkitSelectorRegistry() {
    }

    @Override
    public Optional<EntityType> entityType(NamespacedKey key) {
        return Optional.ofNullable(Registry.ENTITY_TYPE.get(key));
    }

    @Override
    public Optional<Set<EntityType>> entityTag(NamespacedKey key) {
        var tag = Bukkit.getTag(Tag.REGISTRY_ENTITY_TYPES, key, EntityType.class);
        return tag == null ? Optional.empty() : Optional.of(Set.copyOf(tag.getValues()));
    }

    @Override
    public Set<String> teamNames() {
        var manager = Bukkit.getScoreboardManager();
        var names = new HashSet<String>();
        if (manager == null) {
            return names;
        }
        for (Team team : manager.getMainScoreboard().getTeams()) {
            names.add(team.getName());
        }
        return names;
    }
}
