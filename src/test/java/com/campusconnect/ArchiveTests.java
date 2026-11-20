package com.campusconnect;

import com.campusconnect.model.Event;
import com.campusconnect.model.Invitation;
import com.campusconnect.repository.AttendanceRepository;
import com.campusconnect.repository.EventRepository;
import com.campusconnect.repository.InvitationRepository;
import com.campusconnect.repository.UserRepository;
import com.campusconnect.service.AttendanceService;
import com.campusconnect.service.EventService;
import com.campusconnect.service.MemberRate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Archive, restore, and delete, using the sample data (EVT001 = the one GRIT event, 5 attendees).
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:archivetest")
@Transactional
class ArchiveTests {

    @Autowired
    private EventService eventService;
    @Autowired
    private AttendanceService attendanceService;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private AttendanceRepository attendanceRepository;
    @Autowired
    private InvitationRepository invitationRepository;
    @Autowired
    private UserRepository userRepository;

    @Test
    void archivedEventIsHiddenButStillCounts() {
        eventService.setArchived("EVT001", "GRIT", true);

        assertTrue(eventService.pastEvents("GRIT").isEmpty());
        assertEquals("EVT001", eventService.archivedEvents("GRIT").get(0).getEventId());
        // Attendance rate still uses the archived event
        for (MemberRate rate : attendanceService.attendanceRates("GRIT")) {
            assertEquals(1, rate.heldEvents());
            assertEquals(100.0, rate.ratePercent());
        }
        // And the report data is still there
        assertEquals(5, attendanceService.participants("EVT001").size());

        eventService.setArchived("EVT001", "GRIT", false); // restore
        assertEquals(1, eventService.pastEvents("GRIT").size());
        assertTrue(eventService.archivedEvents("GRIT").isEmpty());
    }

    @Test
    void onlyArchivedEventsCanBeDeleted() {
        assertThrows(ResponseStatusException.class, () -> eventService.deleteArchivedEvent("EVT001", "GRIT"));
        assertTrue(eventRepository.existsById("EVT001"));
        // Another group's event cannot be touched
        assertThrows(ResponseStatusException.class, () -> eventService.setArchived("EVT002", "GRIT", true));
    }

    @Test
    void deleteRemovesAttendanceButKeepsPeople() {
        invitationRepository.save(new Invitation("EVT001", "123456789", "Present"));
        eventService.setArchived("EVT001", "GRIT", true);
        eventService.deleteArchivedEvent("EVT001", "GRIT");

        assertFalse(eventRepository.existsById("EVT001"));
        assertTrue(attendanceRepository.findByEventId("EVT001").isEmpty());
        assertTrue(invitationRepository.findByEventId("EVT001").isEmpty());
        assertTrue(userRepository.existsById("123456789")); // people stay
        assertEquals(4, attendanceRepository.findByEventId("EVT002").size()); // other events untouched
    }

    @Test
    void deletedEventIdIsNeverReused() {
        Event newest = eventService.createEvent("Newest", LocalDate.of(2026, 12, 1), "", "GRIT");
        assertEquals("EVT004", newest.getEventId());
        eventService.setArchived("EVT004", "GRIT", true);
        eventService.deleteArchivedEvent("EVT004", "GRIT");

        // A QR code printed for EVT004 must not open the next new event
        Event next = eventService.createEvent("Next", LocalDate.of(2026, 12, 2), "", "GRIT");
        assertEquals("EVT005", next.getEventId());
        List<Event> upcoming = eventService.upcomingEvents("GRIT");
        assertEquals(1, upcoming.size());
    }
}
