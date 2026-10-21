package akyrion.thepredict.engine.duel;

import akyrion.thepredict.domain.ActiveUseConditions;
import akyrion.thepredict.domain.Answer;
import akyrion.thepredict.domain.DecisionRequest;
import akyrion.thepredict.domain.DecisionResult;
import akyrion.thepredict.domain.LocalPlanner;
import akyrion.thepredict.domain.QuestionKey;
import akyrion.thepredict.domain.QuestionSpec;
import akyrion.thepredict.domain.ServiceCheck;
import akyrion.thepredict.domain.ServiceState;
import akyrion.thepredict.domain.StableRandom;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.engine.DecisionClient;
import akyrion.thepredict.engine.Preparation;
import akyrion.thepredict.engine.PreparedRequest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * The scripted Jev of the simulator. The body is text of the size the estimate of 0.0.1 gives, and its input tokens
 * are that size at 1.5 characters per token, counted at the send: a late answer that arrives after its round ended
 * never adds tokens to the next round.
 *
 * <p>Mode {@code ORACLE(correctRatio, seed)} of M18.7, whose bytes the golden files keep: each question gets, with
 * probability {@code correctRatio}, the option that scores best by {@link LocalPlanner#score}, otherwise another option,
 * from one {@link Random}; the answer arrives {@value #LATENCY_TICKS} ticks after the send. It measures the path, not
 * the model.
 *
 * <p>Mode {@code JUDGE(pBest, latency, seed)} of the bench (§7.5, SIM-12): the best option comes from a
 * {@link DecisionOracle} that looks at the world when the request is sent, never at the engine's own score; with
 * probability {@code pBest} the answer is that option, otherwise one of the others, uniform, so {@code pBest = 1/k} is
 * the uniform choice. Each draw is {@code StableRandom.draw(seed, sequence, salt)} of the round's own seed, so nothing is
 * shared between arms or rounds; the latency is a {@link LatencyModel} sample, and an answer the model loses never
 * arrives, so the request expires and the engine decides at {@code LOCAL}.
 */
final class OracleJev implements DecisionClient {
    static final int LATENCY_TICKS = 6;
    static final double CONFIDENCE = 0.8;
    static final double CHARS_PER_TOKEN = 1.5;
    /** {@code pBest} that means one over the number of options. */
    static final double UNIFORM = -1;
    static final int SALT_PICK = 211;
    static final int SALT_OTHER = 212;

    /** What the judge sees when a request is sent: the world, the rivals and the tick of the round. */
    interface Lookout {
        /** A copy of the world now. */
        DuelWorld world();

        /** The classic duelist of a side, as it plays now. */
        ClassicDuelist duelist(DuelWorld.Side side);

        /** The tick of the round. */
        int index();

        /** The tick of the clock. */
        long tick();
    }

    private record Pending(long dueTick, PreparedRequest request, CompletableFuture<DecisionResult> future,
                           Optional<Map<QuestionKey, Answer>> answers) {
    }

    private final double correctRatio;
    private final Random random;
    private final Optional<DecisionOracle> oracle;
    private final double pBest;
    private final boolean uniform;
    private final LatencyModel latency;
    private final long seed;
    private final List<Pending> pending = new ArrayList<>();
    private Optional<Lookout> lookout = Optional.empty();
    private long tick;
    int requests;
    long tokens;
    long answered;
    long lost;

    OracleJev(double correctRatio, long seed) {
        this.correctRatio = correctRatio;
        this.random = new Random(seed);
        this.oracle = Optional.empty();
        this.pBest = correctRatio;
        this.uniform = false;
        this.latency = LatencyModel.fixed(LATENCY_TICKS * 50);
        this.seed = seed;
    }

    /**
     * The judge of the bench.
     *
     * @param oracle where the best option comes from
     * @param pBest probability of the best option, or {@link #UNIFORM}
     * @param latency the round trip
     * @param seed the seed of the round
     */
    OracleJev(DecisionOracle oracle, double pBest, LatencyModel latency, long seed) {
        this.correctRatio = pBest;
        this.random = new Random(seed);
        this.oracle = Optional.of(oracle);
        this.pBest = pBest;
        this.uniform = pBest < 0;
        this.latency = latency;
        this.seed = seed;
    }

    /**
     * The scripted Jev of a mode, empty for {@code NONE}.
     *
     * @param mode the mode
     * @param kit the kit of the round, whose skills the judge plays
     * @return the client
     */
    static Optional<OracleJev> of(DuelSimulator.Mode mode, Kit kit) {
        return switch (mode) {
            case DuelSimulator.Mode.None none -> Optional.empty();
            case DuelSimulator.Mode.Oracle scripted -> Optional.of(new OracleJev(scripted.correctRatio(),
                    scripted.seed()));
            case DuelSimulator.Mode.Judge judge -> Optional.of(new OracleJev(new LookaheadOracle(
                    LookaheadOracle.HORIZON_TICKS, kit), judge.pBest(), judge.latency().model(judge.seed()),
                    judge.seed()));
        };
    }

    /** Lets the judge see the world of the round. */
    void watch(Lookout view) {
        lookout = Optional.of(view);
    }

    @Override
    public Preparation prepare(DecisionRequest request) {
        var size = 400 + 120 * request.questions().size() + 60 * request.facts().participants().size();
        var body = "x".repeat((int) Math.ceil(size * CHARS_PER_TOKEN));
        return new Preparation.Ready(new PreparedRequest(request, body, Set.of(), Set.of(), body.length()));
    }

    @Override
    public CompletableFuture<DecisionResult> decide(PreparedRequest request) {
        var future = new CompletableFuture<DecisionResult>();
        var sequence = requests;
        requests++;
        tokens += inputTokens(request);
        if (oracle.isEmpty()) {
            pending.add(new Pending(tick + LATENCY_TICKS, request, future, Optional.empty()));
            return future;
        }
        var wait = latency.ticks(sequence);
        if (wait < 0) {
            lost++;
            return future;
        }
        var view = lookout.orElseThrow(() -> new IllegalStateException("the judge does not see the world"));
        pending.add(new Pending(view.tick() + wait, request, future, Optional.of(judge(view, request.sent(),
                sequence))));
        return future;
    }

    @Override
    public CompletableFuture<ServiceCheck> check() {
        return CompletableFuture.completedFuture(new ServiceCheck(ServiceState.READY, "oracle"));
    }

    @Override
    public void close() {
        pending.forEach(entry -> entry.future().cancel(false));
        pending.clear();
    }

    /** Completes the answers due at this tick. */
    void deliver(long now) {
        tick = now;
        var due = pending.stream().filter(entry -> entry.dueTick() <= now).toList();
        pending.removeAll(due);
        for (var entry : due) {
            var input = inputTokens(entry.request());
            var answers = entry.answers().orElseGet(() -> {
                var scripted = new LinkedHashMap<QuestionKey, Answer>();
                entry.request().sent().questions().forEach(question -> scripted.put(question.key(),
                        answer(entry.request().sent(), question)));
                return scripted;
            });
            answered++;
            entry.future().complete(new DecisionResult.Answered(answers, "jev-1.13.0", input));
        }
    }

    /** The input tokens of a request, counted when it is sent, so they belong to the round that sent it. */
    private static int inputTokens(PreparedRequest request) {
        return (int) Math.ceil(request.bodyChars() / CHARS_PER_TOKEN);
    }

    private Answer answer(DecisionRequest request, QuestionSpec question) {
        var profile = request.profiles().get(question.mob());
        var facts = request.facts().mobs().stream().filter(mob -> mob.mob().equals(question.mob())).findFirst()
                .orElseThrow();
        var now = ActiveUseConditions.of(facts, request.facts().participants());
        var ranked = new ArrayList<>(question.options());
        ranked.sort(Comparator.comparingInt((TacticId tactic) -> LocalPlanner.score(profile,
                profile.tactic(tactic).orElseThrow(), now)).reversed());
        var chosen = random.nextDouble() < correctRatio ? ranked.getFirst()
                : ranked.get(1 + random.nextInt(ranked.size() - 1));
        return answerFor(chosen, question.options());
    }

    private Map<QuestionKey, Answer> judge(Lookout view, DecisionRequest request, int sequence) {
        var answers = new LinkedHashMap<QuestionKey, Answer>();
        var questions = request.questions();
        for (var index = 0; index < questions.size(); index++) {
            var question = questions.get(index);
            var profile = request.profiles().get(question.mob());
            var world = view.world();
            var side = world.red.id.equals(question.mob().entityId()) ? DuelWorld.Side.RED : DuelWorld.Side.BLUE;
            var best = oracle.orElseThrow().best(world, view.duelist(side.rival()), question, profile, view.index(),
                    view.tick());
            answers.put(question.key(), answerFor(pick(best, question.options(), sequence * 16L + index),
                    question.options()));
        }
        return answers;
    }

    TacticId pick(TacticId best, List<TacticId> options, long sequence) {
        var count = options.size();
        var probability = uniform ? 1.0 / count : pBest;
        if (count == 1 || Scenario.unit(StableRandom.draw(seed, sequence, SALT_PICK)) < probability) {
            return best;
        }
        var others = new ArrayList<TacticId>(count - 1);
        for (var option : options) {
            if (!option.equals(best)) {
                others.add(option);
            }
        }
        return others.get(StableRandom.below(StableRandom.draw(seed, sequence, SALT_OTHER), others.size()));
    }

    private static Answer answerFor(TacticId chosen, List<TacticId> options) {
        var probabilities = new LinkedHashMap<TacticId, Double>();
        probabilities.put(chosen, 0.7);
        var others = options.stream().filter(tactic -> !tactic.equals(chosen)).toList();
        others.forEach(tactic -> probabilities.put(tactic, 0.3 / others.size()));
        return new Answer(probabilities, CONFIDENCE);
    }
}
