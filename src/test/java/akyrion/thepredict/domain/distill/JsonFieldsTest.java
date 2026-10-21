package akyrion.thepredict.domain.distill;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonFieldsTest {
    @Test
    void onlyTopLevelScalarsAreRead() {
        var fields = JsonFields.topLevel(" {\"a\":{\"status\":\"PASS\",\"x\":[1,{\"y\":\"}\"}]},\"status\":\"FAIL\","
                + "\"n\":-1.5e3,\"t\":true,\"z\":null,\"s\":\"q\\\"\\u0041\\n\"} ");
        assertEquals(Map.of("status", "FAIL", "n", "-1.5e3", "t", "true", "z", "null", "s", "q\"A\n"), fields);
        assertEquals(Map.of(), JsonFields.topLevel("{}"));
    }

    @Test
    void malformedTextAndRepeatedNamesAreRefused() {
        for (var text : new String[] {"", "[]", "{", "{\"a\":1", "{\"a\":1}x", "{\"a\" 1}", "{\"a\":tru}",
                "{\"a\":1,}", "{\"a\":\"\u0001\"}", "{\"a\":\"\\x\"}", "{\"a\":1,\"a\":2}", "{\"a\":{},\"a\":2}",
                "{\"a\":[1,2}", "{a:1}"}) {
            assertThrows(IllegalArgumentException.class, () -> JsonFields.topLevel(text), text);
        }
    }
}
