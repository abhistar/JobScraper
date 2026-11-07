package com.jobscraper.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility for parsing and filtering company sizes.
 * Ensures companies meet the required minimum employee threshold (default: 500 employees).
 */
public class CompanySizeFilter {

    public static final int DEFAULT_MIN_EMPLOYEES = 500;

    private static final Pattern SIZE_RANGE_PATTERN = Pattern.compile("(\\d[\\d,]*)\\s*-\\s*(\\d[\\d,]*)");
    private static final Pattern PLUS_PATTERN = Pattern.compile("(\\d[\\d,]*)\\s*\\+");
    private static final Pattern SINGLE_NUM_PATTERN = Pattern.compile("(\\d[\\d,]*)");

    /**
     * Determines whether the given company size meets the default minimum threshold (500 employees).
     */
    public static boolean meetsMinimumCompanySize(String companySize) {
        return meetsMinimumCompanySize(companySize, DEFAULT_MIN_EMPLOYEES);
    }

    /**
     * Determines whether the given company size meets the specified minimum employee threshold.
     *
     * Rules:
     * - If range (e.g. "500-1000", "201-500", "50-200"): checks if the lower bound >= minEmployees.
     *   "500-1000" -> lower 500 >= 500 -> true
     *   "200-500" or "201-500" -> lower 200/201 < 500 -> false (not guaranteed >= 500)
     * - If plus notation (e.g. "1000+", "10,001+", "500+"): checks if the base number >= minEmployees.
     * - If single number (e.g. "600 employees", "250"): checks if the number >= minEmployees.
     * - If null, blank, or unparseable: returns false.
     */
    public static boolean meetsMinimumCompanySize(String companySize, int minEmployees) {
        if (companySize == null || companySize.isBlank()) {
            return false;
        }

        String clean = companySize.replace(",", "").toLowerCase().trim();

        // 1. Check range e.g. "500-1000", "501-1000", "201-500", "50-200", "1-10"
        Matcher rangeMatcher = SIZE_RANGE_PATTERN.matcher(clean);
        if (rangeMatcher.find()) {
            try {
                int lower = Integer.parseInt(rangeMatcher.group(1));
                return lower >= minEmployees;
            } catch (NumberFormatException ignored) {}
        }

        // 2. Check plus notation e.g. "1000+", "10000+", "500+"
        Matcher plusMatcher = PLUS_PATTERN.matcher(clean);
        if (plusMatcher.find()) {
            try {
                int val = Integer.parseInt(plusMatcher.group(1));
                return val >= minEmployees;
            } catch (NumberFormatException ignored) {}
        }

        // 3. Check single number e.g. "600 employees", "250"
        Matcher singleMatcher = SINGLE_NUM_PATTERN.matcher(clean);
        if (singleMatcher.find()) {
            try {
                int val = Integer.parseInt(singleMatcher.group(1));
                return val >= minEmployees;
            } catch (NumberFormatException ignored) {}
        }

        return false;
    }

    /**
     * Determines whether the given company size represents a small company (< 500 employees).
     * Returns true if company size is smaller than 500 employees or size is unknown.
     */
    public static boolean isSmallCompany(String companySize) {
        return !meetsMinimumCompanySize(companySize, DEFAULT_MIN_EMPLOYEES);
    }

    /**
     * Checks if company size does not meet the specified minimum threshold.
     */
    public static boolean isSmallCompany(String companySize, int minEmployees) {
        return !meetsMinimumCompanySize(companySize, minEmployees);
    }
}
