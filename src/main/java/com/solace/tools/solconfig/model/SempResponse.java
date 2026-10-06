package com.solace.tools.solconfig.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.Getter;
import lombok.Setter;
import com.solace.tools.solconfig.Utils;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.solace.tools.solconfig.Utils.objectMapper;

@Getter
@Setter
public class SempResponse {
    private List<Map<String, Object>> data = new LinkedList<>();
    private List<Map<String, String >> links = new LinkedList<>();
    private SempMeta meta;

    public static SempResponse ofString(String content){
        SempResponse resp = new SempResponse();
        try {
            var node = objectMapper.readTree(content);
            List<Map<String, Object>> parsedData = objectMapper.treeToValue(node.get("data"), List.class);
            List<Map<String, String>> parsedLinks = objectMapper.treeToValue(node.get("links"), List.class);
            resp.data = parsedData != null ? parsedData : new LinkedList<>();
            resp.links = parsedLinks != null ? parsedLinks : new LinkedList<>();
            resp.meta = SempMeta.ofJsonNode(node.get("meta"));
        } catch (JsonProcessingException e) {
            Utils.errPrintlnAndExit(e,
                    "Unable to convert below string into SempResponse structure!%n%s",
                    content);
        }
        return resp;
    }

    public Optional<String> getNextPageUri(){
        return Optional.ofNullable(meta).map(SempMeta::getPaging).map(SempMeta.SempPaging::getNextPageUri);
    }

    public boolean isEmpty(){
        return data.isEmpty();
    }

    @Override
    public String toString() {
        return Utils.toPrettyJson(this);
    }
}
