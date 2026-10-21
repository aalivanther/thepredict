package akyrion.thepredict.engine.learn;

import akyrion.thepredict.domain.CandidateScoring;
import akyrion.thepredict.domain.HeldIntent;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.learn.ConfidenceGate;
import akyrion.thepredict.domain.learn.ExploreArm;
import akyrion.thepredict.domain.learn.ExploreChoice;
import akyrion.thepredict.domain.learn.LearnCause;
import akyrion.thepredict.domain.learn.LearnedScope;
import akyrion.thepredict.domain.learn.VoiArm;

import java.util.List;
import java.util.Optional;

/**
 * Per-session learning state (M28). Held on {@code MobSession} so constructors do not grow (rule W5). Absent held
 * intent and a raw confidence floor leave every existing decision unchanged.
 */
public final class SessionLearning {
    private final CreditLedger credit = new CreditLedger();
    private final DamageRateRing damage = new DamageRateRing();
    private HeldIntent held;
    private double confidenceFloor = Double.NaN;
    private int window;
    private ValueTable table = ValueTable.empty();
    private boolean tableActive;
    private int profileCode;
    private long contextFine;
    private ExploreArm explore = ExploreArm.NONE;
    private VoiArm voi = VoiArm.OFF;
    private ExploreChoice exploreChoice;
    private List<TacticId> exploreOptions = List.of();
    private boolean wouldSkip;
    private LearnCause skipCause;
    private CandidateScoring baseScoring = CandidateScoring.PRIORITY;
    private long patternContext = Long.MIN_VALUE;
    private InFightWeights weights;
    private boolean training;
    private int openPatterns;
    private String learnedSource = "";
    private String learnedExtra = "";
    private LearnedArtifacts.State artifact = LearnedArtifacts.State.NONE;
    private String artifactVerdict = LearnedScope.PASS_SIM_ONLY;
    private String learnedScope = "bench";
    private boolean audienceKnown;
    private boolean playersPresent;
    private boolean playerJoined;
    private ConfidenceGate gate = ConfidenceGate.RAW;
    private ConfidenceCalibrator calibrator;
    private akyrion.thepredict.domain.learn.CalibrationKey calibrationKey;
    private String calibrationModel = "";
    private double calibrationTarget = Double.NaN;
    private boolean voiUsable;
    private double voiGap;
    private double voiSdBest;
    private double voiSdSecond;
    private int voiSeen;
    private int voiAgree;
    private double voiDelta;
    private boolean voiForbidden = true;
    private int exploredCount;
    private int adoptionCount;
    private int consecutiveSkips;
    private boolean skippedThisCapture;
    private boolean learnedPending;
    private int captures;
    private akyrion.thepredict.domain.distill.DistillMode distillMode =
            akyrion.thepredict.domain.distill.DistillMode.OFF;
    private boolean distillCovered;
    private java.util.Optional<akyrion.thepredict.domain.distill.OodCause> distillOod = java.util.Optional.empty();
    private akyrion.thepredict.domain.distill.DistillSource distillSource =
            akyrion.thepredict.domain.distill.DistillSource.LOCAL_ORDER;
    private String distillTactic = "";
    private long distillSeed;
    private int auditEvery = 10;
    private boolean groupQuestion;
    private int delegated;
    private boolean captureDelegated;
    private int patternFacts;
    private boolean patternNone;
    private String regimeKey = "r1:ALL";
    private boolean collectTeaching;
    private String contextText = "0";
    private int capturedSignature;
    private String windowContext = "";
    private String capturedLeader = "";
    private String windowLeader = "";
    private double rateBefore;
    private final akyrion.thepredict.engine.distill.DistillSession distillSession =
            new akyrion.thepredict.engine.distill.DistillSession();

    /**
     * Creates a session with no held intent and no active table.
     */
    public SessionLearning() {
    }

    /**
     * The credit ledger.
     *
     * @return the ledger
     */
    public CreditLedger credit() {
        return credit;
    }

    /**
     * The damage ring.
     *
     * @return the ring
     */
    public DamageRateRing damage() {
        return damage;
    }

    /**
     * The held intent, if a skip adopted one.
     *
     * @return the intent, or empty
     */
    public Optional<HeldIntent> held() {
        return Optional.ofNullable(held);
    }

    /**
     * Installs a held intent. A live value-of-information skip is counted toward the skips in a row, which the next
     * capture closes with {@link #noteCaptured(int)}.
     *
     * @param intent the intent
     */
    public void hold(HeldIntent intent) {
        held = intent;
        if (intent.behavior() == akyrion.thepredict.domain.BehaviorPolicy.VOI_SKIP) {
            skippedThisCapture = true;
        }
    }

    /**
     * Drops the held intent.
     */
    public void release() {
        held = null;
    }

    /**
     * Next window id suffix.
     *
     * @return the counter after increment
     */
    public int nextWindow() {
        return ++window;
    }

    /**
     * Confidence floor. NaN means the caller should use the profile floor, which is what the default does.
     *
     * @param profileFloor the profile minimum
     * @return the calibrated floor, or {@code profileFloor} when none was installed
     */
    public double confidenceFloor(double profileFloor) {
        if (!Double.isNaN(confidenceFloor)) {
            return confidenceFloor;
        }
        if (gate != ConfidenceGate.CALIBRATED || calibrator == null || calibrationKey == null) {
            return profileFloor;
        }
        var parity = Double.isNaN(calibrationTarget) || calibrationTarget < 0.50 || calibrationTarget > 0.99;
        return calibrator.threshold(calibrationKey, calibrationModel, profileFloor, parity, calibrationTarget)
                .rawThreshold();
    }

    /**
     * Installs a calibrator so a calibrated gate compares {@code c*} rather than the raw floor.
     *
     * @param mode the confidence gate
     * @param model the calibrator
     * @param key the calibration key
     * @param liveModel the live model name
     * @param target the profile floor
     */
    public void useCalibrator(ConfidenceGate mode, ConfidenceCalibrator model,
                              akyrion.thepredict.domain.learn.CalibrationKey key, String liveModel, double target) {
        gate = mode;
        calibrator = model;
        calibrationKey = key;
        calibrationModel = liveModel;
        calibrationTarget = target;
    }

    /**
     * Installs a calibrated raw threshold. The default path never calls this.
     *
     * @param floor raw confidence floor
     */
    public void setConfidenceFloor(double floor) {
        confidenceFloor = floor;
    }

    /**
     * Whether a value artifact may act in this capture. The default is false, so local planning is unchanged. An
     * activated table acts only when {@link LearnedScope#allows} holds for its verdict, the learning scope and the
     * players of the encounter (rules A16 and W8). Before a caller reports the audience with {@link #audience}, a
     * player who ever joined counts as present, so the guard errs toward the base order.
     *
     * @return true after {@link #activate(ValueTable, int, long)} while the verdict covers the audience
     */
    public boolean tableActive() {
        var players = audienceKnown ? playersPresent : playerJoined;
        return tableActive && LearnedScope.allows(learnedScope, artifactVerdict, players);
    }

    /**
     * Reports whether a player takes part in the encounter of this capture. The learned table and exploration read it.
     *
     * @param present whether a player is a participant now
     */
    public void audience(boolean present) {
        audienceKnown = true;
        playersPresent = present;
    }

    /**
     * The active table. Empty until activation.
     *
     * @return the table
     */
    public ValueTable table() {
        return table;
    }

    /**
     * Profile code used by the active table.
     *
     * @return the code
     */
    public int profileCode() {
        return profileCode;
    }

    /**
     * Packed fine context of the active table.
     *
     * @return the context
     */
    public long contextFine() {
        return contextFine;
    }

    /**
     * Installs an active value table. Only {@code candidateScoring: learned} reads it.
     *
     * @param installed the table
     * @param profile profile code
     * @param fine packed fine context
     */
    public void activate(ValueTable installed, int profile, long fine) {
        table = installed;
        tableActive = true;
        profileCode = profile;
        contextFine = fine;
    }

    /**
     * The explore arm. {@link ExploreArm#NONE} until a training capture arms one, and always {@link ExploreArm#NONE}
     * while a player takes part and the mob is not training (rule A16), whatever was armed before.
     *
     * @return the arm
     */
    public ExploreArm explore() {
        var players = audienceKnown ? playersPresent : playerJoined;
        return players && !training ? ExploreArm.NONE : explore;
    }

    /**
     * Arms exploration for the next capture. The default path never calls this.
     *
     * @param arm the arm
     */
    public void armExplore(ExploreArm arm) {
        explore = arm;
    }

    /**
     * Marks the accepted Jev answer so a later capture does not replace it.
     */
    public void noteAcceptedJev() {
        explore = explore.accepted();
    }

    /**
     * The skip arm. {@link VoiArm#OFF} until a session arms one.
     *
     * @return the arm
     */
    public VoiArm voi() {
        return voi;
    }

    /**
     * Arms a skip for the next capture. {@code off} is the default and asks.
     *
     * @param arm the arm
     */
    public void armVoi(VoiArm arm) {
        voi = arm;
    }

    /**
     * Records the exploration choice, including its propensity.
     *
     * @param choice the choice
     */
    public void noteExplore(ExploreChoice choice) {
        exploreChoice = choice;
    }

    /**
     * Remembers the options the stratum sampled from, before pruning clears the resolved tactic.
     *
     * @param options options of the {@code NOW} question
     */
    public void noteExploreOptions(List<TacticId> options) {
        exploreOptions = List.copyOf(options);
    }

    /**
     * Options saved with the exploration choice.
     *
     * @return the options, or empty
     */
    public List<TacticId> exploreOptions() {
        return exploreOptions;
    }

    /**
     * Captures so far. The first one never skips.
     *
     * @return the count
     */
    public int captures() {
        return captures;
    }

    /**
     * Counts this capture and stores its signature for the next one. A capture that held a live skip adds one to the
     * skips in a row; any other capture asked Jev and resets them (M28.8, condition 5).
     *
     * @param signature live signature of this capture
     */
    public void noteCaptured(int signature) {
        capturedSignature = signature;
        captures++;
        consecutiveSkips = skippedThisCapture ? consecutiveSkips + 1 : 0;
        skippedThisCapture = false;
    }

    /**
     * Whether the signature moved after the previous capture.
     *
     * @param signature live signature now
     * @return {@code true} when a later capture sees a different signature
     */
    public boolean signatureMoved(int signature) {
        return captures > 0 && signature != capturedSignature;
    }

    /**
     * Snapshots the context and the damage rate at the moment a window opens. A window opens on every adopted intent
     * that is not the base AI, so this also counts the adoption for the exploration share of M28.5.
     *
     * @param context context text
     * @param rate damage per tick before the window
     */
    public void noteWindow(String context, double rate) {
        windowContext = context;
        rateBefore = rate;
        adoptionCount++;
    }

    /**
     * Context snapshotted when the window opened.
     *
     * @return the text, or empty when no window was opened
     */
    public String windowContext() {
        return windowContext;
    }

    /**
     * Notes the learned leader of this capture: the option of the {@code NOW} question with the highest value in the
     * active table, or empty without one (M28.2, {@code jev_agree}).
     *
     * @param tactic the tactic id, or empty
     */
    public void noteCaptureLeader(String tactic) {
        capturedLeader = java.util.Objects.requireNonNull(tactic, "tactic");
    }

    /**
     * Snapshots, for the window that opens, the leader noted at the capture its answer replied to, so a later capture
     * does not change what the window compares with. A window that did not ask Jev with its {@code NOW} question
     * compares with nothing.
     *
     * @param askedJev whether the window opens on the answer of Jev to the {@code NOW} question
     */
    public void noteWindowLeader(boolean askedJev) {
        windowLeader = askedJev ? capturedLeader : "";
    }

    /**
     * Whether the tactic of the closing window is the learned leader its opening snapshotted.
     *
     * @param tactic tactic id of the window
     * @return {@code false} without a leader
     */
    public boolean agreesWithLeader(String tactic) {
        return !windowLeader.isEmpty() && windowLeader.equals(tactic);
    }

    /**
     * Damage rate snapshotted when the window opened.
     *
     * @return the rate
     */
    public double rateBefore() {
        return rateBefore;
    }

    /**
     * The recorded exploration choice.
     *
     * @return the choice, or empty
     */
    public Optional<ExploreChoice> exploreChoice() {
        return Optional.ofNullable(exploreChoice);
    }

    /**
     * Records that shadow would have skipped.
     *
     * @param skip whether the conditions hold
     */
    public void noteWouldSkip(boolean skip) {
        wouldSkip = skip;
    }

    /**
     * Whether shadow would skip.
     *
     * @return the flag
     */
    public boolean wouldSkip() {
        return wouldSkip;
    }

    /**
     * Records a failed held skip.
     *
     * @param cause the rule-8 cause
     */
    public void noteSkip(LearnCause cause) {
        skipCause = cause;
    }

    /**
     * The last skip cause.
     *
     * @return the cause, or empty
     */
    public Optional<LearnCause> skipCause() {
        return Optional.ofNullable(skipCause);
    }

    /**
     * Base used by learned scores. The default is priority.
     *
     * @return the base
     */
    public CandidateScoring baseScoring() {
        return baseScoring;
    }

    /**
     * Sets {@code learning.baseScoring}.
     *
     * @param scoring the base
     */
    public void baseScoring(CandidateScoring scoring) {
        baseScoring = scoring;
    }

    /**
     * Context of the pattern query. The fine context is used when none was set.
     *
     * @param fine the question context
     * @return the pattern context
     */
    public long patternContext(long fine) {
        return patternContext == Long.MIN_VALUE ? fine : patternContext;
    }

    /**
     * Sets the pattern-query context. The question context stays the fine key.
     *
     * @param context the packed context
     */
    public void usePatternContext(long context) {
        patternContext = context;
    }

    /**
     * Local-pick weight. It stays 1 outside entropy or before any observation.
     *
     * @param entropy whether the profile is entropy
     * @return a factor in {@code [0.9, 1.1]}
     */
    public double localWeight(boolean entropy) {
        return localWeights(entropy, 1)[0];
    }

    /**
     * Per-tactic local-pick weights. They stay 1 outside entropy or before any observation.
     *
     * @param entropy whether the profile is entropy
     * @param count how many tactics are eligible
     * @return factors in {@code [0.9, 1.1]}
     */
    public double[] localWeights(boolean entropy, int count) {
        var factors = new double[Math.max(count, 0)];
        java.util.Arrays.fill(factors, 1.0);
        if (!entropy || weights == null) {
            return factors;
        }
        var limit = Math.min(factors.length, weights.size());
        for (var index = 0; index < limit; index++) {
            factors[index] = weights.factorMillis(index) / 1000.0;
        }
        return factors;
    }

    /**
     * Steps the in-fight weight. Ignored unless entropy and learned scoring are both on.
     *
     * @param entropy whether the profile is entropy
     * @param learned whether scoring is learned
     * @param tactic tactic index inside the eligible list
     * @param effect immediate effect
     * @param avoided avoided damage
     */
    public void observeWeight(boolean entropy, boolean learned, int tactic, double effect, double avoided) {
        if (!entropy || !learned || tactic < 0) {
            return;
        }
        if (weights == null) {
            weights = new InFightWeights(8);
        }
        weights.observe(tactic, effect, avoided);
    }

    /**
     * Steps the in-fight weight of one tactic by id, so the factor follows the tactic and not its place in a list.
     * Ignored unless entropy and learned scoring are both on.
     *
     * @param entropy whether the profile is entropy
     * @param learned whether scoring is learned
     * @param tactic tactic id
     * @param effect immediate effect
     * @param avoided avoided damage
     */
    public void observeWeight(boolean entropy, boolean learned, String tactic, double effect, double avoided) {
        if (!entropy || !learned) {
            return;
        }
        if (weights == null) {
            weights = new InFightWeights(8);
        }
        weights.observe(tactic, effect, avoided);
    }

    /**
     * Local-pick weights of the given tactics by id. They stay 1 outside entropy or before any observation.
     *
     * @param entropy whether the profile is entropy
     * @param tactics tactic ids in the order of the eligible list
     * @return factors in {@code [0.9, 1.1]}, aligned with {@code tactics}
     */
    public double[] localWeights(boolean entropy, List<String> tactics) {
        var factors = new double[tactics.size()];
        for (var index = 0; index < factors.length; index++) {
            factors[index] = !entropy || weights == null ? 1.0 : weights.factorMillis(tactics.get(index)) / 1000.0;
        }
        return factors;
    }

    /**
     * Drops the in-fight weights. Training keeps them.
     *
     * @param predictorTraining whether the session is PredictorTraining
     */
    public void forgetWeights(boolean predictorTraining) {
        training = predictorTraining;
        if (!predictorTraining) {
            weights = null;
        }
    }

    /**
     * Remembers {@code PredictorTraining} from the mob file without clearing weights.
     *
     * @param predictorTraining whether the mob file sets the key
     */
    public void markTraining(boolean predictorTraining) {
        training = predictorTraining;
    }

    /**
     * Whether training kept the weights.
     *
     * @return the flag
     */
    public boolean training() {
        return training;
    }

    /**
     * Patterns already exploited in the window.
     *
     * @return the count
     */
    public int openPatterns() {
        return openPatterns;
    }

    /**
     * Counts one learned exploit toward the cap of two.
     */
    public void noteOpenPattern() {
        openPatterns++;
    }

    /**
     * Stores a learned exploit and its result so far; {@link LearnedResult#NEUTRAL} waits for the first damaging cast.
     *
     * @param source {@code learned} or {@code distilled}
     * @param result the result so far
     */
    public void noteLearned(String source, LearnedResult result) {
        learnedSource = java.util.Objects.requireNonNull(source, "source");
        learnedExtra = "{\"source\":\"" + source + "\",\"result\":\"" + result.token() + "\"}";
        learnedPending = result == LearnedResult.NEUTRAL;
    }

    /**
     * Whether the first damaging cast of a learned exploit is still open.
     *
     * @return {@code true} while the result is {@link LearnedResult#NEUTRAL}
     */
    public boolean learnedPending() {
        return learnedPending;
    }

    /**
     * Records the first damaging cast and closes the pending result.
     *
     * @param result {@code LANDED}, {@code AVOIDED} or {@code BLOCKED}
     */
    public void settleLearned(LearnedResult result) {
        if (!learnedPending) {
            return;
        }
        noteLearned(learnedSource, result);
    }

    /**
     * The learned extra JSON.
     *
     * @return the JSON, or empty
     */
    public String learnedExtra() {
        return learnedExtra;
    }

    /**
     * Source of the last learned exploit.
     *
     * @return the source, or empty
     */
    public String learnedSource() {
        return learnedSource;
    }

    /**
     * Legacy entry point: stages a table without its verdict. Only {@link LearnedArtifacts.State#ACTIVE} installs it,
     * and it then counts as {@code PASS_SIM_ONLY} under the {@code bench} scope: it never acts with a player present.
     *
     * @param installed the table
     * @param state the artifact state
     * @param profile profile code
     * @param fine packed context
     */
    public void stage(ValueTable installed, LearnedArtifacts.State state, int profile, long fine) {
        stage(installed, state, LearnedScope.PASS_SIM_ONLY, "bench", profile, fine);
    }

    /**
     * Stages a table with the verdict of its fingerprint and {@code learning.scope}. Only
     * {@link LearnedArtifacts.State#ACTIVE} installs it, and {@link #tableActive()} applies the scope.
     *
     * @param installed the table
     * @param state the artifact state
     * @param verdict status of {@code GateVerdicts} for the artifact
     * @param scope {@code learning.scope}
     * @param profile profile code
     * @param fine packed context
     */
    public void stage(ValueTable installed, LearnedArtifacts.State state, String verdict, String scope, int profile,
                      long fine) {
        artifact = state;
        table = installed;
        artifactVerdict = java.util.Objects.requireNonNull(verdict, "verdict");
        learnedScope = java.util.Objects.requireNonNull(scope, "scope");
        profileCode = profile;
        contextFine = fine;
    }

    /**
     * Activates a staged table when the artifact is {@code ACTIVE}.
     */
    public void activateIfReady() {
        if (artifact == LearnedArtifacts.State.ACTIVE) {
            tableActive = true;
        }
    }

    /**
     * A player joined this tick, so exploration must stop.
     *
     * @param joined whether a player is present without training
     */
    public void playerJoined(boolean joined) {
        playerJoined = joined;
    }

    /**
     * Whether a player joined.
     *
     * @return the flag
     */
    public boolean playerJoined() {
        return playerJoined;
    }

    /**
     * The staged artifact state.
     *
     * @return the state
     */
    public LearnedArtifacts.State artifactState() {
        return artifact;
    }

    /**
     * Installs the value-of-information cell the next capture reads.
     *
     * @param usable the best cell has enough windows
     * @param gap value gap
     * @param sdBest deviation of the best
     * @param sdSecond deviation of the second
     * @param seen fine-cell questions
     * @param agree agreements
     * @param delta disagreement delta
     * @param forbidden first capture, contingency or g0
     */
    public void installVoi(boolean usable, double gap, double sdBest, double sdSecond, int seen, int agree,
                           double delta, boolean forbidden) {
        voiUsable = usable;
        voiGap = gap;
        voiSdBest = sdBest;
        voiSdSecond = sdSecond;
        voiSeen = seen;
        voiAgree = agree;
        voiDelta = delta;
        voiForbidden = forbidden;
    }

    /**
     * Whether the installed cell is usable.
     *
     * @return the flag
     */
    public boolean voiUsable() {
        return voiUsable;
    }

    /**
     * Value gap of the installed cell.
     *
     * @return the gap
     */
    public double voiGap() {
        return voiGap;
    }

    /**
     * Deviation of the best cell.
     *
     * @return the deviation
     */
    public double voiSdBest() {
        return voiSdBest;
    }

    /**
     * Deviation of the second cell.
     *
     * @return the deviation
     */
    public double voiSdSecond() {
        return voiSdSecond;
    }

    /**
     * Questions seen in the fine cell.
     *
     * @return the count
     */
    public int voiSeen() {
        return voiSeen;
    }

    /**
     * Agreements in the fine cell.
     *
     * @return the count
     */
    public int voiAgree() {
        return voiAgree;
    }

    /**
     * Disagreement delta of the cell.
     *
     * @return the delta
     */
    public double voiDelta() {
        return voiDelta;
    }

    /**
     * Whether this capture must not skip.
     *
     * @return the flag
     */
    public boolean voiForbidden() {
        return voiForbidden;
    }

    /**
     * Adoptions already explored.
     *
     * @return the count
     */
    public int exploredCount() {
        return exploredCount;
    }

    /**
     * Adoptions so far: one per opened window, explored or not.
     *
     * @return the count
     */
    public int adoptionCount() {
        return adoptionCount;
    }

    /**
     * Marks an exploration choice. The adoption itself is counted when its window opens ({@link #noteWindow}), so
     * the share {@code explored / adoptions} drops again as ordinary adoptions follow and exploration resumes under
     * {@code learning.exploration.maxShare}.
     *
     * @param explored whether the choice was inside the stratum
     */
    public void noteAdoption(boolean explored) {
        if (explored) {
            exploredCount++;
        }
    }

    /**
     * Skips already taken in a row.
     *
     * @return the count
     */
    public int consecutiveSkips() {
        return consecutiveSkips;
    }

    /**
     * Sets the consecutive-skip count.
     *
     * @param skips the count
     */
    public void consecutiveSkips(int skips) {
        consecutiveSkips = skips;
    }

    /**
     * Arms the next distilled capture. {@code off} leaves the question path unchanged.
     *
     * @param mode effective mode
     * @param covered whether the cell is covered
     * @param ood the cause when not covered
     * @param source the student source
     * @param tactic the student tactic
     * @param seed session seed
     * @param every audit period
     * @param group whether the question is g0 or a contingency
     * @param collect whether teaching rows are recorded
     * @param regime regime key
     * @param context fine context text
     * @param facts pattern facts on the target
     * @param none whether the effective pattern is none
     */
    public void armDistill(akyrion.thepredict.domain.distill.DistillMode mode, boolean covered,
                           java.util.Optional<akyrion.thepredict.domain.distill.OodCause> ood,
                           akyrion.thepredict.domain.distill.DistillSource source, String tactic, long seed, int every,
                           boolean group, boolean collect, String regime, String context, int facts, boolean none) {
        distillMode = mode;
        distillCovered = covered;
        distillOod = ood;
        distillSource = source;
        distillTactic = tactic;
        distillSeed = seed;
        auditEvery = every;
        groupQuestion = group;
        collectTeaching = collect;
        regimeKey = regime;
        contextText = context;
        patternFacts = facts;
        patternNone = none;
        captureDelegated = false;
    }

    /**
     * Delegated decisions already accepted.
     *
     * @return the count
     */
    public int delegated() {
        return delegated;
    }

    /**
     * Counts one real delegation. An audit does not count.
     */
    public void noteDelegated() {
        delegated++;
        captureDelegated = true;
    }

    /**
     * Whether the capture just armed was delegated.
     *
     * @return the flag
     */
    public boolean captureDelegated() {
        return captureDelegated;
    }

    /**
     * The armed mode.
     *
     * @return the mode
     */
    public akyrion.thepredict.domain.distill.DistillMode distillMode() {
        return distillMode;
    }

    /**
     * Whether teaching was armed.
     *
     * @return the flag
     */
    public boolean collectTeaching() {
        return collectTeaching;
    }

    /**
     * Regime key of the armed capture.
     *
     * @return the key
     */
    public String regimeKey() {
        return regimeKey;
    }

    /**
     * Fine context text of the armed capture.
     *
     * @return the text
     */
    public String contextText() {
        return contextText;
    }

    /**
     * Student tactic.
     *
     * @return the tactic id
     */
    public String distillTactic() {
        return distillTactic;
    }

    /**
     * Student source.
     *
     * @return the source
     */
    public akyrion.thepredict.domain.distill.DistillSource distillSource() {
        return distillSource;
    }

    /**
     * Whether the cell is covered.
     *
     * @return the flag
     */
    public boolean distillCovered() {
        return distillCovered;
    }

    /**
     * Out-of-coverage cause.
     *
     * @return the cause, empty when covered
     */
    public java.util.Optional<akyrion.thepredict.domain.distill.OodCause> distillOod() {
        return distillOod;
    }

    /**
     * Session seed of the audit draw.
     *
     * @return the seed
     */
    public long distillSeed() {
        return distillSeed;
    }

    /**
     * Audit period.
     *
     * @return the period
     */
    public int auditEvery() {
        return auditEvery;
    }

    /**
     * Whether this question is g0 or a contingency.
     *
     * @return the flag
     */
    public boolean groupQuestion() {
        return groupQuestion;
    }

    /**
     * Pattern facts on the target.
     *
     * @return the count
     */
    public int patternFacts() {
        return patternFacts;
    }

    /**
     * Whether the effective pattern is none.
     *
     * @return the flag
     */
    public boolean patternNone() {
        return patternNone;
    }

    /**
     * Distill state of this session: governor, agreements, regime and the last trace.
     *
     * @return the state
     */
    public akyrion.thepredict.engine.distill.DistillSession distillSession() {
        return distillSession;
    }
}
