package akyrion.thepredict.domain;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Parent, owner and whether this mob has children, read on capture (M25). Only identities inside the encounter
 * count. The children collection itself is never read.
 *
 * @param parent parent identity, empty when none was read
 * @param owner owner identity, empty when none was read
 * @param hasChildren whether a child identity was read
 */
public record GroupRelations(Optional<UUID> parent, Optional<UUID> owner, boolean hasChildren) {
    /** Nothing read. */
    public static final GroupRelations NONE = new GroupRelations(Optional.empty(), Optional.empty(), false);

    /**
     * Checks the relations.
     *
     * @throws NullPointerException if a component is null
     */
    public GroupRelations {
        Objects.requireNonNull(parent, "parent");
        Objects.requireNonNull(owner, "owner");
    }
}
