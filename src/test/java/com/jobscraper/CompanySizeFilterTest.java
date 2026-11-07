package com.jobscraper;

import com.jobscraper.util.CompanySizeFilter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CompanySizeFilterTest {

    @Test
    public void testSmallCompanyFiltering() {
        assertTrue(CompanySizeFilter.isSmallCompany("1-10 employees"));
        assertTrue(CompanySizeFilter.isSmallCompany("0-10 employees"));
        assertTrue(CompanySizeFilter.isSmallCompany("1 - 10"));
        assertTrue(CompanySizeFilter.isSmallCompany("5 employees"));
    }

    @Test
    public void testNonSmallCompanyFiltering() {
        assertFalse(CompanySizeFilter.isSmallCompany("10-50 employees"));
        assertFalse(CompanySizeFilter.isSmallCompany("51-200 employees"));
        assertFalse(CompanySizeFilter.isSmallCompany("201-500 employees"));
        assertFalse(CompanySizeFilter.isSmallCompany("1,001-5,000 employees"));
        assertFalse(CompanySizeFilter.isSmallCompany("10,000+ employees"));
        assertFalse(CompanySizeFilter.isSmallCompany(""));
        assertFalse(CompanySizeFilter.isSmallCompany(null));
    }
}
