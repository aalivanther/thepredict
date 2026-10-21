package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ActiveUseConditions;
import akyrion.thepredict.domain.CombatFacts;
import akyrion.thepredict.domain.DistanceBand;
import akyrion.thepredict.domain.FitCheck;
import akyrion.thepredict.domain.HealthBand;
import akyrion.thepredict.domain.MobFacts;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.OptionContext;
import akyrion.thepredict.domain.OptionPruning;
import akyrion.thepredict.domain.OptionSet;
import akyrion.thepredict.domain.ParticipantFacts;
import akyrion.thepredict.domain.PruningMode;
import akyrion.thepredict.domain.QuestionDetail;
import akyrion.thepredict.domain.QuestionKey;
import akyrion.thepredict.domain.QuestionSpec;
import akyrion.thepredict.domain.Situation;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.domain.ZoneMode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * The questions of one encounter request (M09.8 steps 2 to 4, M16.1, M16.5, M16.6). Pure: it reads only the captured
 * facts, the profiles of the active sessions and the tactic of their current intents. A mob is asked about only with a
 * perceived target and at least two distinct eligible tactics; a mob whose target lies beyond every perception radius
 * waits until the heartbeat perceives it, and one with a single distinct tactic is resolved without Jev (M10).
 *
 * <p>Options are ordered by descending priority and then by ID; tactics identical in everything are merged and the
 * list is capped at {@code decision.maxOptionsPerQuestion}, never dropping the current tactic ({@link OptionSet}). A
 * contingency offers the tactics that would be eligible under its hypothesis ({@link MobFacts#eligibleIn}), at most
 * {@value #COMPACT_CONTINGENCY_OPTIONS} with the {@code COMPACT} detail, and is not asked with fewer than two. A
 * group brief leaves out the mobs its question set does not ask ({@code GroupBrief#asksMember}, M25.10, DOC-02).
 */
final class EncounterQuestions {
    /** Most options of a contingency with the {@code COMPACT} detail (M16.2): the highest priorities after merging. */
    static final int COMPACT_CONTINGENCY_OPTIONS = 5;

    private EncounterQuestions() {
    }

    /**
     * Builds the questions: per mob in the order of the facts, {@code NOW} and then up to {@code contingenciesPerMob}
     * contingencies ({@code IF_TARGET_IN_MELEE} unless the target is already in melee, {@code IF_OWN_HEALTH_CRITICAL}
     * unless the health is already critical) that keep two options, keyed {@code q0}, {@code q1}… in that order; then,
     * for the mobs whose profile sends fit checks, one fit check per {@code NOW} option, keyed after the questions.
     *
     * @param facts captured facts of the encounter
     * @param profileOf profile of an active session of exactly that generation, empty otherwise
     * @param current tactic of the current intent of a mob, empty without one
     * @param maxOptions most options of one question, {@code >= 2}
     * @return the plan; no questions when no mob qualifies
     */
    static Plan plan(CombatFacts facts, Function<MobKey, Optional<TacticalProfile>> profileOf,
                     Function<MobKey, Optional<TacticId>> current, int maxOptions) {
        return plan(facts, profileOf, current, maxOptions, PruningMode.OFF, ZoneMode.OFF);
    }

    /**
     * Builds the questions. {@link PruningMode#OFF} keeps today's priority order (errata E19-1, DP6). The {@code NOW}
     * question reads {@link MobFacts#eligibleSoon()}, which equals {@code eligibleTactics} when the horizon is 0.
     *
     * @param facts captured facts of the encounter
     * @param profileOf profile of an active session of exactly that generation, empty otherwise
     * @param current tactic of the current intent of a mob, empty without one
     * @param maxOptions most options of one question, {@code >= 2}
     * @param pruning {@code off} or {@code safe}
     * @return the plan; no questions when no mob qualifies
     */
    static Plan plan(CombatFacts facts, Function<MobKey, Optional<TacticalProfile>> profileOf,
                     Function<MobKey, Optional<TacticId>> current, int maxOptions, PruningMode pruning) {
        return plan(facts, profileOf, current, maxOptions, pruning, ZoneMode.OFF);
    }

    /**
     * Builds the questions with the global zone mode. An empty profile mode inherits it.
     *
     * @param facts captured facts of the encounter
     * @param profileOf profile of an active session of exactly that generation, empty otherwise
     * @param current tactic of the current intent of a mob, empty without one
     * @param maxOptions most options of one question, {@code >= 2}
     * @param pruning {@code off} or {@code safe}
     * @param zoneMode global zone mode
     * @return the plan; no questions when no mob qualifies
     */
    static Plan plan(CombatFacts facts, Function<MobKey, Optional<TacticalProfile>> profileOf,
                     Function<MobKey, Optional<TacticId>> current, int maxOptions, PruningMode pruning,
                     ZoneMode zoneMode) {
        Objects.requireNonNull(pruning, "pruning");
        Objects.requireNonNull(zoneMode, "zoneMode");
        var questions = new ArrayList<QuestionSpec>();
        var profiles = new LinkedHashMap<MobKey, TacticalProfile>();
        var prunes = new ArrayList<PruneNotice>();
        for (var mob : facts.mobs()) {
            var profile = mob.target().isPresent() && facts.brief().asksMember(mob.mob().entityId())
                    ? profileOf.apply(mob.mob()) : Optional.<TacticalProfile>empty();
            if (profile.isEmpty()) {
                continue;
            }
            var listing = new Listing(profile.get(), mob, facts.participants(), pruning, prunes, zoneMode,
                    current.apply(mob.mob()).orElse(null));
            var now = listing.options(mob.eligibleSoon(), Situation.NOW, maxOptions);
            if (now.size() < QuestionSpec.MIN_OPTIONS) {
                continue;
            }
            profiles.put(mob.mob(), profile.get());
            questions.add(listing.question(questions.size(), Situation.NOW, now));
            var cap = profile.get().timing().questionDetail() == QuestionDetail.COMPACT
                    ? Math.min(maxOptions, COMPACT_CONTINGENCY_OPTIONS) : maxOptions;
            var asked = 0;
            for (var situation : contingencies(mob)) {
                if (asked >= profile.get().timing().contingenciesPerMob()) {
                    break;
                }
                var options = listing.options(mob.eligibleIn(situation), situation, cap);
                if (options.size() >= QuestionSpec.MIN_OPTIONS) {
                    questions.add(listing.question(questions.size(), situation, options));
                    asked++;
                }
            }
        }
        return new Plan(questions, profiles, fitChecks(questions, profiles), prunes);
    }

    /**
     * The option lists of one mob. The current tactic is resolved once, so no {@code Optional} travels as a
     * parameter (rule C4); a mob without a current intent has none. Only the contingencies that are asked are
     * listed, so pruning counts and {@code inspect} never see a contingency Jev was not asked about.
     */
    private static final class Listing {
        private final TacticalProfile profile;
        private final MobFacts mob;
        private final List<ParticipantFacts> participants;
        private final PruningMode pruning;
        private final List<PruneNotice> prunes;
        private final ZoneMode effectiveZone;
        private final TacticId kept;

        Listing(TacticalProfile profile, MobFacts mob, List<ParticipantFacts> participants, PruningMode pruning,
                List<PruneNotice> prunes, ZoneMode zoneMode, TacticId kept) {
            this.profile = profile;
            this.mob = mob;
            this.participants = participants;
            this.pruning = pruning;
            this.prunes = prunes;
            this.effectiveZone = profile.zone().effective(zoneMode);
            this.kept = kept;
        }

        QuestionSpec question(int index, Situation situation, List<TacticId> options) {
            var keep = kept != null && options.contains(kept) ? Optional.of(kept) : Optional.<TacticId>empty();
            return new QuestionSpec(QuestionKey.of(index), mob.mob(), situation, options, keep);
        }

        List<TacticId> options(Set<TacticId> eligible, Situation situation, int maxOptions) {
            var ordered = ordered(eligible);
            var hasKept = kept != null && ordered.contains(kept);
            if (pruning != PruningMode.SAFE) {
                return hasKept
                        ? OptionSet.capped(OptionSet.distinct(ordered, profile, kept), maxOptions, kept)
                        : OptionSet.capped(OptionSet.distinct(ordered, profile), maxOptions);
            }
            var now = OptionContext.hypothetical(ActiveUseConditions.of(mob, participants), situation);
            var result = hasKept
                    ? OptionPruning.prune(ordered, profile, mob, now, profile.timing().castMode(), maxOptions, kept,
                    effectiveZone)
                    : OptionPruning.prune(ordered, profile, mob, now, profile.timing().castMode(), maxOptions,
                    effectiveZone);
            prunes.add(new PruneNotice(mob.mob(), result, ordered.size() >= QuestionSpec.MIN_OPTIONS
                    && result.kept().size() < QuestionSpec.MIN_OPTIONS));
            return result.kept();
        }

        private List<TacticId> ordered(Set<TacticId> eligible) {
            return eligible.stream().filter(profile.tactics()::containsKey)
                    .sorted(Comparator.comparingInt((TacticId id) -> -profile.tactics().get(id).priority())
                            .thenComparing(TacticId::value)).toList();
        }
    }

    private static List<Situation> contingencies(MobFacts mob) {
        var contingencies = new ArrayList<Situation>(2);
        if (mob.target().orElseThrow().distance() != DistanceBand.MELEE) {
            contingencies.add(Situation.IF_TARGET_IN_MELEE);
        }
        if (mob.health() != HealthBand.CRITICAL) {
            contingencies.add(Situation.IF_OWN_HEALTH_CRITICAL);
        }
        return contingencies;
    }

    private static List<FitCheck> fitChecks(List<QuestionSpec> questions, Map<MobKey, TacticalProfile> profiles) {
        var checks = new ArrayList<FitCheck>();
        for (var question : questions) {
            if (question.situation() == Situation.NOW && profiles.get(question.mob()).timing().sendsFitChecks()) {
                for (var option : question.options()) {
                    checks.add(new FitCheck(QuestionKey.of(questions.size() + checks.size()), question.mob(), option));
                }
            }
        }
        return checks;
    }

    /**
     * Questions of one request, the profile of each asked mob and the fit checks.
     *
     * @param questions questions in key order
     * @param profiles profile of each asked mob
     * @param fitChecks fit checks of the {@code NOW} options, keyed after the questions (M16.5)
     */
    record Plan(List<QuestionSpec> questions, Map<MobKey, TacticalProfile> profiles, List<FitCheck> fitChecks,
                List<PruneNotice> prunes) {
        /**
         * The plan of a caller from before pruning recorded its notices (rule W5).
         *
         * @param questions questions in key order
         * @param profiles profile of each asked mob
         * @param fitChecks fit checks of the {@code NOW} options
         */
        Plan(List<QuestionSpec> questions, Map<MobKey, TacticalProfile> profiles, List<FitCheck> fitChecks) {
            this(questions, profiles, fitChecks, List.of());
        }

        Plan {
            questions = List.copyOf(questions);
            profiles = Map.copyOf(profiles);
            fitChecks = List.copyOf(fitChecks);
            prunes = List.copyOf(prunes);
        }
    }

    /**
     * One pruned question and whether it left a single option for the local planner.
     *
     * @param mob the mob
     * @param result what the prune kept and dropped
     * @param resolved whether pruning left fewer than two options where the unpruned list had two
     */
    record PruneNotice(MobKey mob, OptionPruning.PruneResult result, boolean resolved) {
        PruneNotice {
            Objects.requireNonNull(mob, "mob");
            Objects.requireNonNull(result, "result");
        }
    }
}
