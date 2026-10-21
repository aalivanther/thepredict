package akyrion.thepredict.engine;

import akyrion.thepredict.domain.MemoryMode;
import akyrion.thepredict.domain.MemorySettings;

import java.util.Optional;

/**
 * Applies a verified memory mode on the next tick and never to a fight that is already open (M27.7). An unverified
 * mode stays at {@link MemoryMode#RECORD} unless {@code learning.allowUnverified} is true (errata E27-2 applied).
 */
public final class MemoryModeLoader {
    /**
     * Starts at record with nothing pending.
     */
    public MemoryModeLoader() {
    }

    private MemoryMode active = MemoryMode.RECORD;
    private MemoryMode pending = MemoryMode.RECORD;
    private boolean unverified;

    /**
     * The mode admissions may use.
     *
     * @return the active mode
     */
    public MemoryMode active() {
        return active;
    }

    /**
     * Whether the requested mode was refused.
     *
     * @return {@code true} when the loader kept record
     */
    public boolean unverified() {
        return unverified;
    }

    /**
     * Accepts a startup mode. A pass applies on the following {@link #tick()}.
     *
     * @param requested the configured mode
     * @param passed whether the gate passed
     * @param settings the limits, including {@code allowUnverified}
     */
    public void offer(MemoryMode requested, boolean passed, MemorySettings settings) {
        if (passed || requested == MemoryMode.OFF || requested == MemoryMode.RECORD || settings.allowUnverified()) {
            pending = requested;
            unverified = false;
            return;
        }
        pending = MemoryMode.RECORD;
        unverified = true;
    }

    /**
     * The warning written on every startup when an unverified mode is allowed (errata E27-2 applied).
     *
     * @param settings the effective settings
     * @return the warning, empty when the flag is false
     */
    public static Optional<String> startupWarning(MemorySettings settings) {
        if (!settings.allowUnverified()) {
            return Optional.empty();
        }
        return Optional.of("learning.allowUnverified is true; seed and full are allowed without a passing verdict");
    }

    /**
     * Applies the pending mode. Open fights keep the mode they were admitted with.
     */
    public void tick() {
        active = pending;
    }
}
