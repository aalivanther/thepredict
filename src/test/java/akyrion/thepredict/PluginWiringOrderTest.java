package akyrion.thepredict;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B1 at the composition root: the pattern director exists before memory and encounter closes are joined to it, and
 * the memory host exists before both. The root needs a running server, so the order is read from its source, as
 * {@code PatternJoinTest} does for the coordinator: the calls of {@code onEnable} in their order, and the order inside
 * the method that wires the patterns. The shutdown runs every step of T7 through {@link ShutdownSequence}, in order.
 */
class PluginWiringOrderTest {
    private static final Path ROOT = Path.of("src/main/java/akyrion/thepredict/ThePredictPlugin.java");

    @Test
    void thePatternLinkRunsInsideStartAfterTheDirectorAndAfterStorage() throws IOException {
        var source = Files.readString(ROOT);
        var enable = body(source, "public void onEnable() {");
        var storage = enable.indexOf("        wireStorage();");
        var group = enable.indexOf("wireGroup(found, services, settings, hazards)");
        var engine = enable.indexOf("        startEngine();");
        assertTrue(storage >= 0 && group >= 0 && engine >= 0, "anchors of onEnable missing");
        assertTrue(storage < group, "memory must exist before the pattern block");
        assertTrue(group < engine, "the pattern block must run inside start, before the engine task");
        assertTrue(body(source, "private void wireStorage() {").contains("memoryHost = createMemoryHost("));
        assertTrue(body(source, "private void wireGroup(").contains("wirePatterns(found, services, capture, settings);"));
        var patterns = body(source, "private void wirePatterns(");
        var director = patterns.indexOf("new PatternDirector(");
        var link = patterns.indexOf("PatternMemoryLink.attach(");
        assertTrue(director >= 0 && link >= 0, "anchors of the pattern block missing");
        assertTrue(director < link, "the link must follow the pattern director");
        assertEquals(source.indexOf("PatternMemoryLink.attach("), source.lastIndexOf("PatternMemoryLink.attach("));
    }

    @Test
    void theRootNoLongerWiresPatternsThroughAFieldThatIsStillNull() throws IOException {
        var source = Files.readString(ROOT);
        assertFalse(source.contains("patternDirector"));
        assertFalse(source.contains(".useSeed("));
        assertFalse(source.contains(".useStyles("));
        assertFalse(source.contains("::summarize"));
    }

    @Test
    void theShutdownRunsEveryStepOfT7InOrderAndOnItsOwn() throws IOException {
        var disable = body(Files.readString(ROOT), "public void onDisable() {");
        var names = List.of("engine task", "requests", "intent board", "credit", "sessions", "duel", "storage", "zone",
                "jev client");
        var last = -1;
        for (var name : names) {
            var at = disable.indexOf("shutdown.step(\"" + name + "\"");
            assertTrue(at > last, name + " must follow the previous step of T7");
            last = at;
        }
        assertEquals(names.size(), disable.split("shutdown\\.step\\(", -1).length - 1, "every step goes alone");
    }

    /**
     * The root calls of the fixes of the 2026-10-14 audit, method by method: they need a running server, so their
     * presence is read from the source; the logic behind each call has its own test.
     */
    @Test
    void theRootCallsTheTranslationsOfThisRound() throws IOException {
        var source = Files.readString(ROOT);
        var expected = List.of(
                List.of("private void wireGroup(", "director.useDifficulty(settings.difficulty());"),
                List.of("private void hearVerdicts(", "verdictGates.offerMemory(memoryHost.loader(), current.memory())"),
                List.of("private void hearVerdicts(", "verdictGates.offerPatterns(patternGate, current.patterns())"),
                List.of("private void hearVerdicts(", "installCalibrator(services, current, learning);"),
                List.of("private void wirePatterns(", "new PatternDirector(inbox, book, limits, patternGate,"),
                List.of("private void wireRecords(", "ProfileCatalogs.changesOverlay(result.ok(), result.detail())"),
                List.of("private void wireRecords(", "service.recordTap().ifPresent("),
                List.of("private Optional<StorageService> enableStorage() {",
                        "StorageWiring.idleWord(startup.configurationProblem())"),
                List.of("private Optional<StorageService> enableStorage() {", "StorageWiring.spec("),
                List.of("private void closeStorage() {", "service.close(batch.get(), 3000) : service.close(3000)"),
                List.of("private void closeStorage() {", "report.unapplied()"),
                List.of("private boolean start() {", "SystemOneWiring.headerWarning(System::getProperty)"),
                List.of("private void wireLearning(", "learning.distillCeiling(current.distill().maxArtifactBytes())"),
                List.of("private void wireLearning(", "learning.packaged(getClass().getClassLoader())"),
                List.of("private void registerCommand() {", "command.useDefaultDifficulty(current.difficulty());"));
        for (var pair : expected) {
            assertTrue(body(source, pair.get(0)).contains(pair.get(1)), pair.get(0) + " must call " + pair.get(1));
        }
    }

    /**
     * The root wiring of the docs review (DOC-01, DOC-09): the pattern step gets the cue port, the capture gets the cap
     * of pattern facts per request, and the start logs the keys this build does not read.
     */
    @Test
    void theRootWiresThePatternCuesTheRequestFactCapAndTheWarningsOfTheInertKeys() throws IOException {
        var source = Files.readString(ROOT);
        var patterns = body(source, "private void wirePatterns(");
        assertTrue(patterns.contains("services.hearPatterns(patterns);"), "DOC-01");
        assertTrue(patterns.contains("capture.useRequestFactCap(limits.maxFactsPerRequest());"), "DOC-09");
        assertTrue(body(source, "private boolean start() {").contains("InertKeys.of(settings)"), "DOC-09");
        var services = Files.readString(Path.of("src/main/java/akyrion/thepredict/SessionServices.java"));
        assertTrue(body(services, "void hearPatterns(").contains(".useCues(actions);"), "DOC-01");
    }

    private static String body(String source, String signature) {
        var start = source.indexOf(signature);
        assertTrue(start >= 0, signature + " missing");
        var end = source.indexOf("\n    }\n", start);
        return source.substring(start, end);
    }
}
