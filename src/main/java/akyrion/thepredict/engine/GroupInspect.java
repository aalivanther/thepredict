package akyrion.thepredict.engine;

import akyrion.thepredict.domain.AttackPool;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Lines of {@code /thepredict inspect <mob> group} (M25). Reads the published copy and the ledgers of that
 * session only.
 */
final class GroupInspect {
    private GroupInspect() {
    }

    /**
     * Describes the group fields of one session.
     *
     * @param session the mob
     * @return the lines, empty when the session is null
     */
    static List<String> lines(MobSession session) {
        if (session == null) {
            return List.of();
        }
        var copy = session.groupCopy();
        var books = session.groupBooks();
        var lines = new ArrayList<String>();
        lines.add("membershipEpoch " + copy.membershipEpoch());
        lines.add("planEpoch " + copy.planEpoch());
        lines.add("stale " + copy.stale());
        lines.add("state " + copy.mode().name().toLowerCase(Locale.ROOT));
        lines.add("leader " + (books != null && books.leader()));
        lines.add("squad " + copy.squadSize());
        lines.add("role " + copy.role().map(role -> role.name().toLowerCase(Locale.ROOT)).orElse("none"));
        lines.add("slot " + copy.slotIndex());
        lines.add("plan " + copy.plan().map(plan -> plan.option()).orElse("none"));
        lines.add("origin " + (copy.plan().isEmpty() ? "none" : copy.planAnswered() ? "jev" : "local"));
        lines.add("seesTarget " + (copy.seesFirstHand() ? 1 : 0));
        var tokens = new StringBuilder();
        for (var pool : AttackPool.values()) {
            if ((copy.tokenMask() & (1 << pool.ordinal())) != 0) {
                if (!tokens.isEmpty()) {
                    tokens.append(',');
                }
                tokens.append(pool.name().toLowerCase(Locale.ROOT));
            }
        }
        lines.add("tokens " + (tokens.isEmpty() ? "none" : tokens));
        lines.add("player " + (books == null ? -1 : books.playerIndex()));
        lines.add("table " + session.threatTableGroup());
        return List.copyOf(lines);
    }
}
