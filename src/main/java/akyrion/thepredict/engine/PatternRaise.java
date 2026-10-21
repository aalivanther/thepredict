package akyrion.thepredict.engine;

import akyrion.thepredict.domain.CandidatePolicy;
import akyrion.thepredict.domain.CandidateScoring;
import akyrion.thepredict.domain.MobFacts;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.Tactic;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.domain.UseCondition;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Whether {@code ENEMY_PATTERN} alone put a choice first on the set {@code raisedTactic} ranks (M26.5).
 * Package-private. That set is the launchable subset {@code readyTactic} passes in: tactics of
 * {@code eligibleWhenReady} whose choice is off cooldown, ordered with the capture conditions, not the probe
 * overlay. A choice that just started its cooldown stays in the subset, because the rank is the one that was
 * launchable when the cast began. {@code raisedByPattern} of the whole eligible set is not enough when its
 * leader is reloading.
 */
final class PatternRaise {
    private PatternRaise() {
    }

    /**
     * Use conditions of one capture. Production wires the encounter lookup; the default is the stored capture.
     */
    @FunctionalInterface
    interface PatternUse {
        /**
         * @param session the session
         * @param facts the capture {@code raisedTactic} reads
         * @return the conditions, which the caller must not modify
         */
        Set<UseCondition> of(MobSession session, MobFacts facts);
    }

    /**
     * Conditions stored at capture, without the probe overlay.
     *
     * @param session the session
     * @param facts the capture, required and already applied by {@link MobSession#facts}
     * @return the conditions, which the caller must not modify
     */
    static Set<UseCondition> captured(MobSession session, MobFacts facts) {
        Objects.requireNonNull(facts, "facts");
        return session.conditionsAtCapture();
    }

    /**
     * Whether the factor alone put {@code choice} first on the launchable subset.
     *
     * @param control reads cooldowns
     * @param session the session
     * @param choice the choice that was cast
     * @param tick the server tick
     * @return {@code true} when that choice is the raised leader
     */
    static boolean lifts(IntentControl control, MobSession session, SkillChoice choice, long tick) {
        if (session.candidateScoring() != CandidateScoring.UTILITY || session.patternHeld() == 0) {
            return false;
        }
        var facts = session.lastMobFacts().orElse(null);
        if (facts == null || facts.eligibleWhenReady().isEmpty()) {
            return false;
        }
        var tactics = tactics(control, session, facts, choice, tick);
        if (tactics.isEmpty()) {
            return false;
        }
        var rank = CandidatePolicy.rank(CandidateScoring.UTILITY, session.profile(), tactics,
                control.patternConditions(session, facts), session.patternHeld());
        return CandidatePolicy.lifts(rank, session.profile(), choice.id());
    }

    private static List<Tactic> tactics(IntentControl control, MobSession session, MobFacts facts, SkillChoice choice,
                                        long tick) {
        var profile = session.profile();
        var ids = new LinkedHashSet<TacticId>();
        for (var tacticId : facts.eligibleWhenReady()) {
            if (counts(control, session, profile, tacticId, choice, tick)) {
                ids.add(tacticId);
            }
        }
        narrow(session, ids);
        var tactics = new ArrayList<Tactic>();
        for (var tactic : profile.tactics().values()) {
            if (ids.contains(tactic.id())) {
                tactics.add(tactic);
            }
        }
        return tactics;
    }

    private static void narrow(MobSession session, Set<TacticId> ids) {
        var resolved = session.resolvedTactic();
        if (resolved.isEmpty()) {
            return;
        }
        var only = resolved.get();
        var kept = ids.contains(only);
        ids.clear();
        if (kept) {
            ids.add(only);
        }
    }

    private static boolean counts(IntentControl control, MobSession session, TacticalProfile profile, TacticId tacticId,
                                  SkillChoice casting, long tick) {
        var skill = choiceOf(profile, tacticId);
        if (skill == null || skill.id().equals(casting.id())) {
            return true;
        }
        return control.offCooldown(session, skill, tick);
    }

    private static SkillChoice choiceOf(TacticalProfile profile, TacticId tacticId) {
        var tactic = profile.tactics().get(tacticId);
        if (tactic == null || tactic.choice().isEmpty()) {
            return null;
        }
        return profile.choices().get(tactic.choice().get());
    }
}
