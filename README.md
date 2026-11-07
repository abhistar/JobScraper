# JobScraper

Java Playwright job scraping automation for Microsoft Edge browser.

## Features

- **Profile Checker Script**: Detects and inspects Microsoft Edge browser profiles (`Local State`), outputting profile Name, directory, and email.
- **Profile Validation**: Restricts browser profile selection strictly to `"Abhinav"` or `"Anushree"`.
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

Verify / check a specific profile (checks that profile is only among "Abhinav" or "Anushree"):
```bash
# Allowed profiles:
mvn exec:java -Dexec.mainClass="com.jobscraper.ProfileChecker" -Dexec.args="--profile=Abhinav"
mvn exec:java -Dexec.mainClass="com.jobscraper.ProfileChecker" -Dexec.args="--profile=Anushree"

# Check profile and launch Edge with that profile if not already open:
mvn exec:java -Dexec.mainClass="com.jobscraper.ProfileChecker" -Dexec.args="--profile=Abhinav --open"
mvn exec:java -Dexec.mainClass="com.jobscraper.ProfileChecker" -Dexec.args="--profile=Anushree --open"

# Disallowed profiles will be rejected:
mvn exec:java -Dexec.mainClass="com.jobscraper.ProfileChecker" -Dexec.args="--profile=Unknown"
```

### 3. Run Script 2: Main Job Scraper
Run job scraper with default profile ("Abhinav"):
```bash
mvn exec:java -Dexec.mainClass="com.jobscraper.JobScraperApp"
```

Run job scraper with a specific profile:
```bash
# For Abhinav:
mvn exec:java -Dexec.mainClass="com.jobscraper.JobScraperApp" -Dexec.args="--profile=Abhinav"

# For Anushree:
mvn exec:java -Dexec.mainClass="com.jobscraper.JobScraperApp" -Dexec.args="--profile=Anushree"
```

### 4. Run Unit Tests
```bash
mvn test
```
