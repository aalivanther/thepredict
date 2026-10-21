package akyrion.thepredict.adapter.server.events;

import com.destroystokyo.paper.event.server.ServerTickEndEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;

/**
 * Optional MSPT guard of the tick layer (M20.3). A guard of zero never calls the engine. Otherwise the previous
 * server tick forces step 2 when {@code timeRemaining} is negative or {@code getAverageTickTime()} exceeds the guard
 * in milliseconds.
 */
public final class TickGuard implements Listener {
    private final IntSupplier guardMspt;
    private final DoubleSupplier averageTickMillis;
    private final Consumer<Boolean> note;

    /**
     * Creates the listener.
     *
     * @param guardMspt {@code decision.tickLayer.guardMspt}; zero stays silent
     * @param averageTickMillis {@code Server#getAverageTickTime()}, in milliseconds
     * @param note receives whether the previous tick was over the guard
     */
    public TickGuard(IntSupplier guardMspt, DoubleSupplier averageTickMillis, Consumer<Boolean> note) {
        this.guardMspt = Objects.requireNonNull(guardMspt, "guardMspt");
        this.averageTickMillis = Objects.requireNonNull(averageTickMillis, "averageTickMillis");
        this.note = Objects.requireNonNull(note, "note");
    }

    /**
     * Reads the tick that just finished. A zero guard returns before any average or engine call.
     *
     * @param event the end of a server tick
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTickEnd(ServerTickEndEvent event) {
        var guard = guardMspt.getAsInt();
        if (guard == 0) {
            return;
        }
        note.accept(event.getTimeRemaining() < 0L || averageTickMillis.getAsDouble() > guard);
    }
}
