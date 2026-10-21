package akyrion.thepredict.engine;

import java.util.List;

/**
 * A read the server thread asks of the store (M21.6). Sealed so a later module can add a variant.
 */
public sealed interface AggregateQuery permits AggregateQuery.OutcomeStatsQuery,
        AggregateQuery.DifficultyStatsQuery, AggregateQuery.VerdictQuery, AggregateQuery.SubjectMemoryQuery,
        AggregateQuery.ValueStatsQuery, AggregateQuery.ArtifactsQuery, AggregateQuery.ChoiceStatsQuery,
        AggregateQuery.AgreementStatsQuery {
    /**
     * Loads {@code outcome_stats} for the linked profiles, or every profile when the list is empty.
     *
     * @param profiles profile ids
     */
    record OutcomeStatsQuery(List<String> profiles) implements AggregateQuery {
        /**
         * Copies the list.
         *
         * @throws NullPointerException if the list is {@code null}
         */
        public OutcomeStatsQuery {
            profiles = List.copyOf(profiles);
        }
    }

    /**
     * Loads {@code m22_difficulty_stats} for one profile, or every profile when the id is empty.
     *
     * @param profile profile id, empty for all
     */
    record DifficultyStatsQuery(String profile) implements AggregateQuery {
        /**
         * Validates the query.
         */
        public DifficultyStatsQuery {
            java.util.Objects.requireNonNull(profile, "profile");
        }
    }

    /**
     * Loads the newest gate verdicts.
     *
     * @param limit how many, 1 to 200
     */
    record VerdictQuery(int limit) implements AggregateQuery {
    }

    /**
     * Loads one player's memory before batches that might still be in flight (M27.4).
     *
     * @param subject the player
     */
    record SubjectMemoryQuery(java.util.UUID subject) implements AggregateQuery {
        /**
         * Validates the query.
         */
        public SubjectMemoryQuery {
            java.util.Objects.requireNonNull(subject, "subject");
        }
    }

    /**
     * One page of {@code m28_value_stats} (M28.1). At most 2000 rows.
     *
     * @param profileFilter profile id, empty for every profile
     * @param sinceWeek first week, inclusive
     * @param pageRows rows to return, 1 to 2000
     * @param cursor previous cursor, or empty for the first page
     */
    record ValueStatsQuery(String profileFilter, int sinceWeek, int pageRows, String cursor) implements AggregateQuery {
        /**
         * A first page. The cursor is empty (rule W5).
         *
         * @param profileFilter profile id, empty for every profile
         * @param sinceWeek first week, inclusive
         * @param pageRows rows to return, 1 to 2000
         */
        public ValueStatsQuery(String profileFilter, int sinceWeek, int pageRows) {
            this(profileFilter, sinceWeek, pageRows, "");
        }

        /**
         * Validates the page size.
         */
        public ValueStatsQuery {
            java.util.Objects.requireNonNull(profileFilter, "profileFilter");
            java.util.Objects.requireNonNull(cursor, "cursor");
            if (pageRows < 1 || pageRows > 2000) {
                throw new IllegalArgumentException("pageRows must be within [1, 2000]");
            }
        }
    }

    /**
     * Loads artifact rows.
     */
    record ArtifactsQuery() implements AggregateQuery {
    }

    /**
     * One page of {@code m29_choice_stats} (M29.1).
     *
     * @param profileFilter profile id, empty for every profile
     * @param sinceWeek first week, inclusive
     * @param pageRows rows to return, 1 to 2000
     */
    record ChoiceStatsQuery(String profileFilter, int sinceWeek, int pageRows) implements AggregateQuery {
        /**
         * Validates the page size.
         */
        public ChoiceStatsQuery {
            java.util.Objects.requireNonNull(profileFilter, "profileFilter");
            if (pageRows < 1 || pageRows > 2000) {
                throw new IllegalArgumentException("pageRows must be within [1, 2000]");
            }
        }
    }

    /**
     * One page of {@code m29_agreement_stats} (M29.1).
     *
     * @param artifact artifact fingerprint, empty for every artifact
     * @param pageRows rows to return, 1 to 2000
     */
    record AgreementStatsQuery(String artifact, int pageRows) implements AggregateQuery {
        /**
         * Validates the page size.
         */
        public AgreementStatsQuery {
            java.util.Objects.requireNonNull(artifact, "artifact");
            if (pageRows < 1 || pageRows > 2000) {
                throw new IllegalArgumentException("pageRows must be within [1, 2000]");
            }
        }
    }
}
