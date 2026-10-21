package akyrion.thepredict.engine;

import akyrion.thepredict.domain.CounterMap;
import akyrion.thepredict.domain.EnemyPattern;
import akyrion.thepredict.domain.ExploitPolicy;
import akyrion.thepredict.domain.PatternMode;
import akyrion.thepredict.domain.PatternSettings;
import akyrion.thepredict.domain.VarietyPicker;

import java.util.UUID;

/**
 * Copies the live counter onto the session (M26.5, M26.6). Observe keeps the factor at one and the preference empty.
 * Variety, rest, the quota window and the quota itself ({@code patterns.maxExploitShare}, DOC-06) are decided here,
 * then the tick layer and the ready cast read them.
 */
final class PatternBias {
    private static final EnemyPattern[] PATTERNS = EnemyPattern.values();

    private final double[] confidence = new double[PATTERNS.length];
    private long selections;

    /**
     * Publishes mask, forecast, factor, reserve and the preference {@code ReadyCasts} reads.
     *
     * @param session the session
     * @param learned the enemy model, or {@code null} when there is no target
     * @param settings pattern limits
     * @param mode effective mode
     * @param quiet a hot session whose previous signals are not valid
     * @param target the enemy, or {@code null}
     * @param tick the server tick
     */
    void apply(MobSession session, EnemyModel learned, PatternSettings settings, PatternMode mode, boolean quiet,
               UUID target, long tick) {
        session.patternSeenTick(tick);
        session.patternMode(mode);
        var live = mode == PatternMode.BIAS || mode == PatternMode.FULL;
        session.patternPass(live);
        var limit = variety(settings, session);
        session.patternVariety(limit);
        var mask = !live || quiet || target == null || learned == null ? 0 : learned.activeMask();
        session.patternCounterActive(mask != 0);
        session.patternWindow(settings.exploitWindowTicks());
        session.patternQuota(settings.maxExploitShare());
        session.patternRestAfter(settings.restAfterSuccesses());
        session.patternRestTicks(ExploitPolicy.restTicks(settings.restTicks(), session.entropyActive(),
                session.patternPass()));
        if (mask == 0) {
            clear(session);
            return;
        }
        var chosen = variety(session, learned, mask, limit);
        session.patternHeld(chosen);
        copy(session, chosen);
    }

    /**
     * The patterns that carry a bias. {@link VarietyPicker#select} is pure, so it runs only when the active mask, a
     * confidence, the held bits or the cap changed since the previous step of this session (E-11).
     */
    private int variety(MobSession session, EnemyModel learned, int mask, int limit) {
        for (var pattern : PATTERNS) {
            var tracker = learned.tracker(pattern);
            confidence[pattern.ordinal()] = tracker == null ? 0.0 : tracker.evidence().mean();
        }
        var held = session.patternHeld();
        var chosen = session.keptVariety(mask, confidence, held, limit);
        if (chosen < 0) {
            chosen = VarietyPicker.select(mask, confidence, held, limit);
            session.keepVariety(mask, confidence, held, limit, chosen);
            selections++;
        }
        return chosen;
    }

    /**
     * Times {@link VarietyPicker#select} ran: only when the active mask, a confidence, the held bits or the cap of a
     * session changed since its previous step (M26.6, E-11). For the tests and diagnostics.
     *
     * @return the count
     */
    long selections() {
        return selections;
    }

    /**
     * Patterns this session may exploit at once (M26.6): {@code patterns.maxExploitedPatterns} after the entropy rule.
     * The exploit query of the local pick must use the same cap ({@code ExploitPolicy.allowsNow}, DAL-04).
     *
     * @param settings pattern limits
     * @param session the session, after this step wrote its pass mark
     * @return the cap, at least one
     */
    static int variety(PatternSettings settings, MobSession session) {
        return ExploitPolicy.variety(settings.maxExploitedPatterns(), session.entropyActive(), session.patternPass());
    }

    private static void clear(MobSession session) {
        session.patternHeld(0);
        session.patternFactor(1.0);
        session.patternReserve(false);
        session.patternPreference(0L);
        session.patternFocus(-1);
    }

    private static void copy(MobSession session, int chosen) {
        var factor = 1.0;
        var reserve = false;
        var preference = 0L;
        var focus = -1;
        for (var pattern : PATTERNS) {
            if ((chosen & pattern.bit()) == 0) {
                continue;
            }
            var bias = CounterMap.of(pattern);
            factor = Math.max(factor, bias.factor());
            reserve = reserve || bias.reserve();
            preference |= bias.preference();
            if (focus < 0) {
                focus = pattern.ordinal();
            }
        }
        session.patternFactor(factor);
        session.patternReserve(reserve);
        session.patternPreference(preference);
        session.patternFocus(focus);
    }
}
