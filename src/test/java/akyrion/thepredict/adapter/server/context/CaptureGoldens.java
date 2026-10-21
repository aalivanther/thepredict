package akyrion.thepredict.adapter.server.context;

import akyrion.thepredict.adapter.server.context.CaptureDoubles.FakeMob;
import akyrion.thepredict.adapter.server.context.CaptureDoubles.FakePlayer;
import akyrion.thepredict.domain.CaptureContext;
import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.ParticipantAlias;
import akyrion.thepredict.domain.ThreatTableState;
import akyrion.thepredict.golden.FactsText;
import io.lumine.mythic.api.skills.Skill;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Zombie;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Captures of the golden references of M17.0 through the real {@link CombatCapture}, with doubles of Paper and
 * MythicMobs (rule P4): a world whose nearby-player query returns players in the four game modes, a vanilla zombie
 * nearby that must never enter, and targets found by the threat table or by {@code Mob#getTarget()} with the rule of
 * {@link MythicMemberSource}. Each scenario writes the facts, the sightings, the probes, the follow-range checks, the
 * nearby queries and lines of sight of the world with their arguments and order, and every call the capture made on
 * the zombie.
 */
public final class CaptureGoldens {
    private static final EncounterId ENCOUNTER = new EncounterId(7);

    private CaptureGoldens() {
    }

    /**
     * The golden text of every capture scenario, by file name.
     *
     * @return file name to text, in a fixed order
     */
    public static Map<String, String> all() {
        var files = new LinkedHashMap<String, String>();
        files.put("modes-threat-holder.txt", scenario("modes-threat-holder", world -> world.holder(1, world.p1)));
        files.put("target-by-get-target.txt", scenario("target-by-get-target", world -> world.path(1, world.p2)));
        files.put("zombie-get-target.txt", scenario("zombie-get-target", world -> world.path(1, world.zombie)));
        files.put("creative-holder.txt", scenario("creative-holder", world -> {
            world.holder(1, world.p3);
            world.path(1, world.p1);
        }));
        files.put("zombie-holder.txt", scenario("zombie-holder", world -> {
            world.holder(1, world.zombie);
            world.path(1, world.p1);
        }));
        files.put("dead-holder.txt", scenario("dead-holder", world -> {
            world.dead(world.p1);
            world.holder(1, world.p1);
            world.path(1, world.p2);
        }));
        files.put("spectator-get-target.txt", scenario("spectator-get-target", world -> world.path(1, world.p4)));
        files.put("two-mobs.txt", scenario("two-mobs", world -> {
            world.holder(1, world.p1);
            var second = world.mob(2, 10, 0);
            second.sight = player -> !player.equals(world.p2.id);
            world.path(2, world.p2);
            world.threat(2, world.p2, 1.0);
        }));
        files.put("other-world.txt", scenario("other-world", world -> {
            world.holder(1, world.p1);
            var away = world.mob(3, 0, 0);
            away.world = new CaptureDoubles.FakeWorld(CaptureDoubles.OTHER_WORLD_ID);
            world.path(3, world.p2);
        }));
        return files;
    }

    private static String scenario(String name, Consumer<GoldenWorld> setup) {
        var world = new GoldenWorld();
        setup.accept(world);
        var text = new StringBuilder("# M17.0 golden: capture ").append(name).append('\n');
        var keys = world.keys();
        var capture = world.capture();
        var facts = capture.capture(ENCOUNTER, keys, world.context());
        text.append(facts.map(FactsText::of).orElse("facts none\n"));
        for (var sighting : world.doubles.sightings) {
            text.append("sighting ").append(sighting).append('\n');
        }
        for (var key : keys) {
            text.append("probe ").append(key.entityId()).append(' ')
                    .append(capture.probe(key).map(Object::toString).orElse("none")).append('\n');
        }
        for (var first : keys) {
            for (var second : keys) {
                if (!first.equals(second)) {
                    text.append("followRange ").append(first.entityId()).append(' ').append(second.entityId())
                            .append(' ').append(capture.withinFollowRange(first, second)).append('\n');
                }
            }
        }
        text.append("aliases ").append(world.aliases.values()).append('\n');
        text.append("calls\n");
        world.doubles.world.calls.forEach(call -> text.append(call).append('\n'));
        text.append("zombie calls\n");
        world.zombieCalls.forEach(call -> text.append(call).append('\n'));
        return text.toString();
    }

    /** The doubles of one scenario: one member at the origin, four players, a zombie, and their targets. */
    private static final class GoldenWorld {
        final CaptureDoubles doubles = new CaptureDoubles();
        final GoldenSource source = new GoldenSource(doubles.source);
        final Map<UUID, ParticipantAlias> aliases = new LinkedHashMap<>();
        final List<String> zombieCalls = new ArrayList<>();
        final List<MobKey> members = new ArrayList<>();
        final FakePlayer p1;
        final FakePlayer p2;
        final FakePlayer p3;
        final FakePlayer p4;
        final LivingEntity zombie;

        GoldenWorld() {
            mob(1, 0, 0);
            p1 = doubles.player(1, 3, 0);
            p2 = doubles.player(2, 6, 0);
            p2.mode = GameMode.ADVENTURE;
            p3 = doubles.player(3, 4, 4);
            p3.mode = GameMode.CREATIVE;
            p4 = doubles.player(4, 5, -3);
            p4.mode = GameMode.SPECTATOR;
            doubles.player(5, 40, 0);
            zombie = zombie(new UUID(0x2AL, 1), 2, 2);
            doubles.world.living.add(zombie);
            threat(1, p1, 5.0);
            threat(1, p2, 2.0);
        }

        FakeMob mob(int value, double x, double z) {
            members.add(new MobKey(CaptureDoubles.mobId(value), 0));
            return doubles.mob(value, x, z);
        }

        List<MobKey> keys() {
            return List.copyOf(members);
        }

        void holder(int mob, FakePlayer player) {
            source.holders.put(CaptureDoubles.mobId(mob), player.player);
        }

        void holder(int mob, LivingEntity entity) {
            source.holders.put(CaptureDoubles.mobId(mob), entity);
        }

        void path(int mob, FakePlayer player) {
            source.pathTargets.put(CaptureDoubles.mobId(mob), player.player);
        }

        void path(int mob, LivingEntity entity) {
            source.pathTargets.put(CaptureDoubles.mobId(mob), entity);
        }

        void threat(int mob, FakePlayer player, double amount) {
            doubles.source.threat.computeIfAbsent(CaptureDoubles.mobId(mob), key -> new HashMap<>())
                    .put(player.id, amount);
        }

        void dead(FakePlayer player) {
            player.dead = true;
        }

        CombatCapture capture() {
            return new CombatCapture(source, doubles.reads, id -> Optional.ofNullable(doubles.source.entities.get(id)),
                    doubles.contexts, (condition, entity) -> false, doubles.clock, () -> true,
                    doubles.searchRadiusBlocks, ParticipantLookup.NONE);
        }

        CaptureContext context() {
            return new CaptureContext((id, kind) -> aliases.computeIfAbsent(id, key -> new ParticipantAlias(kind,
                    (int) aliases.values().stream().filter(alias -> alias.kind() == kind).count() + 1)), Set.of(), Map.of(), List.of(),
                    CaptureDoubles.WORLD_ID, Set.of(), doubles.sightings::add);
        }

        /** A vanilla zombie that writes every call except its identity, so any read of it shows in the reference. */
        private LivingEntity zombie(UUID id, double x, double z) {
            return (LivingEntity) Proxy.newProxyInstance(Zombie.class.getClassLoader(), new Class<?>[] {Zombie.class},
                    (proxy, method, arguments) -> switch (method.getName()) {
                        case "getUniqueId" -> id;
                        case "hashCode" -> id.hashCode();
                        case "equals" -> proxy == arguments[0];
                        case "toString" -> "zombie";
                        default -> {
                            zombieCalls.add("zombie " + method.getName());
                            yield switch (method.getName()) {
                                case "getWorld" -> doubles.world.world;
                                case "getLocation" -> new Location(doubles.world.world, x, 64, z);
                                case "isValid" -> true;
                                case "isDead", "isInvisible" -> false;
                                default -> method.getReturnType() == boolean.class ? Boolean.FALSE : null;
                            };
                        }
                    });
        }
    }

    /**
     * The MythicMobs side with the target rule of {@link MythicMemberSource#choose}: the top threat holder first, then
     * {@code Mob#getTarget()}, a player when valid, alive and in the mob's world, any other living entity only when the
     * participant policy of the member's profile admits it.
     */
    private static final class GoldenSource implements MemberSource {
        final CaptureDoubles.Source inner;
        final Map<UUID, LivingEntity> holders = new HashMap<>();
        final Map<UUID, LivingEntity> pathTargets = new HashMap<>();

        GoldenSource(CaptureDoubles.Source inner) {
            this.inner = inner;
        }

        @Override
        public Optional<Member> member(UUID mobId) {
            return inner.member(mobId);
        }

        @Override
        public Optional<LivingEntity> target(UUID mobId) {
            var member = inner.members.get(mobId);
            if (member == null) {
                return Optional.empty();
            }
            var actor = member.entity();
            var policy = member.profile().participants();
            return MythicMemberSource.choose(holders.get(mobId), () -> pathTargets.get(mobId), actor,
                    candidate -> policy.admits(candidate, actor, Set.of(), ParticipantLookup.NONE));
        }

        @Override
        public double threat(UUID mobId, UUID participantId) {
            return inner.threat(mobId, participantId);
        }

        @Override
        public boolean onCooldown(UUID mobId, Skill skill) {
            return inner.onCooldown(mobId, skill);
        }

        @Override
        public boolean threatInCombat(UUID mobId) {
            return inner.threatInCombat(mobId);
        }

        @Override
        public boolean isActive(UUID mobId) {
            return inner.isActive(mobId);
        }

        @Override
        public Optional<ThreatTableState> threatTable(UUID mobId) {
            return inner.threatTable(mobId);
        }
    }
}
