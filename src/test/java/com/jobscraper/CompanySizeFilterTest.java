package com.jobscraper;

import com.jobscraper.util.CompanySizeFilter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CompanySizeFilterTest {

    @Test
    public void testCompaniesBelow500EmployeesAreFiltered() {
        // Range formats with upper bound < 500 or lower bound < 500
        assertFalse(CompanySizeFilter.meetsMinimumCompanySize("1-10 employees"));
        assertFalse(CompanySizeFilter.meetsMinimumCompanySize("0-10 employees"));
        assertFalse(CompanySizeFilter.meetsMinimumCompanySize("1 - 10"));
        assertFalse(CompanySizeFilter.meetsMinimumCompanySize("10-50 employees"));
        assertFalse(CompanySizeFilter.meetsMinimumCompanySize("11-50 employees"));
        assertFalse(CompanySizeFilter.meetsMinimumCompanySize("50-200 employees"));
        assertFalse(CompanySizeFilter.meetsMinimumCompanySize("51-200 employees"));
        assertFalse(CompanySizeFilter.meetsMinimumCompanySize("200-500 employees"));
        assertFalse(CompanySizeFilter.meetsMinimumCompanySize("201-500 employees"));
        assertFalse(CompanySizeFilter.meetsMinimumCompanySize("200 - 500"));

        // Plus notation below 500
        assertFalse(CompanySizeFilter.meetsMinimumCompanySize("10+ employees"));
        assertFalse(CompanySizeFilter.meetsMinimumCompanySize("50+ employees"));
        assertFalse(CompanySizeFilter.meetsMinimumCompanySize("200+ employees"));

        // Single number below 500
        assertFalse(CompanySizeFilter.meetsMinimumCompanySize("5 employees"));
        assertFalse(CompanySizeFilter.meetsMinimumCompanySize("50 employees"));
        assertFalse(CompanySizeFilter.meetsMinimumCompanySize("250 employees"));
        assertFalse(CompanySizeFilter.meetsMinimumCompanySize("499 employees"));

        // Blank, null, invalid
        assertFalse(CompanySizeFilter.meetsMinimumCompanySize(""));
        assertFalse(CompanySizeFilter.meetsMinimumCompanySize("   "));
        assertFalse(CompanySizeFilter.meetsMinimumCompanySize(null));
        assertFalse(CompanySizeFilter.meetsMinimumCompanySize("unknown"));

        // isSmallCompany should return true for all of these
        assertTrue(CompanySizeFilter.isSmallCompany("1-10 employees"));
        assertTrue(CompanySizeFilter.isSmallCompany("10-50 employees"));
        assertTrue(CompanySizeFilter.isSmallCompany("50-200 employees"));
        assertTrue(CompanySizeFilter.isSmallCompany("200-500 employees"));
        assertTrue(CompanySizeFilter.isSmallCompany("201-500 employees"));
        assertTrue(CompanySizeFilter.isSmallCompany(""));
        assertTrue(CompanySizeFilter.isSmallCompany(null));
    }

    @Test
    public void testCompaniesWithAtLeast500EmployeesAreAccepted() {
        // Range formats >= 500
        assertTrue(CompanySizeFilter.meetsMinimumCompanySize("500-1000 employees"));
        assertTrue(CompanySizeFilter.meetsMinimumCompanySize("500-1,000 employees"));
        assertTrue(CompanySizeFilter.meetsMinimumCompanySize("501-1,000 employees"));
        assertTrue(CompanySizeFilter.meetsMinimumCompanySize("501 - 1,000"));
        assertTrue(CompanySizeFilter.meetsMinimumCompanySize("1,001-5,000 employees"));
        assertTrue(CompanySizeFilter.meetsMinimumCompanySize("5,001-10,000 employees"));
        assertTrue(CompanySizeFilter.meetsMinimumCompanySize("501-1,000 employees · Software Development"));

        // Plus notation >= 500
        assertTrue(CompanySizeFilter.meetsMinimumCompanySize("500+ employees"));
        assertTrue(CompanySizeFilter.meetsMinimumCompanySize("1000+ employees"));
        assertTrue(CompanySizeFilter.meetsMinimumCompanySize("1,000+ employees"));
        assertTrue(CompanySizeFilter.meetsMinimumCompanySize("5,000+ employees"));
        assertTrue(CompanySizeFilter.meetsMinimumCompanySize("10,000+ employees"));
        assertTrue(CompanySizeFilter.meetsMinimumCompanySize("10,001+ employees"));
        assertTrue(CompanySizeFilter.meetsMinimumCompanySize("10,001+ employees · Technology, Information and Internet"));

        // Single number >= 500
        assertTrue(CompanySizeFilter.meetsMinimumCompanySize("500 employees"));
        assertTrue(CompanySizeFilter.meetsMinimumCompanySize("500"));
        assertTrue(CompanySizeFilter.meetsMinimumCompanySize("1,500 employees"));

        // isSmallCompany should return false for all of these
        assertFalse(CompanySizeFilter.isSmallCompany("500-1000 employees"));
        assertFalse(CompanySizeFilter.isSmallCompany("501-1,000 employees"));
        assertFalse(CompanySizeFilter.isSmallCompany("1,001-5,000 employees"));
        assertFalse(CompanySizeFilter.isSmallCompany("10,000+ employees"));
        assertFalse(CompanySizeFilter.isSmallCompany("10,001+ employees"));
    }
}
