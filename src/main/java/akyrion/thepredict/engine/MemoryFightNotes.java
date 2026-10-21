package akyrion.thepredict.engine;

import akyrion.thepredict.domain.FightEventKind;
import akyrion.thepredict.domain.GroupMode;
import akyrion.thepredict.domain.MemorySettings;
import akyrion.thepredict.domain.PartyBand;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Close notes taken from the live fight clocks (M27.3). The group sample uses {@link GroupFightClock} only.
 */
final class MemoryFightNotes {

    /**
     * One digest per profile. Damage, disengage and time come from the clocks still held by the encounter; the death
     * of the player is the death of {@code subject} that the members noted, not the death of any target.
     *
     * @param encounter the encounter, still holding its members
     * @param subject the player
     * @param mode the group mode
     * @param millis when the encounter ended
     * @param settings the limits
     * @return the digests
     */
    List<FightDigest> digests(Encounter encounter, UUID subject, GroupMode mode, long millis,
                              MemorySettings settings) {
        var fought = fought(encounter);
        if (fought.isEmpty()) {
            return List.of();
        }
        var group = groupOf(fought);
        var solo = fought.getFirst().difficulty().clock();
        var rows = new ArrayList<GroupFightClose.Member>();
        var playerDied = false;
        for (var member : fought) {
            playerDied = playerDied || member.sawDeathOf(subject);
            rows.add(memberOf(member, member.difficulty().clock(), subject));
        }
        var close = closeAnchor(fought);
        var open = group != null && !group.openedAnchor().isEmpty() ? group.openedAnchor() : close;
        var grouped = rows.size() >= 2 && group != null;
        var ln = grouped ? (float) group.lnElapsed(subject) : (float) solo.lnElapsed();
        var rung = grouped ? group.rung() : solo.rung();
        var band = PartyBand.of(Math.max(1, encounter.participantCount()));
        return GroupFightClose.close(subject, open, close, rows, playerDied, mode, band, millis, ln, rung,
                settings.minDamageShareRatio(), attachSample(rows.size(), group, solo, mode)).digests();
    }

    private static List<MobSession> fought(Encounter encounter) {
        var fought = new ArrayList<MobSession>();
        for (var member : encounter.memberSessions()) {
            if (member.difficulty().clock().fought()) {
                fought.add(member);
            }
        }
        return fought;
    }

    private static GroupFightClock groupOf(List<MobSession> fought) {
        for (var member : fought) {
            if (member.groupClock().isPresent()) {
                return member.groupClock().get();
            }
        }
        return null;
    }

    private static String closeAnchor(List<MobSession> fought) {
        var leader = "";
        var names = new ArrayList<String>();
        for (var member : fought) {
            names.add(member.profile().id());
            var books = member.groupBooks();
            if (leader.isEmpty() && books != null && books.leader()) {
                leader = member.profile().id();
            }
        }
        return fought.size() >= 2 ? GroupFightClose.anchor(leader, names) : names.getFirst();
    }

    private static boolean attachSample(int members, GroupFightClock group, FightClock solo, GroupMode mode) {
        if (members >= 2) {
            if (group == null || mode.rank() < GroupMode.FAIR.rank()) {
                return false;
            }
            var kind = group.summary().map(summary -> summary.kind());
            return kind.isEmpty() || (kind.get() != FightEventKind.DISENGAGED
                    && kind.get() != FightEventKind.EVICTED);
        }
        var kind = solo.closedKind();
        if (kind.isPresent() && (kind.get() == FightEventKind.DISENGAGED || kind.get() == FightEventKind.EVICTED)) {
            return false;
        }
        return !solo.mobDiedWithoutEvidence();
    }

    /**
     * One mob of the digest of {@code subject}: it was engaged at the death of the player only when that player died
     * while this mob was alive and fighting it (EFO-06), never because another player of the encounter died. The note
     * is taken at the death, so a mob killed later in the encounter still counts as engaged then.
     */
    private static GroupFightClose.Member memberOf(MobSession member, FightClock clock, UUID subject) {
        var kind = clock.closedKind();
        var disengaged = kind.isPresent() && (kind.get() == FightEventKind.DISENGAGED
                || kind.get() == FightEventKind.GROUP_WIPED);
        return new GroupFightClose.Member(member.profile().id(), disengaged, clock.mobHasDied(),
                member.engagedAtDeathOf(subject), clock.killingBlow(), clock.takenShare(), clock.healthWeight(),
                member.exploitCounters());
    }
}
