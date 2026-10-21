package akyrion.thepredict.engine;

import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.EnemyPattern;
import akyrion.thepredict.domain.EnemyTokenizer;
import akyrion.thepredict.domain.PatternFactFilter;
import akyrion.thepredict.domain.PatternSettings;
import akyrion.thepredict.domain.PatternState;

import java.util.UUID;

/**
 * Enemy models of one encounter (M26.3): one per participant its members fight, at most {@link #CAPACITY}, the
 * participant cap of an encounter (A5). A model starts empty the first time its participant is seen in this encounter
 * and goes with the encounter, so a fight never inherits the habits another fight saw (A14: the model is the
 * encounter's). A full set replaces the participant seen longest ago. Each slot keeps the tokenizer of its model and
 * the hysteresis of the facts it may show (M26.7). Server thread only; allocates only when a slot is filled.
 */
final class PatternModels {
    /** Participants of one encounter. */
    static final int CAPACITY = EncounterRegistry.MAX_PARTICIPANTS;

    private static final EnemyPattern[] PATTERNS = EnemyPattern.values();

    private final EncounterId encounter;
    private final Encounter open;
    private final UUID[] ids = new UUID[CAPACITY];
    private final EnemyModel[] models = new EnemyModel[CAPACITY];
    private final EnemyTokenizer[] tokenizers = new EnemyTokenizer[CAPACITY];
    private final PatternFactFilter[] facts = new PatternFactFilter[CAPACITY];
    private final int[] shown = new int[CAPACITY];
    private final long[] factTick = new long[CAPACITY];
    private final long[] seen = new long[CAPACITY];
    private int count;

    /**
     * An empty set.
     *
     * @param encounter the encounter that owns it, or {@code null} for sessions outside any encounter
     * @param open the open encounter, or {@code null} when it is not known; it says who is a participant
     */
    PatternModels(EncounterId encounter, Encounter open) {
        this.encounter = encounter;
        this.open = open;
    }

    /**
     * Whether a target may have a model here: a participant of the encounter, or any target while the encounter is not
     * known (A14: the models are the encounter's).
     *
     * @param target the enemy
     * @return whether {@link #admit} may fill a slot for it
     */
    boolean admits(UUID target) {
        return open == null || open.isParticipant(target);
    }

    /**
     * The encounter that owns the set.
     *
     * @return the identity, or {@code null} for the set of sessions outside any encounter
     */
    EncounterId encounter() {
        return encounter;
    }

    /**
     * The slot of a participant.
     *
     * @param participant the enemy
     * @return the slot, or {@code -1} when the set has no model of it
     */
    int find(UUID participant) {
        for (var slot = 0; slot < count; slot++) {
            if (participant.equals(ids[slot])) {
                return slot;
            }
        }
        return -1;
    }

    /**
     * The slot of a participant, filling one with an empty model the first time.
     *
     * @param participant the enemy
     * @param settings pattern limits
     * @param seed seeds of new models
     * @param ownTrials own trials required when a seed is present
     * @param tick the server tick
     * @return the slot
     */
    int admit(UUID participant, PatternSettings settings, EnemyModelSeed seed, int ownTrials, long tick) {
        var slot = find(participant);
        if (slot < 0) {
            slot = count < CAPACITY ? count++ : oldest();
            ids[slot] = participant;
            models[slot] = new EnemyModel(participant, settings.order2Contexts(), settings.histogramBinTicks(),
                    seed.seedFor(participant), ownTrials);
            tokenizers[slot] = new EnemyTokenizer(settings.mergeTicks(), settings.idleTicks(),
                    settings.maxTokensPerSecond());
            facts[slot] = new PatternFactFilter();
            shown[slot] = 0;
            factTick[slot] = Long.MIN_VALUE;
        }
        seen[slot] = tick;
        return slot;
    }

    /**
     * The model of a slot.
     *
     * @param slot a slot from {@link #find} or {@link #admit}
     * @return the model
     */
    EnemyModel model(int slot) {
        return models[slot];
    }

    /**
     * The tokenizer of a slot.
     *
     * @param slot a slot from {@link #find} or {@link #admit}
     * @return the tokenizer
     */
    EnemyTokenizer tokenizer(int slot) {
        return tokenizers[slot];
    }

    /**
     * The participant of a slot.
     *
     * @param slot a slot below {@link #size}
     * @return the enemy
     */
    UUID participant(int slot) {
        return ids[slot];
    }

    /**
     * Filled slots.
     *
     * @return the count
     */
    int size() {
        return count;
    }

    /**
     * The facts this participant may show (M26.7): {@link PatternFactFilter} over its active and resting patterns,
     * advanced once per tick however many members visit it.
     *
     * @param slot a slot from {@link #find} or {@link #admit}
     * @param tick the server tick
     * @param settings pattern limits
     * @return the shown mask
     */
    int shown(int slot, long tick, PatternSettings settings) {
        if (factTick[slot] != tick) {
            factTick[slot] = tick;
            var model = models[slot];
            shown[slot] = facts[slot].update(model.activeMask(), (int) tick, settings.factOnTicks(),
                    settings.factOffTicks(), resting(model));
        }
        return shown[slot];
    }

    private static int resting(EnemyModel model) {
        var mask = 0;
        for (var pattern : PATTERNS) {
            var tracker = model.tracker(pattern);
            if (tracker != null && tracker.state() == PatternState.RESTING) {
                mask |= pattern.bit();
            }
        }
        return mask;
    }

    private int oldest() {
        var oldest = 0;
        for (var slot = 1; slot < count; slot++) {
            if (seen[slot] < seen[oldest]) {
                oldest = slot;
            }
        }
        return oldest;
    }
}
