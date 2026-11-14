package com.jobscraper;

import com.jobscraper.model.EdgeProfile;
import com.jobscraper.util.EdgeProfileManager;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class EdgeProfileManagerTest {

    @Test
    public void testAvailableProfilesPassValidation() {
        List<EdgeProfile> profiles = EdgeProfileManager.getAvailableProfiles();
        if (!profiles.isEmpty()) {
            EdgeProfile first = profiles.get(0);
            assertDoesNotThrow(() -> EdgeProfileManager.validateProfileName(first.name()));
            assertDoesNotThrow(() -> EdgeProfileManager.validateProfileName(first.name().toLowerCase()));
            assertEquals(first.name(), EdgeProfileManager.normalizeProfileName(first.name().toLowerCase()));
        }
    }

    @Test
    public void testNonExistentProfilesThrowException() {
        assertThrows(IllegalArgumentException.class, () -> EdgeProfileManager.validateProfileName("NonExistentProfile_12345XYZ"));
        assertThrows(IllegalArgumentException.class, () -> EdgeProfileManager.validateProfileName("UnknownProfile"));
        assertThrows(IllegalArgumentException.class, () -> EdgeProfileManager.validateProfileName(""));
        assertThrows(IllegalArgumentException.class, () -> EdgeProfileManager.validateProfileName(null));
    }

    @Test
    public void testProfileNormalizationAndRetrieval() {
        List<EdgeProfile> profiles = EdgeProfileManager.getAvailableProfiles();
        if (!profiles.isEmpty()) {
            for (EdgeProfile p : profiles) {
                assertEquals(p.name(), EdgeProfileManager.normalizeProfileName(p.name()));
                EdgeProfile retrieved = EdgeProfileManager.getProfileForName(p.name());
                assertEquals(p.name(), retrieved.name());
            }
        }
    }
}
