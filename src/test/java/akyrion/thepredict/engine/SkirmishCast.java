package akyrion.thepredict.engine;

import akyrion.thepredict.domain.BenchPolicy;
import akyrion.thepredict.domain.ExecutionOutcome;
import akyrion.thepredict.domain.GroupCaps;
import akyrion.thepredict.domain.GroupFairnessReason;
import akyrion.thepredict.domain.GroupMode;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.ReleaseOutcome;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.TacticalProfile;

import java.util.Optional;
import java.util.UUID;

/**
 * Opens and releases a skirmish tell through the production cast path (M25.12). The duel package cannot see
 * the package-private lane, so the fight calls this bridge.
 */
public final class SkirmishCast {
    private SkirmishCast() {
    }

    /**
     * What one admission did.
     *
     * @param denial a fairness refusal, empty when the cast may continue
     * @param held whether the tell holds the cast
     * @param untold whether the cast reserved an impact and did not open a tell
     */
    public record Opening(Optional<GroupFairnessReason> denial, boolean held, boolean untold) {
    }

    /**
     * A session aimed at one participant.
     *
     * @param id mob id
     * @param profile profile that lists the duel skills
     * @param target the participant
     * @param player whether the target is a player
     * @return the session
     */
    public static MobSession session(UUID id, TacticalProfile profile, UUID target, boolean player) {
        var session = new MobSession(new MobKey(id, 0), profile);
        session.playerTarget(player);
        session.recordTarget(target, 4);
        session.groupCopy().sight(1);
        return session;
    }

    /**
     * Seeds the tell draw for one fight.
     *
     * @param session the caster
     * @param seed the fight seed
     */
    public static void reseed(MobSession session, long seed) {
        session.reseed(seed);
    }

    /**
     * Binds the ledgers for this tick. A negative player index is an entity and uses the free bench.
     *
     * @param session the caster
     * @param attack impact ledger
     * @param tells tell ledger
     * @param who member index
     * @param mode group mode
     * @param melee melee weight cap
     * @param gap impact gap
     * @param playerIndex participant index, or -1 for an entity
     */
    public static void bind(MobSession session, AttackLedger attack, TellLedger tells, int who, GroupMode mode,
                            int melee, int gap, int playerIndex) {
        var player = playerIndex >= 0;
        session.playerTarget(player);
        var bench = player ? BenchPolicy.FAIR : BenchPolicy.FREE;
        session.groupBooks(new GroupBooks(attack, tells, GroupCaps.DEFAULT, mode, who, player ? playerIndex : -1,
                bench, false, who == 0, melee, gap, false));
    }

    /**
     * Admits the cast and, when a tell opens, holds it.
     *
     * @param session the caster
     * @param choice the skill
     * @param target the participant the forecast is aimed at
     * @param distance blocks to that participant
     * @param tick current tick
     * @return how the cast was admitted
     */
    public static Opening open(MobSession session, SkillChoice choice, UUID target, double distance, long tick) {
        session.recordTarget(target, distance);
        var opens = TellOpening.opens(session, choice);
        var reason = GroupCastGate.admit(session, choice, tick, opens);
        if (reason.isPresent()) {
            return new Opening(reason, false, false);
        }
        if (!opens) {
            return new Opening(Optional.empty(), false, true);
        }
        var held = TellOpening.hold(session, choice, Movement.NONE, tick, QuietActions.INSTANCE, new long[5]);
        return new Opening(Optional.empty(), held, false);
    }

    /**
     * Marks an untold cast past, or deletes it when Mythic rejects it. The caller passes the cast outcome.
     *
     * @param session the caster
     * @param tick current tick
     */
    public static void settle(MobSession session, long tick) {
        GroupTellClose.untold(session, ExecutionOutcome.CAST, tick);
    }

    /**
     * Whether a tell is still open.
     *
     * @param session the caster
     * @return whether a new cast must wait
     */
    public static boolean telling(MobSession session) {
        return session.tellOpen();
    }

    /**
     * Whether the release lane is due.
     *
     * @param session the caster
     * @param tick current tick
     * @return whether {@link #release} should run
     */
    public static boolean due(MobSession session, long tick) {
        return session.tellOpen() && tick >= session.releaseTick();
    }

    /**
     * Shown tell length, for the synthetic's reaction.
     *
     * @param session the caster
     * @return ticks
     */
    public static int shown(MobSession session) {
        return session.telegraphTicks();
    }

    /**
     * Forecast tick of the reservation this session last noted.
     *
     * @param session the caster
     * @return the impact tick
     */
    public static long impact(MobSession session) {
        return session.groupTellImpact();
    }

    /**
     * Releases a due tell. The caller casts only when the outcome is {@link ReleaseOutcome#RELEASED}.
     *
     * @param session the caster
     * @param tick current tick
     * @param actions the release port
     * @return the outcome
     */
    public static ReleaseOutcome release(MobSession session, long tick, CombatActions actions) {
        return TelegraphRelease.release(session, tick, actions, new long[5]);
    }

    /**
     * Closes a lead-zero tell after the cast has gone out.
     *
     * @param session the caster
     * @param tick current tick
     */
    public static void after(MobSession session, long tick) {
        TellOpening.afterCast(session, tick);
    }
}
