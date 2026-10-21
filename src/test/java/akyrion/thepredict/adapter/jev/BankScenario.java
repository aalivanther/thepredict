package akyrion.thepredict.adapter.jev;

import akyrion.thepredict.domain.DistanceBand;
import akyrion.thepredict.domain.HealthBand;
import akyrion.thepredict.domain.Situation;
import akyrion.thepredict.domain.TargetCue;
import akyrion.thepredict.domain.Trend;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * One row of the declarative matrix of the labelled bank of M16.9: who asks, in which situation, what the mob perceives
 * and the labels a person proposes, without looking at what the code computes to fit now (M16.2). Immutable.
 *
 * @param id identifier {@code bank-<family>-<nn>}
 * @param family family of the bank
 * @param profile profile of the asked mob
 * @param situation situation of its only question
 * @param mobHealth health band of the mob
 * @param distance distance band of its target
 * @param trend trend of its target
 * @param reachable whether the target can be reached on foot
 * @param visible whether the mob sees its target
 * @param flankable whether the target can be flanked
 * @param targetHealth health band of the target
 * @param cues cues of the target
 * @param participants participants of the encounter, {@code >= 1}; more than one stand grouped by the target
 * @param best proposed best tactics
 * @param acceptable proposed acceptable tactics, which hold {@code best}
 * @param forbidden proposed forbidden tactics
 * @param rationale why, in one sentence a reviewer can check
 */
record BankScenario(String id, Family family, BankProfile profile, Situation situation, HealthBand mobHealth,
                    DistanceBand distance, Trend trend, boolean reachable, boolean visible, boolean flankable,
                    HealthBand targetHealth, Set<TargetCue> cues, int participants, List<String> best,
                    List<String> acceptable, List<String> forbidden, String rationale) {
    BankScenario {
        Objects.requireNonNull(id, "id");
        cues = cues.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(cues));
        best = List.copyOf(best);
        acceptable = List.copyOf(acceptable);
        forbidden = List.copyOf(forbidden);
    }

    /** Families of the bank, with their identifiers. */
    enum Family {
        MELEE("melee"), SHOOTER("shooter"), SHIELD("shield"), LOWHP("lowhp"), PILLAR("pillar"), GROUPED("grouped"),
        FINISHER("finisher"), DRINKING("drinking"), NOLOS("nolos"), FLEEING("fleeing"), CONT_MELEE("cont-melee"),
        CONT_CRITICAL("cont-critical");

        private final String key;

        Family(String key) {
            this.key = key;
        }

        String key() {
            return key;
        }
    }

    /** Profiles of the bank, all with declared purposes (D2 of M16). */
    enum BankProfile {
        /** The example of M15.9. */
        GUARDIAN,
        /** The example without the context {@code low_health_phase}, so a critical mob keeps bad options. */
        GUARDIAN_WITHOUT_LOW_HEALTH,
        /** {@code scenarios/bank/profiles/bank-skirmisher.yml}. */
        SKIRMISHER,
        /** {@code profiles/purpose-shield.yml} of M15.8. */
        SHIELD
    }

    /** Fluent start of a row. */
    static Builder of(String id, Family family, BankProfile profile) {
        return new Builder(id, family, profile);
    }

    /** Builder of one row; defaults: {@code NOW}, healthy mob and target, {@code MELEE}, steady, reachable, visible. */
    static final class Builder {
        private final String id;
        private final Family family;
        private final BankProfile profile;
        private Situation situation = Situation.NOW;
        private HealthBand mobHealth = HealthBand.HEALTHY;
        private DistanceBand distance = DistanceBand.MELEE;
        private Trend trend = Trend.STEADY;
        private boolean reachable = true;
        private boolean visible = true;
        private boolean flankable;
        private HealthBand targetHealth = HealthBand.HEALTHY;
        private Set<TargetCue> cues = Set.of();
        private int participants = 1;
        private List<String> best = List.of();
        private List<String> acceptable = List.of();
        private List<String> forbidden = List.of();

        private Builder(String id, Family family, BankProfile profile) {
            this.id = id;
            this.family = family;
            this.profile = profile;
        }

        Builder asking(Situation value) {
            situation = value;
            return this;
        }

        Builder mob(HealthBand value) {
            mobHealth = value;
            return this;
        }

        Builder at(DistanceBand band, Trend value) {
            distance = band;
            trend = value;
            return this;
        }

        Builder unreachable() {
            reachable = false;
            return this;
        }

        Builder hidden() {
            visible = false;
            return this;
        }

        Builder flankable() {
            flankable = true;
            return this;
        }

        Builder target(HealthBand value, TargetCue... values) {
            targetHealth = value;
            cues = Set.of(values);
            return this;
        }

        Builder grouped(int count) {
            participants = count;
            return this;
        }

        Builder best(String... values) {
            best = List.of(values);
            return this;
        }

        Builder acceptable(String... values) {
            acceptable = List.of(values);
            return this;
        }

        Builder forbidden(String... values) {
            forbidden = List.of(values);
            return this;
        }

        BankScenario because(String rationale) {
            return new BankScenario(id, family, profile, situation, mobHealth, distance, trend, reachable, visible,
                    flankable, targetHealth, cues, participants, best, acceptable, forbidden, rationale);
        }
    }
}
