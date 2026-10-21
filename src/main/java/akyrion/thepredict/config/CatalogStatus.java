package akyrion.thepredict.config;

/** Summary of a {@link ProfileCatalog} for {@code /thepredict status} (M12). */
public enum CatalogStatus {
    /** No profiles and no errors: a fresh installation waiting for its first profile; not an error. */
    SETUP_PENDING,
    /** Profiles loaded without errors; warnings may exist. */
    LOADED,
    /** At least one error; the valid profiles are still loaded. */
    INVALID
}
