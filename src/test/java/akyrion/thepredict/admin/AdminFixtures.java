package akyrion.thepredict.admin;

import akyrion.thepredict.domain.ArmorBand;
import akyrion.thepredict.domain.ChoiceId;
import akyrion.thepredict.domain.ChoiceKind;
import akyrion.thepredict.domain.ContextId;
import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.CooldownClass;
import akyrion.thepredict.domain.DamageTier;
import akyrion.thepredict.domain.Delivery;
import akyrion.thepredict.domain.DistanceBand;
import akyrion.thepredict.domain.EncounterEvent;
import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.EventType;
import akyrion.thepredict.domain.ExecutionOutcome;
import akyrion.thepredict.domain.FallbackReason;
import akyrion.thepredict.domain.GoalSummary;
import akyrion.thepredict.domain.HealthBand;
import akyrion.thepredict.domain.MobCue;
import akyrion.thepredict.domain.MobFacts;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.MovementProgress;
import akyrion.thepredict.domain.MovementSettings;
import akyrion.thepredict.domain.ParticipantAlias;
import akyrion.thepredict.domain.ParticipantFacts;
import akyrion.thepredict.domain.PredictValue;
import akyrion.thepredict.domain.PurposeOrigin;
import akyrion.thepredict.domain.ServiceCheck;
import akyrion.thepredict.domain.ServiceState;
import akyrion.thepredict.domain.SessionState;
import akyrion.thepredict.domain.Situation;
import akyrion.thepredict.domain.SkillPurpose;
import akyrion.thepredict.domain.SkillRisk;
import akyrion.thepredict.domain.SkillRole;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TargetCue;
import akyrion.thepredict.domain.TargetRelation;
import akyrion.thepredict.domain.ThreatTableState;
import akyrion.thepredict.domain.Trend;
import akyrion.thepredict.domain.UseCondition;
import akyrion.thepredict.engine.DecisionMetrics;
import akyrion.thepredict.engine.OutcomeMetrics;
import akyrion.thepredict.engine.OutcomeWindow;
import akyrion.thepredict.engine.SessionReport;
import akyrion.thepredict.engine.StatusReport;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;

/** Fixed reports and plain-text rendering for the tests of {@code admin}. */
final class AdminFixtures {
    static final UUID MOB = UUID.fromString("1a2b3c4d-0000-4000-8000-000000000001");

    private AdminFixtures() {
    }

    static List<String> plain(List<Component> lines) {
        return lines.stream().map(line -> PlainTextComponentSerializer.plainText().serialize(line)).toList();
    }

    static SetupReport setup() {
        return new SetupReport(25, "26.2", "5.13.1-SNAPSHOT", SetupReport.CatalogState.LOADED, 3, 1, 1,
                "LOADED (revision 3, 1 profiles read, 1 active, 0 errors, 0 warnings)", Optional.empty(), Optional.empty(),
                Optional.empty());
    }

    static StatusReport status() {
        var levels = new EnumMap<ControlLevel, Integer>(ControlLevel.class);
        levels.put(ControlLevel.JEV, 2);
        levels.put(ControlLevel.ALTERNATIVE, 0);
        levels.put(ControlLevel.LOCAL, 1);
        levels.put(ControlLevel.BASE, 1);
        var states = new EnumMap<SessionState, Integer>(SessionState.class);
        states.put(SessionState.ACTIVE, 4);
        states.put(SessionState.SUSPENDED, 1);
        states.put(SessionState.CLOSED, 0);
        var ended = new LinkedHashMap<String, Long>();
        ended.put("DIED", 2L);
        ended.put("UNBOUND", 1L);
        var latency = new EnumMap<DecisionMetrics.Span, StatusReport.Latency>(DecisionMetrics.Span.class);
        for (var span : DecisionMetrics.Span.values()) {
            latency.put(span, new StatusReport.Latency(OptionalLong.of(100), OptionalLong.of(400),
                    OptionalLong.of(Long.MAX_VALUE), 12, 2500));
        }
        return new StatusReport(new StatusReport.Service(new ServiceCheck(ServiceState.PAUSED, "timeout, 1200 ms"),
                OptionalLong.of(1_200), false, "jev-1.13.0", Optional.of("jev-1.13.0")),
                new StatusReport.Sessions(levels, states, 1, 7, ended, 0, 0),
                new StatusReport.Encounters(2, 1, 1, 5, 3, 400),
                new StatusReport.Budget(30, 600, 1, 8), Optional.of(new StatusReport.Budget(12, 300, 1, 4)),
                new StatusReport.Consumption(12_000, 600_000), latency,
                List.of(new StatusReport.RecentCause("TIMEOUT", 240)),
                List.of(new StatusReport.RecentCause("SERVICE_PAUSED", 20)),
                List.of(new StatusReport.CounterGroup("failures", Map.of("TIMEOUT", 2L)),
                        new StatusReport.CounterGroup("discards", Map.of())),
                new StatusReport.Context(10, 2, 7, 3, 0, 4),
                new StatusReport.Threat(1, 2, 3, 4, 5, 0, 12.5, 4.0),
                new StatusReport.Effectiveness(List.of(new OutcomeMetrics.LevelOutcomes(ControlLevel.JEV, 3, 0.12, 0.03,
                        0.67, 0.5, 0.21, 4, 2)), 4, 2, 1));
    }

    static MobFacts perception() {
        return new MobFacts(new MobKey(MOB, 3), HealthBand.HEALTHY, Optional.of(new TargetRelation(
                new ParticipantAlias(1), DistanceBand.RANGED, Trend.CLOSING, true, false, false, true, false)),
                Set.of(new ContextId("tracking_enemy")), Set.of(new TacticId("keep_distance_and_shoot"),
                new TacticId("keep_distance")), Set.of(MobCue.NO_LINE_OF_SIGHT), Set.of());
    }

    static SessionReport session(boolean debug, ThreatTableState table) {
        var contingencies = new LinkedHashMap<Situation, TacticId>();
        contingencies.put(Situation.IF_TARGET_IN_MELEE, new TacticId("press_and_strike"));
        var intent = new SessionReport.Intent(new TacticId("keep_distance_and_shoot"), Movement.KEEP_DISTANCE,
                Optional.of(new ChoiceId("ranged")), 0.82, 17, false, List.of(new TacticId("keep_distance")),
                contingencies, debug);
        return new SessionReport(new MobKey(MOB, 3), "ThePredictGuardian", "thepredict_guardian", SessionState.ACTIVE,
                ControlLevel.JEV, Optional.of(new EncounterId(5)), Optional.of(intent), 0, 1, false, OptionalLong.of(3),
                List.of(new GoalSummary("minecraft:float", Set.of("JUMP"), false),
                        new GoalSummary("thepredict:hold", Set.of("MOVE"), true)),
                new MovementProgress(true, true, false, OptionalDouble.of(6.25)), Optional.of(perception()),
                Optional.of(new ParticipantFacts(new ParticipantAlias(1), HealthBand.WOUNDED, ArmorBand.HEAVY,
                        Set.of(TargetCue.RAISING_SHIELD), 1)), 2, List.of(new EncounterEvent(120, EventType.MELEE_BLOCKED,
                        Optional.of(new ParticipantAlias(1)), Optional.empty())), 14, Optional.of(table), 3.5,
                Optional.of(FallbackReason.SERVICE_PAUSED), Optional.of(ExecutionOutcome.NOT_ELIGIBLE), purposes(),
                List.of(new OutcomeWindow.Summary(ControlLevel.JEV, new TacticId("keep_distance_and_shoot"),
                        Movement.KEEP_DISTANCE, 100, 160, 0.12, 0.03, true, 1, 1, false, false, 0.34)));
    }

    static Map<ChoiceId, Optional<SkillPurpose>> purposes() {
        var purposes = new LinkedHashMap<ChoiceId, Optional<SkillPurpose>>();
        purposes.put(new ChoiceId("melee"), Optional.of(new SkillPurpose(SkillRole.DAMAGE, Set.of(SkillRole.DAMAGE),
                Delivery.MELEE, OptionalDouble.of(2.5), OptionalDouble.empty(), 0, CooldownClass.UNKNOWN,
                DamageTier.UNRATED, Set.of(UseCondition.TARGET_IN_MELEE), Set.of(), false, PurposeOrigin.INFERRED)));
        purposes.put(new ChoiceId("ranged"), Optional.of(new SkillPurpose(SkillRole.DAMAGE, Set.of(SkillRole.DAMAGE),
                Delivery.HITSCAN, OptionalDouble.of(16), OptionalDouble.empty(), 10, CooldownClass.SHORT,
                DamageTier.LOW, Set.of(), Set.of(SkillRisk.NEEDS_LINE_OF_SIGHT), false, PurposeOrigin.DECLARED)));
        return purposes;
    }
}
