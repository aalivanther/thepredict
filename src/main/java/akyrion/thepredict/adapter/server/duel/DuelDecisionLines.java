package akyrion.thepredict.adapter.server.duel;

import akyrion.thepredict.domain.Answer;
import akyrion.thepredict.domain.DecisionResult;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.QuestionKey;
import akyrion.thepredict.domain.QuestionSpec;
import akyrion.thepredict.domain.Situation;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.engine.DecisionDispatcher;
import akyrion.thepredict.engine.Preparation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The typed lines of {@code decisions.jsonl} (M18.4): {@code planned}, {@code body}, {@code result},
 * {@code acceptance} and {@code local}. Every line carries {@code type}, the join key {@code encounter} and
 * {@code sequence} and {@code t_nanos}; a {@code local} line has no request, so its {@code encounter} is {@code null} and
 * its {@code sequence} is −1, the only use of −1. Package-private; pure.
 */
final class DuelDecisionLines {
    /** Sequence of a {@code local} line, which belongs to no request. */
    static final long LOCAL_SEQUENCE = -1;

    /** Where and when a line is written. */
    record Stamp(long tNanos, long tick, int round, String arm) {
    }

    /** A mob of the capture with its side and kind. */
    record MobRef(UUID id, String side, String kind) {
    }

    private DuelDecisionLines() {
    }

    static String planned(long encounter, long sequence, Stamp stamp, String levelDetail, List<MobRef> mobs,
                          List<QuestionSpec> planned, Preparation preparation, DecisionDispatcher.Outcome dispatch) {
        var line = head("planned", String.valueOf(encounter), sequence, stamp.tNanos());
        line.put("tick", String.valueOf(stamp.tick()));
        line.put("round", String.valueOf(stamp.round()));
        line.put("arm", DuelRows.json(stamp.arm()));
        line.put("level_detail", DuelRows.json(levelDetail));
        line.put("mobs", array(mobs.stream().map(DuelDecisionLines::mob).toList()));
        line.put("questions", array(planned.stream().map(DuelDecisionLines::question).toList()));
        line.put("dispatch", DuelRows.json(dispatch.name()));
        line.put("dispatch_detail", preparation instanceof Preparation.Invalid invalid
                ? DuelRows.json(invalid.detail()) : "null");
        line.put("sent_options", sentOptions(preparation));
        line.put("dropped_questions", DuelRows.jsonArray(dropped(planned, preparation)));
        line.put("local_mobs", DuelRows.jsonArray(localMobs(planned, preparation)));
        return DuelRows.jsonObject(line);
    }

    static String body(long encounter, long sequence, Stamp stamp, String body) {
        var line = head("body", String.valueOf(encounter), sequence, stamp.tNanos());
        line.put("tick", String.valueOf(stamp.tick()));
        line.put("chars", String.valueOf(body.length()));
        line.put("body", DuelRows.json(body));
        return DuelRows.jsonObject(line);
    }

    static String result(long encounter, long sequence, Stamp stamp, DecisionResult result, long roundTripNanos) {
        var line = head("result", String.valueOf(encounter), sequence, stamp.tNanos());
        line.put("tick", String.valueOf(stamp.tick()));
        switch (result) {
            case DecisionResult.Answered answered -> {
                line.put("outcome", DuelRows.json("answered"));
                line.put("failure", "null");
                line.put("model", DuelRows.json(answered.effectiveModel()));
                line.put("input_tokens", String.valueOf(answered.inputTokens()));
                line.put("round_trip_ms", DuelRows.number(roundTripNanos / 1_000_000.0));
                line.put("answers", answers(answered.answers()));
                var fits = new LinkedHashMap<String, String>();
                answered.fits().forEach((key, fit) -> fits.put(key.value(), DuelRows.jsonNumber(fit)));
                line.put("fits", DuelRows.jsonObject(fits));
            }
            case DecisionResult.Failed failed -> {
                line.put("outcome", DuelRows.json("failed"));
                line.put("failure", DuelRows.json(failed.kind().name()));
                line.put("detail", DuelRows.json(failed.detail()));
                line.put("round_trip_ms", DuelRows.number(roundTripNanos / 1_000_000.0));
            }
        }
        return DuelRows.jsonObject(line);
    }

    static String acceptance(long encounter, long sequence, Stamp stamp, Optional<QuestionKey> question,
                             Optional<UUID> mob, String decision, Optional<String> level, Optional<String> tactic,
                             Optional<String> reason) {
        var line = head("acceptance", String.valueOf(encounter), sequence, stamp.tNanos());
        line.put("tick", String.valueOf(stamp.tick()));
        line.put("question", DuelRows.jsonText(question.map(QuestionKey::value)));
        line.put("mob", DuelRows.jsonText(mob.map(UUID::toString)));
        line.put("decision", DuelRows.json(decision));
        line.put("level", DuelRows.jsonText(level));
        line.put("tactic", DuelRows.jsonText(tactic));
        line.put("reason", DuelRows.jsonText(reason));
        return DuelRows.jsonObject(line);
    }

    static String local(Stamp stamp, UUID mob, String tactic, String level, Optional<String> reason) {
        var line = head("local", "null", LOCAL_SEQUENCE, stamp.tNanos());
        line.put("tick", String.valueOf(stamp.tick()));
        line.put("round", String.valueOf(stamp.round()));
        line.put("arm", DuelRows.json(stamp.arm()));
        line.put("mob", DuelRows.json(mob.toString()));
        line.put("tactic", DuelRows.json(tactic));
        line.put("level", DuelRows.json(level));
        line.put("reason", DuelRows.jsonText(reason));
        return DuelRows.jsonObject(line);
    }

    private static LinkedHashMap<String, String> head(String type, String encounter, long sequence, long tNanos) {
        var line = new LinkedHashMap<String, String>();
        line.put("type", DuelRows.json(type));
        line.put("encounter", encounter);
        line.put("sequence", String.valueOf(sequence));
        line.put("t_nanos", String.valueOf(tNanos));
        return line;
    }

    private static String mob(MobRef mob) {
        var fields = new LinkedHashMap<String, String>();
        fields.put("uuid", DuelRows.json(mob.id().toString()));
        fields.put("side", DuelRows.json(mob.side()));
        fields.put("kind", DuelRows.json(mob.kind()));
        return DuelRows.jsonObject(fields);
    }

    private static String question(QuestionSpec question) {
        var fields = new LinkedHashMap<String, String>();
        fields.put("key", DuelRows.json(question.key().value()));
        fields.put("mob", DuelRows.json(question.mob().entityId().toString()));
        fields.put("situation", DuelRows.json(question.situation().name()));
        fields.put("options", DuelRows.jsonArray(question.options().stream().map(TacticId::value).toList()));
        return DuelRows.jsonObject(fields);
    }

    private static String sentOptions(Preparation preparation) {
        var sent = new LinkedHashMap<String, String>();
        if (preparation instanceof Preparation.Ready ready) {
            ready.prepared().sent().questions().forEach(question -> sent.put(question.key().value(),
                    DuelRows.jsonArray(question.options().stream().map(TacticId::value).toList())));
        }
        return DuelRows.jsonObject(sent);
    }

    private static List<String> dropped(List<QuestionSpec> planned, Preparation preparation) {
        return switch (preparation) {
            case Preparation.Ready ready -> ready.prepared().droppedQuestions().stream().map(QuestionKey::value)
                    .sorted().toList();
            case Preparation.NothingToAsk ignored -> planned.stream().map(question -> question.key().value()).toList();
            case Preparation.Invalid ignored -> List.of();
        };
    }

    private static List<String> localMobs(List<QuestionSpec> planned, Preparation preparation) {
        var mobs = switch (preparation) {
            case Preparation.Ready ready -> ready.prepared().localMobs().stream().toList();
            case Preparation.NothingToAsk nothing -> nothing.localMobs().stream().toList();
            case Preparation.Invalid ignored -> planned.stream().filter(question -> question.situation()
                    == Situation.NOW).map(QuestionSpec::mob).toList();
        };
        return mobs.stream().map(MobKey::entityId).map(UUID::toString).sorted().distinct().toList();
    }

    private static String answers(Map<QuestionKey, Answer> answers) {
        var byKey = new LinkedHashMap<String, String>();
        var keys = new ArrayList<>(answers.keySet());
        keys.sort((first, second) -> Integer.compare(index(first), index(second)));
        for (var key : keys) {
            var answer = answers.get(key);
            var probabilities = new LinkedHashMap<String, String>();
            answer.ranked().forEach(tactic -> probabilities.put(tactic.value(),
                    DuelRows.jsonNumber(answer.probabilityOf(tactic))));
            var fields = new LinkedHashMap<String, String>();
            fields.put("choice", DuelRows.json(answer.best().value()));
            fields.put("probabilities", DuelRows.jsonObject(probabilities));
            fields.put("confidence", DuelRows.jsonNumber(answer.confidence()));
            byKey.put(key.value(), DuelRows.jsonObject(fields));
        }
        return DuelRows.jsonObject(byKey);
    }

    private static int index(QuestionKey key) {
        return Integer.parseInt(key.value().substring(1));
    }

    private static String array(List<String> jsonValues) {
        return "[" + String.join(",", jsonValues) + "]";
    }
}
