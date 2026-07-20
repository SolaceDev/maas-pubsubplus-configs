package com.solace.tools.solconfig;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Jackson2ImportGuardTest {

    @Test
    void noJackson2ImportsInSources() throws IOException {
        try (Stream<Path> paths = Files.walk(Path.of("src"))) {
            List<String> offenders = paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(this::hasJackson2Import)
                    .map(Path::toString)
                    .collect(Collectors.toList());
            assertEquals(List.of(), offenders);
        }
    }

    @Test
    void jackson2RuntimeIslandMeetsSecurityFloor() throws Exception {
        Class<?> packageVersion;
        try {
            packageVersion = Class.forName("com.fasterxml.jackson.databind.cfg.PackageVersion");
        } catch (ClassNotFoundException e) {
            return;
        }
        Object version = packageVersion.getField("VERSION").get(null);
        int major = (int) version.getClass().getMethod("getMajorVersion").invoke(version);
        int minor = (int) version.getClass().getMethod("getMinorVersion").invoke(version);
        int patch = (int) version.getClass().getMethod("getPatchLevel").invoke(version);
        assertEquals(2, major);
        assertTrue(minor > 18 || (minor == 18 && patch >= 6),
                "Jackson 2 island below DATAGO-139728 floor 2.18.6: " + major + "." + minor + "." + patch);
    }

    private boolean hasJackson2Import(Path file) {
        try {
            return Files.readAllLines(file).stream()
                    .anyMatch(line -> line.startsWith("import com.fasterxml.jackson")
                            && !line.startsWith("import com.fasterxml.jackson.annotation"));
        } catch (IOException e) {
            throw new UncheckedIOException(file.toString(), e);
        }
    }
}
