package com.jobscraper;

import com.jobscraper.util.EdgeProfileManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class EdgeProfileManagerTest {

    @Test
    public void testAllowedProfilesPassValidation() {
        assertDoesNotThrow(() -> EdgeProfileManager.validateProfileName("Abhinav"));
        assertDoesNotThrow(() -> EdgeProfileManager.validateProfileName("abhinav"));
        assertDoesNotThrow(() -> EdgeProfileManager.validateProfileName("Anushree"));
        assertDoesNotThrow(() -> EdgeProfileManager.validateProfileName("anushree"));
    }

    @Test
    public void testDisallowedProfilesThrowException() {
        assertThrows(IllegalArgumentException.class, () -> EdgeProfileManager.validateProfileName("Random"));
        assertThrows(IllegalArgumentException.class, () -> EdgeProfileManager.validateProfileName("John"));
        assertThrows(IllegalArgumentException.class, () -> EdgeProfileManager.validateProfileName(""));
        assertThrows(IllegalArgumentException.class, () -> EdgeProfileManager.validateProfileName(null));
    }

    @Test
    public void testNormalization() {
        assertEquals("Abhinav", EdgeProfileManager.normalizeProfileName("abhinav"));
        assertEquals("Abhinav", EdgeProfileManager.normalizeProfileName("Abhinav"));
        assertEquals("Anushree", EdgeProfileManager.normalizeProfileName("anushree"));
        assertEquals("Anushree", EdgeProfileManager.normalizeProfileName("Anushree"));
    }
}
