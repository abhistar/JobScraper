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
        EdgeProfile testProf = new EdgeProfile("TestProfile", "Profile 1", "test@example.com", "/tmp");
        String dir = EdgeBrowserLauncher.resolveProfileDirectory(testProf, "/tmp/Microsoft Edge Debug");
        assertNotNull(dir);

        EdgeProfile defaultProf = new EdgeProfile("DefaultProfile", "Default", "default@example.com", "/tmp");
        String defaultDir = EdgeBrowserLauncher.resolveProfileDirectory(defaultProf, "/tmp/Microsoft Edge Debug");
        assertEquals("Default", defaultDir);
    }

    @Test
    void testGetCdpUrl() {
        EdgeBrowserLauncher launcher = new EdgeBrowserLauncher();
        String cdpUrl = launcher.getCdpUrl();
        assertTrue(cdpUrl.startsWith("http://127.0.0.1:"));
        assertEquals("http://127.0.0.1:" + launcher.getDebugPort(), cdpUrl);
    }
}
