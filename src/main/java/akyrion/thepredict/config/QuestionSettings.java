package akyrion.thepredict.config;

import akyrion.thepredict.domain.FitGate;
import akyrion.thepredict.domain.OptionSet;
import akyrion.thepredict.domain.QuestionDetail;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Locale;
import java.util.Objects;

/**
 * The keys of {@code decision.*} that shape each question (M16.1, M16.2, M16.5): the option cap, the default detail and
 * the optional fit checks with their thresholds. Global limits that a profile never widens; a profile may choose its
 * own {@code Timing.QuestionDetail} and {@code Timing.FitChecks}, which inherit these defaults when the profile loads.
 * The detail never widens the global token limits (rule C13).
 *
 * @param maxOptionsPerQuestion {@code decision.maxOptionsPerQuestion}, {@value OptionSet#MIN_MAX_OPTIONS} to
 *        {@value OptionSet#MAX_MAX_OPTIONS}
 * @param questionDetail {@code decision.questionDetail}: {@code compact}, {@code standard} or {@code full}
 * @param fitChecks {@code decision.fitChecks}: default of {@code Timing.FitChecks}
 * @param fitChecksAllowed {@code decision.fitChecksAllowed}: {@code false} forbids fit checks in every profile
 * @param fitVeto {@code decision.fitVeto}: a fit below it vetoes an option
 * @param fitRescue {@code decision.fitRescue}: the fit that rescues a low confidence
 */
public record QuestionSettings(int maxOptionsPerQuestion, QuestionDetail questionDetail, boolean fitChecks,
                               boolean fitChecksAllowed, double fitVeto, double fitRescue) {
    /**
     * Validates every value.
     *
     * @throws ConfigurationException if a value is out of range, naming its path
     */
    public QuestionSettings {
        SettingsChecks.requireRange(maxOptionsPerQuestion, OptionSet.MIN_MAX_OPTIONS, OptionSet.MAX_MAX_OPTIONS,
                "decision.maxOptionsPerQuestion");
        Objects.requireNonNull(questionDetail, "questionDetail");
        SettingsChecks.requireRange(fitVeto, 0.0, 1.0, "decision.fitVeto");
        SettingsChecks.requireRange(fitRescue, 0.0, 1.0, "decision.fitRescue");
        if (!(fitVeto < fitRescue)) {
            throw new ConfigurationException("decision.fitRescue", "must be greater than decision.fitVeto ("
                    + fitVeto + "), was " + fitRescue);
        }
        if (fitChecks && !fitChecksAllowed) {
            throw new ConfigurationException("decision.fitChecks", "must be false while decision.fitChecksAllowed is"
                    + " false");
        }
    }

    /**
     * Returns the defaults of a fresh installation: 8 options, {@code full} (M16.10), no fit checks (allowed), veto
     * 0.2 and rescue 0.7.
     *
     * @return the default settings
     */
    public static QuestionSettings standard() {
        return new QuestionSettings(OptionSet.DEFAULT_MAX_OPTIONS, QuestionDetail.DEFAULT, false, true,
                FitGate.FitThresholds.DEFAULT.veto(), FitGate.FitThresholds.DEFAULT.rescue());
    }

    /**
     * Returns the veto and rescue thresholds for the engine.
     *
     * @return the thresholds
     */
    public FitGate.FitThresholds fitThresholds() {
        return new FitGate.FitThresholds(fitVeto, fitRescue);
    }

    /**
     * Reads the keys of the {@code decision} section; absent keys keep {@link #standard()}.
     *
     * @param configuration root of {@code config.yml}
     * @return the validated settings
     * @throws ConfigurationException if a value has the wrong type or range
     */
    public static QuestionSettings from(ConfigurationSection configuration) {
        var standard = standard();
        return new QuestionSettings(
                ConfigurationValues.intValue(configuration, "decision.maxOptionsPerQuestion",
                        standard.maxOptionsPerQuestion()),
                detail(configuration, "decision.questionDetail", standard.questionDetail()),
                ConfigurationValues.booleanValue(configuration, "decision.fitChecks", standard.fitChecks()),
                ConfigurationValues.booleanValue(configuration, "decision.fitChecksAllowed", standard.fitChecksAllowed()),
                ConfigurationValues.decimal(configuration, "decision.fitVeto", standard.fitVeto()),
                ConfigurationValues.decimal(configuration, "decision.fitRescue", standard.fitRescue()));
    }

    /**
     * Reads a detail, {@code compact}, {@code standard} or {@code full} in any case, or uses a default when absent.
     *
     * @param configuration section that holds the key
     * @param path key within the section
     * @param fallback value when absent
     * @return the detail
     * @throws ConfigurationException if the value is not one of the three
     */
    static QuestionDetail detail(ConfigurationSection configuration, String path, QuestionDetail fallback) {
        if (!configuration.contains(path)) {
            return fallback;
        }
        var text = ConfigurationValues.text(configuration, path, "").trim().toUpperCase(Locale.ROOT);
        for (var detail : QuestionDetail.values()) {
            if (detail.name().equals(text)) {
                return detail;
            }
        }
        throw new ConfigurationException(ConfigurationValues.path(configuration, path),
                "must be compact, standard or full");
    }

    /**
     * Returns the value written in {@code config.yml} for a detail.
     *
     * @param detail a detail
     * @return {@code compact}, {@code standard} or {@code full}
     */
    public static String text(QuestionDetail detail) {
        return detail.name().toLowerCase(Locale.ROOT);
    }
}
