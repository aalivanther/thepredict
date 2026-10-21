package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.Eligibility;
import akyrion.thepredict.domain.IntentPolicy;
import akyrion.thepredict.domain.MobFacts;
import akyrion.thepredict.domain.SessionState;
import akyrion.thepredict.domain.Tactic;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TacticalIntent;
import akyrion.thepredict.domain.TacticalProfile;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The intents of {@code /thepredict debug intent} and {@code debug release} (M12.3). Server thread only (rule T1); it
 * never touches the network.
 *
 * <p>A debug intent is a {@code LOCAL} intent of a tactic of the mob's own profile, with confidence 1 and, by default,
 * the {@code maximumIntentTicks} of the profile as validity. It is rejected when the session is missing or suspended,
 * when the profile does not declare the tactic, or when the last capture of the mob did not find it eligible, with
 * the reason taken from {@link Eligibility}; a mob never captured (without a perceived target) has no context and
 * every declared tactic is accepted. The facts are those of the session's last capture ({@code lastMobFacts}), which
 * may be stale: a capture runs only while the mob is in an encounter, so after a fight ends the facts of its last
 * evaluation remain until the next one. While it lasts the coordinator leaves the session alone and never asks Jev about
 * it; when it expires the session drops to level 4 and the fallback chain takes over.
 */
public final class DebugIntents {
    /** Longest validity an operator may ask for, in ticks (five minutes). */
    public static final int MAX_TICKS = 6000;

    private final SessionRegistry sessions;
    private final IntentControl control;

    DebugIntents(SessionRegistry sessions, IntentControl control) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.control = Objects.requireNonNull(control, "control");
    }

    /**
     * Sets a debug intent valid for the {@code maximumIntentTicks} of the mob's profile.
     *
     * @param entityId UUID of the mob
     * @param tactic tactic of the mob's profile
     * @param tick current server tick
     * @param nowNanos monotonic time now
     * @return the applied intent, or why it was refused
     */
    public Outcome set(UUID entityId, TacticId tactic, long tick, long nowNanos) {
        var session = sessions.find(Objects.requireNonNull(entityId, "entityId"));
        var ticks = session.map(found -> found.profile().timing().maximumIntentTicks()).orElse(1);
        return set(entityId, tactic, ticks, tick, nowNanos);
    }

    /**
     * Sets a debug intent valid for the given ticks.
     *
     * @param entityId UUID of the mob
     * @param tactic tactic of the mob's profile
     * @param durationTicks validity, from 1 to {@link #MAX_TICKS}
     * @param tick current server tick
     * @param nowNanos monotonic time now
     * @return the applied intent, or why it was refused
     * @throws IllegalArgumentException if the validity is out of range
     */
    public Outcome set(UUID entityId, TacticId tactic, int durationTicks, long tick, long nowNanos) {
        Objects.requireNonNull(tactic, "tactic");
        if (durationTicks < 1 || durationTicks > MAX_TICKS) {
            throw new IllegalArgumentException("durationTicks must be between 1 and " + MAX_TICKS);
        }
        var session = sessions.find(Objects.requireNonNull(entityId, "entityId"));
        var refusal = session.map(found -> refusal(found, tactic))
                .orElse(Optional.of("the mob has no session: it is not bound to an active profile"));
        if (refusal.isPresent()) {
            return new Outcome.Rejected(refusal.get());
        }
        var active = session.orElseThrow();
        var declared = active.profile().tactic(tactic).orElseThrow();
        var intent = new TacticalIntent(active.key(), tactic, declared.movement(), declared.choice(), 1.0, List.of(),
                Map.of(), ControlLevel.LOCAL, tick, nowNanos + durationTicks * IntentPolicy.NANOS_PER_TICK, false);
        control.applyDebug(active, intent, tick);
        return new Outcome.Applied(intent, durationTicks);
    }

    /**
     * Drops the intent of a mob to level 4, whoever set it; the fallback chain decides again on the next reactive
     * cycle.
     *
     * @param entityId UUID of the mob
     * @param tick current server tick
     * @return {@code false} when the mob has no active session
     */
    public boolean release(UUID entityId, long tick) {
        var session = sessions.find(Objects.requireNonNull(entityId, "entityId"))
                .filter(found -> found.state() == SessionState.ACTIVE);
        session.ifPresent(found -> control.release(found, tick));
        return session.isPresent();
    }

    private static Optional<String> refusal(MobSession session, TacticId tactic) {
        if (session.state() != SessionState.ACTIVE) {
            return Optional.of("the session is suspended; try again once the mob is loaded");
        }
        var profile = session.profile();
        if (profile.tactic(tactic).isEmpty()) {
            return Optional.of("profile " + profile.id() + " does not declare the tactic " + tactic.value()
                    + "; it declares " + String.join(", ", profile.tactics().keySet().stream().map(TacticId::value)
                    .toList()));
        }
        return session.lastMobFacts().filter(facts -> !facts.eligibleTactics().contains(tactic))
                .map(facts -> notEligible(profile, facts, tactic));
    }

    private static String notEligible(TacticalProfile profile, MobFacts facts, TacticId tactic) {
        var governing = Eligibility.governingContext(profile, facts.activeContexts())
                .filter(context -> !context.allowedTactics().contains(tactic));
        if (governing.isPresent()) {
            return "not eligible: the active context " + governing.get().id().value() + " allows only "
                    + String.join(", ", governing.get().allowedTactics().stream().map(TacticId::value).sorted().toList());
        }
        var movement = profile.tactic(tactic).map(Tactic::movement).orElseThrow();
        return "not eligible at the last capture: its skill was on its native cooldown or it needs a target, or "
                + movement + " was not available (no target, or FLANK without a raised shield to flank)";
    }

    /** Result of {@link #set}. */
    public sealed interface Outcome permits Outcome.Applied, Outcome.Rejected {
        /**
         * The debug intent is current.
         *
         * @param intent the applied intent
         * @param durationTicks its validity in ticks
         */
        record Applied(TacticalIntent intent, int durationTicks) implements Outcome {
            /**
             * Validates the components.
             *
             * @throws NullPointerException if the intent is {@code null}
             */
            public Applied {
                Objects.requireNonNull(intent, "intent");
            }
        }

        /**
         * Nothing changed.
         *
         * @param reason fixed English text with profile and tactic names only
         */
        record Rejected(String reason) implements Outcome {
            /**
             * Validates the component.
             *
             * @throws NullPointerException if the reason is {@code null}
             */
            public Rejected {
                Objects.requireNonNull(reason, "reason");
            }
        }
    }
}
