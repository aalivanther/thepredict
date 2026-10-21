package akyrion.thepredict.engine;

import akyrion.thepredict.domain.MemoryAdmission;
import akyrion.thepredict.domain.MemoryAdmissionCause;
import akyrion.thepredict.domain.MemoryDecoded;
import akyrion.thepredict.domain.MemoryEntryState;
import akyrion.thepredict.domain.MemoryMode;
import akyrion.thepredict.domain.MemoryProfile;
import akyrion.thepredict.domain.MemorySettings;
import akyrion.thepredict.domain.PartyBand;
import akyrion.thepredict.domain.PatternSeed;
import akyrion.thepredict.domain.PlayerMemory;
import akyrion.thepredict.domain.PlayerMemoryCodec;
import akyrion.thepredict.domain.DifficultySection;
import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.GroupMode;
import akyrion.thepredict.domain.HintKind;
import akyrion.thepredict.domain.ProfileHash;

import akyrion.thepredict.domain.StyleMemory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * Server-thread owner of the cache, the digests, the loader and the admissions (M27.4). A load that arrives after an
 * admission does not change that fight. Record mode saves and does not decide.
 */
public final class MemoryHost {
    private final MemorySettings settings;
    private final MemoryCounters counters;
    private final MemoryCache cache;
    private final MemoryDigests digests;
    private final MemoryModeLoader loader = new MemoryModeLoader();
    private final AggregateCache aggregates;
    private final DecisionTap tap;
    private final Presence presence;
    private final LongSupplier epochMillis;
    private final Map<EncounterId, Map<UUID, Admitted>> open = new LinkedHashMap<>();
    private final Set<UUID> pendingReset = new LinkedHashSet<>();
    private final Map<UUID, StyleMemory> captured = new LinkedHashMap<>();
    private final MemoryFightNotes notes = new MemoryFightNotes();
    private Function<UUID, Optional<StyleMemory>> styles = subject -> Optional.empty();

    /**
     * Who is online and who is exempt. Both answers come from the server thread.
     */
    public interface Presence {
        /**
         * Whether the id is an online player.
         *
         * @param subject the id
         * @return {@code true} for an online player
         */
        boolean online(UUID subject);

        /**
         * Whether the player holds {@code thepredict.memory.exempt}.
         *
         * @param subject the player
         * @return {@code true} when the player is exempt
         */
        boolean exempt(UUID subject);
    }

    /**
     * Creates the host and listens for storage replies.
     *
     * @param settings the limits
     * @param counters the counters
     * @param aggregates the batch cache
     * @param tap where the admission line is written
     * @param presence online and exempt players
     * @param epochMillis wall clock
     */
    public MemoryHost(MemorySettings settings, MemoryCounters counters, AggregateCache aggregates, DecisionTap tap,
                      Presence presence, LongSupplier epochMillis) {
        this.settings = settings;
        this.counters = counters;
        this.cache = new MemoryCache(settings.cacheSubjects(), counters);
        this.digests = new MemoryDigests(settings, counters);
        this.aggregates = aggregates;
        this.tap = tap;
        this.presence = presence;
        this.epochMillis = epochMillis;
        aggregates.onSubjectMemory(this::loaded);
        aggregates.onMemoryApplied(this::applied);
        aggregates.onMemoryFailed(batch -> counters.add(MemoryCounters.Kind.BATCH_FAILED, 1));
        loader.offer(settings.mode(), false, settings);
    }

    /**
     * The cache.
     *
     * @return the cache
     */
    public MemoryCache cache() {
        return cache;
    }

    /**
     * The pending digests.
     *
     * @return the buffer
     */
    public MemoryDigests digests() {
        return digests;
    }

    /**
     * The mode loader.
     *
     * @return the loader
     */
    public MemoryModeLoader loader() {
        return loader;
    }

    /**
     * Style captured when an encounter closes. The function must not block.
     *
     * @param source style of a player, empty when patterns are off
     */
    public void useStyles(Function<UUID, Optional<StyleMemory>> source) {
        this.styles = source;
    }

    /**
     * Seed frozen at admission. Record, off, exempt and not-loaded admissions yield {@link PatternSeed#NONE}.
     *
     * @return the seed
     */
    public EnemyModelSeed modelSeed() {
        return new EnemyModelSeed() {
            @Override
            public PatternSeed seedFor(UUID participant) {
                for (var admitted : open.values()) {
                    var found = admitted.get(participant);
                    if (found != null) {
                        return found.seed();
                    }
                }
                return PatternSeed.NONE;
            }
        };
    }

    /**
     * Admissions frozen for fights still open, for {@code inspect} and the tests of the bound.
     *
     * @return how many players are admitted in open encounters
     */
    public int openAdmissions() {
        var total = 0;
        for (var admitted : open.values()) {
            total += admitted.size();
        }
        return total;
    }

    /**
     * Drains hints, times out loads and flushes digests. The caller keeps the hints that did not fit this tick.
     *
     * @param hints at most {@code maxHintsPerTick} hints
     * @param nanos monotonic time
     */
    public void drain(List<PrefetchHint> hints, long nanos) {
        if (!hints.isEmpty()) {
            hints(hints, nanos);
        }
        cache.timeoutPending(nanos, settings.loadTimeoutSeconds());
        loader.tick();
        if (digests.subjects() > 0) {
            flush();
        }
    }

    private void hints(List<PrefetchHint> hints, long nanos) {
        for (var hint : hints) {
            if (hint.kind() == HintKind.QUIT) {
                cache.mark(hint.subject(), false, false);
                continue;
            }
            if (cache.beginLoad(hint.subject(), nanos)) {
                aggregates.request(new AggregateQuery.SubjectMemoryQuery(hint.subject()));
            }
            cache.mark(hint.subject(), true, false);
        }
    }

    /**
     * Admits one player. A second call for the same open fight returns the frozen admission.
     *
     * @param encounter the encounter
     * @param subject the player
     * @param todayEpochDay the civil day
     * @return the admission
     */
    public MemoryAdmission admit(Encounter encounter, UUID subject, long todayEpochDay) {
        var admitted = open.get(encounter.id());
        var existing = admitted == null ? null : admitted.get(subject);
        if (existing != null) {
            return existing.admission();
        }
        if (!presence.online(subject)) {
            return bare(MemoryAdmissionCause.COLD);
        }
        var held = admissionBytes(subject);
        var admission = fresh(encounter, subject, todayEpochDay, held);
        open.computeIfAbsent(encounter.id(), id -> new LinkedHashMap<>()).put(subject, new Admitted(admission,
                seedOf(admission, held.bytes(), held.present(), subject, todayEpochDay)));
        cache.mark(subject, true, true);
        tap.memoryAdmitted(encounter.id(), subject, admission);
        return admission;
    }

    /**
     * A new admission with the mode of the encounter's {@code Memory} sections (E-2): the lowest of the global mode and
     * of each profile, and without the history phrase unless every profile shows it.
     */
    private MemoryAdmission fresh(Encounter encounter, UUID subject, long todayEpochDay, AdmissionBytes held) {
        var names = profileNames(encounter);
        var profile = names.isEmpty() ? "" : names.getFirst();
        var section = encounter.memberSessions().size() >= 2 ? DifficultySection.GROUP : DifficultySection.SOLO;
        var band = PartyBand.of(Math.max(1, players(encounter)));
        var memory = memorySection(encounter);
        var mode = memory.effective(loader.active());
        var admission = MemoryAdmissionPolicy.admit(mode, presence.exempt(subject), held.state(), held.bytes(),
                held.present(), loader.unverified(), profile, names.size() > 1, rung(encounter), todayEpochDay, settings,
                subject, counters, section, band);
        return admission.fact().isPresent() && !memory.factShown(mode) ? admission.withoutFact() : admission;
    }

    /**
     * Closes the encounter for every player admitted to it, whether or not the capture still lists that player as a
     * participant (EFO-13): a player who died, left or was dropped before the close still gets its digests and its
     * style, and no admission outlives its encounter. Digests are queued unless the player is exempt or the mode is
     * off, globally or for the profiles of the encounter as its admission froze it (E-2).
     *
     * @param encounter the encounter, still holding its members
     * @param tick the server tick
     * @param mode the group mode
     */
    public void closeEncounter(Encounter encounter, long tick, GroupMode mode) {
        var admitted = open.remove(encounter.id());
        if (admitted == null) {
            return;
        }
        var millis = epochMillis.getAsLong();
        var off = loader.active() == MemoryMode.OFF;
        for (var entry : admitted.entrySet()) {
            var subject = entry.getKey();
            if (off || entry.getValue().admission().mode() == MemoryMode.OFF || presence.exempt(subject)) {
                continue;
            }
            for (var digest : notes.digests(encounter, subject, mode, millis, settings)) {
                digests.add(digest);
            }
            styles.apply(subject).ifPresent(style -> captured.put(subject, style));
        }
    }

    /**
     * Drops pending digests and queues a delete behind any batch already in flight.
     *
     * @param subject the player
     */
    public void prepareReset(UUID subject) {
        digests.clear(subject);
        aggregates.dropMemory(subject);
        pendingReset.add(subject);
        cache.remove(subject);
        aggregates.acceptMemory(List.of(new PlayerMemoryDelta(subject, new byte[0], 0, epochMillis.getAsLong(), true)));
    }

    private void loaded(StorageReply.SubjectMemoryLoaded reply) {
        var bytes = reply.record().orElse(new byte[0]);
        cache.loaded(reply.subject(), bytes, reply.record().isPresent(), reply.newer(), reply.completedAtNanos(),
                reply.appliedThroughBatch());
    }

    private void applied(StorageReply.MemoryApplied applied) {
        for (var entry : applied.records().entrySet()) {
            var subject = entry.getKey();
            var bytes = entry.getValue();
            if (pendingReset.contains(subject)) {
                cache.remove(subject);
                if (bytes.length == 0) {
                    pendingReset.remove(subject);
                }
                continue;
            }
            install(subject, bytes, applied.completedAtNanos(), applied.batchId());
        }
    }

    private void install(UUID subject, byte[] bytes, long nanos, long batchId) {
        if (bytes.length == 0) {
            cache.remove(subject);
            return;
        }
        var decoded = PlayerMemoryCodec.decode(bytes);
        if (decoded instanceof MemoryDecoded.Newer) {
            cache.loaded(subject, bytes, false, true, nanos, batchId);
        } else if (decoded instanceof MemoryDecoded.Corrupt) {
            counters.add(MemoryCounters.Kind.RECORD_CORRUPT, 1);
        } else {
            cache.loaded(subject, bytes, true, false, nanos, batchId);
        }
    }

    private AdmissionBytes admissionBytes(UUID subject) {
        var slot = cache.slot(subject);
        var state = slot.map(MemoryCache.Slot::state).orElse(MemoryEntryState.ABSENT);
        var style = captured.get(subject);
        var viewed = style == null ? cache.view(subject, aggregates.batches(), digests.pending(subject), settings)
                : cache.view(subject, aggregates.batches(), digests.pending(subject), style, settings);
        var rawPresent = slot.isPresent() && slot.get().record().isPresent()
                && (state == MemoryEntryState.LOADED || state == MemoryEntryState.READ_ONLY);
        var raw = rawPresent ? slot.get().record().orElse(new byte[0]) : new byte[0];
        var base = rawPresent ? ready(raw) : PlayerMemory.empty();
        if (state != MemoryEntryState.READ_ONLY && !viewed.equals(base)) {
            var encoded = PlayerMemoryCodec.encode(viewed, settings.maxProfilesPerPlayer()).bytes();
            var usable = state == MemoryEntryState.LOADED ? state : MemoryEntryState.LOADED;
            return new AdmissionBytes(usable, true, encoded);
        }
        return new AdmissionBytes(state, rawPresent, raw);
    }

    private static PlayerMemory ready(byte[] raw) {
        var decoded = PlayerMemoryCodec.decode(raw);
        return decoded instanceof MemoryDecoded.Ready found ? found.memory() : PlayerMemory.empty();
    }

    private record AdmissionBytes(MemoryEntryState state, boolean present, byte[] bytes) {
    }

    /** The admission frozen for one player of one open encounter, and the seed it froze. */
    private record Admitted(MemoryAdmission admission, PatternSeed seed) {
    }

    private void flush() {
        var taken = digests.take(settings.maxSubjectsPerBatch());
        if (taken.isEmpty()) {
            return;
        }
        var grouped = new LinkedHashMap<UUID, List<FightDigest>>();
        for (var digest : taken) {
            grouped.computeIfAbsent(digest.subject(), id -> new ArrayList<>()).add(digest);
        }
        var rows = new ArrayList<PlayerMemoryDelta>();
        var now = epochMillis.getAsLong();
        for (var entry : grouped.entrySet()) {
            if (pendingReset.contains(entry.getKey())) {
                continue;
            }
            var day = Math.max(0L, now / 86_400_000L);
            var fold = new MemoryFold(entry.getValue(), Optional.ofNullable(captured.remove(entry.getKey())), day,
                    settings);
            rows.add(new PlayerMemoryDelta(entry.getKey(), new byte[0], 0, now, false, Optional.of(fold)));
        }
        if (!rows.isEmpty()) {
            aggregates.acceptMemory(rows);
        }
    }

    private PatternSeed seedOf(MemoryAdmission admission, byte[] record, boolean present, UUID subject, long today) {
        if (!present || admission.mode() == MemoryMode.RECORD || admission.mode() == MemoryMode.OFF
                || admission.cause() == MemoryAdmissionCause.NOT_LOADED
                || admission.cause() == MemoryAdmissionCause.EXEMPT
                || admission.cause() == MemoryAdmissionCause.UNVERIFIED
                || admission.cause() == MemoryAdmissionCause.MODE_RECORD) {
            return PatternSeed.NONE;
        }
        var decoded = PlayerMemoryCodec.decode(record);
        if (!(decoded instanceof MemoryDecoded.Ready ready)) {
            return PatternSeed.NONE;
        }
        return new SubjectEnemyModelSeed(ready.memory(), settings, today).seedFor(subject);
    }

    private static MemoryAdmission bare(MemoryAdmissionCause cause) {
        return new MemoryAdmission(cause, 0, Optional.empty(), Optional.empty(), 0, 0.0, MemoryMode.RECORD);
    }

    /**
     * The {@code Memory} section of the encounter's profiles (M27, E-2): the lowest mode any member asks for, and the
     * history phrase only when every member allows it. An encounter without members reads the default section.
     */
    private static MemoryProfile memorySection(Encounter encounter) {
        Optional<MemoryMode> mode = Optional.empty();
        var facts = true;
        for (var member : encounter.memberSessions()) {
            var section = member.profile().memory();
            if (section.mode().isPresent()) {
                mode = Optional.of(mode.map(section.mode().get()::min).orElse(section.mode().get()));
            }
            facts = facts && section.facts();
        }
        return mode.isEmpty() && facts ? MemoryProfile.DEFAULT : new MemoryProfile(mode, facts);
    }

    private static List<String> profileNames(Encounter encounter) {
        var names = new ArrayList<String>();
        for (var member : encounter.memberSessions()) {
            var name = member.profile().id();
            if (!names.contains(name)) {
                names.add(name);
            }
        }
        return names;
    }

    private static int players(Encounter encounter) {
        return encounter.participantCount();
    }

    private static int rung(Encounter encounter) {
        for (var member : encounter.memberSessions()) {
            return member.difficulty().rung();
        }
        return 8;
    }

    static int anchorHash(String name) {
        return ProfileHash.of(name);
    }
}
