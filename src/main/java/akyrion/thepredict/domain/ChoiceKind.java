package akyrion.thepredict.domain;

/** How a skill choice is executed. */
public enum ChoiceKind {
    /** Casts a MythicMobs skill by its exact ID. */
    MYTHIC_SKILL,
    /** Performs a vanilla melee attack with {@code LivingEntity#attack}. */
    VANILLA_MELEE
}
