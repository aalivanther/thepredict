package akyrion.thepredict.domain.distill;

import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Sources a packaged artifact may name (M29.6, errata E29-8 applied). A Jev source is refused. A directory
 * under {@code learn/packaged} is imported. Nothing is packaged until the user trains one. The source is the
 * top-level {@code source} field of {@code verdict.json}, read with {@link JsonFields}; a malformed file is refused.
 */
public final class PackagedSource {
    private PackagedSource() {
    }

    /**
     * One imported artifact.
     *
     * @param name directory name
     * @param source declared source
     * @param imported always true for a loaded directory
     */
    public record Loaded(String name, String source, boolean imported) {
    }

    /**
     * Loads {@code learn/packaged} from a class loader. A missing resource is an empty list.
     *
     * @param loader the plugin class loader
     * @return imported artifacts
     * @throws IOException when a verdict cannot be read
     */
    public static List<Loaded> load(ClassLoader loader) throws IOException {
        var resource = Objects.requireNonNull(loader, "loader").getResource("learn/packaged");
        if (resource == null) {
            return List.of();
        }
        if ("jar".equals(resource.getProtocol())) {
            return loadJar(resource);
        }
        if (!"file".equals(resource.getProtocol())) {
            return List.of();
        }
        try {
            return loadDirectory(Path.of(resource.toURI()));
        } catch (URISyntaxException exception) {
            throw new IOException("packaged");
        }
    }

    private static List<Loaded> loadJar(java.net.URL resource) throws IOException {
        var connection = resource.openConnection();
        if (!(connection instanceof JarURLConnection jar)) {
            return List.of();
        }
        var prefix = jar.getEntryName() == null ? "" : jar.getEntryName();
        if (!prefix.endsWith("/")) {
            prefix = prefix + "/";
        }
        var loaded = new ArrayList<Loaded>();
        var entries = jar.getJarFile().entries();
        while (entries.hasMoreElements()) {
            var entry = entries.nextElement();
            var name = entry.getName();
            if (!name.startsWith(prefix) || !name.endsWith("/verdict.json")) {
                continue;
            }
            var rest = name.substring(prefix.length());
            var slash = rest.indexOf('/');
            if (slash <= 0 || slash != rest.lastIndexOf('/')) {
                continue;
            }
            try (var input = jar.getJarFile().getInputStream(entry)) {
                var source = field(new String(input.readAllBytes(), StandardCharsets.UTF_8), "source");
                refuseJev(source);
                loaded.add(new Loaded(rest.substring(0, slash), source, true));
            }
        }
        loaded.sort(Comparator.comparing(Loaded::name));
        return List.copyOf(loaded);
    }

    /**
     * Loads {@code root/learn/packaged}.
     *
     * @param root the data root
     * @return imported artifacts
     * @throws IOException when a verdict cannot be read
     */
    public static List<Loaded> load(Path root) throws IOException {
        return loadDirectory(root.resolve("learn").resolve("packaged"));
    }

    /**
     * Loads each child that has {@code verdict.json}.
     *
     * @param packaged the packaged directory
     * @return imported artifacts
     * @throws IOException when a verdict cannot be read
     */
    public static List<Loaded> loadDirectory(Path packaged) throws IOException {
        if (!Files.isDirectory(Objects.requireNonNull(packaged, "packaged"))) {
            return List.of();
        }
        var loaded = new ArrayList<Loaded>();
        try (var stream = Files.list(packaged)) {
            for (var child : stream.filter(Files::isDirectory).sorted().toList()) {
                var verdict = child.resolve("verdict.json");
                if (!Files.isRegularFile(verdict)) {
                    continue;
                }
                var source = field(Files.readString(verdict), "source");
                refuseJev(source);
                loaded.add(new Loaded(child.getFileName().toString(), source, true));
            }
        }
        return List.copyOf(loaded);
    }

    private static String field(String text, String name) throws IOException {
        try {
            return JsonFields.topLevel(text).getOrDefault(name, "");
        } catch (IllegalArgumentException malformed) {
            throw new IOException("packaged verdict is not one JSON object", malformed);
        }
    }

    /**
     * Refuses any source but a teacher: {@code outcome}, {@code rollout} or {@code kev-*}, compared without case,
     * and never a name that mentions Jev in any case (rule A16: Jev's outputs are never labels).
     *
     * @param source the declared source
     * @throws IllegalArgumentException when the source is blank, names Jev, or is not a teacher
     */
    public static void refuseJev(String source) {
        var lower = Objects.requireNonNull(source, "source").trim().toLowerCase(Locale.ROOT);
        var teacher = "outcome".equals(lower) || "rollout".equals(lower) || lower.startsWith("kev-");
        if (!teacher || lower.contains("jev")) {
            throw new IllegalArgumentException("packaged jev source");
        }
    }
}
