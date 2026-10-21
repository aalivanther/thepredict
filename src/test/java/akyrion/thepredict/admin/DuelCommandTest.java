package akyrion.thepredict.admin;

import akyrion.thepredict.engine.DuelRecording;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DuelCommandTest {
    private static final UUID DUEL_WORLD = new UUID(0xD0E1L, 1);
    private static final UUID PLAYER_WORLD = new UUID(0xA11L, 2);
    private static final UUID PLAYER = new UUID(0xB0BL, 3);
    private static final Path GUIDE = Path.of("DocumentacionParaDesarrollar/Implementacion-0.0.1/paquete-pruebas/"
            + "Guia-de-pruebas-por-sesiones.md");

    private final List<String> messages = new ArrayList<>();
    private final Recording recording = new Recording();
    private final ThePredictCommand command = new ThePredictCommand(AdminFixtures::setup, (CommandServices) Proxy
            .newProxyInstance(CommandServices.class.getClassLoader(), new Class<?>[] {CommandServices.class},
                    (proxy, method, arguments) -> {
                        throw new UnsupportedOperationException(method.getName());
                    }), recording, name -> name.equals("duel") ? Optional.of(DUEL_WORLD) : Optional.empty());

    @Test
    void theConsoleFormGivesTheWorldAndTheCentreThenTheRadiusAndTheSkills() {
        var parsed = DuelCommand.parse(List.of("local-20260929", "duel_red", "duel_blue", "duel", "0", "65", "0", "24",
                "skills=DuelStrike,DuelBolt"), Optional.empty(), Optional.empty(), this::world);
        var request = assertInstanceOf(DuelCommand.Parsed.Accepted.class, parsed).request();
        assertEquals(new DuelRecording.DuelRequest("local-20260929", "duel_red", "duel_blue", DUEL_WORLD, 0, 65, 0, 24,
                Set.of("DuelStrike", "DuelBolt"), Optional.empty()), request);
    }

    @Test
    void thePlayerFormCentresTheArenaOnThePlayerWithTheDefaultRadius() {
        var arena = new DuelCommand.Arena(PLAYER_WORLD, 1, 65, 2);
        var parsed = DuelCommand.parse(List.of("mine", "duel_red", "duel_blue"), Optional.of(arena),
                Optional.of(PLAYER), this::world);
        var request = assertInstanceOf(DuelCommand.Parsed.Accepted.class, parsed).request();
        assertEquals(PLAYER_WORLD, request.worldId());
        assertEquals(DuelRecording.DuelRequest.DEFAULT_RADIUS_BLOCKS, request.radiusBlocks());
        assertEquals(Optional.of(PLAYER), request.requester());
        assertTrue(request.countedSkills().isEmpty(), "empty: the default of M18.4");
        var radius = DuelCommand.parse(List.of("mine", "duel_red", "duel_blue", "radius=12"), Optional.of(arena),
                Optional.of(PLAYER), this::world);
        assertEquals(12, assertInstanceOf(DuelCommand.Parsed.Accepted.class, radius).request().radiusBlocks());
    }

    @Test
    void theConsoleWithoutAWorldAndBadLabelsWorldsRadiiAndFactionsAreRefused() {
        assertEquals(DuelCommand.CONSOLE_NEEDS_WORLD, refused(List.of("local", "duel_red", "duel_blue")));
        assertEquals(DuelCommand.CONSOLE_NEEDS_WORLD, refused(List.of("local", "duel_red", "duel_blue", "24")));
        for (var label : List.of("a/b", "..", "Upper", "a.b", "x".repeat(41))) {
            assertTrue(refused(List.of(label, "duel_red", "duel_blue", "duel", "0", "65", "0"))
                    .startsWith("Label must match [a-z0-9-]{1,40}"), label);
        }
        assertEquals("World 'nether' is not loaded", refused(List.of("l", "a", "b", "nether", "0", "65", "0")));
        assertEquals("Give <world> <x> <y> <z> after the two factions", refused(List.of("l", "a", "b", "duel", "0")));
        assertEquals("Radius must be a number in (0, 64]", refused(List.of("l", "a", "b", "duel", "0", "65", "0",
                "65")));
        assertEquals("The two factions must be different", refused(List.of("l", "red", "RED", "duel", "0", "65",
                "0")));
        assertEquals("Unknown argument 'fast'", refused(List.of("l", "a", "b", "duel", "0", "65", "0", "fast")));
    }

    @Test
    void startStatusAndStopAnswerWithTheLinesTheSessionsGuideQuotes() throws IOException {
        run(console(), "duel", "start", "local-20260929", "duel_red", "duel_blue", "duel", "0", "65", "0", "30");
        assertEquals("[ThePredict] Duel recording 'local-20260929' started: red = duel_red, blue = duel_blue, radius "
                + "30.00, folder plugins/ThePredict/duels/local-20260929-20260929-120000", messages.getLast());
        var guide = Files.readString(GUIDE);
        assertTrue(guide.contains(messages.getLast().replace("local-20260929", "local-<fecha>")
                .replace("plugins/ThePredict/duels/local-<fecha>-20260929-120000",
                        "<ruta>/plugins/ThePredict/duels/local-<fecha>-<UTC>")), "the sessions guide quotes the start");
        run(console(), "duel", "status");
        assertEquals("[ThePredict] Duel recording 'local-20260929': recording, rounds 0, followed 0, requests 0, "
                + "input tokens 0, rows dropped 0, bytes 0, folder plugins/ThePredict/duels/local-20260929-20260929-120000",
                messages.getLast());
        run(console(), "duel", "stop");
        assertEquals("[ThePredict] Duel recording 'local-20260929' stopped after 0 rounds, 0 rows dropped; summary.md "
                + "is written in plugins/ThePredict/duels/local-20260929-20260929-120000", messages.getLast());
        assertTrue(guide.contains("Duel recording 'local-<fecha>' stopped after 80 rounds, 0 rows dropped; summary.md "
                + "is written in …"), "the sessions guide quotes the stop");
        assertTrue(guide.contains("Duel recording 'local-<fecha>': recording, rounds <n>, followed <m>, requests 0, "
                + "input tokens 0, rows dropped 0, bytes <b>, folder …"), "the sessions guide quotes the status");
        run(console(), "duel", "stop");
        assertEquals("[ThePredict] No duel recording is running", messages.getLast());
        run(console(), "duel");
        assertEquals("[ThePredict] /thepredict duel stop", messages.getLast());
    }

    @Test
    void withoutTheDebugPermissionAPlayerCannotRecord() {
        run(player(false), "duel", "start", "mine", "duel_red", "duel_blue");
        assertEquals("[ThePredict] You need the thepredict.debug permission to record duels", messages.getLast());
        assertTrue(recording.started.isEmpty());
        run(player(true), "duel", "start", "mine", "duel_red", "duel_blue");
        assertEquals(PLAYER_WORLD, recording.started.getFirst().worldId());
        assertEquals(List.of("start", "status", "stop"), command.onTabComplete(player(true), null, "thepredict",
                new String[] {"duel", ""}));
        assertEquals(List.of(), command.onTabComplete(player(false), null, "thepredict", new String[] {"duel", ""}));
    }

    private String refused(List<String> arguments) {
        return assertInstanceOf(DuelCommand.Parsed.Refused.class, DuelCommand.parse(arguments, Optional.empty(),
                Optional.empty(), this::world)).reason();
    }

    private Optional<UUID> world(String name) {
        return name.equals("duel") ? Optional.of(DUEL_WORLD) : Optional.empty();
    }

    private void run(CommandSender sender, String... arguments) {
        assertTrue(command.onCommand(sender, null, "thepredict", arguments));
    }

    private Object message(Object[] arguments) {
        if (arguments.length == 1 && arguments[0] instanceof Component line) {
            messages.add(PlainTextComponentSerializer.plainText().serialize(line));
        }
        return null;
    }

    private CommandSender console() {
        return (CommandSender) Proxy.newProxyInstance(ConsoleCommandSender.class.getClassLoader(),
                new Class<?>[] {ConsoleCommandSender.class}, (proxy, method, arguments) -> switch (method.getName()) {
                    case "sendMessage" -> message(arguments);
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private Player player(boolean debug) {
        var world = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[] {World.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getUID" -> PLAYER_WORLD;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getWorld" -> world;
                    case "getLocation" -> new Location(world, 0.5, 65, 0.5);
                    case "getUniqueId" -> PLAYER;
                    case "hasPermission" -> debug && ThePredictCommand.DEBUG_PERMISSION.equals(arguments[0]);
                    case "sendMessage" -> message(arguments);
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    /** A recorder that records the requests it gets and answers as the real one. */
    private static final class Recording implements DuelRecording {
        private final List<DuelRequest> started = new ArrayList<>();
        private DuelStatus status = DuelStatus.IDLE;

        @Override
        public DuelStatus start(DuelRequest request) {
            started.add(request);
            status = new DuelStatus(true, request.label(), 0, 0, 0, 0, Optional.empty(),
                    "plugins/ThePredict/duels/" + request.label() + "-20260929-120000", 0, 0);
            return status;
        }

        @Override
        public DuelStatus status() {
            return status;
        }

        @Override
        public DuelStatus stop() {
            status = new DuelStatus(false, status.label(), status.rounds(), 0, 0, 0, Optional.of("command"),
                    status.directory(), 0, 0);
            return status;
        }
    }
}
