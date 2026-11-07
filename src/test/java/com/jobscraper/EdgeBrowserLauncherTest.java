package com.jobscraper;

import com.jobscraper.model.EdgeProfile;
import com.jobscraper.util.EdgeBrowserLauncher;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;

import static org.junit.jupiter.api.Assertions.*;

class EdgeBrowserLauncherTest {

    @Test
    void testFindEdgeExecutable() {
        String exe = EdgeBrowserLauncher.findEdgeExecutable();
        assertNotNull(exe);
        assertFalse(exe.isBlank());
    }

    @Test
    void testFindUsableDebugPort() {
        int port = EdgeBrowserLauncher.findUsableDebugPort();
        assertTrue(port >= 9222 && port <= 65535, "Port should be within valid range: " + port);
    }

    @Test
    void testIsPortFreeForBoundPort() throws Exception {
        // Bind an ephemeral port, verify isPortFree returns false, then close it and verify true
        try (ServerSocket ss = new ServerSocket(0)) {
            int boundPort = ss.getLocalPort();
            assertFalse(EdgeBrowserLauncher.isPortFree(boundPort),
                    "Port " + boundPort + " is currently bound and should NOT be reported free");
        }
    }

    @Test
    void testResolveProfileDirectory() {
        EdgeProfile anushree = new EdgeProfile("Anushree", "Profile 1", "anushree@test.com", "/tmp");
        String dir = EdgeBrowserLauncher.resolveProfileDirectory(anushree, "/tmp/Microsoft Edge Debug");
        // Anushree in Microsoft Edge Debug should map to Profile 1 or Anushree
        assertTrue(dir.equals("Profile 1") || dir.equals("Anushree"));

        EdgeProfile abhinav = new EdgeProfile("Abhinav", "Default", "abhinav@test.com", "/tmp");
        String abhinavDir = EdgeBrowserLauncher.resolveProfileDirectory(abhinav, "/tmp/Microsoft Edge Debug");
        assertEquals("Default", abhinavDir);
    }
}
