package com.solace.tools.solconfig;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.solace.tools.solconfig.model.SempSpec;
import com.solace.tools.solconfig.model.SolConfigException;
import lombok.extern.slf4j.Slf4j;
import net.logstash.logback.marker.Markers;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
public class Utils {
    public static ObjectMapper objectMapper = new ObjectMapper();
    public static Properties properties = PropertiesLoader.loadProperties("application.properties");

    public static String sanitizeUrl(String data) {
        if (data != null && data.contains(SempSpec.OPAQUE_PASSWORD + "=")) {
            return data.replaceAll("(opaquePassword=)[^&\"]*([&\"]|$)", "$1*****$2");
        }
        return data;
    }

    public static String sanitizeBody(String body) {
        List<String> sensitiveStrings = List.of(
                "password",
                "begin certificate",
                // Haven't actually seen the following, AI generated them
                // trying to guess other sensitive data
                "token",
                "apiKey",
                "api_secret",
                "api_secret_key",
                "privatekey",
                "private key"
        );
        if (body == null) {
            return null;
        }

        for (String sensitiveString : sensitiveStrings) {
            if (body.toLowerCase().contains(sensitiveString)) {
                log.info(Markers.append("sensitive_string", sensitiveString),
                        "Sensitive data found in body");
                return "Sensitive data, omitted for logging";
            }
        }
        return body;
    }

    // TODO: move to SempSpec class
    public static String getCollectionNameFromUri(String uri) {
        String[] items = uri.split("/");
        return items[items.length - 1].split("\\?")[0];
    }

    public static Optional<String> getFirstMatch(String input, Pattern re) {
        var m = re.matcher(input);
        if (m.find()) {
            return Optional.of(m.group(1));
        } else {
            return Optional.empty();
        }
    }

    public static void log(String text) {
        log.info(text);
    }

    public static void err(String format, Object... args) {
        log.error(String.format(format, args));
    }

    public static void errPrintlnAndExit(String format, Object... args) {
        errPrintlnAndExit(null, format, args);
    }

    public static void errPrintlnAndExit(Exception e, String format, Object... args) {
        err(format, args);
        if (Objects.nonNull(e)) {
            log.error(e.getMessage(), e);
        }
        if (!isExitOnErrors()) {
            throw new SolConfigException("Error when executing broker config command: " + String.format(format, args), e);
        }
        System.exit(1);
    }

    public static Set<Map.Entry<String, Object>> symmetricDiff(Set<Map.Entry<String, Object>> s1, Set<Map.Entry<String, Object>> s2) {
        var symmetricDiff = new HashSet<>(s1);
        symmetricDiff.addAll(s2);
        var tmp = new HashSet<>(s1);
        tmp.retainAll(s2);
        symmetricDiff.removeAll(tmp);

        return symmetricDiff;
    }

    public static String toPrettyJson(Object obj) {
        String result = null;
        try {
            result = Utils.objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            errPrintlnAndExit(e, "Unable to convert the object into the json format.");
        }
        return result;
    }

    public static String toPrettyJsonMultiLineArray(Object obj) {
        String result = null;
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.enable(SerializationFeature.INDENT_OUTPUT);

            DefaultPrettyPrinter prettyPrinter = new DefaultPrettyPrinter();
            prettyPrinter.indentArraysWith(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE);
            result = objectMapper.writer(prettyPrinter).writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            errPrintlnAndExit(e, "Unable to convert the object into the json format.");
        }
        return result;
    }

    public static boolean isExitOnErrors() {
        if ("true".equalsIgnoreCase(properties.getProperty("solace.tools.solconfig.exitOnErrors"))) {
            return true;
        }
        return false;
    }
}
