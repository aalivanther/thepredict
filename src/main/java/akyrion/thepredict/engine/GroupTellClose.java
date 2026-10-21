package akyrion.thepredict.engine;

import akyrion.thepredict.domain.AttackPool;
import akyrion.thepredict.domain.DangerClass;
import akyrion.thepredict.domain.ExecutionOutcome;
import akyrion.thepredict.domain.TelegraphEnd;

/**
 * Applies one telegraph end to the attack and tell ledgers (M25). A session without books changes nothing.
 */
final class GroupTellClose {
    private GroupTellClose() {
    }

    /**
     * Closes the reservation this session opened. Waiters are rearmed by the ledger when the end deletes it.
     *
     * @param session the caster
     * @param end how the tell closed
     * @param tick server tick
     */
    static void apply(MobSession session, TelegraphEnd end, long tick) {
        var books = session.groupBooks();
        if (books == null || books.playerIndex() < 0 || end == null) {
            return;
        }
        var danger = danger(session.groupTellDanger());
        var retention = Math.max(books.gapTicks(), books.caps().bigImpactSpacingTicks());
        books.attack().closeImpact(books.playerIndex(), books.memberIndex(), end, tick, session.groupTellImpact());
        books.tells().closeEnd(books.playerIndex(), danger, end, session.groupTellImpact(), tick, retention);
        for (var pool : AttackPool.values()) {
            if (!books.attack().holding(books.playerIndex(), books.memberIndex(), pool)) {
                session.groupCopy().clearToken(pool);
            }
        }
    }

    /**
     * Closes a reservation a cast made without opening a tell. A cast that went out stays past. A MythicMobs
     * rejection deletes that tick. Any other outcome deletes it so the ring does not keep a live row.
     *
     * @param session the caster
     * @param outcome how the cast returned
     * @param tick server tick
     */
    static void untold(MobSession session, ExecutionOutcome outcome, long tick) {
        if (session == null || outcome == null || !session.takeUntoldReservation()) {
            return;
        }
        var books = session.groupBooks();
        if (books == null || books.playerIndex() < 0) {
            return;
        }
        var end = switch (outcome) {
            case CAST -> TelegraphEnd.RELEASED;
            case REJECTED_BY_MYTHICMOBS -> TelegraphEnd.REJECTED;
            default -> TelegraphEnd.INTERRUPTED;
        };
        books.attack().closeImpact(books.playerIndex(), books.memberIndex(), end, tick, session.groupTellImpact());
        for (var pool : AttackPool.values()) {
            if (!books.attack().holding(books.playerIndex(), books.memberIndex(), pool)) {
                session.groupCopy().clearToken(pool);
            }
        }
    }

    private static DangerClass danger(int ordinal) {
        var values = DangerClass.values();
        if (ordinal < 0 || ordinal >= values.length) {
            return DangerClass.LIGHT;
        }
        return values[ordinal];
    }
}
