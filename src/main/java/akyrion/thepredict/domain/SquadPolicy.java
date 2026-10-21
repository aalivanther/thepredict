package akyrion.thepredict.domain;

/**
 * Squads of one encounter (M25). Declared names come first. A member with children in the encounter then heads
 * a squad, and the owner's children join it when the owner is a member. Everyone else is squad 0. Ties follow
 * join order. At most {@code group.maxSquads} squads are kept, in that order; the members of a later squad join
 * squad 0 (A5, DOC-09).
 */
public final class SquadPolicy {
    private SquadPolicy() {
    }

    /**
     * Assigns a squad id to each member.
     *
     * @param declared squad name, empty when the profile names none
     * @param hasChildren whether the member has a child inside the encounter
     * @param parent join index of the parent, or -1
     * @param owner join index of the owner, or -1
     * @return one id per member; 0 is the remainder
     */
    public static int[] assign(String[] declared, boolean[] hasChildren, int[] parent, int[] owner) {
        return assign(declared, hasChildren, parent, owner, Integer.MAX_VALUE);
    }

    /**
     * Assigns a squad id to each member, keeping at most {@code maxSquads} squads.
     *
     * @param declared squad name, empty when the profile names none
     * @param hasChildren whether the member has a child inside the encounter
     * @param parent join index of the parent, or -1
     * @param owner join index of the owner, or -1
     * @param maxSquads {@code group.maxSquads}, at least 1
     * @return one id per member, from 1 to {@code maxSquads}; 0 is the remainder
     */
    public static int[] assign(String[] declared, boolean[] hasChildren, int[] parent, int[] owner, int maxSquads) {
        var count = declared.length;
        var squad = new int[count];
        for (var index = 0; index < count; index++) {
            squad[index] = -1;
        }
        var names = new String[count];
        var named = 0;
        for (var index = 0; index < count; index++) {
            var name = declared[index] == null ? "" : declared[index];
            if (name.isEmpty()) {
                continue;
            }
            var id = -1;
            for (var prior = 0; prior < named; prior++) {
                if (name.equals(names[prior])) {
                    id = prior;
                    break;
                }
            }
            if (id < 0) {
                names[named] = name;
                id = named++;
            }
            squad[index] = id + 1;
        }
        var next = named + 1;
        for (var index = 0; index < count; index++) {
            if (!hasChildren[index]) {
                continue;
            }
            var id = squad[index];
            if (id < 0) {
                id = next++;
                squad[index] = id;
            }
            claim(squad, parent, index, id);
            if (owner[index] >= 0) {
                claim(squad, parent, owner[index], id);
            }
        }
        for (var index = 0; index < count; index++) {
            if (squad[index] < 0 || squad[index] > maxSquads) {
                squad[index] = 0;
            }
        }
        return squad;
    }

    private static void claim(int[] squad, int[] parent, int head, int id) {
        for (var child = 0; child < squad.length; child++) {
            if (squad[child] < 0 && parent[child] == head) {
                squad[child] = id;
            }
        }
    }
}
