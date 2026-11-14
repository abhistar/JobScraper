# JobScraper

Java Playwright job scraping automation for Microsoft Edge browser.

## Features

- **Profile Checker Script**: Detects and inspects Microsoft Edge browser profiles (`Local State`), outputting profile Name, directory, and email.
- **Profile Validation**: Accepts any profile argument and validates it dynamically against available browser profiles detected by ProfileChecker.
- **LinkedIn Scraping**:
  - Navigates to `https://www.linkedin.com/jobs/`
  - Clicks `Show all ->` under the `Jobs based on your preferences` section
  - Applies date filter ("Past week")
  - Selects all jobs and extracts Company Name, Job Title, Location, LinkedIn Job URL, Apply Button URL (with safety redirect URL decoding) / Easy Apply, Company Employee Size, and Experience Required.
  - Skips already applied jobs and filters out small companies (<= 10 employees).
- **Instahyre Scraping**:
  - Navigates to `https://www.instahyre.com/candidate/opportunities/`
  - Collects all opportunity jobs on the page via Angular model and DOM inspection.
  - Filters out small companies.
- **CSV Export**:
  - Saves all collected jobs into `<target_profile_name>_job_lookup_<ddmmyyyy>.csv`
- **Session Management**:
  - Connects to existing Edge debug sessions or auto-launches Edge in debug mode.
  - Automatically shuts down the Edge debug session upon script completion.

---

## Commands to Run the Scripts

### 1. Build and Compile
```bash
mvn clean compile
```

### 2. Run Script 1: Profile Checker
Inspect all available Edge profiles:
```bash
mvn exec:java -Dexec.mainClass="com.jobscraper.ProfileChecker"
```

Verify / check a specific profile (validates against available Edge profiles):
```bash
# Check profile by name or directory:
mvn exec:java -Dexec.mainClass="com.jobscraper.ProfileChecker" -Dexec.args="--profile=<profile_name>"

# Check profile and launch Edge with that profile if not already open:
mvn exec:java -Dexec.mainClass="com.jobscraper.ProfileChecker" -Dexec.args="--profile=<profile_name> --open"

# Non-existent profiles will be rejected:
mvn exec:java -Dexec.mainClass="com.jobscraper.ProfileChecker" -Dexec.args="--profile=Unknown"
```

### 3. Run Script 2: Main Job Scraper
Run job scraper with default profile (auto-detects running profile or first available profile):
```bash
mvn exec:java -Dexec.mainClass="com.jobscraper.JobScraperApp"
```

Run job scraper with a specific profile:
```bash
mvn exec:java -Dexec.mainClass="com.jobscraper.JobScraperApp" -Dexec.args="--profile=<profile_name>"
```

### 4. Run Unit Tests
```bash
mvn test
```
