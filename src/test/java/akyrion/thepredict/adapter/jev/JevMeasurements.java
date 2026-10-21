package akyrion.thepredict.adapter.jev;

import akyrion.thepredict.MeasurementReport;
import akyrion.thepredict.domain.CombatFacts;
import akyrion.thepredict.domain.DecisionRequest;
import akyrion.thepredict.domain.DecisionResult;
import akyrion.thepredict.domain.EnemyPattern;
import akyrion.thepredict.domain.FitCheck;
import akyrion.thepredict.domain.ParticipantFacts;
import akyrion.thepredict.domain.PatternAdmission;
import akyrion.thepredict.domain.PatternMode;
import akyrion.thepredict.domain.GroupBrief;
import akyrion.thepredict.domain.GroupPlan;
import akyrion.thepredict.domain.GroupQuestions;
import akyrion.thepredict.domain.GroupRelations;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.QuestionDetail;
import akyrion.thepredict.domain.QuestionKey;
import akyrion.thepredict.domain.QuestionSpec;
import akyrion.thepredict.domain.Situation;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.engine.EngineSettings;
import akyrion.thepredict.engine.Preparation;
import akyrion.thepredict.engine.RequestBudget;
import akyrion.thepredict.engine.ServerClock;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static akyrion.thepredict.MeasurementReport.number;

/**
 * Local measurements of the Jev path for M13: request and state sizes with the example profile against the token
 * estimates, the CPU time of encoding and parsing, and the round trip through the real client to the loopback contract
 * server. No request leaves the machine; the times are wall times of this machine and exclude network and model.
 */
public final class JevMeasurements {
    private static final int[][] SIZES = {{1, 1}, {1, 4}, {3, 2}, {8, 4}, {16, 8}, {16, 16}};
    private static final int STATE_LIMIT = 12_000;
    /** Columns of the question-detail table, including the cap of four options (M19.7). */
    static final List<String> DETAIL_COLUMNS = List.of("Detail", "Request", "Body chars", "Tokens at 1.5 (guard)",
            "Tokens at 1.73 (mean)", "Prepare us p50/p95/p99/max", "Chars with cap 4", "Tokens at 1.5 with cap 4");

    private JevMeasurements() {
    }

    /**
     * Writes every measurement of this part.
     *
     * @param report report to write into
     */
    public static void write(MeasurementReport report) {
        var profile = ScenarioFacts.example();
        sizes(report, profile);
        levels(report, profile);
        roundTrip(report);
        groupColumn(report);
        report.line("Columna memoria: una frase fija, unos 15 tokens, solo con memory.mode full.");
    }

    /**
     * Characters of one group body. The cadence is not in the body, so the same count is published for both cadences.
     * {@code -1} when the encoder refuses the request.
     *
     * @param members mobs in the encounter
     * @param questions which group questions are attached
     * @return characters, or {@code -1}
     */
    public static long groupChars(int members, GroupQuestions questions) {
        var encoder = new RequestEncoder("jev-1.13.0", new SemanticEncoder(STATE_LIMIT));
        try {
            return encoder.encode(grouped(ScenarioFacts.example(), members, questions)).length();
        } catch (RuntimeException exception) {
            return -1L;
        }
    }

    /**
     * Group column. Tokens at 1.5 are {@link RequestSizeGuard#tokens}. Tokens at 2.55 are characters divided by 2.55.
     * {@code REACTIVE} and {@code STANDARD} share the measured characters because the cadence is not encoded.
     *
     * @param report report to write into
     */
    public static void groupColumn(MeasurementReport report) {
        report.section("Columna grupo (M25)");
        report.line("La cadencia no entra en el cuerpo. REACTIVE y STANDARD publican los mismos caracteres medidos.");
        var rows = new java.util.ArrayList<java.util.List<String>>();
        for (var members : new int[] {1, 3, 5, 8, 16}) {
            for (var questions : GroupQuestions.values()) {
                var chars = groupChars(members, questions);
                var text = chars < 0 ? "refused" : Long.toString(chars);
                var guard = chars < 0 ? "refused" : Long.toString(RequestSizeGuard.tokens(chars));
                var coarse = chars < 0 ? "refused" : String.format(Locale.ROOT, "%.1f", chars / 2.55);
                var name = questions.name().toLowerCase(Locale.ROOT);
                rows.add(java.util.List.of(Integer.toString(members), name, "REACTIVE", text, guard, coarse));
                rows.add(java.util.List.of(Integer.toString(members), name, "STANDARD", text, guard, coarse));
            }
        }
        report.table(java.util.List.of("Miembros", "Preguntas", "Cadencia", "Caracteres", "Tokens a 1,5",
                "Tokens a 2,55"), rows);
    }

    /**
     * The request of one group as production sends it (M25.10, DOC-02): the first mob is the leader, and the question
     * set keeps only the member questions it names; every mob stays in the state.
     */
    private static DecisionRequest grouped(TacticalProfile profile, int members, GroupQuestions questions) {
        var request = ScenarioFacts.encounter(profile, members, 1, 5);
        var leader = request.facts().mobs().getFirst().mob().entityId();
        var brief = new GroupBrief(questions, java.util.List.of(GroupPlan.PRESS_MAIN_TARGET.option(),
                GroupPlan.HARASS_FROM_RANGE.option()), 1L, members > 1, java.util.Optional.of(leader));
        var asked = request.questions().stream().filter(question -> brief.asksMember(question.mob().entityId()))
                .toList();
        var profiles = new LinkedHashMap<MobKey, TacticalProfile>();
        asked.forEach(question -> profiles.put(question.mob(), request.profiles().get(question.mob())));
        var checks = request.fitChecks().stream().filter(check -> profiles.containsKey(check.mob())).toList();
        return new DecisionRequest(request.encounter(), request.sequence(),
                request.facts().withGroup(GroupRelations.NONE, brief), asked, profiles, checks);
    }

    /**
     * Request size per question detail (M16.2): the production request of M16.2 (1 mob, 1 participant, {@code NOW}
     * and one contingency, 7 options, a state of about 650 characters) and the encounters of M13 with 7 options and 16
     * events, at 1.5 characters per token (the guard) and at 1.73 (the mean measured with the real account, DT-31).
     */
    private static void levels(MeasurementReport report, TacticalProfile profile) {
        report.section("Request size per question detail (M16.2), 7 options, NOW plus one contingency per mob");
        var rows = new ArrayList<List<String>>();
        var encoder = new RequestEncoder("jev-1.13.0", new SemanticEncoder(STATE_LIMIT));
        for (var detail : QuestionDetail.values()) {
            for (var fitChecks : detail == QuestionDetail.FULL ? new boolean[] {false, true} : new boolean[] {false}) {
                var detailed = JevFixtures.detailed(profile, detail, fitChecks);
                var cases = new LinkedHashMap<String, DecisionRequest>();
                cases.put("production 1 x 1", ScenarioFacts.production(detailed));
                cases.put("1 x 1, 16 events", ScenarioFacts.encounter(detailed, 1, 1, 7));
                cases.put("16 x 16, 16 events", ScenarioFacts.encounter(detailed, 16, 16, 7));
                cases.forEach((name, request) -> rows.add(level(encoder, detail, fitChecks, name, request)));
            }
        }
        report.table(DETAIL_COLUMNS, rows);
    }

    /**
     * The same request with at most {@code maxOptions} options on every question. Fit checks whose tactic is no longer
     * an option of the {@code NOW} question of their mob are dropped, so the request stays valid (M19.7).
     *
     * @param request request to cap
     * @param maxOptions most options kept on each question, at least {@link QuestionSpec#MIN_OPTIONS}
     * @return the capped request
     */
    static DecisionRequest capped(DecisionRequest request, int maxOptions) {
        var questions = new ArrayList<QuestionSpec>(request.questions().size());
        var now = new LinkedHashMap<MobKey, List<TacticId>>();
        for (var question : request.questions()) {
            var options = question.options();
            var kept = options.size() <= maxOptions ? options : List.copyOf(options.subList(0, maxOptions));
            var copy = question.withOptions(kept);
            questions.add(copy);
            if (question.situation() == Situation.NOW) {
                now.put(question.mob(), copy.options());
            }
        }
        var checks = new ArrayList<FitCheck>();
        for (var check : request.fitChecks()) {
            var options = now.get(check.mob());
            if (options != null && options.contains(check.tactic())) {
                checks.add(check);
            }
        }
        return new DecisionRequest(request.encounter(), request.sequence(), request.facts(), questions,
                request.profiles(), checks);
    }

    /**
     * Body characters of one prepare, or {@code -1} when preparation refuses the request. One prepare, no timing loop.
     *
     * @param encoder encoder of the measurement
     * @param request request to prepare
     * @return characters, or {@code -1}
     */
    /**
     * Characters of one production body with {@code count} pattern facts admitted as {@code full}.
     *
     * @param count facts, {@code 0}, {@code 1}, {@code 2} or {@code 4}
     * @return encoded characters
     */
    public static long patternChars(int count) {
        return patternText(PatternMode.FULL, mask(count)).length();
    }

    /**
     * Guard tokens of {@link #patternChars}.
     *
     * @param count facts
     * @return tokens at 1.5
     */
    public static long patternTokens(int count) {
        return RequestSizeGuard.tokens(patternChars(count));
    }

    /**
     * Encoded body after the admission filter. Off and observe match an empty set.
     *
     * @param mode effective mode
     * @param activeMask active bits
     * @return the body
     */
    public static String patternBody(PatternMode mode, int activeMask) {
        return patternText(mode, activeMask);
    }

    private static String patternText(PatternMode mode, int activeMask) {
        var request = ScenarioFacts.production(ScenarioFacts.example());
        var facts = request.facts();
        var first = facts.participants().getFirst();
        var replaced = new ParticipantFacts(first.alias(), first.health(), first.armor(), first.cues(),
                first.threatRank(), first.species(), PatternAdmission.forMode(mode, activeMask), first.memory());
        var participants = new ArrayList<>(facts.participants());
        participants.set(0, replaced);
        var combat = new CombatFacts(facts.encounter(), facts.captureTick(), facts.captureNanos(), facts.mobs(),
                participants, facts.recentEvents(), facts.combatSeconds(), facts.relations(), facts.brief());
        var again = new DecisionRequest(request.encounter(), request.sequence(), combat, request.questions(),
                request.profiles(), request.fitChecks());
        return new RequestEncoder("jev-1.13.0", new SemanticEncoder(STATE_LIMIT)).encode(again);
    }

    private static int mask(int count) {
        var bits = 0;
        var patterns = EnumSet.noneOf(EnemyPattern.class);
        for (var index = 0; index < count; index++) {
            var pattern = EnemyPattern.values()[index];
            patterns.add(pattern);
            bits |= pattern.bit();
        }
        if (patterns.contains(EnemyPattern.FIXED_CADENCE)) {
            throw new IllegalArgumentException("cadence has no phrase");
        }
        return bits;
    }

    static long bodyChars(RequestEncoder encoder, DecisionRequest request) {
        var prepared = encoder.prepare(request);
        if (!(prepared instanceof Preparation.Ready ready)) {
            return -1;
        }
        return ready.prepared().bodyChars();
    }

    private static List<String> level(RequestEncoder encoder, QuestionDetail detail, boolean fitChecks, String name,
                                      DecisionRequest request) {
        var label = detail.name().toLowerCase(Locale.ROOT) + (fitChecks ? " + fit checks" : "");
        var checks = new ArrayList<FitCheck>();
        if (fitChecks) {
            for (var question : request.questions()) {
                if (question.situation() == Situation.NOW) {
                    question.options().forEach(option -> checks.add(new FitCheck(QuestionKey.of(
                            request.questions().size() + checks.size()), question.mob(), option)));
                }
            }
        }
        var checked = new DecisionRequest(request.encounter(), request.sequence(), request.facts(),
                request.questions(), request.profiles(), checks);
        var prepared = encoder.prepare(checked);
        if (!(prepared instanceof Preparation.Ready ready)) {
            return List.of(label, name, "refused: " + (prepared instanceof Preparation.Invalid invalid
                    ? invalid.detail() : "nothing to ask"), "-", "-", "-", "-", "-");
        }
        var chars = ready.prepared().bodyChars();
        var cappedChars = bodyChars(encoder, capped(checked, 4));
        return List.of(label, name, String.valueOf(chars), String.valueOf(RequestSizeGuard.tokens(chars)),
                String.valueOf(Math.round(chars / 1.73)), MeasurementReport.micros(timed(2000,
                        () -> encoder.prepare(checked))),
                cappedChars < 0 ? "refused" : String.valueOf(cappedChars),
                cappedChars < 0 ? "-" : String.valueOf(RequestSizeGuard.tokens(cappedChars)));
    }

    private static void sizes(MeasurementReport report, TacticalProfile profile) {
        report.section("Request size with the example profile (5 options, NOW plus one contingency per mob, 16 events)");
        var budget = new RequestBudget(new EngineSettings(600, 300, 8, 4, 600_000, 10, 2, 64,
                16, 64, 2000, 800_000_000L));
        var rows = new ArrayList<List<String>>();
        for (var size : SIZES) {
            var request = ScenarioFacts.encounter(profile, size[0], size[1], 5);
            var full = new RequestEncoder("jev-1.13.0", new SemanticEncoder(1_000_000)).body(request);
            var encoder = new RequestEncoder("jev-1.13.0", new SemanticEncoder(STATE_LIMIT));
            var body = encoder.encode(request);
            var stateChars = full.get("state").toString().length();
            var questions = request.questions().size();
            var encodeNanos = timed(2000, () -> encoder.encode(request));
            var response = answer(request);
            var parseNanos = timed(2000, () -> ResponseParser.parse(response, request.questions()));
            var scenarioBytes = ScenarioSerializer.document(body, request, ResponseParser.parse(response,
                    request.questions())).getBytes(StandardCharsets.UTF_8).length;
            rows.add(List.of(size[0] + " x " + size[1], String.valueOf(questions), String.valueOf(body.length()),
                    String.valueOf(scenarioBytes),
                    String.valueOf(stateChars), stateChars > STATE_LIMIT ? "yes" : "no",
                    String.valueOf(RequestSizeGuard.tokens(body.length())), String.valueOf((body.length() + 3) / 4),
                    String.valueOf(budget.tokensFor(body.length())),
                    MeasurementReport.micros(encodeNanos), MeasurementReport.micros(parseNanos)));
        }
        report.table(List.of("Mobs x players", "Questions", "Body chars", "Scenario file bytes", "Untrimmed state chars",
                "Trimmed to 12 000", "Tokens at 1.5 chars (guard)", "Tokens at 4 chars", "Budget reservation (M16)",
                "Encode us p50/p95/p99/max", "Parse us p50/p95/p99/max"), rows);
    }

    private static void roundTrip(MeasurementReport report) {
        report.section("Round trip through SystemOneClient to the loopback contract server (no network, no model)");
        try (var server = SystemOneContractServer.start()) {
            var client = new SystemOneClient(JevFixtures.settings(server.baseUrl()), ServerClock.of(() -> 0,
                    System::nanoTime));
            try {
                calls(client, 100);
                var decide = calls(client, 300);
                var check = checks(client, 100);
                report.table(List.of("Call", "Samples", "ms p50 / p95 / p99 / max"), List.of(
                        List.of("decide (1 mob, 2 questions)", "300", millis(decide)),
                        List.of("check (GET /v1/models)", "100", millis(check))));
            } finally {
                client.close();
            }
        }
    }

    private static long[] calls(SystemOneClient client, int count) {
        var nanos = new long[count];
        for (var index = 0; index < count; index++) {
            var started = System.nanoTime();
            var result = join(JevFixtures.send(client, JevFixtures.request()));
            nanos[index] = System.nanoTime() - started;
            if (!(result instanceof DecisionResult.Answered)) {
                throw new IllegalStateException("the contract server did not answer: " + result);
            }
        }
        return nanos;
    }

    private static long[] checks(SystemOneClient client, int count) {
        var nanos = new long[count];
        for (var index = 0; index < count; index++) {
            var started = System.nanoTime();
            join(client.check());
            nanos[index] = System.nanoTime() - started;
        }
        return nanos;
    }

    private static <T> T join(CompletableFuture<T> future) {
        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String millis(long[] nanos) {
        var micros = MeasurementReport.micros(nanos).split(" / ");
        var parts = new ArrayList<String>();
        for (var value : micros) {
            parts.add(number(Double.parseDouble(value) / 1000.0, 3));
        }
        return String.join(" / ", parts);
    }

    private static long[] timed(int runs, Runnable work) {
        for (var index = 0; index < runs; index++) {
            work.run();
        }
        var nanos = new long[runs];
        for (var index = 0; index < runs; index++) {
            var started = System.nanoTime();
            work.run();
            nanos[index] = System.nanoTime() - started;
        }
        return nanos;
    }

    private static String answer(DecisionRequest request) {
        var answers = new StringBuilder();
        for (var question : request.questions()) {
            if (!answers.isEmpty()) {
                answers.append(',');
            }
            var options = question.options();
            var share = 1.0 / options.size();
            answers.append('"').append(question.key().value()).append("\":{\"type\":\"choice\",\"choice\":\"")
                    .append(options.getFirst().value()).append("\",\"confidence\":0.5,\"probabilities\":{");
            for (var index = 0; index < options.size(); index++) {
                answers.append(index == 0 ? "" : ",").append('"').append(options.get(index).value()).append("\":")
                        .append(share);
            }
            answers.append("}}");
        }
        return "{\"model\":\"jev-1.13.0\",\"answers\":{" + answers + "},\"usage\":{\"input_tokens\":1000,"
                + "\"output_tokens\":1}}";
    }
}
