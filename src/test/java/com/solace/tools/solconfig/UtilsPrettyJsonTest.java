package com.solace.tools.solconfig;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class UtilsPrettyJsonTest {

    @Test
    void prettyJsonKeepsJackson2Format() {
        assertEquals("{\n  \"name\" : \"q1\",\n  \"owners\" : [ \"a\", \"b\" ]\n}",
                normalize(Utils.toPrettyJson(sample())));
    }

    @Test
    void prettyJsonMultiLineArrayKeepsJackson2Format() {
        assertEquals("{\n  \"name\" : \"q1\",\n  \"owners\" : [\n    \"a\",\n    \"b\"\n  ]\n}",
                normalize(Utils.toPrettyJsonMultiLineArray(sample())));
    }

    private Map<String, Object> sample() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", "q1");
        map.put("owners", List.of("a", "b"));
        return map;
    }

    private String normalize(String json) {
        return json.replace(System.lineSeparator(), "\n");
    }
}
