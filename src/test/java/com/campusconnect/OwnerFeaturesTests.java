package com.campusconnect;

import com.campusconnect.model.Attendance;
import com.campusconnect.model.Event;
import com.campusconnect.model.User;
import com.campusconnect.repository.AttendanceRepository;
import com.campusconnect.repository.EventRepository;
import com.campusconnect.repository.UserRepository;
import com.campusconnect.service.AttendanceService;
import com.campusconnect.service.AttendanceService.BulkCheckoutResult;
import com.campusconnect.service.ParticipantRow;
import com.campusconnect.service.ReportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Tests for check-out by name, owner fixes, the Live order, and the Excel-friendly CSV.
// Each test makes its own event "EVT900"; @Transactional removes it again after the test.
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:ownertest")
@Transactional
class OwnerFeaturesTests {

    private static final String EVENT = "EVT900";
    private static final LocalDateTime SIX_PM = LocalDateTime.of(2026, 11, 24, 18, 0);

    @Autowired
    private AttendanceService attendanceService;
    @Autowired
    private ReportService reportService;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AttendanceRepository attendanceRepository;

    /** Saves a user and their check-in at the test event. */
    private Attendance checkedIn(String studentId, String name, LocalDateTime checkIn) {
        userRepository.save(new User(studentId, name, studentId + "@example.com", "GRIT", User.ROLE_GUEST));
        return attendanceRepository.save(new Attendance(EVENT, studentId, checkIn, null));
    }

    private void createEvent() {
        eventRepository.save(new Event(EVENT, "Test Night", LocalDate.of(2026, 11, 24), "Room 1", "GRIT"));
    }

    @Test
    void checkoutSourceIsRecorded() {
        createEvent();
        Attendance qr = checkedIn("900001", "Ana Lopez", SIX_PM);
        assertEquals("", qr.checkoutSourceLabel()); // not checked out yet

        attendanceService.checkOut(qr);
        assertEquals(Attendance.SOURCE_SELF, qr.getCheckoutSource());
        assertEquals("QR", qr.checkoutSourceLabel());

        // Rows saved before the column existed have null: they were QR check-outs
        Attendance old = new Attendance(EVENT, "900002", SIX_PM, SIX_PM.plusHours(1));
        assertNull(old.getCheckoutSource());
        assertEquals("QR", old.checkoutSourceLabel());
    }

    @Test
    void checkOutEveryoneSkipsLaterCheckIns() {
        createEvent();
        checkedIn("900001", "Ana Lopez", SIX_PM);
        checkedIn("900002", "Ben Ortiz", SIX_PM.plusMinutes(30));
        Attendance late = checkedIn("900003", "Cara Diaz", SIX_PM.plusHours(3)); // after the chosen time
        Attendance done = checkedIn("900004", "Dan Wu", SIX_PM);
        attendanceService.checkOut(done); // already left by QR: must not change

        BulkCheckoutResult result = attendanceService.checkOutEveryone(EVENT, SIX_PM.plusHours(2));
        assertEquals(2, result.checkedOut());
        assertEquals(1, result.skipped());
        assertNull(late.getCheckOutTime());
        assertEquals("QR", done.checkoutSourceLabel());
        assertEquals("Owner", attendanceService.findAttendance(EVENT, "900001").get().checkoutSourceLabel());
    }

    @Test
    void ownerCannotSaveCheckOutBeforeCheckIn() {
        createEvent();
        Attendance a = checkedIn("900001", "Ana Lopez", SIX_PM);
        String error = attendanceService.updateTimes(a, SIX_PM, SIX_PM.minusMinutes(1));
        assertNotNull(error);
        assertNull(a.getCheckOutTime()); // nothing was changed

        assertNull(attendanceService.updateTimes(a, SIX_PM, SIX_PM.plusMinutes(90)));
        assertEquals(90L, a.stayMinutes());
        assertEquals("Owner", a.checkoutSourceLabel());
    }

    @Test
    void fixingOnlyCheckInKeepsQrSource() {
        createEvent();
        Attendance a = checkedIn("900001", "Ana Lopez", SIX_PM);
        attendanceService.checkOut(a);
        LocalDateTime out = a.getCheckOutTime();

        assertNull(attendanceService.updateTimes(a, out.minusHours(1), out));
        assertEquals("QR", a.checkoutSourceLabel());

        // Emptying the check-out puts the person back to "still here"
        assertNull(attendanceService.updateTimes(a, out.minusHours(1), null));
        assertNull(a.getCheckOutTime());
        assertEquals("", a.checkoutSourceLabel());
    }

    @Test
    void nameCandidatesOnlyShowPeopleStillHere() {
        createEvent();
        checkedIn("900001", "Ana Lopez", SIX_PM);
        Attendance left = checkedIn("900002", "Anabel Kim", SIX_PM);
        attendanceService.checkOut(left);
        checkedIn("900003", "Ben Ortiz", SIX_PM);

        assertEquals(List.of("Ana Lopez"), attendanceService.checkoutCandidates(EVENT, "an"));
        assertEquals(List.of("Ben Ortiz"), attendanceService.checkoutCandidates(EVENT, "ORT"));

        // Never more than 5 names
        for (int i = 0; i < 7; i++) {
            checkedIn("91000" + i, "Sam Test" + i, SIX_PM);
        }
        assertEquals(5, attendanceService.checkoutCandidates(EVENT, "sam").size());
    }

    @Test
    void sameNameTwiceIsFoundTwice() {
        createEvent();
        checkedIn("900001", "Ana Lopez", SIX_PM);
        checkedIn("900002", "Ana Lopez", SIX_PM);
        assertEquals(2, attendanceService.stillHereWithName(EVENT, "ana lopez").size());
        // The name appears only once in the suggestions
        assertEquals(List.of("Ana Lopez"), attendanceService.checkoutCandidates(EVENT, "Ana"));
    }

    @Test
    void liveOrderPutsPeopleStillHereFirst() {
        createEvent();
        Attendance left = checkedIn("900001", "Ana Lopez", SIX_PM);
        attendanceService.checkOut(left);
        checkedIn("900002", "Ben Ortiz", SIX_PM.plusMinutes(10));

        List<ParticipantRow> rows = attendanceService.liveOrder(attendanceService.participants(EVENT));
        assertTrue(rows.get(0).isStillHere());
        assertFalse(rows.get(1).isStillHere());
    }

    @Test
    void guestIsShownAsParticipant() {
        assertEquals("Participant", User.displayRole(User.ROLE_GUEST));
        assertEquals("Member", User.displayRole("Member"));
        createEvent();
        checkedIn("900001", "Ana Lopez", SIX_PM);
        assertEquals("Participant", attendanceService.participants(EVENT).get(0).role());
        // The stored role is unchanged, so attendance-rate rules still see a Guest
        assertTrue(userRepository.findById("900001").get().isGuest());
    }

    @Test
    void csvIsExcelFriendly() {
        Event event = new Event(EVENT, "Test Night", LocalDate.of(2026, 11, 24), "Room 1", "GRIT");
        List<ParticipantRow> rows = List.of(
                new ParticipantRow(1L, "012345", "Ana Lopez", "a@example.com", "Participant",
                        LocalDateTime.of(2026, 11, 24, 20, 30), LocalDateTime.of(2026, 11, 24, 21, 5), 35L, "Owner"),
                new ParticipantRow(2L, "222333444", "=HYPERLINK(\"x\")", "b@example.com", "Member",
                        LocalDateTime.of(2026, 11, 24, 9, 0), null, null, ""));
        String csv = reportService.buildCsv(event, rows, 35.0);

        assertTrue(csv.startsWith("EventID,EventName,Date,Location,StudentID,Name,Email,Role,"
                + "CheckInTime,CheckOutTime,StayMinutes,CheckOutBy\n"));
        // Date, time, and Student ID are written as ="..." so Excel keeps them as text
        assertTrue(csv.contains("\"=\"\"11/24/2026\"\"\""));
        assertTrue(csv.contains("\"=\"\"012345\"\"\"")); // leading zero kept
        assertTrue(csv.contains("\"=\"\"11/24/2026 8:30 PM\"\"\""));
        assertTrue(csv.contains("\"=\"\"11/24/2026 9:05 PM\"\"\""));
        assertTrue(csv.contains("\"=\"\"11/24/2026 9:00 AM\"\"\""));
        assertTrue(csv.contains(",35,Owner\n"));
        // A name that looks like a formula gets a leading ' so Excel does not run it
        assertTrue(csv.contains("\"'=HYPERLINK(\"\"x\"\")\""));
    }
}
