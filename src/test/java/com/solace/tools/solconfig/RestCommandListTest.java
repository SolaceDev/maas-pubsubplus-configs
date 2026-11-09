package com.solace.tools.solconfig;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.solace.tools.solconfig.model.HTTPMethod;
import com.solace.tools.solconfig.model.SEMPError;
import com.solace.tools.solconfig.model.SempMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestCommandListTest {

    private final Logger utilsLogger = (Logger) LoggerFactory.getLogger(Utils.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Level previousLevel;

    @BeforeEach
    void attachAppender() {
        previousLevel = utilsLogger.getLevel();
        utilsLogger.setLevel(Level.INFO);
        appender.start();
        utilsLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        utilsLogger.detachAppender(appender);
        utilsLogger.setLevel(previousLevel);
    }

    static Stream<Arguments> retryableErrors() {
        return Stream.of(
                Arguments.of(HTTPMethod.POST, "/msgVpns/vpn1/clientProfiles", SEMPError.NOT_FOUND),
                Arguments.of(HTTPMethod.DELETE, "/msgVpns/vpn1/aclProfiles/acl1", SEMPError.NOT_ALLOWED),
                Arguments.of(HTTPMethod.DELETE, "/msgVpns/vpn1/aclProfiles/acl1", SEMPError.CONFIGDB_OBJECT_DEPENDENCY));
    }

    @ParameterizedTest
    @MethodSource("retryableErrors")
    void transientErrorLogsWarnWithMethodAndPath(HTTPMethod method, String path, SEMPError error) {
        var sempClient = new ScriptedSempClient(error.getValue(), 0);
        var commands = new RestCommandList();
        commands.append(method, path, method == HTTPMethod.POST ? "{\"clientProfileName\":\"p1\"}" : null);

        commands.execute(sempClient, false);

        assertEquals(2, sempClient.calls);
        assertNoErrorAndRetryWarns(List.of(error + " on " + method + " " + path + ", retry later"));
    }

    @Test
    void retryThatFailsTwiceLogsOneWarnPerRound() {
        int notFound = SEMPError.NOT_FOUND.getValue();
        var sempClient = new ScriptedSempClient(notFound, notFound, 0);
        var commands = new RestCommandList();
        commands.append(HTTPMethod.POST, "/msgVpns/vpn1/clientUsernames", "{\"clientUsername\":\"u1\"}");

        commands.execute(sempClient, false);

        assertEquals(3, sempClient.calls);
        var expected = "NOT_FOUND on POST /msgVpns/vpn1/clientUsernames, retry later";
        assertNoErrorAndRetryWarns(List.of(expected, expected));
    }

    private void assertNoErrorAndRetryWarns(List<String> expectedMessages) {
        List<ILoggingEvent> events = appender.list;
        var errors = events.stream().filter(e -> e.getLevel() == Level.ERROR)
                .map(ILoggingEvent::getFormattedMessage).collect(Collectors.toList());
        assertTrue(errors.isEmpty(), "a successful retry must not log ERROR, got: " + errors);
        var warns = events.stream().filter(e -> e.getLevel() == Level.WARN)
                .map(ILoggingEvent::getFormattedMessage).collect(Collectors.toList());
        assertEquals(expectedMessages, warns);
    }

    /**
     * Returns the given SEMP error codes in order (0 = OK), without any HTTP call.
     */
    private static class ScriptedSempClient extends SempClient {
        private final Deque<Integer> sempCodes = new ArrayDeque<>();
        int calls;

        ScriptedSempClient(Integer... sempCodes) {
            super("http://localhost:0", "admin", "admin", false, null);
            this.sempCodes.addAll(List.of(sempCodes));
        }

        @Override
        public SempMeta sendAndGetMeta(String method, String resourcePath, String payload) {
            calls++;
            int code = sempCodes.isEmpty() ? 0 : sempCodes.poll();
            var meta = new SempMeta();
            if (code == 0) {
                meta.setResponseCode(200);
            } else {
                meta.setResponseCode(400);
                var error = new SempMeta.SempError();
                error.setCode(code);
                error.setStatus(SEMPError.ofInt(code).name());
                meta.setError(error);
            }
            return meta;
        }
    }
}
