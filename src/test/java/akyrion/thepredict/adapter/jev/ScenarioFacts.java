package akyrion.thepredict.adapter.jev;

import akyrion.thepredict.config.ProfileLoader;
import akyrion.thepredict.domain.ArmorBand;
import akyrion.thepredict.domain.ChoiceId;
import akyrion.thepredict.domain.CombatFacts;
import akyrion.thepredict.domain.ContextId;
import akyrion.thepredict.domain.CooldownClass;
import akyrion.thepredict.domain.DamageTier;
import akyrion.thepredict.domain.DecisionRequest;
import akyrion.thepredict.domain.Delivery;
import akyrion.thepredict.domain.DistanceBand;
import akyrion.thepredict.domain.EncounterEvent;
import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.EventType;
import akyrion.thepredict.domain.HealthBand;
import akyrion.thepredict.domain.MobCue;
import akyrion.thepredict.domain.MobFacts;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.ParticipantAlias;
import akyrion.thepredict.domain.ParticipantFacts;
import akyrion.thepredict.domain.PurposeOrigin;
import akyrion.thepredict.domain.QuestionKey;
import akyrion.thepredict.domain.QuestionSpec;
import akyrion.thepredict.domain.Situation;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.SkillPurpose;
import akyrion.thepredict.domain.SkillRisk;
import akyrion.thepredict.domain.SkillRole;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.domain.TargetCue;
import akyrion.thepredict.domain.TargetRelation;
import akyrion.thepredict.domain.Trend;
import akyrion.thepredict.domain.UseCondition;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;

/**
 * Decision requests built with the example profile of M12.5 and M15.9 (eight tactics, with declared purposes), for
 * the size and cost measurements of M13 and the seed scenarios of the bank. Only domain records; the encoder turns them
 * into aliases.
 */
final class ScenarioFacts {
    static final TacticId APPROACH = new TacticId("approach");
    static final TacticId PRESS = new TacticId("press_and_strike");
    static final TacticId KEEP = new TacticId("keep_distance");
    static final TacticId SHOOT = new TacticId("keep_distance_and_shoot");
    static final TacticId RETREAT = new TacticId("retreat_and_defend");
    static final TacticId FLANK = new TacticId("flank_and_strike");
    static final TacticId HOLD = new TacticId("hold");
    static final TacticId SLAM = new TacticId("close_in_and_slam");
    static final List<TacticId> ALL = List.of(RETREAT, PRESS, SHOOT, APPROACH, FLANK, HOLD, KEEP, SLAM);
    private static final EncounterId ENCOUNTER = new EncounterId(1);

    private ScenarioFacts() {
    }

    /**
     * The bundled example profile, read with the loader of M03, with the four purposes the author used to declare on
     * the profile. Decision 7 moved those purposes to the {@code Intention} of each skill, so the installed example no
     * longer carries {@code Purpose}; the bank of M16.9 and the criterion texts were recorded from these declared
     * purposes and stay byte for byte without a binder.
     */
    static TacticalProfile example() {
        var profile = loadBundled();
        var choices = new LinkedHashMap<>(profile.choices());
        stamp(choices, "melee", meleePurpose());
        stamp(choices, "ranged", rangedPurpose());
        stamp(choices, "defense", defensePurpose());
        stamp(choices, "slam", slamPurpose());
        return new TacticalProfile(profile.id(), profile.mythicMobId(), profile.objective(), profile.tactics(),
                choices, profile.contexts(), profile.movement(), profile.health(), profile.threat(), profile.baseAi(),
                profile.timing());
    }

    private static TacticalProfile loadBundled() {
        try (var stream = ScenarioFacts.class.getResourceAsStream("/examples/AI/ThePredictGuardian.yml")) {
            var directory = Files.createTempDirectory("thepredict-example");
            Files.copy(Objects.requireNonNull(stream, "bundled example"), directory.resolve("ThePredictGuardian.yml"));
            var profile = new ProfileLoader().load(directory).profiles().get("ThePredictGuardian");
            Files.delete(directory.resolve("ThePredictGuardian.yml"));
            Files.delete(directory);
            return Objects.requireNonNull(profile, "example profile");
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /** Keeps a purpose the loader already read; an untyped choice receives the historical declared purpose. */
    private static void stamp(Map<ChoiceId, SkillChoice> choices, String id, SkillPurpose purpose) {
        var choiceId = new ChoiceId(id);
        var choice = Objects.requireNonNull(choices.get(choiceId), id);
        if (!choice.isTyped()) {
            choices.put(choiceId, choice.withPurpose(purpose));
        }
    }

    private static SkillPurpose meleePurpose() {
        return new SkillPurpose(SkillRole.DAMAGE, Set.of(SkillRole.DAMAGE), Delivery.MELEE, OptionalDouble.of(2.5),
                OptionalDouble.empty(), 5, CooldownClass.UNKNOWN, DamageTier.MEDIUM,
                Set.of(UseCondition.TARGET_IN_MELEE), Set.of(), false, PurposeOrigin.DECLARED);
    }

    private static SkillPurpose rangedPurpose() {
        return new SkillPurpose(SkillRole.DAMAGE, Set.of(SkillRole.DAMAGE), Delivery.HITSCAN, OptionalDouble.of(16),
                OptionalDouble.empty(), 10, CooldownClass.UNKNOWN, DamageTier.LOW,
                Set.of(UseCondition.TARGET_AT_RANGE, UseCondition.TARGET_FLEEING),
                Set.of(SkillRisk.NEEDS_LINE_OF_SIGHT), false, PurposeOrigin.DECLARED);
    }

    private static SkillPurpose defensePurpose() {
        return new SkillPurpose(SkillRole.SUSTAIN, Set.of(SkillRole.SUSTAIN), Delivery.SELF, OptionalDouble.empty(),
                OptionalDouble.empty(), 0, CooldownClass.UNKNOWN, DamageTier.NONE,
                Set.of(UseCondition.SELF_LOW_HEALTH), Set.of(), false, PurposeOrigin.DECLARED);
    }

    private static SkillPurpose slamPurpose() {
        return new SkillPurpose(SkillRole.CONTROL, Set.of(SkillRole.CONTROL, SkillRole.DAMAGE),
                Delivery.AREA_AROUND_SELF, OptionalDouble.empty(), OptionalDouble.of(3), 15, CooldownClass.UNKNOWN,
                DamageTier.MEDIUM, Set.of(UseCondition.ENEMIES_GROUPED, UseCondition.TARGET_IN_MELEE),
                Set.of(SkillRisk.ROOTS_CASTER), false, PurposeOrigin.DECLARED);
    }

    /**
     * An encounter of {@code mobs} mobs and {@code players} players with a full history of 16 events; each mob gets a
     * {@code NOW} question and one contingency with {@code options} options.
     */
    static DecisionRequest encounter(TacticalProfile profile, int mobs, int players, int options) {
        var mobFacts = new ArrayList<MobFacts>();
        var questions = new ArrayList<QuestionSpec>();
        var profiles = new HashMap<MobKey, TacticalProfile>();
        for (var index = 0; index < mobs; index++) {
            var key = new MobKey(new UUID(0xB055L, index + 1), 0);
            var target = new ParticipantAlias(index % players + 1);
            mobFacts.add(new MobFacts(key, index % 3 == 0 ? HealthBand.WOUNDED : HealthBand.HEALTHY,
                    Optional.of(new TargetRelation(target, index % 2 == 0 ? DistanceBand.MELEE : DistanceBand.RANGED,
                            Trend.values()[index % Trend.values().length], true, true, index % 4 == 0, index % 5 == 0, false)),
                    Set.of(), Set.copyOf(ALL.subList(0, options)), index % 6 == 0 ? Set.of(MobCue.CLIMBING) : Set.of(), Set.of()));
            questions.add(new QuestionSpec(QuestionKey.of(questions.size()), key, Situation.NOW, ALL.subList(0, options)));
            questions.add(new QuestionSpec(QuestionKey.of(questions.size()), key, Situation.IF_OWN_HEALTH_CRITICAL,
                    ALL.subList(0, options)));
            profiles.put(key, profile);
        }
        var participants = new ArrayList<ParticipantFacts>();
        for (var player = 1; player <= players; player++) {
            participants.add(new ParticipantFacts(new ParticipantAlias(player), HealthBand.values()[player % 3],
                    ArmorBand.values()[player % 3], player % 2 == 0 ? Set.of(TargetCue.DRAWING_BOW)
                    : Set.of(TargetCue.RAISING_SHIELD, TargetCue.ATTACK_RECHARGING), player));
        }
        var events = new ArrayList<EncounterEvent>();
        for (var event = 0; event < 16; event++) {
            events.add(new EncounterEvent(1000 + event * 5L, EventType.values()[event % EventType.values().length],
                    Optional.of(new ParticipantAlias(event % players + 1)),
                    Optional.of(mobFacts.get(event % mobs).mob())));
        }
        var facts = new CombatFacts(ENCOUNTER, 1100, 55_000_000_000L, mobFacts, participants, events, 45);
        return new DecisionRequest(ENCOUNTER, 1, facts, questions, profiles);
    }

    /**
     * The production request of M16.2: one mob and one participant, {@code NOW} and one contingency, the seven options
     * the example offers against a target at range that cannot be flanked, and a state of about 650 characters (two
     * recent events).
     */
    static DecisionRequest production(TacticalProfile profile) {
        var key = new MobKey(new UUID(0x9D0DL, 1), 0);
        var target = new ParticipantAlias(1);
        var options = List.of(RETREAT, PRESS, SLAM, SHOOT, APPROACH, KEEP, HOLD);
        var mob = new MobFacts(key, HealthBand.HEALTHY, Optional.of(new TargetRelation(target, DistanceBand.RANGED,
                Trend.CLOSING, true, true, false, false, false)), Set.of(), Set.copyOf(options), Set.of(), Set.of());
        var participant = new ParticipantFacts(target, HealthBand.WOUNDED, ArmorBand.MEDIUM,
                Set.of(TargetCue.DRAWING_BOW), 1);
        var events = List.of(new EncounterEvent(980, EventType.RANGED_HIT_ON_MOB, Optional.of(target), Optional.of(key)),
                new EncounterEvent(990, EventType.MELEE_HIT, Optional.of(target), Optional.of(key)));
        var facts = new CombatFacts(ENCOUNTER, 1000, 50_000_000_000L, List.of(mob), List.of(participant), events, 20);
        return new DecisionRequest(ENCOUNTER, 1, facts, List.of(new QuestionSpec(QuestionKey.of(0), key, Situation.NOW,
                options), new QuestionSpec(QuestionKey.of(1), key, Situation.IF_TARGET_IN_MELEE, options)),
                Map.of(key, profile));
    }

    /** One seed of the scenario bank: one mob against one or two players in a situation of M13.5. */
    static DecisionRequest seed(Seed seed, TacticalProfile profile) {
        var key = new MobKey(new UUID(0x5EEDL, seed.ordinal() + 1), 0);
        var target = new ParticipantAlias(1);
        var mob = new MobFacts(key, seed.mobHealth(), Optional.of(new TargetRelation(target, seed.distance(),
                seed.trend(), seed.reachable(), true, false, seed.flankable(), false)),
                seed.mobHealth() == HealthBand.CRITICAL ? Set.of(new ContextId("low_health_phase")) : Set.of(),
                Set.copyOf(seed.options()), Set.of(), Set.of());
        var participants = new ArrayList<ParticipantFacts>();
        participants.add(new ParticipantFacts(target, HealthBand.HEALTHY, ArmorBand.MEDIUM, seed.cues(), 1));
        if (seed == Seed.TANK_AND_SHOOTER) {
            participants.add(new ParticipantFacts(new ParticipantAlias(2), HealthBand.HEALTHY, ArmorBand.LIGHT,
                    Set.of(TargetCue.DRAWING_BOW), 2));
        }
        var events = seed == Seed.TANK_AND_SHOOTER
                ? List.of(new EncounterEvent(980, EventType.RANGED_HIT_ON_MOB, Optional.of(new ParticipantAlias(2)),
                Optional.of(key))) : List.<EncounterEvent>of();
        var facts = new CombatFacts(ENCOUNTER, 1000, 50_000_000_000L, List.of(mob), participants, events, 20);
        return new DecisionRequest(ENCOUNTER, 1, facts, List.of(new QuestionSpec(QuestionKey.of(0), key,
                Situation.NOW, seed.options())), Map.of(key, profile));
    }

    /**
     * Seeds of the bank for the scenarios of M13.5, with the tactics a person would accept as a proposal; the user
     * reviews these labels before they calibrate anything. Since M15.9 every seed whose target is approachable also
     * offers {@code close_in_and_slam}, eligible wherever {@code approach} is; the labels are unchanged. M16.9 retired
     * {@code LOW_HEALTH}, whose two options were both acceptable, for the family "low health of the mob" of the labelled
     * bank ({@code scenarios/bank/}); the paid run of 2026-09-29 keeps it in {@code scenarios/seed-2026-09-29/}.
     */
    enum Seed {
        MELEE_DUEL(DistanceBand.MELEE, Trend.STEADY, true, false, HealthBand.HEALTHY, Set.of(),
                List.of(PRESS, SLAM, SHOOT, APPROACH, HOLD, KEEP), List.of(PRESS), "hold"),
        SHOOTER_BACKING_AWAY(DistanceBand.RANGED, Trend.BACKING_AWAY, true, false, HealthBand.HEALTHY,
                Set.of(TargetCue.DRAWING_BOW), List.of(PRESS, SLAM, SHOOT, APPROACH, HOLD, KEEP), List.of(APPROACH, PRESS),
                "hold"),
        SHIELD(DistanceBand.MELEE, Trend.STEADY, true, true, HealthBand.HEALTHY, Set.of(TargetCue.BLOCKING),
                List.of(PRESS, SLAM, SHOOT, APPROACH, FLANK, HOLD, KEEP), List.of(FLANK, SHOOT, KEEP), "press_and_strike"),
        TANK_AND_SHOOTER(DistanceBand.MELEE, Trend.STEADY, true, false, HealthBand.HEALTHY,
                Set.of(TargetCue.ATTACK_RECHARGING), List.of(PRESS, SLAM, SHOOT, APPROACH, HOLD, KEEP), List.of(PRESS), ""),
        PILLAR(DistanceBand.RANGED, Trend.STEADY, false, false, HealthBand.HEALTHY, Set.of(),
                List.of(PRESS, SLAM, SHOOT, APPROACH, HOLD, KEEP), List.of(SHOOT, KEEP, HOLD), "approach");

        private final DistanceBand distance;
        private final Trend trend;
        private final boolean reachable;
        private final boolean flankable;
        private final HealthBand mobHealth;
        private final Set<TargetCue> cues;
        private final List<TacticId> options;
        private final List<TacticId> acceptable;
        private final String forbidden;

        Seed(DistanceBand distance, Trend trend, boolean reachable, boolean flankable, HealthBand mobHealth,
             Set<TargetCue> cues, List<TacticId> options, List<TacticId> acceptable, String forbidden) {
            this.distance = distance;
            this.trend = trend;
            this.reachable = reachable;
            this.flankable = flankable;
            this.mobHealth = mobHealth;
            this.cues = cues;
            this.options = options;
            this.acceptable = acceptable;
            this.forbidden = forbidden;
        }

        DistanceBand distance() {
            return distance;
        }

        Trend trend() {
            return trend;
        }

        boolean reachable() {
            return reachable;
        }

        boolean flankable() {
            return flankable;
        }

        HealthBand mobHealth() {
            return mobHealth;
        }

        Set<TargetCue> cues() {
            return cues;
        }

        List<TacticId> options() {
            return options;
        }

        List<TacticId> acceptable() {
            return acceptable;
        }

        String forbidden() {
            return forbidden;
        }
    }
}
