package com.solace.tools.solconfig;

import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class JsonMappersTest {
    private final JsonMapper mapper = JsonMappers.create();

    static class DeclarationOrderPojo {
        public int zebra = 1;
        public int apple = 2;
        public int mango = 3;
    }

    enum WireEnum {
        FIRST_VALUE;

        @Override
        public String toString() {
            return "first-value";
        }
    }

    static class PrimitiveHolder {
        public int count = 42;
    }

    @Test
    void serializesPropertiesInDeclarationOrderNotAlphabetical() {
        assertEquals("{\"zebra\":1,\"apple\":2,\"mango\":3}",
                mapper.writeValueAsString(new DeclarationOrderPojo()));
    }

    @Test
    void writesEnumsByNameNotToString() {
        assertEquals("\"FIRST_VALUE\"", mapper.writeValueAsString(WireEnum.FIRST_VALUE));
    }

    @Test
    void readsEnumsByNameAndRejectsToStringForm() {
        assertEquals(WireEnum.FIRST_VALUE, mapper.readValue("\"FIRST_VALUE\"", WireEnum.class));
        assertThrows(JacksonException.class, () -> mapper.readValue("\"first-value\"", WireEnum.class));
    }

    @Test
    void deserializesNullOntoPrimitiveAsDefault() {
        assertEquals(0, mapper.readValue("{\"count\":null}", PrimitiveHolder.class).count);
    }

    @Test
    void pinsJackson2CreatorAndFeatureDefaults() {
        assertFalse(mapper.isEnabled(MapperFeature.DETECT_PARAMETER_NAMES));
        assertFalse(mapper.isEnabled(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY));
        assertFalse(mapper.isEnabled(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS));
        assertFalse(mapper.isEnabled(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES));
        assertFalse(mapper.isEnabled(DeserializationFeature.FAIL_ON_TRAILING_TOKENS));
        assertFalse(mapper.isEnabled(EnumFeature.READ_ENUMS_USING_TO_STRING));
        assertFalse(mapper.isEnabled(EnumFeature.WRITE_ENUMS_USING_TO_STRING));
    }

    @Test
    void preservesMapInsertionOrder() {
        Map<String, Integer> map = new LinkedHashMap<>();
        map.put("zebra", 1);
        map.put("apple", 2);
        assertEquals("{\"zebra\":1,\"apple\":2}", mapper.writeValueAsString(map));
    }

    @Test
    void throwsJacksonExceptionOnMalformedJson() {
        assertThrows(JacksonException.class, () -> mapper.readTree("{invalid"));
    }
}
