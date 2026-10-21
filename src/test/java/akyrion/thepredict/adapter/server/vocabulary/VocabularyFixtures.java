package akyrion.thepredict.adapter.server.vocabulary;

import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.IntentView;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.engine.ServerClock;
import io.lumine.mythic.api.adapters.AbstractEntity;
import io.lumine.mythic.api.config.MythicLineConfig;
import io.lumine.mythic.api.skills.SkillCaster;
import io.lumine.mythic.api.skills.SkillMetadata;
import io.lumine.mythic.core.skills.placeholders.PlaceholderMeta;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.UUID;

/** Doubles of the MythicMobs interfaces the vocabulary reads; unexpected calls fail the test. */
final class VocabularyFixtures {
    private VocabularyFixtures() {
    }

    /** Clock fixed at a nanosecond instant and tick 0. */
    static ServerClock clockAt(long nanos) {
        return ServerClock.of(() -> 0L, () -> nanos);
    }

    /** View published by a session of generation 0 that expires at the given instant. */
    static IntentView view(ControlLevel level, Movement movement, TacticId tactic, long expiresAtNanos) {
        return new IntentView(0, level, movement, tactic, 0.8f, expiresAtNanos);
    }

    /** Entity whose only supported call is {@code getUniqueId}. */
    static AbstractEntity entity(UUID id) {
        return proxy(AbstractEntity.class, (instance, method, arguments) -> {
            if (method.getName().equals("getUniqueId")) {
                return id;
            }
            throw new UnsupportedOperationException(method.getName());
        });
    }

    /** Caster whose only supported call is {@code getEntity}; the entity may be {@code null}. */
    static SkillCaster caster(AbstractEntity entity) {
        return proxy(SkillCaster.class, (instance, method, arguments) -> {
            if (method.getName().equals("getEntity")) {
                return entity;
            }
            throw new UnsupportedOperationException(method.getName());
        });
    }

    /** Skill metadata whose only supported call is {@code getCaster}; the caster may be {@code null}. */
    static SkillMetadata metadata(SkillCaster caster) {
        return proxy(SkillMetadata.class, (instance, method, arguments) -> {
            if (method.getName().equals("getCaster")) {
                return caster;
            }
            throw new UnsupportedOperationException(method.getName());
        });
    }

    /** Placeholder metadata whose only supported call is {@code getCaster}; the caster may be {@code null}. */
    static PlaceholderMeta placeholderMeta(SkillCaster caster) {
        return proxy(PlaceholderMeta.class, (instance, method, arguments) -> {
            if (method.getName().equals("getCaster")) {
                return caster;
            }
            throw new UnsupportedOperationException(method.getName());
        });
    }

    /**
     * Line configuration that answers {@code getString(String[], String, String...)} from a map of keys,
     * returning the default when no key is present, like MythicMobs.
     */
    static MythicLineConfig line(Map<String, String> values) {
        return proxy(MythicLineConfig.class, (instance, method, arguments) -> {
            if (method.getName().equals("getString") && arguments.length == 3 && arguments[0] instanceof String[] keys) {
                for (var key : keys) {
                    if (values.containsKey(key)) {
                        return values.get(key);
                    }
                }
                return arguments[1];
            }
            throw new UnsupportedOperationException(method.getName());
        });
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
    }
}
