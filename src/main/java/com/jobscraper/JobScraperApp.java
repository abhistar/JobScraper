package com.jobscraper;

import com.jobscraper.model.EdgeProfile;
import com.jobscraper.model.JobPost;
import com.jobscraper.scraper.InstahyreScraper;
import com.jobscraper.scraper.LinkedInScraper;
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

        try {
            // Launch or connect to Edge debug session
            Browser browser = launcher.connectOrLaunch(profile);
            BrowserContext context = !browser.contexts().isEmpty() ? browser.contexts().get(0) : browser.newContext();
            Page page = context.newPage();

            // Step 3 & 4: Scrape LinkedIn
            List<JobPost> linkedInJobs = LinkedInScraper.scrape(page, normalizedProfile);
            allJobs.addAll(linkedInJobs);

            // Step 3 & 4: Scrape Instahyre
            List<JobPost> instahyreJobs = InstahyreScraper.scrape(page, normalizedProfile);
            allJobs.addAll(instahyreJobs);

            try {
                page.close();
            } catch (Exception ignored) {}

            // Step 5: Save all collected jobs to CSV
            File repoDir = new File(System.getProperty("user.dir"));
            File csvFile = CsvWriter.writeJobsToCsv(allJobs, normalizedProfile, repoDir);

            System.out.println("\n" + "=".repeat(65));
            System.out.println("[✓] SCRAPING SUMMARY");
            System.out.println("=".repeat(65));
            System.out.println("Profile:       " + normalizedProfile);
            System.out.println("Total Jobs:    " + allJobs.size());
            System.out.println("  - LinkedIn:  " + linkedInJobs.size());
            System.out.println("  - Instahyre: " + instahyreJobs.size());
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
