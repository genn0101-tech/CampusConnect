package com.campusconnect;

import com.campusconnect.model.Attendance;
import com.campusconnect.model.Event;
import com.campusconnect.model.Group;
import com.campusconnect.repository.EventRepository;
import com.campusconnect.service.AttendanceService;
import com.campusconnect.service.EventService;
import com.campusconnect.service.GroupPassphrase;
import com.campusconnect.service.GroupService;
import com.campusconnect.service.MemberRate;
import com.campusconnect.service.ParticipantRow;
import com.campusconnect.service.SiteSecrets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Uses an in-memory database (not ./db) and test passphrases for each sample group.
// @Transactional rolls back every test, so each test starts from the sample CSV data.
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb",
        "app.passphrase.grit=test-grit",
        "app.passphrase.codeclub=test-code",
        "app.passphrase.japaneseclub=",
        "app.group-creation-code=test-create"
})
@Transactional
class CampusConnectApplicationTests {

    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private AttendanceService attendanceService;
    @Autowired
    private GroupPassphrase groupPassphrase;
    @Autowired
    private EventService eventService;
    @Autowired
    private GroupService groupService;
    @Autowired
    private SiteSecrets siteSecrets;

    @Test
    void sampleCsvDataIsLoaded() {
        assertEquals(3, eventRepository.count());
    }

    @Test
    void stayMinutesAndAverageForSampleEvent() {
        // EVT001 stays from attendance.csv: 120, 115, 105, 30, 75 minutes -> average 445 / 5 = 89.0
        List<ParticipantRow> rows = attendanceService.participants("EVT001");
        assertEquals(5, rows.size());
        assertEquals("Riley Morgan", rows.get(0).name()); // name comes from users, not attendance
        assertEquals(120L, rows.get(0).stayMinutes());
        assertEquals(89.0, attendanceService.averageStayMinutes(rows));
    }

    @Test
    void attendanceRateCountsEveryone() {
        // All 5 people who came to GRIT's one held event are counted, Bob Lee (Participant) too
        List<MemberRate> rates = attendanceService.attendanceRates("GRIT");
        assertEquals(5, rates.size());
        for (MemberRate rate : rates) {
            assertEquals(1, rate.heldEvents());
            assertEquals(100.0, rate.ratePercent());
        }
    }

    @Test
    void guestCanRegisterCheckInAndCheckOut() {
        attendanceService.registerGuest("555000111", "Test Guest", "guest@example.com", "GRIT");
        Attendance in = attendanceService.checkIn("EVT001", "555000111");
        assertNull(in.stayMinutes()); // still at the event

        Attendance out = attendanceService.checkOut(in);
        assertNotNull(out.stayMinutes());
        assertTrue(attendanceService.findUser("555000111").get().isGuest());
    }

    @Test
    void eachGroupHasItsOwnPassphrase() {
        Group grit = groupService.getGroup("GRIT");
        Group codeClub = groupService.getGroup("CodeClub");
        assertTrue(groupPassphrase.matches(grit, "test-grit"));
        assertTrue(groupPassphrase.matches(codeClub, "test-code"));
        // One group's passphrase does not open another group
        assertFalse(groupPassphrase.matches(codeClub, "test-grit"));
        assertFalse(groupPassphrase.matches(grit, "wrong"));
        assertFalse(groupPassphrase.matches(grit, null));
        // A group with no passphrase can never be unlocked
        assertNull(groupService.getGroup("JapaneseClub").getPassphraseHash());
        assertFalse(groupPassphrase.matches(groupService.getGroup("JapaneseClub"), ""));
    }

    @Test
    void newGroupStoresOnlyHashOfPassphrase() {
        Group group = groupService.createGroup("ISA Meeting Group", "", "isa-secret-123");
        assertEquals("ISAMeetingGroup", group.getGroupId());
        // The database holds a BCrypt hash, never the passphrase itself
        assertFalse(group.getPassphraseHash().contains("isa-secret-123"));
        assertTrue(group.getPassphraseHash().startsWith("$2"));
        assertTrue(groupPassphrase.matches(group, "isa-secret-123"));

        // Same name again gets a different ID instead of overwriting the first group
        Group second = groupService.createGroup("ISA Meeting Group", "", "other-secret-1");
        assertEquals("ISAMeetingGroup2", second.getGroupId());
    }

    @Test
    void resetPassphraseReplacesOldOne() {
        groupService.resetPassphrase("GRIT", "brand-new-pass");
        Group grit = groupService.getGroup("GRIT");
        assertTrue(groupPassphrase.matches(grit, "brand-new-pass"));
        assertFalse(groupPassphrase.matches(grit, "test-grit"));
    }

    @Test
    void passphraseRules() {
        assertNotNull(groupPassphrase.validate("short"));
        assertNotNull(groupPassphrase.validate(null));
        assertNotNull(groupPassphrase.validate("x".repeat(73))); // over BCrypt's 72-byte limit
        assertNull(groupPassphrase.validate("long-enough"));
    }

    @Test
    void groupCreationCodeIsChecked() {
        assertTrue(siteSecrets.isGroupCreationCode("test-create"));
        assertFalse(siteSecrets.isGroupCreationCode("wrong"));
        assertFalse(siteSecrets.isGroupCreationCode(null));
    }

    @Test
    void emptySecretsMeanNobodyGetsIn() {
        SiteSecrets notConfigured = new SiteSecrets("", "", false);
        assertFalse(notConfigured.isGroupCreationCode(""));
        assertFalse(notConfigured.isAdminPassphrase(""));
    }

    @Test
    void productionRefusesToStartWithoutSecrets() {
        assertThrows(IllegalStateException.class, () -> new SiteSecrets("", "admin-pass", true));
        assertThrows(IllegalStateException.class, () -> new SiteSecrets("create-code", "", true));
    }

    @Test
    void groupOnlySeesItsOwnEvents() {
        List<Event> events = eventService.eventsOfGroupNewestFirst("GRIT");
        assertEquals(1, events.size());
        assertEquals("EVT001", events.get(0).getEventId());
        // EVT002 belongs to JapaneseClub, so GRIT gets "404 Not Found"
        assertThrows(ResponseStatusException.class, () -> eventService.getEventInGroup("EVT002", "GRIT"));
    }
}
