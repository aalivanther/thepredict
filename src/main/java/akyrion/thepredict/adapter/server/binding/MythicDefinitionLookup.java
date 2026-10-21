package akyrion.thepredict.adapter.server.binding;

import io.lumine.mythic.api.mobs.MythicMob;
import io.lumine.mythic.api.skills.Skill;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Read-only view of the loaded MythicMobs definitions used while binding; replaced by doubles in tests. */
public interface MythicDefinitionLookup {
    /**
     * Finds one mob by its exact ID.
     *
     * @param id exact, case-sensitive MythicMobs mob ID
     * @return the mob, or empty when no mob has that exact ID
     */
    Optional<MythicMob> mob(String id);

    /**
     * Lists every loaded mob, to suggest IDs and to find enabled mobs without a profile.
     *
     * @return the loaded mobs
     */
    Collection<MythicMob> mobs();

    /**
     * Finds one skill by its exact ID.
     *
     * @param id exact, case-sensitive MythicMobs skill ID
     * @return the skill, or empty when no skill has that exact ID
     */
    Optional<Skill> skill(String id);

    /**
     * Lists the IDs of every loaded skill, to suggest a correctly capitalized ID.
     *
     * @return the loaded skill IDs
     */
    Collection<String> skillNames();

    /**
     * Converts the condition lines of one context with the MythicMobs skill manager.
     *
     * @param lines author condition lines
     * @return the converted conditions
     */
    NativeConditions conditions(List<String> lines);

    /**
     * A reading prepared by a double that cannot be a {@code MetaSkill} (M19.3). Empty in production: the binder then
     * reads a real metaskill, and leaves every other skill unread.
     *
     * @param skillId exact skill ID
     * @return the reading, if the double prepared one
     */
    default Optional<NativeSkillReader.Reading> preparedReading(String skillId) {
        return Optional.empty();
    }
}
