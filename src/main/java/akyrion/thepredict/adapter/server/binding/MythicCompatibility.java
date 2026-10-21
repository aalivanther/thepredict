package akyrion.thepredict.adapter.server.binding;

import io.lumine.mythic.bukkit.MythicBukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import java.util.regex.Pattern;

/** Checks the installed MythicMobs plugin before its API is used. */
public final class MythicCompatibility {
    /** Oldest MythicMobs version supported by the compiled integration. */
    public static final String MINIMUM_VERSION = "5.13.1";
    private static final Pattern VERSION = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)(?:$|[-+].*)");

    /** Constructs the runtime compatibility checker. */
    public MythicCompatibility() {
    }

    /** Returns the running MythicMobs version or throws a clear compatibility error.
     * @param manager Bukkit plugin manager that owns MythicMobs
     * @return detected compatible plugin version
     */
    public String requireCompatibleVersion(PluginManager manager) {
        Plugin plugin = manager.getPlugin("MythicMobs");
        if (!(plugin instanceof MythicBukkit mythic) || !mythic.isEnabled()) {
            throw new IllegalStateException("MythicMobs must be installed and enabled");
        }
        var version = mythic.getPluginMeta().getVersion();
        if (!supports(version)) {
            throw new IllegalStateException("MythicMobs " + version + " is unsupported; minimum version is " + MINIMUM_VERSION);
        }
        if (mythic.getMobManager() == null || mythic.getSkillManager() == null) {
            throw new IllegalStateException("MythicMobs mob or skill API is unavailable");
        }
        return version;
    }

    /** Compares the numeric core of a release or snapshot version with 5.13.1.
     * @param version plugin version from its metadata
     * @return whether its numeric version satisfies the minimum
     */
    public boolean supports(String version) {
        if (version == null) {
            return false;
        }
        var match = VERSION.matcher(version);
        if (!match.matches()) {
            return false;
        }
        var minimum = new int[] {5, 13, 1};
        try {
            for (int index = 0; index < minimum.length; index++) {
                var current = Integer.parseInt(match.group(index + 1));
                if (current != minimum[index]) {
                    return current > minimum[index];
                }
            }
        } catch (NumberFormatException exception) {
            return false;
        }
        return true;
    }
}
