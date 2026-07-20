package com.solace.tools.solconfig;

import tools.jackson.databind.json.JsonMapper;

public final class JsonMappers {

    private JsonMappers() {
    }

    public static JsonMapper create() {
        return builder().build();
    }

    public static JsonMapper.Builder builder() {
        return JsonMapper.builderWithJackson2Defaults();
    }
}
