package com.solace.tools.solconfig;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UtilsTest {

    private String body = "{" +
                          "\"data\": {" +
                          "\"somePassword\": \"asdfasdf\"," +
                          "\"password\":  \"42345234\"," +
                          "\"topicEndpointsUri\": \"https://mr-connection-mwdinssvzdp.messaging.solace.cloud:943/SEMP/v2/config/msgVpns/serv1/topicEndpoints\"," +
                          "\"uri\": \"https://mr-connection-mwdinssvzdp.messaging.solace.cloud:943/SEMP/v2/config/msgVpns/serv1\"" +
                          "}," +
                          "\"meta\":{" +
                          "\"request\":{" +
                          "\"method\": \"PUT\"," +
                          "\"uri\": \"https://mr-connection-mwdinssvzdp.messaging.solace.cloud:943/SEMP/v2/config/msgVpns/serv1?opaquePassword=asdfasasd\"" +
                          "}," +
                          "\"responseCode\":200" +
                          "}" +
                          "}";

    private String certificateBody = "{\n" +
                                     "        \"certAuthorityName\": \"sapcloudintegration\",\n" +
                                     "            \"certContent\": \"*----BEGIN CERTIFICATE-----\n" +
                                     "        certcertcert\n" +
                                     "                -----END CERTIFICATE-----\n" +
                                     "                         \"\n" +
                                     "    }";

    private String expectedBody = "Sensitive data, omitted for logging";


    @Test
    void blurPasswords() {
        String uri = "https://localhost:8080/SEMP/v2/config/msgVpns/default/queues/queue1?opaquePassword=1234";
        String expectedUri = "https://localhost:8080/SEMP/v2/config/msgVpns/default/queues/queue1?opaquePassword=*****";

        String actual = Utils.sanitizeUrl(uri);

        assertEquals(expectedUri, actual);
    }

    @Test
    void returnTheSameIfNoPasswords() {
        String uri = "https://localhost:8080/SEMP/v2/config/msgVpns/default/queues/queue1";

        String actual = Utils.sanitizeUrl(uri);

        assertEquals(uri, actual);
    }

    @Test
    void returnNullIfDataIsNull() {
        String uri = null;

        String actual = Utils.sanitizeUrl(uri);

        assertEquals(uri, actual);
    }

    @Test
    void blurPasswordsInJsonFields() {
        String actual = Utils.sanitizeBody(
                Utils.sanitizeUrl(body)
        );

        assertEquals(expectedBody, actual);
    }

    @Test
    void blurPasswordsInCertificateFields() {
        String actual = Utils.sanitizeBody(
                Utils.sanitizeUrl(certificateBody)
        );

        assertEquals(expectedBody, actual);
    }
}