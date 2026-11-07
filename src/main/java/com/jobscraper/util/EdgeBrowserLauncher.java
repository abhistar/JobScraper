package com.jobscraper.util;

import com.jobscraper.model.EdgeProfile;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Playwright;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages launching, connecting to, and shutting down Microsoft Edge in remote debugging mode.
 */
public class EdgeBrowserLauncher {

    private static final int DEBUG_PORT = 9222;
    private static final String CDP_URL = "http://127.0.0.1:" + DEBUG_PORT;

    private Playwright playwright;
    private Browser browser;
    private Process launchedProcess;

    /**
     * Checks if CDP endpoint is reachable.
     */
    public static boolean isCdpAvailable() {
        try {
            URL url = URI.create(CDP_URL + "/json/version").toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(1500);
            conn.setReadTimeout(1500);
            conn.setRequestMethod("GET");
            return conn.getResponseCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Locates the Microsoft Edge executable path on the machine.
     */
    public static String findEdgeExecutable() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("mac")) {
            String macEdge = "/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge";
            if (Files.exists(Paths.get(macEdge))) return macEdge;
        } else if (os.contains("win")) {
            String[] winPaths = {
                    System.getenv("ProgramFiles(x86)") + "\\Microsoft\\Edge\\Application\\msedge.exe",
                    System.getenv("ProgramFiles") + "\\Microsoft\\Edge\\Application\\msedge.exe",
                    System.getenv("LOCALAPPDATA") + "\\Microsoft\\Edge\\Application\\msedge.exe"
            };
            for (String p : winPaths) {
                if (Files.exists(Paths.get(p))) return p;
            }
        } else {
            String[] linuxPaths = {"/usr/bin/microsoft-edge", "/usr/bin/microsoft-edge-stable"};
            for (String p : linuxPaths) {
                if (Files.exists(Paths.get(p))) return p;
            }
        }
        return "microsoft-edge";
    }

    /**
     * Resolves the user data directory to use for remote debugging.
     * Chromium/Edge requires a non-default user data directory for --remote-debugging-port.
     */
    public static String resolveDebugUserDataDir(EdgeProfile profile) {
        String home = System.getProperty("user.home");
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("mac")) {
            File debugDir = new File(home, "Library/Application Support/Microsoft Edge Debug");
            if (debugDir.exists()) {
                return debugDir.getAbsolutePath();
            }
            return new File(home, "Library/Application Support/Microsoft Edge Debug").getAbsolutePath();
        } else if (os.contains("win")) {
            String localAppData = System.getenv("LOCALAPPDATA");
            return localAppData + "\\Microsoft\\Edge Debug\\User Data";
        }
        return home + "/.config/microsoft-edge-debug";
    }

    /**
     * Resolves profile directory name for the given debug user data dir.
     */
    public static String resolveProfileDirectory(EdgeProfile profile, String userDataDir) {
        if (userDataDir != null && userDataDir.contains("Microsoft Edge Debug")) {
            if ("Anushree".equalsIgnoreCase(profile.name())) {
                File prof1 = new File(userDataDir, "Profile 1");
                if (prof1.exists()) return "Profile 1";
            } else if ("Abhinav".equalsIgnoreCase(profile.name())) {
                return "Default";
            }
        }
        return profile.directory();
    }

    /**
     * Connects to an existing Edge debug session or launches a new one for the given profile.
     */
    public Browser connectOrLaunch(EdgeProfile profile) throws Exception {
        if (!isCdpAvailable()) {
            String debugUserDataDir = resolveDebugUserDataDir(profile);
            String profileDir = resolveProfileDirectory(profile, debugUserDataDir);

            System.out.println("[*] Edge is not running in debug mode. Launching Edge with profile: " + profile.name() + " (" + profileDir + ")...");
            String edgeExe = findEdgeExecutable();
            List<String> cmd = new ArrayList<>();
            cmd.add(edgeExe);
            cmd.add("--remote-debugging-port=" + DEBUG_PORT);
            cmd.add("--remote-allow-origins=*");
            cmd.add("--profile-directory=" + profileDir);
            cmd.add("--no-first-run");
            cmd.add("--no-default-browser-check");
            cmd.add("--user-data-dir=" + debugUserDataDir);

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            launchedProcess = pb.start();

            // Wait for CDP to become active (up to 15 seconds)
            long deadline = System.currentTimeMillis() + 15000;
            boolean ready = false;
            while (System.currentTimeMillis() < deadline) {
                if (isCdpAvailable()) {
                    ready = true;
                    break;
                }
                Thread.sleep(500);
            }
            if (!ready) {
                throw new IllegalStateException("Failed to launch Edge with remote debugging port " + DEBUG_PORT);
            }
            System.out.println("[✓] Edge launched successfully in debug mode on port " + DEBUG_PORT);
        } else {
            System.out.println("[✓] Connected to existing Edge debug session on port " + DEBUG_PORT);
        }

        playwright = Playwright.create();
        browser = playwright.chromium().connectOverCDP(CDP_URL);
        return browser;
    }

    public Playwright getPlaywright() {
        return playwright;
    }

    public Browser getBrowser() {
        return browser;
    }

    /**
     * Shuts down the debug session of the opened Edge browser.
     * Closes the Playwright CDP connection and terminates the Edge debug process.
     */
    public void shutdownDebugSession() {
        System.out.println("[*] Shutting down debug mode of the Edge browser session...");
        try {
            if (browser != null && browser.isConnected()) {
                browser.close();
            }
        } catch (Exception e) {
            System.err.println("[!] Warning closing browser connection: " + e.getMessage());
        }

        try {
            if (playwright != null) {
                playwright.close();
            }
        } catch (Exception e) {
            System.err.println("[!] Warning closing playwright: " + e.getMessage());
        }

        // Terminate any Edge process listening on debug port 9222
        try {
            List<Long> pids = getPidsOnPort(DEBUG_PORT);
            for (Long pid : pids) {
                System.out.println("[*] Stopping Edge debug process PID: " + pid);
                ProcessHandle.of(pid).ifPresent(ph -> {
                    ph.destroy();
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException ignored) {}
                    if (ph.isAlive()) {
                        ph.destroyForcibly();
                    }
                });
            }
            if (launchedProcess != null && launchedProcess.isAlive()) {
                launchedProcess.destroy();
            }
            System.out.println("[✓] Edge browser debug session closed and shut down.");
        } catch (Exception e) {
            System.err.println("[!] Error while stopping debug process: " + e.getMessage());
        }
    }

    /**
     * Utility to find process IDs listening on a specific port (macOS/Linux).
     */
    private static List<Long> getPidsOnPort(int port) {
        List<Long> pids = new ArrayList<>();
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("mac") || os.contains("linux")) {
            try {
                Process p = new ProcessBuilder("lsof", "-ti", ":" + port).start();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (!line.isEmpty()) {
                            try {
                                pids.add(Long.parseLong(line));
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                }
                p.waitFor();
            } catch (Exception ignored) {}
        }
        return pids;
    }
}
