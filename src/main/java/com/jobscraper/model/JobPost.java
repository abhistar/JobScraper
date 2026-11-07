package com.jobscraper.model;

/**
 * Represents a job posting scraped from LinkedIn or Instahyre.
 */
public record JobPost(
        String source,
        String jobTitle,
        String company,
        String location,
        String experienceRequired,
        String companySize,
        String datePosted,
        String jobUrl,
        String applyUrl
) {
    public static final String[] CSV_HEADERS = {
            "Source",
            "Job Title",
            "Company",
            "Location",
            "Experience Required",
            "Company Size",
            "Date Posted",
            "Job URL",
            "Apply URL"
    };
}
