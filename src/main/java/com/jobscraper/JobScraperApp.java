package com.jobscraper;

import com.jobscraper.model.EdgeProfile;
import com.jobscraper.model.JobPost;
import com.jobscraper.scraper.InstahyreScraper;
import com.jobscraper.scraper.LinkedInScraper;
import com.jobscraper.util.CompanySizeFilter;
import com.jobscraper.util.CsvWriter;
import com.jobscraper.util.EdgeBrowserLauncher;
import com.jobscraper.util.EdgeProfileManager;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Script 2: Main Job Scraper Application
 *
 * Automates job scraping on LinkedIn and Instahyre using Microsoft Edge in debug mode.
 * Generates an output CSV named <target_profile_name>_job_lookup_<ddmmyyyy>.csv
 * and cleanly shuts down the Edge debug session upon completion.
 */
public class JobScraperApp {

    public static void main(String[] args) {
        String targetProfileName = "Abhinav"; // default profile

        // Parse CLI arguments: e.g. --profile=Abhinav or --profile=Anushree
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("--profile=")) {
                targetProfileName = arg.substring("--profile=".length()).trim();
            } else if (arg.equals("--profile") || arg.equals("-p")) {
                if (i + 1 < args.length) {
                    targetProfileName = args[++i].trim();
                }
            } else if (!arg.startsWith("-")) {
                targetProfileName = arg.trim();
            }
        }

        System.out.println("=".repeat(65));
        System.out.println("       EDGE BROWSER JOB SCRAPING AUTOMATION");
        System.out.println("=".repeat(65));

        // Step 2: Validate profile choice strictly ("Abhinav" or "Anushree")
        try {
            EdgeProfileManager.validateProfileName(targetProfileName);
        } catch (IllegalArgumentException e) {
            System.err.println("\n[!] FATAL ERROR: " + e.getMessage() + "\n");
            System.exit(1);
        }

        String normalizedProfile = EdgeProfileManager.normalizeProfileName(targetProfileName);
        EdgeProfile profile = EdgeProfileManager.getProfileForName(normalizedProfile);
        System.out.println("[✓] Active Profile Selected: " + profile.name() + " (Directory: " + profile.directory() + ")");

        EdgeBrowserLauncher launcher = new EdgeBrowserLauncher();
        List<JobPost> allJobs = new ArrayList<>();
        List<JobPost> linkedInJobs = new ArrayList<>();
        List<JobPost> instahyreJobs = new ArrayList<>();

        try {
            // Launch or connect to Edge debug session
            Browser browser = launcher.connectOrLaunch(profile);
            BrowserContext context = !browser.contexts().isEmpty() ? browser.contexts().get(0) : browser.newContext();
            Page page = context.newPage();

            // Step 3 & 4: Scrape LinkedIn
            try {
                linkedInJobs = LinkedInScraper.scrape(page, normalizedProfile);
                allJobs.addAll(linkedInJobs);
            } catch (Exception e) {
                System.err.println("[!] Error during LinkedIn scraping: " + e.getMessage());
            }

            // Step 3 & 4: Scrape Instahyre
            try {
                if (!browser.isConnected()) {
                    browser = launcher.connectOrLaunch(profile);
                    context = !browser.contexts().isEmpty() ? browser.contexts().get(0) : browser.newContext();
                    page = context.newPage();
                } else if (page.isClosed()) {
                    page = context.newPage();
                }
                instahyreJobs = InstahyreScraper.scrape(page, normalizedProfile);
                allJobs.addAll(instahyreJobs);
            } catch (Exception e) {
                System.err.println("[!] Error during Instahyre scraping: " + e.getMessage());
            } finally {
                try {
                    page.close();
                } catch (Exception ignored) {}
            }

            // Step 5: Save all collected jobs to CSV (guaranteeing >= 500 employees)
            List<JobPost> verifiedJobs = new ArrayList<>();
            for (JobPost job : allJobs) {
                if (CompanySizeFilter.meetsMinimumCompanySize(job.companySize())) {
                    verifiedJobs.add(job);
                } else {
                    System.out.println("[!] Dropping job failing >= 500 company size requirement: " + job.company() + " (" + job.companySize() + ")");
                }
            }

            File repoDir = new File(System.getProperty("user.dir"));
            File csvFile = CsvWriter.writeJobsToCsv(verifiedJobs, normalizedProfile, repoDir);

            System.out.println("\n" + "=".repeat(65));
            System.out.println("[✓] SCRAPING SUMMARY");
            System.out.println("=".repeat(65));
            System.out.println("Profile:       " + normalizedProfile);
            System.out.println("Total Jobs:    " + verifiedJobs.size());
            System.out.println("  - LinkedIn:  " + verifiedJobs.stream().filter(j -> "LinkedIn".equalsIgnoreCase(j.source())).count());
            System.out.println("  - Instahyre: " + verifiedJobs.stream().filter(j -> "Instahyre".equalsIgnoreCase(j.source())).count());
            System.out.println("Output File:   " + csvFile.getAbsolutePath());
            System.out.println("=".repeat(65) + "\n");

        } catch (Exception e) {
            System.err.println("[!] Critical error during job scraping: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Step 6: Ensure to shut down debug mode of the opened Edge browser session
            launcher.shutdownDebugSession();
        }
    }
}
