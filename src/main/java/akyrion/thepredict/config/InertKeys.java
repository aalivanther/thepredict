package akyrion.thepredict.config;

import akyrion.thepredict.domain.GroupCaps;
import akyrion.thepredict.domain.LearningSettings;
import akyrion.thepredict.domain.PatternSettings;
import akyrion.thepredict.domain.distill.AutoconfigSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Keys of {@code config.yml} that this build accepts and validates but does not read (DOC-09). None is removed, so an
 * existing file keeps loading. A value other than the delivered one gets one warning at load, because it changes
 * nothing; the delivered value stays silent, since the shipped file writes every key. The keys of {@code difficulty}
 * are left to the difficulty levels of M31.
 */
public final class InertKeys {
    /** The text of each warning. */
    public static final String NO_EFFECT = "has no effect in this build";

    private static final GroupCaps GROUP = GroupCaps.DEFAULT;
    private static final PatternSettings PATTERNS = PatternSettings.DEFAULT;
    private static final LearningSettings LEARNING = LearningSettings.DEFAULT;
    private static final AutoconfigSettings AUTOCONFIG = AutoconfigSettings.DEFAULT;
    private static final List<Inert> KEYS = List.of(
            new Inert("decision.candidateDiversity",
                    s -> s.decision().candidateDiversity() != DecisionSettings.standard().candidateDiversity()),
            new Inert("records.patternSummaryBinary",
                    s -> s.storage().patternSummaryBinary() != StorageSettings.standard().patternSummaryBinary()),
            new Inert("group.idleWarnTicks", s -> s.group().idleWarnTicks() != GROUP.idleWarnTicks()),
            new Inert("group.factOnTicks", s -> s.group().factOnTicks() != GROUP.factOnTicks()),
            new Inert("group.factOffTicks", s -> s.group().factOffTicks() != GROUP.factOffTicks()),
            new Inert("group.maxFacts", s -> s.group().maxFacts() != GROUP.maxFacts()),
            new Inert("patterns.hearingBlocks", s -> s.patterns().hearingBlocks() != PATTERNS.hearingBlocks()),
            new Inert("patterns.maxWatchedPerEncounter",
                    s -> s.patterns().maxWatchedPerEncounter() != PATTERNS.maxWatchedPerEncounter()),
            new Inert("patterns.maxCounterDelayTicks",
                    s -> s.patterns().maxCounterDelayTicks() != PATTERNS.maxCounterDelayTicks()),
            new Inert("learning.maxContextKeysPerProfile",
                    s -> s.learning().maxContextKeysPerProfile() != LEARNING.maxContextKeysPerProfile()),
            new Inert("learning.maxOpenWindowsPerSession",
                    s -> s.learning().maxOpenWindowsPerSession() != LEARNING.maxOpenWindowsPerSession()),
            new Inert("learning.calibration.minDecisions",
                    s -> s.learning().calibrationMinDecisions() != LEARNING.calibrationMinDecisions()),
            new Inert("learning.calibration.driftReferenceWindows",
                    s -> s.learning().driftReferenceWindows() != LEARNING.driftReferenceWindows()),
            new Inert("autoconfig.initialConfigs",
                    s -> s.autoconfig().initialConfigs() != AUTOCONFIG.initialConfigs()),
            new Inert("autoconfig.initialPairs", s -> s.autoconfig().initialPairs() != AUTOCONFIG.initialPairs()),
            new Inert("autoconfig.eta", s -> s.autoconfig().eta() != AUTOCONFIG.eta()),
            new Inert("autoconfig.maxPairs", s -> s.autoconfig().maxPairs() != AUTOCONFIG.maxPairs()),
            new Inert("autoconfig.maxMinutes", s -> s.autoconfig().maxMinutes() != AUTOCONFIG.maxMinutes()));

    /** Every inert key, in the order of the warnings. */
    public static final List<String> PATHS = KEYS.stream().map(Inert::path).toList();

    private InertKeys() {
    }

    /**
     * The warnings of a valid master file.
     *
     * @param settings the settings read from {@code config.yml}
     * @return one warning per inert key whose value is not the delivered one, in the order of {@link #PATHS}
     */
    public static List<ConfigProblem> of(PluginSettings settings) {
        var warnings = new ArrayList<ConfigProblem>();
        for (var key : KEYS) {
            if (key.changed().test(settings)) {
                warnings.add(ConfigProblem.warning(PluginConfigurationLoader.FILE, key.path(), NO_EFFECT));
            }
        }
        return List.copyOf(warnings);
    }

    /**
     * One inert key.
     *
     * @param path the dotted path in {@code config.yml}
     * @param changed whether the settings hold a value other than the delivered one
     */
    private record Inert(String path, Predicate<PluginSettings> changed) {
    }
}
