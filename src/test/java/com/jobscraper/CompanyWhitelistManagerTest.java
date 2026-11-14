package com.jobscraper;

import com.jobscraper.util.CompanyWhitelistManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class CompanyWhitelistManagerTest {

    @TempDir
    Path tempDir;

    private File testWhitelistFile;

    @BeforeEach
    public void setUp() {
        testWhitelistFile = tempDir.resolve("Candidate_company_whitelist.csv").toFile();
        CompanyWhitelistManager.resetInstance();
    }

    @AfterEach
    public void tearDown() {
        CompanyWhitelistManager.resetInstance();
    }

    @Test
    public void testWhitelistFileNameFormat() {
        assertEquals("Abhinav_company_whitelist.csv",
                CompanyWhitelistManager.getWhitelistFileName("Abhinav"));
        assertEquals("Anushree_company_whitelist.csv",
                CompanyWhitelistManager.getWhitelistFileName("Anushree"));
        assertEquals("default_company_whitelist.csv",
                CompanyWhitelistManager.getWhitelistFileName(null));
        assertEquals("default_company_whitelist.csv",
                CompanyWhitelistManager.getWhitelistFileName("   "));

        File expectedFile = tempDir.resolve("Candidate_company_whitelist.csv").toFile();
        assertEquals(expectedFile,
                CompanyWhitelistManager.getWhitelistFile("Candidate", tempDir.toFile()));
    }

    @Test
    public void testCleanCompanyUrlPaths() {
        // Test /home, /life, /about, /jobs, /people
        assertEquals("https://www.linkedin.com/company/skylo-technologies",
                CompanyWhitelistManager.cleanCompanyUrl("https://www.linkedin.com/company/skylo-technologies/life?trk=company_name"));

        assertEquals("https://www.linkedin.com/company/google",
                CompanyWhitelistManager.cleanCompanyUrl("https://www.linkedin.com/company/google/about/"));

        assertEquals("https://www.linkedin.com/company/meta",
                CompanyWhitelistManager.cleanCompanyUrl("https://www.linkedin.com/company/meta/jobs"));

        assertEquals("https://www.linkedin.com/company/apple",
                CompanyWhitelistManager.cleanCompanyUrl("https://www.linkedin.com/company/apple/people"));

        assertEquals("https://www.linkedin.com/company/microsoft",
                CompanyWhitelistManager.cleanCompanyUrl("https://www.linkedin.com/company/microsoft/home/"));

        assertEquals("https://www.linkedin.com/company/netflix",
                CompanyWhitelistManager.cleanCompanyUrl("https://www.linkedin.com/company/netflix/life/culture?param=1#section"));
    }

    @Test
    public void testCleanCompanyUrlQueriesAndTrailingSlash() {
        assertEquals("https://www.linkedin.com/company/amazon",
                CompanyWhitelistManager.cleanCompanyUrl("https://www.linkedin.com/company/amazon/"));

        assertEquals("https://www.linkedin.com/company/amazon",
                CompanyWhitelistManager.cleanCompanyUrl("https://www.linkedin.com/company/amazon?viewAsMember=true"));

        assertEquals("https://www.linkedin.com/company/amazon",
                CompanyWhitelistManager.cleanCompanyUrl("https://www.linkedin.com/company/amazon#top"));

        assertEquals("https://www.linkedin.com/company/acme",
                CompanyWhitelistManager.cleanCompanyUrl("/company/acme/about"));

        assertEquals("", CompanyWhitelistManager.cleanCompanyUrl(""));
        assertEquals("", CompanyWhitelistManager.cleanCompanyUrl(null));
    }

    @Test
    public void testRejectionsCollectedInMemoryAndWrittenAtTheEnd() throws IOException {
        CompanyWhitelistManager manager = CompanyWhitelistManager.getInstance("Candidate", tempDir.toFile());

        // Record rejections - these should only collect into memory without writing to disk
        manager.recordRejectedCompany("Skylo", "https://www.linkedin.com/company/skylo-technologies/life?trk=nav");
        manager.recordRejectedCompany("TrueFoundry", "https://www.linkedin.com/company/truefoundry/about/");

        // Verify CSV file has NOT been written yet
        assertFalse(testWhitelistFile.exists(), "CSV file should not be created immediately per-rejection");
        assertEquals(2, manager.getEntries().size());

        // Now simulate end of scraping: batch save to CSV
        manager.save();

        assertTrue(testWhitelistFile.exists(), "CSV file should be written after explicit save()");

        List<String> lines = Files.readAllLines(testWhitelistFile.toPath());
        assertEquals(3, lines.size());
        assertEquals("company name,company link on linkedin,isWhitelisted", lines.get(0));
        assertTrue(lines.get(1).contains("Skylo"));
        assertTrue(lines.get(1).contains("https://www.linkedin.com/company/skylo-technologies"));
        assertTrue(lines.get(1).endsWith("false"));

        assertTrue(lines.get(2).contains("TrueFoundry"));
        assertTrue(lines.get(2).contains("https://www.linkedin.com/company/truefoundry"));
        assertTrue(lines.get(2).endsWith("false"));
    }

    @Test
    public void testRecordRejectedCompanyDoesNotOverwriteWhitelistedStatus() {
        CompanyWhitelistManager manager = CompanyWhitelistManager.getInstance(testWhitelistFile);

        // Record initially
        manager.recordRejectedCompany("Skylo", "https://www.linkedin.com/company/skylo-technologies/life");
        assertFalse(manager.isWhitelisted("Skylo", "https://www.linkedin.com/company/skylo-technologies"));

        // Simulate user setting isWhitelisted to true
        manager.getEntries().get(0).setWhitelisted(true);
        manager.save();

        assertTrue(manager.isWhitelisted("Skylo", "https://www.linkedin.com/company/skylo-technologies"));
        assertTrue(manager.isWhitelisted("Skylo", ""));
        assertTrue(manager.isWhitelisted("", "https://www.linkedin.com/company/skylo-technologies"));

        // Rejecting again should NOT revert isWhitelisted back to false
        manager.recordRejectedCompany("Skylo", "https://www.linkedin.com/company/skylo-technologies/about");
        assertTrue(manager.isWhitelisted("Skylo", "https://www.linkedin.com/company/skylo-technologies"));

        manager.save();
        assertTrue(manager.isWhitelisted("Skylo", "https://www.linkedin.com/company/skylo-technologies"));
    }

    @Test
    public void testLoadExistingWhitelistCsv() throws IOException {
        // Pre-create a whitelist file with existing entries
        try (FileWriter fw = new FileWriter(testWhitelistFile, StandardCharsets.UTF_8)) {
            fw.write("company name,company link on linkedin,isWhitelisted\n");
            fw.write("SpecialCo,https://www.linkedin.com/company/specialco/about,true\n");
            fw.write("IgnoredCo,https://www.linkedin.com/company/ignoredco,false\n");
        }

        CompanyWhitelistManager manager = CompanyWhitelistManager.getInstance(testWhitelistFile);

        assertTrue(manager.isWhitelisted("SpecialCo", "https://www.linkedin.com/company/specialco"));
        assertTrue(manager.isWhitelisted("specialco", ""));
        assertFalse(manager.isWhitelisted("IgnoredCo", "https://www.linkedin.com/company/ignoredco"));
        assertFalse(manager.isWhitelisted("NonExistent", ""));
    }

    @Test
    public void testLegacyFallbackIfProfileCsvDoesNotExist() throws IOException {
        File legacyFile = tempDir.resolve("company_whitelist.csv").toFile();
        try (FileWriter fw = new FileWriter(legacyFile, StandardCharsets.UTF_8)) {
            fw.write("company name,company link on linkedin,isWhitelisted\n");
            fw.write("LegacyWhitelisted,https://www.linkedin.com/company/legacy-wl,true\n");
        }

        // Target file Candidate_company_whitelist.csv does not exist yet
        assertFalse(testWhitelistFile.exists());

        // Initialize manager for "Candidate" in tempDir
        CompanyWhitelistManager manager = CompanyWhitelistManager.getInstance("Candidate", tempDir.toFile());

        // Verify it picked up the entry from legacy file
        assertTrue(manager.isWhitelisted("LegacyWhitelisted", ""));

        // Save creates Candidate_company_whitelist.csv
        manager.save();
        assertTrue(testWhitelistFile.exists());
    }
}
