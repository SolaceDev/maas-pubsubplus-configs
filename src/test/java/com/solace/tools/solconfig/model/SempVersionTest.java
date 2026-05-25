package com.solace.tools.solconfig.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class SempVersionTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "2.35",
            "2.22",
            "2.11.00091010036",
            "10.25.1.123",
            "10.25"
    })
    void shouldParseValidVersions(String version) {
        SempVersion sempVersion = new SempVersion(version);
        assertEquals(version, sempVersion.getText());
    }

    @ParameterizedTest
    @CsvSource({
            "2.35, 2035",
            "2.22, 2022",
            "10.25, 10025",
            "10.25.1.123, 10025"
    })
    void shouldCalculateCorrectNumber(String version, int expectedNumber) {
        SempVersion sempVersion = new SempVersion(version);
        assertEquals(expectedNumber, getNumber(sempVersion));
    }

    @Test
    void shouldCompareVersionsCorrectly() {
        SempVersion oldVersion = new SempVersion("2.35");
        SempVersion newVersion = new SempVersion("10.25.1.123");

        assertTrue(newVersion.compareTo(oldVersion) > 0);
        assertTrue(oldVersion.compareTo(newVersion) < 0);
    }

    @Test
    void shouldCompareEqualVersions() {
        SempVersion v1 = new SempVersion("10.25");
        SempVersion v2 = new SempVersion("10.25.1.123");

        assertEquals(0, v1.compareTo(v2));
    }

    @ParameterizedTest
    @CsvSource({
            "2.19, 2.35, -1",
            "2.35, 10.25, -1",
            "10.25, 2.35, 1",
            "2.35, 2.35, 0"
    })
    void shouldCompareVersionPairs(String v1, String v2, int expectedSign) {
        SempVersion version1 = new SempVersion(v1);
        SempVersion version2 = new SempVersion(v2);

        int result = version1.compareTo(version2);
        assertEquals(expectedSign, Integer.signum(result));
    }

    // Dev-build brokers report minor segments like "0SOL-143449" or "0main"; these should
    // parse without throwing and compute the same number as the plain numeric equivalent.
    @ParameterizedTest
    @CsvSource({
            "100.0main.0.7706, 100.0",
            "100.0SOL-143449.0.7010, 100.0"
    })
    void devBuildMinorSegment_parsesAsLeadingInt(String devVersion, String equivalentVersion) {
        assertDoesNotThrow(() -> new SempVersion(devVersion));
        // number = major*1000 + leadingInt(minor); "0main"/"0SOL-..." both lead with 0
        assertEquals(0, new SempVersion(devVersion).compareTo(new SempVersion(equivalentVersion)));
    }

    // Regression: standard versions with a numeric minor must still compute correctly.
    @Test
    void regression_standardVersion_9_6_producesCorrectNumber() {
        SempVersion v = new SempVersion("9.6.0.34");
        assertEquals(9006, getNumber(v));
    }

    // A segment with no leading digits (e.g. "main.0") has no numeric prefix to parse,
    // so errPrintlnAndExit is called (throws SolConfigException in non-exit test mode).
    @Test
    void noLeadingDigits_callsErrPrintlnAndExit() {
        assertThrows(SolConfigException.class, () -> new SempVersion("main.0"));
    }

    private int getNumber(SempVersion sempVersion) {
        String text = sempVersion.getText();
        String[] parts = text.split("\\.");
        return Integer.parseInt(parts[0]) * 1000 + Integer.parseInt(parts[1]);
    }
}