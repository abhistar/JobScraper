package com.jobscraper.util;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;

import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Manages company whitelist for bypassing employee size filters.
 * Reads and maintains <targetProfileName>_company_whitelist.csv with columns:
 * company name, company link on linkedin, isWhitelisted.
 * Rejections are collected in memory during scraping and written to CSV at the end.
 */
public class CompanyWhitelistManager {

    public static final String[] CSV_HEADERS = {
            "company name",
            "company link on linkedin",
            "isWhitelisted"
    };

    private static final Pattern PATH_CLEAN_PATTERN = Pattern.compile(
            "/(home|life|about|jobs|people)(?:[/\\?#].*)?$",
            Pattern.CASE_INSENSITIVE
    );

    private static CompanyWhitelistManager instance;

    private final String targetProfileName;
    private final File whitelistFile;
    private final List<WhitelistEntry> entries = new ArrayList<>();
    private final Map<String, WhitelistEntry> nameIndex = new HashMap<>();
    private final Map<String, WhitelistEntry> urlIndex = new HashMap<>();

    public static class WhitelistEntry {
        private String companyName;
        private String companyLink;
        private boolean whitelisted;

        public WhitelistEntry(String companyName, String companyLink, boolean whitelisted) {
            this.companyName = companyName != null ? companyName.trim() : "";
            this.companyLink = companyLink != null ? companyLink.trim() : "";
            this.whitelisted = whitelisted;
        }

        public String getCompanyName() {
            return companyName;
        }

        public void setCompanyName(String companyName) {
            this.companyName = companyName != null ? companyName.trim() : "";
        }

        public String getCompanyLink() {
            return companyLink;
        }

        public void setCompanyLink(String companyLink) {
            this.companyLink = companyLink != null ? companyLink.trim() : "";
        }

        public boolean isWhitelisted() {
            return whitelisted;
        }

        public void setWhitelisted(boolean whitelisted) {
            this.whitelisted = whitelisted;
        }
    }

    /**
     * Formats the whitelist CSV file name as <targetProfileName>_company_whitelist.csv
     */
    public static String getWhitelistFileName(String targetProfileName) {
        String name = (targetProfileName != null && !targetProfileName.isBlank())
                ? targetProfileName.trim()
                : "default";
        return String.format("%s_company_whitelist.csv", name);
    }

    /**
     * Resolves the whitelist File in the specified directory.
     */
    public static File getWhitelistFile(String targetProfileName, File dir) {
        File parentDir = dir != null ? dir : new File(System.getProperty("user.dir"));
        return new File(parentDir, getWhitelistFileName(targetProfileName));
    }

    public static synchronized CompanyWhitelistManager getInstance(String targetProfileName, File dir) {
        File file = getWhitelistFile(targetProfileName, dir);
        if (instance == null || !instance.whitelistFile.equals(file)) {
            instance = new CompanyWhitelistManager(targetProfileName, file);
        }
        return instance;
    }

    public static synchronized CompanyWhitelistManager getInstance(String targetProfileName) {
        return getInstance(targetProfileName, new File(System.getProperty("user.dir")));
    }

    public static synchronized CompanyWhitelistManager getInstance(File customFile) {
        if (instance == null || !instance.whitelistFile.equals(customFile)) {
            String name = customFile.getName().replace("_company_whitelist.csv", "");
            instance = new CompanyWhitelistManager(name, customFile);
        }
        return instance;
    }

    public static synchronized CompanyWhitelistManager getInstance() {
        if (instance == null) {
            instance = getInstance("default");
        }
        return instance;
    }

    public static synchronized void resetInstance() {
        instance = null;
    }

    public CompanyWhitelistManager(String targetProfileName, File whitelistFile) {
        this.targetProfileName = targetProfileName != null ? targetProfileName.trim() : "default";
        this.whitelistFile = whitelistFile;
        load();
    }

    /**
     * Cleans LinkedIn company URLs by removing /home, /life, /about, /jobs, /people
     * and anything after them, along with query strings and trailing slashes.
     */
    public static String cleanCompanyUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return "";
        }
        String url = rawUrl.trim();

        // Handle URL-encoded safety redirects (e.g. linkedin.com/safety/go/?url=...)
        if (url.contains("linkedin.com/safety/go/?url=")) {
            try {
                int start = url.indexOf("url=") + 4;
                int end = url.indexOf("&", start);
                String sub = end > start ? url.substring(start, end) : url.substring(start);
                url = URLDecoder.decode(sub, StandardCharsets.UTF_8);
            } catch (Exception ignored) {}
        }

        // Relative path support
        if (url.startsWith("/")) {
            url = "https://www.linkedin.com" + url;
        }

        // 1. Remove /home, /life, /about, /jobs, /people and anything that follows
        Matcher m = PATH_CLEAN_PATTERN.matcher(url);
        if (m.find()) {
            url = url.substring(0, m.start());
        }

        // 2. Strip query string (?...) and fragment (#...) if any remain
        int q = url.indexOf('?');
        if (q != -1) {
            url = url.substring(0, q);
        }
        int h = url.indexOf('#');
        if (h != -1) {
            url = url.substring(0, h);
        }

        // 3. Strip trailing slashes
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }

        return url;
    }

    /**
     * Checks if a company is whitelisted by company name or cleaned LinkedIn company link.
     */
    public synchronized boolean isWhitelisted(String companyName, String rawOrCleanUrl) {
        String cleanUrl = cleanCompanyUrl(rawOrCleanUrl);
        if (!cleanUrl.isBlank()) {
            WhitelistEntry byUrl = urlIndex.get(cleanUrl.toLowerCase());
            if (byUrl != null && byUrl.isWhitelisted()) {
                return true;
            }
        }

        if (companyName != null && !companyName.isBlank()) {
            WhitelistEntry byName = nameIndex.get(companyName.trim().toLowerCase());
            if (byName != null && byName.isWhitelisted()) {
                return true;
            }
        }

        return false;
    }

    /**
     * Records a company rejected due to employee count into memory.
     * Collects all rejections in the single in-memory list without writing to disk immediately.
     * All entries are persisted to CSV at the end of scraping via save().
     * If the company is already present, preserves its isWhitelisted status.
     * Updates empty company link if a valid clean link is now available.
     * If not present, adds with isWhitelisted = false.
     */
    public synchronized void recordRejectedCompany(String companyName, String rawOrCleanUrl) {
        String cleanName = companyName != null ? companyName.trim() : "";
        String cleanUrl = cleanCompanyUrl(rawOrCleanUrl);

        if (cleanName.isBlank() && cleanUrl.isBlank()) {
            return;
        }

        WhitelistEntry existing = null;
        if (!cleanUrl.isBlank() && urlIndex.containsKey(cleanUrl.toLowerCase())) {
            existing = urlIndex.get(cleanUrl.toLowerCase());
        } else if (!cleanName.isBlank() && nameIndex.containsKey(cleanName.toLowerCase())) {
            existing = nameIndex.get(cleanName.toLowerCase());
        }

        if (existing != null) {
            // Already tracked: update missing fields but NEVER overwrite isWhitelisted
            if (existing.getCompanyLink().isBlank() && !cleanUrl.isBlank()) {
                existing.setCompanyLink(cleanUrl);
                urlIndex.put(cleanUrl.toLowerCase(), existing);
            }
            if (existing.getCompanyName().isBlank() && !cleanName.isBlank()) {
                existing.setCompanyName(cleanName);
                nameIndex.put(cleanName.toLowerCase(), existing);
            }
            return;
        }

        // New rejected company entry collected in memory
        WhitelistEntry newEntry = new WhitelistEntry(cleanName, cleanUrl, false);
        entries.add(newEntry);
        if (!cleanName.isBlank()) {
            nameIndex.put(cleanName.toLowerCase(), newEntry);
        }
        if (!cleanUrl.isBlank()) {
            urlIndex.put(cleanUrl.toLowerCase(), newEntry);
        }
    }

    /**
     * Loads the whitelist CSV from disk into memory.
     */
    public synchronized void load() {
        entries.clear();
        nameIndex.clear();
        urlIndex.clear();

        File fileToLoad = whitelistFile;
        if (fileToLoad == null || !fileToLoad.exists() || !fileToLoad.isFile()) {
            // Check for legacy company_whitelist.csv if profile-specific target file does not exist yet
            if (whitelistFile != null && whitelistFile.getParentFile() != null) {
                File legacy = new File(whitelistFile.getParentFile(), "company_whitelist.csv");
                if (legacy.exists() && legacy.isFile()) {
                    fileToLoad = legacy;
                } else {
                    return;
                }
            } else {
                return;
            }
        }

        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreHeaderCase(true)
                .setTrim(true)
                .build();

        try (FileReader reader = new FileReader(fileToLoad, StandardCharsets.UTF_8);
             CSVParser parser = format.parse(reader)) {

            for (CSVRecord record : parser) {
                String name = "";
                String link = "";
                String whitelistedStr = "false";

                if (record.isMapped("company name")) {
                    name = record.get("company name");
                } else if (record.size() > 0) {
                    name = record.get(0);
                }

                if (record.isMapped("company link on linkedin")) {
                    link = record.get("company link on linkedin");
                } else if (record.size() > 1) {
                    link = record.get(1);
                }

                if (record.isMapped("isWhitelisted")) {
                    whitelistedStr = record.get("isWhitelisted");
                } else if (record.size() > 2) {
                    whitelistedStr = record.get(2);
                }

                boolean whitelisted = parseBoolean(whitelistedStr);
                String cleanUrl = cleanCompanyUrl(link);
                WhitelistEntry entry = new WhitelistEntry(name, cleanUrl, whitelisted);
                entries.add(entry);

                if (!name.isBlank()) {
                    nameIndex.put(name.trim().toLowerCase(), entry);
                }
                if (!cleanUrl.isBlank()) {
                    urlIndex.put(cleanUrl.toLowerCase(), entry);
                }
            }
        } catch (Exception e) {
            System.err.println("[!] Warning: Error reading whitelist CSV (" + whitelistFile + "): " + e.getMessage());
        }
    }

    /**
     * Writes all collected whitelist entries to the CSV file at the end in a single write operation.
     */
    public synchronized void save() {
        if (whitelistFile == null) return;

        File parent = whitelistFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader(CSV_HEADERS)
                .build();

        try (FileWriter writer = new FileWriter(whitelistFile, StandardCharsets.UTF_8);
             CSVPrinter printer = new CSVPrinter(writer, format)) {

            for (WhitelistEntry entry : entries) {
                printer.printRecord(
                        entry.getCompanyName(),
                        entry.getCompanyLink(),
                        entry.isWhitelisted()
                );
            }
            printer.flush();
            System.out.println("[✓] Saved " + entries.size() + " company whitelist entries to: " + whitelistFile.getName());
        } catch (IOException e) {
            System.err.println("[!] Error saving whitelist CSV (" + whitelistFile + "): " + e.getMessage());
        }
    }

    public List<WhitelistEntry> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    public File getWhitelistFile() {
        return whitelistFile;
    }

    public String getTargetProfileName() {
        return targetProfileName;
    }

    private static boolean parseBoolean(String val) {
        if (val == null) return false;
        String s = val.trim().toLowerCase();
        return s.equals("true") || s.equals("yes") || s.equals("1") || s.equals("t") || s.equals("y");
    }
}
