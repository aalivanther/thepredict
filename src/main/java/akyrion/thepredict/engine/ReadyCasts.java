package akyrion.thepredict.engine;

import akyrion.thepredict.domain.CastPreference;
import akyrion.thepredict.domain.ChoiceKind;
import akyrion.thepredict.domain.DamageTier;
import akyrion.thepredict.domain.ExecutionOutcome;
import akyrion.thepredict.domain.ImpactTiming;
import akyrion.thepredict.domain.Reaction;
import akyrion.thepredict.domain.ReactionBand;
import akyrion.thepredict.domain.ReactionPolicy;
import akyrion.thepredict.domain.ReadyCastOrder;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.SkillRisk;
import akyrion.thepredict.domain.TacticalIntent;
import akyrion.thepredict.domain.TickLayerMode;
import akyrion.thepredict.domain.TickSignals;
import akyrion.thepredict.domain.UntypedCastMode;
import akyrion.thepredict.domain.UseCondition;

import java.util.Set;

/**
 * Chooses and fires one skill of {@code ANY_READY} (M19.5). Package-private. The choice of the intent goes first and
 * keeps the bookkeeping of {@link IntentControl#cast}; any other candidate does not consume that choice. An untyped
 * skill is last and is a candidate only when the profile allows it. One line of sight per mob per tick. A refusal of
 * the intent choice that started no cast (MythicMobs rejected it, or the target is still invulnerable) lets the other
 * candidates try on the same tick; with none cast, the retry the refusal scheduled stays.
 */
final class ReadyCasts {
    private static final Reaction[] REACTIONS = Reaction.values();

    private ReadyCasts() {
    }

    /** What the intent choice leaves to the rest of the tick. */
    private enum Turn {
        /** The tick is over. */
        STOP,
        /** The candidates try; without a cast the session waits for the soonest of them. */
        NEXT,
        /** The candidates try; without a cast the retry the intent choice scheduled stays. */
        KEEP
    }

    /** Fires the first candidate that is ready, or reschedules when none is. */
    static void fire(IntentControl control, MobSession session, long tick, long nowNanos) {
        var intent = session.intent().orElseThrow();
        var choices = listed(session);
        session.ensureChoiceSlots(choices.length);
        if (preference(control, session, tick, intent, choices, true)) {
            return;
        }
        var turn = tryIntent(control, session, tick, intent, choices);
        if (turn == Turn.STOP || preference(control, session, tick, intent, choices, false)) {
            return;
        }
        if (!tryCandidates(control, session, tick, intent, choices) && turn == Turn.NEXT) {
            session.scheduleReady(soonest(control, session, tick));
        }
    }

    /**
     * The earliest tick at which some candidate can be tried. An intent choice whose tactic is not eligible does not
     * wake the session while it is already ready.
     *
     * @param control intent control that reads cooldowns
     * @param session the session
     * @param tick current tick
     * @return that tick, or two ticks ahead when nothing is a candidate
     */
    static long soonest(IntentControl control, MobSession session, long tick) {
        return soonest(control, session, tick, false);
    }

    /**
     * The earliest tick at which some candidate, or the choice of the intent, can be tried. The choice of the intent
     * counts whatever its kind: a vanilla hit refused out of reach is retried on the next tick, not after a reactive
     * cycle, so it does not lose the first hit of every exchange to a rival with the same rhythm (D-EF7, segunda ronda
     * de efectividad). {@code nearOnly} skips the read of a choice whose cached ready tick is more than two ticks ahead
     * and was read in this reactive cycle (M19.4, SA-13).
     *
     * @param control intent control that reads cooldowns
     * @param session the session
     * @param tick current tick
     * @param nearOnly whether a far cached tick is kept
     * @return that tick, or two ticks ahead when nothing is a candidate
     */
    static long soonest(IntentControl control, MobSession session, long tick, boolean nearOnly) {
        var choices = listed(session);
        var intent = session.intent().orElse(null);
        var intentChoice = intent == null ? null : intentChoice(session, intent);
        var soon = Long.MAX_VALUE;
        for (var index = 0; index < choices.length; index++) {
            var choice = choices[index];
            if (!candidate(session, choice) && (intentChoice == null || !same(choice, intentChoice))) {
                continue;
            }
            var at = Math.max(control.cachedReady(session, choice, index, tick, nearOnly),
                    session.choiceFreeTick(index));
            if (skippedIntent(control, session, intent, intentChoice, choice) && at <= tick) {
                continue;
            }
            soon = Math.min(soon, at);
        }
        return soon == Long.MAX_VALUE ? tick + ReactiveLayer.CYCLE_TICKS : soon;
    }

    private static Turn tryIntent(IntentControl control, MobSession session, long tick, TacticalIntent intent,
                                  SkillChoice[] choices) {
        var intentChoice = intentChoice(session, intent);
        if (intentChoice == null || !control.intentChoiceOpen(session, intent)) {
            return Turn.NEXT;
        }
        var index = indexOf(choices, intentChoice);
        if (!control.ready(session, intentChoice, index, tick) || !stanceOk(session, intentChoice, true)
                || !control.admitted(session, intentChoice, intent.movement())) {
            return Turn.NEXT;
        }
        if (index >= 0 && session.choiceHeld(index, tick) || blocked(session, intentChoice)) {
            return Turn.NEXT;
        }
        var wait = impactUntil(control, session, intent, intentChoice, index, tick);
        if (wait >= 0) {
            if (index >= 0) {
                session.holdChoice(index, wait);
            }
            return Turn.NEXT;
        }
        if (!control.castStartAllowed(session, tick, intentChoice)) {
            control.deferStart(session, tick);
            return Turn.STOP;
        }
        return afterIntent(control, session, tick, index, control.attemptChoice(session, intent, intentChoice, tick,
                true));
    }

    /**
     * The rest of the tick after the intent choice was tried. Only a cast and an expiry end the tick: a refusal that
     * started no cast leaves the start of this tick to the next candidate (M19.4 keeps one start per tick).
     */
    private static Turn afterIntent(IntentControl control, MobSession session, long tick, int index,
                                    ExecutionOutcome outcome) {
        return switch (outcome) {
            case CAST -> {
                session.scheduleReady(soonest(control, session, tick));
                yield Turn.STOP;
            }
            case NOT_ELIGIBLE, SKIPPED_SHIELD -> {
                if (index >= 0) {
                    session.holdChoice(index, tick + 1);
                }
                yield Turn.NEXT;
            }
            case REJECTED_BY_MYTHICMOBS, DEFERRED_INVULNERABLE -> Turn.KEEP;
            case EXPIRED -> Turn.STOP;
        };
    }

    private static boolean tryCandidates(IntentControl control, MobSession session, long tick, TacticalIntent intent,
                                         SkillChoice[] choices) {
        var order = order(control, session, tick, intent, choices);
        var sight = 0;
        for (var place = 0; place < order.length; place++) {
            var index = order[place];
            if (index < 0) {
                break;
            }
            var choice = choices[index];
            if (needsSight(choice)) {
                if (sight == 0) {
                    sight = control.sees(session) ? 1 : -1;
                }
                if (sight < 0) {
                    session.scheduleReady(tick + 1);
                    return true;
                }
            }
            if (finish(control, session, tick, intent, choice, index)) {
                return true;
            }
        }
        return false;
    }

    /** @return whether the tick must stop; a refusal that can try the next candidate returns false */
    private static boolean finish(IntentControl control, MobSession session, long tick, TacticalIntent intent,
                                  SkillChoice choice, int index) {
        if (blocked(session, choice) || held(control, session, intent, choice, index, tick)) {
            return false;
        }
        if (!control.castStartAllowed(session, tick, choice)) {
            control.deferStart(session, tick);
            return true;
        }
        session.armChoice(index);
        var outcome = control.fire(session, choice, intent.movement(), tick);
        if (session.consumeTellHeld()) {
            return true;
        }
        LearnedCast.settle(session, choice, outcome);
        control.tap().readyCast(session, intent, choice.id(), outcome);
        return afterCandidate(control, session, tick, intent, choice, index, outcome);
    }

    /** Holds a candidate that the precision of impact or its slack keeps back, and says so. */
    private static boolean held(IntentControl control, MobSession session, TacticalIntent intent, SkillChoice choice,
                                int index, long tick) {
        var wait = impactUntil(control, session, intent, choice, index, tick);
        if (wait >= 0) {
            session.holdChoice(index, wait);
            return true;
        }
        var slacked = control.slackedReady(session, choice, index, tick);
        if (slacked > tick) {
            session.holdChoice(index, slacked);
            return true;
        }
        return false;
    }

    /** The rest of the tick after a candidate was fired: its cast ends it, and so does a wait the signals ask for. */
    private static boolean afterCandidate(IntentControl control, MobSession session, long tick, TacticalIntent intent,
                                          SkillChoice choice, int index, ExecutionOutcome outcome) {
        if (outcome == ExecutionOutcome.CAST) {
            control.noteCandidateCast(session, choice, tick);
            control.noteHazard(session, choice, tick);
            ReadyExploit.note(control, session, intent, choice, tick);
            session.scheduleReady(soonest(control, session, tick));
            return true;
        }
        var zoneDelay = session.takeZoneDelay();
        if (outcome == ExecutionOutcome.NOT_ELIGIBLE && zoneDelay > 0) {
            session.holdChoice(index, tick + zoneDelay);
            return false;
        }
        if (outcome == ExecutionOutcome.DEFERRED_INVULNERABLE || needsSight(choice)
                && outcome == ExecutionOutcome.NOT_ELIGIBLE) {
            session.scheduleReady(tick + 1);
            return true;
        }
        var until = outcome == ExecutionOutcome.REJECTED_BY_MYTHICMOBS
                ? tick + FallbackStep.localIntentTicks(session.profile().timing())
                : Math.max(tick + 1, control.readyTick(session, choice, index, tick));
        session.holdChoice(index, until);
        return false;
    }

    /**
     * Counts a cast of the intent choice when the pattern factor alone put it first on the launchable subset
     * ({@link ReadyExploit#noteRaised}). Kept here for the intent path of {@link IntentControl#cast}.
     *
     * @param control reads cooldowns and the tap
     * @param session the session
     * @param intent the intent whose choice was cast
     * @param choice the choice that was cast
     * @param tick the server tick
     */
    static void noteRaised(IntentControl control, MobSession session, TacticalIntent intent, SkillChoice choice,
                           long tick) {
        ReadyExploit.noteRaised(control, session, intent, choice, tick);
    }

    private static int[] order(IntentControl control, MobSession session, long tick, TacticalIntent intent,
                               SkillChoice[] choices) {
        var intentChoice = intentChoice(session, intent);
        var now = session.useNow();
        var order = session.orderScratch();
        if (order.length != choices.length) {
            session.listedChoices();
            order = session.orderScratch();
        }
        var count = 0;
        for (var index = 0; index < choices.length; index++) {
            var choice = choices[index];
            if (!same(choice, intentChoice) && launchable(control, session, tick, intent, choice, index)) {
                count = insert(order, count, index, choices, now);
            }
        }
        if (count < order.length) {
            order[count] = -1;
        }
        return order;
    }

    private static boolean preference(IntentControl control, MobSession session, long tick, TacticalIntent intent,
                                      SkillChoice[] choices, boolean ahead) {
        return preferenceBand(control, session, tick, intent, choices, ReactionBand.SURVIVAL, ahead)
                || preferenceBand(control, session, tick, intent, choices, ReactionBand.OPPORTUNITY, ahead);
    }

    private static boolean preferenceBand(IntentControl control, MobSession session, long tick, TacticalIntent intent,
                                          SkillChoice[] choices, ReactionBand band, boolean ahead) {
        var slot = session.slot(band);
        if (slot < 0 || session.slotUntil(band) <= tick
                || session.profile().timing().reactionsAhead().contains(band) != ahead) {
            return false;
        }
        var reaction = REACTIONS[slot];
        var exploit = reaction == Reaction.EXPLOIT_PATTERN;
        var mask = exploit ? session.patternPreference() : ReactionPolicy.mask(reaction);
        if (mask == 0L) {
            return false;
        }
        for (var index = 0; index < choices.length; index++) {
            var choice = choices[index];
            var fits = exploit ? ReadyExploit.fitsCounter(session, choice) : CastPreference.fits(mask, choice);
            if (fits && launchable(control, session, tick, intent, choice, index)
                    && finish(control, session, tick, intent, choice, index)) {
                return true;
            }
        }
        return false;
    }

    /** A candidate that is not held, is off its cooldowns and fits the direction, the stance and the cheap checks. */
    private static boolean launchable(IntentControl control, MobSession session, long tick, TacticalIntent intent,
                                      SkillChoice choice, int index) {
        return candidate(session, choice) && !session.choiceHeld(index, tick)
                && control.ready(session, choice, index, tick)
                && directionOk(session, intent, choice, tick) && stanceOk(session, choice, false)
                && control.admitted(session, choice, intent.movement());
    }

    private static boolean blocked(MobSession session, SkillChoice choice) {
        return TickSignals.bit(session.tickSignals(), TickSignals.TARGET_MISMATCH) && choice.requiresTarget();
    }

    /**
     * Tick to retry a damaging choice, or {@code -1} when nothing asks the engine to wait. The hit of a choice that
     * opens a tell lands when the shown length ends, not after the author's wind-up alone, so such a tell may open
     * while the target is still invulnerable as long as it shows its whole length (A11); the release guard waits again
     * if a new hit renewed the invulnerability ({@link TelegraphRelease}). After the window the signals show, the hit
     * also waits for the windows its own hits in flight will open (E-1, {@link OwnImpacts#freeLanding}), so it neither
     * lands inside one nor opens one over a hit already on its way. A profile of {@link TickLayerMode#CASTS} reads no
     * signals by design: its own hits still wait for one another (D-EF2 b, segunda ronda de efectividad), on the target
     * of the last capture, and the window of the target, which only the signals show, counts as open.
     */
    private static long impactUntil(IntentControl control, MobSession session, TacticalIntent intent,
                                    SkillChoice choice, int index, long tick) {
        var signals = session.tickSignals();
        var read = TickSignals.bit(signals, TickSignals.VALID) && TickSignals.bit(signals, TickSignals.HAS_TARGET);
        if (!read && session.profile().timing().tickLayer() != TickLayerMode.CASTS) {
            return -1L;
        }
        if (choice.nativeFacts().damageIgnoresInvulnerability()) {
            return -1L;
        }
        var purpose = choice.purpose();
        if (purpose.isPresent() && !purpose.get().damaging()) {
            return -1L;
        }
        var hittable = read ? TickSignals.hittableTicks(signals) : 0;
        var impact = TellOpening.impactTicks(session, choice);
        var wait = ImpactTiming.mustWait(hittable, impact) ? tick + (hittable - impact) : -1L;
        var target = session.lastTargetId();
        if (target.isEmpty() || !OwnImpacts.hits(choice)) {
            return wait;
        }
        var landing = (wait >= 0 ? wait : tick) + impact;
        var free = session.ownImpacts().freeLanding(target.get(), landing, OwnImpacts.weight(choice), tick);
        var start = free > landing ? free - impact : wait;
        var stronger = StrongerFirst.until(control, session, intent, choice, index, target.get(), free, tick);
        return stronger > start ? stronger : start;
    }

    /**
     * Whether a typed choice could be cast by this step now as far as its kind, direction and stance go (E-1,
     * {@link StrongerFirst}): the choice of the intent skips the direction rule, as when it is tried.
     *
     * @param session the caster
     * @param intent the current intent
     * @param choice the choice
     * @param tick the current tick
     * @return whether the step could cast it
     */
    static boolean couldFire(MobSession session, TacticalIntent intent, SkillChoice choice, long tick) {
        var own = same(choice, intentChoice(session, intent));
        if (!own && !candidate(session, choice)) {
            return false;
        }
        return (own || directionOk(session, intent, choice, tick)) && stanceOk(session, choice, own);
    }

    /**
     * Whether {@code later} is tried after {@code first} on this tick: the choice of the intent goes before every
     * candidate, and the candidates follow the order of {@link #ahead}.
     *
     * @param session the caster
     * @param intent the current intent
     * @param first a choice
     * @param firstIndex its declaration index
     * @param later another choice
     * @param laterIndex its declaration index
     * @return whether {@code later} comes after {@code first}
     */
    static boolean after(MobSession session, TacticalIntent intent, SkillChoice first, int firstIndex,
                         SkillChoice later, int laterIndex) {
        var intentChoice = intentChoice(session, intent);
        if (same(later, intentChoice) || same(first, intentChoice)) {
            return !same(later, intentChoice);
        }
        return ahead(first, firstIndex, later, laterIndex, session.useNow());
    }

    private static boolean directionOk(MobSession session, TacticalIntent intent, SkillChoice choice, long tick) {
        var purpose = choice.purpose();
        if (purpose.isEmpty()) {
            return true;
        }
        var evade = session.slot(akyrion.thepredict.domain.ReactionBand.SURVIVAL)
                == akyrion.thepredict.domain.Reaction.EVADE_CRITICAL.ordinal()
                && session.slotUntil(akyrion.thepredict.domain.ReactionBand.SURVIVAL) > tick;
        var punish = session.slot(akyrion.thepredict.domain.ReactionBand.OPPORTUNITY)
                == akyrion.thepredict.domain.Reaction.PUNISH_CONSUMING.ordinal()
                && session.slotUntil(akyrion.thepredict.domain.ReactionBand.OPPORTUNITY) > tick;
        return ReadyCastOrder.directionAllows(purpose.get(), intent.movement(),
                session.lastTargetDistanceBlocks().orElse(0.0), session.profile().movement(), evade, punish,
                inReach(session, choice), inMelee(session));
    }

    /**
     * Whether the target of the last capture is inside the reach of a choice (E-2): it has a target and the choice is
     * not among the ones it found out of reach.
     */
    private static boolean inReach(MobSession session, SkillChoice choice) {
        var facts = session.lastMobFacts().orElse(null);
        return facts != null && facts.target().isPresent() && !facts.choicesOutOfReach().contains(choice.id());
    }

    /** Whether the target of the last capture was in the melee band of the mob (E-2). */
    private static boolean inMelee(MobSession session) {
        var facts = session.lastMobFacts().orElse(null);
        return facts != null && facts.target().isPresent()
                && facts.target().get().distance() == akyrion.thepredict.domain.DistanceBand.MELEE;
    }

    /**
     * Stance filter. An idle action and an empty cast map keep the previous order, so a golden does not move.
     */
    private static boolean stanceOk(MobSession session, SkillChoice choice, boolean intentChoice) {
        var action = session.actionStance();
        var settings = session.profile().stances();
        if (action == akyrion.thepredict.domain.ActionStance.NONE && settings.casts().isEmpty()) {
            return true;
        }
        return ReadyCastOrder.stanceAllows(choice, intentChoice, action, session.baseStance(), settings);
    }

    private static int insert(int[] order, int count, int index, SkillChoice[] choices, Set<UseCondition> now) {
        var place = count;
        while (place > 0 && ahead(choices[index], index, choices[order[place - 1]], order[place - 1], now)) {
            order[place] = order[place - 1];
            place--;
        }
        order[place] = index;
        return count + 1;
    }

    private static boolean ahead(SkillChoice left, int leftIndex, SkillChoice right, int rightIndex,
                                 Set<UseCondition> now) {
        var leftTyped = left.purpose().isPresent();
        var rightTyped = right.purpose().isPresent();
        if (leftTyped != rightTyped) {
            return leftTyped;
        }
        if (!leftTyped) {
            return leftIndex < rightIndex;
        }
        var leftAlive = ReadyCastOrder.keepsAlive(left.purpose().get(), now);
        if (leftAlive != ReadyCastOrder.keepsAlive(right.purpose().get(), now)) {
            return leftAlive;
        }
        return ReadyCastOrder.compare(ReadyCastOrder.bestWhenNow(bestWhen(left), now), damage(left), leftIndex,
                ReadyCastOrder.bestWhenNow(bestWhen(right), now), damage(right), rightIndex) < 0;
    }

    private static boolean skippedIntent(IntentControl control, MobSession session, TacticalIntent intent,
                                         SkillChoice intentChoice, SkillChoice choice) {
        return intent != null && same(choice, intentChoice) && !intent.skillConsumed()
                && !control.intentChoiceOpen(session, intent);
    }

    private static Set<UseCondition> bestWhen(SkillChoice choice) {
        var purpose = choice.purpose();
        return purpose.isPresent() ? purpose.get().bestWhen() : Set.of();
    }

    private static DamageTier damage(SkillChoice choice) {
        var purpose = choice.purpose();
        return purpose.isPresent() ? purpose.get().damage() : DamageTier.UNRATED;
    }

    private static boolean needsSight(SkillChoice choice) {
        if (choice.nativeFacts().requiresLineOfSight()) {
            return true;
        }
        var purpose = choice.purpose();
        return purpose.isPresent() && purpose.get().risks().contains(SkillRisk.NEEDS_LINE_OF_SIGHT);
    }

    /** The choice the intent names, {@code null} without one. No allocation. */
    private static SkillChoice intentChoice(MobSession session, TacticalIntent intent) {
        var id = intent.choice();
        return id.isPresent() ? session.profile().choices().get(id.get()) : null;
    }

    private static boolean candidate(MobSession session, SkillChoice choice) {
        if (choice.kind() != ChoiceKind.MYTHIC_SKILL) {
            return false;
        }
        if (choice.purpose().isPresent()) {
            return true;
        }
        return session.profile().timing().untypedCast() == UntypedCastMode.ANY_READY;
    }

    private static SkillChoice[] listed(MobSession session) {
        return session.listedChoices();
    }

    private static int indexOf(SkillChoice[] choices, SkillChoice choice) {
        for (var index = 0; index < choices.length; index++) {
            if (same(choices[index], choice)) {
                return index;
            }
        }
        return -1;
    }

    private static boolean same(SkillChoice left, SkillChoice right) {
        return left == right || right != null && left.id().equals(right.id());
    }
}
