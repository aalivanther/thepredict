package akyrion.thepredict.golden;

import io.papermc.paper.datacomponent.DataComponentType;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.key.Key;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry access of the tests, found by {@code ServiceLoader} as Paper finds the server's own. Two registries answer,
 * with one stable double per key: the attributes, so {@code Attribute.MAX_HEALTH} and the other attribute constants
 * load without a server, and the data component types, so {@code DataComponentTypes} loads and an item double can
 * answer {@code getData} and {@code hasData} by type. A component type double is both valued and non-valued, because
 * {@code DataComponentTypes} checks the kind of each constant when it loads; the tests only compare them by identity.
 * Every other registry still refuses each lookup. The listener references of M17.0 need the attributes to compute the
 * health fraction of a hit (M16.8) through the real listener; the item reads of M17.4 need the component types.
 */
public final class TestRegistryAccess implements RegistryAccess {
    private static final Map<String, Attribute> ATTRIBUTES = new ConcurrentHashMap<>();
    private static final Map<String, DataComponentType> COMPONENTS = new ConcurrentHashMap<>();

    /** Creates the access; {@code ServiceLoader} needs a public constructor without arguments. */
    public TestRegistryAccess() {
    }

    /** {@inheritDoc} */
    @Override
    @Deprecated(since = "1.20.6", forRemoval = true)
    @SuppressWarnings({"unchecked", "removal"})
    public <T extends Keyed> Registry<T> getRegistry(Class<T> type) {
        return (Registry<T>) refusing(type.getSimpleName());
    }

    /** {@inheritDoc} */
    @Override
    @SuppressWarnings("unchecked")
    public <T extends Keyed> Registry<T> getRegistry(RegistryKey<T> registryKey) {
        var attributes = registryKey == RegistryKey.ATTRIBUTE;
        var components = registryKey == RegistryKey.DATA_COMPONENT_TYPE;
        return (Registry<T>) Proxy.newProxyInstance(Registry.class.getClassLoader(), new Class<?>[] {Registry.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    case "toString" -> "test registry " + registryKey;
                    case "get", "getOrThrow" -> {
                        if (!(attributes || components) || arguments == null || !(arguments[0] instanceof Key key)) {
                            throw new UnsupportedOperationException("no " + registryKey + " registry in the tests");
                        }
                        yield attributes ? attribute(key) : component(key);
                    }
                    default -> throw new UnsupportedOperationException("no " + registryKey + " registry in the tests");
                });
    }

    private static Registry<?> refusing(String name) {
        return (Registry<?>) Proxy.newProxyInstance(Registry.class.getClassLoader(), new Class<?>[] {Registry.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    case "toString" -> "test registry " + name;
                    default -> throw new UnsupportedOperationException("no " + name + " registry in the tests");
                });
    }

    private static DataComponentType component(Key key) {
        return COMPONENTS.computeIfAbsent(key.asString(), text -> (DataComponentType) Proxy.newProxyInstance(
                DataComponentType.class.getClassLoader(), new Class<?>[] {DataComponentType.Valued.class,
                        DataComponentType.NonValued.class}, (proxy, method, arguments) -> switch (method.getName()) {
                            case "hashCode" -> text.hashCode();
                            case "equals" -> proxy == arguments[0];
                            case "toString" -> text;
                            case "getKey", "key" -> new NamespacedKey(key.namespace(), key.value());
                            case "isPersistent" -> true;
                            default -> throw new UnsupportedOperationException(method.getName());
                        }));
    }

    private static Attribute attribute(Key key) {
        return ATTRIBUTES.computeIfAbsent(key.asString(), text -> (Attribute) Proxy.newProxyInstance(
                Attribute.class.getClassLoader(), new Class<?>[] {Attribute.class}, (proxy, method, arguments) ->
                        switch (method.getName()) {
                            case "hashCode" -> text.hashCode();
                            case "equals" -> proxy == arguments[0];
                            case "toString", "name" -> text;
                            case "getKey", "key" -> new NamespacedKey(key.namespace(), key.value());
                            default -> throw new UnsupportedOperationException(method.getName());
                        }));
    }
}
