package akyrion.thepredict.admin;

import akyrion.thepredict.domain.ServiceCheck;
import akyrion.thepredict.domain.ServiceState;
import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Texts of the first start, of every state of the service, the catalog and the migration, and of the notice to
 * administrators who join (M12.4). Each state has its own text with a next step. Pure; no text holds a key or a
 * configured value that could be one (rule C8).
 */
public final class SetupMessages {
    /** What {@code /v1/models} can and cannot tell (plan §6). */
    public static final String CHECK_SCOPE = "The check reads the key and the model list; it does not verify an"
            + " inference or its quality.";

    /**
     * End of every next step that needs a restart: {@code config.yml} and the Jev key are read only when the plugin
     * starts, so {@code /thepredict check} alone never picks up a new value.
     */
    public static final String RESTART = " and restart the server, then run /thepredict status.";

    private SetupMessages() {
    }

    /**
     * Console lines of the start: configuration, service without a key, empty catalog and migration. A clean
     * installation gets only information, never an error.
     *
     * @param setup installation facts
     * @param service initial state of the service
     * @return the lines, in that order
     */
    public static List<String> startup(SetupReport setup, ServiceState service) {
        Objects.requireNonNull(service, "service");
        var lines = new ArrayList<String>();
        setup.configurationProblem().ifPresent(problem -> lines.add("Invalid configuration: " + problem
                + ". ThePredict stays inactive and /thepredict status still works. Next: fix "
                + "plugins/MythicMobs/AIConfig/config.yml" + RESTART));
        setup.locationNotice().ifPresent(lines::add);
        if (service == ServiceState.UNCONFIGURED) {
            lines.add(unconfigured());
        }
        if (setup.catalog() == SetupReport.CatalogState.SETUP_PENDING && setup.configurationProblem().isEmpty()) {
            lines.add(setup.profilesRead() == 0
                    ? "No profiles: copy examples/ or create a .yml file in plugins/MythicMobs/AIConfig/Profiles/."
                    + " Next: read plugins/MythicMobs/AIConfig/Profiles/README.txt."
                    : setup.profilesRead() + " profiles read, but no mob targeted by a profile has Predictor: true."
                    + " Next: set Predictor: true on the MythicMobs mob and run /mm reload.");
        }
        setup.migrationReport().ifPresent(report -> lines.add(migration(report)));
        if (service == ServiceState.CHECKING) {
            lines.add("Checking Jev without spending tokens; the result follows in the console.");
        }
        return List.copyOf(lines);
    }

    /**
     * Explains the result of a check with its next step.
     *
     * @param check the check as the client reported it
     * @return one line
     */
    public static String checkResult(ServiceCheck check) {
        var head = "Jev check: " + check.state() + " (" + check.detail() + "). ";
        return switch (check.state()) {
            case READY -> head + "Decisions are enabled. " + CHECK_SCOPE;
            case INVALID_CREDENTIALS -> head + "The key is read only at startup. Next: set a valid TypeSafe key in"
                    + " TYPESAFE_API_KEY or jev.apiKey" + RESTART;
            case INVALID_MODEL -> head + "Next: set jev.model to a model your account lists (jev-1.13.0 is the"
                    + " tested one)" + RESTART;
            case PAUSED -> head + "ThePredict retries on its own; MythicMobs keeps control meanwhile.";
            case UNCONFIGURED -> unconfigured();
            case INVALID_CONFIGURATION -> head + "Next: fix plugins/MythicMobs/AIConfig/config.yml" + RESTART;
            case CHECKING -> head + "The check is still running; see /thepredict status.";
        };
    }

    /**
     * The join notice as a component, sent with the Adventure {@code sendMessage(Component)} instead of the
     * {@code sendMessage(String)} that Paper marks obsolete.
     *
     * @param setup state of the setup
     * @param service state of the service
     * @return the notice, empty when nothing needs attention
     */
    public static Optional<Component> joinMessage(SetupReport setup, ServiceState service) {
        return joinNotice(setup, service).map(Component::text);
    }

    /**
     * Notice for an administrator who joins, when something needs attention: an invalid configuration or catalog, a
     * pending migration, or a service that is not {@code READY}.
     *
     * @param setup installation facts
     * @param service current state of the service
     * @return the notice, empty when everything is ready
     */
    public static Optional<String> joinNotice(SetupReport setup, ServiceState service) {
        var causes = new ArrayList<String>();
        if (setup.configurationProblem().isPresent()) {
            causes.add("plugins/MythicMobs/AIConfig/config.yml has errors");
        }
        if (setup.locationNotice().isPresent()) {
            causes.add("old configuration files are ignored");
        }
        if (setup.catalog() == SetupReport.CatalogState.INVALID) {
            causes.add("some profiles have errors");
        }
        if (setup.migrationReport().isPresent()) {
            causes.add("a migration is pending");
        }
        if (service != ServiceState.READY) {
            causes.add("Jev is " + service);
        }
        return causes.isEmpty() ? Optional.empty()
                : Optional.of(AdminText.PREFIX + String.join("; ", causes) + ". Run /thepredict status for details.");
    }

    private static String unconfigured() {
        return "Jev not configured: set TYPESAFE_API_KEY or jev.apiKey. MythicMobs works normally. Next: set the key"
                + RESTART;
    }

    private static String migration(String report) {
        return "Migration proposal: review " + report + " and the .proposed files next to it, then copy them into"
                + " plugins/MythicMobs/AIConfig/ by hand. No file was changed and no query was enabled.";
    }
}
