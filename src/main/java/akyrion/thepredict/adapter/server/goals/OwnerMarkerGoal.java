package akyrion.thepredict.adapter.server.goals;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import org.bukkit.entity.Mob;

import java.util.EnumSet;
import java.util.Objects;

/**
 * {@code thepredict:owner} marker goal (I10, M05.3). It never activates; its presence tells that ThePredict's
 * goal set is still installed, and it keeps the catalog revision, session generation and goal count of that
 * set so {@link GoalOwnership} can decide whether to reinstall ({@link InstallDecision}). A refusal marker
 * ({@link #refused}) records instead that the set of that revision and generation could not be installed, so
 * the stateless adapter does not clear and reinstall the mob again for the same inputs.
 *
 * <p>The marker declares {@code LOOK}, which has no effect because it never runs; a goal without types would get
 * the {@code UNKNOWN_BEHAVIOR} flag from Paper ({@code PaperCustomGoal}, javap).
 */
public final class OwnerMarkerGoal implements Goal<Mob> {
    /** Priority of a refusal marker; it never runs, so the value only has to be valid. */
    public static final int REFUSAL_PRIORITY = 100;

    private final GoalKey<Mob> key;
    private final long revision;
    private final int generation;
    private final int goalCount;
    private final String refusal;

    /**
     * Creates the marker of a generated goal set, declaring {@code LOOK}.
     *
     * @param key {@link GoalKeys#owner()}
     * @param revision catalog revision of the installed set
     * @param generation session generation of the installed set
     * @param goalCount goals of the installed set, {@code >= 0}
     */
    public OwnerMarkerGoal(GoalKey<Mob> key, long revision, int generation, int goalCount) {
        this(key, revision, generation, goalCount, "");
    }

    /**
     * Creates the marker left on a mob whose generated set could not be installed; it declares {@code LOOK} and
     * never runs, so the mob keeps its MythicMobs AI.
     *
     * @param key {@link GoalKeys#owner()}
     * @param revision catalog revision of the refused set
     * @param generation session generation of the refused set
     * @param reason fixed cause returned as {@code Unsupported}; not blank
     * @return the refusal marker
     */
    public static OwnerMarkerGoal refused(GoalKey<Mob> key, long revision, int generation, String reason) {
        if (reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
        return new OwnerMarkerGoal(key, revision, generation, 0, reason);
    }

    private OwnerMarkerGoal(GoalKey<Mob> key, long revision, int generation, int goalCount, String refusal) {
        this.key = Objects.requireNonNull(key, "key");
        if (goalCount < 0) {
            throw new IllegalArgumentException("goalCount must be >= 0, was " + goalCount);
        }
        this.revision = revision;
        this.generation = generation;
        this.goalCount = goalCount;
        this.refusal = refusal;
    }

    /**
     * Cause of a refusal marker.
     *
     * @return the reason, or empty text for the marker of an installed set
     */
    public String refusal() {
        return refusal;
    }

    /**
     * Catalog revision of the installed set.
     *
     * @return revision
     */
    public long revision() {
        return revision;
    }

    /**
     * Session generation of the installed set.
     *
     * @return generation
     */
    public int generation() {
        return generation;
    }

    /**
     * Number of goals of the installed set, without {@code clear}.
     *
     * @return goal count
     */
    public int goalCount() {
        return goalCount;
    }

    /**
     * Never activates.
     *
     * @return always {@code false}
     */
    @Override
    public boolean shouldActivate() {
        return false;
    }

    /**
     * Returns the key {@code <plugin>:owner}.
     *
     * @return goal key
     */
    @Override
    public GoalKey<Mob> getKey() {
        return key;
    }

    /**
     * Returns the declared flag.
     *
     * @return a new set with {@code LOOK}
     */
    @Override
    public EnumSet<GoalType> getTypes() {
        return EnumSet.of(GoalType.LOOK);
    }
}
