package akyrion.thepredict.domain;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Resolved stance section of one profile (M23.2). Empty maps use the defaults.
 *
 * @param nativeMode how the stance is shared
 * @param minDwellTicks ticks a base must hold
 * @param hysteresisCycles reactive cycles a new base must hold
 * @param casts roles allowed in each base; empty allows every role
 * @param byMovement base of a movement; empty uses {@link BaseStance#fromMovement}
 * @param enragedContexts context ids that mark enraged
 * @param enragedNative author stance names that mark enraged
 * @param describe whether the question text names the stance
 */
public record StanceSettings(StanceNativeMode nativeMode, int minDwellTicks, int hysteresisCycles,
                             Map<BaseStance, List<SkillRole>> casts, Map<Movement, BaseStance> byMovement,
                             List<String> enragedContexts, List<String> enragedNative, boolean describe) {
    /** Installation defaults: hybrid, 20 ticks, 2 cycles, nothing else. */
    public static final StanceSettings DEFAULT = new StanceSettings(StanceNativeMode.DEFAULT, 20, 2, Map.of(),
            Map.of(), List.of(), List.of(), false);

    /**
     * Validates and copies the maps.
     *
     * @throws NullPointerException if a component is null
     * @throws IllegalArgumentException if a range fails
     */
    public StanceSettings {
        Objects.requireNonNull(nativeMode, "nativeMode");
        DomainChecks.requireRange(minDwellTicks, 4, 200, "minDwellTicks");
        DomainChecks.requireRange(hysteresisCycles, 1, 7, "hysteresisCycles");
        casts = copyCasts(casts);
        byMovement = Map.copyOf(Objects.requireNonNull(byMovement, "byMovement"));
        enragedContexts = List.copyOf(Objects.requireNonNull(enragedContexts, "enragedContexts"));
        enragedNative = List.copyOf(Objects.requireNonNull(enragedNative, "enragedNative"));
    }

    /**
     * Base a movement asks for.
     *
     * @param movement the movement
     * @return the override or the default
     */
    public BaseStance baseOf(Movement movement) {
        return byMovement.getOrDefault(movement, BaseStance.fromMovement(movement));
    }

    /**
     * Whether a role may be cast in this base. The intent choice is exempt at the call site.
     *
     * @param base current base
     * @param role primary role
     * @return whether the filter allows it
     */
    public boolean allows(BaseStance base, SkillRole role) {
        var allowed = casts.get(base);
        return allowed == null || allowed.contains(role);
    }

    private static Map<BaseStance, List<SkillRole>> copyCasts(Map<BaseStance, List<SkillRole>> source) {
        Objects.requireNonNull(source, "casts");
        var copy = new EnumMap<BaseStance, List<SkillRole>>(BaseStance.class);
        source.forEach((base, roles) -> copy.put(Objects.requireNonNull(base, "casts"),
                List.copyOf(Objects.requireNonNull(roles, "casts"))));
        return Map.copyOf(copy);
    }
}
