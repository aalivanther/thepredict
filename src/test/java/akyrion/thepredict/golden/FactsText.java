package akyrion.thepredict.golden;

import akyrion.thepredict.domain.CombatFacts;
import akyrion.thepredict.domain.EncounterEvent;
import akyrion.thepredict.domain.MobFacts;
import akyrion.thepredict.domain.ParticipantFacts;
import akyrion.thepredict.domain.TargetRelation;

import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Deterministic text of the facts of a capture for the golden references of M17.0: every set and map is written in
 * sorted order, because the iteration order of the immutable collections of the JDK changes between runs. A field
 * added later that is absent (an empty species) writes nothing, so the text of a capture of players does not change.
 */
public final class FactsText {
    private FactsText() {
    }

    /**
     * The facts of one capture, one line per mob, participant and event.
     *
     * @param facts captured facts
     * @return the text, ending with a line break
     */
    public static String of(CombatFacts facts) {
        var text = new StringBuilder("facts encounter=").append(facts.encounter().value()).append(" tick=")
                .append(facts.captureTick()).append(" nanos=").append(facts.captureNanos()).append(" combatSeconds=")
                .append(facts.combatSeconds()).append('\n');
        for (var mob : facts.mobs()) {
            text.append(mob(mob)).append('\n');
        }
        for (var participant : facts.participants()) {
            text.append(participant(participant)).append('\n');
        }
        for (var event : facts.recentEvents()) {
            text.append(event(event)).append('\n');
        }
        return text.toString();
    }

    /**
     * One mob.
     *
     * @param mob facts of a mob
     * @return one line
     */
    public static String mob(MobFacts mob) {
        var contingencies = new TreeMap<String, String>();
        mob.contingencyEligible().forEach((situation, tactics) -> contingencies.put(situation.name(), sorted(tactics)));
        return "mob " + mob.mob().entityId() + "/" + mob.mob().generation() + " health=" + mob.health() + " target="
                + mob.target().map(FactsText::relation).orElse("none") + " contexts=" + sorted(mob.activeContexts())
                + " eligible=" + sorted(mob.eligibleTactics()) + " cues=" + sorted(mob.cues()) + " outOfReach="
                + sorted(mob.choicesOutOfReach()) + " contingencies=" + contingencies;
    }

    /**
     * One relation of a mob to its target.
     *
     * @param relation the relation
     * @return its text
     */
    public static String relation(TargetRelation relation) {
        return relation.target().text() + "[" + relation.distance() + "," + relation.trend() + ",reachable="
                + relation.reachable() + ",visible=" + relation.visible() + ",outside=" + relation.outsideTargetReach()
                + ",flankable=" + relation.flankable() + ",grouped=" + relation.grouped() + "]";
    }

    /**
     * One participant.
     *
     * @param participant facts of a participant
     * @return one line
     */
    public static String participant(ParticipantFacts participant) {
        return "participant " + participant.alias().text() + " health=" + participant.health() + " armor="
                + participant.armor() + " cues=" + sorted(participant.cues()) + " rank=" + participant.threatRank()
                + species(participant);
    }

    /**
     * One event of the history.
     *
     * @param event the event
     * @return one line
     */
    public static String event(EncounterEvent event) {
        return "event tick=" + event.tick() + " " + event.type() + " participant="
                + event.participant().map(alias -> alias.text()).orElse("none") + " mob="
                + event.mob().map(key -> key.entityId() + "/" + key.generation()).orElse("none");
    }

    /**
     * A collection in sorted order of its texts.
     *
     * @param values values
     * @return {@code [a, b]}
     */
    public static String sorted(Collection<?> values) {
        return values.stream().map(String::valueOf).sorted().collect(Collectors.joining(", ", "[", "]"));
    }

    /**
     * A map in sorted order of its keys.
     *
     * @param values values
     * @return {@code {a=1, b=2}}
     */
    public static String sorted(Map<?, ?> values) {
        var sorted = new TreeMap<String, String>();
        values.forEach((key, value) -> sorted.put(String.valueOf(key), String.valueOf(value)));
        return sorted.toString();
    }

    private static String species(ParticipantFacts participant) {
        return "";
    }
}
