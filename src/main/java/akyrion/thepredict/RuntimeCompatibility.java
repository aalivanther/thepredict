package akyrion.thepredict;

import akyrion.thepredict.adapter.server.binding.MythicCompatibility;
import org.bukkit.Server;

import java.util.Objects;
import java.util.regex.Pattern;

/** Checks the three runtime prerequisites before the addon starts any work. */
final class RuntimeCompatibility {
    /** Oldest Minecraft server version supported by the compiled Paper API. */
    public static final String MINIMUM_MINECRAFT_VERSION = "26.2";
    private static final Pattern MINECRAFT_VERSION = Pattern.compile("^(\\d+)\\.(\\d+)(?:\\.(\\d+))?$");

    /** Constructs the runtime prerequisite checker. */
    public RuntimeCompatibility() {
    }

    /** Validates Java, server version, and the MythicMobs integration in order.
     * @param server active Bukkit server
     * @return detected versions to display in the administrative status command
     * @throws IllegalStateException when a runtime prerequisite is unavailable
     */
    public DetectedVersions verify(Server server) {
        Objects.requireNonNull(server, "server");
        var javaFeature = new JavaCompatibility().requireCompatibleRuntime();
        var minecraftVersion = server.getMinecraftVersion();
        if (!supportsMinecraft(minecraftVersion)) {
            throw new IllegalStateException("Minecraft " + minecraftVersion + " is unsupported; this Paper build requires "
                    + MINIMUM_MINECRAFT_VERSION + " or newer");
        }
        var mythicVersion = new MythicCompatibility().requireCompatibleVersion(server.getPluginManager());
        return new DetectedVersions(javaFeature, minecraftVersion, mythicVersion);
    }

    /** Checks stable Minecraft version numbers against the Paper 26.2 baseline.
     * @param version value reported by Server.getMinecraftVersion()
     * @return whether this version satisfies the baseline
     */
    public boolean supportsMinecraft(String version) {
        if (version == null) {
            return false;
        }
        var match = MINECRAFT_VERSION.matcher(version);
        if (!match.matches()) {
            return false;
        }
        try {
            var major = Integer.parseInt(match.group(1));
            var minor = Integer.parseInt(match.group(2));
            return major > 26 || major == 26 && minor >= 2;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    /** Versions observed during successful startup.
     * @param javaFeature current Java feature release
     * @param minecraftVersion server Minecraft version string
     * @param mythicVersion enabled MythicMobs plugin version
     */
    public record DetectedVersions(int javaFeature, String minecraftVersion, String mythicVersion) {
    }
}
