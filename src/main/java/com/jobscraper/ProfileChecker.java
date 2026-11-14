package com.jobscraper;

import com.jobscraper.model.EdgeProfile;
import com.jobscraper.util.EdgeProfileManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Optional;

/**
 * Script 1: Profile Checker & Browser Profile Launcher
 * 
 * Checks current available profiles in Microsoft Edge browser and outputs
 * profile Name, directory, email, and whether it is actively running.
 * Supports verifying/selecting a specific profile and opening the browser
 * with that profile if requested.
 */
public class ProfileChecker {

    /**
     * Retrieves all available Edge profiles on the system.
     */
    public static List<EdgeProfile> getAvailableProfiles() {
        return EdgeProfileManager.getAvailableProfiles();
    }

    /**
     * Finds the profile matching the given profile name or directory (case-insensitive).
     */
    public static Optional<EdgeProfile> findProfile(String targetProfile) {
        if (targetProfile == null || targetProfile.isBlank()) {
            return Optional.empty();
        }
        String query = targetProfile.trim();
        for (EdgeProfile p : getAvailableProfiles()) {
            if (p.name().equalsIgnoreCase(query) || p.directory().equalsIgnoreCase(query)) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }

    public static void main(String[] args) {
        String targetProfile = null;
        boolean openBrowser = false;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("--profile=")) {
                targetProfile = arg.substring("--profile=".length()).trim();
            } else if (arg.equals("--profile") || arg.equals("-p")) {
                if (i + 1 < args.length) {
                    targetProfile = args[++i].trim();
                }
            } else if (arg.equals("--open") || arg.equals("--launch")) {
                openBrowser = true;
            } else if (!arg.startsWith("-")) {
                targetProfile = arg.trim();
            }
        }

        // If target profile is provided, check against available profiles grabbed from ProfileChecker
        if (targetProfile != null && !targetProfile.isBlank()) {
            final String requested = targetProfile;
            Optional<EdgeProfile> matchedProfile = findProfile(requested);
            if (matchedProfile.isEmpty()) {
                List<String> names = getAvailableProfiles().stream().map(EdgeProfile::name).toList();
                System.err.println("[!] ERROR: Invalid browser profile: '" + requested + "'. Available profiles: " + names);
                System.exit(1);
                return;
            }

            EdgeProfile profile = matchedProfile.get();
            boolean isRunning = isProfileRunning(profile);

            System.out.println("Profile Name: " + profile.name() +
                               ", Directory: " + profile.directory() +
                               ", Email: " + (profile.email().isBlank() ? "N/A" : profile.email()) +
                               ", Running: " + (isRunning ? "Yes" : "No"));

            if (openBrowser) {
                if (isRunning) {
                    System.out.println("[*] Microsoft Edge is already running with profile: " + profile.name() + " (" + profile.directory() + ")");
                } else {
                    System.out.println("[*] Launching Microsoft Edge for profile: " + profile.name() + " (" + profile.directory() + ")...");
                    launchBrowserProfile(profile);
                }
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
            boolean isRunning = isProfileRunning(p);
            System.out.println("Profile Name: " + p.name() +
                               ", Directory: " + p.directory() +
                               ", Email: " + (p.email().isBlank() ? "N/A" : p.email()) +
                               ", Running: " + (isRunning ? "Yes" : "No"));
        }
    }

    /**
     * Checks if Microsoft Edge is running with the specified profile directory.
     */
    public static boolean isProfileRunning(EdgeProfile profile) {
        String targetDir = profile.directory();
        if (targetDir == null || targetDir.isBlank()) {
            return false;
        }

        // 1. Check via ProcessHandle if commandLine info is available
        try {
            boolean matched = ProcessHandle.allProcesses().anyMatch(ph -> {
                Optional<String> cmd = ph.info().commandLine();
                if (cmd.isPresent()) {
                    String cl = cmd.get();
                    if (cl.toLowerCase().contains("edge") || cl.toLowerCase().contains("msedge")) {
                        return cl.contains("--profile-directory=" + targetDir) ||
                               cl.contains("--profile-directory=\"" + targetDir + "\"") ||
                               ("Default".equalsIgnoreCase(targetDir) && !cl.contains("--profile-directory") && cl.contains("/MacOS/Microsoft Edge"));
                    }
                }
                return false;
            });
            if (matched) return true;
        } catch (Exception ignored) {}

        // 2. Fallback check via OS-specific process list
        String os = System.getProperty("os.name", "").toLowerCase();
        try {
            Process process;
            if (os.contains("mac")) {
                process = new ProcessBuilder("ps", "-Ao", "args").start();
            } else if (os.contains("linux")) {
                process = new ProcessBuilder("pgrep", "-af", "edge").start();
            } else if (os.contains("win")) {
                process = new ProcessBuilder("cmd.exe", "/c", "wmic process where \"name like '%msedge%'\" get commandline").start();
            } else {
                return false;
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.toLowerCase().contains("edge") || line.toLowerCase().contains("msedge")) {
                        if (line.contains("--profile-directory=" + targetDir) ||
                            line.contains("--profile-directory=\"" + targetDir + "\"")) {
                            return true;
                        }
                        if ("Default".equalsIgnoreCase(targetDir) && !line.contains("--profile-directory") && line.contains("/MacOS/Microsoft Edge")) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        return false;
    }

    /**
     * Launches Microsoft Edge configured with the specified profile directory.
     */
    public static void launchBrowserProfile(EdgeProfile profile) {
        String os = System.getProperty("os.name", "").toLowerCase();
        try {
            ProcessBuilder pb;
            if (os.contains("mac")) {
                pb = new ProcessBuilder("open", "-na", "Microsoft Edge", "--args", "--profile-directory=" + profile.directory());
            } else if (os.contains("win")) {
                pb = new ProcessBuilder("cmd.exe", "/c", "start", "msedge", "--profile-directory=" + profile.directory());
            } else {
                pb = new ProcessBuilder("microsoft-edge", "--profile-directory=" + profile.directory());
            }
            pb.start();
            System.out.println("[✓] Successfully opened Microsoft Edge with profile: " + profile.name() + " (" + profile.directory() + ")");
        } catch (Exception e) {
            System.err.println("[!] Failed to launch browser for profile " + profile.name() + ": " + e.getMessage());
        }
    }
}
