package com.jobscraper.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jobscraper.model.EdgeProfile;

import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Manages detection, parsing, and validation of Microsoft Edge browser profiles.
 */
public class EdgeProfileManager {

    /**
     * Returns candidate user data directories based on the operating system.
     */
    public static List<Path> getCandidateUserDataDirs() {
        List<Path> paths = new ArrayList<>();
        String home = System.getProperty("user.home");
        String os = System.getProperty("os.name", "").toLowerCase();

        if (os.contains("mac")) {
            paths.add(Paths.get(home, "Library", "Application Support", "Microsoft Edge"));
            paths.add(Paths.get(home, "Library", "Application Support", "Microsoft Edge Debug"));
        } else if (os.contains("win")) {
            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData != null) {
                paths.add(Paths.get(localAppData, "Microsoft", "Edge", "User Data"));
            }
        } else {
            paths.add(Paths.get(home, ".config", "microsoft-edge"));
        }

        return paths.stream().filter(Files::exists).toList();
    }

    /**
     * Reads all Edge browser profiles available in the system.
     */
    public static List<EdgeProfile> getAvailableProfiles() {
        Map<String, EdgeProfile> profileMap = new LinkedHashMap<>();

        for (Path userDataDir : getCandidateUserDataDirs()) {
            File localStateFile = userDataDir.resolve("Local State").toFile();
            if (!localStateFile.exists() || !localStateFile.isFile()) {
                continue;
            }

            try (FileReader reader = new FileReader(localStateFile)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                if (!root.has("profile") || !root.getAsJsonObject("profile").has("info_cache")) {
                    continue;
                }

                JsonObject infoCache = root.getAsJsonObject("profile").getAsJsonObject("info_cache");
                for (Map.Entry<String, JsonElement> entry : infoCache.entrySet()) {
                    String dirKey = entry.getKey();
                    if (!entry.getValue().isJsonObject()) {
                        continue;
                    }
                    JsonObject pObj = entry.getValue().getAsJsonObject();

                    String name = pObj.has("name") ? pObj.get("name").getAsString() : dirKey;
                    String email = pObj.has("user_name") ? pObj.get("user_name").getAsString() : "";

                    // If name is a placeholder (e.g. "Profile 1") but there's a gaia/account name, use it
                    if ((name == null || name.startsWith("Profile ") || name.equalsIgnoreCase("Default")) && pObj.has("edge_account_first_name")) {
                        String firstName = pObj.get("edge_account_first_name").getAsString();
                        if (!firstName.isBlank()) {
                            name = firstName;
                        }
                    }
                    if ((name == null || name.startsWith("Profile ") || name.equalsIgnoreCase("Default")) && pObj.has("gaia_name")) {
                        String gaiaName = pObj.get("gaia_name").getAsString();
                        if (!gaiaName.isBlank()) {
                            name = gaiaName;
                        }
                    }

                    // Key by lower case name to deduplicate between standard and debug copies
                    String dedupKey = name.trim().toLowerCase();
                    if (!profileMap.containsKey(dedupKey) || (!email.isBlank() && profileMap.get(dedupKey).email().isBlank())) {
                        profileMap.put(dedupKey, new EdgeProfile(name, dirKey, email, userDataDir.toString()));
                    }
                }
            } catch (Exception e) {
                System.err.println("[!] Warning: Error reading " + localStateFile + ": " + e.getMessage());
            }
        }

        return new ArrayList<>(profileMap.values());
    }

    /**
     * Validates that the requested target profile name matches an available profile.
     * Throws IllegalArgumentException if invalid.
     */
    public static void validateProfileName(String targetName) {
        if (targetName == null || targetName.isBlank()) {
            throw new IllegalArgumentException("Browser profile name cannot be null or empty.");
        }
        List<EdgeProfile> profiles = getAvailableProfiles();
        boolean matched = profiles.stream().anyMatch(p ->
                p.name().equalsIgnoreCase(targetName.trim()) ||
                p.directory().equalsIgnoreCase(targetName.trim()));
        if (!matched) {
            List<String> names = profiles.stream().map(EdgeProfile::name).toList();
            throw new IllegalArgumentException(
                    "Invalid browser profile: '" + targetName + "'. " +
                    "Available profiles: " + names
            );
        }
    }

    /**
     * Normalizes the target profile name to canonical profile name from available profiles.
     */
    public static String normalizeProfileName(String targetName) {
        if (targetName == null || targetName.isBlank()) {
            throw new IllegalArgumentException("Browser profile name cannot be null or empty.");
        }
        List<EdgeProfile> profiles = getAvailableProfiles();
        for (EdgeProfile p : profiles) {
            if (p.name().equalsIgnoreCase(targetName.trim()) ||
                p.directory().equalsIgnoreCase(targetName.trim())) {
                return p.name();
            }
        }
        throw new IllegalArgumentException(
                "Invalid browser profile: '" + targetName + "'. " +
                "Available profiles: " + profiles.stream().map(EdgeProfile::name).toList()
        );
    }

    /**
     * Finds the EdgeProfile matching the target profile name or directory.
     */
    public static EdgeProfile getProfileForName(String targetName) {
        if (targetName == null || targetName.isBlank()) {
            throw new IllegalArgumentException("Browser profile name cannot be null or empty.");
        }
        List<EdgeProfile> profiles = getAvailableProfiles();

        for (EdgeProfile p : profiles) {
            if (p.name().equalsIgnoreCase(targetName.trim()) || p.directory().equalsIgnoreCase(targetName.trim())) {
                return p;
            }
        }

        if (profiles.isEmpty()) {
            List<Path> dirs = getCandidateUserDataDirs();
            String uDir = dirs.isEmpty() ? "" : dirs.get(0).toString();
            return new EdgeProfile(targetName.trim(), "Default", "", uDir);
        }

        throw new IllegalArgumentException(
                "Invalid browser profile: '" + targetName + "'. " +
                "Available profiles: " + profiles.stream().map(EdgeProfile::name).toList()
        );
    }
}
