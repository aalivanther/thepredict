package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ActionStance;
import akyrion.thepredict.domain.AggressionScore;
import akyrion.thepredict.domain.AttackPool;
import akyrion.thepredict.domain.CueRequest;
import akyrion.thepredict.domain.BenchPolicy;
import akyrion.thepredict.domain.DangerClass;
import akyrion.thepredict.domain.DangerRules;
import akyrion.thepredict.domain.GroupBrief;
import akyrion.thepredict.domain.GroupCaps;
import akyrion.thepredict.domain.GroupMode;
import akyrion.thepredict.domain.GroupPlan;
import akyrion.thepredict.domain.GroupPlanPolicy;
import akyrion.thepredict.domain.GroupQuestions;
import akyrion.thepredict.domain.GroupRole;
import akyrion.thepredict.domain.HealthBand;
import akyrion.thepredict.domain.Hungarian;
import akyrion.thepredict.domain.LeaderDeath;
import akyrion.thepredict.domain.RegroupPolicy;
import akyrion.thepredict.domain.RoleAssignment;
import akyrion.thepredict.domain.RoleCapabilities;
import akyrion.thepredict.domain.SharedTableAssignment;
import akyrion.thepredict.domain.SlotAssignment;
import akyrion.thepredict.domain.SquadPolicy;
import akyrion.thepredict.domain.SurroundSlots;
import akyrion.thepredict.domain.TargetDistribution;
import akyrion.thepredict.domain.TickSignals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Membership, plan and ledgers of one encounter (M25). The director owns one of these per open encounter.
 */
final class GroupState {
    private static final AttackPool[] POOLS = AttackPool.values();
    final AttackLedger attack = new AttackLedger();
    final TellLedger tells = new TellLedger();
    final Hungarian hungarian = new Hungarian(24);
    final GroupFightClock fights = new GroupFightClock();
    private final MobSession[] members = new MobSession[16];
    private final MobSession[] slotted = new MobSession[16];
    private final GroupRole[] roles = new GroupRole[16];
    private final int[] heldSlot = new int[16];
    private final UUID[] players = new UUID[16];
    private final boolean[] slotValid = new boolean[48];

    private final GroupPlanBook book = new GroupPlanBook();
    private int count;
    private long membershipEpoch = 1;
    private long planEpoch = 1;
    private int leader = -1;
    private GroupPlan plan;
    private boolean leaderLost;
    private boolean planLost;
    private boolean answerRejected;
    private boolean broken;
    private long brokenUntil;
    private boolean fairFallback;
    private int brokenFor = 100;
    private GroupBrief brief = GroupBrief.NONE;
    private String anchor = "";
    private int arbitrations;
    private final GroupRole[] lastRole = new GroupRole[16];
    private final long[] roleCueAt = new long[16];
    private GroupPlan cuedPlan;
    private int regroupElapsed;
    private long lastSightTick = Long.MIN_VALUE;

    {
        java.util.Arrays.fill(heldSlot, -1);
        java.util.Arrays.fill(roleCueAt, Long.MIN_VALUE);
    }

    long membershipEpoch() {
        return membershipEpoch;
    }

    long planEpoch() {
        return planEpoch;
    }

    GroupBrief brief() {
        return brief;
    }

    GroupPlan currentPlan() {
        return plan;
    }

    /**
     * Whether the last settle dropped {@code g0} because the membership epoch moved. {@code planEpoch} does not.
     *
     * @return whether the caller should count {@code GROUP_EPOCH_CHANGED}
     */
    boolean takeAnswerRejected() {
        var rejected = answerRejected;
        answerRejected = false;
        return rejected;
    }

    GroupFightClock fights() {
        return fights;
    }

    int count() {
        return count;
    }

    /**
     * Copies the encounter's join order. A change of membership bumps the membership epoch.
     *
     * @param encounter the encounter
     * @param tick current tick
     * @return whether the roster changed
     */
    boolean sync(Encounter encounter, long tick, GroupCaps caps) {
        brokenFor = caps.brokenTicks();
        var incoming = new ArrayList<MobSession>(encounter.memberSessions());
        if (incoming.size() > 16) {
            incoming.subList(16, incoming.size()).clear();
        }
        for (var session : incoming) {
            claim(session);
        }
        releaseAbsent(incoming);
        var same = incoming.size() == count;
        if (same) {
            for (var index = 0; index < count; index++) {
                if (members[index] != incoming.get(index)) {
                    same = false;
                    break;
                }
            }
        }
        var previousLeader = leader >= 0 && leader < count ? members[leader] : null;
        if (!same) {
            var had = count > 0;
            count = incoming.size();
            for (var index = 0; index < count; index++) {
                members[index] = incoming.get(index);
            }
            for (var index = count; index < members.length; index++) {
                members[index] = null;
            }
            if (had) {
                membershipEpoch++;
            }
        }
        if (count == 0) {
            fights.wiped(tick);
        }
        var lost = previousLeader != null && indexOf(previousLeader) < 0;
        if (lost) {
            onLeaderLost(previousLeader, tick);
        } else if (!broken) {
            chooseLeader();
        } else if (tick >= brokenUntil) {
            broken = false;
            chooseLeader();
        }
        return !same || lost;
    }

    GroupMode mode(GroupCaps caps) {
        if (fairFallback || count < 2 || broken) {
            return count < 2 ? GroupMode.OFF : GroupMode.FAIR;
        }
        if (leader >= 0) {
            return members[leader].profile().group().effective(caps.mode());
        }
        var best = GroupMode.OFF;
        for (var index = 0; index < count; index++) {
            best = best.max(members[index].profile().group().effective(caps.mode()));
        }
        return best;
    }

    /**
     * Applies a capture, a {@code g0} answer and the death of the plan. Roles are redone when the plan changes
     * or the caller asks for a refresh. The same plan does not raise {@code planEpoch}.
     *
     * @param encounter the encounter
     * @param tick current tick
     * @param caps limits
     * @param refreshRoles whether the periodic replan should assign roles again
     * @return whether the plan died and {@code PLAN_LOST} should be marked
     */
    boolean settle(Encounter encounter, long tick, GroupCaps caps, boolean refreshRoles) {
        answerRejected = false;
        planLost = false;
        var epoch = planEpoch;
        noteSight(tick);
        takeAnswer(encounter, tick, caps);
        takeCapture(encounter, tick, caps);
        if (book.release(tick, caps.minPlanTicks())) {
            planEpoch++;
        }
        var mode = mode(caps);
        var possible = possible(caps, tick);
        if (mode == GroupMode.OFF || broken || count < 2) {
            if (plan != null || book.plan() != null) {
                book.clear();
                plan = null;
                planEpoch++;
                planLost = true;
            }
            leaderLost = false;
            clearRoles();
            brief = GroupBrief.NONE;
            return planLost;
        }
        var expired = book.plan() != null && book.age(tick) >= caps.planHorizonTicks();
        var impossible = book.plan() != null && !possible.contains(book.plan());
        if (book.plan() != null && (leaderLost || expired || impossible)) {
            if (book.die(tick, possible::contains, count)) {
                planEpoch++;
            }
            planLost = true;
        }
        leaderLost = false;
        plan = book.plan();
        if (planEpoch != epoch || refreshRoles) {
            if (plan != null && mode.rank() >= GroupMode.COORDINATE.rank()) {
                assignRoles(caps);
            } else {
                clearRoles();
            }
        }
        publishBrief(caps, mode, possible);
        return planLost;
    }

    private void takeAnswer(Encounter encounter, long tick, GroupCaps caps) {
        if (encounter == null || !encounter.hasGroupAnswer()) {
            return;
        }
        var membership = encounter.groupAnswerMembership();
        var chosen = encounter.groupAnswerPlan();
        var sequence = encounter.groupAnswerSequence();
        encounter.clearGroupAnswer();
        if (membership != membershipEpoch) {
            answerRejected = true;
            return;
        }
        if (book.acceptAnswer(chosen, sequence, tick, caps.minPlanTicks())) {
            planEpoch++;
        }
    }

    private void takeCapture(Encounter encounter, long tick, GroupCaps caps) {
        if (encounter == null || !encounter.hasGroupCapture()) {
            return;
        }
        var asks = encounter.groupCaptureAsks();
        var sequence = encounter.groupCaptureSequence();
        var candidates = encounter.groupCapturePlans();
        encounter.clearGroupCapture();
        var currentPossible = book.plan() == null || possible(caps, tick).contains(book.plan());
        if (book.noteCapture(tick, sequence, asks, candidates, caps.minPlanTicks(), caps.planHorizonTicks(),
                currentPossible)) {
            planEpoch++;
        }
    }

    private List<GroupPlan> possible(GroupCaps caps, long tick) {
        if (mode(caps) == GroupMode.OFF || broken || count < 2) {
            return List.of();
        }
        var ranged = false;
        var support = false;
        for (var index = 0; index < count; index++) {
            var capable = members[index].profile().group().capabilities();
            ranged = ranged || capable.ranged() > 0;
            support = support || capable.support() > 0;
        }
        return GroupPlanPolicy.possible(count, leader >= 0, ranged, support, unseen(caps, tick));
    }

    /** Remembers the last tick a member saw its target in its last capture: the age of the last known position. */
    private void noteSight(long tick) {
        for (var index = 0; index < count; index++) {
            if (sight(members[index]) == 1) {
                lastSightTick = tick;
                return;
            }
        }
    }

    /**
     * Whether {@code search_last_known} can run (M25.9, DOC-03): no member sees its target now, and one saw it at most
     * {@code group.lkpForgetTicks} ago, so the last known position is still fresh. A group that never saw its target
     * has no position to search.
     */
    private boolean unseen(GroupCaps caps, long tick) {
        if (lastSightTick == Long.MIN_VALUE || tick - lastSightTick > caps.lkpForgetTicks()) {
            return false;
        }
        for (var index = 0; index < count; index++) {
            if (sight(members[index]) == 1) {
                return false;
            }
        }
        return true;
    }

    private void clearRoles() {
        for (var index = 0; index < roles.length; index++) {
            roles[index] = null;
        }
    }

    void publish(long tick, GroupCaps caps) {
        var mode = mode(caps);
        finishRegroup();
        fights.noteMembers(count);
        if (count > 0) {
            var source = leader >= 0 ? members[leader] : members[0];
            fights.noteRung(source.difficulty().rung());
        }
        var squads = squads(caps);
        var slots = placeSlots(caps, mode);
        attack.bind(members, stableIndexes(), count);
        for (var index = 0; index < count; index++) {
            publishOne(tick, caps, mode, index, slots, squads);
        }
        if (book.plan() != null && book.started() >= 0) {
            for (var index = 0; index < count; index++) {
                members[index].learning().distillSession().notePlan(book.started());
            }
        }
        publishLeaderReserve(tick, caps);
        if (leader >= 0) {
            var leaderProfile = members[leader].profile().id();
            anchor = leaderProfile;
            if (!leaderProfile.equals(fights.anchor())) {
                fights.leaderChanged(leaderProfile);
            }
        }
    }

    /**
     * Keeps the ledgers and drops role, slot and plan when the copy is behind and this tick does not publish.
     *
     * @return how many members were stale
     */
    int degradeFair() {
        var stale = 0;
        for (var index = 0; index < count; index++) {
            var session = members[index];
            var copy = session.groupCopy();
            copy.noteEpoch(membershipEpoch, planEpoch);
            var books = session.groupBooks();
            if (copy.stale() && books != null) {
                session.groupBooks(books.withMode(GroupMode.FAIR));
                stale++;
            }
        }
        return stale;
    }

    /**
     * Gives melee to the highest score that asked, and steals an unlocked token when the margin is past the cap.
     *
     * @param tick current tick
     * @param caps limits
     * @return whether this call was an arbitration
     */
    boolean arbitrate(long tick, GroupCaps caps, int phase) {
        var period = caps.arbitrationPeriodTicks();
        if (count < 2 || Math.floorMod(tick, period) != Math.floorMod(phase, period)) {
            return false;
        }
        var best = -1;
        var bestScore = -1;
        for (var index = 0; index < count; index++) {
            var session = members[index];
            if (!session.groupCopy().takeMeleeAsk()) {
                continue;
            }
            var score = score(session, index);
            if (score > bestScore || (score == bestScore && best >= 0 && stable(session) < stable(members[best]))) {
                bestScore = score;
                best = index;
            }
        }
        if (best >= 0) {
            award(tick, caps, best, bestScore);
        }
        arbitrations++;
        return true;
    }

    int arbitrations() {
        return arbitrations;
    }

    void fallToFair() {
        fairFallback = true;
        book.clear();
        plan = null;
        brief = GroupBrief.NONE;
        clearRoles();
    }

    private void publishLeaderReserve(long tick, GroupCaps caps) {
        if (leader < 0 || leader >= count || caps.leaderReserveTicks() <= 0 || broken) {
            return;
        }
        var session = members[leader];
        var choices = session.listedChoices();
        long readyAt = Long.MAX_VALUE;
        var busy = 0;
        for (var index = 0; index < choices.length; index++) {
            if (DangerRules.classify(choices[index]).ordinal() < DangerClass.HEAVY.ordinal()) {
                continue;
            }
            var at = session.choiceReadyAt(index);
            if (at == Long.MIN_VALUE || at > readyAt) {
                continue;
            }
            readyAt = at;
            busy = choices[index].busyTicks();
        }
        var player = playerIndex(session);
        tells.expireLeader(player, tick);
        if (readyAt == Long.MAX_VALUE || readyAt > tick + caps.leaderReserveTicks()) {
            return;
        }
        tells.reserveLeader(player, tick, caps.leaderReserveTicks(), readyAt + busy);
    }

    /**
     * The brief of the next capture (M25.10): in {@code full} with a leader, the question set of the leader's profile,
     * the leader, and with a plan question the candidates of {@link GroupPlanPolicy#candidates}, ranked with one plan
     * per family so that {@code search_last_known} can be offered (DOC-03). {@code members} keeps the body of fair
     * and coordinate; the other sets drop member questions (DOC-02).
     */
    private void publishBrief(GroupCaps caps, GroupMode mode, java.util.List<GroupPlan> possible) {
        if (leader < 0 || mode != GroupMode.FULL) {
            brief = GroupBrief.NONE;
            return;
        }
        var questions = members[leader].profile().group().resolvedQuestions(mode, true, count);
        if (questions == GroupQuestions.MEMBERS) {
            brief = GroupBrief.NONE;
            return;
        }
        var names = new ArrayList<String>();
        if (questions.asksPlan()) {
            for (var candidate : GroupPlanPolicy.candidates(possible, plan, count, caps.maxPlanCandidates())) {
                names.add(candidate.option());
            }
        }
        brief = new GroupBrief(questions, names, membershipEpoch, true,
                java.util.Optional.of(members[leader].key().entityId()));
    }

    private void assignRoles(GroupCaps caps) {
        if (count < 2) {
            return;
        }
        var capabilities = new RoleCapabilities[count];
        var allowed = new int[count];
        var distance = new int[count][GroupRole.values().length];
        var current = new GroupRole[count];
        var assigned = new GroupRole[count];
        for (var index = 0; index < count; index++) {
            capabilities[index] = members[index].profile().group().capabilities();
            allowed[index] = RoleAssignment.allowed(members[index].profile().group().roles());
            var seat = stable(members[index]);
            current[index] = seat < 0 ? null : roles[seat];
        }
        try {
            RoleAssignment.assign(capabilities, allowed, distance, current, caps.roleSwitchMarginMillis(), hungarian,
                    assigned);
            for (var index = 0; index < count; index++) {
                var seat = stable(members[index]);
                if (seat >= 0) {
                    roles[seat] = assigned[index];
                }
            }
            fairFallback = false;
        } catch (IllegalStateException exception) {
            fallToFair();
        }
    }

    private void onLeaderLost(MobSession previous, long tick) {
        leaderLost = true;
        var death = previous.profile().group().onLeaderDeath();
        if (death == LeaderDeath.BREAK) {
            broken = true;
            brokenUntil = tick + brokenFor;
            leader = -1;
            return;
        }
        chooseLeader();
    }

    private void chooseLeader() {
        var anyPriority = false;
        var anySquad = false;
        for (var index = 0; index < count; index++) {
            var group = members[index].profile().group();
            anyPriority = anyPriority || group.leaderPriority() > 0;
            anySquad = anySquad || !group.squad().isEmpty();
        }
        if (!anyPriority && !anySquad) {
            leader = -1;
            return;
        }
        var best = 0;
        for (var index = 1; index < count; index++) {
            if (outranks(index, best)) {
                best = index;
            }
        }
        leader = best;
    }

    private boolean outranks(int candidate, int incumbent) {
        var left = members[candidate].profile().group();
        var right = members[incumbent].profile().group();
        if (left.leaderPriority() != right.leaderPriority()) {
            return left.leaderPriority() > right.leaderPriority();
        }
        var leftSquad = squadSize(left.squad());
        var rightSquad = squadSize(right.squad());
        if (leftSquad != rightSquad) {
            return leftSquad > rightSquad;
        }
        return health(candidate) > health(incumbent);
    }

    private int squadSize(String squad) {
        if (squad == null || squad.isEmpty()) {
            return 0;
        }
        var size = 0;
        for (var index = 0; index < count; index++) {
            if (squad.equals(members[index].profile().group().squad())) {
                size++;
            }
        }
        return size;
    }

    private int health(int index) {
        var facts = members[index].lastMobFacts();
        if (facts.isEmpty()) {
            return 0;
        }
        var band = facts.get().health();
        if (band == HealthBand.HEALTHY) {
            return 3;
        }
        if (band == HealthBand.WOUNDED) {
            return 2;
        }
        return 1;
    }

    private int indexOf(MobSession session) {
        for (var index = 0; index < count; index++) {
            if (members[index] == session) {
                return index;
            }
        }
        return -1;
    }

    private int playerIndex(MobSession session) {
        var target = session.lastTargetId();
        if (target.isEmpty()) {
            return -1;
        }
        var id = target.get();
        for (var index = 0; index < players.length; index++) {
            if (id.equals(players[index])) {
                return index;
            }
        }
        for (var index = 0; index < players.length; index++) {
            if (players[index] == null) {
                players[index] = id;
                return index;
            }
        }
        var freed = fights.evictQuietest();
        if (freed >= 0) {
            players[freed] = id;
            return freed;
        }
        return -1;
    }

    void release(UUID entityId) {
        for (var index = 0; index < slotted.length; index++) {
            if (slotted[index] != null && slotted[index].key().entityId().equals(entityId)) {
                attack.cancelUnlocked(index);
            }
        }
    }

    private void publishOne(long tick, GroupCaps caps, GroupMode mode, int index, int[] slots, int[] squads) {
        var session = members[index];
        var seat = stable(session);
        var effective = session.profile().group().effective(caps.mode());
        if (mode == GroupMode.OFF || effective == GroupMode.OFF || seat < 0) {
            outside(session);
            return;
        }
        var shown = shown(mode, effective);
        var melee = Math.min(caps.maxMeleeAttackersPerPlayer(), session.levers().maxMeleeAttackers());
        var gap = Math.max(caps.minImpactGapTicks(), session.levers().impactGapTicks());
        var player = playerIndex(session);
        session.groupBooks(new GroupBooks(attack, tells, caps, shown, seat, player,
                session.profile().group().againstEntities(), session.entropyActive(), index == leader, melee, gap,
                false));
        session.groupOwnsRung(count >= 2 && shown != GroupMode.OFF);
        session.groupFight(fights, player);
        var role = roleOf(shown, seat);
        var slot = slots == null || index >= slots.length ? -1 : slots[index];
        if (slot >= 0) {
            heldSlot[seat] = slot;
        }
        var squad = squadSizeOf(squads, index);
        session.groupCopy().publish(membershipEpoch, planEpoch, role, slot, squad,
                plan == null || shown == GroupMode.FAIR ? -1 : plan.ordinal(), tokenBits(player, seat), sight(session),
                shown);
        session.groupCopy().regroupElapsed(plan == GroupPlan.FALL_BACK_AND_REGROUP ? regroupElapsed : 0);
        session.groupCopy().planAnswered(book.answered());
        trimTokens(session, player, melee, caps);
    }

    /**
     * The token bits of one member as the attack ledger holds them now (EFO-15): a token the ledger released for
     * idleness, or gave to another member, is not published again, so the copy never says a member holds melee that
     * the ledger has taken away. No allocation.
     */
    private int tokenBits(int player, int seat) {
        if (player < 0) {
            return 0;
        }
        var mask = 0;
        for (var pool : POOLS) {
            if (attack.holding(player, seat, pool)) {
                mask |= 1 << pool.ordinal();
            }
        }
        return mask;
    }

    /** A member the group does not coordinate this tick: no books, no group rung, no group clock. */
    private void outside(MobSession session) {
        session.groupBooks(null);
        session.groupOwnsRung(false);
        session.groupFight(null, -1);
        session.groupCopy().noteEpoch(membershipEpoch, planEpoch);
    }

    private GroupMode shown(GroupMode mode, GroupMode effective) {
        var shown = fairFallback || plan == null ? GroupMode.FAIR : effective;
        return shown.rank() > mode.rank() ? mode : shown;
    }

    private GroupRole roleOf(GroupMode shown, int seat) {
        var role = shown.rank() >= GroupMode.COORDINATE.rank() && plan != null ? roles[seat] : null;
        if (role == GroupRole.GUARD && plan != GroupPlan.SCREEN_THE_LEADER) {
            return GroupRole.RESERVE;
        }
        return role;
    }

    private int sight(MobSession session) {
        var facts = session.lastMobFacts();
        if (facts.isEmpty() || facts.get().target().isEmpty() || !facts.get().target().get().visible()) {
            return 0;
        }
        return 1;
    }

    private void trimTokens(MobSession session, int player, int melee, GroupCaps caps) {
        for (var pool : POOLS) {
            var cap = pool == AttackPool.MELEE ? melee : caps.maxRangedAttackersPerPlayer();
            if (attack.stealExcess(player, pool, cap)) {
                session.groupCopy().clearToken(pool);
            }
        }
    }

    private int[] placeSlots(GroupCaps caps, GroupMode mode) {
        if (mode.rank() < GroupMode.COORDINATE.rank() || plan == null || count < 1) {
            return null;
        }
        var placing = new GroupRole[count];
        var current = new int[count];
        var sectorOn = sectorOn();
        java.util.Arrays.fill(slotValid, true);
        var guard = placing(mode, placing, current);
        var mayEnter = new boolean[count];
        var anyRestricted = restrict(mayEnter);
        var anyEntropy = anyEntropy();
        var guardLifted = guard >= 0 && guard < count && members[guard].entropyActive();
        var angle = guardLine(caps, anyRestricted && !guardLifted);
        if (!anyEntropy) {
            markSector(caps.exitSectorDegrees(), sectorOn);
        }
        var sectorRows = anyEntropy && anyRestricted;
        var east = new double[count];
        var south = new double[count];
        standing(east, south);
        return assignSlots(placing, current, guard, angle, sectorRows ? mayEnter : new boolean[0],
                sectorRows ? caps.exitSectorDegrees() : 0, east, south);
    }

    /** Fills the role each member is placed with and the slot it already holds, and returns the guard row or -1. */
    private int placing(GroupMode mode, GroupRole[] placing, int[] current) {
        var guard = -1;
        for (var index = 0; index < count; index++) {
            var seat = stable(members[index]);
            placing[index] = seat < 0 ? GroupRole.RESERVE : roleOf(mode, seat);
            if (placing[index] == null) {
                placing[index] = GroupRole.RESERVE;
            }
            current[index] = seat < 0 ? -1 : heldSlot[seat];
            if (placing[index] == GroupRole.GUARD) {
                guard = index;
            }
            if (seat >= 0) {
                applyMask(index, members[index].slotMask());
            }
        }
        return guard;
    }

    /** Fills whether each member may stand in the exit sector, and returns whether any member may not. */
    private boolean restrict(boolean[] mayEnter) {
        var anyRestricted = false;
        for (var index = 0; index < count; index++) {
            var restricted = !members[index].entropyActive() && (members[index].playerTarget()
                    || members[index].profile().group().againstEntities() != BenchPolicy.FREE);
            mayEnter[index] = !restricted;
            anyRestricted |= restricted;
        }
        return anyRestricted;
    }

    private boolean anyEntropy() {
        for (var index = 0; index < count; index++) {
            if (members[index].entropyActive()) {
                return true;
            }
        }
        return false;
    }

    /** One assignment of the slots; a programming error drops the encounter to fair for the tick. */
    private int[] assignSlots(GroupRole[] placing, int[] current, int guard, int angle, boolean[] mayEnter,
                              int sectorDegrees, double[] east, double[] south) {
        var assigned = new int[count];
        try {
            SlotAssignment.assign(placing, current, slotValid, guard, angle, hungarian, assigned, mayEnter,
                    sectorDegrees, east, south);
        } catch (IllegalStateException exception) {
            fallToFair();
            return null;
        }
        return assigned;
    }

    /**
     * Where each member stands, in blocks east and south of its target (E-5, M25.5): the column of the mob minus the
     * column of its target, the frame of the zone. A member whose columns were never noted stands on the player, as
     * the assignment measured before.
     *
     * @param east filled with the blocks east of the target of each member
     * @param south filled with the blocks south of the target of each member
     */
    private void standing(double[] east, double[] south) {
        for (var index = 0; index < count; index++) {
            var member = members[index];
            if (member.anchorKnown()) {
                east[index] = member.anchorX() - member.threatX();
                south[index] = member.anchorZ() - member.threatZ();
            }
        }
    }

    private boolean sectorOn() {
        if (leader < 0) {
            return true;
        }
        var leaderSession = members[leader];
        if (leaderSession.entropyActive()) {
            return false;
        }
        return leaderSession.playerTarget() || leaderSession.profile().group().againstEntities() != BenchPolicy.FREE;
    }

    private void markSector(int degrees, boolean on) {
        if (!on) {
            return;
        }
        for (var ring = 0; ring < 3; ring++) {
            var angles = ring == 2 ? SurroundSlots.RANGED : ring == 0 ? SurroundSlots.INNER : SurroundSlots.OUTER;
            for (var angle = 0; angle < angles; angle++) {
                if (SurroundSlots.inExitSector(SurroundSlots.angleDegrees(angle, angles), degrees)) {
                    slotValid[ring * 16 + angle] = false;
                }
            }
        }
    }

    private void applyMask(int index, long mask) {
        if (mask == -1L) {
            return;
        }
        var role = roles[stable(members[index])];
        var ring = SlotAssignment.ring(role == null ? GroupRole.RESERVE : role);
        var angles = ring == 2 ? SurroundSlots.RANGED : ring == 0 ? SurroundSlots.INNER : SurroundSlots.OUTER;
        for (var angle = 0; angle < angles; angle++) {
            if ((mask & (1L << (ring * 16 + angle))) == 0) {
                slotValid[ring * 16 + angle] = false;
            }
        }
    }

    private int[] stableIndexes() {
        var slots = new int[count];
        for (var index = 0; index < count; index++) {
            slots[index] = stable(members[index]);
        }
        return slots;
    }

    private void claim(MobSession session) {
        if (stable(session) >= 0) {
            return;
        }
        for (var index = 0; index < slotted.length; index++) {
            if (slotted[index] == null) {
                slotted[index] = session;
                return;
            }
        }
    }

    private void releaseAbsent(List<MobSession> incoming) {
        for (var index = 0; index < slotted.length; index++) {
            var seated = slotted[index];
            if (seated == null || incoming.contains(seated)) {
                continue;
            }
            attack.cancelUnlocked(index);
            if (!attack.anyLocked(index)) {
                slotted[index] = null;
                heldSlot[index] = -1;
            }
        }
    }

    private int stable(MobSession session) {
        for (var index = 0; index < slotted.length; index++) {
            if (slotted[index] == session) {
                return index;
            }
        }
        return -1;
    }

    private int score(MobSession session, int index) {
        var signals = session.tickSignals();
        var stance = session.actionStance();
        var canHit = stance != ActionStance.RECOVERY && stance != ActionStance.STAGGERED && readySoon(session);
        var seat = stable(session);
        var role = seat < 0 || roles[seat] == null ? GroupRole.RESERVE : roles[seat];
        var player = session.groupBooks() == null ? -1 : session.groupBooks().playerIndex();
        var holds = player >= 0 && attack.holding(player, seat, AttackPool.MELEE);
        return AggressionScore.of(role, TickSignals.gapQuarters(signals), TickSignals.octant(signals), canHit, holds,
                role == GroupRole.PRESSURE);
    }

    private boolean readySoon(MobSession session) {
        var choices = session.listedChoices();
        for (var index = 0; index < choices.length; index++) {
            var at = session.choiceReadyAt(index);
            if (at != Long.MIN_VALUE && at <= session.signalsAtTick() + 4) {
                return true;
            }
        }
        return choices.length == 0;
    }

    private void award(long tick, GroupCaps caps, int index, int score) {
        var session = members[index];
        var books = session.groupBooks();
        if (books == null || books.playerIndex() < 0) {
            return;
        }
        var seat = books.memberIndex();
        var player = books.playerIndex();
        if (attack.holding(player, seat, AttackPool.MELEE)) {
            return;
        }
        var unit = Math.max(1, session.profile().group().weight());
        if (attack.grant(player, seat, AttackPool.MELEE, tick, books.meleeCap(), unit).isEmpty()) {
            session.groupCopy().token(AttackPool.MELEE);
            return;
        }
        steal(tick, player, seat, score, caps.stealMarginMillis(), unit, session);
    }

    private void steal(long tick, int player, int seat, int score, int margin, int unit, MobSession challenger) {
        for (var offset = 0; offset < 4; offset++) {
            var holder = attack.holderAt(player, AttackPool.MELEE, offset);
            if (holder < 0 || attack.locked(player, holder, AttackPool.MELEE)) {
                continue;
            }
            var victim = memberBySeat(holder);
            var victimScore = victim == null ? 0 : score(victim, 0);
            if (score - victimScore <= margin) {
                continue;
            }
            if (attack.stealUnlocked(player, AttackPool.MELEE, holder, seat, tick, unit)) {
                if (victim != null) {
                    victim.groupCopy().clearToken(AttackPool.MELEE);
                }
                challenger.groupCopy().token(AttackPool.MELEE);
            }
            return;
        }
    }

    private MobSession memberBySeat(int seat) {
        return seat < 0 || seat >= slotted.length ? null : slotted[seat];
    }

    /**
     * Sends {@code PREDICTOR_ROLE_} at most once per 100 ticks and {@code PREDICTOR_PLAN_} when the plan changes.
     * The adapter drops a signal the mob does not handle.
     *
     * @param tick server tick
     * @param actions the cue port, or null
     */
    void cue(long tick, CombatActions actions) {
        if (actions == null || plan == null) {
            return;
        }
        var planCue = cuedPlan != plan;
        if (planCue) {
            cuedPlan = plan;
        }
        for (var index = 0; index < count; index++) {
            var session = members[index];
            var seat = stable(session);
            if (seat < 0) {
                continue;
            }
            var role = session.groupCopy().role().orElse(null);
            var due = roleCueAt[seat] == Long.MIN_VALUE || tick - roleCueAt[seat] >= 100;
            if (role != null && role != lastRole[seat] && due) {
                lastRole[seat] = role;
                roleCueAt[seat] = tick;
                actions.cue(session.key(), CueRequest.role(role));
            }
            if (planCue) {
                actions.cue(session.key(), CueRequest.plan(plan));
            }
        }
    }

    /**
     * One assignment per shared threat table. Children that inherit the table, and mobs without one, are counted
     * and not written.
     *
     * @param encounter the encounter
     * @param caps limits
     * @param sink the threat writer, or null
     * @param metrics counters
     * @param tick server tick
     */
    void assignTargets(Encounter encounter, GroupCaps caps, GroupAssignment sink, DecisionMetrics metrics, long tick) {
        if (sink == null || plan == null || mode(caps).rank() < GroupMode.COORDINATE.rank()) {
            return;
        }
        java.util.UUID target = null;
        if (leader >= 0) {
            target = members[leader].lastTargetId().orElse(null);
        }
        if (target == null) {
            return;
        }
        var tables = new int[count];
        var inherits = new boolean[count];
        for (var index = 0; index < count; index++) {
            tables[index] = members[index].threatTableGroup();
            inherits[index] = members[index].inheritsThreatTable();
        }
        var writers = SharedTableAssignment.writers(tables, inherits);
        var skipped = SharedTableAssignment.unavailable(tables, inherits);
        if (skipped > 0) {
            metrics.add(DecisionMetrics.GroupCounter.ASSIGNMENT_UNAVAILABLE, skipped);
        }
        var focus = GroupTargetFocus.of(encounter, members, count, target);
        var spread = TargetDistribution.of(plan) == TargetDistribution.SPREAD && focus.count() > 0;
        for (var index = 0; index < count; index++) {
            if (writers[index] < 0) {
                continue;
            }
            var session = members[index];
            var aim = target;
            if (focus.focus() != null) {
                aim = focus.focus();
            }
            if (spread) {
                aim = focus.idAt(index % focus.count());
            }
            if (session.entropyActive() && focus.weakest() != null) {
                aim = focus.weakest();
            }
            var onTarget = GroupTargetFocus.threatOf(session, aim);
            var magnitude = SharedTableAssignment.magnitude(session.tableTop(), onTarget,
                    caps.assignmentThreatMargin(), session.profile().threat().maxPerWindow());
            sink.apply(encounter, session, aim, magnitude, tick);
        }
    }

    private void finishRegroup() {
        if (plan != GroupPlan.FALL_BACK_AND_REGROUP) {
            regroupElapsed = 0;
            return;
        }
        if (RegroupPolicy.finished(rallyBlocks(), regroupElapsed)) {
            book.clear();
            plan = null;
            planEpoch++;
            regroupElapsed = 0;
            clearRoles();
            return;
        }
        regroupElapsed++;
    }

    private double rallyBlocks() {
        if (leader < 0 || !members[leader].anchorKnown()) {
            return Double.MAX_VALUE;
        }
        var originX = 0.0;
        var originZ = 0.0;
        var known = 0;
        for (var index = 0; index < count; index++) {
            if (!members[index].anchorKnown()) {
                continue;
            }
            originX += members[index].threatX();
            originZ += members[index].threatZ();
            known++;
        }
        if (known == 0) {
            return Double.MAX_VALUE;
        }
        var point = RegroupPolicy.rally(members[leader].anchorX(), members[leader].anchorZ(), originX / known,
                originZ / known);
        var furthest = 0.0;
        for (var index = 0; index < count; index++) {
            if (!members[index].anchorKnown()) {
                continue;
            }
            var distance = StrictMath.hypot(members[index].anchorX() - point[0], members[index].anchorZ() - point[1]);
            if (distance > furthest) {
                furthest = distance;
            }
        }
        return furthest;
    }

    private int[] squads(GroupCaps caps) {
        var declared = new String[count];
        var children = new boolean[count];
        var parent = new int[count];
        var owner = new int[count];
        for (var index = 0; index < count; index++) {
            declared[index] = members[index].profile().group().squad();
            children[index] = members[index].hasRelationChildren();
            parent[index] = indexOfId(members[index].relationParent());
            owner[index] = indexOfId(members[index].relationOwner());
        }
        return SquadPolicy.assign(declared, children, parent, owner, caps.maxSquads());
    }

    private int indexOfId(java.util.UUID id) {
        if (id == null) {
            return -1;
        }
        for (var index = 0; index < count; index++) {
            if (members[index].key().entityId().equals(id)) {
                return index;
            }
        }
        return -1;
    }

    private static int squadSizeOf(int[] squads, int index) {
        if (squads == null || index < 0 || index >= squads.length) {
            return 0;
        }
        var id = squads[index];
        var size = 0;
        for (var other : squads) {
            if (other == id) {
                size++;
            }
        }
        return size;
    }

    private int guardLine(GroupCaps caps, boolean sectorOn) {
        var sector = sectorOn ? caps.exitSectorDegrees() : 0;
        if (leader < 0 || !members[leader].anchorKnown()) {
            return SlotAssignment.guardAngle(0, sector, false);
        }
        var leaderSession = members[leader];
        var dx = leaderSession.anchorX() - leaderSession.threatX();
        var dz = leaderSession.anchorZ() - leaderSession.threatZ();
        if (dx == 0 && dz == 0) {
            return SlotAssignment.guardAngle(0, sector, false);
        }
        var bearing = (int) Math.round(StrictMath.toDegrees(StrictMath.atan2(-dx, dz)));
        var relative = Math.floorMod(bearing - leaderSession.lookDegrees(), 360);
        return SlotAssignment.guardAngle(relative, sector, dz < 0);
    }
}
