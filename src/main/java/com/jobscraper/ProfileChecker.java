package com.jobscraper;

import com.jobscraper.model.EdgeProfile;
import com.jobscraper.util.EdgeProfileManager;

import java.util.List;

/**
 * Script 1: Profile Checker
 * 
 * Checks current available profiles in Microsoft Edge browser and outputs
 * profile Name, directory, and email. Supports verifying/selecting a specific
 * profile among the allowed choices ("Abhinav" or "Anushree").
 */
public class ProfileChecker {

    public static void main(String[] args) {
        String targetProfile = null;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("--profile=")) {
                targetProfile = arg.substring("--profile=".length()).trim();
            } else if (arg.equals("--profile") || arg.equals("-p")) {
                if (i + 1 < args.length) {
                    targetProfile = args[++i].trim();
                }
            } else if (!arg.startsWith("-")) {
                targetProfile = arg.trim();
            }
        }

        // If target profile is provided, perform strict validation
        if (targetProfile != null && !targetProfile.isBlank()) {
            try {
                EdgeProfileManager.validateProfileName(targetProfile);
                EdgeProfile profile = EdgeProfileManager.getProfileForName(targetProfile);
                System.out.println("Profile Name: " + profile.name() +
                                   ", Directory: " + profile.directory() +
                                   ", Email: " + (profile.email().isBlank() ? "N/A" : profile.email()));
            } catch (IllegalArgumentException e) {
                System.err.println("[!] ERROR: " + e.getMessage());
                System.exit(1);
            }
            return;
        }

        // Otherwise list all available profiles
        List<EdgeProfile> profiles = EdgeProfileManager.getAvailableProfiles();
        if (profiles.isEmpty()) {
            System.out.println("No Edge profiles found.");
            return;
        }

        for (EdgeProfile p : profiles) {
            System.out.println("Profile Name: " + p.name() +
                               ", Directory: " + p.directory() +
                               ", Email: " + (p.email().isBlank() ? "N/A" : p.email()));
        }
    }
}
