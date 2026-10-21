package akyrion.thepredict.adapter.server.binding;

import io.lumine.mythic.core.skills.SkillCondition;

import java.util.List;

/**
 * The MythicMobs conditions of one profile context, converted with
 * {@code getSkillManager().getConditions(List<String>)} while binding (M03.3). M07 evaluates them.
 * Server thread only.
 */
public interface NativeConditions {
    /**
     * Returns the condition lines written by the author.
     *
     * @return the lines, in declaration order
     */
    List<String> lines();

    /**
     * Returns the converted MythicMobs conditions of the valid lines.
     *
     * @return the conditions, in declaration order
     */
    List<SkillCondition> conditions();

    /**
     * Returns the lines MythicMobs rejected while converting them.
     *
     * @return the invalid lines; empty when every line converted
     */
    List<String> invalidLines();

    /**
     * Returns the lines whose custom condition no plugin has resolved. MythicMobs resolves custom
     * conditions one tick after converting them, so this is meaningful only after that tick.
     *
     * @return the unresolved lines; empty when every condition resolved
     */
    List<String> unresolvedLines();
}
