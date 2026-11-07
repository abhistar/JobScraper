package com.jobscraper.model;

/**
 * Represents a Microsoft Edge browser profile.
 */
public record EdgeProfile(
        String name,
        String directory,
        String email,
        String userDataDir
) {
    @Override
    public String toString() {
        return String.format("Profile Name: %s, Directory: %s, Email: %s",
                name != null ? name : "Unknown",
                directory != null ? directory : "Unknown",
                (email == null || email.isBlank()) ? "N/A" : email);
    }
}
