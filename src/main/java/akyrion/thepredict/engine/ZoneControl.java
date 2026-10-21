package akyrion.thepredict.engine;

import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.ZoneSettings;

import java.util.List;
import java.util.Optional;

/**
 * The zone step of one tick (M24.2). The default does nothing. The server thread calls it; the build itself runs on
 * {@code ThePredict-Zone}.
 */
public interface ZoneControl {
    /** A step that builds nothing. */
    ZoneControl NONE = new ZoneControl() {
        @Override
        public void open(EncounterId encounter, List<MobKey> members, ZoneSettings settings) {
        }

        @Override
        public void close(EncounterId encounter) {
        }

        @Override
        public ZoneStepReport step(long tick, long budgetNanos) {
            return ZoneStepReport.EMPTY;
        }

        @Override
        public Optional<ZoneStatus> status(EncounterId encounter) {
            return Optional.empty();
        }
    };

    /**
     * Registers the encounter before any member is installed.
     *
     * @param encounter the encounter
     * @param members its mobs
     * @param settings the profile zone section
     */
    void open(EncounterId encounter, List<MobKey> members, ZoneSettings settings);

    /**
     * Drops the encounter.
     *
     * @param encounter the encounter
     */
    void close(EncounterId encounter);

    /**
     * Patches due zones while the budget lasts. The clock is read before each zone.
     *
     * @param tick server tick
     * @param budgetNanos remaining nanos
     * @return what the step did
     */
    ZoneStepReport step(long tick, long budgetNanos);

    /**
     * The status of one encounter.
     *
     * @param encounter the encounter
     * @return empty when the encounter has no zone
     */
    Optional<ZoneStatus> status(EncounterId encounter);
}
