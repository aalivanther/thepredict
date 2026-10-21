package akyrion.thepredict.engine;

import akyrion.thepredict.domain.FightEventKind;
import akyrion.thepredict.domain.FightSummary;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * One whole-fight clock (M22.8, M22.13, errata E22-5, M23.7). It starts on damage dealt to the current target or on
 * damage taken from that target, and does not restart when the target changes. Either direction keeps it open. The
 * clock lives as long as its session, so a fight that opens after a close starts from nothing: dealt damage, the health
 * left on the target, the share taken, the close kind and the summary all belong to one fight.
 */
public final class FightClock {
    private static final Optional<FightEventKind> NONE = Optional.empty();
    private static final Optional<FightEventKind> ENGAGED = Optional.of(FightEventKind.ENGAGED);
    private static final Optional<FightEventKind> TARGET_CHANGED = Optional.of(FightEventKind.TARGET_CHANGED);
    private static final Optional<FightEventKind> TARGET_DIED = Optional.of(FightEventKind.TARGET_DIED);
    private static final Optional<FightEventKind> MOB_DIED = Optional.of(FightEventKind.MOB_DIED);
    private static final Optional<FightEventKind> DISENGAGED = Optional.of(FightEventKind.DISENGAGED);

    /**
     * An idle clock. The first damage dealt opens the fight.
     */
    public FightClock() {
    }

    /**
     * Hears a close. The clock still returns the same event. An idle close is not reported (M27, errata E25-4 applied).
     *
     * @param listener the listener
     */
    public void onClosed(Consumer<FightEventKind> listener) {
        var next = Objects.requireNonNull(listener, "listener");
        var previous = this.closed;
        this.closed = event -> {
            previous.accept(event);
            next.accept(event);
        };
    }

    private boolean open;
    private long startTick;
    private long lastDealtTick = Long.MIN_VALUE;
    private long lastTakenTick = Long.MIN_VALUE;
    private UUID target;
    private double dealt;
    private double healthRemaining = 1.0;
    private boolean mobDied;
    private Consumer<FightEventKind> closed = event -> {
    };
    private boolean targetDied;
    private boolean fought;
    private boolean killingBlow;
    private FightEventKind closedKind;
    private double takenShare;
    private double healthWeight;
    private FightSummary summary;
    private int rung;

    /**
     * Whether a fight is open.
     *
     * @return whether it is open
     */
    public boolean open() {
        return open;
    }

    /**
     * Tick of the first dealt hit, or {@link Long#MIN_VALUE} when idle.
     *
     * @return the tick
     */
    public long startTick() {
        return open ? startTick : Long.MIN_VALUE;
    }

    /**
     * Health fraction dealt since the fight engaged.
     *
     * @return the fraction
     */
    public double dealt() {
        return dealt;
    }

    /**
     * Health fraction still on the target.
     *
     * @return the fraction
     */
    public double healthRemaining() {
        return healthRemaining;
    }

    /**
     * Target of the fight, kept after the close until the next fight opens.
     *
     * @return the target, empty before the first fight (EFO-24: no {@code null} in the API)
     */
    public Optional<UUID> target() {
        return Optional.ofNullable(target);
    }

    /**
     * Whether an open fight is against this participant, without allocating.
     *
     * @param participant the participant
     * @return whether the fight is open and its target is {@code participant}
     */
    public boolean fighting(UUID participant) {
        return open && Objects.requireNonNull(participant, "participant").equals(target);
    }

    /**
     * Tick of the latest dealt hit.
     *
     * @return the tick, or {@link Long#MIN_VALUE} when none was dealt
     */
    public long lastDealtTick() {
        return lastDealtTick;
    }

    /**
     * Tick of the latest damage taken from the fight target.
     *
     * @return the tick, or {@link Long#MIN_VALUE} when none was taken
     */
    public long lastTakenTick() {
        return lastTakenTick;
    }

    /**
     * Damage dealt to a victim. Only the current target engages or refreshes the clock; a session without a target
     * has no fight to feed and does not call this.
     *
     * @param tick server tick
     * @param victim damaged entity
     * @param currentTarget target the session is fighting
     * @param healthFraction damage over the victim's maximum health
     * @param healthAfter fraction left on the victim
     * @return the event, empty when the hit only refreshes the fight or is not part of it
     */
    public Optional<FightEventKind> dealt(long tick, UUID victim, UUID currentTarget, double healthFraction,
                                          double healthAfter) {
        if (!Objects.requireNonNull(victim, "victim").equals(Objects.requireNonNull(currentTarget, "currentTarget"))) {
            return NONE;
        }
        if (!open) {
            reopen(tick, victim);
            lastDealtTick = tick;
            noteHealth(healthFraction, healthAfter);
            return ENGAGED;
        }
        lastDealtTick = tick;
        if (target != null && !target.equals(victim)) {
            target = victim;
            noteHealth(healthFraction, healthAfter);
            return TARGET_CHANGED;
        }
        noteHealth(healthFraction, healthAfter);
        return NONE;
    }

    /**
     * The target died. The clock closes.
     *
     * @param tick server tick
     * @return the event, empty when no fight was open
     */
    public Optional<FightEventKind> targetDied(long tick) {
        if (!open) {
            return NONE;
        }
        targetDied = true;
        open = false;
        lastDealtTick = tick;
        lastTakenTick = tick;
        closedKind = FightEventKind.TARGET_DIED;
        remember();
        closed.accept(FightEventKind.TARGET_DIED);
        return TARGET_DIED;
    }

    /**
     * The mob died. The clock closes.
     *
     * @return the event, empty when no fight was open
     */
    public Optional<FightEventKind> mobDied() {
        if (!open) {
            return NONE;
        }
        mobDied = true;
        open = false;
        closedKind = FightEventKind.MOB_DIED;
        remember();
        closed.accept(FightEventKind.MOB_DIED);
        return MOB_DIED;
    }

    /**
     * Damage taken from the current target. It engages the fight and refreshes the quiet timer (M23.7); a session
     * without a target does not call this.
     *
     * @param tick server tick
     * @param attacker entity that caused the damage
     * @param currentTarget target the session is fighting
     * @return {@link FightEventKind#ENGAGED} when this hit opens the fight, else empty
     */
    public Optional<FightEventKind> takenFrom(long tick, UUID attacker, UUID currentTarget) {
        if (!Objects.requireNonNull(attacker, "attacker").equals(Objects.requireNonNull(currentTarget,
                "currentTarget"))) {
            return NONE;
        }
        if (!open) {
            reopen(tick, currentTarget);
            lastTakenTick = tick;
            return ENGAGED;
        }
        lastTakenTick = tick;
        return NONE;
    }

    /**
     * Damage taken from the current target, counted toward that player's share of this mob. A hit that opens a new
     * fight counts toward the share of that fight only.
     *
     * @param tick server tick
     * @param attacker entity that caused the damage
     * @param currentTarget target the session is fighting
     * @param healthFraction damage over this mob's maximum health
     * @return {@link FightEventKind#ENGAGED} when this hit opens the fight, else empty
     */
    public Optional<FightEventKind> takenFrom(long tick, UUID attacker, UUID currentTarget, double healthFraction) {
        var event = takenFrom(tick, attacker, currentTarget);
        if (attacker.equals(currentTarget)) {
            takenShare += Math.max(0.0, healthFraction);
        }
        return event;
    }

    /**
     * Starts a new fight on a clock that is idle or closed (M22.8): everything the previous fight measured is dropped,
     * so the estimator of the new fight never starts from the damage, the health or the share of the last one, and the
     * summary and the close kind of the last fight are empty while the new one is open. The max health of the mob and
     * the noted rung stay, because they belong to the mob.
     *
     * @param tick tick of the hit that opens the fight
     * @param fightTarget target of the new fight
     */
    private void reopen(long tick, UUID fightTarget) {
        open = true;
        startTick = tick;
        fought = true;
        target = fightTarget;
        dealt = 0.0;
        healthRemaining = 1.0;
        takenShare = 0.0;
        lastDealtTick = Long.MIN_VALUE;
        lastTakenTick = Long.MIN_VALUE;
        closedKind = null;
        summary = null;
        mobDied = false;
        targetDied = false;
        killingBlow = false;
    }

    /**
     * Closes the fight after {@code quietTicks} without dealt or taken damage.
     *
     * @param tick server tick
     * @param quietTicks the disengage gap
     * @return {@link FightEventKind#DISENGAGED} when this call closed the fight, else empty
     */
    public Optional<FightEventKind> quiet(long tick, int quietTicks) {
        var last = lastActivity();
        if (!open || last == Long.MIN_VALUE || tick - last < quietTicks) {
            return NONE;
        }
        open = false;
        closedKind = FightEventKind.DISENGAGED;
        remember();
        closed.accept(FightEventKind.DISENGAGED);
        return DISENGAGED;
    }

    private long lastActivity() {
        if (lastDealtTick == Long.MIN_VALUE) {
            return lastTakenTick;
        }
        if (lastTakenTick == Long.MIN_VALUE) {
            return lastDealtTick;
        }
        return Math.max(lastDealtTick, lastTakenTick);
    }

    /**
     * Ticks since the fight engaged.
     *
     * @param tick current tick
     * @return the elapsed ticks, or 0 when idle
     */
    public long elapsed(long tick) {
        return open ? tick - startTick : 0L;
    }

    /**
     * Whether the mob's death had no dealt evidence.
     *
     * @return whether the closed death had no dealt damage
     */
    public boolean mobDiedWithoutEvidence() {
        return mobDied && dealt <= 0.0;
    }

    /**
     * Whether the target death closed the fight.
     *
     * @return whether the target died
     */
    public boolean targetDied() {
        return targetDied;
    }

    /**
     * Whether any hit opened this clock. It stays true after the close.
     *
     * @return whether the fight was engaged
     */
    public boolean fought() {
        return fought;
    }

    /**
     * Whether the mob's hit left the target at no health.
     *
     * @return whether this clock landed the killing blow
     */
    public boolean killingBlow() {
        return killingBlow;
    }

    /**
     * How the clock closed.
     *
     * @return the event, empty while the fight is open or idle
     */
    public Optional<FightEventKind> closedKind() {
        return Optional.ofNullable(closedKind);
    }

    /**
     * Whether the mob's death closed the clock.
     *
     * @return whether the mob died
     */
    public boolean mobHasDied() {
        return mobDied;
    }

    /**
     * Health fraction this mob took from the fight target.
     *
     * @return the share
     */
    public double takenShare() {
        return takenShare;
    }

    /**
     * Max health of this mob, the weight of {@link #takenShare()}. Zero until {@link #noteMaxHealth(double)}.
     *
     * @return the max health, {@code 0} when it was never noted
     */
    public double healthWeight() {
        return healthWeight;
    }

    /**
     * Stores this mob's max health. A non-positive or non-finite value is ignored.
     *
     * @param maxHealth max health
     */
    public void noteMaxHealth(double maxHealth) {
        if (maxHealth > 0.0 && Double.isFinite(maxHealth)) {
            healthWeight = maxHealth;
        }
    }

    /**
     * Summary left by the last close. Empty while the fight is open or idle.
     *
     * @return the summary
     */
    public Optional<FightSummary> summary() {
        return Optional.ofNullable(summary);
    }

    private void remember() {
        if (closedKind == null) {
            return;
        }
        var end = lastActivity();
        var elapsed = fought && end > startTick ? (int) Math.min(Integer.MAX_VALUE, end - startTick) : 0;
        var ln = elapsed > 0 ? StrictMath.log(elapsed) : 0.0;
        var fraction = takenShare <= 0.0 ? 0.0 : Math.min(1.0, takenShare);
        var permille = (int) Math.round(fraction * 1000.0);
        summary = new FightSummary(closedKind, elapsed, ln, rung, -1, -1, permille, (float) healthWeight);
    }

    /**
     * Stores the rung the session published on this clock.
     *
     * @param rung the rung, 0 to 8
     */
    public void noteRung(int rung) {
        this.rung = rung;
    }

    /**
     * Rung last noted on this clock.
     *
     * @return the rung
     */
    public int rung() {
        return rung;
    }

    /**
     * Log of the ticks from the first hit to the last, including after the close.
     *
     * @return the log time, {@code 0} when the clock has no span
     */
    public double lnElapsed() {
        if (!fought) {
            return 0.0;
        }
        var end = lastActivity();
        if (end <= startTick) {
            return 0.0;
        }
        return StrictMath.log(end - startTick);
    }

    private void noteHealth(double healthFraction, double healthAfter) {
        dealt += healthFraction;
        healthRemaining = healthAfter;
        if (healthAfter <= 0.0) {
            killingBlow = true;
        }
    }
}
