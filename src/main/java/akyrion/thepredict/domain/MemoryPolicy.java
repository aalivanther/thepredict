package akyrion.thepredict.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

/**
 * Fingerprint of the keys that change a memory decision (M27.7). Cache size and retention are excluded.
 */
public final class MemoryPolicy {
    /** Codec version inside the fingerprint. */
    public static final int CODEC_VERSION = 1;
    /** Policy version inside the fingerprint. */
    public static final int POLICY_VERSION = 1;

    private MemoryPolicy() {
    }

    /**
     * SHA-256 of the decision keys and the four phrases.
     *
     * @param settings the settings
     * @return lowercase hex
     */
    public static String fingerprint(MemorySettings settings) {
        return HexFormat.of().formatHex(sha256(canonical(settings).getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Canonical JSON of the decision keys. Key order does not change the text. {@code p1} is {@code θ − 0.1}.
     * {@code maxFactsPerRequest} is included. Cache size and retention are not.
     *
     * @param settings the settings
     * @return the JSON object
     */
    public static String canonical(MemorySettings settings) {
        var keys = new TreeMap<String, String>();
        keys.put("codec", Integer.toString(CODEC_VERSION));
        keys.put("policy", Integer.toString(POLICY_VERSION));
        keys.put("actionVocab", Integer.toString(PlayerMemoryCodec.ACTION_VOCAB));
        keys.put("patternVocab", Integer.toString(PlayerMemoryCodec.PATTERN_VOCAB));
        keys.put("fightDecay", Double.toString(settings.fightDecay()));
        keys.put("halfLifeDays", Integer.toString(settings.halfLifeDays()));
        keys.put("seedMaxTrials", Integer.toString(settings.seedMaxTrials()));
        keys.put("minFightTrials", Integer.toString(settings.minFightTrials()));
        keys.put("crossFightH", Integer.toString(settings.crossFightH()));
        keys.put("styleBreakFactor", Double.toString(settings.styleBreakFactor()));
        keys.put("maxBreaks", Integer.toString(PlayerMemoryMath.MAX_BREAKS));
        keys.put("p1", Double.toString(PlayerMemoryMath.p1()));
        keys.put("rungPriorFights", Integer.toString(settings.rungPriorFights()));
        keys.put("maxRungOffset", Integer.toString(settings.maxRungOffset()));
        keys.put("minDamageShareRatio", Double.toString(settings.minDamageShareRatio()));
        keys.put("factMaxAgeDays", Integer.toString(settings.factMaxAgeDays()));
        keys.put("maxFactsPerRequest", Integer.toString(settings.maxFactsPerRequest()));
        keys.put("phrases", HexFormat.of().formatHex(sha256(phrases().getBytes(StandardCharsets.UTF_8))));
        var text = new StringBuilder();
        text.append('{');
        var first = true;
        for (Map.Entry<String, String> entry : keys.entrySet()) {
            if (!first) {
                text.append(',');
            }
            first = false;
            text.append('"').append(entry.getKey()).append("\":");
            if (numeric(entry.getValue())) {
                text.append(entry.getValue());
            } else {
                text.append('"').append(entry.getValue()).append('"');
            }
        }
        text.append('}');
        return text.toString();
    }

    private static boolean numeric(String value) {
        if (value.isEmpty()) {
            return false;
        }
        for (var index = 0; index < value.length(); index++) {
            var symbol = value.charAt(index);
            if (symbol != '.' && symbol != '-' && (symbol < '0' || symbol > '9')) {
                return false;
            }
        }
        return true;
    }

    /**
     * The four phrases joined, for the fingerprint.
     *
     * @return the text
     */
    public static String phrases() {
        return MemoryFact.LOST_MANY.phrase("player_N") + "\n" + MemoryFact.LOST_LAST.phrase("player_N") + "\n"
                + MemoryFact.BEAT_MANY.phrase("player_N") + "\n" + MemoryFact.BEAT_BEFORE.phrase("player_N");
    }

    private static byte[] sha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
