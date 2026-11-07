package com.jobscraper.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility for parsing and filtering company sizes.
 */
public class CompanySizeFilter {

    private static final Pattern SIZE_RANGE_PATTERN = Pattern.compile("(\\d[\\d,]*)\\s*-\\s*(\\d[\\d,]*)");
    private static final Pattern SINGLE_NUM_PATTERN = Pattern.compile("(\\d[\\d,]*)");

    /**
     * Determines whether the given company size string represents a small company.
     * By default, filters companies with 10 or fewer employees (e.g. "1-10 employees", "0-10 employees").
     */
    public static boolean isSmallCompany(String companySize) {
        return isSmallCompany(companySize, 10);
    }

    /**
     * Checks if company size upper bound is <= maxThreshold.
     */
    public static boolean isSmallCompany(String companySize, int maxThreshold) {
        if (companySize == null || companySize.isBlank()) {
            return false;
        }

        String clean = companySize.replace(",", "").toLowerCase().trim();
        Matcher rangeMatcher = SIZE_RANGE_PATTERN.matcher(clean);
        if (rangeMatcher.find()) {
            try {
                int upper = Integer.parseInt(rangeMatcher.group(2));
                return upper <= maxThreshold;
            } catch (NumberFormatException ignored) {}
        }

        Matcher singleMatcher = SINGLE_NUM_PATTERN.matcher(clean);
        if (singleMatcher.find() && !clean.contains("+")) {
            try {
                int val = Integer.parseInt(singleMatcher.group(1));
                return val <= maxThreshold;
            } catch (NumberFormatException ignored) {}
        }

        return false;
    }
}
