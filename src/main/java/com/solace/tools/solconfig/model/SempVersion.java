package com.solace.tools.solconfig.model;

import com.solace.tools.solconfig.Utils;
import lombok.Getter;

import java.util.Objects;

public class SempVersion implements Comparable<SempVersion>{
    @Getter
    private String text;
    private int   number;

    public SempVersion(String version) {
        if (Objects.isNull(version)){
            Utils.errPrintlnAndExit(new IllegalArgumentException("Null is an illegal SEMPv2 version."),"Unable to new a SempVersion object");
        }
        this.text = version;
        String[] v = version.split("\\.");

        // both "2.22" and "2.11.00091010036" are legal version, check https://github.com/flyisland/solconfig/issues/3
        if (v.length < 2) {
            Utils.errPrintlnAndExit(new IllegalArgumentException(version+" is an illegal SEMPv2 version."),"Unable to new a SempVersion object");
        }
        try {
            // dev-build minor segments like "0SOL-143449" or "0main" parse to their leading int (0)
            number = parseLeadingInt(v[0]) * 1000 + parseLeadingInt(v[1]);
        }catch (NumberFormatException e){
            Utils.errPrintlnAndExit(new IllegalArgumentException(version+" is an illegal SEMPv2 version."),"Unable to new a SempVersion object");
        }
    }

    // Parses the leading numeric prefix of a version segment, stopping at the first
    // non-digit character.  Mirrors EventBrokerVersionUtil.parseLeadingInt in maas-base.
    private static int parseLeadingInt(String segment) {
        int i = 0;
        while (i < segment.length() && Character.isDigit(segment.charAt(i))) {
            i++;
        }
        if (i == 0) {
            throw new NumberFormatException("For input string: \"" + segment + "\"");
        }
        return Integer.parseInt(segment.substring(0, i));
    }

    @Override
    public String toString() {
        return "SempVersion{" +
                "text='" + text + '\'' +
                ", number=" + number +
                '}';
    }

    @Override
    public int compareTo(SempVersion input) {
        return number-input.number;
    }
}
