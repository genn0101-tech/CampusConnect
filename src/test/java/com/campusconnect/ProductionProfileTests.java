package com.campusconnect;

import com.campusconnect.repository.AttendanceRepository;
import com.campusconnect.repository.EventRepository;
import com.campusconnect.repository.GroupRepository;
import com.campusconnect.repository.InvitationRepository;
import com.campusconnect.repository.UserRepository;
import com.campusconnect.service.SiteSecrets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Starts the app with the "prod" profile. Only the database is replaced by an in-memory H2,
// so no PostgreSQL is needed for this test. The secrets are fake test values.
@SpringBootTest(properties = {
        "spring.profiles.active=prod",
        "spring.datasource.url=jdbc:h2:mem:prodtest",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "app.group-creation-code=prod-test-create",
        "app.admin-passphrase=prod-test-admin"
})
class ProductionProfileTests {

    @Autowired
    private GroupRepository groupRepository;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AttendanceRepository attendanceRepository;
    @Autowired
    private InvitationRepository invitationRepository;
    @Autowired
    private SiteSecrets siteSecrets;
    @Autowired
    private Environment environment;

    @Test
    void productionStartsWithEmptyDatabase() {
        assertEquals(0, groupRepository.count());
        assertEquals(0, eventRepository.count());
        assertEquals(0, userRepository.count());
        assertEquals(0, attendanceRepository.count());
        assertEquals(0, invitationRepository.count());
    }

    @Test
    void developmentValuesAreNotLoaded() {
        // application-dev.properties is not read in production
        assertNull(environment.getProperty("app.passphrase.grit"));
        assertFalse(siteSecrets.isGroupCreationCode("dev-create"));
        assertFalse(siteSecrets.isAdminPassphrase("dev-admin"));
        assertTrue(siteSecrets.isGroupCreationCode("prod-test-create"));
    }
}
