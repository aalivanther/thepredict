package akyrion.thepredict.adapter.server.duel;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DuelRowsTest {
    @Test
    void csvCellsFollowRfc4180AndEmptyValuesGiveEmptyCells() {
        var id = UUID.fromString("00000000-0000-0000-0000-000000000001");
        assertEquals("1,a,\"b,c\",\"say \"\"hi\"\"\",,," + id + ",0.5,,RED",
                DuelRows.csv(1, "a", "b,c", "say \"hi\"", Optional.empty(), OptionalDouble.empty(), id, 0.5,
                        Double.NaN, Side.RED));
        assertEquals("\"two\nlines\"", DuelRows.cell("two\nlines"));
    }

    @Test
    void numbersKeepFourDecimalsWithADotAndNoExponent() {
        assertEquals("0.3333", DuelRows.number(1.0 / 3));
        assertEquals("12", DuelRows.number(12.0));
        assertEquals("0", DuelRows.number(-0.00001));
        assertEquals("1500000000", DuelRows.number(1.5e9));
        assertEquals("", DuelRows.number(Double.POSITIVE_INFINITY));
    }

    @Test
    void jsonTextIsEscapedAndObjectsKeepTheirOrder() {
        assertEquals("\"a\\\"b\\\\c\\n\\u0001\"", DuelRows.json("a\"b\\c\n\u0001"));
        var members = new LinkedHashMap<String, String>();
        members.put("z", "1");
        members.put("a", DuelRows.jsonArray(List.of("x", "y")));
        members.put("n", DuelRows.jsonText(Optional.empty()));
        assertEquals("{\"z\":1,\"a\":[\"x\",\"y\"],\"n\":null}", DuelRows.jsonObject(members));
        assertEquals("null", DuelRows.jsonNumber(Double.NaN));
    }

    @Test
    void everyCsvFileHasAHeaderThatStartsWithTheTickRoundAndArmOrTheRound() {
        for (var file : DuelFile.values()) {
            if (file == DuelFile.DECISIONS || file == DuelFile.SUMMARY) {
                assertTrue(file.header().isEmpty(), file.name());
            } else if (file == DuelFile.ROUNDS) {
                assertTrue(file.header().orElseThrow().startsWith("round,arm,"), file.name());
            } else {
                assertTrue(file.header().orElseThrow().startsWith("tick,round,arm,"), file.name());
            }
        }
    }

    private enum Side {
        RED
    }
}
