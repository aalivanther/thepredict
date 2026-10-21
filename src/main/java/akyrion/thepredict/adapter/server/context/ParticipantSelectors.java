package akyrion.thepredict.adapter.server.context;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.SpawnCategory;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * A compiled list of participant selectors (M17.2): what each native selector matches, resolved once per catalog
 * revision. {@code type:} and {@code tag:} become one {@link EnumSet} of entity types (a tag is dumped at that moment,
 * DT-39), {@code category:} an {@link EnumSet} of spawn categories, {@code kind:} a set of interfaces, and
 * {@code mythic:}, {@code faction:}, {@code team:}, {@code tagged:} and {@code predictor} sets of names. A selector
 * whose value does not resolve is reported with its path and ignored. Immutable; server thread only.
 *
 * <p>Cost per entity: the cheap part ({@link #matchesCheap}) is a few {@code EnumSet} lookups, {@code instanceof}
 * checks and one {@code Set.contains} per scoreboard tag selector; the rest ({@link #matches}) adds at most one O(1)
 * MythicMobs lookup and one scoreboard entry lookup.
 */
public final class ParticipantSelectors {
    /** Selectors that match nothing. */
    public static final ParticipantSelectors NONE = new ParticipantSelectors(EnumSet.noneOf(EntityType.class),
            EnumSet.noneOf(SpawnCategory.class), EnumSet.noneOf(Kind.class), false, Set.of(), Set.of(), Set.of(),
            Set.of(), false);

    /** The {@code kind:} selector: a native interface of the entity. */
    public enum Kind {
        /** Any {@code LivingEntity}. */
        LIVING,
        /** {@code org.bukkit.entity.Mob}. */
        MOB,
        /** {@code org.bukkit.entity.Enemy}, wider than {@code Monster}: slime, ghast, phantom, hoglin, shulker. */
        ENEMY,
        /** {@code org.bukkit.entity.Monster}. */
        MONSTER,
        /** {@code org.bukkit.entity.Animals}. */
        ANIMAL
    }

    private final Set<EntityType> types;
    private final Set<SpawnCategory> categories;
    private final Set<Kind> kinds;
    private final boolean anyMythic;
    private final Set<String> mythicTypes;
    private final Set<String> factions;
    private final Set<String> teams;
    private final Set<String> tagged;
    private final boolean predictor;

    private ParticipantSelectors(Set<EntityType> types, Set<SpawnCategory> categories, Set<Kind> kinds,
                                 boolean anyMythic, Set<String> mythicTypes, Set<String> factions, Set<String> teams,
                                 Set<String> tagged, boolean predictor) {
        this.types = types;
        this.categories = categories;
        this.kinds = kinds;
        this.anyMythic = anyMythic;
        this.mythicTypes = Set.copyOf(mythicTypes);
        this.factions = Set.copyOf(factions);
        this.teams = Set.copyOf(teams);
        this.tagged = Set.copyOf(tagged);
        this.predictor = predictor;
    }

    /**
     * Compiles selectors whose syntax {@code config} already checked.
     *
     * @param selectors selector texts
     * @param paths path of each selector, for the warnings
     * @param registry entity types, tags and teams of the server
     * @param warnings receives the path and the reason of each selector that resolves to nothing
     * @return the compiled selectors
     */
    public static ParticipantSelectors compile(List<String> selectors, List<String> paths, SelectorRegistry registry,
                                               BiConsumer<String, String> warnings) {
        var builder = new Builder(registry, warnings);
        for (var index = 0; index < selectors.size(); index++) {
            builder.add(selectors.get(index), paths.get(index));
        }
        return new ParticipantSelectors(builder.types, builder.categories, builder.kinds, builder.anyMythic,
                builder.mythic, builder.factions, builder.teams, builder.tagged, builder.predictor);
    }

    /**
     * Tells whether the list has no selector that resolved.
     *
     * @return {@code true} when nothing can ever match
     */
    public boolean isEmpty() {
        return types.isEmpty() && categories.isEmpty() && kinds.isEmpty() && !anyMythic && mythicTypes.isEmpty()
                && factions.isEmpty() && teams.isEmpty() && tagged.isEmpty() && !predictor;
    }

    /**
     * Tells whether some selector needs MythicMobs or the scoreboard, so the cheap test cannot rule an entity out.
     *
     * @return {@code true} with a {@code mythic:}, {@code faction:}, {@code team:} or {@code predictor} selector
     */
    public boolean needsLookup() {
        return anyMythic || !mythicTypes.isEmpty() || !factions.isEmpty() || !teams.isEmpty() || predictor;
    }

    /**
     * Matches the cheap selectors: type, tag, category, kind and scoreboard tag.
     *
     * @param entity candidate
     * @return {@code true} if one of them matches
     */
    public boolean matchesCheap(LivingEntity entity) {
        if (!types.isEmpty() && types.contains(entity.getType())) {
            return true;
        }
        if (!categories.isEmpty() && categories.contains(entity.getSpawnCategory())) {
            return true;
        }
        for (var kind : kinds) {
            if (kind(entity, kind)) {
                return true;
            }
        }
        if (!tagged.isEmpty()) {
            for (var tag : entity.getScoreboardTags()) {
                if (tagged.contains(tag)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Matches every selector: the cheap ones first, then MythicMobs and the scoreboard.
     *
     * @param entity candidate
     * @param lookup native identities
     * @return {@code true} if one selector matches
     */
    public boolean matches(LivingEntity entity, ParticipantLookup lookup) {
        if (matchesCheap(entity)) {
            return true;
        }
        if (!needsLookup()) {
            return false;
        }
        var id = entity.getUniqueId();
        if (anyMythic || !mythicTypes.isEmpty()) {
            var type = lookup.mythicType(id);
            if (type.isPresent() && (anyMythic || mythicTypes.contains(type.get()))) {
                return true;
            }
        }
        if (!factions.isEmpty() && lookup.faction(id).filter(faction -> factions.contains(faction
                .toLowerCase(Locale.ROOT))).isPresent()) {
            return true;
        }
        if (!teams.isEmpty() && lookup.team(entity).filter(team -> teams.contains(team.toLowerCase(Locale.ROOT)))
                .isPresent()) {
            return true;
        }
        return predictor && lookup.predictor(id);
    }

    /** Collects the resolved values of the selectors of one list. */
    private static final class Builder {
        private final SelectorRegistry registry;
        private final BiConsumer<String, String> warnings;
        private final Set<EntityType> types = EnumSet.noneOf(EntityType.class);
        private final Set<SpawnCategory> categories = EnumSet.noneOf(SpawnCategory.class);
        private final Set<Kind> kinds = EnumSet.noneOf(Kind.class);
        private final Set<String> mythic = new HashSet<>();
        private final Set<String> factions = new HashSet<>();
        private final Set<String> teams = new HashSet<>();
        private final Set<String> tagged = new HashSet<>();
        private boolean anyMythic;
        private boolean predictor;

        private Builder(SelectorRegistry registry, BiConsumer<String, String> warnings) {
            this.registry = registry;
            this.warnings = warnings;
        }

        private void add(String selector, String path) {
            if (selector.equals("predictor")) {
                predictor = true;
                return;
            }
            var colon = selector.indexOf(':');
            var prefix = colon < 0 ? selector : selector.substring(0, colon);
            var value = colon < 0 ? "" : selector.substring(colon + 1);
            switch (prefix) {
                case "type" -> type(value, registry, types, path, warnings);
                case "tag" -> tag(value, registry, types, path, warnings);
                case "category" -> category(value, categories, path, warnings);
                case "kind" -> kind(value, kinds, path, warnings);
                case "mythic" -> mythic(value);
                case "faction" -> factions.add(value.toLowerCase(Locale.ROOT));
                case "team" -> {
                    teams.add(value.toLowerCase(Locale.ROOT));
                    ambiguousTeam(value, registry, path, warnings);
                }
                case "tagged" -> tagged.add(value);
                default -> warnings.accept(path, "unknown selector '" + selector + "' is ignored");
            }
        }

        private void mythic(String value) {
            if (value.equals("*")) {
                anyMythic = true;
            } else {
                mythic.add(value);
            }
        }
    }

    private static boolean kind(LivingEntity entity, Kind kind) {
        return switch (kind) {
            case LIVING -> true;
            case MOB -> entity instanceof Mob;
            case ENEMY -> entity instanceof Enemy;
            case MONSTER -> entity instanceof Monster;
            case ANIMAL -> entity instanceof Animals;
        };
    }

    private static void type(String value, SelectorRegistry registry, Set<EntityType> types, String path,
                             BiConsumer<String, String> warnings) {
        var key = NamespacedKey.fromString(value);
        var type = key == null ? Optional.<EntityType>empty() : registry.entityType(key);
        if (type.isEmpty()) {
            warnings.accept(path, "unknown entity type '" + value + "' is ignored");
        } else if (!type.get().isAlive()) {
            warnings.accept(path, "entity type '" + value + "' is not a living entity and is ignored");
        } else {
            types.add(type.get());
        }
    }

    private static void tag(String value, SelectorRegistry registry, Set<EntityType> types, String path,
                            BiConsumer<String, String> warnings) {
        var key = NamespacedKey.fromString(value);
        var tag = key == null ? Optional.<Set<EntityType>>empty() : registry.entityTag(key);
        if (tag.isEmpty()) {
            warnings.accept(path, "unknown entity type tag '" + value + "' is ignored; after /minecraft:reload run "
                    + "/mm reload");
        } else {
            types.addAll(tag.get());
        }
    }

    private static void category(String value, Set<SpawnCategory> categories, String path,
                                 BiConsumer<String, String> warnings) {
        try {
            categories.add(SpawnCategory.valueOf(value.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException exception) {
            warnings.accept(path, "unknown spawn category '" + value + "' is ignored");
        }
    }

    private static void kind(String value, Set<Kind> kinds, String path, BiConsumer<String, String> warnings) {
        try {
            kinds.add(Kind.valueOf(value.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException exception) {
            warnings.accept(path, "unknown kind '" + value + "' is ignored");
        }
    }

    private static void ambiguousTeam(String value, SelectorRegistry registry, String path,
                                      BiConsumer<String, String> warnings) {
        var matching = registry.teamNames().stream().filter(name -> name.equalsIgnoreCase(value)).count();
        if (matching > 1) {
            warnings.accept(path, "team '" + value + "' matches " + matching + " teams that differ only in case; all"
                    + " of them take part");
        }
    }
}
