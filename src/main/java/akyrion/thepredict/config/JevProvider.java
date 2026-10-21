package akyrion.thepredict.config;

/**
 * Who answers {@code jev.*} (M29.9). {@code kev} never reads {@code TYPESAFE_API_KEY}.
 */
public enum JevProvider {
    /** TypeSafe System One. A key is required. */
    TYPESAFE,
    /** A local model. An empty key is allowed and no Authorization header is sent. */
    KEV;

    /**
     * Parses {@code jev.provider}.
     *
     * @param token the token
     * @return the provider
     */
    public static JevProvider parse(String token) {
        return switch (token) {
            case "typesafe" -> TYPESAFE;
            case "kev" -> KEV;
            default -> throw new ConfigurationException("jev.provider", "must be typesafe or kev");
        };
    }
}
