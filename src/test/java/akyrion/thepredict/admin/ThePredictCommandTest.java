package akyrion.thepredict.admin;

import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.GoalSummary;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.MovementProgress;
import akyrion.thepredict.domain.ServiceCheck;
import akyrion.thepredict.domain.ServiceState;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TacticalIntent;
import akyrion.thepredict.domain.ThreatTableState;
import akyrion.thepredict.engine.DebugIntents;
import akyrion.thepredict.engine.DuelRecording;
import akyrion.thepredict.engine.SessionReport;
import akyrion.thepredict.engine.StatusReport;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThePredictCommandTest {
    private static final UUID WORLD = new UUID(0xA11L, 1);
    private static final UUID FAR = new UUID(0xFA2L, 2);

    private static final World WORLD_PROXY = (World) Proxy.newProxyInstance(World.class.getClassLoader(),
            new Class<?>[] {World.class}, (proxy, method, arguments) -> switch (method.getName()) {
                case "getUID" -> WORLD;
                case "equals" -> proxy == arguments[0];
                case "hashCode" -> 1;
                default -> throw new UnsupportedOperationException(method.getName());
            });

    private final List<String> messages = new ArrayList<>();
    private final Services services = new Services();
    private final ThePredictCommand command = new ThePredictCommand(AdminFixtures::setup, services,
            DuelRecording.UNAVAILABLE, name -> Optional.empty());

    @Test
    void statusFromTheConsoleHasTheNineSectionsWithoutAWorldBudget() {
        run(console(), "status");
        assertTrue(messages.contains("[ThePredict] Environment"), messages.toString());
        assertTrue(messages.contains("[ThePredict] Counters"), messages.toString());
        assertEquals(List.of("status()"), services.calls);
        run(console());
        assertEquals(List.of("status()", "status()"), services.calls);
    }

    @Test
    void statusForAPlayerAddsTheBudgetOfTheirWorld() {
        run(player(true, List.of()), "STATUS");
        assertEquals(List.of("status(" + WORLD + ")"), services.calls);
        assertTrue(messages.stream().anyMatch(line -> line.startsWith("[ThePredict] Your world:")), messages.toString());
    }

    @Test
    void checkStartsWithoutWaitingAndReportsTheResultWhenTheEngineCallsBack() {
        run(console(), "check");
        assertEquals(1, messages.size());
        assertTrue(messages.getFirst().contains("the result arrives here without blocking the server"), messages.getFirst());
        assertTrue(messages.getFirst().contains("does not verify an inference"), messages.getFirst());
        services.checkWatcher.accept(new ServiceCheck(ServiceState.INVALID_CREDENTIALS, "authentication"));
        assertTrue(messages.getLast().startsWith("[ThePredict] Jev check: INVALID_CREDENTIALS (authentication)."),
                messages.getLast());
        services.checkStart = CommandServices.CheckStart.BUSY;
        run(console(), "check");
        assertTrue(messages.getLast().contains("already waiting"), messages.getLast());
    }

    @Test
    void inspectNearestPicksTheClosestBoundMobWithinSixteenBlocks() {
        var bound = entity(AdminFixtures.MOB, 5);
        var closerButUnbound = entity(UUID.randomUUID(), 2);
        var tooFar = entity(FAR, 17);
        services.bound.add(FAR);
        run(player(false, List.of(tooFar, closerButUnbound, bound)), "inspect", "nearest");
        assertTrue(messages.getFirst().contains("Mob 1a2b3c4d generation 3"), messages.toString());
    }

    @Test
    void inspectPatternsPrintsTheServiceLineAndAnEmptyAnswerSaysNoSession() {
        services.patternLines = List.of("spams_clicks mask=1 forecast=none");
        run(console(), "inspect", AdminFixtures.MOB.toString(), "patterns");
        assertTrue(messages.getLast().contains("spams_clicks mask=1 forecast=none"), messages.toString());
        assertEquals("inspectPatterns(" + AdminFixtures.MOB + ")", services.calls.getLast());
        services.patternLines = List.of();
        run(console(), "inspect", AdminFixtures.MOB.toString(), "patterns");
        assertTrue(messages.getLast().contains("has no session"), messages.getLast());
    }

    @Test
    void inspectDetailsIgnoreCaseAreNamedInTheUsageAndAreCompleted() {
        services.patternLines = List.of("spams_clicks mask=1 forecast=none");
        run(console(), "inspect", AdminFixtures.MOB.toString(), "Patterns");
        assertTrue(messages.getLast().contains("spams_clicks mask=1 forecast=none"), messages.toString());
        run(console(), "inspect", AdminFixtures.MOB.toString(), "zone");
        assertEquals("[ThePredict] Usage: /thepredict inspect <nearest|uuid> [tick|stance|telegraph|group|patterns]",
                messages.getLast());
        assertEquals(List.of("tick", "stance", "telegraph", "group", "patterns"),
                command.onTabComplete(console(), null, "thepredict", new String[] {"inspect", "nearest", ""}));
        assertEquals(List.of("patterns"),
                command.onTabComplete(console(), null, "thepredict", new String[] {"inspect", "nearest", "pa"}));
    }

    @Test
    void inspectOfAMobWithoutSessionAnswersWithoutErrorAndNearestNeedsAPlayer() {
        run(console(), "inspect", "123e4567-e89b-12d3-a456-426614174000");
        assertEquals(List.of("[ThePredict] 123e4567 has no session: it is not a bound mob with Predictor: true and an"
                + " active profile."), messages);
        run(console(), "inspect", "nearest");
        assertTrue(messages.getLast().contains("'nearest' needs a player"), messages.getLast());
        run(console(), "inspect", "zombie");
        assertTrue(messages.getLast().contains("Mob must be 'nearest' or an entity UUID"), messages.getLast());
        run(console(), "inspect", "y".repeat(40) + "SECRET-TAIL");
        assertTrue(messages.getLast().endsWith("y".repeat(40) + "…'"), messages.getLast());
        run(player(false, List.of()), "inspect", "nearest");
        assertTrue(messages.getLast().contains("within 16 blocks"), messages.getLast());
    }

    @Test
    void withoutThePermissionDebugIsNeitherListedNorRun() {
        var player = player(false, List.of(entity(AdminFixtures.MOB, 3)));
        run(player, "debug", "intent", "nearest", "hold");
        assertTrue(messages.getFirst().contains("thepredict.debug"), messages.getFirst());
        assertTrue(services.calls.isEmpty());
        assertEquals(List.of("status", "check", "inspect", "intention", "records", "eval", "zone", "memory", "learn",
                "distill", "autoconfig"), command.onTabComplete(player, null,
                "thepredict", new String[] {""}));
        assertEquals(List.of(), command.onTabComplete(player, null, "thepredict", new String[] {"debug", ""}));
        run(player, "nonsense");
        assertEquals("[ThePredict] Usage: /thepredict [status|check|inspect|intention|records|eval|zone|memory|learn|distill|autoconfig]", messages.getLast());
    }

    @Test
    void withThePermissionDebugIsListedAndSetsAnIntentOfTheProfile() {
        var player = player(true, List.of(entity(AdminFixtures.MOB, 3)));
        assertEquals(List.of("status", "check", "inspect", "intention", "records", "eval", "zone", "memory", "learn",
                "distill", "autoconfig", "debug", "duel"),
                command.onTabComplete(player,
                null, "thepredict", new String[] {""}));
        assertEquals(List.of("intent"), command.onTabComplete(player, null, "thepredict", new String[] {"debug", "in"}));
        assertEquals(List.of("nearest"), command.onTabComplete(player, null, "thepredict", new String[] {"inspect", ""}));
        run(player, "debug", "intent", "nearest", "hold_ground");
        assertEquals("setIntent(" + AdminFixtures.MOB + ", hold_ground)", services.calls.getLast());
        assertTrue(messages.getLast().contains("Debug intent hold_ground (HOLD, LOCAL) on 1a2b3c4d generation 3 for 40"
                + " ticks; it never sends a request to Jev"), messages.getLast());
        run(player, "debug", "intent", "nearest", "dance_around", "100");
        assertTrue(messages.getLast().contains("Debug intent refused for 1a2b3c4d: profile does not declare"),
                messages.getLast());
    }

    @Test
    void theConsoleMayUseDebugReleaseAndGoalsWithAUuid() {
        run(console(), "debug", "release", AdminFixtures.MOB.toString());
        assertTrue(messages.getLast().contains("Released 1a2b3c4d to level 4"), messages.getLast());
        run(console(), "debug", "goals", AdminFixtures.MOB.toString());
        assertTrue(messages.stream().anyMatch(line -> line.contains("Goals of " + AdminFixtures.MOB + " generation 3: 1")),
                messages.toString());
        run(console(), "debug");
        assertTrue(messages.getLast().contains("/thepredict debug goals <nearest|uuid>"), messages.getLast());
        run(console(), "debug", "cast", "nearest", "Skill");
        assertTrue(messages.getLast().contains("Unknown debug action 'cast'"), messages.getLast());
    }

    @Test
    void learnAndDistillStatusPrintTheLiveLinesOfTheRunningServer() {
        var control = new akyrion.thepredict.engine.distill.DistillControl(
                akyrion.thepredict.domain.distill.DistillMode.SHADOW, akyrion.thepredict.domain.distill.DistillMode.SHADOW);
        var learning = akyrion.thepredict.domain.LearningSettings.DEFAULT;
        command.useLearnStatus(() -> akyrion.thepredict.engine.learn.LearnStatus.line(learning));
        command.useDistillStatus(() -> akyrion.thepredict.engine.distill.DistillStatus.line(control,
                akyrion.thepredict.domain.distill.DistillMode.SHADOW));
        run(admin(), "distill", "status");
        assertTrue(messages.getLast().contains("state=NONE mode=shadow regime=r1:ALL"), messages.toString());
        control.disable();
        run(admin(), "distill", "status");
        assertTrue(messages.getLast().contains("state=NONE mode=off regime=r1:ALL"), messages.toString());
        run(admin(), "learn", "status");
        assertTrue(messages.getLast().contains("collect " + learning.collect() + " scope " + learning.scope()),
                messages.toString());
    }

    private CommandSender admin() {
        return (CommandSender) Proxy.newProxyInstance(ConsoleCommandSender.class.getClassLoader(),
                new Class<?>[] {ConsoleCommandSender.class}, (proxy, method, arguments) ->
                        method.getName().equals("hasPermission") ? Boolean.TRUE
                                : capture(method.getName(), arguments == null ? new Object[0] : arguments));
    }

    private void run(CommandSender sender, String... arguments) {
        assertTrue(command.onCommand(sender, null, "thepredict", arguments));
    }

    private Object capture(String method, Object[] arguments) {
        if (method.equals("sendMessage") && arguments.length == 1 && arguments[0] instanceof Component line) {
            messages.add(PlainTextComponentSerializer.plainText().serialize(line));
            return null;
        }
        throw new UnsupportedOperationException(method);
    }

    private CommandSender console() {
        return (CommandSender) Proxy.newProxyInstance(ConsoleCommandSender.class.getClassLoader(),
                new Class<?>[] {ConsoleCommandSender.class}, (proxy, method, arguments) ->
                        capture(method.getName(), arguments == null ? new Object[0] : arguments));
    }

    private Player player(boolean debug, List<Entity> nearby) {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getWorld" -> WORLD_PROXY;
                    case "getLocation" -> new Location(WORLD_PROXY, 0, 64, 0);
                    case "getNearbyEntities" -> nearby;
                    case "hasPermission" -> debug && ThePredictCommand.DEBUG_PERMISSION.equals(arguments[0]);
                    default -> capture(method.getName(), arguments == null ? new Object[0] : arguments);
                });
    }

    private static Entity entity(UUID id, double distance) {
        return (Entity) Proxy.newProxyInstance(Entity.class.getClassLoader(), new Class<?>[] {Entity.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "getLocation" -> new Location(WORLD_PROXY, distance, 64, 0);
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    /** Engine double: fixed reports, recorded calls. */
    private static final class Services implements CommandServices {
        final List<String> calls = new ArrayList<>();
        final Set<UUID> bound = new HashSet<>(Set.of(AdminFixtures.MOB));
        CheckStart checkStart = CheckStart.STARTED;
        Consumer<ServiceCheck> checkWatcher = check -> {
        };

        @Override
        public Optional<StatusReport> status() {
            calls.add("status()");
            return Optional.of(AdminFixtures.status());
        }

        @Override
        public Optional<StatusReport> status(UUID worldId) {
            calls.add("status(" + worldId + ")");
            return Optional.of(AdminFixtures.status());
        }

        @Override
        public Optional<SessionReport> session(UUID entityId) {
            return entityId.equals(AdminFixtures.MOB) ? Optional.of(AdminFixtures.session(false,
                    ThreatTableState.DISABLED)) : Optional.empty();
        }

        @Override
        public boolean bound(UUID entityId) {
            return bound.contains(entityId);
        }

        @Override
        public List<GoalSummary> goals(UUID entityId) {
            return List.of(new GoalSummary("thepredict:hold", Set.of("MOVE"), false));
        }

        @Override
        public MovementProgress progress(UUID entityId) {
            return new MovementProgress(false, false, false, OptionalDouble.empty());
        }

        @Override
        public DebugIntents.Outcome setIntent(UUID entityId, TacticId tactic) {
            calls.add("setIntent(" + entityId + ", " + tactic.value() + ")");
            if (!tactic.value().equals("hold_ground")) {
                return new DebugIntents.Outcome.Rejected("profile does not declare the tactic " + tactic.value());
            }
            var intent = new TacticalIntent(new MobKey(entityId, 3), tactic, Movement.HOLD, Optional.empty(), 1.0,
                    List.of(), Map.of(), ControlLevel.LOCAL, 0, 40 * 50_000_000L, false);
            return new DebugIntents.Outcome.Applied(intent, 40);
        }

        @Override
        public DebugIntents.Outcome setIntent(UUID entityId, TacticId tactic, int durationTicks) {
            return setIntent(entityId, tactic);
        }

        @Override
        public boolean release(UUID entityId) {
            calls.add("release(" + entityId + ")");
            return true;
        }

        List<String> patternLines = List.of();

        @Override
        public List<String> inspectPatterns(UUID entityId) {
            calls.add("inspectPatterns(" + entityId + ")");
            return patternLines;
        }

        @Override
        public CheckStart requestCheck(Consumer<ServiceCheck> result) {
            checkWatcher = result;
            return checkStart;
        }
    }
}
