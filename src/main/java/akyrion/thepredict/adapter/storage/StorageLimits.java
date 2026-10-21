package akyrion.thepredict.adapter.storage;

import akyrion.thepredict.domain.LearningSettings;
import akyrion.thepredict.domain.MemorySettings;
import akyrion.thepredict.domain.distill.DistillSettings;

import java.util.Objects;

/**
 * The row caps and retentions the writer applies to the learned tables (A5, M27.3, M28.1, M29.1), and the
 * {@code learning} section {@code learn build} reads (M28.9). The root translates them from the configuration (D10).
 * The table caps of A5 are the most a release accepts: a smaller cap is only for a test.
 *
 * @param learning the {@code learning} section: build limits and {@code weeksKept}
 * @param memoryRows {@code memory.maxRows}
 * @param memoryRetentionDays {@code memory.retentionDays}
 * @param distillWeeksKept {@code distill.weeksKept}
 * @param valueRows cap of {@code m28_value_stats}, at most {@link ValueStatSql#CAP}
 * @param choiceRows cap of {@code m29_choice_stats}, at most {@link ChoiceStatSql#CAP}
 * @param agreementRows cap of {@code m29_agreement_stats}, at most {@link AgreementStatSql#CAP}
 */
public record StorageLimits(LearningSettings learning, int memoryRows, int memoryRetentionDays, int distillWeeksKept,
                            int valueRows, int choiceRows, int agreementRows) {
    /** The limits of a fresh installation. */
    public static final StorageLimits DEFAULTS = of(MemorySettings.DEFAULT, LearningSettings.DEFAULT,
            DistillSettings.DEFAULT);

    /**
     * Validates the limits.
     *
     * @throws NullPointerException if {@code learning} is {@code null}
     * @throws IllegalArgumentException if a limit is below 1 or a table cap is above its A5 cap
     */
    public StorageLimits {
        Objects.requireNonNull(learning, "learning");
        positive(memoryRows, Integer.MAX_VALUE, "memoryRows");
        positive(memoryRetentionDays, Integer.MAX_VALUE, "memoryRetentionDays");
        positive(distillWeeksKept, Integer.MAX_VALUE, "distillWeeksKept");
        positive(valueRows, ValueStatSql.CAP, "valueRows");
        positive(choiceRows, ChoiceStatSql.CAP, "choiceRows");
        positive(agreementRows, AgreementStatSql.CAP, "agreementRows");
    }

    /**
     * The limits of a configuration, with the A5 table caps.
     *
     * @param memory the {@code memory} section
     * @param learning the {@code learning} section
     * @param distill the {@code distill} section
     * @return the limits
     */
    public static StorageLimits of(MemorySettings memory, LearningSettings learning, DistillSettings distill) {
        return new StorageLimits(learning, memory.maxRows(), memory.retentionDays(), distill.weeksKept(),
                ValueStatSql.CAP, ChoiceStatSql.CAP, AgreementStatSql.CAP);
    }

    private static void positive(int value, int most, String name) {
        if (value < 1 || value > most) {
            throw new IllegalArgumentException(name + " must be within [1, " + most + "], was " + value);
        }
    }
}
