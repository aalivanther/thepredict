package akyrion.thepredict.engine;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One flag for one tell (M23.7). The engine creates it. MythicMobs reads it from any thread. It is never reused.
 */
public final class CastInterrupt {
    private final AtomicBoolean flag = new AtomicBoolean();

    /** Creates a flag that starts clear. */
    public CastInterrupt() {
    }

    /**
     * The reference MythicMobs stores on the skill metadata.
     *
     * @return the flag
     */
    public AtomicBoolean flag() {
        return flag;
    }

    /** Cuts the skill. Visible to MythicMobs after the next delay, never inside the opening tick. */
    public void interrupt() {
        flag.set(true);
    }

    /**
     * Whether the tell was cut.
     *
     * @return the flag
     */
    public boolean interrupted() {
        return flag.get();
    }
}
