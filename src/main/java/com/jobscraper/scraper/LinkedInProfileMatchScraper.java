package com.jobscraper.scraper;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jobscraper.model.JobPost;
import com.jobscraper.util.CompanySizeFilter;
import com.jobscraper.util.CompanyWhitelistManager;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.microsoft.playwright.options.WaitUntilState;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scrapes jobs from LinkedIn based on the 'Jobs that match your profile' section.
 */
public class LinkedInProfileMatchScraper {

    private static final Pattern COMPANY_SIZE_PATTERN = Pattern.compile("([\\d,]+(?:\\s*-\\s*[\\d,]+|\\+)?\\s+employees)", Pattern.CASE_INSENSITIVE);
    private static final Pattern EXP_REQUIRED_PATTERN = Pattern.compile("(\\d+\\+?\\s*(?:to\\s*\\d+\\+?)?\\s*years?(?:\\s+of)?(?:\\s+\\w+)?\\s+experience)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DATE_POSTED_PATTERN = Pattern.compile("(\\d+\\s+(?:day|hour|minute|week|month)s?\\s+ago|\\d+[dhwm]\\s+ago)", Pattern.CASE_INSENSITIVE);

    public static List<JobPost> scrape(Page page, String targetProfileName) {
        List<JobPost> jobs = new ArrayList<>();
        Set<String> seenUrls = new HashSet<>();
        CompanyWhitelistManager whitelistManager = CompanyWhitelistManager.getInstance(targetProfileName);

        System.out.println("\n" + "=".repeat(60));
        System.out.println("  LINKEDIN JOBS - MATCH YOUR PROFILE (Profile: " + targetProfileName + ")");
        System.out.println("=".repeat(60));

        try {
            System.out.println("[*] Navigating to https://www.linkedin.com/jobs/ ...");
            page.navigate("https://www.linkedin.com/jobs/",
                    new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED).setTimeout(30000));
            try {
                page.waitForSelector("main, div[data-testid='lazy-column'], a[href*='LANDING'], input[name='session_key'], #username",
                        new Page.WaitForSelectorOptions().setState(WaitForSelectorState.ATTACHED).setTimeout(15000));
            } catch (Exception ignored) {}

            // Check if redirected to login/authwall
            String currentUrl = page.url().toLowerCase();
            if (currentUrl.contains("/login") || currentUrl.contains("/authwall") || currentUrl.contains("/checkpoint")) {
                System.err.println("[!] ERROR: LinkedIn session is not logged in.");
                System.err.println("[!] Please log into LinkedIn in Edge for profile '" + targetProfileName + "'.");
                return jobs;
            }

            // Step 3: Find "Jobs that match your profile" section and click "Show all ->"
            System.out.println("[*] Finding 'Jobs that match your profile' section and clicking 'Show all'...");
            Boolean clicked = (Boolean) page.evaluate("() => {" +
                    "  const link = document.querySelector(\"a[href*='JobSearchOrigin_QUALIFICATION_LANDING']\") ||" +
                    "               document.querySelector(\"a[href*='QUALIFICATION_LANDING']\");" +
                    "  if (link) {" +
                    "    link.click();" +
                    "    return true;" +
                    "  }" +
                    "  const headings = Array.from(document.querySelectorAll('h1, h2, h3, header, div, span'));" +
                    "  const targetH = headings.find(h => h.innerText && h.innerText.trim().startsWith('Jobs that match your profile'));" +
                    "  if (targetH) {" +
                    "    let curr = targetH.parentElement;" +
                    "    while (curr && curr !== document.body) {" +
                    "      const showAll = Array.from(curr.querySelectorAll('a')).find(a => a.innerText && a.innerText.toLowerCase().includes('show all'));" +
                    "      if (showAll && (!showAll.getAttribute('href') || !showAll.getAttribute('href').includes('PREFERENCES_LANDING'))) {" +
                    "        showAll.click();" +
                    "        return true;" +
                    "      }" +
                    "      curr = curr.parentElement;" +
                    "    }" +
                    "  }" +
                    "  return false;" +
                    "}");

            if (clicked == null || !clicked) {
                System.out.println("[!] Attempting locator search for Profile Match 'Show all' link...");
                var qualLocator = page.locator("a[href*='QUALIFICATION_LANDING']").first();
                if (qualLocator.isVisible()) {
                    qualLocator.click();
                    clicked = true;
                }
            }

            try {
                page.waitForSelector("div[data-testid='lazy-column'], div[role='button'][componentkey^='job-card-component-ref-'], .jobs-search-results__list-item",
                        new Page.WaitForSelectorOptions().setState(WaitForSelectorState.ATTACHED).setTimeout(15000));
            } catch (Exception ignored) {}

            System.out.println("[*] Profile match search results loaded: " + page.url());

            // Apply 'Past week' filter if not present
            if (!page.url().contains("f_TPR=r604800")) {
                System.out.println("[*] Applying 'Past week' filter...");
                // Open Date posted dropdown
                page.evaluate("() => {" +
                        "  const btn = document.querySelector(\"div[role='button'][aria-label*='Date posted']\") ||" +
                        "              document.querySelector(\"[aria-label*='Filter by Date posted']\") ||" +
                        "              Array.from(document.querySelectorAll(\"div[role='button'], button, label\")).find(el => el.innerText && el.innerText.trim() === 'Date posted');" +
                        "  if (btn) btn.click();" +
                        "}");
                try {
                    page.waitForSelector("input[aria-label='Past week'], label:has-text('Past week'), [aria-label*='Date posted']",
                            new Page.WaitForSelectorOptions().setState(WaitForSelectorState.ATTACHED).setTimeout(4000));
                } catch (Exception ignored) {}

                // Select Past week radio
                page.evaluate("() => {" +
                        "  const radio = document.querySelector(\"input[aria-label='Past week']\") ||" +
                        "                Array.from(document.querySelectorAll(\"label, span, p\")).find(el => el.innerText && el.innerText.trim() === 'Past week');" +
                        "  if (radio) radio.click();" +
                        "}");
                try {
                    page.waitForSelector("button:has-text('Show results'), a:has-text('Show results')",
                            new Page.WaitForSelectorOptions().setState(WaitForSelectorState.ATTACHED).setTimeout(3000));
                } catch (Exception ignored) {}

                // Click Show results button
                page.evaluate("() => {" +
                        "  const showBtn = Array.from(document.querySelectorAll(\"a, button\")).find(el => el.innerText && el.innerText.includes('Show results'));" +
                        "  if (showBtn) showBtn.click();" +
                        "}");
                try {
                    page.waitForURL(url -> url.contains("f_TPR=r604800"),
                            new Page.WaitForURLOptions().setTimeout(5000));
                } catch (Exception ignored) {}

                // Fallback to query parameter navigation if not yet applied
                if (!page.url().contains("f_TPR=r604800")) {
                    String sep = page.url().contains("?") ? "&" : "?";
                    page.navigate(page.url() + sep + "f_TPR=r604800",
                            new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED).setTimeout(20000));
                    try {
                        page.waitForSelector("div[data-testid='lazy-column']",
                                new Page.WaitForSelectorOptions().setState(WaitForSelectorState.ATTACHED).setTimeout(10000));
                    } catch (Exception ignored) {}
                }
            }

            // Step 4: Paginate through up to 5 pages
            int maxPages = 5;
            for (int currentPage = 1; currentPage <= maxPages; currentPage++) {
                System.out.println("\n[*] --- Processing Profile Match Page " + currentPage + " of " + maxPages + " ---");

                // Scroll down jobs column to load listings
                System.out.println("[*] Loading job listings via scroll on page " + currentPage + "...");
                for (int i = 0; i < 4; i++) {
                    page.evaluate("() => {" +
                            "  const col = document.querySelector(\"div[data-testid='lazy-column']\");" +
                            "  if (col) col.scrollBy(0, 1000);" +
                            "  else window.scrollBy(0, 800);" +
                            "}");
                    try {
                        page.locator("div[data-testid='lazy-column'] div[role='button'][componentkey^='job-card-component-ref-']")
                                .last().waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.ATTACHED).setTimeout(1500));
                    } catch (Exception ignored) {}
                }

                // Extract all job cards and their data
                String cardsJson = (String) page.evaluate("() => {" +
                        "  const col = document.querySelector(\"div[data-testid='lazy-column']\");" +
                        "  const root = col || document;" +
                        "  let buttons = Array.from(root.querySelectorAll(\"div[role='button']\")).filter(b => {" +
                        "    const ck = b.getAttribute('componentkey') || '';" +
                        "    return ck.startsWith('job-card-component-ref-');" +
                        "  });" +
                        "  if (buttons.length === 0) {" +
                        "    buttons = Array.from(root.querySelectorAll('.jobs-search-results__list-item, li[data-occludable-job-id], div[data-job-id]'));" +
                        "  }" +
                        "  return JSON.stringify(buttons.map((b, idx) => {" +
                        "    const ck = b.getAttribute('componentkey') || '';" +
                        "    const jobId = ck.replace('job-card-component-ref-', '') || b.getAttribute('data-occludable-job-id') || b.getAttribute('data-job-id') || '';" +
                        "    let title = '';" +
                        "    const titleSpan = b.querySelector(\"span[aria-hidden='true']\");" +
                        "    if (titleSpan) title = titleSpan.innerText.trim();" +
                        "    if (!title) {" +
                        "      const dismissBtn = b.querySelector(\"button[aria-label*='Dismiss']\");" +
                        "      if (dismissBtn) {" +
                        "        const al = dismissBtn.getAttribute('aria-label') || '';" +
                        "        title = al.replace(/^Dismiss\\s+/i, '').replace(/\\s+job$/i, '').trim();" +
                        "      }" +
                        "    }" +
                        "    if (!title) {" +
                        "      const lines = b.innerText.split('\\n').map(l => l.trim()).filter(Boolean);" +
                        "      let raw = lines[0] || '';" +
                        "      title = raw.replace(/^Selected,\\s*/i, '').replace(/\\(Verified job\\)/gi, '').trim();" +
                        "    }" +
                        "    const paras = Array.from(b.querySelectorAll('p')).filter(p => {" +
                        "      const txt = p.innerText.trim();" +
                        "      if (!txt) return false;" +
                        "      if (title && (txt === title || txt.includes(title))) return false;" +
                        "      if (p.querySelector(\"span[aria-hidden='true']\")) return false;" +
                        "      if (p.querySelector('button, svg, figure')) return false;" +
                        "      if (txt.includes('connection works here') || txt.includes('alumni work here') ||" +
                        "          txt.includes('Viewed') || txt.includes('Be an early applicant') ||" +
                        "          txt.includes('Actively reviewing applicants') || txt.includes('Easy Apply') ||" +
                        "          txt === '·' || txt === '•') return false;" +
                        "      return true;" +
                        "    }).map(p => p.innerText.trim());" +
                        "    let company = paras[0] || '';" +
                        "    let location = paras[1] || '';" +
                        "    if (!company || !location) {" +
                        "      const lines = b.innerText.split('\\n').map(l => l.trim()).filter(Boolean);" +
                        "      const clean = lines.filter(l => !['(Verified job)', 'Be an early applicant', 'Easy Apply', 'Actively reviewing applicants', 'Viewed', '·', '•'].includes(l));" +
                        "      if (!company && clean.length > 1) company = clean[1];" +
                        "      if (!location && clean.length > 2) location = clean[2];" +
                        "    }" +
                        "    return {" +
                        "      index: idx," +
                        "      jobId: jobId," +
                        "      title: title," +
                        "      company: company," +
                        "      location: location," +
                        "      fullText: b.innerText" +
                        "    };" +
                        "  }));" +
                        "}");

                if (cardsJson == null || cardsJson.isBlank()) {
                    System.out.println("[-] No job cards detected on profile match results page " + currentPage + ".");
                    break;
                }

                JsonArray cards = JsonParser.parseString(cardsJson).getAsJsonArray();
                System.out.println("[*] Found " + cards.size() + " listings on profile match page " + currentPage + ".");
                if (cards.isEmpty()) {
                    break;
                }

                String firstCardKey = "";
                if (cards.size() > 0 && cards.get(0).isJsonObject()) {
                    JsonObject firstObj = cards.get(0).getAsJsonObject();
                    firstCardKey = firstObj.has("jobId") && !firstObj.get("jobId").getAsString().isBlank()
                            ? firstObj.get("jobId").getAsString()
                            : (firstObj.has("title") ? firstObj.get("title").getAsString() : "");
                }

                for (JsonElement el : cards) {
                    try {
                        JsonObject card = el.getAsJsonObject();
                        String jobId = card.has("jobId") ? card.get("jobId").getAsString() : "";
                        String title = card.has("title") ? card.get("title").getAsString().trim() : "";
                        String company = card.has("company") ? card.get("company").getAsString().trim() : "";
                        String location = card.has("location") ? card.get("location").getAsString().trim() : "";
                        String fullText = card.has("fullText") ? card.get("fullText").getAsString() : "";
                        int index = card.get("index").getAsInt();

                        if (title.isBlank() || title.equalsIgnoreCase("N/A") || company.isBlank()) {
                            continue;
                        }

                        // Check if already applied
                        boolean applied = false;
                        for (String line : fullText.split("\\n")) {
                            String l = line.trim().toLowerCase();
                            if (l.equals("applied") || l.equals("application submitted") || l.startsWith("applied on") || l.startsWith("applied yesterday")) {
                                applied = true;
                                break;
                            }
                        }
                        if (applied) {
                            continue;
                        }

                        String jobUrl = !jobId.isBlank() ? "https://www.linkedin.com/jobs/view/" + jobId + "/" : page.url();
                        if (seenUrls.contains(jobUrl)) {
                            continue;
                        }

                        if (page.isClosed()) {
                            System.err.println("[!] Page closed unexpectedly. Halting card parsing.");
                            break;
                        }

                        // Click on the job card to load right detail pane
                        String detailJson = (String) page.evaluate("([jid, idx]) => {" +
                                "  const col = document.querySelector(\"div[data-testid='lazy-column']\");" +
                                "  const root = col || document;" +
                                "  let card = null;" +
                                "  if (jid) {" +
                                "    card = root.querySelector(`div[role='button'][componentkey*='${jid}']`) ||" +
                                "           root.querySelector(`[componentkey*='${jid}']`) ||" +
                                "           root.querySelector(`li[data-occludable-job-id*='${jid}']`) ||" +
                                "           root.querySelector(`[data-job-id='${jid}']`);" +
                                "  }" +
                                "  if (!card) {" +
                                "    const all = Array.from(root.querySelectorAll(" +
                                "      \"div[role='button'][componentkey^='job-card-component-ref-'], \" +" +
                                "      \".jobs-search-results__list-item, \" +" +
                                "      \"li[data-occludable-job-id], \" +" +
                                "      \"div[data-job-id]\"" +
                                "    ));" +
                                "    if (idx < all.length) card = all[idx];" +
                                "  }" +
                                "  if (card) {" +
                                "    card.scrollIntoView({ block: 'nearest' });" +
                                "    const btn = card.querySelector(\"div[role='button']\") || card;" +
                                "    btn.click();" +
                                "    return JSON.stringify({ clicked: true });" +
                                "  }" +
                                "  return JSON.stringify({ clicked: false });" +
                                "}", List.of(jobId, index));

                        // Wait for the detail pane to update for this job
                        if (!jobId.isBlank()) {
                            try {
                                page.waitForSelector("#JobDetails_AboutTheJob_" + jobId + ", [id*='" + jobId + "']",
                                        new Page.WaitForSelectorOptions().setState(WaitForSelectorState.ATTACHED).setTimeout(3000));
                            } catch (Exception ignored) {}
                        }

                        // Wait for company size/insight element to attach in the right detail pane
                        try {
                            Locator rightPane = page.locator("div[data-testid='lazy-column']").nth(1);
                            if (rightPane.count() == 0) {
                                rightPane = page.locator(".jobs-search__job-details, .scaffold-layout__detail, main .jobs-details");
                            }
                            Locator empLocator = rightPane.locator("text=/([\\d,]+(?:\\s*-\\s*[\\d,]+|\\+)?\\s+employees?)/i").first();
                            empLocator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.ATTACHED).setTimeout(3500));
                        } catch (Exception ignored) {}

                        // Wait for apply button to be attached
                        try {
                            page.waitForSelector(".jobs-apply-button, button[aria-label*='Apply'], a[aria-label*='Apply'], .jobs-s-apply button",
                                    new Page.WaitForSelectorOptions().setState(WaitForSelectorState.ATTACHED).setTimeout(1500));
                        } catch (Exception ignored) {}

                        // Inspect details pane for apply URL, company size, experience, and full text
                        String paneInfoJson = (String) page.evaluate("() => {" +
                                "  const applyBtn = document.querySelector(" +
                                "    '.jobs-apply-button, ' +" +
                                "    'button[aria-label*=\"Apply\"], ' +" +
                                "    'a[aria-label*=\"Apply\"], ' +" +
                                "    '.jobs-s-apply button, ' +" +
                                "    'button.jobs-apply-button' " +
                                "  );" +
                                "  let applyUrl = '';" +
                                "  if (applyBtn) {" +
                                "    if (applyBtn.tagName === 'A' && applyBtn.getAttribute('href')) {" +
                                "      applyUrl = applyBtn.getAttribute('href');" +
                                "    } else if (applyBtn.innerText && applyBtn.innerText.toLowerCase().includes('easy apply')) {" +
                                "      applyUrl = 'Easy Apply';" +
                                "    } else {" +
                                "      applyUrl = (applyBtn.getAttribute('aria-label') || applyBtn.innerText || 'Apply').trim();" +
                                "    }" +
                                "  }" +
                                "  " +
                                "  const cols = document.querySelectorAll(\"div[data-testid='lazy-column']\");" +
                                "  let detailContainer = cols.length > 1 ? cols[1] : document.querySelector(" +
                                "    '.jobs-search__job-details, ' +" +
                                "    '.jobs-search__job-details--container, ' +" +
                                "    '.scaffold-layout__detail, ' +" +
                                "    '.job-view-layout, ' +" +
                                "    'main .jobs-details, ' +" +
                                "    '[data-view-name=\"job-details\"], ' +" +
                                "    '.jobs-details__main-content' " +
                                "  );" +
                                "  if (!detailContainer && applyBtn) {" +
                                "    let p = applyBtn.parentElement;" +
                                "    while (p && p !== document.body) {" +
                                "      if (p.innerText && p.innerText.includes('About the job')) {" +
                                "        detailContainer = p;" +
                                "        break;" +
                                "      }" +
                                "      p = p.parentElement;" +
                                "    }" +
                                "  }" +
                                "  " +
                                "  // Extract Company Size\n" +
                                "  let companySize = '';" +
                                "  const sizePattern = /([\\d,]+(?:\\s*-\\s*[\\d,]+|\\+)?\\s+employees?)/i;" +
                                "  if (detailContainer) {" +
                                "    const specificEls = Array.from(detailContainer.querySelectorAll(" +
                                "      '.job-details-jobs-unified-top-card__job-insight, ' +" +
                                "      '.jobs-unified-top-card__job-insight, ' +" +
                                "      '.job-details-jobs-unified-top-card__primary-description-container li, ' +" +
                                "      '.jobs-company__box, ' +" +
                                "      '.jobs-company' " +
                                "    ));" +
                                "    for (const el of specificEls) {" +
                                "      const t = el.innerText ? el.innerText.trim() : '';" +
                                "      const m = t.match(sizePattern);" +
                                "      if (m) {" +
                                "        companySize = m[1].trim();" +
                                "        break;" +
                                "      }" +
                                "    }" +
                                "    if (!companySize) {" +
                                "      const fullTxt = detailContainer.innerText || '';" +
                                "      const m = fullTxt.match(sizePattern);" +
                                "      if (m) companySize = m[1].trim();" +
                                "    }" +
                                "  }" +
                                "  if (!companySize) {" +
                                "    const m = document.body.innerText.match(sizePattern);" +
                                "    if (m) companySize = m[1].trim();" +
                                "  }" +
                                "  " +
                                "  // Extract Company URL from top of job details\n" +
                                "  let companyUrl = '';" +
                                "  const topCardSelectors = [" +
                                "    '.job-details-jobs-unified-top-card__company-name a', " +
                                "    '.jobs-unified-top-card__company-name a', " +
                                "    '.job-details-jobs-unified-top-card__primary-description-container a[href*=\"/company/\"], ' + " +
                                "    '.job-details-jobs-unified-top-card__primary-description-container a', " +
                                "    '.jobs-unified-top-card__primary-description a[href*=\"/company/\"], ' + " +
                                "    'a.topcard__org-name-link', " +
                                "    'a[href*=\"/company/\"]'" +
                                "  ];" +
                                "  if (detailContainer) {" +
                                "    for (const sel of topCardSelectors) {" +
                                "      const el = detailContainer.querySelector(sel);" +
                                "      if (el && el.getAttribute('href')) {" +
                                "        const h = el.getAttribute('href');" +
                                "        if (h.includes('/company/')) {" +
                                "          companyUrl = h;" +
                                "          break;" +
                                "        }" +
                                "      }" +
                                "    }" +
                                "  }" +
                                "  if (!companyUrl) {" +
                                "    const anyLink = document.querySelector('.job-details-jobs-unified-top-card__company-name a, .jobs-unified-top-card__company-name a, a[href*=\"linkedin.com/company/\"]');" +
                                "    if (anyLink && anyLink.getAttribute('href')) {" +
                                "      companyUrl = anyLink.getAttribute('href');" +
                                "    }" +
                                "  }" +
                                "  " +
                                "  const fullDetailText = detailContainer ? detailContainer.innerText : '';" +
                                "  return JSON.stringify({" +
                                "    applyUrl: applyUrl," +
                                "    companySize: companySize," +
                                "    companyUrl: companyUrl," +
                                "    detailText: fullDetailText.substring(0, 10000)" +
                                "  });" +
                                "}");

                        String detailText = "";
                        String applyUrl = "";
                        String companySize = "";
                        String rawCompanyUrl = "";
                        if (paneInfoJson != null) {
                            JsonObject paneObj = JsonParser.parseString(paneInfoJson).getAsJsonObject();
                            applyUrl = paneObj.has("applyUrl") ? paneObj.get("applyUrl").getAsString() : "";
                            companySize = paneObj.has("companySize") ? paneObj.get("companySize").getAsString() : "";
                            rawCompanyUrl = paneObj.has("companyUrl") ? paneObj.get("companyUrl").getAsString() : "";
                            detailText = paneObj.has("detailText") ? paneObj.get("detailText").getAsString() : "";
                        }

                        String cleanCompanyUrl = CompanyWhitelistManager.cleanCompanyUrl(rawCompanyUrl);

                        if (companySize.isBlank()) {
                            Matcher csMatcher = COMPANY_SIZE_PATTERN.matcher(detailText);
                            if (csMatcher.find()) {
                                companySize = csMatcher.group(0).trim();
                            }
                        }

                        if (companySize.isBlank()) {
                            try {
                                Locator generalEmp = page.locator("text=/([\\d,]+(?:\\s*-\\s*[\\d,]+|\\+)?\\s+employees?)/i").first();
                                generalEmp.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.ATTACHED).setTimeout(2000));
                                String txt = generalEmp.innerText();
                                Matcher csMatcher = COMPANY_SIZE_PATTERN.matcher(txt);
                                if (csMatcher.find()) {
                                    companySize = csMatcher.group(0).trim();
                                }
                            } catch (Exception ignored) {}
                        }

                        // Decode safety redirect apply URL if present
                        if (applyUrl.contains("linkedin.com/safety/go/?url=")) {
                            try {
                                int start = applyUrl.indexOf("url=") + 4;
                                int end = applyUrl.indexOf("&", start);
                                String rawUrl = end > start ? applyUrl.substring(start, end) : applyUrl.substring(start);
                                applyUrl = URLDecoder.decode(rawUrl, StandardCharsets.UTF_8);
                            } catch (Exception ignored) {}
                        }

                        // Extract date posted
                        Matcher dateMatcher = DATE_POSTED_PATTERN.matcher(fullText);
                        String datePosted = dateMatcher.find() ? dateMatcher.group(0) : "Past week";

                        // Company size filter (minimum 500 employees, unless whitelisted)
                        boolean whitelisted = whitelistManager.isWhitelisted(company, cleanCompanyUrl);
                        if (whitelisted) {
                            System.out.println("    [★] Whitelisted company - bypassing employee size filter: " + company + " (" + (companySize.isBlank() ? "unknown size" : companySize) + ")");
                        } else if (!CompanySizeFilter.meetsMinimumCompanySize(companySize)) {
                            System.out.println("    [-] Skipping company with < 500 employees: " + company + " (" + (companySize.isBlank() ? "unknown size" : companySize) + ")");
                            whitelistManager.recordRejectedCompany(company, cleanCompanyUrl);
                            continue;
                        }

                        // Extract Experience Required
                        String expReq = "";
                        Matcher expMatcher = EXP_REQUIRED_PATTERN.matcher(detailText);
                        if (expMatcher.find()) {
                            expReq = expMatcher.group(0).trim();
                        }

                        seenUrls.add(jobUrl);
                        JobPost post = new JobPost(
                                "LinkedIn",
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
                    } catch (Exception e) {
                        System.err.println("    [!] Error parsing LinkedIn Profile Match card: " + e.getMessage());
                        if (page.isClosed()) {
                            System.err.println("[!] LinkedIn page is closed. Stopping card iteration.");
                            break;
                        }
                    }
                }

                System.out.println("[*] Page " + currentPage + " complete. Total jobs collected so far: " + jobs.size());

                // If not last page, navigate to next page by clicking Next
                if (currentPage < maxPages) {
                    // Scroll to bottom of lazy column to render pagination controls
                    page.evaluate("() => {" +
                            "  const col = document.querySelector(\"div[data-testid='lazy-column']\");" +
                            "  if (col) col.scrollTop = col.scrollHeight;" +
                            "}");

                    Locator nextBtn = page.locator("[data-testid='pagination-controls-next-button-visible'], button[aria-label='Next']").first();
                    boolean hasNext = false;
                    try {
                        nextBtn.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(3000));
                        hasNext = nextBtn.isVisible() && nextBtn.isEnabled();
                    } catch (Exception e) {
                        hasNext = false;
                    }

                    if (!hasNext) {
                        System.out.println("[*] No Next button available or reached last page at page " + currentPage + ". Finishing pagination.");
                        break;
                    }

                    System.out.println("[*] Navigating to page " + (currentPage + 1) + " by clicking Next...");
                    try {
                        nextBtn.scrollIntoViewIfNeeded();
                        nextBtn.click();
                    } catch (Exception e) {
                        System.out.println("[*] Playwright click failed, trying DOM click for Next button...");
                        page.evaluate("() => {" +
                                "  const btn = document.querySelector(\"[data-testid='pagination-controls-next-button-visible'], button[aria-label='Next']\");" +
                                "  if (btn) btn.click();" +
                                "}");
                    }

                    int nextPage = currentPage + 1;
                    boolean pageLoaded = false;
                    try {
                        Locator nextIndicator = page.locator("button[data-testid^='pagination-indicator-'][aria-current='true']")
                                .filter(new Locator.FilterOptions().setHasText(String.valueOf(nextPage)));
                        nextIndicator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.ATTACHED).setTimeout(7000));
                        pageLoaded = true;
                    } catch (Exception ignored) {}

                    if (!pageLoaded) {
                        long waitStart = System.currentTimeMillis();
                        while (System.currentTimeMillis() - waitStart < 5000) {
                            String curCardKey = (String) page.evaluate("() => {" +
                                    "  const col = document.querySelector(\"div[data-testid='lazy-column']\");" +
                                    "  const b = col ? col.querySelector(\"div[role='button'][componentkey^='job-card-component-ref-']\") : null;" +
                                    "  return b ? (b.getAttribute('componentkey') || b.innerText) : '';" +
                                    "}");
                            if (curCardKey != null && !curCardKey.isBlank() && !firstCardKey.isBlank() && !curCardKey.contains(firstCardKey)) {
                                pageLoaded = true;
                                break;
                            }
                            try {
                                Thread.sleep(250);
                            } catch (InterruptedException ignored) {}
                        }
                    }

                    // Reset column scroll to top for the new page's cards
                    page.evaluate("() => {" +
                            "  const col = document.querySelector(\"div[data-testid='lazy-column']\");" +
                            "  if (col) col.scrollTop = 0;" +
                            "}");
                    try {
                        page.locator("div[data-testid='lazy-column'] div[role='button'][componentkey^='job-card-component-ref-']")
                                .first().waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.ATTACHED).setTimeout(4000));
                    } catch (Exception ignored) {}
                }
            }

        } catch (Exception e) {
            System.err.println("[!] Error during LinkedIn Profile Match scraping: " + e.getMessage());
        }

        System.out.println("[✓] LinkedIn Profile Match scraping finished. Collected " + jobs.size() + " jobs.\n");
        return jobs;
    }
}
