package com.jobscraper.scraper;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jobscraper.model.JobPost;
import com.jobscraper.util.CompanySizeFilter;
import com.jobscraper.util.CompanyWhitelistManager;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitUntilState;

import java.util.*;

/**
 * Scrapes job opportunities from Instahyre candidate opportunities page.
 */
public class InstahyreScraper {

    public static List<JobPost> scrape(Page page, String targetProfileName) {
        List<JobPost> jobs = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();
        CompanyWhitelistManager whitelistManager = CompanyWhitelistManager.getInstance(targetProfileName);

        System.out.println("\n" + "=".repeat(60));
        System.out.println("  INSTAHYRE JOBS (Profile: " + targetProfileName + ")");
        System.out.println("=".repeat(60));

        try {
            System.out.println("[*] Navigating to https://www.instahyre.com/candidate/opportunities/ ...");
            page.navigate("https://www.instahyre.com/candidate/opportunities/",
                    new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED).setTimeout(30000));
            page.waitForTimeout(4000);

            // Scroll down to load all opportunities
            System.out.println("[*] Scrolling to load Instahyre opportunities...");
            for (int i = 0; i < 3; i++) {
                page.evaluate("() => window.scrollBy(0, 1000)");
                page.waitForTimeout(1000);
            }

            // Extract opportunities using Angular scope or DOM fallback
            String dataJson = (String) page.evaluate("() => {" +
                    "  const rows = Array.from(document.querySelectorAll('.employer-row, [ng-repeat*=\"opportunity\"], .employer-details'));" +
                    "  const results = [];" +
                    "  for (const row of rows) {" +
                    "    let title = '';" +
                    "    let company = '';" +
                    "    let location = '';" +
                    "    let companySize = '';" +
                    "    let jobUrl = '';" +
                    "    let applyUrl = '';" +
                    "    let expReq = '';" +
                    "    let datePosted = '';" +
                    "    " +
                    "    if (window.angular) {" +
                    "      try {" +
                    "        const scope = window.angular.element(row).scope();" +
                    "        if (scope && scope.opp) {" +
                    "          const opp = scope.opp;" +
                    "          if (opp.job) {" +
                    "            title = opp.job.title || opp.job.candidate_title || '';" +
                    "            location = opp.job.locations || '';" +
                    "            if (opp.job.opportunity_url) {" +
                    "              jobUrl = 'https://www.instahyre.com' + opp.job.opportunity_url;" +
                    "              applyUrl = jobUrl;" +
                    "            }" +
                    "            if (opp.job.keywords && Array.isArray(opp.job.keywords)) {" +
                    "              expReq = opp.job.keywords.join(', ');" +
                    "            }" +
                    "          }" +
                    "          if (opp.employer) {" +
                    "            company = opp.employer.company_name || '';" +
                    "            if (opp.employer.employee_count) {" +
                    "              companySize = opp.employer.employee_count + ' employees';" +
                    "            }" +
                    "          }" +
                    "          if (opp.reviewed_at) datePosted = opp.reviewed_at;" +
                    "        }" +
                    "      } catch (e) {}" +
                    "    }" +
                    "    " +
                    "    // DOM Fallback / enrichment\n" +
                    "    const nameEl = row.querySelector('.company-name, .employer-job-name, h2');" +
                    "    if (nameEl) {" +
                    "      const fullHeading = nameEl.innerText.trim();" +
                    "      if (fullHeading.includes(' - ')) {" +
                    "        const parts = fullHeading.split(' - ');" +
                    "        if (!company) company = parts[0].trim();" +
                    "        if (!title && parts.length > 1) title = parts[1].trim();" +
                    "      } else if (!company) {" +
                    "        company = fullHeading;" +
                    "      }" +
                    "    }" +
                    "    const locEl = row.querySelector('.chip-location, .employer-chips-location');" +
                    "    if (locEl && !location) location = locEl.innerText.trim();" +
                    "    " +
                    "    const sizeEl = row.querySelector('.chip-employees, .employer-chips-employees, [ng-bind*=\"employee_count\"]');" +
                    "    if (sizeEl && !companySize) companySize = sizeEl.innerText.trim();" +
                    "    " +
                    "    const dateEl = row.querySelector('.status-date, .date');" +
                    "    if (dateEl && !datePosted) datePosted = dateEl.innerText.trim();" +
                    "    " +
                    "    const linkEl = row.querySelector('a[href*=\"/job-\"]');" +
                    "    if (linkEl && linkEl.href && !jobUrl) {" +
                    "      jobUrl = linkEl.href;" +
                    "      applyUrl = jobUrl;" +
                    "    }" +
                    "    if (!jobUrl) jobUrl = window.location.href;" +
                    "    if (!applyUrl) applyUrl = 'Instahyre Candidate Portal';" +
                    "    " +
                    "    if (company && (title || company)) {" +
                    "      results.push({" +
                    "        title: title || 'Software Engineer'," +
                    "        company: company," +
                    "        location: location," +
                    "        companySize: companySize," +
                    "        expReq: expReq," +
                    "        datePosted: datePosted || 'Recent'," +
                    "        jobUrl: jobUrl," +
                    "        applyUrl: applyUrl" +
                    "      });" +
                    "    }" +
                    "  }" +
                    "  return JSON.stringify(results);" +
                    "}");

            if (dataJson != null && !dataJson.isBlank()) {
                JsonArray array = JsonParser.parseString(dataJson).getAsJsonArray();
                System.out.println("[*] Found " + array.size() + " opportunity cards on Instahyre.");

                for (JsonElement el : array) {
                    JsonObject obj = el.getAsJsonObject();
                    String title = obj.has("title") ? obj.get("title").getAsString().trim() : "";
                    String company = obj.has("company") ? obj.get("company").getAsString().trim() : "";
                    String location = obj.has("location") ? obj.get("location").getAsString().trim() : "";
                    String companySize = obj.has("companySize") ? obj.get("companySize").getAsString().trim() : "";
                    String expReq = obj.has("expReq") ? obj.get("expReq").getAsString().trim() : "";
                    String datePosted = obj.has("datePosted") ? obj.get("datePosted").getAsString().trim() : "";
                    String jobUrl = obj.has("jobUrl") ? obj.get("jobUrl").getAsString().trim() : "";
                    String applyUrl = obj.has("applyUrl") ? obj.get("applyUrl").getAsString().trim() : "";

                    String dedupKey = (company + "::" + title).toLowerCase();
                    if (seenKeys.contains(dedupKey)) {
                        continue;
                    }

                    // Company size filter (minimum 500 employees, unless whitelisted)
                    boolean whitelisted = whitelistManager.isWhitelisted(company, "");
                    if (whitelisted) {
                        System.out.println("    [★] Whitelisted company - bypassing employee size filter: " + company + " (" + (companySize.isBlank() ? "unknown size" : companySize) + ")");
                    } else if (!CompanySizeFilter.meetsMinimumCompanySize(companySize)) {
                        System.out.println("    [-] Skipping company with < 500 employees: " + company + " (" + (companySize.isBlank() ? "unknown size" : companySize) + ")");
                        continue;
                    }

                    seenKeys.add(dedupKey);
                    JobPost post = new JobPost(
                            "Instahyre",
                            title,
                            company,
                            location,
                            expReq,
                            companySize,
                            datePosted,
                            jobUrl,
                            applyUrl
                    );
                    jobs.add(post);
                    System.out.println("    [+] Collected: " + title + " @ " + company + " (" + location + ") | Size: " + companySize);
                }
            } else {
                System.out.println("[-] No opportunities detected on Instahyre home page.");
            }

        } catch (Exception e) {
            System.err.println("[!] Error during Instahyre scraping: " + e.getMessage());
        }

        System.out.println("[✓] Instahyre scraping finished. Collected " + jobs.size() + " jobs.\n");
        return jobs;
    }
}
