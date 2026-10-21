package akyrion.thepredict.engine;

import akyrion.thepredict.domain.BehaviorPolicy;
import akyrion.thepredict.domain.LearningSettings;
import akyrion.thepredict.domain.ParticipantKind;
import akyrion.thepredict.domain.Situation;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.learn.ExploreArm;
import akyrion.thepredict.domain.learn.LearnCause;
import akyrion.thepredict.domain.learn.VoiArm;
import akyrion.thepredict.engine.learn.LearnedArtifacts;

import java.util.List;

/**
 * Arms exploration and a skip at capture, before the question is sent (M28.5, M28.8). Null settings leave a
 * pre-armed session alone. Defaults do not explore and do not skip.
 */
final class CaptureArm {
    private CaptureArm() {
    }

    /**
     * Stages an offered table and activates it when the artifact is {@code ACTIVE}.
     *
     * @param encounter the encounter
     * @param table the offered table, or null
     * @param state the artifact state
     * @param verdict status of {@code GateVerdicts} for the offered table
     * @param scope {@code learning.scope}
     * @param profile profile code of the offered table
     * @param fine packed fine context of the offered table
     */
    static void prepare(Encounter encounter, akyrion.thepredict.engine.learn.ValueTable table,
                        LearnedArtifacts.State state, String verdict, String scope, int profile, long fine) {
        var players = players(encounter);
        for (var session : encounter.memberSessions()) {
            var learning = session.learning();
            learning.audience(players);
            if (table != null && state != null) {
                learning.stage(table, state, verdict, scope, profile, fine);
            }
            learning.activateIfReady();
        }
    }

    /**
     * Arms one session from the learning settings.
     *
     * @param session the session
     * @param encounter the encounter
     * @param plan the planned questions
     * @param settings the learning section, or null
     * @param tap where {@code PLAYER_JOINED} is emitted
     */
    static void arm(MobSession session, Encounter encounter, EncounterQuestions.Plan plan, LearningSettings settings,
                    DecisionTap tap) {
        if (settings == null) {
            return;
        }
        var learning = session.learning();
        if (players(encounter) && !learning.training()) {
            learning.playerJoined(true);
            learning.armExplore(ExploreArm.NONE);
            learning.noteSkip(LearnCause.PLAYER_JOINED);
            tap.learnEvent("", "learn", LearnCause.PLAYER_JOINED);
        } else if (learning.training() || entityDuel(encounter) && settings.exploreInDuels()) {
            learning.armExplore(explore(session, plan, settings));
        }
        armVoi(session, encounter, plan, settings);
    }

    /**
     * Options of the {@code NOW} question for this mob.
     *
     * @param session the session
     * @param plan the plan
     * @return the options, or empty
     */
    static List<TacticId> options(MobSession session, EncounterQuestions.Plan plan) {
        for (var question : plan.questions()) {
            if (question.mob().equals(session.key()) && question.situation() == Situation.NOW) {
                return question.options();
            }
        }
        return List.of();
    }

    private static void armVoi(MobSession session, Encounter encounter, EncounterQuestions.Plan plan,
                               LearningSettings settings) {
        var learning = session.learning();
        if ("off".equals(settings.voiSkip())) {
            learning.armVoi(VoiArm.OFF);
            return;
        }
        var options = options(session, plan);
        var filled = VoiFill.prepare(session, encounter, plan, settings, options);
        var tactic = filled.tactic();
        learning.armVoi(new VoiArm(filled.pass(), settings.voiSkip(), filled.usable(), filled.gap(), filled.sdBest(),
                filled.sdSecond(), filled.seen(), filled.agree(), settings.voiMinAgreement(), filled.delta(),
                settings.voiMaxExpectedGain(), learning.consecutiveSkips(), settings.voiMaxConsecutiveSkips(),
                filled.forbidden(), tactic, session.liveSignature(), session.profile().timing().maximumIntentTicks()));
    }

    /**
     * The exploration arm of this capture. An answer of Jev already accepted by this session forbids dropping the
     * question (errata E28-8: exploration never replaces an accepted answer), so the arm carries that flag forward.
     */
    private static ExploreArm explore(MobSession session, EncounterQuestions.Plan plan, LearningSettings settings) {
        var count = Math.max(1, options(session, plan).size());
        var previous = session.learning().explore();
        var accepted = previous.epsilon() > 0.0 && previous.acceptedJev();
        var rates = settings.exploration();
        return new ExploreArm(session.seed(), 0, count, rates.epsilon(), session.learning().exploredCount(),
                session.learning().adoptionCount(), rates.maxShare(), false, BehaviorPolicy.JEV_ARGMAX, accepted);
    }

    /**
     * Whether a player is a participant of the last capture of the encounter.
     *
     * @param encounter the encounter
     * @return {@code true} with a player participant; {@code false} without facts
     */
    static boolean players(Encounter encounter) {
        return encounter.lastFacts().map(facts -> facts.participants().stream()
                .anyMatch(participant -> participant.alias().kind() == ParticipantKind.PLAYER)).orElse(false);
    }

    private static boolean entityDuel(Encounter encounter) {
        var facts = encounter.lastFacts();
        if (facts.isEmpty() || facts.get().participants().isEmpty()) {
            return false;
        }
        for (var participant : facts.get().participants()) {
            if (participant.alias().kind() != ParticipantKind.ENTITY) {
                return false;
            }
        }
        return true;
    }
}
