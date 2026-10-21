package akyrion.thepredict.engine;

import akyrion.thepredict.domain.DangerClass;
import akyrion.thepredict.domain.DangerRules;
import akyrion.thepredict.domain.HitShape;
import akyrion.thepredict.domain.ShortWindUp;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.TelegraphEnd;
import akyrion.thepredict.domain.TelegraphResult;
import akyrion.thepredict.domain.TelegraphSummary;

import java.util.UUID;

/**
 * Fixed ring of at most two tell results for one session (M23.10, A5). The slots exist for the life of the session.
 */
final class TellResultRing {
    /** Slots in the ring. */
    static final int CAPACITY = 2;

    private final Slot open = new Slot();
    private final Slot[] pending = {new Slot(), new Slot()};
    private final TelegraphSummary[] drained = new TelegraphSummary[CAPACITY];
    private int count;
    private int drainedCount;
    private long dealtTick = Long.MIN_VALUE;
    private UUID dealtVictim;
    private double dealtRatio;
    private long blockedTick = Long.MIN_VALUE;
    private UUID blockedBy;
    private long punishTick = Long.MIN_VALUE;
    private UUID punishAttacker;
    private double punishRatio;
    private boolean stancePending;
    private int stanceFrom;
    private int stanceTo;
    private int stanceBase;
    private int stanceAction;
    private boolean stanceEnraged;
    private int stanceCause;
    private long stanceDwell;
    private int stanceMode;
    private boolean stanceVariable;
    private boolean stanceNative;
    private String stanceMirror = "none";

    /**
     * How many results may wait.
     *
     * @return 2
     */
    int capacity() {
        return CAPACITY;
    }

    /**
     * How many results are waiting.
     *
     * @return the count, at most {@link #CAPACITY}
     */
    int pending() {
        return count;
    }

    /**
     * Whether a stance line is waiting.
     *
     * @return whether a change was noted and not yet taken
     */
    boolean stancePending() {
        return stancePending;
    }

    /**
     * Remembers the open tell. A later close copies it into the ring.
     *
     * @param session the caster
     * @param choice the choice
     * @param tick opening tick
     * @param shown ticks shown
     * @param lead ticks of advance
     * @param recovery recovery ticks
     * @param feint whether it will not cast
     * @param target target at the open, or {@code null}
     */
    void capture(MobSession session, SkillChoice choice, long tick, int shown, int lead, int recovery, boolean feint,
                 UUID target) {
        var settings = session.profile().telegraph();
        var danger = DangerRules.classify(choice);
        var answer = DangerRules.answer(choice);
        open.clear();
        open.live = true;
        open.choice = session.tellChoice();
        open.choiceId = choice.id().value();
        open.danger = danger.ordinal();
        open.answer = answer.ordinal();
        open.feint = feint;
        open.player = session.playerTarget();
        open.recovery = recovery;
        open.lead = lead;
        open.windup = choice.busyTicks();
        open.shown = shown;
        open.minimum = settings.minimumTicks(danger, answer);
        open.windMode = settings.shortWindUp().ordinal();
        open.channels = choice.nativeFacts().cueChannels();
        open.projectile = choice.nativeFacts().hitShape() == HitShape.PROJECTILE;
        open.windowTicks = settings.resultWindowTicks();
        open.rung = session.difficulty().rung();
        open.start = tick;
        open.profile = session.profile().id();
        open.target = target;
    }

    /**
     * Copies the open tell into the ring. The third waiting result is refused and the open tell is dropped.
     *
     * @param session the caster, or {@code null} when a test offers a slot directly
     * @param impact impact tick
     * @param end how the tell closed
     * @return whether the slot was stored
     */
    boolean enqueue(MobSession session, long impact, TelegraphEnd end) {
        if (!open.live) {
            return false;
        }
        if (count >= CAPACITY) {
            open.clear();
            return false;
        }
        var slot = pending[count];
        slot.copy(open);
        slot.deferred = session != null && session.releaseDeferred();
        var stored = end == TelegraphEnd.RELEASED ? Math.max(open.start + open.shown, impact) : impact;
        slot.impact = stored;
        slot.end = end.ordinal();
        slot.forcedNone = end != TelegraphEnd.RELEASED && end != TelegraphEnd.WHIFFED;
        var span = slot.projectile ? 40 : slot.windowTicks;
        slot.closeAt = slot.forcedNone ? stored : stored + span;
        slot.markDealt(dealtVictim, dealtRatio, dealtTick);
        slot.markBlocked(blockedBy == null ? slot.target : blockedBy, blockedTick);
        slot.markPunish(punishAttacker, punishRatio, punishTick);
        count++;
        open.clear();
        return true;
    }

    /**
     * Offers one result without a live session. Tests use it for the cap and the window edges.
     *
     * @param impact impact tick
     * @param windowTicks result window when the shape is not a projectile
     * @param projectile whether the window is 40 ticks
     * @param target target, or {@code null}
     * @param end how it closed
     * @return whether the slot was stored
     */
    boolean offer(long impact, int windowTicks, boolean projectile, UUID target, TelegraphEnd end) {
        open.clear();
        open.live = true;
        open.windowTicks = windowTicks;
        open.projectile = projectile;
        open.target = target;
        open.profile = "profile";
        open.choiceId = "choice";
        open.danger = DangerClass.MEDIUM.ordinal();
        open.answer = akyrion.thepredict.domain.TellAnswer.BLOCK.ordinal();
        open.windMode = ShortWindUp.LEAD.ordinal();
        return enqueue(null, impact, end);
    }

    /**
     * A hit from this mob. A hit already stored is applied when the tell closes.
     *
     * @param victim damaged entity
     * @param ratio damage over maximum health
     * @param tick server tick
     */
    void noteDealt(UUID victim, double ratio, long tick) {
        dealtTick = tick;
        dealtVictim = victim;
        dealtRatio = ratio;
        for (var index = 0; index < count; index++) {
            pending[index].markDealt(victim, ratio, tick);
        }
    }

    /**
     * The target blocked this mob's melee hit.
     *
     * @param target blocking entity
     * @param tick server tick
     */
    void noteBlocked(UUID target, long tick) {
        blockedTick = tick;
        blockedBy = target;
        for (var index = 0; index < count; index++) {
            pending[index].markBlocked(target, tick);
        }
    }

    /**
     * This mob's melee hit was blocked. The blocker is the tell's target when the caller does not name one.
     *
     * @param tick server tick
     */
    void noteBlocked(long tick) {
        blockedTick = tick;
        blockedBy = null;
        for (var index = 0; index < count; index++) {
            pending[index].markBlocked(pending[index].target, tick);
        }
    }

    /**
     * The tell's target damaged the caster.
     *
     * @param attacker causing entity
     * @param ratio damage over maximum health
     * @param tick server tick
     */
    void notePunish(UUID attacker, double ratio, long tick) {
        punishTick = tick;
        punishAttacker = attacker;
        punishRatio = ratio;
        for (var index = 0; index < count; index++) {
            pending[index].markPunish(attacker, ratio, tick);
        }
    }

    /**
     * Notes one stance change. A second change before the flush replaces it.
     *
     * @param from visible stance before
     * @param to visible stance after
     * @param base base after
     * @param action action after
     * @param enraged enraged after
     * @param cause cause ordinal, or negative when the machine did not name one
     * @param dwell ticks the base had been held
     * @param mode native mode ordinal
     * @param variableWritten whether the variable was written
     * @param nativeWritten whether the native stance was written
     */
    void noteStance(int from, int to, int base, int action, boolean enraged, int cause, long dwell, int mode,
                    boolean variableWritten, boolean nativeWritten, String mirror) {
        stanceFrom = from;
        stanceTo = to;
        stanceBase = base;
        stanceAction = action;
        stanceEnraged = enraged;
        stanceCause = cause;
        stanceDwell = Math.max(0L, dwell);
        stanceMode = mode;
        stanceVariable = variableWritten;
        stanceNative = nativeWritten;
        stanceMirror = mirror == null || mirror.isEmpty() ? "none" : mirror;
        stancePending = true;
    }

    /**
     * Arms the punish window of the open tell and of results still waiting. Recovery can outlast the result window.
     *
     * @param from first tick of recovery or stagger
     * @param until last tick of that action
     */
    void armPunish(long from, long until) {
        if (until < from) {
            return;
        }
        for (var index = 0; index < count; index++) {
            if (pending[index].punishUntil == 0L) {
                pending[index].punishFrom = from;
                pending[index].punishUntil = until;
            }
        }
        if (open.live && open.punishUntil == 0L) {
            open.punishFrom = from;
            open.punishUntil = until;
        }
    }

    /**
     * Takes the pending stance change.
     *
     * @return the change
     */
    akyrion.thepredict.domain.StanceChange takeStance() {
        stancePending = false;
        var cause = stanceCause >= 0 && stanceCause < akyrion.thepredict.domain.StanceCause.values().length
                ? akyrion.thepredict.domain.StanceCause.values()[stanceCause]
                : akyrion.thepredict.domain.StanceCause.TACTIC;
        return new akyrion.thepredict.domain.StanceChange(
                akyrion.thepredict.domain.CombatStance.values()[stanceFrom],
                akyrion.thepredict.domain.CombatStance.values()[stanceTo],
                akyrion.thepredict.domain.BaseStance.values()[stanceBase],
                akyrion.thepredict.domain.ActionStance.values()[stanceAction], stanceEnraged, cause, stanceDwell,
                akyrion.thepredict.domain.StanceNativeMode.values()[stanceMode], stanceMirror, stanceVariable,
                stanceNative);
    }

    /** Drops a stance change that the current record level does not keep. */
    void dropStance() {
        stancePending = false;
    }

    /**
     * Moves due results into {@link #drained(int)}.
     *
     * @param tick server tick
     * @return how many closed
     */
    int drain(long tick) {
        drainedCount = 0;
        var index = 0;
        while (index < count) {
            if (pending[index].dueAt() > tick) {
                index++;
                continue;
            }
            drained[drainedCount++] = pending[index].summarize();
            count--;
            if (index != count) {
                pending[index].copy(pending[count]);
            }
            pending[count].clear();
        }
        return drainedCount;
    }

    /**
     * Drops due results without building a line.
     *
     * @param tick server tick
     */
    void discardDue(long tick) {
        var index = 0;
        while (index < count) {
            if (pending[index].dueAt() > tick) {
                index++;
                continue;
            }
            count--;
            if (index != count) {
                pending[index].copy(pending[count]);
            }
            pending[count].clear();
        }
    }

    /**
     * One drained summary.
     *
     * @param index index under the last {@link #drain(long)}
     * @return the summary
     */
    TelegraphSummary drained(int index) {
        return drained[index];
    }

    /** Drops the open tell and does not touch a result already stored. */
    void forgetOpen() {
        open.clear();
    }

    /** Drops the open tell, the ring and the stance note. A new incarnation calls it. */
    void reset() {
        open.clear();
        for (var slot : pending) {
            slot.clear();
        }
        count = 0;
        drainedCount = 0;
        stancePending = false;
        dealtTick = Long.MIN_VALUE;
        dealtVictim = null;
        blockedTick = Long.MIN_VALUE;
        blockedBy = null;
        punishTick = Long.MIN_VALUE;
        punishAttacker = null;
    }

    private static final class Slot {
        private boolean live;
        private boolean forcedNone;
        private boolean projectile;
        private boolean feint;
        private boolean deferred;
        private boolean player;
        private boolean landed;
        private boolean blocked;
        private boolean punished;
        private int choice;
        private int danger;
        private int answer;
        private int recovery;
        private int lead;
        private int windup;
        private int shown;
        private int minimum;
        private int windMode;
        private int channels;
        private int rung;
        private int windowTicks = 10;
        private int end;
        private long start;
        private long impact;
        private long closeAt;
        private long punishFrom;
        private long punishUntil;
        private double damage;
        private double punishDamage;
        private String profile = "";
        private String choiceId = "";
        private UUID target;

        private void clear() {
            live = false;
            forcedNone = false;
            feint = false;
            deferred = false;
            landed = false;
            blocked = false;
            punished = false;
            damage = 0.0;
            punishDamage = 0.0;
            target = null;
            shown = 0;
            start = 0L;
            punishFrom = 0L;
            punishUntil = 0L;
        }

        private void copy(Slot other) {
            live = true;
            forcedNone = other.forcedNone;
            projectile = other.projectile;
            feint = other.feint;
            deferred = other.deferred;
            player = other.player;
            landed = other.landed;
            blocked = other.blocked;
            punished = other.punished;
            choice = other.choice;
            danger = other.danger;
            answer = other.answer;
            recovery = other.recovery;
            lead = other.lead;
            windup = other.windup;
            shown = other.shown;
            minimum = other.minimum;
            windMode = other.windMode;
            channels = other.channels;
            rung = other.rung;
            windowTicks = other.windowTicks;
            end = other.end;
            start = other.start;
            impact = other.impact;
            closeAt = other.closeAt;
            punishFrom = other.punishFrom;
            punishUntil = other.punishUntil;
            damage = other.damage;
            punishDamage = other.punishDamage;
            profile = other.profile;
            choiceId = other.choiceId;
            target = other.target;
        }

        private void markDealt(UUID victim, double ratio, long tick) {
            if (!hits(victim, tick)) {
                return;
            }
            landed = true;
            damage += ratio;
        }

        private void markBlocked(UUID who, long tick) {
            if (!hits(who, tick)) {
                return;
            }
            blocked = true;
        }

        private void markPunish(UUID attacker, double ratio, long tick) {
            if (target == null || attacker == null || !attacker.equals(target) || punishUntil <= 0L) {
                return;
            }
            if (tick < punishFrom || tick > punishUntil) {
                return;
            }
            punished = true;
            punishDamage += ratio;
        }

        private long dueAt() {
            return punishUntil > closeAt ? punishUntil : closeAt;
        }

        private boolean hits(UUID who, long tick) {
            if (!live || target == null || who == null || !who.equals(target)) {
                return false;
            }
            return tick >= impact - 1 && tick <= closeAt;
        }

        private TelegraphSummary summarize() {
            return new TelegraphSummary(choice, choiceId, TelegraphEnd.values()[end], DangerClass.values()[danger],
                    akyrion.thepredict.domain.TellAnswer.values()[answer], feint, result(), profile,
                    target == null ? "" : target.toString(), kind(), start, lead, windup, shown, minimum,
                    ShortWindUp.values()[windMode], deferred, damage, recovery, punished, punishDamage, channels, rung);
        }

        private String kind() {
            if (target == null) {
                return "none";
            }
            return player ? "player" : "entity";
        }

        private TelegraphResult result() {
            if (forcedNone) {
                return TelegraphResult.NONE;
            }
            if (blocked) {
                return TelegraphResult.BLOCKED;
            }
            if (landed) {
                return TelegraphResult.LANDED;
            }
            if (end == TelegraphEnd.RELEASED.ordinal() || end == TelegraphEnd.WHIFFED.ordinal()) {
                return TelegraphResult.AVOIDED;
            }
            return TelegraphResult.NONE;
        }
    }
}
