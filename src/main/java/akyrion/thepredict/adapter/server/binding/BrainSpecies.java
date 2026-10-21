package akyrion.thepredict.adapter.server.binding;

import org.bukkit.entity.EntityType;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Species whose vanilla AI is a {@code Brain} instead of a goal selector (plan section 2). ThePredict does
 * not govern their movement, so their tactics may only use {@code Movement: NONE} (M03.4) and the goal
 * installer never gives them goals (M05.6). This is the only list of these species.
 */
public final class BrainSpecies {
    /** Every species with a {@code Brain} in Paper 26.2, from plan section 2. */
    public static final Set<EntityType> SPECIES = Set.copyOf(EnumSet.of(EntityType.ALLAY, EntityType.ARMADILLO,
            EntityType.AXOLOTL, EntityType.CAMEL, EntityType.FROG, EntityType.TADPOLE, EntityType.GOAT,
            EntityType.COPPER_GOLEM, EntityType.HAPPY_GHAST, EntityType.NAUTILUS, EntityType.ZOMBIE_NAUTILUS,
            EntityType.SNIFFER, EntityType.ZOGLIN, EntityType.BREEZE, EntityType.CREAKING, EntityType.HOGLIN,
            EntityType.PIGLIN, EntityType.PIGLIN_BRUTE, EntityType.WARDEN, EntityType.VILLAGER));

    private static final Set<String> NORMALIZED = normalizedNames();

    private BrainSpecies() {
    }

    /**
     * Tells whether a live entity type has a {@code Brain}. Server thread or any thread; pure.
     *
     * @param entityType Bukkit type of the entity
     * @return {@code true} for a species with a {@code Brain}
     */
    public static boolean contains(EntityType entityType) {
        return SPECIES.contains(Objects.requireNonNull(entityType, "entityType"));
    }

    /**
     * Tells whether a MythicMobs {@code Type} names a species with a {@code Brain}. Case, underscores,
     * spaces and a {@code BABY} prefix are ignored, because M01 still has to confirm the exact form.
     *
     * @param entityType MythicMobs entity type, for example {@code PIGLIN_BRUTE} or {@code piglin}
     * @return {@code true} for a species with a {@code Brain}
     */
    public static boolean contains(String entityType) {
        Objects.requireNonNull(entityType, "entityType");
        var normalized = normalize(entityType);
        if (normalized.startsWith("BABY")) {
            normalized = normalized.substring("BABY".length());
        }
        return NORMALIZED.contains(normalized);
    }

    private static String normalize(String text) {
        return text.toUpperCase(Locale.ROOT).replace("_", "").replace(" ", "").replace("-", "");
    }

    private static Set<String> normalizedNames() {
        var names = new HashSet<String>();
        for (var species : SPECIES) {
            names.add(normalize(species.name()));
        }
        return Set.copyOf(names);
    }
}
