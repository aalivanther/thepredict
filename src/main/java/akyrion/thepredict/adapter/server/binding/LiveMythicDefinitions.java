package akyrion.thepredict.adapter.server.binding;

import io.lumine.mythic.api.mobs.MythicMob;
import io.lumine.mythic.api.skills.Skill;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.skills.SkillCondition;
import io.lumine.mythic.core.skills.conditions.InvalidCondition;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Reads the definitions currently loaded by MythicMobs Premium. Server thread only. */
final class LiveMythicDefinitions implements MythicDefinitionLookup {
    @Override
    public Optional<MythicMob> mob(String id) {
        return MythicBukkit.inst().getMobManager().getMythicMob(id);
    }

    @Override
    public Collection<MythicMob> mobs() {
        return MythicBukkit.inst().getMobManager().getMobTypes();
    }

    @Override
    public Optional<Skill> skill(String id) {
        return MythicBukkit.inst().getSkillManager().getSkill(id);
    }

    @Override
    public Collection<String> skillNames() {
        return MythicBukkit.inst().getSkillManager().getSkillNames();
    }

    @Override
    public NativeConditions conditions(List<String> lines) {
        var manager = MythicBukkit.inst().getSkillManager();
        var converted = new ArrayList<SkillCondition>();
        var invalid = new ArrayList<String>();
        for (var line : lines) {
            var conditions = manager.getConditions(List.of(line));
            if (conditions.isEmpty() || conditions.stream().anyMatch(InvalidCondition.class::isInstance)) {
                invalid.add(line);
            } else {
                converted.addAll(conditions);
            }
        }
        return new LiveNativeConditions(lines, converted, invalid);
    }
}
