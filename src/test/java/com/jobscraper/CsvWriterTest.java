package com.jobscraper;

import com.jobscraper.model.JobPost;
import com.jobscraper.util.CsvWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class CsvWriterTest {

    @Test
    public void testOutputFileNameFormat() {
        String expectedDate = LocalDate.now().format(DateTimeFormatter.ofPattern("ddMMyyyy"));
        assertEquals("Abhinav_job_lookup_" + expectedDate + ".csv", CsvWriter.getOutputFileName("Abhinav"));
        assertEquals("Anushree_job_lookup_" + expectedDate + ".csv", CsvWriter.getOutputFileName("Anushree"));
    }

    @Test
    public void testWriteJobsToCsv(@TempDir Path tempDir) throws IOException {
        JobPost job1 = new JobPost(
                "LinkedIn",
                "Senior Backend Engineer",
                "Acme Corp",
                "Remote",
                "5+ years experience",
                "51-200 employees",
                "1 day ago",
                "https://www.linkedin.com/jobs/view/12345/",
                "https://acme.com/apply"
        );
        JobPost job2 = new JobPost(
                "Instahyre",
                "Staff Software Engineer",
                "Beta Inc",
                "Bangalore",
                "Java, Microservices",
                "200-500 employees",
                "2 days ago",
                "https://www.instahyre.com/job-999/",
                "https://www.instahyre.com/job-999/"
        );

        File outputFile = CsvWriter.writeJobsToCsv(List.of(job1, job2), "Abhinav", tempDir.toFile());
        assertTrue(outputFile.exists());

        List<String> lines = Files.readAllLines(outputFile.toPath());
        assertTrue(lines.size() >= 3);
        assertTrue(lines.get(0).contains("Source"));
        assertTrue(lines.get(0).contains("Apply URL"));
        assertTrue(lines.get(1).contains("Acme Corp"));
        assertTrue(lines.get(2).contains("Beta Inc"));
    }
}
