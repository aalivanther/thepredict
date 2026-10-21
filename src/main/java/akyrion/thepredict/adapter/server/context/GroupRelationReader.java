package akyrion.thepredict.adapter.server.context;

import akyrion.thepredict.domain.GroupRelations;
import io.lumine.mythic.core.mobs.ActiveMob;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Parent, owner and children of one mob, by UUID only (M25). {@code getChildren()} is never called.
 * Identities outside the encounter are dropped.
 */
public final class GroupRelationReader {
    private GroupRelationReader() {
    }

    /**
     * Reads one active mob. The children collection is {@code getChildrenUUID()}.
     *
     * @param mob the MythicMobs mob
     * @param inside identities that belong to the encounter
     * @return the relations that count
     */
    public static GroupRelations read(ActiveMob mob, Set<UUID> inside) {
        Objects.requireNonNull(mob, "mob");
        var parent = mob.getParentUUID() == null ? null : mob.getParentUUID().orElse(null);
        var owner = mob.getOwnerUUID() == null ? null : mob.getOwnerUUID().orElse(null);
        return read(parent, owner, mob.getChildrenUUID(), inside);
    }

    /**
     * Keeps only identities inside the encounter.
     *
     * @param parent parent identity, or null
     * @param owner owner identity, or null
     * @param children child identities, or null
     * @param inside identities that belong to the encounter
     * @return the relations that count
     */
    public static GroupRelations read(UUID parent, UUID owner, Collection<UUID> children, Set<UUID> inside) {
        Objects.requireNonNull(inside, "inside");
        var keptParent = parent != null && inside.contains(parent) ? Optional.of(parent) : Optional.<UUID>empty();
        var keptOwner = owner != null && inside.contains(owner) ? Optional.of(owner) : Optional.<UUID>empty();
        var hasChildren = false;
        if (children != null) {
            for (var child : children) {
                if (child != null && inside.contains(child)) {
                    hasChildren = true;
                    break;
                }
            }
        }
        return new GroupRelations(keptParent, keptOwner, hasChildren);
    }
}
