package akyrion.thepredict.engine;

import akyrion.thepredict.domain.CaptureContext;
import akyrion.thepredict.domain.CombatFacts;
import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.MobProbe;
import akyrion.thepredict.domain.ThreatTableState;
import akyrion.thepredict.domain.TickSignals;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** A reader of active mobs in combat that captures nothing, for the tests of the tick paths. */
final class QuietReader implements CombatReader {
    private final Map<UUID, Long> signals;
    private final boolean players;
    private int worldReads;

    /**
     * @param signals tick signals per expected target; a target not named reads {@link TickSignals#PRESENT}
     * @param players whether every target is a player
     */
    QuietReader(Map<UUID, Long> signals, boolean players) {
        this.signals = Map.copyOf(signals);
        this.players = players;
    }

    /** @return how many times the world name was read since the last {@link #resetWorldReads()} */
    int worldReads() {
        return worldReads;
    }

    /** Starts the count of world reads again. */
    void resetWorldReads() {
        worldReads = 0;
    }

    @Override
    public Optional<CombatFacts> capture(EncounterId encounter, List<MobKey> members, CaptureContext context) {
        return Optional.empty();
    }

    @Override
    public Optional<MobProbe> probe(MobKey mob) {
        return Optional.empty();
    }

    @Override
    public boolean inCombat(UUID entityId) {
        return true;
    }

    @Override
    public boolean withinFollowRange(MobKey mob, MobKey member) {
        return false;
    }

    @Override
    public boolean isActive(UUID entityId) {
        return true;
    }

    @Override
    public Optional<ThreatTableState> threatTable(MobKey mob) {
        return Optional.empty();
    }

    @Override
    public long tickSignals(MobKey mob, UUID expectedTarget) {
        return signals.getOrDefault(expectedTarget, TickSignals.PRESENT);
    }

    @Override
    public boolean playerTarget(UUID entityId) {
        return players;
    }

    @Override
    public String worldName(MobKey mob) {
        worldReads++;
        return "";
    }
}
