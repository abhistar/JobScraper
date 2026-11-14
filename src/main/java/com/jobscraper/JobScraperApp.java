package com.jobscraper;

import com.jobscraper.model.EdgeProfile;
import com.jobscraper.model.JobPost;
import com.jobscraper.scraper.InstahyreScraper;
import com.jobscraper.scraper.LinkedInProfileMatchScraper;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Script 2: Main Job Scraper Application
 *
 * Automates job scraping on LinkedIn (preferences and profile match) and Instahyre using Microsoft Edge in debug mode.
 * Generates an output CSV named <target_profile_name>_job_lookup_<ddmmyyyy>.csv
 * and cleanly shuts down the Edge debug session upon completion.
 */
public class JobScraperApp {

    public static void main(String[] args) {
        String targetProfileName = null;
        boolean scrapePreferences = true;
        boolean scrapeProfileMatch = true;
        boolean scrapeInstahyre = true;

        // Parse CLI arguments: e.g. --profile=<profile_name>
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("--profile=")) {
                targetProfileName = arg.substring("--profile=".length()).trim();
            } else if (arg.equals("--profile") || arg.equals("-p")) {
                if (i + 1 < args.length) {
                    targetProfileName = args[++i].trim();
                }
            } else if (arg.equals("--profile-match-only") || arg.equals("--section=profile-match") || arg.equals("--scraper=profile-match")) {
                scrapePreferences = false;
                scrapeProfileMatch = true;
                scrapeInstahyre = false;
            } else if (arg.equals("--preferences-only") || arg.equals("--section=preferences") || arg.equals("--scraper=preferences")) {
                scrapePreferences = true;
                scrapeProfileMatch = false;
                scrapeInstahyre = false;
            } else if (arg.equals("--skip-preferences")) {
                scrapePreferences = false;
            } else if (arg.equals("--skip-profile-match")) {
                scrapeProfileMatch = false;
            } else if (arg.equals("--skip-instahyre")) {
                scrapeInstahyre = false;
            } else if (!arg.startsWith("-")) {
                targetProfileName = arg.trim();
            }
        }

        System.out.println("=".repeat(65));
        System.out.println("       EDGE BROWSER JOB SCRAPING AUTOMATION");
        System.out.println("=".repeat(65));

        // Step 2: Validate profile choice against available profiles grabbed from ProfileChecker
        List<EdgeProfile> availableProfiles = ProfileChecker.getAvailableProfiles();
        if (targetProfileName == null || targetProfileName.isBlank()) {
            targetProfileName = availableProfiles.stream()
                    .filter(ProfileChecker::isProfileRunning)
                    .map(EdgeProfile::name)
                    .findFirst()
                    .or(() -> availableProfiles.stream().map(EdgeProfile::name).findFirst())
                    .orElse("Default");
        }

        final String requestedProfile = targetProfileName;
        EdgeProfile profile = ProfileChecker.findProfile(requestedProfile).orElse(null);
        if (profile == null) {
            List<String> validNames = availableProfiles.stream().map(EdgeProfile::name).toList();
            System.err.println("\n[!] FATAL ERROR: Invalid browser profile: '" + requestedProfile + "'. Available profiles: " + validNames + "\n");
            System.exit(1);
            return;
        }

        String normalizedProfile = profile.name();
        System.out.println("[✓] Active Profile Selected: " + profile.name() + " (Directory: " + profile.directory() + ")");

        EdgeBrowserLauncher launcher = new EdgeBrowserLauncher();
        List<JobPost> allJobs = new ArrayList<>();
        List<JobPost> linkedInJobs = new ArrayList<>();
        List<JobPost> linkedInProfileMatchJobs = new ArrayList<>();
        List<JobPost> instahyreJobs = new ArrayList<>();

        try {
            // Launch or connect to Edge debug session
            Browser browser = launcher.connectOrLaunch(profile);
            BrowserContext context = !browser.contexts().isEmpty() ? browser.contexts().get(0) : browser.newContext();
            Page page = context.newPage();

            // Step 3 & 4: Scrape LinkedIn (Jobs based on your preferences)
            if (scrapePreferences) {
                try {
                    linkedInJobs = LinkedInScraper.scrape(page, normalizedProfile);
                    allJobs.addAll(linkedInJobs);
                } catch (Exception e) {
                    System.err.println("[!] Error during LinkedIn Preferences scraping: " + e.getMessage());
                }
            }

            // Step 3 & 4: Scrape LinkedIn (Jobs that match your profile)
            if (scrapeProfileMatch) {
                try {
                    if (!browser.isConnected()) {
                        browser = launcher.connectOrLaunch(profile);
                        context = !browser.contexts().isEmpty() ? browser.contexts().get(0) : browser.newContext();
                        page = context.newPage();
                    } else if (page.isClosed()) {
                        page = context.newPage();
                    }
                    linkedInProfileMatchJobs = LinkedInProfileMatchScraper.scrape(page, normalizedProfile);
                    allJobs.addAll(linkedInProfileMatchJobs);
                } catch (Exception e) {
                    System.err.println("[!] Error during LinkedIn Profile Match scraping: " + e.getMessage());
                }
            }

            // Step 3 & 4: Scrape Instahyre
            if (scrapeInstahyre) {
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
            }

            // Step 5: Save all collected jobs to CSV (guaranteeing >= 500 employees and deduplicating by URL)
            Set<String> seenJobUrls = new HashSet<>();
            List<JobPost> verifiedJobs = new ArrayList<>();
            for (JobPost job : allJobs) {
                String u = job.jobUrl() != null ? job.jobUrl().trim() : "";
                if (!u.isBlank() && !seenJobUrls.add(u)) {
                    continue;
                }
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
            System.out.println("Profile:                         " + normalizedProfile);
            System.out.println("Total Jobs:                      " + verifiedJobs.size());
            System.out.println("  - LinkedIn (Preferences):      " + linkedInJobs.size());
            System.out.println("  - LinkedIn (Profile Match):    " + linkedInProfileMatchJobs.size());
            System.out.println("  - Instahyre:                   " + instahyreJobs.size());
            System.out.println("Output File:                     " + csvFile.getAbsolutePath());
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
