package akyrion.thepredict.adapter.server.context;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.EntityType;

import java.util.Optional;
import java.util.Set;

/**
 * The registries a participant selector is resolved against when a profile binds (M17.2): entity types, entity type
 * tags (vanilla or from a datapack) and the teams of the main scoreboard. Kept apart so the compilation is tested with
 * doubles (rule P4); the live implementation is {@link BukkitSelectorRegistry}. Server thread only.
 */
public interface SelectorRegistry {
    /**
     * Finds an entity type by key ({@code Registry.ENTITY_TYPE}).
     *
     * @param key key of the type
     * @return the type, empty when unknown
     */
    Optional<EntityType> entityType(NamespacedKey key);

    /**
     * Reads the values of an entity type tag ({@code Bukkit.getTag(Tag.REGISTRY_ENTITY_TYPES, key,
     * EntityType.class)}), at this moment (DT-39).
     *
     * @param key key of the tag
     * @return its types, empty when the tag is unknown
     */
    Optional<Set<EntityType>> entityTag(NamespacedKey key);

    /**
     * Lists the teams of the main scoreboard, to warn about names that differ only in case.
     *
     * @return the team names
     */
    Set<String> teamNames();
}
