package akyrion.thepredict.adapter.jev;

import akyrion.thepredict.domain.CombatFacts;
import akyrion.thepredict.domain.EnemyPatternFact;
import akyrion.thepredict.domain.EncounterEvent;
import akyrion.thepredict.domain.MobCue;
import akyrion.thepredict.domain.MobFacts;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.ParticipantAlias;
import akyrion.thepredict.domain.ParticipantFacts;
import akyrion.thepredict.domain.ParticipantKind;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.domain.TargetRelation;
import akyrion.thepredict.domain.ZoneFact;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure encoder from {@link CombatFacts} to the System One {@code state} (M08.2, M16.4): short English facts, mob
 * references {@code mob_1}… in the order of {@code facts.mobs()} and participant aliases in {@code participants}, never
 * UUIDs, names or positions. Reads no clock and no global configuration, and gives the same text for the same facts.
 * A mob cue {@code NO_LINE_OF_SIGHT} is not repeated when the target facts already say {@code no line of sight to
 * target}. The facts of a mob's target hold everything a question about it needs, so no fact of the target of an asked
 * mob requires reading another array (G5): the health, armor, threat rank and cues of the target participant are
 * repeated there with the subject {@code target}, and the mob facts say whether the mob is in better or worse shape
 * than its target when their health bands differ. Thread-safe: it holds only its limit.
 */
final class SemanticEncoder {
    static final String MOB_PREFIX = "mob_";

    private final int maxStateChars;

    SemanticEncoder(int maxStateChars) {
        if (maxStateChars < 1) {
            throw new IllegalArgumentException("maxStateChars must be positive");
        }
        this.maxStateChars = maxStateChars;
    }

    /**
     * Encodes the state, trimming it to {@code maxStateChars} if needed.
     *
     * @param facts captured facts
     * @param profiles profiles of the request; only the objectives of asked mobs are written
     * @param askedMobs mobs that at least one question asks about; they and their targets are never trimmed
     * @return the {@code state} object
     * @throws SystemOneRequestException if the state does not fit even after trimming
     */
    JsonObject encodeState(CombatFacts facts, Map<MobKey, TacticalProfile> profiles, Set<MobKey> askedMobs) {
        return trimmed(facts, profiles, askedMobs).render();
    }

    /**
     * Builds the draft of the state and trims it to {@code maxStateChars}; the request encoder renders it and reads the
     * final position of each entry for the paths of the instructions (M16.3).
     *
     * @param facts captured facts
     * @param profiles profiles of the request; only the objectives of asked mobs are written
     * @param askedMobs mobs that at least one question asks about; they and their targets are never trimmed
     * @return the trimmed draft
     * @throws SystemOneRequestException if the state does not fit even after trimming
     */
    StateDraft trimmed(CombatFacts facts, Map<MobKey, TacticalProfile> profiles, Set<MobKey> askedMobs) {
        var draft = draft(facts, profiles, askedMobs);
        while (draft.render().toString().length() > maxStateChars) {
            if (!draft.trimOnce()) {
                throw new SystemOneRequestException("state exceeds maxStateChars even after trimming");
            }
        }
        return draft;
    }

    /**
     * Assigns {@code mob_1}, {@code mob_2}… in the order of {@code facts.mobs()}.
     *
     * @param facts captured facts
     * @return reference of each mob, in capture order
     */
    static Map<MobKey, String> mobRefs(CombatFacts facts) {
        var refs = new LinkedHashMap<MobKey, String>();
        for (var mob : facts.mobs()) {
            refs.put(mob.mob(), MOB_PREFIX + (refs.size() + 1));
        }
        return refs;
    }

    static JsonArray array(List<String> texts) {
        var array = new JsonArray(texts.size());
        texts.forEach(array::add);
        return array;
    }

    private static StateDraft draft(CombatFacts facts, Map<MobKey, TacticalProfile> profiles, Set<MobKey> askedMobs) {
        var encounter = new JsonObject();
        encounter.addProperty("duration", SemanticPhrases.duration(facts.combatSeconds()));
        var entities = (int) facts.participants().stream().filter(participant -> participant.species().isPresent())
                .count();
        encounter.addProperty("headcount", SemanticPhrases.headcount(facts.participants().size() - entities, entities,
                facts.mobs().size()));
        var refs = mobRefs(facts);
        var protectedTargets = new HashSet<ParticipantAlias>();
        var mobs = mobEntries(facts, refs, profiles, askedMobs, protectedTargets);
        var participants = new ArrayList<StateDraft.ParticipantEntry>(facts.participants().size());
        for (var participant : facts.participants()) {
            participants.add(participant(participant, protectedTargets.contains(participant.alias())));
        }
        var events = new ArrayList<StateDraft.EventEntry>(facts.recentEvents().size());
        for (var index = facts.recentEvents().size() - 1; index >= 0; index--) {
            var event = facts.recentEvents().get(index);
            var relevant = event.mob().filter(askedMobs::contains).isPresent()
                    || event.participant().filter(protectedTargets::contains).isPresent();
            events.add(new StateDraft.EventEntry(event(event, refs), relevant));
        }
        return new StateDraft(encounter, mobs, participants, events);
    }

    /** The mob entries in capture order; the targets of the asked mobs are added to {@code protectedTargets}. */
    private static List<StateDraft.MobEntry> mobEntries(CombatFacts facts, Map<MobKey, String> refs,
                                                        Map<MobKey, TacticalProfile> profiles, Set<MobKey> askedMobs,
                                                        Set<ParticipantAlias> protectedTargets) {
        var byAlias = new LinkedHashMap<ParticipantAlias, ParticipantFacts>();
        facts.participants().forEach(participant -> byAlias.put(participant.alias(), participant));
        var mobs = new ArrayList<StateDraft.MobEntry>(facts.mobs().size());
        for (var mob : facts.mobs()) {
            var asked = askedMobs.contains(mob.mob());
            var profile = asked ? profiles.get(mob.mob()) : null;
            var target = mob.target().map(relation -> byAlias.get(relation.target()));
            mobs.add(new StateDraft.MobEntry(mob(mob, refs.get(mob.mob()), profile, target.orElse(null)), asked));
            if (asked) {
                mob.target().ifPresent(relation -> protectedTargets.add(relation.target()));
            }
        }
        return mobs;
    }

    private static JsonObject mob(MobFacts mob, String ref, TacticalProfile profile, ParticipantFacts target) {
        var json = new JsonObject();
        json.addProperty("ref", ref);
        if (profile != null) {
            json.addProperty("objective", profile.objective());
        }
        var facts = new ArrayList<String>();
        facts.add(SemanticPhrases.health(mob.health(), ref));
        if (target != null && target.health() != mob.health()) {
            facts.add(SemanticPhrases.relativeHealth(mob.health().compareTo(target.health()) > 0, ref));
        }
        var targetHidden = mob.target().map(relation -> !relation.visible()).orElse(false);
        for (var cue : ordered(mob.cues())) {
            if (!(targetHidden && cue == MobCue.NO_LINE_OF_SIGHT)) {
                facts.add(SemanticPhrases.mobCue(cue, ref));
            }
        }
        if (mob.target().isEmpty()) {
            facts.add(SemanticPhrases.noTarget(ref));
        }
        json.add("facts", array(facts));
        if (!mob.zoneFacts().isEmpty()) {
            var zone = new ArrayList<String>();
            var player = target != null && target.alias().kind() == ParticipantKind.PLAYER;
            var entropyDrop = mob.rawZoneBits() < 0L;
            for (var fact : mob.zoneFacts()) {
                if (fact == ZoneFact.TARGET_NEAR_DROP && player && !entropyDrop) {
                    continue;
                }
                zone.add(ref + " " + fact.phrase());
            }
            if (!zone.isEmpty()) {
                json.add("zone_facts", array(zone));
            }
        }
        mob.target().ifPresent(relation -> json.add("target", target(relation, ref, target)));
        return json;
    }

    private static JsonObject target(TargetRelation relation, String mobRef, ParticipantFacts participant) {
        var facts = new ArrayList<String>();
        facts.add(SemanticPhrases.targetIs(relation.target().text()));
        facts.add(SemanticPhrases.distance(relation.distance(), "target"));
        facts.add(SemanticPhrases.trend(relation.trend(), "target"));
        if (participant != null) {
            participantFacts(facts, participant);
        }
        if (!relation.reachable()) {
            facts.add(SemanticPhrases.unreachable());
        }
        if (!relation.visible()) {
            facts.add(SemanticPhrases.notVisible());
        }
        if (relation.outsideTargetReach()) {
            facts.add(SemanticPhrases.outsideTargetReach(mobRef));
        }
        if (relation.flankable()) {
            facts.add(SemanticPhrases.flankable());
        }
        if (relation.grouped()) {
            facts.add(SemanticPhrases.grouped());
        }
        var json = new JsonObject();
        json.addProperty("ref", relation.target().text());
        json.add("facts", array(facts));
        return json;
    }

    /** The facts of the target participant with the subject {@code target} (M16.4), its species first (M17.9). */
    private static void participantFacts(List<String> facts, ParticipantFacts participant) {
        participant.species().ifPresent(species -> facts.add(SemanticPhrases.species("target", species)));
        facts.add(SemanticPhrases.health(participant.health(), "target"));
        facts.add(SemanticPhrases.armor(participant.armor(), "target"));
        facts.add(SemanticPhrases.threat(participant.threatRank(), "target"));
        for (var cue : ordered(participant.cues())) {
            facts.add(SemanticPhrases.targetCue(cue, "target"));
        }
        patternPhrases(facts, participant, "target");
    }

    private static StateDraft.ParticipantEntry participant(ParticipantFacts participant, boolean protectedTarget) {
        var ref = participant.alias().text();
        var facts = new ArrayList<String>(4);
        participant.species().ifPresent(species -> facts.add(SemanticPhrases.species(ref, species)));
        facts.add(SemanticPhrases.health(participant.health(), ref));
        facts.add(SemanticPhrases.armor(participant.armor(), ref));
        facts.add(SemanticPhrases.threat(participant.threatRank(), ref));
        var cues = new ArrayList<String>();
        for (var cue : ordered(participant.cues())) {
            cues.add(SemanticPhrases.targetCue(cue, ref));
        }
        patternPhrases(facts, participant, ref);
        var memory = new ArrayList<String>();
        participant.memory().ifPresent(fact -> memory.add(fact.phrase(ref)));
        return new StateDraft.ParticipantEntry(ref, facts, cues, participant.threatRank(), protectedTarget, memory);
    }

    private static String event(EncounterEvent event, Map<MobKey, String> refs) {
        var participant = event.participant().map(ParticipantAlias::text).orElse("a player");
        var entity = event.participant().filter(alias -> alias.kind() == ParticipantKind.ENTITY).isPresent();
        var mob = event.mob().map(key -> refs.getOrDefault(key, "another mob")).orElse("a mob");
        return SemanticPhrases.event(event.type(), participant, mob, entity);
    }

    private static void patternPhrases(java.util.List<String> facts, ParticipantFacts participant, String ref) {
        if (participant.patterns().isEmpty()) {
            return;
        }
        for (var pattern : ordered(participant.patterns())) {
            var phrase = EnemyPatternFact.phrase(pattern);
            if (!phrase.isEmpty()) {
                facts.add(ref + " " + phrase);
            }
        }
    }

    private static <E extends Enum<E>> Set<E> ordered(Set<E> values) {
        return values.isEmpty() ? Set.of() : EnumSet.copyOf(values);
    }
}
