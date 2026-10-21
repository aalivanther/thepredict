package akyrion.thepredict.engine;

import akyrion.thepredict.domain.AttackPool;
import akyrion.thepredict.domain.BenchPolicy;
import akyrion.thepredict.domain.ChoiceKind;
import akyrion.thepredict.domain.DangerClass;
import akyrion.thepredict.domain.DangerRules;
import akyrion.thepredict.domain.Delivery;
import akyrion.thepredict.domain.GroupFairnessReason;
import akyrion.thepredict.domain.GroupFairnessRules;
import akyrion.thepredict.domain.GroupMode;
import akyrion.thepredict.domain.ImpactForecast;
import akyrion.thepredict.domain.SkillChoice;

import java.util.Optional;

/**
 * Steps 3 to 5 of the cast order (M25): token, tell load, honesty. It runs after the zone advice and before
 * a tell is opened. A refusal opens nothing. Releasing a tell does not call this class. Against a free bench,
 * or while entropy is active, the ledger grants and records and does not deny.
 */
public final class GroupCastGate {
    private GroupCastGate() {
    }

    /**
     * Whether the cast may continue.
     *
     * @param session the caster
     * @param choice the choice
     * @param tick current tick
     * @param willTell whether a tell would open
     * @return empty when the cast may continue
     */
    public static Optional<GroupFairnessReason> admit(MobSession session, SkillChoice choice, long tick,
                                                      boolean willTell) {
        var books = session.groupBooks();
        if (books == null || books.mode() == GroupMode.OFF || books.playerIndex() < 0) {
            return Optional.empty();
        }
        var asked = pool(choice);
        if (asked.isEmpty()) {
            return Optional.empty();
        }
        var recordOnly = recordOnly(session, books);
        var danger = DangerRules.classify(choice);
        if (!recordOnly && !session.groupCopy().seesFirstHand()
                && danger.ordinal() >= DangerClass.MEDIUM.ordinal()) {
            return Optional.of(GroupFairnessReason.SECOND_HAND);
        }
        if (!recordOnly && books.grace()) {
            session.ledgerWaitUntilTick(tick + 1);
            return Optional.of(GroupFairnessReason.GRACE);
        }
        var player = books.playerIndex();
        var pool = asked.get();
        if (!grant(session, books, player, pool, tick, recordOnly)) {
            return Optional.of(GroupFairnessReason.TOKEN_CAPACITY);
        }
        writeSpeed(choice, session);
        var forecast = forecast(choice, tick, session, willTell);
        var margin = margin(choice, books);
        var gap = recordOnly ? 0 : books.gapTicks();
        var retention = Math.max(books.gapTicks(), books.caps().bigImpactSpacingTicks());
        var reserved = books.attack().reserve(player, books.memberIndex(), forecast.tick(), tick, retention, gap,
                recordOnly ? 0 : margin);
        if (!recordOnly && reserved.reason().isPresent()) {
            session.ledgerWaitUntilTick(reserved.waitUntil());
            return reserved.reason();
        }
        if (reserved.reason().isEmpty()) {
            session.noteGroupTell(forecast.tick(), danger.ordinal());
            if (!willTell) {
                session.noteUntoldReservation();
            }
        }
        if (!willTell) {
            return Optional.empty();
        }
        return tell(session, books, choice, player, forecast.tick(), tick, recordOnly);
    }

    private static boolean recordOnly(MobSession session, GroupBooks books) {
        var lifted = akyrion.thepredict.domain.ExploitPolicy.liftsQuota(books.entropy(), session.patternPass());
        if (books.entropy() || lifted) {
            return true;
        }
        return !session.playerTarget() && books.bench() == BenchPolicy.FREE;
    }

    private static void writeSpeed(SkillChoice choice, MobSession session) {
        var declared = choice.nativeFacts().projectileBlocksPerTick();
        session.projectileBlocksPerTick(declared.orElse(0.0));
    }

    /**
     * Whether a choice asks the attack ledger for a token (A13): only those choices can be refused by the token, the
     * impact reservation, the tell load or the grace, so only they wait for {@link MobSession#ledgerWaitUntilTick()}.
     * A heal, an escape or a defense without damage never asks.
     *
     * @param choice the choice
     * @return whether {@link #admit} would ask for a token
     */
    static boolean asksToken(SkillChoice choice) {
        return pool(choice).isPresent();
    }

    private static Optional<AttackPool> pool(SkillChoice choice) {
        var purpose = choice.purpose();
        var damaging = purpose.map(found -> found.damaging()).orElse(true);
        var vanilla = choice.kind() == ChoiceKind.VANILLA_MELEE;
        if (purpose.isEmpty()) {
            return GroupFairnessRules.pool(damaging, vanilla);
        }
        return GroupFairnessRules.pool(damaging, purpose.get().delivery(), vanilla);
    }

    private static boolean grant(MobSession session, GroupBooks books, int player, AttackPool asked, long tick,
                                 boolean recordOnly) {
        if (books.attack().holds(player, books.memberIndex(), asked, tick, books.caps().tokenIdleTimeoutTicks())) {
            return true;
        }
        var unit = Math.max(1, session.profile().group().weight());
        var cap = recordOnly ? Integer.MAX_VALUE
                : asked == AttackPool.MELEE ? books.meleeCap() : books.caps().maxRangedAttackersPerPlayer();
        var granted = books.attack().grant(player, books.memberIndex(), asked, tick, cap, unit);
        if (granted.isPresent()) {
            session.ledgerWaitUntilTick(tick + 1);
            return false;
        }
        session.groupCopy().token(asked);
        if (session.patternReserve()) {
            books.attack().markReserved(player, books.memberIndex(), asked);
        }
        return true;
    }

    private static Optional<GroupFairnessReason> tell(MobSession session, GroupBooks books, SkillChoice choice,
                                                      int player, long impact, long tick, boolean recordOnly) {
        var danger = DangerRules.classify(choice);
        var answer = DangerRules.answer(choice);
        var retention = Math.max(books.gapTicks(), books.caps().bigImpactSpacingTicks());
        books.tells().evictPast(player, tick, retention);
        if (!recordOnly) {
            var honest = books.entropy() || session.groupCopy().seesFirstHand()
                    || danger.ordinal() < DangerClass.MEDIUM.ordinal();
            var refused = books.tells().admit(player, danger, answer, impact, tick,
                    books.caps().bigImpactSpacingTicks(), books.caps().maxMediumTellsPerPlayer(), honest,
                    books.leader());
            if (refused.isPresent()) {
                books.attack().dropFresh(player, books.memberIndex(), impact);
                session.ledgerWaitUntilTick(tick + books.caps().bigImpactSpacingTicks());
                return refused;
            }
        }
        books.tells().open(player, danger, answer, impact, books.leader());
        books.attack().lock(player, books.memberIndex(), pool(choice).orElse(AttackPool.MELEE));
        session.noteGroupTell(impact, danger.ordinal());
        return Optional.empty();
    }

    private static int margin(SkillChoice choice, GroupBooks books) {
        var delivery = choice.purpose().map(found -> found.delivery());
        if (delivery.isPresent() && delivery.get() == Delivery.PROJECTILE) {
            return books.caps().projectileFlightMarginTicks();
        }
        return 0;
    }

    /**
     * Forecast of the impact this cast reserves (A13). A cast that opens a tell lands when the shown length ends,
     * {@code t0 + shown}, and not after the author's wind-up alone: with a lead the skill starts {@code shown - w}
     * ticks later (PZ-05). The length is the one {@link TellOpening#hold} shows.
     */
    private static ImpactForecast.Result forecast(SkillChoice choice, long tick, MobSession session,
                                                  boolean willTell) {
        var distance = session.lastTargetDistanceBlocks().orElse(0.0);
        var purpose = choice.purpose();
        var ticks = willTell ? TellOpening.impactTicks(session, choice) : choice.busyTicks();
        ImpactForecast.Result result;
        if (purpose.isEmpty()) {
            result = ImpactForecast.at(tick, ticks);
        } else {
            var delivery = purpose.get().delivery();
            var speed = delivery == Delivery.PROJECTILE ? session.projectileBlocksPerTick() : 0.0;
            result = ImpactForecast.at(tick, ticks, delivery, distance, speed, 0);
        }
        session.projectileFlightAssumed(result.assumed());
        return result;
    }
}
