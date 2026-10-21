package akyrion.thepredict.engine;

import akyrion.thepredict.domain.CounterMemory;
import akyrion.thepredict.domain.DifficultySample;
import akyrion.thepredict.domain.DifficultySection;
import akyrion.thepredict.domain.EncounterOutcomePolicy;
import akyrion.thepredict.domain.GroupMode;
import akyrion.thepredict.domain.PartyBand;
import akyrion.thepredict.domain.ProfileHash;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Builds one digest per profile when a group clock closes (M27.3, N-2, N-3). Below {@link GroupMode#FAIR} a group
 * emits counts and no difficulty sample, so the solo section stays untouched.
 */
public final class GroupFightClose {
    private GroupFightClose() {
    }

    /**
     * One mob of the group.
     *
     * @param profile profile name
     * @param disengaged the clock disengaged
     * @param mobDied the mob died
     * @param engagedAtPlayerDeath a living mob was engaged when the player died
     * @param killingBlow this mob landed the killing blow
     * @param damageShare share of this mob's max health that the target took away in the fight (M27.1)
     * @param maxHealth max health
     * @param counters counters of this mob
     */
    public record Member(String profile, boolean disengaged, boolean mobDied, boolean engagedAtPlayerDeath,
                         boolean killingBlow, double damageShare, double maxHealth, List<CounterMemory> counters) {
        /**
         * A mob with no counters (rule W5).
         *
         * @param profile profile name
         * @param disengaged the clock disengaged
         * @param mobDied the mob died
         * @param engagedAtPlayerDeath a living mob was engaged when the player died
         * @param killingBlow this mob landed the killing blow
         * @param damageShare share of this mob's max health that the target took away in the fight (M27.1)
         * @param maxHealth max health
         */
        public Member(String profile, boolean disengaged, boolean mobDied, boolean engagedAtPlayerDeath,
                      boolean killingBlow, double damageShare, double maxHealth) {
            this(profile, disengaged, mobDied, engagedAtPlayerDeath, killingBlow, damageShare, maxHealth, List.of());
        }

        /**
         * Copies the counters.
         */
        public Member {
            counters = List.copyOf(counters == null ? List.of() : counters);
        }
    }

    /**
     * The close.
     *
     * @param digests one per profile
     * @param anchor the anchor profile
     * @param anchorChanged whether the anchor moved after the open
     */
    public record Result(List<FightDigest> digests, String anchor, boolean anchorChanged) {
        /**
         * Copies the digests.
         */
        public Result {
            digests = List.copyOf(digests);
        }
    }

    /**
     * The anchor at close: the leader, or the profile with the most members, ties by the smaller FNV-1a.
     *
     * @param leader leader profile, empty when the group has no leader
     * @param profiles one name per member
     * @return the anchor name
     */
    public static String anchor(String leader, List<String> profiles) {
        if (!leader.isEmpty()) {
            return leader;
        }
        var counts = new LinkedHashMap<String, Integer>();
        for (var name : profiles) {
            counts.merge(name, 1, Integer::sum);
        }
        var best = "";
        var bestCount = -1;
        var bestHash = 0;
        for (var entry : counts.entrySet()) {
            var hash = ProfileHash.of(entry.getKey());
            var better = best.isEmpty() || entry.getValue() > bestCount
                    || (entry.getValue() == bestCount && Integer.compareUnsigned(hash, bestHash) < 0);
            if (better) {
                best = entry.getKey();
                bestCount = entry.getValue();
                bestHash = hash;
            }
        }
        return best;
    }

    /**
     * Digests for one encounter.
     *
     * @param subject the player
     * @param openAnchor anchor when the clock opened
     * @param closeAnchor anchor when the clock closed
     * @param members the mobs
     * @param playerDied whether the player died
     * @param mode the group mode
     * @param band the party band
     * @param endEpochMillis when the encounter ended
     * @param lnTtk log time to kill of the clock that owns the sample
     * @param rung the rung estimate
     * @param minDamageShareRatio share required for a win
     * @return the digests
     */
    public static Result close(UUID subject, String openAnchor, String closeAnchor, List<Member> members,
                               boolean playerDied, GroupMode mode, PartyBand band, long endEpochMillis, float lnTtk,
                               float rung, double minDamageShareRatio) {
        return close(subject, openAnchor, closeAnchor, members, playerDied, mode, band, endEpochMillis, lnTtk, rung,
                minDamageShareRatio, true);
    }

    /**
     * Digests for one encounter.
     *
     * @param subject the player
     * @param openAnchor anchor when the clock opened
     * @param closeAnchor anchor when the clock closed
     * @param members the mobs
     * @param playerDied whether the player died
     * @param mode the group mode
     * @param band the party band
     * @param endEpochMillis when the encounter ended
     * @param lnTtk log time to kill of the clock that owns the sample
     * @param rung the rung estimate
     * @param minDamageShareRatio share required for a win
     * @param attachSample whether the clock left a valid difficulty sample
     * @return the digests
     */
    public static Result close(UUID subject, String openAnchor, String closeAnchor, List<Member> members,
                               boolean playerDied, GroupMode mode, PartyBand band, long endEpochMillis, float lnTtk,
                               float rung, double minDamageShareRatio, boolean attachSample) {
        var names = new ArrayList<String>();
        var clocks = new ArrayList<EncounterOutcomePolicy.ClockClose>();
        for (var member : members) {
            names.add(member.profile());
            clocks.add(new EncounterOutcomePolicy.ClockClose(ProfileHash.of(member.profile()), member.disengaged(),
                    member.mobDied(), member.engagedAtPlayerDeath(), member.killingBlow(), member.damageShare(),
                    member.maxHealth()));
        }
        var anchor = closeAnchor.isEmpty() ? anchor(closeAnchor, names) : closeAnchor;
        var changed = !openAnchor.isEmpty() && !openAnchor.equals(anchor);
        var grouped = members.size() >= 2;
        var section = grouped ? DifficultySection.GROUP : DifficultySection.SOLO;
        var sampleAllowed = attachSample && (!grouped || mode.rank() >= GroupMode.FAIR.rank());
        var anchorHash = ProfileHash.of(anchor);
        var assignment = EncounterOutcomePolicy.assign(playerDied, clocks, minDamageShareRatio);
        var digests = new ArrayList<FightDigest>();
        for (var outcome : assignment.outcomes()) {
            var sampled = sampleAllowed && (!grouped || outcome.profileHash() == anchorHash);
            Optional<DifficultySample> sample = sampled
                    ? Optional.of(new DifficultySample(lnTtk, rung)) : Optional.empty();
            digests.add(new FightDigest(subject, outcome.profileHash(), outcome.outcome(), band, section, sample,
                    endEpochMillis, changed && outcome.profileHash() == anchorHash,
                    counters(members, outcome.profileHash())));
        }
        return new Result(digests, anchor, changed);
    }

    private static List<CounterMemory> counters(List<Member> members, int hash) {
        var landed = new float[25];
        var tries = new float[25];
        var seen = new boolean[25];
        for (var member : members) {
            if (ProfileHash.of(member.profile()) != hash) {
                continue;
            }
            for (var counter : member.counters()) {
                var pattern = counter.pattern();
                landed[pattern] += counter.landed();
                tries[pattern] += counter.tries();
                seen[pattern] = true;
            }
        }
        var list = new ArrayList<CounterMemory>();
        for (var pattern = 0; pattern < seen.length; pattern++) {
            if (seen[pattern]) {
                list.add(new CounterMemory(pattern, landed[pattern], tries[pattern]));
            }
        }
        return list;
    }
}
