package com.solace.tools.solconfig;

import com.solace.tools.solconfig.model.SempMeta;
import com.solace.tools.solconfig.model.SempResponse;
import org.junit.jupiter.api.Test;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SempClientTest {

    @Test
    void mergeResponses_bothPopulated_concatenatesDataAndLinks() {
        SempResponse acc = responseWith(List.of(Map.of("q", "q1")), List.of(Map.of("uri", "u1")));
        SempResponse page = responseWith(List.of(Map.of("q", "q2")), List.of(Map.of("uri", "u2")));

        SempResponse result = SempClient.mergeResponses(acc, page);

        assertSame(acc, result);
        assertEquals(2, result.getData().size());
        assertEquals(2, result.getLinks().size());
        assertEquals("q1", result.getData().get(0).get("q"));
        assertEquals("q2", result.getData().get(1).get("q"));
    }

    @Test
    void mergeResponses_pageHasEmptyDataAndLinks_returnsAccUnchanged() {
        SempResponse acc = responseWith(List.of(Map.of("q", "q1")), List.of(Map.of("uri", "u1")));
        SempResponse page = new SempResponse();

        SempResponse result = SempClient.mergeResponses(acc, page);

        assertSame(acc, result);
        assertEquals(1, result.getData().size());
        assertEquals(1, result.getLinks().size());
    }

    @Test
    void mergeResponses_accHasEmptyDataAndLinks_takesFromPage() {
        SempResponse acc = new SempResponse();
        SempResponse page = responseWith(List.of(Map.of("q", "q1")), List.of(Map.of("uri", "u1")));

        SempResponse result = SempClient.mergeResponses(acc, page);

        assertSame(acc, result);
        assertEquals(1, result.getData().size());
        assertEquals(1, result.getLinks().size());
    }

    @Test
    void mergeResponses_pageHasMeta_overwritesAccMeta() {
        SempResponse acc = new SempResponse();
        SempMeta accMeta = new SempMeta();
        accMeta.setResponseCode(200);
        acc.setMeta(accMeta);

        SempResponse page = new SempResponse();
        SempMeta pageMeta = new SempMeta();
        pageMeta.setResponseCode(204);
        page.setMeta(pageMeta);

        SempClient.mergeResponses(acc, page);

        assertNotNull(acc.getMeta());
        assertEquals(204, acc.getMeta().getResponseCode());
    }

    @Test
    void mergeResponses_pageNullMeta_preservesAccMeta() {
        SempResponse acc = new SempResponse();
        SempMeta accMeta = new SempMeta();
        accMeta.setResponseCode(200);
        acc.setMeta(accMeta);

        SempResponse page = new SempResponse();

        SempClient.mergeResponses(acc, page);

        assertNotNull(acc.getMeta());
        assertEquals(200, acc.getMeta().getResponseCode());
    }

    @Test
    void mergeResponses_reproducesProductionNpeScenario_doesNotThrow() {
        // Production case: a paginated response page came back with empty/null data and links.
        // Pre-fix this triggered NPE inside addAll. After fix, SempResponse guarantees
        // non-null collections, so merge is safe.
        SempResponse acc = new SempResponse();
        SempResponse emptyPage = new SempResponse();

        SempResponse result = SempClient.mergeResponses(acc, emptyPage);

        assertTrue(result.getData().isEmpty());
        assertTrue(result.getLinks().isEmpty());
    }

    private static SempResponse responseWith(List<Map<String, Object>> data,
                                             List<Map<String, String>> links) {
        SempResponse r = new SempResponse();
        r.setData(new LinkedList<>(data));
        r.setLinks(new LinkedList<>(links));
        return r;
    }
}
