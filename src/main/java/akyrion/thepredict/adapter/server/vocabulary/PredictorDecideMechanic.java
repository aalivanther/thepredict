package akyrion.thepredict.adapter.server.vocabulary;

import akyrion.thepredict.engine.ContextSignals;
import io.lumine.mythic.api.skills.INoTargetSkill;
import io.lumine.mythic.api.skills.SkillMetadata;
import io.lumine.mythic.api.skills.SkillResult;
import io.lumine.mythic.api.skills.ThreadSafetyLevel;

import java.util.Objects;

/**
 * {@code predictordecide}: asks for a re-evaluation of the caster from a native trigger, for example
 * {@code - predictordecide @self ~onDamaged} (M04.3).
 *
 * <p>It only offers the caster's UUID to {@link ContextSignals}; the coordinator (M09) decides later
 * whether there is budget. It never sends requests, never waits, never blocks and never throws: a
 * missing caster or entity is ignored, and an offer dropped because the queue is full is counted in
 * {@link ContextSignals#droppedCount()}. It always returns {@link SkillResult#SUCCESS}. Safe on any
 * thread (rule T3); the UUID read follows the note in {@link IntentCondition}.
 */
public final class PredictorDecideMechanic implements INoTargetSkill {
    /** Name of the mechanic in MythicMobs lines, compared without case (rule I8). */
    public static final String NAME = "predictordecide";

    private final ContextSignals signals;

    /**
     * Creates the mechanic.
     *
     * @param signals queue of mobs pending re-evaluation
     */
    public PredictorDecideMechanic(ContextSignals signals) {
        this.signals = Objects.requireNonNull(signals, "signals");
    }

    /**
     * Marks the caster for re-evaluation.
     *
     * @param data skill metadata with the caster
     * @return always {@link SkillResult#SUCCESS}, even when the queue is full
     */
    @Override
    public SkillResult cast(SkillMetadata data) {
        var caster = data == null ? null : data.getCaster();
        var entity = caster == null ? null : caster.getEntity();
        var entityId = entity == null ? null : entity.getUniqueId();
        if (entityId != null) {
            signals.offer(entityId);
        }
        return SkillResult.SUCCESS;
    }

    /**
     * Declares explicitly that MythicMobs may run the mechanic on any thread.
     *
     * @return {@link ThreadSafetyLevel#EITHER}
     */
    @Override
    public ThreadSafetyLevel getThreadSafetyLevel() {
        return ThreadSafetyLevel.EITHER;
    }
}
