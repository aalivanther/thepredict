package akyrion.thepredict.adapter.server.binding;

import akyrion.thepredict.domain.CueChannels;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Signal handlers a mob already declares (M23.3). Read at bind and again after {@code /mm reload}, from the
 * {@code Skills} lines of the mob type, which MythicMobs has already merged with its templates
 * ({@code TemplateUtil.handleTemplates} runs before {@code MobType} is built, javap).
 *
 * <p>Evidence (javap, MythicMobs 5.13.1): {@code MobType} keys its private {@code signalSkills} map with the group of
 * {@code SIGNAL_PATTERN = ~onSignal:([a-zA-Z0-9_-]*)}, and {@code executeSignalSkill(signal)} looks that map up by
 * the exact name; when the name is missing it runs the generic {@code ~onSignal} skills (an empty group) instead. This
 * class reads the lines with the same pattern, so {@link #handles} answers what {@code executeSignalSkill} would run.
 */
public final class MobCueHandlers {
    /** A mob without any {@code ~onSignal} line. */
    public static final MobCueHandlers NONE = new MobCueHandlers(Set.of(), false);

    private static final Pattern SIGNAL = Pattern.compile("(?i)~onSignal(?::([a-zA-Z0-9_-]*))?");

    private final Set<String> signals;
    private final boolean generic;

    private MobCueHandlers(Set<String> signals, boolean generic) {
        this.signals = signals;
        this.generic = generic;
    }

    /**
     * Handlers named in skill lines ({@code ~onSignal:NAME}). A line with {@code ~onSignal} and no colon is a generic
     * handler that MythicMobs runs for every signal without a named handler; {@code ~onSignal:} with an empty name is
     * keyed by the empty string and never runs.
     *
     * @param lines mechanic lines of the mob
     * @return the handlers
     */
    public static MobCueHandlers read(List<String> lines) {
        var found = new LinkedHashSet<String>();
        var generic = false;
        for (var line : lines) {
            var matcher = SIGNAL.matcher(line);
            while (matcher.find()) {
                var name = matcher.group(1);
                if (name == null) {
                    generic = true;
                } else if (!name.isEmpty()) {
                    found.add(name);
                }
            }
        }
        if (found.isEmpty() && !generic) {
            return NONE;
        }
        return new MobCueHandlers(Set.copyOf(found), generic);
    }

    /**
     * Whether the mob listens for this exact signal, or for every signal through a generic handler.
     *
     * @param signal signal name
     * @return whether {@code signalMob} would run a skill
     */
    public boolean handles(String signal) {
        return generic || signals.contains(signal);
    }

    /**
     * Channels of the lines that listen for one signal. Audio and visual only, as {@link CueChannels}.
     *
     * @param lines mechanic lines
     * @param signal exact signal
     * @return channel bits
     */
    public static int channels(List<String> lines, String signal) {
        var bits = 0;
        var marker = "~onSignal:" + signal;
        for (var line : lines) {
            if (!line.contains(marker)) {
                continue;
            }
            var lower = line.toLowerCase(Locale.ROOT);
            if (lower.contains("sound")) {
                bits |= CueChannels.AUDIO;
            }
            if (lower.contains("particle") || lower.contains("glow") || lower.contains("swingarm")
                    || lower.contains("playanimation") || lower.contains("setpose") || lower.contains("state")) {
                bits |= CueChannels.VISUAL;
            }
        }
        return bits;
    }
}
