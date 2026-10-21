package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ActionStance;
import akyrion.thepredict.domain.BaseStance;
import akyrion.thepredict.domain.CombatStance;
import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.CueRequest;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.Reaction;
import akyrion.thepredict.domain.ReactionBand;
import akyrion.thepredict.domain.StanceCause;
import akyrion.thepredict.domain.StanceMachine;
import akyrion.thepredict.domain.StanceNativeMode;
import akyrion.thepredict.domain.StanceState;

/**
 * Applies {@link StanceMachine} when a deadline is due, and the base on the reactive cycle (M23.1). The step array is
 * owned by the caller. A cause below zero does not rewrite {@code busyUntilTick}.
 */
final class StanceClock {
    private StanceClock() {
    }

    /**
     * Ends a wind-up, a recovery or a stagger when its deadline is due. An early call moves the stance deadline to the
     * action's own, and a call without an action clears it, so the release lane does not step the session again on
     * every tick.
     *
     * @param session the mob
     * @param tick server tick
     * @param actions signals
     * @param step length at least 5
     */
    static void advance(MobSession session, long tick, CombatActions actions, long[] step) {
        if (session.actionStance() == ActionStance.NONE) {
            session.nextStanceTick(Long.MAX_VALUE);
            return;
        }
        if (tick < session.actionUntilTick()) {
            session.nextStanceTick(session.actionUntilTick());
            return;
        }
        var winding = session.actionStance() == ActionStance.WINDUP;
        apply(session, tick, desired(session), survival(session, tick), controlled(session), false,
                winding ? StanceMachine.EVENT_IMPACT : StanceMachine.EVENT_NONE,
                winding ? session.tellRecovery() : 0, 0, actions, step);
        if (session.actionStance() == ActionStance.NONE) {
            session.nextStanceTick(Long.MAX_VALUE);
        } else {
            session.nextStanceTick(session.actionUntilTick());
        }
    }

    /**
     * Re-evaluates the base. Called from the reactive cycle, including ticks with no action deadline.
     *
     * @param session the mob
     * @param tick server tick
     * @param actions signals
     * @param step length at least 5
     */
    static void evaluateBase(MobSession session, long tick, CombatActions actions, long[] step) {
        if (session.actionStance() == ActionStance.WINDUP && tick >= session.actionUntilTick()) {
            return;
        }
        apply(session, tick, desired(session), survival(session, tick), controlled(session), false,
                StanceMachine.EVENT_NONE, 0, 0, actions, step);
    }

    /**
     * One machine step. An action that ends here, also on the reactive cycle, clears the stance deadline.
     *
     * @param session the mob
     * @param tick server tick
     * @param desiredBase desired base ordinal
     * @param survival whether evade is live
     * @param controlled whether ThePredict controls the mob
     * @param enraged whether enraged should be set
     * @param event machine event
     * @param eventTicks recovery or stagger
     * @param whiffFloor extra recovery
     * @param actions signals
     * @param step length at least 5
     */
    static void apply(MobSession session, long tick, int desiredBase, boolean survival, boolean controlled,
                      boolean enraged, int event, int eventTicks, int whiffFloor, CombatActions actions, long[] step) {
        var before = session.actionStance();
        var baseBefore = session.baseStance().ordinal();
        var visibleBefore = CombatStance.visible(baseBefore, before.ordinal());
        var enragedBefore = StanceState.enraged(session.stanceState());
        StanceMachine.step(step, session.stanceState(), tick, session.baseSinceTick(), session.actionUntilTick(),
                desiredBase, StanceCause.TACTIC.ordinal(), survival, controlled, enraged || raging(session),
                session.profile().stances().hysteresisCycles(), session.profile().stances().minDwellTicks(), event,
                eventTicks, whiffFloor);
        var cause = (int) step[StanceMachine.OUT_CAUSE];
        var state = step[StanceMachine.OUT_STATE];
        if (state != session.stanceState() || cause >= 0) {
            session.stanceState(state);
            session.actionUntilTick(step[StanceMachine.OUT_ACTION_UNTIL]);
            session.baseSinceTick(step[StanceMachine.OUT_BASE_SINCE]);
        }
        var actionChanged = settle(session, tick, before, cause, step);
        var visible = CombatStance.visible(session.baseStance().ordinal(), session.actionStance().ordinal());
        var enragedNow = StanceState.enraged(session.stanceState());
        if (visible != visibleBefore || enragedNow != enragedBefore) {
            publish(session, visible, enragedNow, enragedBefore, actionChanged, tick, actions);
        }
        remember(session, tick, visibleBefore, baseBefore, before, enragedBefore, cause);
        session.publishStanceView();
    }

    /**
     * What a machine step leaves on the session besides its state: the busy deadline, the punish window, the end of
     * the stance deadline and of the wind-up answer.
     *
     * @return whether the action changed
     */
    private static boolean settle(MobSession session, long tick, ActionStance before, int cause, long[] step) {
        var after = session.actionStance();
        if (cause >= 0 && (after != ActionStance.NONE || before != ActionStance.NONE)) {
            session.busyUntil(step[StanceMachine.OUT_BUSY]);
            session.tellBusy(step[StanceMachine.OUT_BUSY]);
        }
        if (after == ActionStance.NONE && before != ActionStance.NONE) {
            session.punishTarget(null);
            session.nextStanceTick(Long.MAX_VALUE);
        }
        var actionChanged = after != before;
        if (actionChanged) {
            session.stanceConflict(false);
        }
        if ((after == ActionStance.RECOVERY || after == ActionStance.STAGGERED) && actionChanged) {
            var until = step[StanceMachine.OUT_ACTION_UNTIL];
            if (until != Long.MAX_VALUE) {
                session.tellResults().armPunish(tick, until);
            }
        }
        if (after != ActionStance.WINDUP) {
            session.clearWindupAnswer();
        }
        return actionChanged;
    }

    /**
     * Keeps one stance line when the visible stance, the base, the action or the enraged mark changed.
     *
     * @param session the mob
     * @param tick server tick
     * @param visibleBefore visible stance before the step
     * @param baseBefore base ordinal before the step
     * @param actionBefore action before the step
     * @param enragedBefore enraged mark before the step
     * @param cause machine cause, or negative when the step named none
     */
    static void remember(MobSession session, long tick, CombatStance visibleBefore, int baseBefore,
                         ActionStance actionBefore, boolean enragedBefore, int cause) {
        var visible = CombatStance.visible(session.baseStance().ordinal(), session.actionStance().ordinal());
        var enragedNow = StanceState.enraged(session.stanceState());
        if (visible == visibleBefore && session.baseStance().ordinal() == baseBefore
                && session.actionStance() == actionBefore && enragedNow == enragedBefore) {
            return;
        }
        var mode = session.profile().stances().nativeMode();
        var dwell = tick - session.baseSinceTick();
        session.tellResults().noteStance(visibleBefore.ordinal(), visible.ordinal(), session.baseStance().ordinal(),
                session.actionStance().ordinal(), enragedNow, cause, dwell, mode.ordinal(),
                mode == StanceNativeMode.HYBRID, mode == StanceNativeMode.WRITE && !session.stanceConflict(),
                session.authorMirror());
    }

    private static void publish(MobSession session, CombatStance visible, boolean enraged, boolean enragedBefore,
                                boolean actionChanged, long tick, CombatActions actions) {
        var mode = session.profile().stances().nativeMode();
        if (mode == StanceNativeMode.HYBRID || mode == StanceNativeMode.DEFAULT) {
            actions.writeStanceVariable(session.key(), visible.name().toLowerCase(java.util.Locale.ROOT));
        } else if (mode == StanceNativeMode.WRITE) {
            writeNative(session, visible, actionChanged, tick, actions);
        }
        actions.cue(session.key(), CueRequest.stance(visible));
        if (enraged != enragedBefore) {
            actions.cue(session.key(), new CueRequest(enraged ? "PREDICTOR_STANCE_ENRAGED" : "PREDICTOR_STANCE_CALM"));
        }
    }

    /**
     * Logs a latched native-stance conflict once, inside the execution-log limit (rule C8).
     *
     * @param session the mob
     * @param tick server tick
     * @param log operator log
     * @param metrics counters
     */
    static void flushConflict(MobSession session, long tick, ExecutionLog log, DecisionMetrics metrics) {
        if (!session.takeConflictPending()) {
            return;
        }
        log.always(tick, "NATIVE_STANCE_CONFLICT " + session.key().entityId());
        metrics.add(DecisionMetrics.TellCounter.NATIVE_STANCE_CONFLICT, 1);
    }

    private static void writeNative(MobSession session, CombatStance visible, boolean actionChanged, long tick,
                                    CombatActions actions) {
        var want = "predictor_" + visible.name().toLowerCase(java.util.Locale.ROOT);
        var author = session.authorMirror();
        var last = session.lastNativeStance();
        var readSinceWrite = session.mirrorTick() > session.nativeWriteTick();
        if (!actionChanged && session.stanceConflict()) {
            return;
        }
        if (!actionChanged && readSinceWrite && !last.isEmpty() && !author.equals("none") && !author.equals(want)
                && !author.equals(last)) {
            session.stanceConflict(true);
            session.markConflictPending();
            return;
        }
        actions.writeNativeStance(session.key(), want);
        session.lastNativeStance(want, tick);
    }

    /**
     * Whether enrage is on. A listed context that is active, or an author stance named in {@code EnragedNative}.
     * Neither shortens a tell.
     *
     * @param session the mob
     * @return whether the machine should set enraged
     */
    private static boolean raging(MobSession session) {
        var settings = session.profile().stances();
        var contexts = settings.enragedContexts();
        var natives = settings.enragedNative();
        if (contexts.isEmpty() && natives.isEmpty()) {
            return false;
        }
        if (!contexts.isEmpty()) {
            var facts = session.lastMobFacts().orElse(null);
            if (facts != null) {
                for (var id : facts.activeContexts()) {
                    var name = id.value();
                    for (var index = 0; index < contexts.size(); index++) {
                        if (contexts.get(index).equals(name)) {
                            return true;
                        }
                    }
                }
            }
        }
        var mirror = session.authorMirror();
        for (var index = 0; index < natives.size(); index++) {
            if (natives.get(index).equals(mirror)) {
                return true;
            }
        }
        return false;
    }

    /** Writes {@code none} after a session is dropped. Hybrid only. */
    static void invalidated(MobSession session, CombatActions actions) {
        if (session.profile().stances().nativeMode() == StanceNativeMode.OFF
                || session.profile().stances().nativeMode() == StanceNativeMode.READ) {
            return;
        }
        if (session.profile().stances().nativeMode() == StanceNativeMode.WRITE) {
            actions.writeNativeStance(session.key(), "predictor_none");
            return;
        }
        actions.writeStanceVariable(session.key(), "none");
    }

    private static int desired(MobSession session) {
        var intent = session.intent().orElse(null);
        if (intent == null || intent.level() == ControlLevel.BASE) {
            return BaseStance.NEUTRAL.ordinal();
        }
        var declared = session.profile().tactic(intent.tactic()).flatMap(tactic -> tactic.stance());
        if (declared.isPresent()) {
            return declared.get().ordinal();
        }
        return session.profile().stances().baseOf(intent.movement()).ordinal();
    }

    private static boolean controlled(MobSession session) {
        var intent = session.intent().orElse(null);
        return intent != null && intent.level() != ControlLevel.BASE;
    }

    private static boolean survival(MobSession session, long tick) {
        return session.slot(ReactionBand.SURVIVAL) == Reaction.EVADE_CRITICAL.ordinal()
                && session.slotUntil(ReactionBand.SURVIVAL) > tick;
    }

    /**
     * Movement a view should publish while an action is up. Wind-up and stagger publish hold (errata E23-7).
     *
     * @param session the mob
     * @param movement movement of the intent
     * @return the published movement
     */
    static Movement published(MobSession session, Movement movement) {
        var action = session.actionStance();
        if (action == ActionStance.WINDUP || action == ActionStance.STAGGERED) {
            return Movement.HOLD;
        }
        return movement;
    }
}
