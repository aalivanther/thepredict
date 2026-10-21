package akyrion.thepredict.adapter.server.execution;

import akyrion.thepredict.adapter.server.context.Blocking;
import akyrion.thepredict.config.ProfileLoader;
import akyrion.thepredict.domain.ChoiceId;
import akyrion.thepredict.domain.ChoiceKind;
import akyrion.thepredict.domain.CooldownClass;
import akyrion.thepredict.domain.DamageTier;
import akyrion.thepredict.domain.Delivery;
import akyrion.thepredict.domain.ExecutionOutcome;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.MovementSettings;
import akyrion.thepredict.domain.NativeSkillFacts;
import akyrion.thepredict.domain.PredictValue;
import akyrion.thepredict.domain.PurposeOrigin;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.SkillPurpose;
import akyrion.thepredict.domain.SkillRisk;
import akyrion.thepredict.domain.SkillRole;
import akyrion.thepredict.domain.TacticId;
import io.lumine.mythic.api.skills.Skill;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** M10.3, M10.8, M10.9 and M15.6 with doubles of Paper and MythicMobs (rule P4). */
class CombatActionsAdapterTest {
    private static final MobKey MOB = new MobKey(new UUID(0x30BL, 1), 0);
    private static final UUID PLAYER = new UUID(0x9L, 1);
    private static final UUID ZOMBIE = new UUID(0x2AL, 1);

    private final World world = ExecutionDoubles.world("world");
    private final ExecutionDoubles.Source source = new ExecutionDoubles.Source();
    private final ExecutionDoubles.Caster caster = new ExecutionDoubles.Caster(world, 0, 2);
    private final ExecutionDoubles.Target target = new ExecutionDoubles.Target(world);
    private final ExecutionDoubles.EntityTarget zombie = new ExecutionDoubles.EntityTarget(world);
    private Predicate<LivingEntity> blocking = Blocking::blocking;
    private final Skill strike = ExecutionDoubles.skill("TPV_Strike");
    private Optional<Skill> loaded = Optional.of(strike);
    private OptionalDouble shieldAngle = OptionalDouble.empty();
    private Optional<MovementSettings> movement = Optional.empty();
    private boolean serverThread = true;

    private CombatActionsAdapter adapter() {
        Function<LivingEntity, OptionalDouble> angle = entity -> shieldAngle;
        return new CombatActionsAdapter(source, id -> id.equals("TPV_Strike") ? loaded : Optional.empty(), angle,
                entity -> blocking.test(entity), id -> movement,
                id -> id.equals(PLAYER) ? Optional.<LivingEntity>of(target.player)
                        : id.equals(ZOMBIE) ? Optional.of(zombie.entity) : Optional.empty(),
                () -> serverThread);
    }

    private static SkillChoice skill(boolean requiresTarget, PredictValue... values) {
        return new SkillChoice(new ChoiceId("strike"), ChoiceKind.MYTHIC_SKILL, Optional.of("TPV_Strike"),
                Set.of(values), requiresTarget, 0);
    }

    private static SkillChoice strike(PredictValue... extra) {
        var values = new HashSet<>(Set.of(extra));
        values.add(PredictValue.MELEE_ATTACK);
        return skill(true, values.toArray(PredictValue[]::new));
    }

    private static SkillChoice melee() {
        return new SkillChoice(new ChoiceId("swing"), ChoiceKind.VANILLA_MELEE, Optional.empty(),
                Set.of(PredictValue.MELEE_ATTACK), true, 0);
    }

    private ExecutionOutcome cast(SkillChoice choice, Movement intentMovement) {
        return adapter().cast(MOB, choice, intentMovement);
    }

    private void engaged() {
        source.caster = Optional.of(caster.entity);
        source.target = Optional.of(target.player);
    }

    @Test
    void aSkillIsCastOnceAtTheTargetWhenEveryCheckPasses() {
        engaged();
        assertEquals(ExecutionOutcome.CAST, cast(strike(), Movement.APPROACH));
        assertEquals(List.of("conditionsAt TPV_Strike target", "executeAt TPV_Strike target"), source.calls);
    }

    @Test
    void aMissingCasterOrRequiredTargetIsNotEligibleAndASkillWithoutTargetRunsAlone() {
        assertEquals(ExecutionOutcome.NOT_ELIGIBLE, cast(skill(false), Movement.APPROACH));
        source.caster = Optional.of(caster.entity);
        assertEquals(ExecutionOutcome.NOT_ELIGIBLE, cast(strike(), Movement.APPROACH));
        assertEquals(ExecutionOutcome.CAST, cast(skill(false), Movement.HOLD));
        assertEquals(List.of("conditions TPV_Strike", "execute TPV_Strike"), source.calls);
    }

    @Test
    void theNativeCooldownIsNotEligibleWithoutAskingMythicMobsToExecute() {
        engaged();
        source.onCooldown = true;
        assertEquals(ExecutionOutcome.NOT_ELIGIBLE, cast(strike(), Movement.APPROACH));
        assertTrue(source.calls.isEmpty());
    }

    @Test
    void readinessReadsOnlyTheNativeCooldownWithoutCastingAnything() {
        engaged();
        var actions = adapter();
        assertTrue(actions.ready(MOB, strike()));
        source.onCooldown = true;
        assertFalse(actions.ready(MOB, strike()));
        assertTrue(actions.ready(MOB, melee()), "a vanilla melee choice has no native cooldown");
        source.onCooldown = false;
        loaded = Optional.empty();
        assertFalse(adapter().ready(MOB, strike()), "a skill that is no longer loaded is never ready");
        assertTrue(source.calls.isEmpty(), "nothing is executed, checked or signalled");
        serverThread = false;
        assertThrows(IllegalStateException.class, () -> adapter().ready(MOB, strike()));
    }

    @Test
    void aRefusedOrUnloadedSkillIsRejectedByMythicMobs() {
        engaged();
        source.executes = false;
        assertEquals(ExecutionOutcome.REJECTED_BY_MYTHICMOBS, cast(strike(), Movement.APPROACH));
        loaded = Optional.empty();
        assertEquals(ExecutionOutcome.REJECTED_BY_MYTHICMOBS, cast(strike(), Movement.APPROACH));
        assertEquals(1, source.calls.stream().filter(call -> call.startsWith("execute")).count());
    }

    @Test
    void theInvulnerabilityWindowDefersTheHitAtTheExactVanillaBoundary() {
        engaged();
        target.noDamageTicks = 11;
        assertEquals(ExecutionOutcome.DEFERRED_INVULNERABLE, cast(strike(), Movement.APPROACH));
        target.noDamageTicks = 10;
        assertEquals(ExecutionOutcome.CAST, cast(strike(), Movement.APPROACH));
        target.maximumNoDamageTicks = 21;
        target.noDamageTicks = 11;
        assertEquals(ExecutionOutcome.DEFERRED_INVULNERABLE, cast(strike(), Movement.APPROACH));
        target.noDamageTicks = 10;
        assertEquals(ExecutionOutcome.CAST, cast(strike(), Movement.APPROACH));
    }

    @Test
    void aBlockingShieldSkipsTheChoiceUnlessItIgnoresShields() {
        engaged();
        target.blocking = true;
        assertEquals(ExecutionOutcome.SKIPPED_SHIELD, cast(strike(), Movement.APPROACH));
        assertEquals(ExecutionOutcome.SKIPPED_SHIELD, cast(strike(PredictValue.IGNORES_SHIELD), Movement.APPROACH),
                "PredictValues IGNORES_SHIELD no longer skips the shield (E19-1 DP5)");
        target.blocking = false;
        shieldAngle = OptionalDouble.of(90);
        assertEquals(ExecutionOutcome.CAST, cast(strike(), Movement.APPROACH), "a raised shield that does not block yet");
    }

    @Test
    void aFlankCastWaitsInsideTheBlockingAngleAndLandsOutsideIt() {
        engaged();
        target.blocking = true;
        shieldAngle = OptionalDouble.of(90);
        assertEquals(ExecutionOutcome.NOT_ELIGIBLE, cast(strike(), Movement.FLANK));
        caster.z = -2;
        assertEquals(ExecutionOutcome.CAST, cast(strike(), Movement.FLANK));
        assertEquals(1, source.calls.stream().filter(call -> call.startsWith("execute")).count());
    }

    @Test
    void theFlankAngleIsTheOneReadFromTheShieldComponent() {
        engaged();
        target.blocking = true;
        caster.x = 2;
        caster.z = 0.54;
        shieldAngle = OptionalDouble.of(90);
        assertEquals(ExecutionOutcome.NOT_ELIGIBLE, cast(strike(), Movement.FLANK));
        shieldAngle = OptionalDouble.of(60);
        assertEquals(ExecutionOutcome.CAST, cast(strike(), Movement.FLANK));
    }

    @Test
    void aFlankAgainstATargetThatStoppedBlockingUsesTheNormalChecks() {
        engaged();
        assertEquals(ExecutionOutcome.CAST, cast(strike(), Movement.FLANK));
        target.noDamageTicks = 15;
        assertEquals(ExecutionOutcome.DEFERRED_INVULNERABLE, cast(strike(), Movement.FLANK));
    }

    @Test
    void aVanillaMeleeChoiceAttacksOnlyWithinReachAndFollowsTheSameRules() {
        engaged();
        caster.z = 0;
        caster.x = 2.5;
        assertEquals(ExecutionOutcome.CAST, cast(melee(), Movement.APPROACH));
        assertEquals(List.of("attack", "swing"), caster.calls);
        caster.x = 3.2;
        assertEquals(ExecutionOutcome.NOT_ELIGIBLE, cast(melee(), Movement.APPROACH));
        movement = Optional.of(new MovementSettings(4.0, 5, 9, 16, 10, 1.2, 1.15, OptionalDouble.empty()));
        assertEquals(ExecutionOutcome.CAST, cast(melee(), Movement.APPROACH), "the reach of the profile");
        target.noDamageTicks = 15;
        assertEquals(ExecutionOutcome.DEFERRED_INVULNERABLE, cast(melee(), Movement.APPROACH));
        target.noDamageTicks = 0;
        target.blocking = true;
        assertEquals(ExecutionOutcome.SKIPPED_SHIELD, cast(melee(), Movement.APPROACH));
        assertEquals(4, caster.calls.size());
        assertTrue(source.calls.isEmpty(), "a vanilla hit never runs a skill");
    }

    @Test
    void failingSkillConditionsWaitWithoutConsumingTheChoiceAndAreCheckedWithoutExecuting() {
        engaged();
        source.conditionsHold = false;
        assertEquals(ExecutionOutcome.NOT_ELIGIBLE, cast(strike(), Movement.APPROACH));
        assertEquals(List.of("conditionsAt TPV_Strike target"), source.calls);
        source.conditionsHold = true;
        assertEquals(ExecutionOutcome.CAST, cast(strike(), Movement.APPROACH));
    }

    @Test
    void conditionsAreOnlyEvaluatedOnceEveryOtherCheckPasses() {
        engaged();
        target.noDamageTicks = 15;
        assertEquals(ExecutionOutcome.DEFERRED_INVULNERABLE, cast(strike(), Movement.APPROACH));
        source.onCooldown = true;
        assertEquals(ExecutionOutcome.NOT_ELIGIBLE, cast(strike(), Movement.APPROACH));
        assertTrue(source.calls.isEmpty());
    }

    @Test
    void aMeleeSkillWaitsUntilItsTargetIsInReachWhileARangedSkillDoesNot() {
        engaged();
        caster.z = 6;
        assertEquals(ExecutionOutcome.CAST, cast(strike(), Movement.APPROACH),
                "an untyped skill has no reach (M19, decision 5)");
        assertEquals(ExecutionOutcome.CAST, cast(skill(true, PredictValue.RANGED_ATTACK), Movement.KEEP_DISTANCE));
        caster.z = 2.5;
        assertEquals(ExecutionOutcome.CAST, cast(strike(), Movement.APPROACH));
    }

    @Test
    void aSkillThatDoesNotHitTheTargetIgnoresShieldsAndInvulnerability() {
        engaged();
        target.blocking = true;
        target.noDamageTicks = 15;
        var defense = skill(false, PredictValue.DEFENSE, PredictValue.HEALS_CASTER);
        assertEquals(ExecutionOutcome.CAST, cast(defense, Movement.RETREAT));
        assertEquals(ExecutionOutcome.DEFERRED_INVULNERABLE, cast(skill(true, PredictValue.SUMMONS_ALLY),
                Movement.HOLD), "an untyped choice that needs a target counts as damaging (decision 5)");
        assertEquals(ExecutionOutcome.DEFERRED_INVULNERABLE, cast(skill(true, PredictValue.HIGH_DAMAGE), Movement.HOLD));
        target.noDamageTicks = 0;
        assertEquals(ExecutionOutcome.SKIPPED_SHIELD, cast(skill(true, PredictValue.HIGH_DAMAGE), Movement.HOLD),
                "a damaging choice aimed at the target still respects the shield");
    }

    @Test
    void theDamageAndReachRulesFollowTheDeclaredValues() {
        assertTrue(HitChecks.damaging(melee()));
        assertTrue(HitChecks.damaging(skill(true, PredictValue.LOW_DAMAGE)));
        assertTrue(HitChecks.damaging(skill(true, PredictValue.APPLIES_POISON)),
                "an untyped choice that needs a target counts as damaging (decision 5)");
        assertFalse(HitChecks.damaging(skill(false, PredictValue.HIGH_DAMAGE)));
    }

    @Test
    void aCastWithATellInterruptHandsItsFlagToMythicMobsAndKeepsTheTargetChecks() {
        engaged();
        var interrupt = new akyrion.thepredict.engine.CastInterrupt();
        assertEquals(ExecutionOutcome.CAST, adapter().cast(MOB, strike(), Movement.APPROACH, interrupt));
        assertEquals(List.of("conditionsAt TPV_Strike target", "executeAt TPV_Strike target flagged"), source.calls);
        assertEquals(List.of(interrupt.flag()), source.flags);
        source.calls.clear();
        assertEquals(ExecutionOutcome.NOT_ELIGIBLE,
                adapter().cast(MOB, strike(), Movement.APPROACH, new UUID(0x9L, 2), interrupt));
        assertTrue(source.calls.isEmpty());
        var aimed = strike().withNativeFacts(within(16));
        source.aiTarget = Optional.of(ZOMBIE);
        assertEquals(ExecutionOutcome.NOT_ELIGIBLE, adapter().cast(MOB, aimed, Movement.APPROACH, PLAYER, interrupt));
        source.aiTarget = Optional.of(PLAYER);
        assertEquals(ExecutionOutcome.CAST, adapter().cast(MOB, aimed, Movement.APPROACH, PLAYER, interrupt));
        assertEquals(2, source.flags.size());
        assertEquals(ExecutionOutcome.CAST, cast(strike(), Movement.APPROACH));
        assertEquals(2, source.flags.size(), "a cast without a tell stores no flag");
    }

    @Test
    void theLineOfSightIsReadOnceForAMobInATickAcrossSeesTargetAndTheCast() {
        engaged();
        var bolt = declared(Delivery.HITSCAN, OptionalDouble.of(16), OptionalDouble.empty(),
                Set.of(SkillRisk.NEEDS_LINE_OF_SIGHT), false);
        var tick = new java.util.concurrent.atomic.AtomicLong(100);
        Function<LivingEntity, OptionalDouble> angle = entity -> shieldAngle;
        var actions = new CombatActionsAdapter(source, id -> loaded, angle, entity -> blocking.test(entity),
                id -> movement, id -> Optional.empty(), () -> serverThread,
                akyrion.thepredict.engine.ServerClock.of(tick::get, () -> 0L));
        assertTrue(actions.seesTarget(MOB));
        assertEquals(ExecutionOutcome.CAST, actions.cast(MOB, bolt, Movement.HOLD));
        assertEquals(1, caster.sightChecks, "the cast reuses the ray of seesTarget in the same tick");
        tick.set(101);
        caster.sight = false;
        assertEquals(ExecutionOutcome.NOT_ELIGIBLE, actions.cast(MOB, bolt, Movement.HOLD));
        assertFalse(actions.seesTarget(MOB));
        assertEquals(2, caster.sightChecks, "a new tick reads once more");
        var direct = adapter();
        assertFalse(direct.seesTarget(MOB));
        assertEquals(ExecutionOutcome.NOT_ELIGIBLE, direct.cast(MOB, bolt, Movement.HOLD));
        assertEquals(4, caster.sightChecks, "without a clock every call reads");
    }

    @Test
    void aTacticSignalUsesTheUpperCaseNameOfTheTactic() {
        engaged();
        adapter().signalTactic(MOB, new TacticId("keep_range"));
        assertEquals(List.of("signal PREDICTOR_KEEP_RANGE"), source.calls);
    }

    @Test
    void threatChangesOnlyReachAnOnlinePlayerInTheSameWorld() {
        engaged();
        var actions = adapter();
        actions.addThreat(MOB, PLAYER, 3.0);
        actions.addThreat(MOB, PLAYER, -2.0);
        actions.addThreat(MOB, PLAYER, 0.0);
        actions.addThreat(MOB, PLAYER, Double.NaN);
        actions.addThreat(MOB, new UUID(0x9L, 2), 3.0);
        target.online = false;
        actions.addThreat(MOB, PLAYER, 3.0);
        target.online = true;
        target.world = ExecutionDoubles.world("nether");
        actions.addThreat(MOB, PLAYER, 3.0);
        assertEquals(List.of("threat 3.0", "threat -2.0"), source.calls);
        target.world = world;
        source.threatTable = false;
        actions.addThreat(MOB, PLAYER, 1.0);
        assertEquals(3, source.calls.size(), "a mob without a threat table ignores the change without an error");
    }

    @Test
    void aDeadPlayerGetsNoThreatAndALossFollowsTheSameChecksAsAGain() {
        engaged();
        var actions = adapter();
        target.dead = true;
        actions.addThreat(MOB, PLAYER, 3.0);
        actions.addThreat(MOB, PLAYER, -2.0);
        assertTrue(source.calls.isEmpty(), "a dead player is neither targeted nor changed");
        target.dead = false;
        target.world = ExecutionDoubles.world("nether");
        actions.addThreat(MOB, PLAYER, -2.0);
        target.world = world;
        target.online = false;
        actions.addThreat(MOB, PLAYER, -2.0);
        assertTrue(source.calls.isEmpty());
        target.online = true;
        actions.addThreat(MOB, PLAYER, -2.0);
        assertEquals(List.of("threat -2.0"), source.calls);
    }

    @Test
    void everyActionIsRefusedOffTheServerThread() {
        engaged();
        serverThread = false;
        var actions = adapter();
        assertThrows(IllegalStateException.class, () -> actions.cast(MOB, strike(), Movement.APPROACH));
        assertThrows(IllegalStateException.class, () -> actions.ticksUntilReady(MOB, strike()));
        assertThrows(IllegalStateException.class, () -> actions.cheaplyAdmitted(MOB, strike(), Movement.APPROACH));
        assertThrows(IllegalStateException.class, () -> actions.seesTarget(MOB));
        assertThrows(IllegalStateException.class, () -> actions.signalTactic(MOB, new TacticId("keep_range")));
        assertThrows(IllegalStateException.class, () -> actions.addThreat(MOB, PLAYER, 1.0));
        var interrupt = new akyrion.thepredict.engine.CastInterrupt();
        assertThrows(IllegalStateException.class, () -> actions.cast(MOB, strike(), Movement.APPROACH, PLAYER));
        assertThrows(IllegalStateException.class, () -> actions.cast(MOB, strike(), Movement.APPROACH, interrupt));
        assertThrows(IllegalStateException.class, () -> actions.cast(MOB, strike(), Movement.APPROACH, PLAYER, interrupt));
        assertThrows(IllegalStateException.class, () -> actions.release(MOB, strike(), Movement.APPROACH, PLAYER, interrupt));
        assertThrows(IllegalStateException.class, () -> actions.ready(MOB, strike()));
        assertThrows(IllegalStateException.class, () -> actions.readyIn(MOB, strike()));
        assertThrows(IllegalStateException.class, () -> actions.liveTargetMatches(MOB, PLAYER));
        assertThrows(IllegalStateException.class, () -> actions.aiTargetMatches(MOB, PLAYER));
        assertThrows(IllegalStateException.class, () -> actions.cue(MOB,
                akyrion.thepredict.domain.CueRequest.tell(akyrion.thepredict.domain.TellAnswer.BLOCK)));
        assertThrows(IllegalStateException.class, () -> actions.writeStanceVariable(MOB, "PRESSING"));
        assertThrows(IllegalStateException.class, () -> actions.writeNativeStance(MOB, "PRESSING"));
        assertTrue(source.calls.isEmpty());
        assertTrue(caster.calls.isEmpty());
    }

    @Test
    void aReleaseCastsAtTheTellTargetWithTheFlagAndSkipsTheShield() {
        engaged();
        target.blocking = true;
        var interrupt = new akyrion.thepredict.engine.CastInterrupt();
        assertEquals(akyrion.thepredict.domain.ReleaseOutcome.RELEASED,
                adapter().release(MOB, strike(), Movement.APPROACH, PLAYER, interrupt));
        assertEquals(List.of("conditionsAt TPV_Strike target", "executeAt TPV_Strike target flagged"), source.calls);
        assertEquals(List.of(interrupt.flag()), source.flags);
        assertEquals(akyrion.thepredict.domain.ReleaseOutcome.TARGET_LOST,
                adapter().release(MOB, strike(), Movement.APPROACH, new UUID(0x9L, 2), interrupt));
        source.onCooldown = true;
        assertEquals(akyrion.thepredict.domain.ReleaseOutcome.REJECTED,
                adapter().release(MOB, strike(), Movement.APPROACH, PLAYER, interrupt));
        source.onCooldown = false;
        source.conditionsHold = false;
        assertEquals(akyrion.thepredict.domain.ReleaseOutcome.REJECTED,
                adapter().release(MOB, strike(), Movement.APPROACH, PLAYER, interrupt));
        source.conditionsHold = true;
        var bolt = declared(Delivery.HITSCAN, OptionalDouble.of(12), OptionalDouble.empty(), Set.of(), false);
        caster.z = 12.1;
        assertEquals(akyrion.thepredict.domain.ReleaseOutcome.WHIFFED,
                adapter().release(MOB, bolt, Movement.HOLD, PLAYER, interrupt));
        caster.z = 2;
        assertEquals(akyrion.thepredict.domain.ReleaseOutcome.RELEASED,
                adapter().release(MOB, melee(), Movement.APPROACH, PLAYER, interrupt));
        assertEquals(List.of("attack", "swing"), caster.calls);
    }

    @Test
    void theGapBetweenBoxesIsZeroWhenTheyTouchAndEuclideanOtherwise() {
        var box = new BoundingBox(0, 0, 0, 1, 1, 1);
        assertEquals(0.0, HitChecks.gapBlocks(box, new BoundingBox(1, 0, 0, 2, 1, 1)));
        assertEquals(2.0, HitChecks.gapBlocks(box, new BoundingBox(3, 0, 0, 4, 1, 1)));
        assertEquals(5.0, HitChecks.gapBlocks(box, new BoundingBox(4, 0, 5, 5, 1, 6)));
    }

    @Test
    void aRangedSkillWithADeclaredReachOfTwelveWaitsAtTwelvePointOneAndCastsAtTwelve() {
        engaged();
        var bolt = declared(Delivery.HITSCAN, OptionalDouble.of(12), OptionalDouble.empty(), Set.of(), false);
        caster.z = 12.1;
        assertEquals(ExecutionOutcome.NOT_ELIGIBLE, cast(bolt, Movement.KEEP_DISTANCE));
        assertTrue(source.calls.isEmpty());
        caster.z = 12;
        assertEquals(ExecutionOutcome.CAST, cast(bolt, Movement.KEEP_DISTANCE));
    }

    @Test
    void aSkillThatNeedsALineOfSightWaitsWithoutOneAndChecksItLast() {
        engaged();
        var bolt = declared(Delivery.HITSCAN, OptionalDouble.of(16), OptionalDouble.empty(),
                Set.of(SkillRisk.NEEDS_LINE_OF_SIGHT), false);
        caster.sight = false;
        assertEquals(ExecutionOutcome.NOT_ELIGIBLE, cast(bolt, Movement.HOLD));
        assertTrue(source.calls.isEmpty());
        source.onCooldown = true;
        assertEquals(ExecutionOutcome.NOT_ELIGIBLE, cast(bolt, Movement.HOLD));
        assertEquals(1, caster.sightChecks, "no sight check while the native cooldown already waits");
        source.onCooldown = false;
        caster.sight = true;
        assertEquals(ExecutionOutcome.CAST, cast(bolt, Movement.HOLD));
    }

    @Test
    void cheapAdmissionUsesReachAndSightIsASeparateRead() {
        engaged();
        var bolt = declared(Delivery.HITSCAN, OptionalDouble.of(12), OptionalDouble.empty(), Set.of(), false);
        caster.z = 12.1;
        caster.sight = false;
        var actions = adapter();
        assertFalse(actions.cheaplyAdmitted(MOB, bolt, Movement.KEEP_DISTANCE));
        assertEquals(0, caster.sightChecks);
        caster.z = 12;
        assertTrue(actions.cheaplyAdmitted(MOB, bolt, Movement.KEEP_DISTANCE));
        assertEquals(0, caster.sightChecks);
        assertFalse(actions.seesTarget(MOB));
        assertEquals(1, caster.sightChecks);
        caster.sight = true;
        assertTrue(actions.seesTarget(MOB));
    }

    @Test
    void aNativeLineOfSightRefusesTheCastBeforeMythicMobsRunsIt() {
        engaged();
        var bolt = declared(Delivery.HITSCAN, OptionalDouble.of(16), OptionalDouble.empty(), Set.of(), false)
                .withNativeFacts(new NativeSkillFacts(OptionalDouble.empty(), 1, NativeSkillFacts.SEQUENTIAL,
                        OptionalInt.empty(), OptionalDouble.empty(), OptionalDouble.empty(), OptionalDouble.empty(),
                        false, false, true, true));
        caster.sight = false;
        assertEquals(ExecutionOutcome.NOT_ELIGIBLE, cast(bolt, Movement.HOLD));
        assertTrue(source.calls.isEmpty());
        assertEquals(1, caster.sightChecks);
        caster.sight = true;
        assertEquals(ExecutionOutcome.CAST, cast(bolt, Movement.HOLD));
    }

    @Test
    void aDeclaredOrALabelledShieldBreakerGoesThroughABlockingTarget() {
        engaged();
        target.blocking = true;
        var plain = declared(Delivery.MELEE, OptionalDouble.of(2.5), OptionalDouble.empty(), Set.of(), false);
        assertEquals(ExecutionOutcome.SKIPPED_SHIELD, cast(plain, Movement.APPROACH));
        var breaker = declared(Delivery.MELEE, OptionalDouble.of(2.5), OptionalDouble.empty(), Set.of(), true);
        assertEquals(ExecutionOutcome.CAST, cast(breaker, Movement.APPROACH));
        assertEquals(ExecutionOutcome.SKIPPED_SHIELD, cast(strike(PredictValue.IGNORES_SHIELD), Movement.APPROACH),
                "PredictValues IGNORES_SHIELD no longer skips the shield (E19-1 DP5)");
    }

    @Test
    void anAreaAroundTheCasterIsNotAimedAtTheTargetSoTheShieldDoesNotStopIt() {
        engaged();
        target.blocking = true;
        target.noDamageTicks = 20;
        var slam = declared(Delivery.AREA_AROUND_SELF, OptionalDouble.empty(), OptionalDouble.of(3), Set.of(), false);
        assertEquals(ExecutionOutcome.CAST, cast(slam, Movement.APPROACH));
        caster.z = 3;
        assertEquals(ExecutionOutcome.NOT_ELIGIBLE, cast(slam, Movement.APPROACH),
                "at exactly its radius, as @PlayersInRadius{r=3}, it waits");
        caster.z = 2.9999;
        assertEquals(ExecutionOutcome.CAST, cast(slam, Movement.APPROACH));
    }

    @Test
    void thePurposeShieldTestProfileSkipsTheShieldOnlyWithTheNormalStrike() throws Exception {
        engaged();
        target.blocking = true;
        var directory = Files.createTempDirectory("purpose-shield");
        try (var stream = getClass().getResourceAsStream("/profiles/purpose-shield.yml")) {
            Files.copy(Objects.requireNonNull(stream), directory.resolve("purpose-shield.yml"));
        }
        var profile = new ProfileLoader().load(directory).profiles().get("PurposeShieldMob");
        Files.delete(directory.resolve("purpose-shield.yml"));
        Files.delete(directory);
        loaded = Optional.of(strike);
        var press = rename(profile.choices().get(new ChoiceId("strike")));
        var breaker = rename(profile.choices().get(new ChoiceId("guard_break")));
        assertEquals(ExecutionOutcome.SKIPPED_SHIELD, cast(press, Movement.APPROACH));
        assertEquals(ExecutionOutcome.CAST, cast(breaker, Movement.APPROACH));
    }

    private static SkillChoice rename(SkillChoice choice) {
        return new SkillChoice(choice.id(), choice.kind(), Optional.of("TPV_Strike"), choice.values(),
                choice.requiresTarget(), choice.extraCooldownTicks(), choice.typedPurpose());
    }

    private static SkillChoice declared(Delivery delivery, OptionalDouble reach, OptionalDouble radius,
                                        Set<SkillRisk> risks, boolean ignoresShield) {
        return new SkillChoice(new ChoiceId("strike"), ChoiceKind.MYTHIC_SKILL, Optional.of("TPV_Strike"), Set.of(),
                true, 0, new SkillPurpose(SkillRole.DAMAGE, Set.of(SkillRole.DAMAGE), delivery, reach, radius, 0,
                CooldownClass.UNKNOWN, DamageTier.MEDIUM, Set.of(), risks, ignoresShield, PurposeOrigin.DECLARED));
    }

    @Test
    void aSkillIsCastAtAZombieTargetWithTheChecksOfAnyTarget() {
        source.caster = Optional.of(caster.entity);
        source.target = Optional.of(zombie.entity);
        assertEquals(ExecutionOutcome.CAST, cast(strike(), Movement.APPROACH));
        assertEquals(List.of("conditionsAt TPV_Strike zombie", "executeAt TPV_Strike zombie"), source.calls);
    }

    @Test
    void aMobWhoseShieldAlreadyBlocksSkipsTheChoiceUnlessItIgnoresShields() {
        source.caster = Optional.of(caster.entity);
        source.target = Optional.of(zombie.entity);
        blocking = entity -> entity == zombie.entity;
        assertEquals(ExecutionOutcome.SKIPPED_SHIELD, cast(strike(), Movement.APPROACH));
        assertEquals(ExecutionOutcome.SKIPPED_SHIELD, cast(strike(PredictValue.IGNORES_SHIELD), Movement.APPROACH),
                "PredictValues IGNORES_SHIELD no longer skips the shield (E19-1 DP5)");
        blocking = entity -> false;
        assertEquals(ExecutionOutcome.CAST, cast(strike(), Movement.APPROACH), "raised but not yet blocking");
    }

    @Test
    void threatReachesAValidLivingParticipantInTheSameWorldAndNoDeadInvalidOrUnknownOne() {
        engaged();
        var actions = adapter();
        actions.addThreat(MOB, ZOMBIE, 2.0);
        assertEquals(List.of("threat 2.0"), source.calls);
        zombie.dead = true;
        actions.addThreat(MOB, ZOMBIE, 2.0);
        zombie.dead = false;
        zombie.valid = false;
        actions.addThreat(MOB, ZOMBIE, 2.0);
        zombie.valid = true;
        zombie.world = ExecutionDoubles.world("nether");
        actions.addThreat(MOB, ZOMBIE, 2.0);
        actions.addThreat(MOB, new UUID(0x2AL, 9), 2.0);
        assertEquals(List.of("threat 2.0"), source.calls);
    }

    @Test
    void ticksUntilReadyCeilsACooldownAndTreatsARemainingChargeAsReady() {
        engaged();
        assertEquals(OptionalInt.empty(), adapter().ticksUntilReady(MOB, melee()));
        loaded = Optional.empty();
        assertEquals(OptionalInt.empty(), adapter().ticksUntilReady(MOB, strike()));
        loaded = Optional.of(strike);
        source.onCooldown = false;
        source.remaining = OptionalLong.empty();
        assertEquals(OptionalInt.empty(), adapter().ticksUntilReady(MOB, strike()));
        source.remaining = OptionalLong.of(1_250);
        assertEquals(OptionalInt.of(0), adapter().ticksUntilReady(MOB, strike()));
        source.remaining = OptionalLong.of(Long.MAX_VALUE);
        assertEquals(OptionalInt.of(0), adapter().ticksUntilReady(MOB, strike()));
        source.onCooldown = true;
        source.remaining = OptionalLong.empty();
        assertEquals(OptionalInt.empty(), adapter().ticksUntilReady(MOB, strike()));
        source.remaining = OptionalLong.of(Long.MAX_VALUE);
        assertEquals(OptionalInt.empty(), adapter().ticksUntilReady(MOB, strike()));
        source.remaining = OptionalLong.of(-1);
        assertEquals(OptionalInt.empty(), adapter().ticksUntilReady(MOB, strike()));
        assertMillis(0, 0);
        assertMillis(1_250, 25);
        assertMillis(49, 1);
        assertMillis(50, 1);
        assertMillis(51, 2);
        assertMillis(3_600_050, CombatActionsAdapter.MAX_READY_TICKS);
        assertMillis(36_000_000L, CombatActionsAdapter.MAX_READY_TICKS);
        assertTrue(source.calls.isEmpty());
        assertTrue(caster.calls.isEmpty());
    }

    @Test
    void anExpectedTargetThatIsNotTheLiveOneIsNotEligibleAndDoesNotExecute() {
        engaged();
        assertEquals(ExecutionOutcome.NOT_ELIGIBLE,
                adapter().cast(MOB, strike(), Movement.APPROACH, new UUID(0x9L, 2)));
        assertTrue(source.calls.isEmpty());
        assertTrue(caster.calls.isEmpty());
    }

    @Test
    void targetWithinAlsoComparesTheAiTargetAndASkillWithoutItDoesNot() {
        engaged();
        var aimed = strike().withNativeFacts(within(16));
        assertEquals(ExecutionOutcome.NOT_ELIGIBLE, adapter().cast(MOB, aimed, Movement.APPROACH, PLAYER));
        source.aiTarget = Optional.of(ZOMBIE);
        assertEquals(ExecutionOutcome.NOT_ELIGIBLE, adapter().cast(MOB, aimed, Movement.APPROACH, PLAYER));
        source.aiTarget = Optional.of(PLAYER);
        assertEquals(ExecutionOutcome.CAST, adapter().cast(MOB, aimed, Movement.APPROACH, PLAYER));
        source.calls.clear();
        caster.calls.clear();
        source.aiTarget = Optional.of(ZOMBIE);
        assertEquals(ExecutionOutcome.CAST, adapter().cast(MOB, strike(), Movement.APPROACH, PLAYER));
    }

    @Test
    void ignoringInvulnerabilitySkipsTheWindowAndStillRespectsABlockingShield() {
        engaged();
        target.noDamageTicks = 20;
        var ignoring = strike().withNativeFacts(ignoresInvulnerability());
        assertEquals(ExecutionOutcome.CAST, cast(ignoring, Movement.APPROACH));
        source.calls.clear();
        caster.calls.clear();
        assertEquals(ExecutionOutcome.DEFERRED_INVULNERABLE, cast(strike(), Movement.APPROACH));
        assertTrue(source.calls.isEmpty());
        assertTrue(caster.calls.isEmpty());
        var declaredIgnoring = declared(Delivery.MELEE, OptionalDouble.of(2.5), OptionalDouble.empty(), Set.of(), false)
                .withNativeFacts(ignoresInvulnerability());
        assertEquals(ExecutionOutcome.CAST, cast(declaredIgnoring, Movement.APPROACH));
        target.blocking = true;
        source.calls.clear();
        caster.calls.clear();
        assertEquals(ExecutionOutcome.SKIPPED_SHIELD, cast(ignoring, Movement.APPROACH));
        assertTrue(source.calls.isEmpty());
        assertTrue(caster.calls.isEmpty());
    }

    private void assertMillis(long millis, int ticks) {
        source.onCooldown = true;
        source.remaining = OptionalLong.of(millis);
        assertEquals(OptionalInt.of(ticks), adapter().ticksUntilReady(MOB, strike()));
    }

    private static NativeSkillFacts within(double blocks) {
        return new NativeSkillFacts(OptionalDouble.empty(), 1, NativeSkillFacts.SEQUENTIAL, OptionalInt.empty(),
                OptionalDouble.of(blocks), OptionalDouble.empty(), OptionalDouble.empty(), false, false, false, false);
    }

    private static NativeSkillFacts ignoresInvulnerability() {
        return new NativeSkillFacts(OptionalDouble.empty(), 1, NativeSkillFacts.SEQUENTIAL, OptionalInt.empty(),
                OptionalDouble.empty(), OptionalDouble.empty(), OptionalDouble.empty(), false, true, false, true);
    }
}
