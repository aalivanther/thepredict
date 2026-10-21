package akyrion.thepredict.adapter.server.duel;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Rounds and arms of a duel recording (M18.3, M18.4). Package-private; pure; server thread only.
 *
 * <p>A round starts with the first sample that sees a living duelist of each side and ends when one side has none
 * left: a death gives the round to the other side; a removal ({@code DuelTimeout}, {@code /mm mobs kill}) is a draw. The
 * arm comes from the kind of each side at the start ({@code tp} with a bound ThePredict profile, {@code classic}
 * without): A is red {@code tp} against blue {@code classic}, B the reverse, C both {@code tp} and D both
 * {@code classic}. The sides are swapped when red appears beyond the centre on the {@code z} axis, the spawn point of
 * blue in the script. At most {@link #MAX_TRACKED} living duelists are followed.
 */
final class DuelRounds {
    /** Most living duelists followed at once. */
    static final int MAX_TRACKED = DuelScene.MAX_DUELISTS;
    /** Arm text between rounds or before the first. */
    static final String NO_ARM = "-";

    /** Side of a duelist. */
    enum Side {
        RED, BLUE;

        String text() {
            return name().toLowerCase(Locale.ROOT);
        }

        Side rival() {
            return this == RED ? BLUE : RED;
        }
    }

    /** Who decides for a duelist. */
    enum Kind {
        TP, CLASSIC;

        String text() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** One followed duelist with its spawn point. */
    record Duelist(UUID id, Side side, Kind kind, double spawnZ) {
        Duelist {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(side, "side");
            Objects.requireNonNull(kind, "kind");
        }
    }

    /** A started round. */
    record Round(int number, String arm, boolean spawnSwapped, Duelist red, Duelist blue, long startTick) {
    }

    /** A finished round with its totals, one row of {@code rounds.csv}. */
    record Ended(Round round, long endTick, String winner, String endReason, double redDealt, double blueDealt,
                 int redHits, int blueHits, int requests, long inputTokens) {
        String row() {
            return DuelRows.csv(round.number(), round.arm(), round.spawnSwapped(), round.red().id(),
                    round.red().kind().text(), round.blue().id(), round.blue().kind().text(), round.startTick(),
                    endTick, winner, endReason, redDealt, blueDealt, redHits, blueHits, requests, inputTokens);
        }

        long ticks() {
            return endTick - round.startTick();
        }
    }

    private final double centerZ;
    private final Map<UUID, Duelist> alive = new LinkedHashMap<>();
    private Optional<Round> current = Optional.empty();
    private int started;
    private String lastArm = NO_ARM;
    private double redDealt;
    private double blueDealt;
    private int redHits;
    private int blueHits;
    private int requests;
    private long inputTokens;

    DuelRounds(double centerZ) {
        this.centerZ = centerZ;
    }

    /** Follows a new living duelist; {@code false} when it is followed already or {@link #MAX_TRACKED} are. */
    boolean follow(UUID id, Side side, Kind kind, double z) {
        if (alive.containsKey(id) || alive.size() >= MAX_TRACKED) {
            return false;
        }
        alive.put(id, new Duelist(id, side, kind, z));
        return true;
    }

    /** The followed living duelist with this UUID. */
    Optional<Duelist> duelist(UUID id) {
        return Optional.ofNullable(alive.get(id));
    }

    /** Living duelists followed now. */
    int tracked() {
        return alive.size();
    }

    /** Starts a round when none runs and each side has a living duelist; returns it. */
    Optional<Round> tryStart(long tick) {
        if (current.isPresent()) {
            return Optional.empty();
        }
        var red = first(Side.RED);
        var blue = first(Side.BLUE);
        if (red.isEmpty() || blue.isEmpty()) {
            return Optional.empty();
        }
        started++;
        var arm = arm(red.get().kind(), blue.get().kind());
        var round = new Round(started, arm, red.get().spawnZ() > centerZ, red.get(), blue.get(), tick);
        current = Optional.of(round);
        lastArm = arm;
        redDealt = 0;
        blueDealt = 0;
        redHits = 0;
        blueHits = 0;
        requests = 0;
        inputTokens = 0;
        return current;
    }

    /** A followed duelist died or was removed; returns the round it ends, if any. */
    Optional<Ended> gone(UUID id, boolean died, long tick) {
        var duelist = alive.remove(id);
        if (duelist == null || current.isEmpty() || first(duelist.side()).isPresent()) {
            return Optional.empty();
        }
        var round = current.get();
        current = Optional.empty();
        var winner = died ? duelist.side().rival().text() : "draw";
        return Optional.of(new Ended(round, tick, winner, died ? "death" : "removed", redDealt, blueDealt, redHits,
                blueHits, requests, inputTokens));
    }

    /** Ends a running round as {@code unfinished} because the recording stops; returns it, if one ran. */
    Optional<Ended> abort(long tick) {
        if (current.isEmpty()) {
            return Optional.empty();
        }
        var round = current.get();
        current = Optional.empty();
        return Optional.of(new Ended(round, tick, "unfinished", "stopped", redDealt, blueDealt, redHits, blueHits,
                requests, inputTokens));
    }

    /** A hit of a followed duelist on a rival during the round, with its damage as a fraction of maximum health. */
    void hit(Side attacker, double healthFraction) {
        if (current.isEmpty()) {
            return;
        }
        if (attacker == Side.RED) {
            redDealt += healthFraction;
            redHits++;
        } else {
            blueDealt += healthFraction;
            blueHits++;
        }
    }

    /** A request sent during the round. */
    void request() {
        if (current.isPresent()) {
            requests++;
        }
    }

    /** Input tokens of an answer received during the round. */
    void tokens(long tokens) {
        if (current.isPresent()) {
            inputTokens += tokens;
        }
    }

    /** Number of the running round, or of the last one between rounds; 0 before the first. */
    int round() {
        return current.map(Round::number).orElse(started);
    }

    /** Arm of the running round, or of the last one between rounds; {@link #NO_ARM} before the first. */
    String arm() {
        return lastArm;
    }

    /** Whether a round runs now. */
    boolean running() {
        return current.isPresent();
    }

    /** Rounds started. */
    int started() {
        return started;
    }

    private Optional<Duelist> first(Side side) {
        return alive.values().stream().filter(duelist -> duelist.side() == side).findFirst();
    }

    /** The arm of M18.3 from the kind of each side. */
    static String arm(Kind red, Kind blue) {
        if (red == Kind.TP) {
            return blue == Kind.TP ? "C" : "A";
        }
        return blue == Kind.TP ? "B" : "D";
    }
}
