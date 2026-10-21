package akyrion.thepredict.adapter.server.events;

import akyrion.thepredict.domain.HintKind;
import akyrion.thepredict.engine.MemoryPrefetchHints;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Objects;
import java.util.UUID;

/**
 * The only Bukkit entry of player memory (M27.4, T6). Every handler offers a hint and does nothing else. Pre-login
 * offers only when the login is allowed.
 */
public final class MemoryPresenceListener implements Listener {
    private final MemoryPrefetchHints hints;

    /**
     * Creates the listener.
     *
     * @param hints the queue
     */
    public MemoryPresenceListener(MemoryPrefetchHints hints) {
        this.hints = Objects.requireNonNull(hints, "hints");
    }

    /**
     * Offers a pre-login hint when the player is allowed.
     *
     * @param event the login
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onAsyncPreLogin(AsyncPlayerPreLoginEvent event) {
        onPreLogin(event.getUniqueId(), event.getLoginResult() == AsyncPlayerPreLoginEvent.Result.ALLOWED);
    }

    /**
     * Offers a join hint.
     *
     * @param event the join
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        onJoin(event.getPlayer().getUniqueId());
    }

    /**
     * Offers a quit hint.
     *
     * @param event the quit
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        onQuit(event.getPlayer().getUniqueId());
    }

    /**
     * The testable pre-login offer. A refused login offers nothing (T6).
     *
     * @param subject the player
     * @param allowed whether the login was allowed
     * @return whether a hint was queued
     */
    public boolean onPreLogin(UUID subject, boolean allowed) {
        return hints.offer(subject, HintKind.PRE_LOGIN, allowed);
    }

    /**
     * The testable join offer.
     *
     * @param subject the player
     * @return whether a hint was queued
     */
    public boolean onJoin(UUID subject) {
        return hints.offer(subject, HintKind.JOIN, true);
    }

    /**
     * The testable quit offer.
     *
     * @param subject the player
     * @return whether a hint was queued
     */
    public boolean onQuit(UUID subject) {
        return hints.offer(subject, HintKind.QUIT, true);
    }
}
