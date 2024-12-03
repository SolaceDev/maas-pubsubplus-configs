package com.solace.tools.solconfig.model;

import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;

public class SempSpecTest {
    Logger log = Logger.getLogger(SempSpecTest.class.getName());
    String defaultJsonPath = "/semp-v2-config-2.19.json";

    static void setup(String filePath) throws IOException {
        var jsonString = Files.readString(Path.of(SempSpecTest.class.getResource(filePath).getPath()));
        SempSpec.setupByString(jsonString);
    }

    @ParameterizedTest
    @CsvSource({
            "/dmrClusters, /dmrClusters",
            "/msgVpns/{msgVpnName}/aclProfiles, /msgVpns/aclProfiles",
            "'/msgVpns/{msgVpnName}/aclProfiles/{aclProfileName}/publishTopicExceptions/{publishTopicExceptionSyntax},{publishTopicException}', /msgVpns/aclProfiles/publishTopicExceptions"
    })
    @SneakyThrows
    void testGenerateSpecPath(String path, String expected) {
        setup(defaultJsonPath);
        assertEquals(expected, SempSpec.generateSpecPath(path));
    }


    @Test
    @SneakyThrows
    void testOfJsonNode() {
        setup(defaultJsonPath);
        SempSpec.sempSpecMap.keySet().forEach(log::info);
    }

    @ParameterizedTest
    @CsvSource({
            "'/semp-v2-config-2.19.json', dmrClusters, dmrClusterName",
            "'/semp-v2-config-2.19.json', msgVpns, msgVpnName",
            "'/semp-v2-config-2.19.json', clientCertAuthorities, certAuthorityName",
            "'/semp-v2-config-2.39.json', dmrClusters, dmrClusterName",
            "'/semp-v2-config-2.39.json', msgVpns, msgVpnName",
            "'/semp-v2-config-2.39.json', clientCertAuthorities, certAuthorityName"
    })
    @SneakyThrows
    void testGetTopResourceIdentifierKey(String filePath, String topName, String expected) {
        setup(filePath);
        assertEquals(expected, SempSpec.getTopResourceIdentifierKey(topName));
    }

    @ParameterizedTest
    @MethodSource("testGetRequiresAttributeWithDefaultValueProvider")
    @SneakyThrows
    void testGetRequiresAttributeWithDefaultValue(String specPath, Set<String> attributes, Map<String, Object> expected){
        setup(defaultJsonPath);
        var sempSpec = SempSpec.sempSpecMap.get(specPath);
        var result = sempSpec.getRequiresAttributeWithDefaultValue(attributes);
        assertEquals(expected, result);
    }

    static Stream<Arguments> testGetRequiresAttributeWithDefaultValueProvider() {
        return Stream.of(
                arguments("/msgVpns/restDeliveryPoints/restConsumers", Set.of("remotePort", "tlsEnabled"), Map.of()),
                arguments("/msgVpns/restDeliveryPoints/restConsumers", Set.of("remotePort"), Map.of("tlsEnabled", false)),
                arguments("/msgVpns/restDeliveryPoints/restConsumers", Set.of("tlsEnabled"), Map.of("remotePort", 8080)),
                arguments("/msgVpns/restDeliveryPoints/restConsumers", Set.of("restConsumerName"), Map.of()),
                arguments("/msgVpns/restDeliveryPoints/restConsumers", Set.of(), Map.of())
        );
    }
}
