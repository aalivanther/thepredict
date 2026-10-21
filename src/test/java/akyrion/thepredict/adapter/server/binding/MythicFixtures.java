package akyrion.thepredict.adapter.server.binding;

import io.lumine.mythic.api.config.MythicConfig;
import io.lumine.mythic.api.mobs.MythicMob;
import io.lumine.mythic.api.skills.Skill;
import io.lumine.mythic.core.skills.SkillCondition;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Doubles of the MythicMobs Premium interfaces used by the binder; unexpected calls fail the test. */
public final class MythicFixtures {
    private MythicFixtures() {
    }

    /**
     * Creates a mob whose configuration answers from a map of YAML paths.
     *
     * @param id internal mob ID
     * @param entityType MythicMobs {@code Type}
     * @param config values by path, for example {@code Predictor} or {@code Skills}
     * @return the mob double
     */
    public static MythicMob mob(String id, String entityType, Map<String, Object> config) {
        var configuration = config(config);
        return proxy(MythicMob.class, (instance, method, args) -> switch (method.getName()) {
            case "getInternalName" -> id;
            case "getConfig" -> configuration;
            case "getEntityTypeString" -> entityType;
            default -> throw new UnsupportedOperationException("Unexpected MythicMob call: " + method.getName());
        });
    }

    /**
     * Creates a skill whose configuration answers from a map of YAML paths.
     *
     * @param id internal skill ID
     * @param config values by path, for example {@code PredictValues} or {@code Cooldown}
     * @return the skill double
     */
    public static Skill skill(String id, Map<String, Object> config) {
        var configuration = config(config);
        return proxy(Skill.class, (instance, method, args) -> switch (method.getName()) {
            case "getInternalName" -> id;
            case "getConfig" -> configuration;
            default -> throw new UnsupportedOperationException("Unexpected Skill call: " + method.getName());
        });
    }

    /**
     * Creates a mutable lookup over mobs, skills and condition lines.
     *
     * @return an empty lookup
     */
    public static Definitions definitions() {
        return new Definitions();
    }

    private static MythicConfig config(Map<String, Object> values) {
        return proxy(MythicConfig.class, (instance, method, args) -> switch (method.getName()) {
            case "isSet" -> values.containsKey((String) args[0]);
            case "isList" -> values.get((String) args[0]) instanceof List<?>;
            case "getBooleanNullable" -> values.get((String) args[0]) instanceof Boolean value ? value : null;
            case "getBoolean" -> values.get((String) args[0]) instanceof Boolean value ? value : args[1];
            case "getString" -> values.get((String) args[0]) instanceof String value ? value : args[1];
            case "getStringList" -> values.get((String) args[0]) instanceof List<?> list
                    ? list.stream().map(String::valueOf).toList() : List.of();
            case "getFileName" -> "test.yml";
            default -> throw new UnsupportedOperationException("Unexpected MythicConfig call: " + method.getName());
        });
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
    }

    /** Mutable definitions: tests add mobs and skills and mark condition lines invalid or unresolved. */
    public static final class Definitions implements MythicDefinitionLookup {
        private final Map<String, MythicMob> mobs = new LinkedHashMap<>();
        private final Map<String, Skill> skills = new LinkedHashMap<>();
        private final Map<String, NativeSkillReader.Reading> readings = new HashMap<>();
        private final Set<String> invalidLines = new HashSet<>();
        private final Set<String> unresolvedLines = new HashSet<>();

        private Definitions() {
        }

        /**
         * Adds or replaces a mob.
         *
         * @param mob the mob double
         * @return this lookup
         */
        public Definitions add(MythicMob mob) {
            mobs.put(mob.getInternalName(), mob);
            return this;
        }

        /**
         * Adds or replaces a skill.
         *
         * @param skill the skill double
         * @return this lookup
         */
        public Definitions add(Skill skill) {
            skills.put(skill.getInternalName(), skill);
            return this;
        }

        /**
         * Removes a skill.
         *
         * @param id skill ID
         * @return this lookup
         */
        public Definitions removeSkill(String id) {
            skills.remove(id);
            readings.remove(id);
            return this;
        }

        /**
         * Stores the reading of a skill double that cannot be a metaskill (M19.3).
         *
         * @param id skill ID
         * @param view lines the reader would see on a metaskill
         * @return this lookup
         */
        public Definitions prepare(String id, NativeSkillView view) {
            readings.put(id, NativeSkillReader.read(id, view));
            return this;
        }

        /**
         * Marks a condition line as rejected by MythicMobs.
         *
         * @param line condition line
         * @return this lookup
         */
        public Definitions invalid(String line) {
            invalidLines.add(line);
            return this;
        }

        /**
         * Marks a condition line as a custom condition that no plugin resolves.
         *
         * @param line condition line
         * @return this lookup
         */
        public Definitions unresolved(String line) {
            unresolvedLines.add(line);
            return this;
        }

        @Override
        public Optional<MythicMob> mob(String id) {
            return Optional.ofNullable(mobs.get(id));
        }

        @Override
        public Collection<MythicMob> mobs() {
            return List.copyOf(mobs.values());
        }

        @Override
        public Optional<Skill> skill(String id) {
            return Optional.ofNullable(skills.get(id));
        }

        @Override
        public Optional<NativeSkillReader.Reading> preparedReading(String skillId) {
            return Optional.ofNullable(readings.get(skillId));
        }

        @Override
        public Collection<String> skillNames() {
            return List.copyOf(skills.keySet());
        }

        @Override
        public NativeConditions conditions(List<String> lines) {
            var invalid = lines.stream().filter(invalidLines::contains).toList();
            var unresolved = lines.stream().filter(unresolvedLines::contains).toList();
            return new NativeConditions() {
                @Override
                public List<String> lines() {
                    return lines;
                }

                @Override
                public List<SkillCondition> conditions() {
                    return List.of();
                }

                @Override
                public List<String> invalidLines() {
                    return invalid;
                }

                @Override
                public List<String> unresolvedLines() {
                    return unresolved;
                }
            };
        }
    }
}
