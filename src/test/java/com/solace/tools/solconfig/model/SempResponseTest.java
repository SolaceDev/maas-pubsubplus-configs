package com.solace.tools.solconfig.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SempResponseTest {

    private static final String FULL_RESPONSE = "{" +
            "\"data\":[{\"queueName\":\"q1\"},{\"queueName\":\"q2\"}]," +
            "\"links\":[{\"uri\":\"u1\"},{\"uri\":\"u2\"}]," +
            "\"meta\":{\"responseCode\":200,\"request\":{\"method\":\"GET\",\"uri\":\"/x\"}}" +
            "}";

    private static final String NULL_DATA_RESPONSE = "{" +
            "\"data\":null," +
            "\"links\":[{\"uri\":\"u1\"}]," +
            "\"meta\":{\"responseCode\":200,\"request\":{\"method\":\"GET\",\"uri\":\"/x\"}}" +
            "}";

    private static final String NULL_LINKS_RESPONSE = "{" +
            "\"data\":[{\"queueName\":\"q1\"}]," +
            "\"links\":null," +
            "\"meta\":{\"responseCode\":200,\"request\":{\"method\":\"GET\",\"uri\":\"/x\"}}" +
            "}";

    private static final String BOTH_NULL_RESPONSE = "{" +
            "\"data\":null," +
            "\"links\":null," +
            "\"meta\":{\"responseCode\":200,\"request\":{\"method\":\"GET\",\"uri\":\"/x\"}}" +
            "}";

    private static final String MISSING_FIELDS_RESPONSE = "{" +
            "\"meta\":{\"responseCode\":200,\"request\":{\"method\":\"GET\",\"uri\":\"/x\"}}" +
            "}";

    private static final String NO_META_RESPONSE = "{" +
            "\"data\":[{\"queueName\":\"q1\"}]," +
            "\"links\":[{\"uri\":\"u1\"}]" +
            "}";

    @Test
    void defaultConstructor_dataAndLinksAreEmptyNotNull() {
        SempResponse resp = new SempResponse();

        assertNotNull(resp.getData());
        assertNotNull(resp.getLinks());
        assertTrue(resp.getData().isEmpty());
        assertTrue(resp.getLinks().isEmpty());
    }

    @Test
    void ofString_validResponse_populatesAllFields() {
        SempResponse resp = SempResponse.ofString(FULL_RESPONSE);

        assertEquals(2, resp.getData().size());
        assertEquals(2, resp.getLinks().size());
        assertNotNull(resp.getMeta());
        assertEquals(200, resp.getMeta().getResponseCode());
    }

    @Test
    void ofString_nullDataField_returnsEmptyList() {
        SempResponse resp = SempResponse.ofString(NULL_DATA_RESPONSE);

        assertNotNull(resp.getData());
        assertTrue(resp.getData().isEmpty());
        assertEquals(1, resp.getLinks().size());
    }

    @Test
    void ofString_nullLinksField_returnsEmptyList() {
        SempResponse resp = SempResponse.ofString(NULL_LINKS_RESPONSE);

        assertNotNull(resp.getLinks());
        assertTrue(resp.getLinks().isEmpty());
        assertEquals(1, resp.getData().size());
    }

    @Test
    void ofString_bothDataAndLinksNull_returnsEmptyLists() {
        SempResponse resp = SempResponse.ofString(BOTH_NULL_RESPONSE);

        assertNotNull(resp.getData());
        assertNotNull(resp.getLinks());
        assertTrue(resp.getData().isEmpty());
        assertTrue(resp.getLinks().isEmpty());
    }

    @Test
    void ofString_missingDataAndLinksKeys_returnsEmptyLists() {
        SempResponse resp = SempResponse.ofString(MISSING_FIELDS_RESPONSE);

        assertNotNull(resp.getData());
        assertNotNull(resp.getLinks());
        assertTrue(resp.getData().isEmpty());
        assertTrue(resp.getLinks().isEmpty());
    }

    @Test
    void ofString_missingMeta_metaIsNull() {
        SempResponse resp = SempResponse.ofString(NO_META_RESPONSE);

        assertNull(resp.getMeta());
        assertEquals(1, resp.getData().size());
    }

    @Test
    void isEmpty_afterOfStringWithNullData_returnsTrue() {
        SempResponse resp = SempResponse.ofString(NULL_DATA_RESPONSE);

        assertTrue(resp.isEmpty());
    }

    @Test
    void isEmpty_afterOfStringWithDataPresent_returnsFalse() {
        SempResponse resp = SempResponse.ofString(FULL_RESPONSE);

        assertFalse(resp.isEmpty());
    }

    @Test
    void isEmpty_afterDefaultConstructor_returnsTrue() {
        SempResponse resp = new SempResponse();

        assertTrue(resp.isEmpty());
    }

    @Test
    void getNextPageUri_metaIsNull_returnsEmptyOptional() {
        SempResponse resp = SempResponse.ofString(NO_META_RESPONSE);

        assertTrue(resp.getNextPageUri().isEmpty());
    }

    @Test
    void getNextPageUri_metaPresentButPagingMissing_returnsEmptyOptional() {
        SempResponse resp = SempResponse.ofString(FULL_RESPONSE);

        assertTrue(resp.getNextPageUri().isEmpty());
    }

    @Test
    void getNextPageUri_pagingPresent_returnsNextPageUri() {
        String paginated = "{" +
                "\"data\":[]," +
                "\"links\":[]," +
                "\"meta\":{\"responseCode\":200,\"paging\":{\"nextPageUri\":\"https://broker/SEMP/v2/config/page2\"}}" +
                "}";

        SempResponse resp = SempResponse.ofString(paginated);

        assertTrue(resp.getNextPageUri().isPresent());
        assertEquals("https://broker/SEMP/v2/config/page2", resp.getNextPageUri().get());
    }
}
