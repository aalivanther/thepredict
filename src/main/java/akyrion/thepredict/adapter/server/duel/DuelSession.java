package akyrion.thepredict.adapter.server.duel;

import akyrion.thepredict.domain.ChoiceKind;
import akyrion.thepredict.domain.PurposeRules;
import akyrion.thepredict.domain.SkillPurpose;
import akyrion.thepredict.domain.SkillRole;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.engine.DuelRecording;
import akyrion.thepredict.engine.IntentBoard;
import akyrion.thepredict.engine.ServerClock;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * The state of one duel recording (M18.4): its request, writer, rounds, samples and totals, and what its tap and its
 * combat log share: the stamp of each row, the side and kind of each duelist and the relevance of a mob. A mob is
 * relevant when it is followed or its MythicMobs faction is one of the two sides, both O(1) lookups by UUID; the
 * recording never writes about anything else. Package-private; server thread only.
 */
final class DuelSession {
    private final DuelRecording.DuelRequest request;
    private final Path directory;
    private final DuelWriter writer;
    private final ServerClock clock;
    private final DuelScene scene;
    private final DuelRounds rounds;
    private final DuelSummary summary = new DuelSummary();
    private final DuelSampler sampler;
    private final Set<String> counted;
    private final Set<String> damaging;
    private final Set<String> guards;
    private final Set<String> known;
    private final long startNanos;
    private final long startTick;

    DuelSession(DuelRecording.DuelRequest request, Path directory, DuelWriter writer, ServerClock clock,
                DuelScene scene, IntentBoard board) {
        this.request = Objects.requireNonNull(request, "request");
        this.directory = Objects.requireNonNull(directory, "directory");
        this.writer = Objects.requireNonNull(writer, "writer");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.scene = Objects.requireNonNull(scene, "scene");
        this.rounds = new DuelRounds(request.centerZ());
        this.sampler = new DuelSampler(rounds, board, this::row, summary);
        var profiles = scene.profiles();
        this.counted = request.countedSkills().isEmpty() ? defaultSkills(profiles, request) : request.countedSkills();
        this.damaging = skills(profiles, false);
        this.guards = skills(profiles, true);
        var all = new TreeSet<String>();
        profiles.forEach(profile -> profile.choices().values().forEach(choice -> choice.mythicSkillId()
                .ifPresent(all::add)));
        this.known = Set.copyOf(all);
        this.startNanos = clock.nanoTime();
        this.startTick = clock.currentTick();
    }

    /**
     * The skills counted by default: the {@code MYTHIC_SKILL} choices of the bound profiles whose
     * {@code Participants.Entities.Allow} names either faction ({@code faction:<name>}, ignoring case).
     */
    static Set<String> defaultSkills(Collection<TacticalProfile> profiles, DuelRecording.DuelRequest request) {
        var skills = new TreeSet<String>();
        for (var profile : profiles) {
            var admitted = profile.participants().entityAllow().stream().map(rule -> rule.toLowerCase(Locale.ROOT))
                    .anyMatch(rule -> rule.equals("faction:" + request.sideA().toLowerCase(Locale.ROOT))
                            || rule.equals("faction:" + request.sideB().toLowerCase(Locale.ROOT)));
            if (admitted) {
                profile.choices().values().stream().filter(choice -> choice.kind() == ChoiceKind.MYTHIC_SKILL)
                        .forEach(choice -> choice.mythicSkillId().ifPresent(skills::add));
            }
        }
        return Set.copyOf(skills);
    }

    /** Skills of the bound profiles that deal damage, or that defend when {@code defense} is set. */
    private static Set<String> skills(Collection<TacticalProfile> profiles, boolean defense) {
        var skills = new TreeSet<String>();
        for (var profile : profiles) {
            for (var choice : profile.choices().values()) {
                var purpose = choice.purpose();
                var matches = defense
                        ? purpose.filter(found -> found.roles().contains(SkillRole.DEFENSE)).isPresent()
                        : purpose.map(SkillPurpose::damaging).orElse(PurposeRules.UNTYPED_DEFAULTS.damaging());
                if (matches) {
                    choice.mythicSkillId().ifPresent(skills::add);
                }
            }
        }
        return Set.copyOf(skills);
    }

    /** Takes one sample: follows the new duelists, starts a round when both sides are there, writes the rows. */
    void sample(long tick) {
        var readings = scene.duelists(request.worldId(), request.centerX(), request.centerY(), request.centerZ(),
                request.radiusBlocks(), request.sideA(), request.sideB());
        for (var reading : readings) {
            side(reading.faction()).ifPresent(side -> rounds.follow(reading.id(), side,
                    scene.profile(reading.id()).isPresent() ? DuelRounds.Kind.TP : DuelRounds.Kind.CLASSIC,
                    reading.z()));
        }
        rounds.tryStart(tick);
        sampler.sample(readings, tick);
    }

    /** A followed duelist died or was removed: the round may end, and its open retreat is written. */
    void gone(UUID id, boolean died, long tick) {
        if (rounds.duelist(id).isEmpty()) {
            return;
        }
        sampler.forget(id, tick);
        rounds.gone(id, died, tick).ifPresent(ended -> {
            row(DuelFile.ROUNDS, ended.row());
            summary.ended(ended);
        });
    }

    /** Writes the round still running when the recording stops, as {@code unfinished}, in both files. */
    void unfinished(long tick) {
        rounds.abort(tick).ifPresent(ended -> {
            row(DuelFile.ROUNDS, ended.row());
            summary.ended(ended);
        });
    }

    /** The side of a faction, compared ignoring case as MythicMobs compares factions. */
    Optional<DuelRounds.Side> side(String faction) {
        if (faction.equalsIgnoreCase(request.sideA())) {
            return Optional.of(DuelRounds.Side.RED);
        }
        return faction.equalsIgnoreCase(request.sideB()) ? Optional.of(DuelRounds.Side.BLUE) : Optional.empty();
    }

    /** The side of a mob: followed, or read from its faction. */
    Optional<DuelRounds.Side> sideOf(UUID id) {
        var followed = rounds.duelist(id);
        return followed.isPresent() ? followed.map(DuelRounds.Duelist::side) : scene.faction(id).flatMap(this::side);
    }

    /** Whether the recording writes about a mob: it is followed or of one of the two factions. */
    boolean relevant(UUID id) {
        return rounds.duelist(id).isPresent() || scene.faction(id).flatMap(this::side).isPresent();
    }

    /** The kind of a mob, {@code tp} with a bound profile. */
    DuelRounds.Kind kindOf(UUID id) {
        return rounds.duelist(id).map(DuelRounds.Duelist::kind).orElseGet(() -> scene.profile(id).isPresent()
                ? DuelRounds.Kind.TP : DuelRounds.Kind.CLASSIC);
    }

    /** The stamp of a line written now. */
    DuelDecisionLines.Stamp stamp() {
        return new DuelDecisionLines.Stamp(clock.nanoTime(), clock.currentTick(), rounds.round(), rounds.arm());
    }

    /** Queues one row; a full queue drops it and counts it. */
    void row(DuelFile file, String line) {
        writer.offer(file, line);
    }

    /** The text of {@code summary.md}. */
    String summaryText(String stoppedBy) {
        var header = new ArrayList<String>(List.of("# Duel recording `" + request.label() + "`", "",
                "- stopped_by: " + stoppedBy, "- folder: `" + directory + "`",
                "- arena: world " + request.worldId() + ", centre " + DuelRows.number(request.centerX()) + " "
                        + DuelRows.number(request.centerY()) + " " + DuelRows.number(request.centerZ()) + ", radius "
                        + DuelRows.number(request.radiusBlocks()),
                "- sides: red = " + request.sideA() + ", blue = " + request.sideB(),
                "- counted skills: " + String.join(", ", new TreeSet<>(counted)),
                "- rounds: " + rounds.started() + "; ticks " + startTick + " to " + clock.currentTick() + "; "
                        + DuelRows.number((clock.nanoTime() - startNanos) / 1e9) + " s",
                "- rows dropped (queue full): " + writer.dropped() + "; rows not written (size limit): "
                        + writer.skippedByLimit() + "; bytes written before the summary: " + writer.bytesWritten(),
                "- the Wilson intervals count a draw as half a win of each side; with 20 rounds a 95 % interval spans "
                        + "about 22 points on each side, so only large differences show (M18.3)"));
        return summary.render(header);
    }

    DuelRecording.DuelRequest request() {
        return request;
    }

    Path directory() {
        return directory;
    }

    DuelWriter writer() {
        return writer;
    }

    DuelRounds rounds() {
        return rounds;
    }

    DuelSummary summary() {
        return summary;
    }

    DuelSampler sampler() {
        return sampler;
    }

    DuelScene scene() {
        return scene;
    }

    Set<String> counted() {
        return counted;
    }

    /** Whether a skill deals damage: a bound profile declares it so, or no bound profile knows it. */
    boolean damaging(String skill) {
        return damaging.contains(skill) || !known.contains(skill);
    }

    boolean guard(String skill) {
        return guards.contains(skill);
    }

    long startNanos() {
        return startNanos;
    }
}
