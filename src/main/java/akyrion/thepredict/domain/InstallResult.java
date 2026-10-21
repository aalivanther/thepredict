package akyrion.thepredict.domain;

/** Result of installing the goals of a profile on a mob. Immutable. */
public sealed interface InstallResult permits InstallResult.Installed, InstallResult.Deferred,
        InstallResult.Unsupported {
    /**
     * The goals were installed.
     *
     * @param goalCount number of installed goals, {@code >= 0}
     */
    record Installed(int goalCount) implements InstallResult {
        /**
         * Validates the count.
         *
         * @throws IllegalArgumentException if {@code goalCount < 0}
         */
        public Installed {
            if (goalCount < 0) {
                throw new IllegalArgumentException("goalCount must be >= 0, was " + goalCount);
            }
        }
    }

    /**
     * The installation will be retried later.
     *
     * @param reason short cause; not blank
     */
    record Deferred(String reason) implements InstallResult {
        /**
         * Validates the reason.
         *
         * @throws NullPointerException if {@code reason} is {@code null}
         * @throws IllegalArgumentException if {@code reason} is blank
         */
        public Deferred {
            DomainChecks.requireText(reason, "reason");
        }
    }

    /**
     * The mob cannot receive the goals.
     *
     * @param reason short cause; not blank
     */
    record Unsupported(String reason) implements InstallResult {
        /**
         * Validates the reason.
         *
         * @throws NullPointerException if {@code reason} is {@code null}
         * @throws IllegalArgumentException if {@code reason} is blank
         */
        public Unsupported {
            DomainChecks.requireText(reason, "reason");
        }
    }
}
