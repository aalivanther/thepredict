package akyrion.thepredict.domain;

/**
 * The only source of the wind-up {@code w} and of the wait {@code h > w} (M20.4). The engine and the adapter both
 * call these methods; neither computes {@code max(windUp)} on its own.
 */
public final class ImpactTiming {
    private ImpactTiming() {
    }

    /**
     * Ticks until a target can be hit. {@code floor(maximum / 2)} matches the float comparison of vanilla, including
     * an odd maximum.
     *
     * @param noDamageTicks current no-damage ticks
     * @param maximumNoDamageTicks maximum no-damage ticks
     * @return {@code max(0, noDamageTicks - floor(maximum / 2))}, not clamped
     */
    public static int ticksUntilHittable(int noDamageTicks, int maximumNoDamageTicks) {
        var half = Math.floorDiv(maximumNoDamageTicks, 2);
        var ticks = noDamageTicks - half;
        return ticks < 0 ? 0 : ticks;
    }

    /**
     * Effective wind-up of a typed choice: the larger of the purpose and the native facts.
     *
     * @param purpose authored or inferred purpose
     * @param facts facts read from the mechanic
     * @return ticks
     */
    public static int effectiveWindUpTicks(SkillPurpose purpose, NativeSkillFacts facts) {
        var nativeWind = facts.windUpTicks().orElse(0);
        return purpose.windUpTicks() > nativeWind ? purpose.windUpTicks() : nativeWind;
    }

    /**
     * Wind-up of a choice that has no purpose (errata E20-6): the native facts, and the damage tier is
     * {@link DamageTier#UNRATED} for whoever asks. Optional is not a parameter (rule C4).
     *
     * @param facts facts read from the mechanic
     * @return ticks, zero when the facts declare none
     */
    public static int effectiveWindUpTicks(NativeSkillFacts facts) {
        return facts.windUpOrZero();
    }

    /**
     * Whether a damaging cast must wait.
     *
     * @param hittableTicks ticks until the target can be hit
     * @param windUpTicks effective wind-up
     * @return {@code hittableTicks > windUpTicks}
     */
    public static boolean mustWait(int hittableTicks, int windUpTicks) {
        return hittableTicks > windUpTicks;
    }
}
