package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ActiveUseConditions;
import akyrion.thepredict.domain.CandidateSet;
import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.DistanceBand;
import akyrion.thepredict.domain.MicroBehaviour;
import akyrion.thepredict.domain.MicroSide;
import akyrion.thepredict.domain.Reaction;
import akyrion.thepredict.domain.ReactionPolicy;
import akyrion.thepredict.domain.TickSignals;
import akyrion.thepredict.domain.HealthBand;
import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.ExecutionOutcome;
import akyrion.thepredict.domain.FallbackReason;
import akyrion.thepredict.domain.InstallResult;
import akyrion.thepredict.domain.MobFacts;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.MobProbe;
import akyrion.thepredict.domain.PruneReason;
import akyrion.thepredict.domain.SessionState;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.StableRandom;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TacticalIntent;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.domain.UseCondition;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * Mutable state of one bound mob (01 §8, M06.1). Server thread only (rule T1); it lives only in {@code engine}
 * (rule A3). {@link SessionRegistry} alone changes its identity and lifecycle state and
 * {@link EncounterRegistry} alone changes its encounter; the decision fields belong to the coordinator of M09
 * and M10, inside this package.
 *
 * <p>Every generation change drops what belonged to the previous incarnation: intent, streaks, target,
 * facts, installed revision, extra choice cooldown, the tick its choice was last consumed, progress reference, the mark
 * of a Jev answer, the mark of a debug intent and the open outcome window, and asks for a new installation.
 */
public final class MobSession {
    /** Tick that means a ready cast is not scheduled (M19.4). */
    static final long NEVER = Long.MAX_VALUE;
    /** Participants whose death one session keeps per encounter, as many as the players of a group clock (A5). */
    static final int DEATHS_KEPT = 16;
    /** The action stances read once, so the per-tick stance read copies no array (PZ-17, C12). */
    private static final akyrion.thepredict.domain.ActionStance[] ACTION_STANCES =
            akyrion.thepredict.domain.ActionStance.values();
    /** The base stances read once, as {@link #ACTION_STANCES}. */
    private static final akyrion.thepredict.domain.BaseStance[] BASE_STANCES =
            akyrion.thepredict.domain.BaseStance.values();
    /** The reactions read once, for the slot of an armed one. */
    private static final Reaction[] REACTIONS = Reaction.values();

    private MobKey key;
    private TacticalProfile profile;
    private SessionState state = SessionState.ACTIVE;
    private Optional<EncounterId> encounter = Optional.empty();
    private Optional<TacticalIntent> intent = Optional.empty();
    private int lowConfidenceStreak;
    private int noProgressStreak;
    private Optional<FallbackReason> lastFallbackReason = Optional.empty();
    private ExecutionOutcome lastOutcome;
    private TacticId outcomeTactic;
    private ControlLevel outcomeLevel;
    private long outcomeStartTick;
    private int outcomeEpoch;
    private OptionalLong choiceConsumedTick = OptionalLong.empty();
    private boolean choiceConsumedByCast;
    private int choiceEpoch;
    private long tacticStartTick;
    private boolean installPending = true;
    private OptionalLong installedRevision = OptionalLong.empty();
    private long nextProbeTick;
    private Optional<UUID> lastTargetId = Optional.empty();
    private OptionalDouble lastTargetDistanceBlocks = OptionalDouble.empty();
    private Optional<MobFacts> lastMobFacts = Optional.empty();
    private long nextChoiceTick;
    private long nextMeleeTick;
    private long nextReadyTick = NEVER;
    private int readyGeneration;
    private long busyUntilTick;
    private final SessionDifficulty difficulty = new SessionDifficulty();
    private final OwnImpacts ownImpacts = new OwnImpacts();
    private long lastCastStartTick = Long.MIN_VALUE;
    private HealthBand probeHealth = HealthBand.HEALTHY;
    private Optional<DistanceBand> probeBand = Optional.empty();
    private long probeTick = Long.MIN_VALUE;
    private long[] choiceHoldUntil = new long[0];
    private int choiceSlots;
    private TacticalProfile listedProfile;
    private SkillChoice[] listedChoices = new SkillChoice[0];
    private int[] orderScratch = new int[0];
    private long[] choiceReadyAt = new long[0];
    private long[] choiceReadTick = new long[0];
    private long[] whiffUntil = new long[0];
    private boolean[] tokenChoices = new boolean[0];
    private long[] readySince = new long[0];
    private final UUID[] deathsSeen = new UUID[DEATHS_KEPT];
    private final boolean[] deathsEngaged = new boolean[DEATHS_KEPT];
    private int deathCount;
    private boolean contextMiss;
    private final EnumSet<UseCondition> capturedNow = EnumSet.noneOf(UseCondition.class);
    private final EnumSet<UseCondition> liveNow = EnumSet.noneOf(UseCondition.class);
    private Optional<TacticId> resolvedTactic = Optional.empty();
    private OptionalDouble progressReferenceBlocks = OptionalDouble.empty();
    private long progressJudgedTick = Long.MIN_VALUE;
    private TacticId stuckTactic;
    private long stuckTick = Long.MIN_VALUE;
    private boolean jevAnswered;
    private boolean debugIntent;
    private Map<TacticId, PruneReason> pruned = Map.of();
    private final SessionOutcomes outcomes = new SessionOutcomes();
    private long packedSignals = TickSignals.PRESENT;
    private final long[] reactionCooldown = new long[Reaction.values().length];
    private final long[] reactionArmedSince = new long[Reaction.values().length];
    private int survivalSlot = ReactionPolicy.NONE;
    private long survivalUntil;
    private int opportunitySlot = ReactionPolicy.NONE;
    private long opportunityUntil;
    private int spacingSlot = ReactionPolicy.NONE;
    private long spacingUntil;
    private int lastGapQuarters;
    private long seed;
    private long signalsAtTick = Long.MIN_VALUE;
    private long microSince = -1;
    private boolean sidestepped;
    private boolean mismatchOpen;
    private Optional<CandidateSet> candidates = Optional.empty();
    private int patternMask;
    private int patternForecast = -1;
    private akyrion.thepredict.domain.PatternMode patternMode = akyrion.thepredict.domain.PatternMode.OBSERVE;
    private boolean patternPass;
    private boolean patternCounterActive;
    private boolean patternReserve;
    private double patternFactor = 1.0;
    private double patternQuota = akyrion.thepredict.domain.PatternSettings.DEFAULT.maxExploitShare();
    private double patternShare;
    private akyrion.thepredict.domain.CandidateScoring candidateScoring =
            akyrion.thepredict.domain.CandidateScoring.DEFAULT;
    private long patternPreference;
    private final ExploitBudget exploitBudget = new ExploitBudget();
    private final akyrion.thepredict.engine.learn.SessionLearning learning =
            new akyrion.thepredict.engine.learn.SessionLearning();
    private final int[] exploitLanded = new int[25];
    private final int[] exploitHits = new int[25];
    private int motivatedPattern = -1;
    private int patternHeld;
    private double[] varietyConfidence = new double[0];
    private int varietyActive = -1;
    private int varietyHeld;
    private int varietyLimit;
    private int varietyChosen;
    private int patternFocus = -1;
    private int patternRestStreak;
    private long patternRestUntil;
    private int patternWindow = 400;
    private int patternVariety = akyrion.thepredict.domain.PatternSettings.DEFAULT.maxExploitedPatterns();
    private int patternRestAfter = 3;
    private int patternRestTicks = 300;
    private int patternMotivated;
    private boolean patternRestPending;
    private long motivatedCastTick = Long.MIN_VALUE;
    private boolean motivatedLanded;
    private boolean lastMotivatedLanded;
    private final PendingExploit pendingExploit = new PendingExploit();
    private long patternSeenTick;
    private MicroBehaviour micro = MicroBehaviour.NATIVE;
    private MicroSide microSide = MicroSide.LEFT;
    private int liveSignature;
    private int captureSignature;
    private boolean captureNoted;
    private long pendingCaptureNanos;
    private int intentCaptureSignature;
    private long intentCaptureNanos;
    private boolean intentCaptureHeld;
    private long stanceState;
    private long baseSinceTick;
    private long actionUntilTick = Long.MAX_VALUE;
    private long nextStanceTick = NEVER;
    private long poiseImmuneUntil;
    private double poiseDamage;
    private long lastPoiseHit = Long.MIN_VALUE;
    private int tellChoice = -1;
    private long releaseTick = NEVER;
    private int telegraphTicks;
    private int tellRecovery;
    private boolean tellFeint;
    private boolean playerTarget;
    private boolean zoneGuard;
    private boolean displaceReady;
    private akyrion.thepredict.domain.ZoneMode zoneMode = akyrion.thepredict.domain.ZoneMode.OFF;
    private akyrion.thepredict.domain.PushDanger pushDanger = akyrion.thepredict.domain.PushDanger.UNKNOWN;
    private akyrion.thepredict.domain.Feasibility answerFeasibility = akyrion.thepredict.domain.Feasibility.UNKNOWN;
    private int pushDeferredTicks;
    private int answerWaitedTicks;
    private final int[] pushByChoice = new int[64];
    private final int[] waitByChoice = new int[64];
    private int armedChoice = -1;
    private int zoneDelay;
    private int pushDeferStep = akyrion.thepredict.domain.ZoneCaps.DEFAULT.pushDeferTicks();
    private int answerRetryStep = akyrion.thepredict.domain.ZoneCaps.DEFAULT.answerRetryTicks();
    private int pushDeferMax = akyrion.thepredict.domain.ZoneCaps.DEFAULT.pushDeferMaxTicks();
    private int answerWaitMax = akyrion.thepredict.domain.ZoneCaps.DEFAULT.answerMaxWaitTicks();
    private int zoneFactOn = akyrion.thepredict.domain.ZoneCaps.DEFAULT.factOnTicks();
    private int zoneFactOff = akyrion.thepredict.domain.ZoneCaps.DEFAULT.factOffTicks();
    private int zoneFactCap = akyrion.thepredict.domain.ZoneCaps.DEFAULT.maxFactsPerMob();
    private int zoneFactPeriod = akyrion.thepredict.domain.ZoneCaps.DEFAULT.factPeriodTicks();
    private long factSampleTick = Long.MIN_VALUE;
    private long factClock;
    private java.util.Set<akyrion.thepredict.domain.ZoneFact> heldZoneFacts = java.util.Set.of();
    private akyrion.thepredict.domain.TickTier cadence = akyrion.thepredict.domain.TickTier.HOT;
    private java.util.function.Function<akyrion.thepredict.domain.TellAnswer,
            akyrion.thepredict.domain.Feasibility> answerProbe = answer -> akyrion.thepredict.domain.Feasibility.UNKNOWN;
    private long notedZoneBits;
    private final akyrion.thepredict.domain.ZoneFactPolicy[] zonePolicies =
            new akyrion.thepredict.domain.ZoneFactPolicy[akyrion.thepredict.domain.ZoneFact.values().length];
    private boolean tellHeld;
    private int tellSequence;
    private int releaseWaits;
    private boolean releaseDeferred;
    private CastInterrupt tellInterrupt = new CastInterrupt();
    private CastInterrupt inFlightInterrupt = new CastInterrupt();
    private java.util.UUID tellTarget;
    private java.util.UUID punishTarget;
    private boolean punished;
    private String authorMirror = "none";
    private String lastNativeStance = "";
    private long mirrorTick = -1;
    private long nativeWriteTick = -1;
    private boolean stanceConflict;
    private boolean conflictPending;
    private long tellBusy;
    private int poiseShown = -1;
    private int windupAnswer = -1;
    private IntentBoard views;
    private int lastDanger = -1;
    private final TellResultRing tellResults = new TellResultRing();
    private final GroupCopy groupCopy = new GroupCopy();
    private GroupBooks groupBooks;
    private long ledgerWaitUntilTick;
    private boolean groupOwnsRung;
    private long groupTellImpact;
    private int groupTellDanger = -1;
    private boolean untoldReservation;
    private double projectileBlocksPerTick;
    private boolean projectileFlightAssumed;
    private final java.util.UUID[] threatWho = new java.util.UUID[16];
    private final double[] threatPoint = new double[16];
    private int threatSampleCount;
    private GroupFightClock groupFight;
    private int groupPlayer = -1;
    private long slotMask = -1L;
    private java.util.UUID relationParent;
    private java.util.UUID relationOwner;
    private boolean relationChildren;
    private int threatTableGroup;
    private boolean inheritsThreatTable;
    private double tableTop;
    private double tableOnTarget;
    private int anchorX;
    private int anchorZ;
    private int threatX;
    private int threatZ;
    private int lookDegrees;
    private boolean anchorKnown;

    MobSession(MobKey key, TacticalProfile profile) {
        this.key = Objects.requireNonNull(key, "key");
        this.profile = Objects.requireNonNull(profile, "profile");
        Arrays.fill(reactionArmedSince, -1L);
    }

    /**
     * Returns the identity of the current incarnation.
     *
     * @return identity of the current incarnation
     */
    public MobKey key() {
        return key;
    }

    /**
     * Returns the tactical profile of the mob type.
     *
     * @return tactical profile of the mob type
     */
    public TacticalProfile profile() {
        return profile;
    }

    /**
     * Returns the lifecycle state.
     *
     * @return lifecycle state; a closed session is no longer in the registry
     */
    public SessionState state() {
        return state;
    }

    /**
     * Returns the encounter the session belongs to.
     *
     * @return encounter the session belongs to, if any
     */
    public Optional<EncounterId> encounter() {
        return encounter;
    }

    /**
     * Returns the intent of this generation.
     *
     * @return intent of this generation, if any
     */
    public Optional<TacticalIntent> intent() {
        return intent;
    }

    /**
     * Returns the number of consecutive answers below the minimum confidence.
     *
     * @return consecutive answers below the minimum confidence
     */
    public int lowConfidenceStreak() {
        return lowConfidenceStreak;
    }

    /**
     * Returns the number of consecutive decisions without movement progress.
     *
     * @return consecutive decisions without movement progress
     */
    public int noProgressStreak() {
        return noProgressStreak;
    }

    /**
     * Returns why the session last dropped a control level.
     *
     * @return why the session last dropped a control level, if it did
     */
    public Optional<FallbackReason> lastFallbackReason() {
        return lastFallbackReason;
    }

    /**
     * Returns the result of the last cast attempt.
     *
     * @return result of the last cast attempt, if any
     */
    public Optional<ExecutionOutcome> lastOutcome() {
        return Optional.ofNullable(lastOutcome);
    }

    /**
     * Tells whether the goal set still has to be installed for this generation.
     *
     * @return whether the goal set still has to be installed for this generation
     */
    public boolean installPending() {
        return installPending;
    }

    /**
     * Returns the catalog revision of the goal set installed for this generation.
     *
     * @return catalog revision of the installed goal set, if it was installed for this generation
     */
    public OptionalLong installedRevision() {
        return installedRevision;
    }

    /**
     * Returns the server tick of the next cheap probe.
     *
     * @return server tick of the next cheap probe
     */
    public long nextProbeTick() {
        return nextProbeTick;
    }

    /**
     * Returns the UUID of the last target seen.
     *
     * @return UUID of the last target seen, if any
     */
    public Optional<UUID> lastTargetId() {
        return lastTargetId;
    }

    /**
     * Returns the distance to the last target.
     *
     * @return distance to the last target in blocks, if any
     */
    public OptionalDouble lastTargetDistanceBlocks() {
        return lastTargetDistanceBlocks;
    }

    /**
     * Returns the facts of the last capture.
     *
     * @return facts of the last capture, if any
     */
    public Optional<MobFacts> lastMobFacts() {
        return lastMobFacts;
    }

    /**
     * Returns the first tick at which a choice may be cast again: the extra cooldown of compatibility
     * ({@code extraCooldownTicks}), kept per instance since M06 (M10.3).
     *
     * @return server tick; a choice cast earlier answers {@code NOT_ELIGIBLE}
     */
    public long nextChoiceTick() {
        return nextChoiceTick;
    }

    /**
     * Returns the tick at which the choice of an intent was last consumed (cast, rejected, skipped against a shield
     * without alternative, or expired): a consumed choice is re-armed only in a later tick, once it is ready again (M10,
     * real-time readiness; DT-28).
     *
     * @return server tick, empty until a choice of this generation was consumed
     */
    public OptionalLong choiceConsumedTick() {
        return choiceConsumedTick;
    }

    /**
     * Returns the target distance read by the previous evaluation of the current tactic, the reference of the
     * {@code NO_PROGRESS} rule (M10.4).
     *
     * @return distance in blocks, empty before the first reading of the tactic
     */
    public OptionalDouble progressReferenceBlocks() {
        return progressReferenceBlocks;
    }

    /**
     * Tells whether Jev answered a question about this generation; until then the local planner covers the wait
     * for the first answer (M10.6).
     *
     * @return {@code true} once an answer about this generation was accepted, adopted or rejected
     */
    public boolean jevAnswered() {
        return jevAnswered;
    }

    /**
     * Tells whether the current intent was set by {@code /thepredict debug intent} (M12.3): the coordinator leaves the
     * session alone until it expires or is released, and never asks Jev about it meanwhile.
     *
     * @return {@code true} while the current intent is a debug intent
     */
    public boolean debugIntent() {
        return debugIntent;
    }

    /**
     * Applies the result of {@code MobControl.ensureInstalled} for this generation (M05.4, M09.1 step 5):
     * {@code Installed} clears the pending mark and keeps the revision; {@code Deferred} keeps the mark to retry;
     * {@code Unsupported} clears the mark without a revision, so the session stays at level 4 until the
     * generation or the catalog revision changes.
     */
    void recordInstall(InstallResult result, long catalogRevision) {
        switch (Objects.requireNonNull(result, "result")) {
            case InstallResult.Installed ignored -> {
                installPending = false;
                installedRevision = OptionalLong.of(catalogRevision);
            }
            case InstallResult.Deferred ignored -> installPending = true;
            case InstallResult.Unsupported ignored -> {
                installPending = false;
                installedRevision = OptionalLong.empty();
            }
        }
    }

    /**
     * Starts the next incarnation with the given profile and drops what belonged to the previous one. Like every
     * invalidation of M20.9, the micro goes back to {@code NATIVE} and the reaction slots and cooldowns, the armed
     * triggers and the candidate set are forgotten (EFO-17), and so are the zone counts of the old choices (EFO-10).
     */
    void nextGeneration(TacticalProfile nextProfile) {
        key = new MobKey(key.entityId(), Math.incrementExact(key.generation()));
        profile = Objects.requireNonNull(nextProfile, "nextProfile");
        intent = Optional.empty();
        lowConfidenceStreak = 0;
        noProgressStreak = 0;
        lastOutcome = null;
        outcomeTactic = null;
        outcomeLevel = null;
        installPending = true;
        installedRevision = OptionalLong.empty();
        outcomes.discardOpen();
        lastTargetId = Optional.empty();
        lastTargetDistanceBlocks = OptionalDouble.empty();
        lastMobFacts = Optional.empty();
        clearCastClock();
        clearTell();
        clearChoiceSlots();
        clearDecisionMarks();
        clearZoneCounts();
        clearDeaths();
        invalidateReady();
        microSince = -1;
        sidestepped = false;
        mismatchOpen = false;
    }

    /**
     * Forgets the cast schedule of the previous incarnation: cooldown marks, the ready tick, the wind-up and the own
     * hits in flight.
     */
    private void clearCastClock() {
        nextChoiceTick = 0;
        nextMeleeTick = 0;
        nextReadyTick = NEVER;
        readyGeneration = key.generation();
        busyUntilTick = 0;
        ownImpacts.clear();
        lastCastStartTick = Long.MIN_VALUE;
        probeHealth = HealthBand.HEALTHY;
        probeBand = Optional.empty();
        probeTick = Long.MIN_VALUE;
    }

    /** Forgets every per-choice slot of the previous profile, so the next listing builds them again. */
    private void clearChoiceSlots() {
        choiceHoldUntil = new long[0];
        choiceSlots = 0;
        listedProfile = null;
        listedChoices = new SkillChoice[0];
        orderScratch = new int[0];
        choiceReadyAt = new long[0];
        choiceReadTick = new long[0];
        whiffUntil = new long[0];
        tokenChoices = new boolean[0];
        readySince = new long[0];
    }

    /** Forgets the decision marks of the previous incarnation: conditions, consumption, progress and captures. */
    private void clearDecisionMarks() {
        contextMiss = false;
        capturedNow.clear();
        liveNow.clear();
        resolvedTactic = Optional.empty();
        choiceConsumedTick = OptionalLong.empty();
        pruned = Map.of();
        choiceConsumedByCast = false;
        tacticStartTick = 0;
        progressReferenceBlocks = OptionalDouble.empty();
        progressJudgedTick = Long.MIN_VALUE;
        stuckTactic = null;
        stuckTick = Long.MIN_VALUE;
        jevAnswered = false;
        debugIntent = false;
        liveSignature = 0;
        captureSignature = 0;
        captureNoted = false;
        pendingCaptureNanos = 0;
        releaseIntentCapture();
    }

    void state(SessionState next) {
        state = Objects.requireNonNull(next, "next");
    }

    void joinEncounter(EncounterId id) {
        if (encounter.filter(id::equals).isEmpty()) {
            clearDeaths();
        }
        encounter = Optional.of(id);
    }

    /**
     * A participant of the encounter of this session died (EFO-06): the memory of M27 judges each player by its own
     * death, never by the death of another player of the same encounter. At most {@value #DEATHS_KEPT} participants
     * are kept; a death past that is not noted.
     *
     * @param participant the participant that died
     * @param engaged whether this mob was fighting that participant when it died
     */
    void noteParticipantDeath(UUID participant, boolean engaged) {
        Objects.requireNonNull(participant, "participant");
        var index = deathIndex(participant);
        if (index >= 0) {
            deathsEngaged[index] |= engaged;
            return;
        }
        if (deathCount < deathsSeen.length) {
            deathsSeen[deathCount] = participant;
            deathsEngaged[deathCount] = engaged;
            deathCount++;
        }
    }

    /**
     * Whether this session saw that participant die in its current encounter.
     *
     * @param participant the participant
     * @return whether its death was noted
     */
    boolean sawDeathOf(UUID participant) {
        return deathIndex(participant) >= 0;
    }

    /**
     * Whether this mob was fighting that participant when it died in the current encounter.
     *
     * @param participant the participant
     * @return whether the death was noted while engaged
     */
    boolean engagedAtDeathOf(UUID participant) {
        var index = deathIndex(participant);
        return index >= 0 && deathsEngaged[index];
    }

    private int deathIndex(UUID participant) {
        for (var index = 0; index < deathCount; index++) {
            if (deathsSeen[index].equals(participant)) {
                return index;
            }
        }
        return -1;
    }

    private void clearDeaths() {
        Arrays.fill(deathsSeen, null);
        Arrays.fill(deathsEngaged, false);
        deathCount = 0;
        clearExploits();
    }

    /**
     * Forgets the pattern-motivated casts of the previous encounter (M26.9, M27): the counters of one fight never
     * reach the memory or the pattern rows of the next one.
     */
    private void clearExploits() {
        Arrays.fill(exploitLanded, 0);
        Arrays.fill(exploitHits, 0);
        motivatedPattern = -1;
    }

    void leaveEncounter() {
        encounter = Optional.empty();
    }

    void adoptIntent(TacticalIntent next) {
        if (!next.mob().equals(key)) {
            throw new IllegalArgumentException("intent of " + next.mob() + " does not belong to " + key);
        }
        intent = Optional.of(next);
    }

    void dropIntent() {
        intent = Optional.empty();
        debugIntent = false;
    }

    void debugIntent(boolean fromDebugCommand) {
        debugIntent = fromDebugCommand && intent.isPresent();
    }

    /**
     * Remembers the target of a capture and its distance; another target empties the ring of own hits in flight,
     * whose windows belong to the previous one (E-1).
     */
    void recordTarget(UUID targetId, double distanceBlocks) {
        lastTargetId = Optional.of(targetId);
        lastTargetDistanceBlocks = OptionalDouble.of(distanceBlocks);
        ownImpacts.retarget(targetId);
    }

    void loseTarget() {
        lastTargetId = Optional.empty();
        lastTargetDistanceBlocks = OptionalDouble.empty();
    }

    void nextProbeTick(long tick) {
        nextProbeTick = tick;
    }

    void fallback(FallbackReason reason) {
        lastFallbackReason = Optional.of(reason);
    }

    void outcome(ExecutionOutcome next) {
        lastOutcome = Objects.requireNonNull(next, "next");
    }

    /**
     * Tells whether a result is new for the intent it belongs to, identified by tactic and start tick (a renewal keeps
     * both) and by how many times its choice was re-armed, so it is counted once per armed choice instead of every
     * reactive cycle (rule C12).
     */
    boolean newOutcomeForIntent(ExecutionOutcome next, TacticId tactic, long startTick) {
        return !(lastOutcome == next && tactic.equals(outcomeTactic) && outcomeStartTick == startTick
                && outcomeEpoch == choiceEpoch);
    }

    /** Remembers the tick at which the choice of the current intent was consumed, and whether a cast consumed it. */
    void choiceConsumed(long tick, boolean cast) {
        choiceConsumedTick = OptionalLong.of(tick);
        choiceConsumedByCast = cast;
    }

    /**
     * Tells whether the last consumption of a choice was a {@code CAST}; any other consumption waits one local intent
     * lifetime before a re-arm, so a rejection or a shield is not retried every reactive cycle.
     */
    boolean choiceConsumedByCast() {
        return choiceConsumedByCast;
    }

    /** Remembers the tick at which the tactic of the current intent started, kept by renewals and replans of it. */
    void tacticStarted(long tick) {
        tacticStartTick = tick;
    }

    /**
     * The tick at which the current tactic started: a local replan for readiness switches tactic only once the current
     * one has lasted the minimum hold (M10, real-time readiness).
     */
    long tacticStartTick() {
        return tacticStartTick;
    }

    /** Counts one re-arm of the choice of a renewed intent, so its next result is counted again. */
    void rearmChoice() {
        choiceEpoch++;
    }

    /** How many times a choice was re-armed in this session, part of the key of a cast result. */
    int choiceEpoch() {
        return choiceEpoch;
    }

    /**
     * Tells whether a result is worth a log line: the result, the tactic or the level changed, so a local replan of the
     * same tactic writes nothing new (rule C8).
     */
    boolean noteworthyOutcome(ExecutionOutcome next, TacticId tactic, ControlLevel level) {
        return !(lastOutcome == next && tactic.equals(outcomeTactic) && outcomeLevel == level);
    }

    /** Keeps the last result of a cast attempt with the key of its intent. */
    void outcome(ExecutionOutcome next, TacticId tactic, ControlLevel level, long startTick) {
        lastOutcome = Objects.requireNonNull(next, "next");
        outcomeTactic = Objects.requireNonNull(tactic, "tactic");
        outcomeLevel = Objects.requireNonNull(level, "level");
        outcomeStartTick = startTick;
        outcomeEpoch = choiceEpoch;
    }

    void streaks(int lowConfidence, int noProgress) {
        lowConfidenceStreak = lowConfidence;
        noProgressStreak = noProgress;
    }

    void facts(MobFacts facts) {
        var tick = factClock;
        factClock += zoneFactPeriod;
        facts(facts, tick);
    }

    /**
     * Stores one capture. Raw zone facts are sampled every {@code factPeriodTicks}, not on every capture.
     *
     * @param facts the capture
     * @param tick server tick of the capture
     */
    void facts(MobFacts facts, long tick) {
        var published = facts.rawZoneBits() == 0L && notedZoneBits == 0L && !facts.rawNoRetreat()
                ? facts : filterZone(facts, tick);
        lastMobFacts = Optional.of(published);
        noteConditions(ActiveUseConditions.of(published, List.of()));
    }

    /**
     * Raw fact bits observed by the zone this tick. The session filter decides what is published.
     *
     * @param bits ordinal bits, without the entropy sign
     */
    public void noteZoneBits(long bits) {
        notedZoneBits = bits;
    }

    private MobFacts filterZone(MobFacts incoming, long tick) {
        var combined = incoming.rawZoneBits() | notedZoneBits;
        var publish = zoneMode == akyrion.thepredict.domain.ZoneMode.FULL && profile.zone().facts();
        if (!publish) {
            heldZoneFacts = java.util.Set.of();
            return incoming.withZone(java.util.Set.of(), incoming.rawNoRetreat(), combined);
        }
        var period = zoneFactPeriod;
        if (cadence == akyrion.thepredict.domain.TickTier.WARM) {
            period *= 2;
        }
        if (cadence == akyrion.thepredict.domain.TickTier.COLD || period < 1
                || factSampleTick != Long.MIN_VALUE && tick - factSampleTick < period) {
            return incoming.withZone(heldZoneFacts, incoming.rawNoRetreat(), combined);
        }
        factSampleTick = tick;
        var kept = java.util.EnumSet.noneOf(akyrion.thepredict.domain.ZoneFact.class);
        var entropy = difficulty().entropy();
        var sample = tick > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) tick;
        for (var fact : akyrion.thepredict.domain.ZoneFact.values()) {
            var raw = (combined & (1L << fact.ordinal())) != 0L
                    || fact == akyrion.thepredict.domain.ZoneFact.NO_SAFE_RETREAT && incoming.rawNoRetreat();
            if (fact == akyrion.thepredict.domain.ZoneFact.TARGET_NEAR_DROP && playerTarget && !entropy) {
                raw = false;
            }
            var policy = zonePolicy(fact.ordinal());
            policy.advance(raw, sample, zoneFactOn, zoneFactOff);
            if (policy.active() && kept.size() < zoneFactCap) {
                kept.add(fact);
            }
        }
        var bits = combined;
        if (entropy && kept.contains(akyrion.thepredict.domain.ZoneFact.TARGET_NEAR_DROP)) {
            bits |= Long.MIN_VALUE;
        }
        heldZoneFacts = java.util.Set.copyOf(kept);
        return incoming.withZone(heldZoneFacts, incoming.rawNoRetreat(), bits);
    }

    private akyrion.thepredict.domain.ZoneFactPolicy zonePolicy(int ordinal) {
        var policy = zonePolicies[ordinal];
        if (policy == null) {
            policy = new akyrion.thepredict.domain.ZoneFactPolicy();
            zonePolicies[ordinal] = policy;
        }
        return policy;
    }

    /**
     * Remembers the use conditions of the last capture. A later probe refreshes only health and distance.
     *
     * @param captured conditions that hold, including the target participant when the capture had one
     */
    void noteConditions(java.util.Set<UseCondition> captured) {
        capturedNow.clear();
        capturedNow.addAll(Objects.requireNonNull(captured, "captured"));
        rebuildNow();
    }

    /**
     * Conditions used to order ready candidates. The caller must not modify the set.
     *
     * @return the conditions that hold for the candidate order
     */
    java.util.Set<UseCondition> useNow() {
        return liveNow;
    }

    /**
     * Conditions of the last capture, without the probe overlay of {@link #useNow()}. The caller must not modify
     * the set.
     *
     * @return the captured conditions
     */
    java.util.Set<UseCondition> conditionsAtCapture() {
        return capturedNow;
    }

    /** The tactic pruning left as the only option of the current question, if it resolved one. */
    Optional<TacticId> resolvedTactic() {
        return resolvedTactic;
    }

    /** Remembers the single tactic a pruned {@code NOW} question kept. */
    void noteResolvedTactic(TacticId tactic) {
        resolvedTactic = Optional.of(Objects.requireNonNull(tactic, "tactic"));
    }

    /** Forgets a resolved tactic when the next plan does not resolve one. */
    void clearResolvedTactic() {
        resolvedTactic = Optional.empty();
    }

    private void rebuildNow() {
        liveNow.clear();
        liveNow.addAll(capturedNow);
        if (probeTick < 0) {
            return;
        }
        liveNow.remove(UseCondition.SELF_LOW_HEALTH);
        liveNow.remove(UseCondition.TARGET_IN_MELEE);
        liveNow.remove(UseCondition.TARGET_AT_RANGE);
        if (probeHealth == HealthBand.CRITICAL) {
            liveNow.add(UseCondition.SELF_LOW_HEALTH);
        }
        if (probeBand.orElse(null) == DistanceBand.MELEE) {
            liveNow.add(UseCondition.TARGET_IN_MELEE);
        } else if (probeBand.orElse(null) == DistanceBand.RANGED) {
            liveNow.add(UseCondition.TARGET_AT_RANGE);
        }
    }

    void lowConfidenceStreak(int streak) {
        lowConfidenceStreak = streak;
    }

    void noProgressStreak(int streak) {
        noProgressStreak = streak;
    }

    void nextChoiceTick(long tick) {
        nextChoiceTick = tick;
    }

    /**
     * The first tick at which a vanilla melee choice may hit again: the attack interval of vanilla after the last
     * {@code CAST} of one (M10, real-time readiness).
     */
    long nextMeleeTick() {
        return nextMeleeTick;
    }

    void nextMeleeTick(long tick) {
        nextMeleeTick = tick;
    }

    /**
     * Tick on which a ready cast may run. {@link #NEVER} means none is scheduled (M19.4).
     *
     * @return the scheduled tick
     */
    long nextReadyTick() {
        return nextReadyTick;
    }

    /**
     * Schedules a ready cast and remembers the generation it belongs to.
     *
     * @param tick tick on which the cast may run
     */
    void scheduleReady(long tick) {
        nextReadyTick = tick;
        readyGeneration = key.generation();
    }

    /** Drops a scheduled ready cast so a closed, suspended or fallen session never fires. */
    void invalidateReady() {
        nextReadyTick = NEVER;
        candidates = Optional.empty();
        micro = MicroBehaviour.NATIVE;
        microSide = MicroSide.LEFT;
        survivalSlot = ReactionPolicy.NONE;
        survivalUntil = 0L;
        opportunitySlot = ReactionPolicy.NONE;
        opportunityUntil = 0L;
        spacingSlot = ReactionPolicy.NONE;
        spacingUntil = 0L;
        Arrays.fill(reactionCooldown, 0L);
        Arrays.fill(reactionArmedSince, -1L);
    }

    /**
     * Generation captured when the ready cast was scheduled.
     *
     * @return that generation
     */
    int readyGeneration() {
        return readyGeneration;
    }

    /**
     * First tick at which another cast may start, after the wind-up of the cast in progress.
     *
     * @return the tick; {@code 0} when nothing is in wind-up
     */
    long busyUntilTick() {
        return busyUntilTick;
    }

    /**
     * Holds the mob until a wind-up ends.
     *
     * @param tick first tick that is free again
     */
    void busyUntil(long tick) {
        busyUntilTick = tick;
    }

    /**
     * Tick of the last cast that started, or {@link Long#MIN_VALUE} when none has.
     *
     * @return that tick
     */
    long lastCastStartTick() {
        return lastCastStartTick;
    }

    /**
     * Remembers the options the last pruned question dropped, for {@code inspect}.
     *
     * @param dropped tactic to the cause that removed it
     */
    void notePruned(Map<TacticId, PruneReason> dropped) {
        pruned = Map.copyOf(Objects.requireNonNull(dropped, "dropped"));
    }

    /**
     * Options the last pruned question dropped.
     *
     * @return an immutable map, empty when pruning is off or nothing was dropped
     */
    Map<TacticId, PruneReason> pruned() {
        return pruned;
    }

    /**
     * Records that a cast started on this tick, so a mob starts at most one.
     *
     * @param tick current tick
     */
    void noteCastStarted(long tick) {
        lastCastStartTick = tick;
    }

    /**
     * Remembers the cheap probe used to refresh {@code SELF_LOW_HEALTH}, {@code TARGET_IN_MELEE} and
     * {@code TARGET_AT_RANGE} for the candidate order (M19.5).
     *
     * @param probe probe of this reactive cycle
     * @param tick current tick
     */
    void noteProbe(MobProbe probe, long tick) {
        probeHealth = Objects.requireNonNull(probe, "probe").health();
        probeBand = probe.targetDistance();
        probeTick = tick;
        rebuildNow();
    }

    /**
     * Health of the last probe.
     *
     * @return the band; {@link HealthBand#HEALTHY} before the first probe
     */
    HealthBand probeHealth() {
        return probeHealth;
    }

    /**
     * Distance band of the last probe.
     *
     * @return the band, empty without a target
     */
    Optional<DistanceBand> probeBand() {
        return probeBand;
    }

    /**
     * Choices of the profile in declaration order. Built once per profile and reused on the due path (M19.4, C12).
     *
     * @return the cached choices
     */
    SkillChoice[] listedChoices() {
        var values = profile.choices();
        if (listedProfile == profile && listedChoices.length == values.size()) {
            return listedChoices;
        }
        var choices = new SkillChoice[values.size()];
        var index = 0;
        for (var choice : values.values()) {
            choices[index++] = choice;
        }
        listedChoices = choices;
        listedProfile = profile;
        orderScratch = new int[choices.length];
        choiceReadyAt = new long[choices.length];
        Arrays.fill(choiceReadyAt, Long.MIN_VALUE);
        choiceReadTick = new long[choices.length];
        Arrays.fill(choiceReadTick, Long.MIN_VALUE);
        whiffUntil = new long[choices.length];
        Arrays.fill(whiffUntil, Long.MIN_VALUE);
        tokenChoices = new boolean[choices.length];
        for (var at = 0; at < choices.length; at++) {
            tokenChoices[at] = GroupCastGate.asksToken(choices[at]);
        }
        readySince = new long[choices.length];
        Arrays.fill(readySince, Long.MIN_VALUE);
        difficulty.bind(choices.length);
        return listedChoices;
    }

    /**
     * Notes one read of the ready tick of a listed choice for the "ready to cast" latency of M19.4. A read that finds
     * the choice not ready yet remembers the tick it will be ready on; a read that finds it ready keeps that earlier
     * tick, or marks this one when nothing earlier was known. So a choice that became ready between two reads counts
     * from the tick it became ready, not from the read that noticed it. No allocation.
     *
     * @param index declaration index; outside the list nothing is noted
     * @param at the ready tick that was read
     * @param tick current tick
     */
    void noteReadyRead(int index, long at, long tick) {
        if (index < 0 || index >= readySince.length) {
            return;
        }
        if (at > tick) {
            readySince[index] = at;
        } else if (readySince[index] == Long.MIN_VALUE || readySince[index] > tick) {
            readySince[index] = tick;
        }
    }

    /**
     * Ticks between the tick a choice was known to be ready and its cast on {@code tick}, and forgets the mark. A
     * choice whose readiness was never read before its cast counts zero.
     *
     * @param index declaration index
     * @param tick tick of the cast
     * @return the latency, {@code >= 0}
     */
    int takeReadyLatency(int index, long tick) {
        if (index < 0 || index >= readySince.length) {
            return 0;
        }
        var since = readySince[index];
        readySince[index] = Long.MIN_VALUE;
        return since == Long.MIN_VALUE || since >= tick ? 0 : (int) Math.min(Integer.MAX_VALUE, tick - since);
    }

    /**
     * Whether the listed choice at a declaration index asks the group ledgers for a token, computed once per profile
     * so the ready tick allocates nothing (C12).
     *
     * @param index declaration index
     * @return whether the choice asks for a token; an index outside the list asks
     */
    boolean asksToken(int index) {
        listedChoices();
        return index < 0 || index >= tokenChoices.length || tokenChoices[index];
    }

    /**
     * Scratch order of candidates. Reused on the due path so a tick does not allocate an {@code int[]} (M19.4, C12).
     *
     * @return the scratch, one slot per cached choice
     */
    int[] orderScratch() {
        return orderScratch;
    }

    /**
     * Cached tick at which a choice was last read as ready, or {@link Long#MIN_VALUE} when it has not been read.
     *
     * @param index declaration index
     * @return that tick
     */
    long choiceReadyAt(int index) {
        return index >= 0 && index < choiceReadyAt.length ? choiceReadyAt[index] : Long.MIN_VALUE;
    }

    /**
     * Cached ready tick of a choice while its read is younger than one reactive cycle, or {@link Long#MIN_VALUE} when
     * it was not read or the read is {@link ReactiveLayer#CYCLE_TICKS} or more ticks old (SA-13). MythicMobs counts
     * cooldowns in wall milliseconds, so below 20 TPS a ready tick read earlier lies further ahead than the real one;
     * a read per cycle keeps the cast within a cycle of the real end of the cooldown.
     *
     * @param index declaration index
     * @param tick current tick
     * @return the cached ready tick, or {@link Long#MIN_VALUE}
     */
    long freshChoiceReady(int index, long tick) {
        if (index < 0 || index >= choiceReadyAt.length) {
            return Long.MIN_VALUE;
        }
        var readAt = choiceReadTick[index];
        if (readAt == Long.MIN_VALUE || readAt > tick || tick - readAt >= ReactiveLayer.CYCLE_TICKS) {
            return Long.MIN_VALUE;
        }
        return choiceReadyAt[index];
    }

    /**
     * Remembers the ready tick of a choice and the tick it was read on, so a recalibration in the same reactive cycle
     * can skip a far read (M19.4, SA-13).
     *
     * @param index declaration index
     * @param at the ready tick
     * @param tick the tick of the read
     */
    void noteChoiceReady(int index, long at, long tick) {
        if (index >= 0 && index < choiceReadyAt.length) {
            choiceReadyAt[index] = at;
            choiceReadTick[index] = tick;
        }
    }

    /**
     * Tick until which a whiff, a lost target, a rejection or a feint holds this choice.
     *
     * @param index declaration index
     * @return that tick, or {@link Long#MIN_VALUE} when the choice is not waiting
     */
    long whiffUntil(int index) {
        return index >= 0 && index < whiffUntil.length ? whiffUntil[index] : Long.MIN_VALUE;
    }

    /**
     * Holds one choice after a tell that did not land. Other choices are not held.
     *
     * @param index declaration index
     * @param until tick the choice may be tried again
     */
    void noteWhiff(int index, long until) {
        if (whiffUntil.length != listedChoices().length) {
            listedChoices();
        }
        if (index >= 0 && index < whiffUntil.length) {
            whiffUntil[index] = until;
        }
    }

    /** The live target was not the captured one, so the encounter should be marked pending (M19.4). */
    void noteContextMiss() {
        contextMiss = true;
    }

    /**
     * Takes the target-miss mark, clearing it.
     *
     * @return whether the last ready step saw a miss
     */
    boolean consumeContextMiss() {
        var missed = contextMiss;
        contextMiss = false;
        return missed;
    }

    /**
     * Sizes the hold of each candidate to the listing of the profile (M19.4). The slots of the attempts that nobody read
     * are gone (EFO-27); the sizing happens once per listing, as before.
     *
     * @param count choices of the listing
     */
    void ensureChoiceSlots(int count) {
        if (choiceSlots == count) {
            return;
        }
        choiceSlots = count;
        choiceHoldUntil = new long[count];
    }

    /**
     * Tells whether a candidate must wait after a refusal that was not a cast.
     *
     * @param index declaration index
     * @param tick current tick
     * @return whether the choice is held
     */
    boolean choiceHeld(int index, long tick) {
        return index < choiceHoldUntil.length && choiceHoldUntil[index] > tick;
    }

    /**
     * Holds a candidate until a tick, so a refusal does not wake the session for that skill alone. A hold never
     * shortens one already in force: the zone delay that the same attempt set (a deferral of 10 ticks) is not
     * replaced by the one-tick hold of a plain refusal (EFO-10).
     *
     * @param index declaration index
     * @param until first tick on which the choice may be tried again
     */
    void holdChoice(int index, long until) {
        if (index >= choiceHoldUntil.length) {
            choiceHoldUntil = Arrays.copyOf(choiceHoldUntil, index + 1);
        }
        choiceHoldUntil[index] = Math.max(choiceHoldUntil[index], until);
    }

    /**
     * Tick until which a candidate is held, or {@code 0} when it is not.
     *
     * @param index declaration index
     * @return that tick
     */
    long choiceFreeTick(int index) {
        return index < choiceHoldUntil.length ? choiceHoldUntil[index] : 0;
    }

    void progressReferenceBlocks(double distanceBlocks) {
        progressReferenceBlocks = OptionalDouble.of(distanceBlocks);
    }

    void clearProgressReference() {
        progressReferenceBlocks = OptionalDouble.empty();
    }

    /**
     * Forgets the progress of the previous tactic: streak and reference distance. The tick of the last counted
     * evaluation stays, so the next one is still {@code minimumDecisionIntervalTicks} later (PZ-07).
     */
    void resetProgress() {
        noProgressStreak = 0;
        progressReferenceBlocks = OptionalDouble.empty();
    }

    /**
     * Whether an evaluation at {@code tick} counts for {@code NO_PROGRESS} (M10.4, PZ-07): the first one, and then
     * one at least {@code intervalTicks} after the previous counted one, so the rule measures time and a cadence that
     * evaluates more often is not stricter.
     *
     * @param tick tick of the evaluation
     * @param intervalTicks {@code minimumDecisionIntervalTicks} of the profile
     * @return whether to judge it
     */
    boolean progressDue(long tick, int intervalTicks) {
        return progressJudgedTick == Long.MIN_VALUE || tick - progressJudgedTick >= intervalTicks;
    }

    void progressJudged(long tick) {
        progressJudgedTick = tick;
    }

    /**
     * Remembers the tactic {@code NO_PROGRESS} dropped to the base AI, so the replan of that same tactic does not send
     * its signal again (PZ-07).
     *
     * @param tactic the dropped tactic
     * @param tick tick of the drop
     */
    void noteStuck(TacticId tactic, long tick) {
        stuckTactic = Objects.requireNonNull(tactic, "tactic");
        stuckTick = tick;
    }

    /**
     * Whether a new intent of {@code tactic} resumes the tactic {@code NO_PROGRESS} just dropped, within
     * {@code windowTicks} of the drop. The mark is spent by the first new tactic either way.
     *
     * @param tactic tactic of the new intent
     * @param tick current tick
     * @param windowTicks how long the drop counts as just now
     * @return whether the signal of this tactic was already sent for this engagement
     */
    boolean resumesStuck(TacticId tactic, long tick, int windowTicks) {
        var resumes = stuckTactic != null && stuckTactic.equals(tactic) && tick - stuckTick <= windowTicks;
        stuckTactic = null;
        return resumes;
    }

    void markAnswered() {
        jevAnswered = true;
    }

    /** The outcome windows of this session (M16.8): the open window and the last closed ones. */
    SessionOutcomes outcomes() {
        return outcomes;
    }

    /**
     * Returns the last closed outcome windows of this session, oldest first, for {@code inspect} (M16.8).
     *
     * @return at most eight summaries
     */
    public List<OutcomeWindow.Summary> recentOutcomes() {
        return outcomes.recent();
    }

    /** Packed tick signals of the last visit. */
    long tickSignals() {
        return packedSignals;
    }

    void tickSignals(long signals) {
        packedSignals = signals;
    }

    long[] reactionCooldown() {
        return reactionCooldown;
    }

    long[] reactionArmedSince() {
        return reactionArmedSince;
    }

    /** Earliest armed reaction whose slot is still inside {@code tick}. */
    int reactionSlot(long tick) {
        var best = ReactionPolicy.NONE;
        best = earlier(best, survivalSlot, survivalUntil, tick);
        best = earlier(best, opportunitySlot, opportunityUntil, tick);
        return earlier(best, spacingSlot, spacingUntil, tick);
    }

    private static int earlier(int best, int slot, long until, long tick) {
        if (slot >= 0 && until > tick && (best < 0 || slot < best)) {
            return slot;
        }
        return best;
    }

    int slot(akyrion.thepredict.domain.ReactionBand band) {
        return switch (band) {
            case SURVIVAL -> survivalSlot;
            case OPPORTUNITY -> opportunitySlot;
            case SPACING -> spacingSlot;
        };
    }

    long slotUntil(akyrion.thepredict.domain.ReactionBand band) {
        return switch (band) {
            case SURVIVAL -> survivalUntil;
            case OPPORTUNITY -> opportunityUntil;
            case SPACING -> spacingUntil;
        };
    }

    void armReaction(int ordinal, long untilTick, long cooldownUntil) {
        if (ordinal >= 0) {
            switch (REACTIONS[ordinal].band()) {
                case SURVIVAL -> {
                    survivalSlot = ordinal;
                    survivalUntil = untilTick;
                }
                case OPPORTUNITY -> {
                    opportunitySlot = ordinal;
                    opportunityUntil = untilTick;
                }
                case SPACING -> {
                    spacingSlot = ordinal;
                    spacingUntil = untilTick;
                }
            }
            reactionCooldown[ordinal] = cooldownUntil;
        }
    }

    int lastGapQuarters() {
        return lastGapQuarters;
    }

    void lastGapQuarters(int quarters) {
        lastGapQuarters = quarters;
    }

    long seed() {
        return seed;
    }

    void reseed(long base) {
        var id = key.entityId();
        seed = StableRandom.seed(base, id.getMostSignificantBits() ^ id.getLeastSignificantBits(),
                key.generation());
    }

    /**
     * Learning state of this session. A fresh session has no held intent and no calibrated floor.
     *
     * @return the session learning
     */
    akyrion.thepredict.engine.learn.SessionLearning learning() {
        return learning;
    }

    Optional<CandidateSet> candidates() {
        return candidates;
    }

    void candidates(CandidateSet set) {
        candidates = Optional.of(Objects.requireNonNull(set, "set"));
    }

    /**
     * Publishes the pattern mask and forecast. Observe writes them here and does not copy them into the intent body.
     *
     * @param mask active bits
     * @param forecast action ordinal, or {@code -1}
     */
    void publishPattern(int mask, int forecast) {
        patternMask = mask;
        patternForecast = forecast;
    }

    int patternMask() {
        return patternMask;
    }

    int patternForecast() {
        return patternForecast;
    }

    akyrion.thepredict.domain.PatternMode patternMode() {
        return patternMode;
    }

    void patternMode(akyrion.thepredict.domain.PatternMode mode) {
        patternMode = Objects.requireNonNull(mode, "mode");
    }

    boolean patternPass() {
        return patternPass;
    }

    void patternPass(boolean pass) {
        patternPass = pass;
    }

    boolean patternCounterActive() {
        return patternCounterActive;
    }

    void patternCounterActive(boolean active) {
        patternCounterActive = active;
    }

    boolean patternReserve() {
        return patternReserve;
    }

    void patternReserve(boolean reserve) {
        patternReserve = reserve;
    }

    double patternFactor() {
        return patternFactor;
    }

    void patternFactor(double factor) {
        patternFactor = factor;
    }

    akyrion.thepredict.domain.CandidateScoring candidateScoring() {
        return candidateScoring;
    }

    void candidateScoring(akyrion.thepredict.domain.CandidateScoring scoring) {
        candidateScoring = Objects.requireNonNull(scoring, "scoring");
    }

    double patternQuota() {
        return patternQuota;
    }

    /**
     * The configured {@code patterns.maxExploitShare}, written by the pattern step (M26.6, DOC-06).
     *
     * @param share the quota in {@code [0, 1]}
     */
    void patternQuota(double share) {
        patternQuota = share;
    }

    void patternShare(double share) {
        patternShare = share;
    }

    double patternShare() {
        return patternShare;
    }

    void patternPreference(long preference) {
        patternPreference = preference;
    }

    long patternPreference() {
        return patternPreference;
    }

    ExploitBudget exploitBudget() {
        return exploitBudget;
    }

    void patternHeld(int held) {
        patternHeld = held;
    }

    int patternHeld() {
        return patternHeld;
    }

    /**
     * The variety choice of the previous pattern step when its inputs are the same now, or {@code -1} when one changed
     * (M26.6, E-11). {@link akyrion.thepredict.domain.VarietyPicker#select} is pure, so its result stands until the
     * active mask, a confidence, the held bits or the cap change. No allocation.
     *
     * @param active bits that are active
     * @param confidence one value per pattern ordinal
     * @param held bits already biased
     * @param limit how many may stay biased
     * @return the kept bits, or {@code -1}
     */
    int keptVariety(int active, double[] confidence, int held, int limit) {
        if (active != varietyActive || held != varietyHeld || limit != varietyLimit
                || !Arrays.equals(confidence, varietyConfidence)) {
            return -1;
        }
        return varietyChosen;
    }

    /**
     * Keeps the inputs and the result of a variety choice for {@link #keptVariety}. The copy of the confidences is
     * allocated once per session.
     *
     * @param active bits that are active
     * @param confidence one value per pattern ordinal
     * @param held bits already biased
     * @param limit how many may stay biased
     * @param chosen the bits {@code select} returned
     */
    void keepVariety(int active, double[] confidence, int held, int limit, int chosen) {
        if (varietyConfidence.length != confidence.length) {
            varietyConfidence = new double[confidence.length];
        }
        System.arraycopy(confidence, 0, varietyConfidence, 0, confidence.length);
        varietyActive = active;
        varietyHeld = held;
        varietyLimit = limit;
        varietyChosen = chosen;
    }

    void patternFocus(int ordinal) {
        patternFocus = ordinal;
    }

    int patternFocus() {
        return patternFocus;
    }

    void patternWindow(int ticks) {
        patternWindow = ticks;
    }

    int patternWindow() {
        return patternWindow;
    }

    /**
     * Stores the cap of patterns exploited at once that the pattern step computed for this session, after the entropy
     * rule ({@code ExploitPolicy.variety}, DAL-04). Until the step stores one, the delivered cap applies.
     *
     * @param cap patterns that may be exploited at once
     * @throws IllegalArgumentException if {@code cap} is below one
     */
    void patternVariety(int cap) {
        if (cap < 1) {
            throw new IllegalArgumentException("cap must be >= 1, was " + cap);
        }
        patternVariety = cap;
    }

    /**
     * The cap of patterns exploited at once, {@code patterns.maxExploitedPatterns} after the entropy rule.
     *
     * @return the cap, at least one
     */
    int patternVariety() {
        return patternVariety;
    }

    void patternRestAfter(int successes) {
        patternRestAfter = successes;
    }

    void patternRestTicks(int ticks) {
        patternRestTicks = ticks;
    }

    boolean patternResting(long tick) {
        return patternRestUntil > tick;
    }

    long patternRestUntil() {
        return patternRestUntil;
    }

    void patternSeenTick(long tick) {
        patternSeenTick = tick;
    }

    long patternSeenTick() {
        return patternSeenTick;
    }

    int patternMotivated() {
        return patternMotivated;
    }

    /**
     * Counts one pattern-motivated cast. It does not decide whether the cast happens.
     *
     * @param pattern pattern index, ignored outside {@code 0..24}
     */
    void notePatternExploit(int pattern) {
        if (pattern < 0 || pattern >= exploitLanded.length) {
            return;
        }
        exploitLanded[pattern]++;
        motivatedPattern = pattern;
    }

    /**
     * Pattern-motivated casts of this encounter for one pattern ({@code m26_pattern_stats.exploited_casts}).
     *
     * @param pattern pattern ordinal
     * @return the count, {@code 0} outside {@code 0..24}
     */
    int exploitCasts(int pattern) {
        return pattern < 0 || pattern >= exploitLanded.length ? 0 : exploitLanded[pattern];
    }

    /**
     * Those casts that a hit on the target settled as landed ({@code exploited_landed}).
     *
     * @param pattern pattern ordinal
     * @return the count, {@code 0} outside {@code 0..24}
     */
    int exploitHits(int pattern) {
        return pattern < 0 || pattern >= exploitHits.length ? 0 : exploitHits[pattern];
    }

    /**
     * Counters of patterns this session exploited. Each hit counts as one landed try.
     *
     * @return the counters, empty when none were noted
     */
    java.util.List<akyrion.thepredict.domain.CounterMemory> exploitCounters() {
        var counters = new java.util.ArrayList<akyrion.thepredict.domain.CounterMemory>();
        for (var pattern = 0; pattern < exploitLanded.length; pattern++) {
            if (exploitLanded[pattern] > 0) {
                var hits = exploitLanded[pattern];
                counters.add(new akyrion.thepredict.domain.CounterMemory(pattern, hits, hits));
            }
        }
        return java.util.List.copyOf(counters);
    }

    /**
     * Counts one cast in the exploit budget. A motivated cast waits for its result (M26.6, E-10): a hit on the target
     * within {@value OutcomeWindow#HIT_WINDOW_TICKS} ticks ({@link #noteMotivatedHit}) adds to the streak that opens a
     * rest, and a cast that did not land, settled by the next cast or once its window passed ({@link #expireMotivated}),
     * clears it.
     *
     * @param tick server tick of the cast
     * @param motivated whether a pattern motivated the cast
     */
    void noteCastQuota(int tick, boolean motivated) {
        settleMotivated(tick);
        exploitBudget.record(tick, motivated);
        patternMotivated = exploitBudget.motivated(tick, patternWindow);
        if (motivated) {
            motivatedCastTick = tick;
            motivatedLanded = false;
        }
    }

    /**
     * Damage dealt to the target: it confirms the pending motivated cast when it comes within the hit window of the
     * outcome windows (M16.8), and the streak moves at once.
     *
     * @param tick server tick of the hit
     */
    void noteMotivatedHit(long tick) {
        if (motivatedCastTick == Long.MIN_VALUE || tick < motivatedCastTick
                || tick - motivatedCastTick > OutcomeWindow.HIT_WINDOW_TICKS) {
            return;
        }
        motivatedLanded = true;
        settleMotivated(tick);
    }

    /**
     * Settles a motivated cast whose hit window has passed without a hit (E-10), so its {@code pattern_exploit} event
     * does not wait for the next cast. The streak moves as the next cast would have moved it.
     *
     * @param tick the server tick
     */
    void expireMotivated(long tick) {
        if (motivatedCastTick != Long.MIN_VALUE && tick - motivatedCastTick > OutcomeWindow.HIT_WINDOW_TICKS) {
            settleMotivated(tick);
        }
    }

    /**
     * Whether the last motivated cast that was settled landed.
     *
     * @return whether it hit
     */
    boolean lastMotivatedLanded() {
        return lastMotivatedLanded;
    }

    /**
     * The {@code pattern_exploit} event that waits for the result of its motivated cast (M26.10, E-10).
     *
     * @return the pending event of this session
     */
    PendingExploit pendingExploit() {
        return pendingExploit;
    }

    private void settleMotivated(long tick) {
        if (motivatedCastTick == Long.MIN_VALUE) {
            return;
        }
        var landed = motivatedLanded;
        motivatedCastTick = Long.MIN_VALUE;
        motivatedLanded = false;
        lastMotivatedLanded = landed;
        pendingExploit.settle(landed);
        if (landed && motivatedPattern >= 0) {
            exploitHits[motivatedPattern]++;
        }
        motivatedPattern = -1;
        patternRestStreak = akyrion.thepredict.domain.RestStreak.next(patternRestStreak, landed);
        if (landed && akyrion.thepredict.domain.RestStreak.opens(patternRestStreak, patternRestAfter)
                && patternRestTicks > 0) {
            patternRestUntil = tick + (long) patternRestTicks;
            patternRestPending = true;
            patternRestStreak = 0;
        }
    }

    boolean takeRest() {
        var pending = patternRestPending;
        patternRestPending = false;
        return pending;
    }

    /**
     * Patterns that may enter a body. Empty unless the effective mode is full and the profile's {@code Patterns.Facts}
     * is on (M26.7, E-2).
     *
     * @return the admitted set
     */
    public java.util.Set<akyrion.thepredict.domain.EnemyPattern> admittedPatterns() {
        if (!profile.patterns().factsShown(patternMode)) {
            return java.util.Set.of();
        }
        return akyrion.thepredict.domain.PatternAdmission.forMode(patternMode, patternMask);
    }

    MicroBehaviour micro() {
        return micro;
    }

    MicroSide microSide() {
        return microSide;
    }

    void micro(MicroBehaviour behaviour, MicroSide side) {
        micro = Objects.requireNonNull(behaviour, "behaviour");
        microSide = Objects.requireNonNull(side, "side");
    }

    long signalsAtTick() {
        return signalsAtTick;
    }

    void signalsAtTick(long tick) {
        signalsAtTick = tick;
    }

    long microSince() {
        return microSince;
    }

    void noteMicro(long tick) {
        microSince = tick;
    }

    boolean sidestepped() {
        return sidestepped;
    }

    void sidestepped(boolean value) {
        sidestepped = value;
    }

    boolean mismatchOpen() {
        return mismatchOpen;
    }

    void mismatchOpen(boolean value) {
        mismatchOpen = value;
    }

    void noteLiveSignature(int signature) {
        liveSignature = signature;
    }

    int liveSignature() {
        return liveSignature;
    }

    void noteCaptureSignature(int signature) {
        captureSignature = signature;
        captureNoted = true;
    }

    int captureSignature() {
        return captureSignature;
    }

    boolean captureNoted() {
        return captureNoted;
    }

    void notePendingCaptureNanos(long nanos) {
        pendingCaptureNanos = nanos;
    }

    long pendingCaptureNanos() {
        return pendingCaptureNanos;
    }

    void holdIntentCapture(int signature, long nanos) {
        intentCaptureSignature = signature;
        intentCaptureNanos = nanos;
        intentCaptureHeld = true;
    }

    void releaseIntentCapture() {
        intentCaptureHeld = false;
        intentCaptureSignature = 0;
        intentCaptureNanos = 0;
    }

    boolean intentCaptureHeld() {
        return intentCaptureHeld;
    }

    int intentCaptureSignature() {
        return intentCaptureSignature;
    }

    long intentCaptureNanos() {
        return intentCaptureNanos;
    }

    /**
     * Pressure state of this session (M22). Bench levers leave M20 unchanged.
     *
     * @return the state
     */
    SessionDifficulty difficulty() {
        return difficulty;
    }

    /**
     * The own hits in flight toward the current target (fase de efectividad E-1): a damaging cast waits until its
     * impact takes nothing from them.
     *
     * @return the ring of this incarnation
     */
    OwnImpacts ownImpacts() {
        return ownImpacts;
    }

    /**
     * Levers of the current rung.
     *
     * @return the levers
     */
    akyrion.thepredict.domain.PressureLevers levers() {
        return difficulty.levers();
    }

    long stanceState() {
        return stanceState;
    }

    void stanceState(long state) {
        stanceState = state;
    }

    long baseSinceTick() {
        return baseSinceTick;
    }

    void baseSinceTick(long tick) {
        baseSinceTick = tick;
    }

    long actionUntilTick() {
        return actionUntilTick;
    }

    void actionUntilTick(long tick) {
        actionUntilTick = tick;
    }

    long nextStanceTick() {
        return nextStanceTick;
    }

    void nextStanceTick(long tick) {
        nextStanceTick = tick;
    }

    akyrion.thepredict.domain.ActionStance actionStance() {
        return ACTION_STANCES[akyrion.thepredict.domain.StanceState.action(stanceState)];
    }

    akyrion.thepredict.domain.BaseStance baseStance() {
        return BASE_STANCES[akyrion.thepredict.domain.StanceState.base(stanceState)];
    }

    /**
     * Whether the target of this tick is a player.
     *
     * @return {@code true} for a player
     */
    public boolean playerTarget() {
        return playerTarget;
    }

    /**
     * Whether entropy is active. Adapters read this instead of the package difficulty state.
     *
     * @return {@code true} when the rung has opened entropy
     */
    public boolean entropyActive() {
        return difficulty.entropy();
    }

    void playerTarget(boolean player) {
        playerTarget = player;
    }

    /** Whether guard or full may arm the edge step. False until a ready zone says so. */
    boolean zoneGuard() {
        return zoneGuard;
    }

    /** Whether a hazard push may arm. False until an entity exploit or entropy says so. */
    boolean displaceReady() {
        return displaceReady;
    }

    /**
     * Stores the zone flags for this tick. Server thread only.
     *
     * @param guard whether the edge step may arm
     * @param displace whether a hazard push may arm
     */
    public void zoneFlags(boolean guard, boolean displace) {
        zoneGuard = guard;
        displaceReady = displace;
    }

    /**
     * Effective zone mode. {@link akyrion.thepredict.domain.ZoneMode#OFF} until a zone is attached.
     *
     * @return the mode
     */
    public akyrion.thepredict.domain.ZoneMode zoneMode() {
        return zoneMode;
    }

    /** Push danger of the current choice direction. Unknown never changes a cast. */
    akyrion.thepredict.domain.PushDanger pushDanger() {
        return pushDanger;
    }

    /** Feasibility of the expected tell. Unknown is not infeasible. */
    akyrion.thepredict.domain.Feasibility answerFeasibility() {
        return answerFeasibility;
    }

    /**
     * Stores the reading of one tick. Server thread only.
     *
     * @param mode effective mode
     * @param danger push danger
     * @param feasibility tell feasibility
     */
    public void zoneReading(akyrion.thepredict.domain.ZoneMode mode, akyrion.thepredict.domain.PushDanger danger,
                     akyrion.thepredict.domain.Feasibility feasibility) {
        zoneMode = mode;
        pushDanger = danger;
        answerFeasibility = feasibility;
        if (danger != akyrion.thepredict.domain.PushDanger.LETHAL) {
            pushDeferredTicks = 0;
            Arrays.fill(pushByChoice, 0);
        }
        if (feasibility != akyrion.thepredict.domain.Feasibility.INFEASIBLE) {
            answerWaitedTicks = 0;
            Arrays.fill(waitByChoice, 0);
        }
    }

    /**
     * Remembers the defer, retry and fact limits of the attached zone.
     *
     * @param deferTicks ticks added on each defer
     * @param retryTicks ticks added on each infeasible retry
     * @param factOn samples before a fact turns on
     * @param factOff samples before a fact turns off
     * @param factCap facts kept per mob
     * @param factPeriod captures between raw samples; warm doubles it and cold samples none
     */
    public void zoneLimits(int deferTicks, int retryTicks, int factOn, int factOff, int factCap, int factPeriod) {
        pushDeferStep = deferTicks;
        answerRetryStep = retryTicks;
        zoneFactOn = factOn;
        zoneFactOff = factOff;
        zoneFactCap = factCap;
        zoneFactPeriod = factPeriod < 1 ? 1 : factPeriod;
    }

    /**
     * Remembers every limit of the attached zone that the cast order reads (E-12, EFO-12): the steps of a deferral
     * and of a retry, the ticks before a skip ({@code zone.pushDeferMaxTicks}) and before a drop
     * ({@code zone.answerMaxWaitTicks}), and the fact limits. The six-argument form keeps the delivered maxima.
     *
     * @param caps the resolved caps of the zone
     */
    public void zoneLimits(akyrion.thepredict.domain.ZoneCaps caps) {
        Objects.requireNonNull(caps, "caps");
        zoneLimits(caps.pushDeferTicks(), caps.answerRetryTicks(), caps.factOnTicks(), caps.factOffTicks(),
                caps.maxFactsPerMob(), caps.factPeriodTicks());
        pushDeferMax = caps.pushDeferMaxTicks();
        answerWaitMax = caps.answerMaxWaitTicks();
    }

    /** Ticks a lethal push may be deferred before it is skipped, {@code zone.pushDeferMaxTicks}. */
    int pushDeferMaxTicks() {
        return pushDeferMax;
    }

    /** Ticks an infeasible answer may be retried before its tell is dropped, {@code zone.answerMaxWaitTicks}. */
    int answerMaxWaitTicks() {
        return answerWaitMax;
    }

    /**
     * The tier used to space raw zone facts. Hot until the tick layer says otherwise.
     *
     * @param tier the tier of this visit
     */
    public void cadence(akyrion.thepredict.domain.TickTier tier) {
        cadence = Objects.requireNonNull(tier, "tier");
    }

    /**
     * The tier stored for zone cadence.
     *
     * @return the tier
     */
    public akyrion.thepredict.domain.TickTier cadence() {
        return cadence;
    }

    /**
     * Q7 for the answer a choice would open. Unknown until a zone supplies the probe.
     *
     * @param probe answer to feasibility
     */
    public void answerProbe(java.util.function.Function<akyrion.thepredict.domain.TellAnswer,
            akyrion.thepredict.domain.Feasibility> probe) {
        answerProbe = Objects.requireNonNull(probe, "probe");
    }

    /**
     * Feasibility of one tell answer on the current ground.
     *
     * @param answer the answer the tell would ask
     * @return the probe, or unknown when no zone is attached
     */
    akyrion.thepredict.domain.Feasibility feasibility(akyrion.thepredict.domain.TellAnswer answer) {
        return answerProbe.apply(Objects.requireNonNull(answer, "answer"));
    }

    /** Holds a backward escape for ten ticks. */
    void noteEscape() {
        zoneDelay = 10;
    }

    /** The choice {@link #fire} is about to consider. */
    void armChoice(int index) {
        armedChoice = index;
    }

    /** Counts one deferral of the armed choice, in {@code pushDeferTicks} steps. */
    void notePushDefer() {
        addChoice(pushByChoice, pushDeferStep);
        pushDeferredTicks += pushDeferStep;
        zoneDelay = pushDeferStep;
    }

    /** Counts one infeasible retry of the armed choice, in {@code answerRetryTicks} steps. */
    void noteAnswerWait() {
        addChoice(waitByChoice, answerRetryStep);
        answerWaitedTicks += answerRetryStep;
        zoneDelay = answerRetryStep;
    }

    /** Ticks already deferred for the armed choice. */
    int pushDeferredTicks() {
        return armedChoice >= 0 && armedChoice < pushByChoice.length ? pushByChoice[armedChoice] : pushDeferredTicks;
    }

    /** Ticks already spent retrying the armed choice. */
    int answerWaitedTicks() {
        return armedChoice >= 0 && armedChoice < waitByChoice.length ? waitByChoice[armedChoice] : answerWaitedTicks;
    }

    /** Delay the last defer or retry asked the next attempt to wait, then clears it. */
    int takeZoneDelay() {
        var delay = zoneDelay;
        zoneDelay = 0;
        return delay;
    }

    /**
     * Gives up the tell of an infeasible answer for the whiff cooldown ({@code Step.DROP}, M24.6): the choice waits
     * like a whiffed one, and its wait starts again from zero afterwards (EFO-10).
     *
     * @param index declaration index of the choice
     * @param until first tick the choice may be tried again
     */
    void dropAnswer(int index, long until) {
        noteWhiff(index, until);
        answerWaitedTicks = 0;
        if (index >= 0 && index < waitByChoice.length) {
            waitByChoice[index] = 0;
        }
    }

    /** Forgets every zone count of the previous incarnation: deferrals, retries, the armed choice and the delay. */
    private void clearZoneCounts() {
        pushDeferredTicks = 0;
        answerWaitedTicks = 0;
        Arrays.fill(pushByChoice, 0);
        Arrays.fill(waitByChoice, 0);
        armedChoice = -1;
        zoneDelay = 0;
    }

    private void addChoice(int[] counts, int step) {
        if (armedChoice < 0) {
            return;
        }
        if (armedChoice >= counts.length) {
            return;
        }
        var sum = counts[armedChoice] + step;
        counts[armedChoice] = sum < 0 ? Integer.MAX_VALUE : sum;
    }

    boolean tellOpen() {
        return tellChoice >= 0;
    }

    int tellChoice() {
        return tellChoice;
    }

    long releaseTick() {
        return releaseTick;
    }

    void releaseTick(long tick) {
        releaseTick = tick;
    }

    int telegraphTicks() {
        return telegraphTicks;
    }

    int tellRecovery() {
        return tellRecovery;
    }

    boolean tellFeint() {
        return tellFeint;
    }

    boolean tellHeld() {
        return tellHeld;
    }

    boolean consumeTellHeld() {
        var held = tellHeld;
        tellHeld = false;
        return held;
    }

    int tellSequence() {
        return tellSequence;
    }

    CastInterrupt tellInterrupt() {
        return tellInterrupt;
    }

    /**
     * Interrupt of the last cast that went out with a tell (SA-02). It outlives {@link #closeTell()}: a poise break
     * cuts it while {@code tick < busyUntilTick()}.
     *
     * @return the flag; a clear one that cuts nothing before the first told cast
     */
    CastInterrupt inFlightInterrupt() {
        return inFlightInterrupt;
    }

    /**
     * Keeps the interrupt of a told cast that went out.
     *
     * @param flag the tell's flag
     */
    void holdInterrupt(CastInterrupt flag) {
        inFlightInterrupt = Objects.requireNonNull(flag, "flag");
    }

    java.util.UUID tellTarget() {
        return tellTarget;
    }

    long tellBusy() {
        return tellBusy;
    }

    void tellBusy(long tick) {
        tellBusy = tick;
    }

    boolean releaseDeferred() {
        return releaseDeferred;
    }

    void releaseDeferred(boolean deferred) {
        releaseDeferred = deferred;
    }

    int releaseWaits() {
        return releaseWaits;
    }

    void releaseWaits(int waits) {
        releaseWaits = waits;
    }

    long poiseImmuneUntil() {
        return poiseImmuneUntil;
    }

    void poiseImmuneUntil(long tick) {
        poiseImmuneUntil = tick;
    }

    double poiseDamage() {
        return poiseDamage;
    }

    void poiseDamage(double damage) {
        poiseDamage = damage;
    }

    long lastPoiseHit() {
        return lastPoiseHit;
    }

    void lastPoiseHit(long tick) {
        lastPoiseHit = tick;
    }

    /**
     * Opens a tell. The caller has already decided the lengths.
     *
     * @param choiceIndex index in the listed choices
     * @param tick opening tick
     * @param shown shown length
     * @param lead ticks before the cast
     * @param recovery recovery after impact
     * @param feint whether the tell will not cast
     * @param target target captured at open
     */
    void openTell(int choiceIndex, long tick, int shown, int lead, int recovery, boolean feint, java.util.UUID target) {
        tellChoice = choiceIndex;
        tellSequence++;
        telegraphTicks = shown;
        tellRecovery = recovery;
        tellFeint = feint;
        tellTarget = target;
        punishTarget = target;
        punished = false;
        tellInterrupt = new CastInterrupt();
        releaseWaits = 0;
        releaseDeferred = false;
        releaseTick = lead > 0 ? tick + lead : NEVER;
        tellHeld = lead > 0;
        nextStanceTick = lead > 0 ? releaseTick : tick + (long) shown;
    }

    /**
     * Closes the tell bookkeeping and keeps the stance, the recovery and the punish target.
     */
    void closeTell() {
        tellChoice = -1;
        releaseTick = NEVER;
        tellHeld = false;
        releaseDeferred = false;
        releaseWaits = 0;
        tellTarget = null;
        tellInterrupt = new CastInterrupt();
        tellResults.forgetOpen();
    }

    /**
     * Result ring of this session. At most two closes wait.
     *
     * @return the ring
     */
    TellResultRing tellResults() {
        return tellResults;
    }

    /**
     * Target that can punish this tell during recovery or stagger.
     *
     * @return the target captured at open, or {@code null}
     */
    java.util.UUID punishTarget() {
        return punishTarget;
    }

    /**
     * Whether the tell's target damaged the mob during recovery or stagger.
     *
     * @return the mark
     */
    boolean punished() {
        return punished;
    }

    /**
     * Sets the punish mark.
     *
     * @param value the mark
     */
    void punished(boolean value) {
        punished = value;
    }

    /**
     * Last poise percent published, or {@code -1} when none was.
     *
     * @return the percent
     */
    int poiseShown() {
        return poiseShown;
    }

    /**
     * Remembers the published poise percent.
     *
     * @param percent 0–100, or {@code -1}
     */
    void poiseShown(int percent) {
        poiseShown = percent;
    }

    /**
     * Binds the board this session republishes when posture, fury, the answer or poise changes.
     *
     * @param board the session board
     */
    void useBoard(IntentBoard board) {
        views = Objects.requireNonNull(board, "board");
    }

    /** Republishes the bound view. A session opened outside the registry does nothing. */
    void publishStanceView() {
        if (views != null) {
            StanceBoard.sync(views, this);
        }
    }

    /**
     * Remembers the answer of the wind-up. Cleared when the action leaves wind-up.
     *
     * @param answer the tell answer
     */
    void windupAnswer(akyrion.thepredict.domain.TellAnswer answer) {
        windupAnswer = Objects.requireNonNull(answer, "answer").ordinal();
    }

    /** Forgets the wind-up answer. */
    void clearWindupAnswer() {
        windupAnswer = -1;
    }

    /**
     * Ordinal of the wind-up answer, or {@code -1} when the action is not winding up.
     *
     * @return the ordinal
     */
    int windupAnswerOrdinal() {
        return windupAnswer;
    }

    /**
     * Danger class of the last cast, or {@code -1} when none was cast.
     *
     * @return {@link akyrion.thepredict.domain.DangerClass#ordinal()}
     */
    int lastDanger() {
        return lastDanger;
    }

    /**
     * Remembers the danger class of a cast that can be blocked.
     *
     * @param ordinal class ordinal
     */
    void lastDanger(int ordinal) {
        lastDanger = ordinal;
    }

    /**
     * Forgets the target that could punish this tell.
     *
     * @param target the target, or {@code null}
     */
    void punishTarget(java.util.UUID target) {
        punishTarget = target;
    }

    /**
     * Author stance read on the probe. {@code none} when it was not read.
     *
     * @return the mirror
     */
    String authorMirror() {
        return authorMirror;
    }

    /**
     * Stores the author stance. Blank becomes {@code none}.
     *
     * @param stance the native stance
     */
    void authorMirror(String stance) {
        authorMirror(stance, mirrorTick);
    }

    /**
     * Stores the author stance read at {@code tick}. A later write is not a conflict until a newer read disagrees.
     *
     * @param stance the native stance
     * @param tick server tick of the read
     */
    void authorMirror(String stance, long tick) {
        authorMirror = stance == null || stance.isBlank() ? "none" : stance;
        mirrorTick = tick;
    }

    /**
     * Tick of the last author-stance read, or {@code -1} when none was read.
     *
     * @return the tick
     */
    long mirrorTick() {
        return mirrorTick;
    }

    /**
     * Last native stance this session wrote, or empty when it has not written one.
     *
     * @return the stance
     */
    String lastNativeStance() {
        return lastNativeStance;
    }

    /**
     * Remembers the native stance just written.
     *
     * @param stance stance passed to {@code setStance}
     */
    void lastNativeStance(String stance, long tick) {
        lastNativeStance = stance == null ? "" : stance;
        nativeWriteTick = tick;
    }

    /**
     * Tick of the last native write, or {@code -1} when none was written.
     *
     * @return the tick
     */
    long nativeWriteTick() {
        return nativeWriteTick;
    }

    /**
     * Whether a native-stance conflict is latched. While it is, write mode does not call {@code setStance}.
     *
     * @return the latch
     */
    boolean stanceConflict() {
        return stanceConflict;
    }

    /**
     * Sets or clears the conflict latch.
     *
     * @param conflict whether the author owns the stance
     */
    void stanceConflict(boolean conflict) {
        stanceConflict = conflict;
    }

    /** Marks a conflict that the log has not reported yet. */
    void markConflictPending() {
        conflictPending = true;
    }

    /**
     * Takes the pending conflict mark.
     *
     * @return whether a new conflict should be logged
     */
    boolean takeConflictPending() {
        var pending = conflictPending;
        conflictPending = false;
        return pending;
    }

    /** Closes the tell and forgets its release. */
    void clearTell() {
        tellChoice = -1;
        releaseTick = NEVER;
        telegraphTicks = 0;
        tellRecovery = 0;
        tellFeint = false;
        tellHeld = false;
        tellTarget = null;
        punishTarget = null;
        punished = false;
        authorMirror = "none";
        lastNativeStance = "";
        mirrorTick = -1;
        nativeWriteTick = -1;
        stanceConflict = false;
        conflictPending = false;
        poiseShown = -1;
        windupAnswer = -1;
        releaseDeferred = false;
        releaseWaits = 0;
        tellInterrupt = new CastInterrupt();
        inFlightInterrupt = new CastInterrupt();
        stanceState = 0L;
        actionUntilTick = Long.MAX_VALUE;
        nextStanceTick = NEVER;
        poiseDamage = 0;
        lastPoiseHit = Long.MIN_VALUE;
        poiseImmuneUntil = 0;
        lastDanger = -1;
        baseSinceTick = 0;
        tellResults.reset();
    }

    /**
     * The group copy of this session. It is never null. A session outside a group keeps the empty copy.
     *
     * @return the copy
     */
    GroupCopy groupCopy() {
        return groupCopy;
    }

    /**
     * Ledgers shared with the encounter, or null when this session is not in a fair group.
     *
     * @return the books
     */
    GroupBooks groupBooks() {
        return groupBooks;
    }

    /**
     * Attaches or clears the encounter books.
     *
     * @param books the books, or null
     */
    void groupBooks(GroupBooks books) {
        groupBooks = books;
    }

    /**
     * Tick a ledger wait holds the cast. Zero means the ledgers are not waiting.
     *
     * @return the tick
     */
    long ledgerWaitUntilTick() {
        return ledgerWaitUntilTick;
    }

    /**
     * Sets the ledger wait. It does not clear {@code readySinceTick}.
     *
     * @param tick the tick
     */
    void ledgerWaitUntilTick(long tick) {
        ledgerWaitUntilTick = tick;
    }

    /**
     * Whether the group clock owns the difficulty rung.
     *
     * @return whether fast adjustment must stay still
     */
    boolean groupOwnsRung() {
        return groupOwnsRung;
    }

    /**
     * Sets the group rung flag. The default is false, which keeps a solo fight on the solo ladder.
     *
     * @param owns whether the group owns the rung
     */
    void groupOwnsRung(boolean owns) {
        groupOwnsRung = owns;
        difficulty.groupOwnsRung(owns);
    }

    /**
     * Remembers the impact and class of the tell the gate just opened.
     *
     * @param impact forecast tick
     * @param danger ordinal of the danger class
     */
    void noteGroupTell(long impact, int danger) {
        groupTellImpact = impact;
        groupTellDanger = danger;
    }

    /**
     * Marks the reservation just stored for a cast that will not open a tell.
     */
    void noteUntoldReservation() {
        untoldReservation = true;
    }

    /**
     * Takes the untold-reservation flag. A second read is false.
     *
     * @return whether a cast without a tell still owns a live row
     */
    boolean takeUntoldReservation() {
        var held = untoldReservation;
        untoldReservation = false;
        return held;
    }

    long groupTellImpact() {
        return groupTellImpact;
    }

    int groupTellDanger() {
        return groupTellDanger;
    }

    /**
     * Declared or read projectile speed of the current skill, in blocks per tick. Zero uses the forecast default of 3.
     *
     * @return the speed
     */
    public double projectileBlocksPerTick() {
        return projectileBlocksPerTick;
    }

    /**
     * Stores the projectile speed of the current skill. Zero clears a speed left by an earlier skill that did not
     * declare {@code velocity} or {@code v}, so the forecast uses 3 and marks the flight assumed.
     *
     * @param blocksPerTick speed of this skill, or 0 when this skill declares none
     */
    public void projectileBlocksPerTick(double blocksPerTick) {
        projectileBlocksPerTick = blocksPerTick > 0.0 && Double.isFinite(blocksPerTick) ? blocksPerTick : 0.0;
    }

    /**
     * Whether the forecast just taken used the unread projectile branch.
     *
     * @return whether that flight was assumed
     */
    boolean projectileFlightAssumed() {
        return projectileFlightAssumed;
    }

    /**
     * Records the branch of the forecast just taken.
     *
     * @param assumed whether the flight was assumed
     */
    void projectileFlightAssumed(boolean assumed) {
        projectileFlightAssumed = assumed;
    }

    /**
     * The group fight clock of the encounter, empty while the session is not in a group.
     *
     * @return the clock
     */
    java.util.Optional<GroupFightClock> groupClock() {
        return java.util.Optional.ofNullable(groupFight);
    }

    /**
     * Attaches the group fight clock and the stable player index. A negative index clears it.
     *
     * @param clock the encounter clock
     * @param player player index, or -1
     */
    void groupFight(GroupFightClock clock, int player) {
        groupFight = clock;
        groupPlayer = player;
    }

    /**
     * Notes damage dealt to or taken from the fight player. The clock opens on the first hit.
     *
     * @param playerId the other entity
     * @param tick server tick
     * @param died whether that player died
     */
    void noteGroupHit(java.util.UUID playerId, long tick, boolean died) {
        if (groupFight == null || groupPlayer < 0 || playerId == null) {
            return;
        }
        groupFight.noteHit(groupPlayer, playerId, tick, died, profile.id());
    }

    /**
     * Whether the published slot is more than a block and a half away.
     *
     * @param far whether micro may choose the slot
     */
    public void noteSlotFar(boolean far) {
        groupCopy.slotFar(far);
    }

    /**
     * Published slot, or {@code -1} when the member has none.
     *
     * @return the packed slot
     */
    public int groupSlotIndex() {
        return groupCopy.slotIndex();
    }

    /**
     * Bits of slots the ready zone rejected. {@code -1} means every slot is valid.
     *
     * @return the mask, bit {@code ring * 16 + angle}
     */
    long slotMask() {
        return slotMask;
    }

    /**
     * Stores the zone mask. A ready zone clears a bit when that column is not walkable or is inside a hazard.
     *
     * @param mask bitset, or {@code -1} when the zone is not ready
     */
    public void noteSlotMask(long mask) {
        slotMask = mask;
    }

    /**
     * Stores the parent, owner and whether a child sits in the encounter.
     *
     * @param parent parent identity, or null
     * @param owner owner identity, or null
     * @param children whether a child identity was kept
     */
    void noteRelations(java.util.UUID parent, java.util.UUID owner, boolean children) {
        relationParent = parent;
        relationOwner = owner;
        relationChildren = children;
    }

    java.util.UUID relationParent() {
        return relationParent;
    }

    java.util.UUID relationOwner() {
        return relationOwner;
    }

    boolean hasRelationChildren() {
        return relationChildren;
    }

    /**
     * Stores the threat-table identity used by one group assignment per table.
     *
     * @param group identity, or 0 when the mob has no table
     * @param inherits whether this mob is a child on the summoner's table
     * @param top threat of the table leader
     * @param onTarget threat of the participant being assigned
     */
    public void noteThreatTable(int group, boolean inherits, double top, double onTarget) {
        threatTableGroup = group;
        inheritsThreatTable = inherits;
        tableTop = top;
        tableOnTarget = onTarget;
    }

    int threatTableGroup() {
        return threatTableGroup;
    }

    boolean inheritsThreatTable() {
        return inheritsThreatTable;
    }

    double tableTop() {
        return tableTop;
    }

    double tableOnTarget() {
        return tableOnTarget;
    }

    /**
     * Stores the threat this mob holds on each participant, at most sixteen.
     *
     * @param who participant ids
     * @param points threat of each id
     * @param count how many entries are filled
     */
    public void noteThreatSamples(java.util.UUID[] who, double[] points, int count) {
        var n = who == null || points == null ? 0 : Math.min(threatWho.length, Math.max(0, count));
        n = Math.min(n, Math.min(who == null ? 0 : who.length, points == null ? 0 : points.length));
        for (var index = 0; index < n; index++) {
            threatWho[index] = who[index];
            threatPoint[index] = points[index];
        }
        threatSampleCount = n;
    }

    int threatSampleCount() {
        return threatSampleCount;
    }

    java.util.UUID threatSampleId(int index) {
        return index < 0 || index >= threatSampleCount ? null : threatWho[index];
    }

    double threatSample(int index) {
        return index < 0 || index >= threatSampleCount ? 0.0 : threatPoint[index];
    }

    /**
     * Stores the columns of the mob and its target, and the target's look in degrees.
     *
     * @param mobX mob column
     * @param mobZ mob column
     * @param targetX target column
     * @param targetZ target column
     * @param look look of the target in degrees
     */
    public void noteAnchor(int mobX, int mobZ, int targetX, int targetZ, int look) {
        anchorX = mobX;
        anchorZ = mobZ;
        threatX = targetX;
        threatZ = targetZ;
        lookDegrees = Math.floorMod(look, 360);
        anchorKnown = true;
    }

    boolean anchorKnown() {
        return anchorKnown;
    }

    int anchorX() {
        return anchorX;
    }

    int anchorZ() {
        return anchorZ;
    }

    int threatX() {
        return threatX;
    }

    int threatZ() {
        return threatZ;
    }

    int lookDegrees() {
        return lookDegrees;
    }
}
