package akyrion.thepredict.config;

import akyrion.thepredict.domain.DecisionCadence;
import akyrion.thepredict.domain.DifficultyGlobals;
import akyrion.thepredict.domain.LearningSettings;
import akyrion.thepredict.domain.DecisionTiming;
import akyrion.thepredict.domain.QuestionDetail;
import akyrion.thepredict.domain.ReactionBands;
import akyrion.thepredict.domain.ReactionScope;
import akyrion.thepredict.domain.StanceSettings;
import akyrion.thepredict.domain.TelegraphSettings;
import akyrion.thepredict.domain.GroupCaps;
import akyrion.thepredict.domain.PatternSettings;
import akyrion.thepredict.domain.ZoneCaps;

import java.util.Objects;

/**
 * A validated {@code config.yml} of schema 2: the three sections plus the checks that relate them (M03.1).
 *
 * <p>Cross checks: {@code decision.encounterHeartbeatTicks >= profileDefaults.minimumDecisionIntervalTicks};
 * {@code jev.timeoutMillis < profileDefaults.maximumIntentDurationTicks × 50}, so that an answer can arrive
 * before the intent expires; {@code decision.maxDecisionAgeMillis >= jev.timeoutMillis}.
 *
 * @param jev the {@code jev} section
 * @param decision the {@code decision} section and {@code debug.recordScenarios}
 * @param profileDefaults the {@code profileDefaults} section
 * @param participants the optional {@code participants} section (M17.2)
 * @param storage the {@code records}, {@code storage} and {@code decision.answerCache} keys (M21.12)
 * @param difficulty the {@code difficulty} section (M22)
 * @param telegraph the {@code telegraph} section (M23)
 * @param stance the {@code stance} section (M23)
 * @param zone the {@code zone} section (M24)
 * @param group the {@code group} section (M25)
 * @param patterns the {@code patterns} section (M26)
 * @param memory the {@code memory} section (M27)
 * @param learning the {@code learning} section (M28)
 * @param distill the {@code distill} section (M29)
 * @param autoconfig the {@code autoconfig} section (M29)
 */
public record PluginSettings(JevSettings jev, DecisionSettings decision, ProfileDefaults profileDefaults,
                             ParticipantSettings participants, StorageSettings storage, DifficultyGlobals difficulty,
                             TelegraphSettings telegraph, StanceSettings stance, ZoneCaps zone, GroupCaps group,
                             PatternSettings patterns, akyrion.thepredict.domain.MemorySettings memory,
                             LearningSettings learning, akyrion.thepredict.domain.distill.DistillSettings distill,
                             akyrion.thepredict.domain.distill.AutoconfigSettings autoconfig) {
    /** Nominal duration of one server tick in milliseconds. */
    public static final int MILLIS_PER_TICK = 50;

    /**
     * Validates the relations between sections.
     *
     * @throws NullPointerException if a component is {@code null}
     * @throws ConfigurationException if a cross check fails, naming the path of the value to change
     */
    public PluginSettings {
        Objects.requireNonNull(jev, "jev");
        Objects.requireNonNull(decision, "decision");
        Objects.requireNonNull(profileDefaults, "profileDefaults");
        Objects.requireNonNull(participants, "participants");
        Objects.requireNonNull(storage, "storage");
        Objects.requireNonNull(difficulty, "difficulty");
        Objects.requireNonNull(telegraph, "telegraph");
        Objects.requireNonNull(stance, "stance");
        Objects.requireNonNull(zone, "zone");
        Objects.requireNonNull(group, "group");
        Objects.requireNonNull(patterns, "patterns");
        Objects.requireNonNull(memory, "memory");
        Objects.requireNonNull(learning, "learning");
        Objects.requireNonNull(distill, "distill");
        Objects.requireNonNull(autoconfig, "autoconfig");
        if (decision.encounterHeartbeatTicks() < profileDefaults.minimumDecisionIntervalTicks()) {
            throw new ConfigurationException("decision.encounterHeartbeatTicks",
                    "must be >= profileDefaults.minimumDecisionIntervalTicks ("
                            + profileDefaults.minimumDecisionIntervalTicks() + "), was " + decision.encounterHeartbeatTicks());
        }
        var intentMillis = profileDefaults.maximumIntentDurationTicks() * MILLIS_PER_TICK;
        if (jev.timeoutMillis() >= intentMillis) {
            throw new ConfigurationException("jev.timeoutMillis", "must be below profileDefaults.maximumIntentDurationTicks x "
                    + MILLIS_PER_TICK + " (" + intentMillis + " ms), was " + jev.timeoutMillis());
        }
        if (decision.maxDecisionAgeMillis() < jev.timeoutMillis()) {
            throw new ConfigurationException("decision.maxDecisionAgeMillis", "must be >= jev.timeoutMillis ("
                    + jev.timeoutMillis() + "), was " + decision.maxDecisionAgeMillis());
        }
    }

    /**
     * Returns the settings of a fresh installation.
     *
     * @return the default settings
     */
    public static PluginSettings standard() {
        return new PluginSettings(JevSettings.standard(), DecisionSettings.standard(), ProfileDefaults.standard(),
                ParticipantSettings.standard(), StorageSettings.standard(), DifficultyGlobals.DEFAULT);
    }

    /**
     * Settings from before tells and stances were components (rule W5). Both use their defaults.
     *
     * @param jev the {@code jev} section
     * @param decision the {@code decision} section
     * @param profileDefaults the {@code profileDefaults} section
     * @param participants the {@code participants} section
     * @param storage the storage section
     * @param difficulty the difficulty section
     */
    public PluginSettings(JevSettings jev, DecisionSettings decision, ProfileDefaults profileDefaults,
                          ParticipantSettings participants, StorageSettings storage, DifficultyGlobals difficulty) {
        this(jev, decision, profileDefaults, participants, storage, difficulty, TelegraphSettings.DEFAULT,
                StanceSettings.DEFAULT, ZoneCaps.DEFAULT);
    }

    /**
     * Settings from before the zone section (rule W5). The zone uses {@link ZoneCaps#DEFAULT}.
     *
     * @param jev the {@code jev} section
     * @param decision the {@code decision} section
     * @param profileDefaults the {@code profileDefaults} section
     * @param participants the {@code participants} section
     * @param storage the storage section
     * @param difficulty the difficulty section
     * @param telegraph the telegraph section
     * @param stance the stance section
     */
    public PluginSettings(JevSettings jev, DecisionSettings decision, ProfileDefaults profileDefaults,
                          ParticipantSettings participants, StorageSettings storage, DifficultyGlobals difficulty,
                          TelegraphSettings telegraph, StanceSettings stance) {
        this(jev, decision, profileDefaults, participants, storage, difficulty, telegraph, stance, ZoneCaps.DEFAULT,
                GroupCaps.DEFAULT);
    }

    /**
     * Settings from before the patterns section (rule W5). Patterns use {@link PatternSettings#DEFAULT}.
     *
     * @param jev the {@code jev} section
     * @param decision the {@code decision} section
     * @param profileDefaults the {@code profileDefaults} section
     * @param participants the {@code participants} section
     * @param storage the storage section
     * @param difficulty the difficulty section
     * @param telegraph the telegraph section
     * @param stance the stance section
     * @param zone the zone section
     * @param group the group section
     */
    public PluginSettings(JevSettings jev, DecisionSettings decision, ProfileDefaults profileDefaults,
                          ParticipantSettings participants, StorageSettings storage, DifficultyGlobals difficulty,
                          TelegraphSettings telegraph, StanceSettings stance, ZoneCaps zone, GroupCaps group) {
        this(jev, decision, profileDefaults, participants, storage, difficulty, telegraph, stance, zone, group,
                PatternSettings.DEFAULT, akyrion.thepredict.domain.MemorySettings.DEFAULT);
    }

    /**
     * Settings from before the learning section (rule W5). Learning uses {@link LearningSettings#DEFAULT}.
     *
     * @param jev the {@code jev} section
     * @param decision the {@code decision} section
     * @param profileDefaults the {@code profileDefaults} section
     * @param participants the {@code participants} section
     * @param storage the storage section
     * @param difficulty the difficulty section
     * @param telegraph the telegraph section
     * @param stance the stance section
     * @param zone the zone section
     * @param group the group section
     * @param patterns the patterns section
     * @param memory the memory section
     */
    public PluginSettings(JevSettings jev, DecisionSettings decision, ProfileDefaults profileDefaults,
                          ParticipantSettings participants, StorageSettings storage, DifficultyGlobals difficulty,
                          TelegraphSettings telegraph, StanceSettings stance, ZoneCaps zone, GroupCaps group,
                          PatternSettings patterns, akyrion.thepredict.domain.MemorySettings memory) {
        this(jev, decision, profileDefaults, participants, storage, difficulty, telegraph, stance, zone, group,
                patterns, memory, LearningSettings.DEFAULT, akyrion.thepredict.domain.distill.DistillSettings.DEFAULT,
                akyrion.thepredict.domain.distill.AutoconfigSettings.DEFAULT);
    }

    /**
     * Settings from before distillation (rule W5). Distill and autoconfig use their defaults.
     *
     * @param jev the {@code jev} section
     * @param decision the {@code decision} section
     * @param profileDefaults the {@code profileDefaults} section
     * @param participants the {@code participants} section
     * @param storage the storage section
     * @param difficulty the difficulty section
     * @param telegraph the telegraph section
     * @param stance the stance section
     * @param zone the zone section
     * @param group the group section
     * @param patterns the patterns section
     * @param memory the memory section
     * @param learning the learning section
     */
    public PluginSettings(JevSettings jev, DecisionSettings decision, ProfileDefaults profileDefaults,
                          ParticipantSettings participants, StorageSettings storage, DifficultyGlobals difficulty,
                          TelegraphSettings telegraph, StanceSettings stance, ZoneCaps zone, GroupCaps group,
                          PatternSettings patterns, akyrion.thepredict.domain.MemorySettings memory,
                          LearningSettings learning) {
        this(jev, decision, profileDefaults, participants, storage, difficulty, telegraph, stance, zone, group,
                patterns, memory, learning, akyrion.thepredict.domain.distill.DistillSettings.DEFAULT,
                akyrion.thepredict.domain.distill.AutoconfigSettings.DEFAULT);
    }

    /**
     * Settings from before the memory section (rule W5). Memory uses {@link akyrion.thepredict.domain.MemorySettings#DEFAULT}.
     *
     * @param jev the {@code jev} section
     * @param decision the {@code decision} section
     * @param profileDefaults the {@code profileDefaults} section
     * @param participants the {@code participants} section
     * @param storage the storage section
     * @param difficulty the difficulty section
     * @param telegraph the telegraph section
     * @param stance the stance section
     * @param zone the zone section
     * @param group the group section
     * @param patterns the patterns section
     */
    public PluginSettings(JevSettings jev, DecisionSettings decision, ProfileDefaults profileDefaults,
                          ParticipantSettings participants, StorageSettings storage, DifficultyGlobals difficulty,
                          TelegraphSettings telegraph, StanceSettings stance, ZoneCaps zone, GroupCaps group,
                          PatternSettings patterns) {
        this(jev, decision, profileDefaults, participants, storage, difficulty, telegraph, stance, zone, group,
                patterns, akyrion.thepredict.domain.MemorySettings.DEFAULT);
    }

    /**
     * Settings from before the group section (rule W5). The group uses {@link GroupCaps#DEFAULT}.
     *
     * @param jev the {@code jev} section
     * @param decision the {@code decision} section
     * @param profileDefaults the {@code profileDefaults} section
     * @param participants the {@code participants} section
     * @param storage the storage section
     * @param difficulty the difficulty section
     * @param telegraph the telegraph section
     * @param stance the stance section
     * @param zone the zone section
     */
    public PluginSettings(JevSettings jev, DecisionSettings decision, ProfileDefaults profileDefaults,
                          ParticipantSettings participants, StorageSettings storage, DifficultyGlobals difficulty,
                          TelegraphSettings telegraph, StanceSettings stance, ZoneCaps zone) {
        this(jev, decision, profileDefaults, participants, storage, difficulty, telegraph, stance, zone,
                GroupCaps.DEFAULT);
    }

    /**
     * Settings from before difficulty was a component (rule W5). Difficulty uses {@link DifficultyGlobals#DEFAULT}.
     *
     * @param jev the {@code jev} section
     * @param decision the {@code decision} section
     * @param profileDefaults the {@code profileDefaults} section
     * @param participants the {@code participants} section
     * @param storage the storage section
     */
    public PluginSettings(JevSettings jev, DecisionSettings decision, ProfileDefaults profileDefaults,
                          ParticipantSettings participants, StorageSettings storage) {
        this(jev, decision, profileDefaults, participants, storage, DifficultyGlobals.DEFAULT);
    }

    /**
     * Settings from before storage was a component (rule W5). Storage uses {@link StorageSettings#standard()}.
     *
     * @param jev the {@code jev} section
     * @param decision the {@code decision} section
     * @param profileDefaults the {@code profileDefaults} section
     * @param participants the {@code participants} section
     */
    public PluginSettings(JevSettings jev, DecisionSettings decision, ProfileDefaults profileDefaults,
                          ParticipantSettings participants) {
        this(jev, decision, profileDefaults, participants, StorageSettings.standard());
    }

    /**
     * Returns the decision timing of a profile that does not override {@code Timing}.
     *
     * @return the default timing
     */
    public DecisionTiming timing() {
        return timing(decision.minimumConfidence(), decision.tacticSwitchMargin(), decision.questions().questionDetail(),
                decision.questions().fitChecks());
    }

    /**
     * Returns the decision timing of a profile with its own confidence and switch margin and the global question detail
     * and fit checks.
     *
     * @param minimumConfidence confidence required to adopt an answer
     * @param switchMargin probability margin required to switch tactic
     * @return the timing with the global heartbeat, intervals and contingencies
     */
    public DecisionTiming timing(double minimumConfidence, double switchMargin) {
        return timing(minimumConfidence, switchMargin, decision.questions().questionDetail(),
                decision.questions().fitChecks());
    }

    /**
     * Returns the decision timing of a profile with its own confidence, switch margin, question detail and fit checks
     * (M16.2, M16.5): the global {@code decision.questionDetail} and {@code decision.fitChecks} are resolved here as
     * the defaults of each profile, so the engine reads only the timing (rule C13).
     *
     * @param minimumConfidence confidence required to adopt an answer
     * @param switchMargin probability margin required to switch tactic
     * @param questionDetail detail of the questions about mobs of the profile
     * @param fitChecks whether fit checks are asked about mobs of the profile
     * @return the timing with the global heartbeat, intervals and contingencies
     */
    public DecisionTiming timing(double minimumConfidence, double switchMargin, QuestionDetail questionDetail,
                                 boolean fitChecks) {
        var maximum = profileDefaults.maximumIntentDurationTicks();
        var horizon = Math.min(10, maximum);
        return new DecisionTiming(decision.encounterHeartbeatTicks(), profileDefaults.minimumDecisionIntervalTicks(),
                maximum, minimumConfidence, switchMargin, decision.contingenciesPerMob(), questionDetail, fitChecks,
                decision.castMode(), decision.readyHorizonTicks(), decision.untypedCast(), decision.tickLayerMode(),
                ReactionScope.DEFAULT, ReactionBands.DEFAULT_AHEAD, 0, DecisionCadence.DEFAULT, 6, horizon);
    }
}
