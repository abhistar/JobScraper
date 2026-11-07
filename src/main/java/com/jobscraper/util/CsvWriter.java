package com.jobscraper.util;

import com.jobscraper.model.JobPost;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Utility to write scraped jobs to CSV file with format:
 * <target_profile_name>_job_lookup_<ddmmyyyy>.csv
 */
public class CsvWriter {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("ddMMyyyy");

    /**
     * Generates standard file name for the output CSV.
     */
    public static String getOutputFileName(String targetProfileName) {
        String dateStr = LocalDate.now().format(DATE_FORMATTER);
        return String.format("%s_job_lookup_%s.csv", targetProfileName, dateStr);
    }

    /**
     * Writes list of JobPost objects to a CSV file in the specified directory.
     */
    public static File writeJobsToCsv(List<JobPost> jobs, String targetProfileName, File outputDir) throws IOException {
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        String fileName = getOutputFileName(targetProfileName);
        File outputFile = new File(outputDir, fileName);

        CSVFormat csvFormat = CSVFormat.DEFAULT.builder()
                .setHeader(JobPost.CSV_HEADERS)
                .build();

        try (FileWriter fileWriter = new FileWriter(outputFile, StandardCharsets.UTF_8);
             CSVPrinter csvPrinter = new CSVPrinter(fileWriter, csvFormat)) {

            for (JobPost job : jobs) {
                csvPrinter.printRecord(
                        job.source(),
                        job.jobTitle(),
                        job.company(),
                        job.location(),
                        job.experienceRequired(),
                        job.companySize(),
                        job.datePosted(),
                        job.jobUrl(),
                        job.applyUrl()
                );
            }
            csvPrinter.flush();
        }

        return outputFile;
    }
}
