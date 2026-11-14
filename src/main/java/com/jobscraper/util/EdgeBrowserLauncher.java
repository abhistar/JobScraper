package com.jobscraper.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jobscraper.model.EdgeProfile;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Playwright;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Manages launching, connecting to, and shutting down Microsoft Edge in remote debugging mode.
 * Ensures that opening or closing the debug session never closes any already opened session of the browser.
 */
public class EdgeBrowserLauncher {

    private int debugPort = 9222;
    private Playwright playwright;
    private Browser browser;
    private Process launchedProcess;
    private boolean wasAlreadyRunning = false;

    /**
     * Checks if a valid CDP endpoint is actively responding on the given port.
     */
    public static boolean isCdpAvailable(int port) {
        try {
            URL url = URI.create("http://127.0.0.1:" + port + "/json/version").toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(1000);
            conn.setReadTimeout(1000);
            conn.setRequestMethod("GET");
            if (conn.getResponseCode() == 200) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    return sb.toString().contains("webSocketDebuggerUrl");
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Checks whether a TCP port is free to bind.
     */
    public static boolean isPortFree(int port) {
        // 1. Check if another process is already actively listening on 127.0.0.1:port
        try (java.net.Socket s = new java.net.Socket()) {
            s.connect(new java.net.InetSocketAddress("127.0.0.1", port), 200);
            return false; // Connection succeeded -> port is already occupied!
        } catch (Exception ignored) {
            // Not listening, proceed to check binding
        }

        // 2. Test binding specifically to 127.0.0.1 (IPv4 loopback)
        try (ServerSocket ss4 = new ServerSocket()) {
            ss4.setReuseAddress(false);
            ss4.bind(new java.net.InetSocketAddress(java.net.InetAddress.getByName("127.0.0.1"), port));
        } catch (Exception e) {
            return false;
        }

        // 3. Test binding to wildcard
        try (ServerSocket ss = new ServerSocket()) {
            ss.setReuseAddress(false);
            ss.bind(new java.net.InetSocketAddress(port));
        } catch (Exception e) {
            return false;
        }

        return true;
    }

    /**
     * Finds an appropriate debug port:
     * - Priority 1: Returns a port in 9222..9230 if a valid CDP session is already running.
     * - Priority 2: Returns the first completely free port (9222..9299) to launch a dedicated debug session.
     * Never conflicts with or closes any already running browser session.
     */
    public static int findUsableDebugPort() {
        for (int p = 9222; p <= 9230; p++) {
            if (isCdpAvailable(p)) {
                return p;
            }
        }
        for (int p = 9222; p <= 9299; p++) {
            if (isPortFree(p)) {
                return p;
            }
        }
        try (ServerSocket ss = new ServerSocket(0)) {
            return ss.getLocalPort();
        } catch (Exception e) {
            return 9223;
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
     * Uses a dedicated debug directory so that the user's regular browser session
     * is completely isolated and never disturbed.
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
            File localStateFile = new File(userDataDir, "Local State");
            if (localStateFile.exists() && localStateFile.isFile()) {
                try (FileReader reader = new FileReader(localStateFile)) {
                    JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                    if (root.has("profile") && root.getAsJsonObject("profile").has("info_cache")) {
                        JsonObject infoCache = root.getAsJsonObject("profile").getAsJsonObject("info_cache");
                        for (Map.Entry<String, JsonElement> entry : infoCache.entrySet()) {
                            String dirKey = entry.getKey();
                            if (!entry.getValue().isJsonObject()) continue;
                            JsonObject pObj = entry.getValue().getAsJsonObject();
                            String name = pObj.has("name") ? pObj.get("name").getAsString() : "";
                            String firstName = pObj.has("edge_account_first_name") ? pObj.get("edge_account_first_name").getAsString() : "";
                            String gaiaName = pObj.has("gaia_name") ? pObj.get("gaia_name").getAsString() : "";

                            if (dirKey.equalsIgnoreCase(profile.directory()) ||
                                dirKey.equalsIgnoreCase(profile.name()) ||
                                name.equalsIgnoreCase(profile.name()) ||
                                firstName.equalsIgnoreCase(profile.name()) ||
                                gaiaName.equalsIgnoreCase(profile.name())) {
                                return dirKey;
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }
            if (new File(userDataDir, profile.directory()).exists()) {
                return profile.directory();
            }
            if (new File(userDataDir, profile.name()).exists()) {
                return profile.name();
            }
        }
        return profile.directory();
    }

    /**
     * Connects to an already opened Edge debug session or launches a new dedicated debug instance.
     * Never closes or terminates any existing browser session.
     */
    public Browser connectOrLaunch(EdgeProfile profile) throws Exception {
        this.debugPort = findUsableDebugPort();
        this.wasAlreadyRunning = isCdpAvailable(debugPort);

        String cdpUrl = "http://127.0.0.1:" + debugPort;

        if (wasAlreadyRunning) {
            System.out.println("[✓] Connected to already opened browser session on port " + debugPort);
            System.out.println("[*] The existing browser session will be preserved when scraping completes.");
            playwright = Playwright.create();
            browser = playwright.chromium().connectOverCDP(cdpUrl);
            return browser;
        }

        // Dedicated debug instance launch
        String debugUserDataDir = resolveDebugUserDataDir(profile);
        String profileDir = resolveProfileDirectory(profile, debugUserDataDir);

        // Safely clear dead lock files in debug folder without touching any running process
        cleanupStaleLocks(debugUserDataDir);

        System.out.println("[*] Launching dedicated Edge debug session with profile: " + profile.name() + " (" + profileDir + ") on port " + debugPort + "...");
        String edgeExe = findEdgeExecutable();
        List<String> cmd = new ArrayList<>();
        cmd.add(edgeExe);
        cmd.add("--remote-debugging-port=" + debugPort);
        cmd.add("--remote-allow-origins=*");
        cmd.add("--profile-directory=" + profileDir);
        cmd.add("--no-first-run");
        cmd.add("--no-default-browser-check");
        cmd.add("--user-data-dir=" + debugUserDataDir);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        pb.redirectError(ProcessBuilder.Redirect.DISCARD);
        launchedProcess = pb.start();

        // Wait for CDP to become active (up to 30 seconds)
        long deadline = System.currentTimeMillis() + 30000;
        boolean ready = false;
        while (System.currentTimeMillis() < deadline) {
            if (isCdpAvailable(debugPort)) {
                ready = true;
                break;
            }
            Thread.sleep(600);
        }
        if (!ready) {
            throw new IllegalStateException("Failed to launch Edge with remote debugging port " + debugPort + " within timeout.");
        }
        System.out.println("[✓] Edge launched successfully in debug mode on port " + debugPort);

        playwright = Playwright.create();
        browser = playwright.chromium().connectOverCDP(cdpUrl);
        return browser;
    }

    /**
     * Cleans up stale lock symlinks in debug directory ONLY if the process that held them is dead.
     * Never terminates or interrupts any running process.
     */
    private static void cleanupStaleLocks(String userDataDir) {
        if (userDataDir == null || userDataDir.isBlank()) return;
        try {
            File dir = new File(userDataDir);
            if (!dir.exists()) return;
            Path lockPath = dir.toPath().resolve("SingletonLock");
            if (Files.isSymbolicLink(lockPath)) {
                String target = Files.readSymbolicLink(lockPath).toString();
                int dashIdx = target.lastIndexOf('-');
                if (dashIdx != -1) {
                    try {
                        long pid = Long.parseLong(target.substring(dashIdx + 1));
                        Optional<ProcessHandle> ph = ProcessHandle.of(pid);
                        if (ph.isEmpty() || !ph.get().isAlive()) {
                            Files.deleteIfExists(lockPath);
                            Files.deleteIfExists(dir.toPath().resolve("SingletonSocket"));
                            Files.deleteIfExists(dir.toPath().resolve("SingletonCookie"));
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
        } catch (Exception ignored) {}
    }

    public boolean wasAlreadyRunning() {
        return wasAlreadyRunning;
    }

    public int getDebugPort() {
        return debugPort;
    }

    public Playwright getPlaywright() {
        return playwright;
    }

    public Browser getBrowser() {
        return browser;
    }

    /**
     * Shuts down or disconnects from the debug session.
     * If the session was already opened before the script ran, it only disconnects
     * and leaves the user's browser session open and untouched.
     * If this script spawned the browser process itself, it terminates only its own spawned process.
     */
    public void shutdownDebugSession() {
        if (wasAlreadyRunning) {
            System.out.println("[*] Disconnecting from already opened browser session...");
            try {
                if (playwright != null) {
                    playwright.close();
                }
            } catch (Exception e) {
                System.err.println("[!] Warning disconnecting Playwright: " + e.getMessage());
            }
            System.out.println("[✓] Disconnected cleanly. The already opened browser session remains open and active.");
            return;
        }

        // Only shut down the process that this script itself launched
        System.out.println("[*] Shutting down debug mode of the Edge browser session spawned by this script...");
        try {
            if (browser != null && browser.isConnected()) {
                browser.close();
            }
        } catch (Exception ignored) {}

        try {
            if (playwright != null) {
                playwright.close();
            }
        } catch (Exception ignored) {}

        if (launchedProcess != null) {
            try {
                launchedProcess.toHandle().descendants().forEach(ProcessHandle::destroy);
                launchedProcess.destroy();
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ignored) {}
                if (launchedProcess.isAlive()) {
                    launchedProcess.toHandle().descendants().forEach(ProcessHandle::destroyForcibly);
                    launchedProcess.destroyForcibly();
                }
                System.out.println("[✓] Spawned Edge debug process terminated.");
            } catch (Exception e) {
                System.err.println("[!] Error terminating spawned process: " + e.getMessage());
            }
        }
    }
}
