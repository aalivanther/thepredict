package akyrion.thepredict.adapter.server.duel;

import akyrion.thepredict.domain.ChoiceId;
import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.ExecutionOutcome;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.engine.IntentBoard;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The casts, damage and guards of a duel recording (M18.4). A ThePredict cast is confirmed by the engine
 * ({@code CAST}). {@code MythicSkillEvent} fires before the cooldown lock and the charges and also without a usage check
 * (DT-40), so for a classic duelist, or a ThePredict duelist at level 4 whose {@code DuelRule*} fire, it is written as an
 * {@code ATTEMPT} and counted as a cast; for a ThePredict duelist under control it is written but not counted, because
 * the engine counts the same cast. A cast of a damaging skill waits {@value #HIT_WINDOW_TICKS} ticks for a hit: the
 * first damage of its caster on the rival in that window is a hit of that skill; the misses are the counted casts
 * without a hit. Package-private; server thread only; at most {@value #MAX_PENDING} pending casts per duelist.
 */
final class DuelCombat {
    /** Ticks after a cast within which a damage of its caster on the rival is a hit of that cast. */
    static final int HIT_WINDOW_TICKS = 20;
    /** Pending casts kept per duelist. */
    static final int MAX_PENDING = 8;
    /** Name of the vanilla attack of a {@code VANILLA_MELEE} choice in the totals. */
    static final String VANILLA_MELEE = "vanilla_melee";

    private record Pending(String skill, long tick) {
    }

    private final DuelSession session;
    private final IntentBoard board;
    private final Map<UUID, ArrayDeque<Pending>> pending = new HashMap<>();

    DuelCombat(DuelSession session, IntentBoard board) {
        this.session = Objects.requireNonNull(session, "session");
        this.board = Objects.requireNonNull(board, "board");
    }

    /** A choice of a ThePredict duelist was tried; a {@code CAST} counts and waits for its hit. */
    void attempted(MobKey mob, TacticId tactic, ChoiceId choice, ExecutionOutcome outcome, long tick) {
        var id = mob.entityId();
        var skill = session.scene().profile(id).map(profile -> profile.choices().get(choice))
                .flatMap(SkillChoice::mythicSkillId).orElse("");
        var side = session.sideOf(id).map(DuelRounds.Side::text).orElse("");
        session.row(DuelFile.CASTS, DuelRows.csv(tick, session.rounds().round(), session.rounds().arm(), id, side,
                session.kindOf(id).text(), "THEPREDICT", skill, tactic.value(), choice.value(), outcome.name()));
        if (outcome == ExecutionOutcome.CAST) {
            counted(id, skill.isEmpty() ? VANILLA_MELEE : skill, tick);
        }
    }

    /** A {@code MythicSkillEvent} of a counted skill cast by a followed duelist. */
    void skillEvent(UUID caster, String skill, long tick) {
        var duelist = session.rounds().duelist(caster);
        if (duelist.isEmpty() || !session.counted().contains(skill)) {
            return;
        }
        var kind = duelist.get().kind();
        session.row(DuelFile.CASTS, DuelRows.csv(tick, session.rounds().round(), session.rounds().arm(), caster,
                duelist.get().side().text(), kind.text(), "MYTHIC_EVENT", skill, "", "", "ATTEMPT"));
        if (kind == DuelRounds.Kind.CLASSIC || board.current(caster).level() == ControlLevel.BASE) {
            counted(caster, skill, tick);
        }
    }

    /** A damage taken or dealt by a followed duelist. */
    void damaged(Optional<UUID> attacker, UUID victim, String cause, double finalDamage, double healthFraction,
                 boolean victimBlocking, long tick) {
        var victimSide = session.rounds().duelist(victim).map(DuelRounds.Duelist::side);
        var attackerSide = attacker.flatMap(id -> session.rounds().duelist(id)).map(DuelRounds.Duelist::side);
        if (victimSide.isEmpty() && attackerSide.isEmpty()) {
            return;
        }
        var onRival = attackerSide.isPresent() && victimSide.isPresent() && attackerSide.get() != victimSide.get();
        var skill = onRival ? hitOf(attacker.get(), tick) : Optional.<String>empty();
        if (onRival) {
            session.rounds().hit(attackerSide.get(), healthFraction);
            skill.ifPresent(name -> session.summary().hit(session.rounds().arm(), name));
        }
        session.row(DuelFile.DAMAGE, DuelRows.csv(tick, session.rounds().round(), session.rounds().arm(),
                attacker, attackerSide.map(DuelRounds.Side::text), victim,
                victimSide.map(DuelRounds.Side::text), cause, finalDamage, healthFraction, victimBlocking,
                skill.orElse(""), skill.isPresent()));
        if (victimBlocking && victimSide.isPresent()) {
            session.row(DuelFile.DEFENSE, DuelRows.csv(tick, session.rounds().round(), session.rounds().arm(),
                    victim, "BLOCKED_HIT"));
        }
    }

    /** Forgets the pending casts of a duelist that died or was removed. */
    void forget(UUID id) {
        pending.remove(id);
    }

    private void counted(UUID caster, String skill, long tick) {
        var arm = session.rounds().arm();
        session.summary().cast(arm, skill);
        if (session.guard(skill)) {
            session.summary().guard(arm);
            session.row(DuelFile.DEFENSE, DuelRows.csv(tick, session.rounds().round(), arm, caster, "GUARD_CAST"));
        }
        if (skill.equals(VANILLA_MELEE) || session.damaging(skill)) {
            var queue = pending.computeIfAbsent(caster, key -> new ArrayDeque<>());
            if (queue.size() >= MAX_PENDING) {
                queue.removeFirst();
            }
            queue.addLast(new Pending(skill, tick));
        }
    }

    /** The oldest cast of the attacker still in its window, which this damage confirms. */
    private Optional<String> hitOf(UUID attacker, long tick) {
        var queue = pending.get(attacker);
        while (queue != null && !queue.isEmpty()) {
            var first = queue.removeFirst();
            if (tick - first.tick() <= HIT_WINDOW_TICKS) {
                return Optional.of(first.skill());
            }
        }
        return Optional.empty();
    }
}
