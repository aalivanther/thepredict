package akyrion.thepredict.adapter.jev;

import akyrion.thepredict.domain.ArmorBand;
import akyrion.thepredict.domain.ChoiceId;
import akyrion.thepredict.domain.ChoiceKind;
import akyrion.thepredict.domain.CombatFacts;
import akyrion.thepredict.domain.CooldownClass;
import akyrion.thepredict.domain.DamageTier;
import akyrion.thepredict.domain.DecisionRequest;
import akyrion.thepredict.domain.DecisionTiming;
import akyrion.thepredict.domain.Delivery;
import akyrion.thepredict.domain.DistanceBand;
import akyrion.thepredict.domain.EncounterEvent;
import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.EventType;
import akyrion.thepredict.domain.HealthBand;
import akyrion.thepredict.domain.HealthThresholds;
import akyrion.thepredict.domain.MobCue;
import akyrion.thepredict.domain.MobFacts;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.MovementSettings;
import akyrion.thepredict.domain.ParticipantAlias;
import akyrion.thepredict.domain.ParticipantFacts;
import akyrion.thepredict.domain.PredictValue;
import akyrion.thepredict.domain.PurposeOrigin;
import akyrion.thepredict.domain.QuestionDetail;
import akyrion.thepredict.domain.QuestionKey;
import akyrion.thepredict.domain.QuestionSpec;
import akyrion.thepredict.domain.Situation;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.SkillPurpose;
import akyrion.thepredict.domain.SkillRisk;
import akyrion.thepredict.domain.SkillRole;
import akyrion.thepredict.domain.Tactic;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.domain.TargetCue;
import akyrion.thepredict.domain.TargetRelation;
import akyrion.thepredict.domain.ThreatWeights;
import akyrion.thepredict.domain.UseCondition;
import akyrion.thepredict.domain.Trend;
import akyrion.thepredict.domain.UseCondition;
import akyrion.thepredict.domain.DecisionResult;
import akyrion.thepredict.domain.FailureKind;
import akyrion.thepredict.engine.DecisionClient;
import akyrion.thepredict.engine.Preparation;
import akyrion.thepredict.engine.ServerClock;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Shared test data of the System One adapter. Keys are obviously fake. */
final class JevFixtures {
    static final String KEY = "test-key-7f3a9c";
    static final EncounterId ENCOUNTER = new EncounterId(7);
    static final MobKey MOB_A = new MobKey(UUID.fromString("00000000-0000-0000-0000-0000000000a1"), 3);
    static final MobKey MOB_B = new MobKey(UUID.fromString("00000000-0000-0000-0000-0000000000b2"), 1);
    static final MobKey MOB_C = new MobKey(UUID.fromString("00000000-0000-0000-0000-0000000000c3"), 0);
    static final ParticipantAlias PLAYER_1 = new ParticipantAlias(1);
    static final ParticipantAlias PLAYER_2 = new ParticipantAlias(2);
    static final ParticipantAlias PLAYER_3 = new ParticipantAlias(3);
    static final ServerClock CLOCK = ServerClock.of(() -> 100, () -> 5_000_000_000L);

    private JevFixtures() {
    }

    static TacticId tactic(String value) {
        return new TacticId(value);
    }

    static TacticalProfile guardian() {
        var tactics = new LinkedHashMap<TacticId, Tactic>();
        put(tactics, new Tactic(tactic("press_attack"), Movement.APPROACH, Optional.of(new ChoiceId("slash")),
                Optional.empty(), 20));
        put(tactics, new Tactic(tactic("keep_range"), Movement.KEEP_DISTANCE, Optional.of(new ChoiceId("volley")),
                Optional.of("Shoot while the target cannot reach you"), 20));
        put(tactics, new Tactic(tactic("defend_self"), Movement.RETREAT, Optional.of(new ChoiceId("guard")),
                Optional.empty(), 40));
        put(tactics, new Tactic(tactic("hold_ground"), Movement.HOLD, Optional.empty(), Optional.empty(), 10));
        return profile(tactics, "Protect the altar and defeat intruders");
    }

    static TacticalProfile profile(Map<TacticId, Tactic> tactics, String objective) {
        var choices = new LinkedHashMap<ChoiceId, SkillChoice>();
        choices.put(new ChoiceId("slash"), new SkillChoice(new ChoiceId("slash"), ChoiceKind.VANILLA_MELEE,
                Optional.empty(), Set.of(PredictValue.SINGLE_TARGET, PredictValue.MELEE_ATTACK), true, 0));
        choices.put(new ChoiceId("volley"), new SkillChoice(new ChoiceId("volley"), ChoiceKind.MYTHIC_SKILL,
                Optional.of("Guardian_Volley"), Set.of(PredictValue.MEDIUM_DAMAGE, PredictValue.RANGED_ATTACK,
                PredictValue.SINGLE_TARGET), true, 0, recordedVolley()));
        choices.put(new ChoiceId("guard"), new SkillChoice(new ChoiceId("guard"), ChoiceKind.MYTHIC_SKILL,
                Optional.of("Guardian_Guard"), Set.of(PredictValue.HEALS_CASTER, PredictValue.DEFENSE), false, 0,
                recordedGuard()));
        return new TacticalProfile("guardian_ai", "Guardian", objective, tactics, choices, List.of(),
                new MovementSettings(2.5, 5, 9, 16, 10, 1.2, 1.15, OptionalDouble.empty()),
                new HealthThresholds(0.3, 0.6), new ThreatWeights(1, 1, 1, 0.5, 10), List.of(),
                new DecisionTiming(40, 20, 100, 0.5, 0.2, 2));
    }

    /**
     * The ranged purpose the retired inference attached to {@code RANGED_ATTACK} plus {@code MEDIUM_DAMAGE}. Stated
     * here so the recorded request stays byte for byte; the binder leaves those labels untyped (decision 5).
     */
    private static SkillPurpose recordedVolley() {
        return new SkillPurpose(SkillRole.DAMAGE, Set.of(SkillRole.DAMAGE), Delivery.PROJECTILE, OptionalDouble.empty(),
                OptionalDouble.empty(), 0, CooldownClass.UNKNOWN, DamageTier.MEDIUM,
                Set.of(UseCondition.TARGET_AT_RANGE), Set.of(), false, PurposeOrigin.INFERRED);
    }

    /**
     * The self purpose the retired inference attached to {@code HEALS_CASTER} plus {@code DEFENSE}.
     */
    private static SkillPurpose recordedGuard() {
        return new SkillPurpose(SkillRole.SUSTAIN, Set.of(SkillRole.SUSTAIN, SkillRole.DEFENSE), Delivery.SELF,
                OptionalDouble.empty(), OptionalDouble.empty(), 0, CooldownClass.UNKNOWN, DamageTier.NONE,
                Set.of(UseCondition.SELF_LOW_HEALTH), Set.of(), false, PurposeOrigin.INFERRED);
    }

    /**
     * The choice with the longest criterion M15.7 allows: {@code AREA_AT_TARGET} with reach and area, the three longest
     * roles, a long wind-up, every skill piece, the four longest conditions and the four longest risks.
     */
    static SkillChoice worstCaseChoice() {
        var purpose = new SkillPurpose(SkillRole.ESCAPE, Set.of(SkillRole.ESCAPE, SkillRole.ZONE, SkillRole.INTERRUPT),
                Delivery.AREA_AT_TARGET, OptionalDouble.of(9), OptionalDouble.of(6), 100, CooldownClass.MEDIUM,
                DamageTier.MEDIUM, Set.of(UseCondition.TARGET_SHIELDING, UseCondition.ENEMIES_GROUPED,
                UseCondition.TARGET_AT_RANGE, UseCondition.TARGET_IN_MELEE), Set.of(SkillRisk.LEAVES_CASTER_EXPOSED,
                SkillRisk.ROOTS_CASTER, SkillRisk.SLOW_PROJECTILE, SkillRisk.NEEDS_LINE_OF_SIGHT), true,
                PurposeOrigin.DECLARED);
        return new SkillChoice(new ChoiceId("worst"), ChoiceKind.MYTHIC_SKILL, Optional.of("Worst"), Set.of(), true, 0,
                purpose);
    }

    /** The same profile with another question detail and fit checks (M16.2, M16.5). */
    static TacticalProfile detailed(TacticalProfile profile, QuestionDetail detail, boolean fitChecks) {
        var timing = profile.timing();
        return new TacticalProfile(profile.id(), profile.mythicMobId(), profile.objective(), profile.tactics(),
                profile.choices(), profile.contexts(), profile.movement(), profile.health(), profile.threat(),
                profile.baseAi(), new DecisionTiming(timing.heartbeatTicks(), timing.minimumDecisionIntervalTicks(),
                timing.maximumIntentTicks(), timing.minimumConfidence(), timing.switchMargin(),
                timing.contingenciesPerMob(), detail, fitChecks));
    }

    /** The same profile with another question detail and without fit checks. */
    static TacticalProfile detailed(TacticalProfile profile, QuestionDetail detail) {
        return detailed(profile, detail, false);
    }

    /**
     * The guardian profile with {@code count} extra {@code FLANK} tactics on {@link #worstCaseChoice()}, each with a
     * distinct description of 200 characters: the longest options a profile can offer.
     */
    static TacticalProfile worstCaseProfile(int count) {
        var base = guardian();
        var tactics = new LinkedHashMap<>(base.tactics());
        var choice = worstCaseChoice();
        for (var index = 0; index < count; index++) {
            var id = tactic(String.format("worst_case_%03d", index));
            tactics.put(id, new Tactic(id, Movement.FLANK, Optional.of(choice.id()),
                    Optional.of(String.format("%03d", index) + "x".repeat(197)), 50));
        }
        var choices = new LinkedHashMap<>(base.choices());
        choices.put(choice.id(), choice);
        return new TacticalProfile(base.id(), base.mythicMobId(), base.objective(), tactics, choices, base.contexts(),
                base.movement(), base.health(), base.threat(), base.baseAi(), base.timing());
    }

    static MobFacts meleeMob(MobKey mob, ParticipantAlias target) {
        return new MobFacts(mob, HealthBand.HEALTHY, Optional.of(new TargetRelation(target, DistanceBand.MELEE,
                Trend.CLOSING, true, true, false, false, false)), Set.of(), Set.of(), Set.of(), Set.of());
    }

    static ParticipantFacts player(ParticipantAlias alias, int rank, TargetCue... cues) {
        return new ParticipantFacts(alias, HealthBand.WOUNDED, ArmorBand.MEDIUM, Set.of(cues), rank);
    }

    static EncounterEvent event(long tick, EventType type, ParticipantAlias player, MobKey mob) {
        return new EncounterEvent(tick, type, Optional.of(player), Optional.of(mob));
    }

    static CombatFacts meleeFacts() {
        return new CombatFacts(ENCOUNTER, 1200, 9_000_000L, List.of(meleeMob(MOB_A, PLAYER_1)),
                List.of(player(PLAYER_1, 1, TargetCue.ATTACK_RECHARGING)),
                List.of(event(1100, EventType.MELEE_HIT, PLAYER_1, MOB_A),
                        event(1180, EventType.RANGED_MISS, PLAYER_1, MOB_A)), 12);
    }

    static CombatFacts shieldFacts() {
        var mob = new MobFacts(MOB_A, HealthBand.WOUNDED, Optional.of(new TargetRelation(PLAYER_1, DistanceBand.RANGED,
                Trend.BACKING_AWAY, false, false, true, true, false)), Set.of(), Set.of(),
                Set.of(MobCue.NO_LINE_OF_SIGHT, MobCue.IN_WATER), Set.of());
        return new CombatFacts(ENCOUNTER, 2400, 18_000_000L, List.of(mob),
                List.of(new ParticipantFacts(PLAYER_1, HealthBand.CRITICAL, ArmorBand.HEAVY,
                        Set.of(TargetCue.SHIELD_DISABLED, TargetCue.RAISING_SHIELD, TargetCue.HOLDS_TOTEM), 1)),
                List.of(event(2300, EventType.MELEE_BLOCKED, PLAYER_1, MOB_A),
                        new EncounterEvent(2350, EventType.SHIELD_DISABLED, Optional.of(PLAYER_1), Optional.empty())),
                75);
    }

    static CombatFacts threeMobFacts() {
        var mobC = new MobFacts(MOB_C, HealthBand.CRITICAL, Optional.empty(), Set.of(), Set.of(), Set.of(MobCue.CLIMBING), Set.of());
        return new CombatFacts(ENCOUNTER, 3000, 27_000_000L,
                List.of(meleeMob(MOB_A, PLAYER_1), meleeMob(MOB_B, PLAYER_2), mobC),
                List.of(player(PLAYER_1, 2, TargetCue.DRAWING_BOW), player(PLAYER_2, 1, TargetCue.GLIDING)),
                List.of(event(2900, EventType.RANGED_HIT_ON_MOB, PLAYER_2, MOB_B),
                        event(2950, EventType.PEARL_THROWN, PLAYER_3, MOB_C)), 200);
    }

    static CombatFacts crowdedFacts() {
        var mobB = new MobFacts(MOB_B, HealthBand.HEALTHY, Optional.of(new TargetRelation(PLAYER_2, DistanceBand.RANGED,
                Trend.STRAFING, true, true, true, false, false)), Set.of(), Set.of(), Set.of(MobCue.IN_LAVA), Set.of());
        var events = new ArrayList<EncounterEvent>();
        for (var index = 0; index < 6; index++) {
            events.add(event(1000 + index * 10L, index % 2 == 0 ? EventType.MELEE_HIT : EventType.POTION_CONSUMED,
                    index % 2 == 0 ? PLAYER_1 : PLAYER_3, index % 2 == 0 ? MOB_A : MOB_B));
        }
        return new CombatFacts(ENCOUNTER, 1100, 11_000_000L, List.of(meleeMob(MOB_A, PLAYER_1), mobB),
                List.of(player(PLAYER_1, 1, TargetCue.BLOCKING), player(PLAYER_2, 2, TargetCue.CROSSBOW_LOADED),
                        player(PLAYER_3, 3, TargetCue.CONSUMING, TargetCue.INVISIBLE)), events, 3);
    }

    static QuestionSpec question(int index, MobKey mob, Situation situation, String... options) {
        return new QuestionSpec(QuestionKey.of(index), mob, situation,
                Arrays.stream(options).map(TacticId::new).toList());
    }

    static DecisionRequest request() {
        return new DecisionRequest(ENCOUNTER, 4, meleeFacts(), List.of(
                question(0, MOB_A, Situation.NOW, "press_attack", "keep_range", "hold_ground"),
                question(1, MOB_A, Situation.IF_OWN_HEALTH_CRITICAL, "defend_self", "hold_ground")),
                Map.of(MOB_A, guardian()));
    }

    /**
     * Prepares a request with a client and sends what it prepared, as the dispatcher does (M16.1): an invalid
     * preparation answers the {@code INVALID_REQUEST} failure an encoding failure gave before M16, and nothing is sent.
     */
    static CompletableFuture<DecisionResult> send(DecisionClient client, DecisionRequest request) {
        return switch (client.prepare(request)) {
            case Preparation.Ready ready -> client.decide(ready.prepared());
            case Preparation.Invalid invalid -> CompletableFuture.completedFuture(new DecisionResult.Failed(
                    FailureKind.INVALID_REQUEST, Optional.empty(), invalid.detail()));
            case Preparation.NothingToAsk ignored -> throw new AssertionError("nothing to ask in " + request);
        };
    }

    /**
     * Writes a fresh rendering of a fixture of {@code src/test/resources/systemone} to {@code build/systemone-fresh/}
     * when it differs, so a deliberate encoder change can be reviewed and copied; never touches the fixture itself.
     */
    static void keepFresh(String name, String fresh) {
        try {
            if (!fresh.equals(SystemOneContractServer.fixture(name))) {
                var file = Path.of("build", "systemone-fresh", name);
                Files.createDirectories(file.getParent());
                Files.writeString(file, fresh);
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    static SystemOneSettings settings(URI baseUrl) {
        return settings(baseUrl, Duration.ofSeconds(5));
    }

    static SystemOneSettings settings(URI baseUrl, Duration requestTimeout) {
        return new SystemOneSettings(baseUrl, "jev-1.13.0", KEY, Duration.ofSeconds(2), requestTimeout, 262_144,
                12_000, "0.0.1");
    }

    private static void put(Map<TacticId, Tactic> tactics, Tactic tactic) {
        tactics.put(tactic.id(), tactic);
    }
}
