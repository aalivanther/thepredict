package akyrion.thepredict.engine;

import akyrion.thepredict.domain.CastSlack;
import akyrion.thepredict.domain.DifficultyGlobals;
import akyrion.thepredict.domain.FightEventKind;
import akyrion.thepredict.domain.DifficultyPolicy;
import akyrion.thepredict.domain.DifficultyPreset;
import akyrion.thepredict.domain.DifficultyResolution;
import akyrion.thepredict.domain.EntropyCurve;
import akyrion.thepredict.domain.PlayMode;
import akyrion.thepredict.domain.PressureCause;
import akyrion.thepredict.domain.PressureLadder;
import akyrion.thepredict.domain.PressureLevers;
import akyrion.thepredict.domain.TtkEstimator;

import java.util.Arrays;

/**
 * Pressure state of one session (M22). Bench levers are the identity of M20. Slack stays unanchored until a damaging
 * choice is otherwise ready.
 */
final class SessionDifficulty {
    private final FightClock clock = new FightClock();
    private DifficultyGlobals globals = DifficultyGlobals.DEFAULT;
    private PressureLevers levers = PressureLevers.BENCH;
    private int rung = initialRung();
    private int outsideStreak;
    private int outsideSign;
    private double prior = DifficultyPolicy.MAX_RUNG;
    private double integrator;
    private long integratorTick = Long.MIN_VALUE;
    private double rateAtLastHit;
    private double frozenRate;
    private boolean rateFrozen;
    private boolean hitSinceCheck;
    private int forcedRung = DifficultyResolution.ABSENT;
    private DifficultyPreset preset = DifficultyPreset.DEFAULT;
    private int targetSeconds = DifficultyPreset.DEFAULT.targetSeconds();
    private PlayMode play = PlayMode.BENCH;
    private boolean entropy;
    private boolean groupOwnsRung;
    private int floorTicks = DifficultyPreset.DEFAULT.reactionFloorTicks();
    private int entropyFloor = DifficultyPreset.DEFAULT.entropyReactionFloorTicks();
    private double adaptStart = DifficultyPreset.DEFAULT.entropyAdaptStartSeconds();
    private boolean ladderTrusted = true;
    private boolean resolved;
    private long[] readySince = new long[0];
    private int publishedPressure = -1;
    private FightEventKind pending;
    private final int[] slowSigns = new int[10];
    private int slowFill;
    private int slowAt;
    private int storedHalvings;
    private int calmFights;
    private int extremeStreak;
    private int extremeSign;
    private int spawnDelta;
    private int noEvidence;
    private PressureCause slowCause;
    private double lastProjected;
    private long rungMarked = Long.MIN_VALUE;
    private double rungWeight;
    private double rungWeighted;
    private long lastLocalTick = Long.MIN_VALUE;
    private String cachedWorldDifficulty = "NORMAL";
    private boolean cachedRegeneration = true;
    private int cachedParty = 1;
    private String cachedWorld = "";
    private DifficultyStats stats = DifficultyStats.NONE;

    FightClock clock() {
        return clock;
    }

    private int initialRung() {
        clock.noteRung(DifficultyPolicy.MAX_RUNG);
        return DifficultyPolicy.MAX_RUNG;
    }

    private void setRung(int next) {
        rung = next;
        clock.noteRung(next);
    }

    void groupOwnsRung(boolean owns) {
        groupOwnsRung = owns;
    }

    PressureLevers levers() {
        return levers;
    }

    int rung() {
        return rung;
    }

    int targetSeconds() {
        return targetSeconds;
    }

    PlayMode play() {
        return play;
    }

    boolean entropy() {
        return entropy;
    }

    int floorTicks() {
        return floorTicks;
    }

    double adaptSeconds(long tick) {
        var elapsed = clock.open() ? clock.elapsed(tick) * TtkEstimator.SECONDS_PER_TICK : 0.0;
        return EntropyCurve.adaptSeconds(adaptStart, elapsed, targetSeconds);
    }

    int reactionNow(long tick) {
        if (!entropy) {
            return levers.reactionDelayTicks();
        }
        var elapsed = clock.open() ? clock.elapsed(tick) * TtkEstimator.SECONDS_PER_TICK : 0.0;
        return EntropyCurve.reactionTicks(floorTicks, entropyFloor, elapsed, targetSeconds);
    }

    int publishedPressure() {
        return publishedPressure;
    }

    void publishedPressure(int pressure) {
        publishedPressure = pressure;
    }

    void globals(DifficultyGlobals globals) {
        this.globals = globals;
        ladderTrusted = PressureLadder.bundledMatches();
    }

    /**
     * Where the fights the slow loop judges are written (M22.6, JS point 7).
     *
     * @param sink the shared sink of the registry
     */
    void stats(DifficultyStats sink) {
        stats = java.util.Objects.requireNonNull(sink, "sink");
    }

    void forceRung(int rungOrAbsent) {
        forcedRung = rungOrAbsent;
    }

    void levers(PressureLevers next) {
        levers = next;
    }

    String presetName() {
        return preset.displayName();
    }

    boolean nativeAllowed() {
        return globals.nativeLevelAllowed();
    }

    int spawnDelta() {
        return spawnDelta;
    }

    int noEvidence() {
        return noEvidence;
    }

    PressureCause slowCause() {
        return slowCause;
    }

    double projectedSeconds() {
        return lastProjected;
    }

    String cachedWorldDifficulty() {
        return cachedWorldDifficulty;
    }

    boolean cachedRegeneration() {
        return cachedRegeneration;
    }

    int cachedParty() {
        return cachedParty;
    }

    String cachedWorld() {
        return cachedWorld;
    }

    void rememberContext(String worldDifficulty, boolean naturalRegeneration, int partySize, String worldName) {
        cachedWorldDifficulty = worldDifficulty;
        cachedRegeneration = naturalRegeneration;
        cachedParty = partySize;
        cachedWorld = worldName;
    }

    /**
     * Question or local interval. Entropy uses {@code min(c(t), normal)}; every other mode keeps {@code normal}.
     *
     * @param normalTicks the profile interval
     * @param tick current tick
     * @return the interval in ticks
     */
    int effectiveInterval(int normalTicks, long tick) {
        if (!entropy) {
            return normalTicks;
        }
        return EntropyCurve.effectiveIntervalTicks(adaptSeconds(tick), normalTicks);
    }

    /**
     * Whether the local plan may change tactic now. Outside entropy it is always due. The fallback asks only when a
     * change of tactic would happen, so a true answer marks a real replan; giving the same tactic its ready choice back
     * never asks (PZ-11).
     *
     * @param tick current tick
     * @param normalTicks the local interval of the profile
     * @return whether the planner may change tactic
     */
    boolean localDue(long tick, int normalTicks) {
        if (!entropy) {
            return true;
        }
        var gap = effectiveInterval(normalTicks, tick);
        if (lastLocalTick != Long.MIN_VALUE && tick - lastLocalTick < gap) {
            return false;
        }
        lastLocalTick = tick;
        return true;
    }

    void bind(int choiceCount) {
        if (readySince.length != choiceCount) {
            readySince = new long[choiceCount];
            Arrays.fill(readySince, CastSlack.UNANCHORED);
        }
    }

    long ready(int index, long nativeAt, long now, boolean termsAllow, int slack, boolean damaging) {
        if (index < 0 || index >= readySince.length || !damaging || slack <= 0) {
            return nativeAt;
        }
        readySince[index] = CastSlack.anchor(readySince[index], now, termsAllow);
        return CastSlack.ready(nativeAt, readySince[index], slack, true);
    }

    void clearAnchors() {
        Arrays.fill(readySince, CastSlack.UNANCHORED);
    }

    /**
     * Resolves preset, mode and levers for this visit. Entity fights stay on the bench identity.
     *
     * @param tick current tick
     * @param player whether the target is a player
     * @param profileDelay profile reaction delay
     * @param profileMin minimum rung
     * @param profileMax maximum rung
     * @param profileMode mode, or the global default when {@code null}
     * @param profileTarget profile target seconds, or {@link DifficultyResolution#ABSENT}
     * @param worldPreset world preset, or {@code null}
     * @param variablePreset variable preset, or {@code null}
     * @param mobPreset mob-file preset, or {@code null}
     * @param worldEntropy world entropy, or {@code null}
     * @param mobEntropy mob-file entropy
     * @param variableSeconds variable target seconds, or {@link DifficultyResolution#ABSENT}
     */
    void resolve(long tick, boolean player, int profileDelay, int profileMin, int profileMax,
                 akyrion.thepredict.domain.DifficultyMode profileMode, int profileTarget, DifficultyPreset worldPreset,
                 DifficultyPreset variablePreset, DifficultyPreset mobPreset, Boolean worldEntropy, boolean mobEntropy,
                 int variableSeconds) {
        var mode = profileMode == null ? globals.defaultMode() : profileMode;
        preset = DifficultyResolution.preset(worldPreset, variablePreset, mobPreset, globals.defaultPreset());
        var numbers = globals.numbers(preset);
        var imposed = worldPreset != null;
        targetSeconds = DifficultyResolution.targetSeconds(imposed, numbers.targetSeconds(), variableSeconds,
                profileTarget, numbers.targetSeconds());
        floorTicks = numbers.reactionFloorTicks();
        entropyFloor = numbers.entropyReactionFloorTicks();
        adaptStart = numbers.entropyAdaptStartSeconds();
        entropy = player && DifficultyResolution.entropy(worldEntropy, mobEntropy);
        play = entropy ? PlayMode.BENCH : DifficultyResolution.play(mode, player);
        if (!ladderTrusted && play == PlayMode.PLAYER) {
            play = PlayMode.BENCH;
        }
        var effective = play == PlayMode.PLAYER && !entropy;
        if (forcedRung != DifficultyResolution.ABSENT) {
            setRung(Math.max(profileMin, Math.min(profileMax, forcedRung)));
        } else if (!effective) {
            setRung(DifficultyPolicy.MAX_RUNG);
        }
        levers = leversFor(tick, player, profileDelay);
    }

    /** Levers of the resolved mode: entropy's curve, the group's rung, the bench identity or the ladder (M22.7). */
    private PressureLevers leversFor(long tick, boolean player, int profileDelay) {
        if (entropy && player) {
            return new PressureLevers(reactionNow(tick), akyrion.thepredict.domain.ReactionMask.ALL, 0, true, 0);
        }
        var floor = player ? floorTicks : 0;
        if (groupOwnsRung && play == PlayMode.PLAYER) {
            return PressureLadder.groupLevers(rung, profileDelay, floor, player);
        }
        if (play == PlayMode.BENCH) {
            return player ? PressureLadder.levers(DifficultyPolicy.MAX_RUNG, profileDelay, floor, true)
                    : PressureLadder.bench(Math.max(profileDelay, floor));
        }
        return PressureLadder.levers(rung, profileDelay, floor, player);
    }

    /**
     * Fast loop. Returns the cause when the rung moves, or {@code null}.
     *
     * @param tick current tick
     * @param busy whether the caster is busy
     * @param minRung minimum rung
     * @param maxRung maximum rung
     * @param inFight whether the profile allows the fast loop
     * @return the cause, or {@code null}
     */
    PressureCause adjust(long tick, boolean busy, int minRung, int maxRung, boolean inFight) {
        if (groupOwnsRung || play != PlayMode.PLAYER || entropy || !inFight) {
            return null;
        }
        var half = TtkEstimator.scaledHalfLifeTicks(globals.estimatorHalfLifeTicks(), targetSeconds);
        var decayed = integratorTick == Long.MIN_VALUE ? 0.0
                : TtkEstimator.decay(integrator, integratorTick, tick, half);
        markRung(tick);
        var estimate = TtkEstimator.project(decayed, clock.dealt(), clock.healthRemaining(), clock.startTick(), tick,
                half, globals.minEvidenceTicks(), targetSeconds, globals.projectedTtkCapRatio());
        lastProjected = estimate.valid() ? estimate.seconds() : 0.0;
        var evidence = estimate.valid() && hitSinceCheck;
        hitSinceCheck = false;
        var before = rung;
        var next = DifficultyPolicy.fast(new DifficultyPolicy.Fast(rung, outsideStreak, outsideSign),
                estimate.seconds(), targetSeconds, globals.toleranceRatio(), evidence, busy, minRung, maxRung);
        outsideStreak = next.outsideStreak();
        outsideSign = next.sign();
        if (next.rung() == before) {
            return busy && evidence ? PressureCause.IN_FIGHT : null;
        }
        setRung(next.rung());
        return PressureCause.IN_FIGHT;
    }

    void noteFight(FightEventKind event) {
        if (event == null) {
            return;
        }
        pending = event;
        if (event == FightEventKind.ENGAGED) {
            resetFight(clock.startTick());
        }
    }

    /**
     * Closes the whole-fight clock and, when the fight counted, moves the slow loop. The row of the fight goes to the
     * sink without a profile; production closes through the overload with one.
     *
     * @param kind {@link FightEventKind#MOB_DIED} or {@link FightEventKind#TARGET_DIED}
     * @param tick tick of the close
     * @param minRung lowest rung
     * @param maxRung highest rung
     * @param learnEnabled whether the profile allows the slow loop
     */
    void closeAs(FightEventKind kind, long tick, int minRung, int maxRung, boolean learnEnabled) {
        closeAs(kind, tick, minRung, maxRung, learnEnabled, "");
    }

    /**
     * Closes the whole-fight clock and, when the fight counted, moves the slow loop. A fight the slow loop judges
     * writes its {@code m22_difficulty_stats} row under {@code profile} (M22.6): counted, censored or excluded.
     *
     * @param kind {@link FightEventKind#MOB_DIED} or {@link FightEventKind#TARGET_DIED}
     * @param tick tick of the close
     * @param minRung lowest rung
     * @param maxRung highest rung
     * @param learnEnabled whether the profile allows the slow loop
     * @param profile profile id of the session
     */
    void closeAs(FightEventKind kind, long tick, int minRung, int maxRung, boolean learnEnabled, String profile) {
        markRung(tick);
        var seconds = clock.open() ? clock.elapsed(tick) * TtkEstimator.SECONDS_PER_TICK : 0.0;
        var event = kind == FightEventKind.MOB_DIED ? clock.mobDied() : clock.targetDied(tick);
        if (event.isEmpty()) {
            return;
        }
        pending = event.get();
        if (learnEnabled && play == PlayMode.PLAYER) {
            judge(kind, seconds, minRung, maxRung, profile);
        }
        finishFight(tick);
    }

    /** The slow loop on one closed fight of player play, and its row (M22.8, M22.6). */
    private void judge(FightEventKind kind, double seconds, int minRung, int maxRung, String profile) {
        var played = fightRung();
        if (kind == FightEventKind.MOB_DIED && !rateFrozen) {
            noEvidence++;
            pending = FightEventKind.MOB_DIED_NO_EVIDENCE;
            stats.excluded(profile, fightContext(), played, prior);
            return;
        }
        var observed = kind == FightEventKind.TARGET_DIED ? seconds : censored(seconds);
        if (!(observed > 0.0)) {
            return;
        }
        var logTtk = StrictMath.log(observed);
        learn(logTtk, true, minRung, maxRung);
        stats.counted(profile, fightContext(), played, logTtk, kind == FightEventKind.MOB_DIED, prior);
    }

    /** The rung a fight was played at: its time-weighted mean, within the ladder. */
    private int fightRung() {
        var mean = (int) Math.round(fightMean());
        return Math.max(0, Math.min(DifficultyPolicy.MAX_RUNG, mean));
    }

    /** The {@code v1:} context of a fight of player play, with the world and party the last resolve read. */
    private String fightContext() {
        return akyrion.thepredict.domain.DifficultyContext.of(akyrion.thepredict.domain.TargetKind.PLAYER, cachedParty,
                cachedWorldDifficulty);
    }

    private double censored(double seconds) {
        var cap = globals.projectedTtkCapRatio() * targetSeconds;
        var extra = frozenRate > 0.0 ? clock.healthRemaining() / frozenRate : cap;
        return Math.min(seconds + extra, cap);
    }

    private void finishFight(long tick) {
        rateFrozen = false;
        frozenRate = 0.0;
        resetFight(tick);
    }

    void noteHit(long tick, double fraction) {
        var half = TtkEstimator.scaledHalfLifeTicks(globals.estimatorHalfLifeTicks(), targetSeconds);
        if (integratorTick == Long.MIN_VALUE) {
            integrator = fraction;
        } else {
            integrator = TtkEstimator.hit(integrator, integratorTick, tick, fraction, half);
        }
        integratorTick = tick;
        var estimate = TtkEstimator.project(integrator, clock.dealt(), clock.healthRemaining(), clock.startTick(),
                tick, half, globals.minEvidenceTicks(), targetSeconds, globals.projectedTtkCapRatio());
        if (estimate.valid()) {
            frozenRate = estimate.ratePerSecond();
            rateFrozen = true;
        }
        rateAtLastHit = rateFrozen ? frozenRate : 0.0;
        hitSinceCheck = true;
    }

    double rateAtLastHit() {
        return rateAtLastHit;
    }

    double prior() {
        return prior;
    }

    void learn(double logTtk, boolean counted, int minRung, int maxRung) {
        if (!counted || play != PlayMode.PLAYER) {
            return;
        }
        var signal = DifficultyPolicy.dampings(reversals(), globals.dampingReversals());
        if (signal > 0) {
            storedHalvings = signal;
            calmFights = 0;
            slowCause = PressureCause.DAMPED;
        } else {
            calmFights++;
            if (calmFights >= 20 && storedHalvings > 0) {
                storedHalvings--;
                calmFights = 0;
            }
        }
        var gain = DifficultyPolicy.dampedGain(globals.effectiveGain(), storedHalvings);
        var delta = DifficultyPolicy.deltaU(logTtk, StrictMath.log(targetSeconds), globals.deadbandRatio(), gain,
                DifficultyGlobals.LADDER_K, true);
        rememberSign(delta > 0.0 ? 1 : delta < 0.0 ? -1 : 0);
        prior = DifficultyPolicy.nextU(fightMean(), delta, minRung, maxRung);
        setRung((int) Math.round(prior));
        setRung(Math.max(minRung, Math.min(maxRung, rung)));
        noteExtreme(logTtk, minRung, maxRung);
    }

    private void noteExtreme(double logTtk, int minRung, int maxRung) {
        var gap = StrictMath.log(targetSeconds) - logTtk;
        var outside = Math.abs(gap) > StrictMath.log(1.0 + globals.deadbandRatio());
        var mark = 0;
        if (outside && rung == minRung && gap > 0.0) {
            mark = -1;
        } else if (outside && rung == maxRung && gap < 0.0) {
            mark = 1;
        }
        if (mark == 0 || mark != extremeSign) {
            extremeSign = mark;
            extremeStreak = mark == 0 ? 0 : 1;
            if (mark == 0) {
                spawnDelta = 0;
            }
            return;
        }
        extremeStreak++;
        if (extremeStreak >= 5) {
            spawnDelta = mark;
            slowCause = mark < 0 ? PressureCause.TARGET_UNREACHABLE_TOO_STRONG
                    : PressureCause.TARGET_UNREACHABLE_TOO_WEAK;
        }
    }

    private void rememberSign(int sign) {
        slowSigns[slowAt] = sign;
        slowAt = (slowAt + 1) % slowSigns.length;
        if (slowFill < slowSigns.length) {
            slowFill++;
        }
    }

    private int reversals() {
        var previous = 0;
        var changes = 0;
        var start = slowFill < slowSigns.length ? 0 : slowAt;
        for (var step = 0; step < slowFill; step++) {
            var sign = slowSigns[(start + step) % slowSigns.length];
            if (sign == 0 || sign == previous) {
                continue;
            }
            if (previous != 0) {
                changes++;
            }
            previous = sign;
        }
        return changes;
    }

    private void markRung(long tick) {
        if (rungMarked == Long.MIN_VALUE) {
            rungMarked = tick;
            return;
        }
        var span = tick - rungMarked;
        if (span > 0) {
            rungWeighted += rung * (double) span;
            rungWeight += span;
        }
        rungMarked = tick;
    }

    private double fightMean() {
        return rungWeight > 0.0 ? rungWeighted / rungWeight : prior;
    }

    private void resetFight(long tick) {
        rungWeighted = 0.0;
        rungWeight = 0.0;
        rungMarked = tick;
    }

    /**
     * One visit. Levers are rebuilt on the staggered adjust tick, not on every tick. A forced rung is applied on the
     * tick it is set and then only on the adjust tick, so the reads of M22 stay off the per-tick path; the fast loop
     * does not move a forced rung. A fight event is built only while the tap records.
     *
     * @param session the session
     * @param tick current tick
     * @param reader combat reader
     * @param tap records a rung change
     * @param board republishes pressure when the rung changes
     */
    void touch(MobSession session, long tick, CombatReader reader, DecisionTap tap, IntentBoard board) {
        var disengaged = clock.quiet(tick, globals.disengageTicks());
        if (disengaged.isPresent()) {
            pending = disengaged.get();
            if (play == PlayMode.PLAYER && session.profile().difficulty().learn()) {
                stats.excluded(session.profile().id(), fightContext(), fightRung(), prior);
            }
        }
        if (entropy && resolved) {
            var delay = reactionNow(tick);
            if (levers.reactionDelayTicks() != delay) {
                levers = new PressureLevers(delay, akyrion.thepredict.domain.ReactionMask.ALL, 0, true, 0);
            }
        }
        var interval = Math.max(1, globals.adjustIntervalTicks());
        var due = Math.floorMod(tick + session.key().entityId().hashCode(), interval) == 0;
        flushEvent(session, tick, reader, tap);
        if (resolved && !due && !forcedPending(session)) {
            return;
        }
        var before = rung;
        resolveFrom(session, tick, reader);
        if (forcedRung == DifficultyResolution.ABSENT) {
            var settings = session.profile().difficulty();
            adjust(tick, tick < session.busyUntilTick(), settings.minRung(), settings.maxRung(), settings.inFight());
        }
        report(session, before, tap, board);
    }

    private void flushEvent(MobSession session, long tick, CombatReader reader, DecisionTap tap) {
        if (pending == null) {
            return;
        }
        if (tap.recording()) {
            tap.fightEvent(session.key(), pending, clock.open() ? clock.elapsed(tick) : 0L, rung, play.name(),
                    targetSeconds, fightFacts(session, reader));
        }
        pending = null;
    }

    /** A forced rung the session does not hold yet: the command takes effect on this tick, once. */
    private boolean forcedPending(MobSession session) {
        if (forcedRung == DifficultyResolution.ABSENT) {
            return false;
        }
        var settings = session.profile().difficulty();
        return rung != Math.max(settings.minRung(), Math.min(settings.maxRung(), forcedRung));
    }

    /** The reads of M22 and the resolution they feed: preset, mode, target seconds, entropy and training. */
    private void resolveFrom(MobSession session, long tick, CombatReader reader) {
        var settings = session.profile().difficulty();
        var variable = reader.targetSecondsOverride(session.key());
        var variableSeconds = DifficultyResolution.ABSENT;
        if (variable.isPresent()) {
            var seconds = (int) variable.getAsDouble();
            if (seconds >= 5 && seconds <= 3600) {
                variableSeconds = seconds;
            }
        }
        var worldName = reader.worldName(session.key());
        rememberContext(reader.worldDifficulty(session.key()), reader.naturalRegeneration(session.key()),
                reader.partySize(session.key()), worldName);
        var imposed = worldName.isEmpty() ? null : globals.worlds().get(worldName);
        var target = session.lastTargetId();
        resolve(tick, target.isPresent() && reader.playerTarget(target.get()),
                session.profile().timing().reactionDelayTicks(), settings.minRung(), settings.maxRung(),
                settings.mode().orElse(null),
                settings.targetSeconds().isPresent() ? settings.targetSeconds().getAsInt() : DifficultyResolution.ABSENT,
                imposed == null ? null : imposed.preset().orElse(null),
                reader.difficultyOverride(session.key()).orElse(null),
                reader.declaredPreset(session.key()).orElse(null),
                imposed == null || imposed.entropy().isEmpty() ? null : imposed.entropy().get(),
                reader.declaredEntropy(session.key()), variableSeconds);
        session.learning().markTraining(reader.declaredTraining(session.key()));
        resolved = true;
    }

    private void report(MobSession session, int before, DecisionTap tap, IntentBoard board) {
        if (rung == before && slowCause != PressureCause.TARGET_UNREACHABLE_TOO_STRONG
                && slowCause != PressureCause.TARGET_UNREACHABLE_TOO_WEAK && slowCause != PressureCause.DAMPED) {
            return;
        }
        var cause = rung != before ? PressureCause.IN_FIGHT : slowCause;
        tap.pressureChanged(session.key(), before, rung, cause, lastProjected, targetSeconds, 0);
        if (rung != before) {
            publish(session, board);
        }
        if (cause != PressureCause.IN_FIGHT) {
            slowCause = null;
        }
    }

    private FightFacts fightFacts(MobSession session, CombatReader reader) {
        var encounter = session.encounter().map(id -> Long.toString(id.value())).orElse("");
        var targetId = session.lastTargetId().orElse(null);
        var target = targetId == null ? "" : targetId.toString();
        var kind = targetId == null ? "" : reader.playerTarget(targetId) ? "player" : "entity";
        return new FightFacts(encounter, session.profile().id(), target, kind, clock.healthRemaining(),
                cachedWorldDifficulty, cachedRegeneration, cachedParty);
    }

    private void publish(MobSession session, IntentBoard board) {
        var view = board.current(session.key().entityId());
        var next = play == PlayMode.PLAYER ? rung : -1;
        if (view.pressure() == next) {
            return;
        }
        publishedPressure = next;
        board.publish(session.key(), view.withPressure(next));
    }
}
