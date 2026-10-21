package akyrion.thepredict.adapter.server.execution;

import io.lumine.mythic.api.skills.SkillMetadata;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The tell flag is the reference MythicMobs reads, and a poise break sets it (M23.7). */
class CastInterruptTest {
    @Test
    void storeTerminatedHandsTheFlagToSkillMetadata() {
        var held = new AtomicReference<AtomicBoolean>();
        var metadata = (SkillMetadata) Proxy.newProxyInstance(SkillMetadata.class.getClassLoader(),
                new Class<?>[] {SkillMetadata.class}, (proxy, method, arguments) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "toString" -> "metadata";
                            case "hashCode" -> 1;
                            case "equals" -> proxy == arguments[0];
                            default -> null;
                        };
                    }
                    if (method.getName().equals("setTerminatedReference")) {
                        held.set((AtomicBoolean) arguments[0]);
                        return null;
                    }
                    if (method.getName().equals("isTerminated")) {
                        var flag = held.get();
                        return flag != null && flag.get();
                    }
                    return defaultValue(method.getReturnType());
                });
        var flag = new AtomicBoolean();
        MythicSkillExecutor.storeTerminated(metadata, flag);
        assertSame(flag, held.get());
        flag.set(true);
        assertTrue(metadata.isTerminated());
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == double.class) {
            return 0.0;
        }
        if (type == float.class) {
            return 0.0f;
        }
        return null;
    }
}
