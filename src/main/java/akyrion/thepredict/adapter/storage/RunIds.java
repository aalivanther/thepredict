package akyrion.thepredict.adapter.storage;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Run ids, the model label and the configuration fingerprint (M21.1). Errata E21-4: the nonce comes from
 * {@link UUID#randomUUID()} here, in {@code adapter.storage}, because C16 only binds {@code domain} and {@code engine}.
 * Nothing here keeps a key: the fingerprint leaves out every {@code apiKey} entry and the label leaves out a model that
 * is not a versioned {@code jev-} or {@code kev-} id (rules C8 and W6).
 */
public final class RunIds {
    /** Stored instead of a model that is not a versioned id, because a key pasted there could fit. */
    public static final String CUSTOM_MODEL = "<custom model>";

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(ZoneOffset.UTC);
    private static final Pattern DISPLAYABLE_MODEL = Pattern.compile("(jev|kev)-[0-9][0-9.]*");
    private static final Pattern KEY_ENTRY = Pattern.compile("(?i)(^|[\\s{,'\"])apikey['\"]?\\s*:");

    private RunIds() {
    }

    /**
     * A run id {@code yyyyMMdd'T'HHmmss'Z'-xxxx}.
     *
     * @param clock wall clock of the adapter
     * @return the id
     */
    public static String run(Clock clock) {
        var nonce = UUID.randomUUID().toString().replace("-", "").substring(0, 4);
        return STAMP.format(clock.instant()) + "-" + nonce;
    }

    /**
     * The model as storage keeps it in {@code runs.model} and the {@code run_start} line: a versioned {@code jev-} or
     * {@code kev-} id as it is, and {@link #CUSTOM_MODEL} for anything else.
     *
     * @param model the configured model
     * @return the label
     */
    public static String modelLabel(String model) {
        return DISPLAYABLE_MODEL.matcher(model).matches() ? model : CUSTOM_MODEL;
    }

    /**
     * The model as {@code decision_daily} and {@code answer_cache_daily} keep it: the {@link #modelLabel} of the noted
     * model, and as they are the empty name and the placeholder {@code -} of a decision taken before a model was noted.
     *
     * @param model the model of the delta
     * @return the stored name
     */
    static String dailyModel(String model) {
        return model.isEmpty() || model.equals("-") ? model : modelLabel(model);
    }

    /**
     * SHA-256 of the configuration without its {@code apiKey} entries. A line whose key is {@code apiKey}, with any
     * case, quotes or spaces before the colon, is left out with the more indented lines that continue its value; a
     * flow mapping that names {@code apiKey} is left out whole.
     *
     * @param yaml the file text
     * @return the hex digest
     */
    public static String configSha256(String yaml) {
        var kept = new StringBuilder(yaml.length());
        var skipDeeperThan = -1;
        for (var line : yaml.lines().toList()) {
            var indent = indentOf(line);
            if (skipDeeperThan >= 0) {
                if (line.isBlank() || indent > skipDeeperThan) {
                    continue;
                }
                skipDeeperThan = -1;
            }
            if (KEY_ENTRY.matcher(line).find()) {
                skipDeeperThan = indent;
                continue;
            }
            kept.append(line).append('\n');
        }
        return sha256(kept.toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * SHA-256 of bytes, as hex.
     *
     * @param bytes the bytes
     * @return the digest
     */
    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256", exception);
        }
    }

    /**
     * SHA-256 of a body.
     *
     * @param body the body
     * @return the digest
     */
    public static String bodySha256(String body) {
        return sha256(body.getBytes(StandardCharsets.UTF_8));
    }

    private static int indentOf(String line) {
        var indent = 0;
        while (indent < line.length() && (line.charAt(indent) == ' ' || line.charAt(indent) == '\t')) {
            indent++;
        }
        return indent;
    }
}
