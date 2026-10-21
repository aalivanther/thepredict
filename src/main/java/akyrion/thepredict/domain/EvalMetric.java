package akyrion.thepredict.domain;

/**
 * Named evaluation counters (M22.1, extended by M23.10 and M24). One definition for the bank, the simulator and the
 * server records. Aggregation of labels is the mode of the repetitions of one scenario (M16.7) and only
 * {@code REVIEWED} labels count. The harness computes the values. The domain only names them.
 *
 * <p>{@code WINS}, {@code DRAWS} and {@code LOSSES} are separate round counts. {@code SCORE} is
 * {@code (wins + draws / 2) / n}. {@code WIN_TIME_MEDIAN_TICKS} is the first time the win CIF reaches 0.5, or a dash
 * when the win rate stays under 0.5. {@code TTK_TARGET_TICKS} runs from {@code ENGAGED} to {@code TARGET_DIED}.
 * {@code DAMAGE_TAKEN_PER_WIN_RATIO} is health lost by the winner. {@code LABEL_ACCEPTABLE_RATIO},
 * {@code LABEL_BEST_RATIO} and {@code LABEL_FORBIDDEN_COUNT} are per distinct scenario.
 * {@code CONTROL_SHARE_RATIO} is the share of ticks in each control level. {@code ROUNDTRIP_MILLIS} and
 * {@code ANSWER_AGE_TICKS} are latency and age. {@code REQUESTS_PER_MINUTE}, {@code INPUT_TOKENS_PER_WIN} and
 * {@code USD_PER_WIN} use paid input tokens and the single price of one million input tokens.
 * {@code CASTS_PER_MINUTE_RATIO} compares casts with the cooldown ceiling. {@code TACTIC_ENTROPY_RATIO} is normalised
 * Shannon entropy of adopted tactics. {@code UNWARNED_HITS_PER_MINUTE} counts player hits that land before the
 * preset reaction floor. {@code FAIRNESS_VIOLATIONS} must stay at zero.
 */
public enum EvalMetric {
    /** Wins of the mob. */
    WINS,
    /** Draws. */
    DRAWS,
    /** Losses of the mob. */
    LOSSES,
    /** Bradley-Terry score. */
    SCORE,
    /** Median ticks of a win. */
    WIN_TIME_MEDIAN_TICKS,
    /** Target time to kill. */
    TTK_TARGET_TICKS,
    /** Damage taken per win. */
    DAMAGE_TAKEN_PER_WIN_RATIO,
    /** Agreement of a label. */
    LABEL_AGREE_RATIO,
    /** Share of distinct scenarios whose reviewed label is acceptable (M16.7 mode). */
    LABEL_ACCEPTABLE_RATIO,
    /** Share of distinct scenarios whose reviewed label names the best tactic (M16.7 mode). */
    LABEL_BEST_RATIO,
    /** Distinct scenarios whose reviewed label is forbidden (M16.7 mode). */
    LABEL_FORBIDDEN_COUNT,
    /** Share of ticks under control. */
    CONTROL_SHARE_RATIO,
    /** Model round trip. */
    ROUNDTRIP_MILLIS,
    /** Age of the answer. */
    ANSWER_AGE_TICKS,
    /** Requests per minute. */
    REQUESTS_PER_MINUTE,
    /** Input tokens per win. */
    INPUT_TOKENS_PER_WIN,
    /** Dollars per win. */
    USD_PER_WIN,
    /** Casts per minute against the reference. */
    CASTS_PER_MINUTE_RATIO,
    /** Tactic entropy. */
    TACTIC_ENTROPY_RATIO,
    /** Hits of medium or harder with no readable tell, per minute. */
    UNWARNED_HITS_PER_MINUTE,
    /** Fairness violations, including tell violations. */
    FAIRNESS_VIOLATIONS,
    /** Share of damage that was telegraphed. */
    TELEGRAPHED_DAMAGE_SHARE_RATIO,
    /** Dodges after a real tell. Feints are excluded. */
    DODGE_AFTER_TELEGRAPH_RATIO,
    /** Damage taken from the tell's target during recovery or stagger. Feints are excluded. */
    PUNISH_TAKEN_RATIO,
    /** Tells that whiffed. Feints are excluded. */
    WHIFF_RATIO,
    /** Tells the caster closed itself. */
    TELEGRAPH_SELF_ABORT_RATIO,
    /** Feints over tells. */
    FEINT_RATIO,
    /** Hits the author opted out of with {@link ShortWindUp#IMMEDIATE}. */
    UNWARNED_BY_OPT_OUT,
    /** Hits whose cue is only the native skill. */
    NATIVE_HITS_PER_MINUTE,
    /** Stance changes per minute. */
    STANCE_CHANGES_PER_MINUTE,
    /** Staggers per minute. */
    STAGGERS_PER_MINUTE,
    /** Deaths dealt by an unwarned hit. */
    DEATH_BY_UNWARNED,
    /** Share of damage the mob took from the ground. */
    HAZARD_DAMAGE_SELF_RATIO,
    /** Mob deaths to a hazard per hour. */
    HAZARD_DEATHS_MOB_PER_HOUR,
    /** Target deaths to a hazard per hour. */
    HAZARD_DEATHS_TARGET_PER_HOUR,
    /** Pushes that ended in a hazard. */
    PUSHED_INTO_HAZARD,
    /** Destinations the guard rejected, per minute. */
    UNSAFE_DESTINATION_REJECTED_PER_MINUTE,
    /** Casts the escape guard held. */
    ESCAPE_BLOCKED,
    /** Choices the push policy deferred. */
    PUSH_DEFERRED,
    /** Tells skipped because the answer did not fit. */
    TELL_INFEASIBLE_RATIO,
    /** Filtered zone facts that changed, per minute. */
    ZONE_FACT_CHANGES_PER_MINUTE,
    /** Bodies that differ only by one zone phrase. */
    BODY_CHURN_BY_ZONE_RATIO,
    /** Cover cells the next capture could still see. */
    COVER_FALSE_POSITIVE_RATIO,
    /** Columns a verification corrected without an event. */
    ZONE_UNSEEN_CHANGES,
    /** Most simultaneous preparations against one player. */
    MAX_ATTACKERS_ON_PLAYER,
    /** Impacts against one player in twenty ticks, p99. */
    IMPACTS_PER_SECOND_P99,
    /** Ticks with two big tells open. */
    BIG_TELL_OVERLAP_TICKS,
    /** Ticks with incompatible tell answers. */
    ANSWER_CONFLICT_TICKS,
    /** Medium or harder attacks without first-hand sight. */
    SECOND_HAND_ATTACKS,
    /** Share of time the exit sector stayed open. */
    EXIT_SECTOR_OPEN_RATIO,
    /** Ticks a member had no token, no useful skill and was off its slot. */
    IDLE_MEMBER_TICKS,
    /** Pairs of members closer than one block. */
    CLUMPING_PAIR_TICKS,
    /** Share of group damage on the target that dies first. */
    FOCUS_EFFICIENCY,
    /** Damage dealt after the target died. */
    OVERKILL_RATIO,
    /** Group time to kill one player. */
    GROUP_TTK,
    /** Reads of a stale membership or plan epoch. */
    GROUP_STALE_EPOCH,
    /** Ticks a session degraded to fair because its copy was stale. */
    GROUP_COPY_STALE_TICKS,
    /** Ticks the real impact differed from the reservation. */
    IMPACT_DEVIATION_TICKS,
    /** Pairs of real impacts closer than the reserved gap. */
    REAL_SPACING_BELOW_RESERVED,
    /** Leader reserves that expired without an open tell. */
    LEADER_RESERVE_EXPIRED,
    /** Role changes per member per minute. */
    ROLE_SWITCHES_PER_MINUTE,
    /** Precision of an active pattern against the script label. */
    PATTERN_PRECISION,
    /** Recall of an active pattern against the script label. */
    PATTERN_RECALL,
    /** False activations over trials. */
    PATTERN_FALSE_ACTIVATION,
    /** Trials of a detection experiment. */
    PATTERN_DETECTION_TRIALS,
    /** Ticks until a detection. */
    PATTERN_DETECTION_TICKS,
    /** Trials of an adaptation experiment. */
    PATTERN_ADAPTATION_TRIALS,
    /** Top-1 forecast accuracy. */
    FORECAST_ACCURACY,
    /** Share of motivated casts. */
    EXPLOIT_SHARE,
    /** Longest run of the same exploited pattern. */
    SAME_PATTERN_STREAK_MAX,
    /** Time-to-kill gain of exploitation. */
    EXPLOIT_GAIN_TTK,
    /** Bodies that differ only by a pattern phrase. */
    BODY_CHURN_BY_PATTERN_RATIO,
    /** Detection points gained by a seed. */
    MEMORY_DETECTION_GAIN,
    /** Activations that survived a diluted habit. */
    MEMORY_STALE_ACTIVATION,
    /** Casts that exploited a diluted habit. */
    MEMORY_STALE_EXPLOIT_CASTS,
    /** Fights until the mean falls after a break. */
    MEMORY_RECOVERY_FIGHTS,
    /** Longest exploit streak across fights. */
    CROSS_FIGHT_EXPLOIT_STREAK,
    /** Fights whose time-to-kill stayed inside the band. */
    TTK_BAND_FIGHTS,
    /** Error of the opening rung against the script. */
    START_RUNG_ERROR,
    /** Whether a seed left the off body unchanged. */
    MEMORY_SEED_PARITY,
    /** Direct method of an off-policy estimate (M28). */
    OPE_DM,
    /** Importance sampling (M28). */
    OPE_IPS,
    /** Self-normalised importance sampling (M28). */
    OPE_SNIPS,
    /** Doubly robust estimate (M28). */
    OPE_DR,
    /** Share of actions inside the logging support (M28). */
    OPE_SUPPORT_RATIO,
    /** Kish effective sample size (M28). */
    OPE_ESS
}
