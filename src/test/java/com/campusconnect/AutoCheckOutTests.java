package com.campusconnect;

import com.campusconnect.model.Attendance;
import com.campusconnect.model.Event;
import com.campusconnect.model.User;
import com.campusconnect.repository.AttendanceRepository;
import com.campusconnect.repository.UserRepository;
import com.campusconnect.service.AttendanceService;
import com.campusconnect.service.EventService;
import com.campusconnect.service.EventService.MeetingTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

// Meeting times and the automatic check-out at the end time.
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:autotest")
@Transactional
class AutoCheckOutTests {

    @Autowired
    private AttendanceService attendanceService;
    @Autowired
    private EventService eventService;
    @Autowired
    private AttendanceRepository attendanceRepository;
    @Autowired
    private UserRepository userRepository;

    private Attendance checkIn(String eventId, String studentId, LocalDateTime time) {
        userRepository.save(new User(studentId, "Person " + studentId, studentId + "@example.com", "GRIT", User.ROLE_GUEST));
        return attendanceRepository.save(new Attendance(eventId, studentId, time, null));
    }

    /** An event that ran from 5 hours ago to 3 hours ago (already over). */
    private Event endedEvent() {
        LocalDateTime now = attendanceService.currentTime();
        return eventService.createEvent("Ended", now.toLocalDate(), "", "GRIT", null,
                new MeetingTime(now.minusHours(5), now.minusHours(3)));
    }

    @Test
    void attendanceRateStartsAtTheFirstVisit() {
        // GRIT: sample EVT001 (March) + two new held events
        LocalDate today = attendanceService.currentTime().toLocalDate();
        Event lastWeek = eventService.createEvent("Last week", today.minusDays(7), "", "GRIT");
        Event yesterday = eventService.createEvent("Yesterday", today.minusDays(1), "", "GRIT");
        eventService.createEvent("Next week", today.plusDays(7), "", "GRIT"); // not held yet: not counted

        // A newcomer who first came last week and then missed yesterday: 1 / 2 (March is not counted)
        checkIn(lastWeek.getEventId(), "800001", today.minusDays(7).atTime(19, 0));
        // Riley came in March and yesterday: 2 / 3
        attendanceRepository.save(new Attendance(yesterday.getEventId(), "123456789", today.minusDays(1).atTime(19, 0), null));

        var rates = attendanceService.attendanceRates("GRIT");
        var newcomer = rates.stream().filter(r -> r.studentId().equals("800001")).findFirst().orElseThrow();
        assertEquals("Participant", newcomer.role());
        assertEquals(1, newcomer.attendedEvents());
        assertEquals(2, newcomer.heldEvents());
        assertEquals(50.0, newcomer.ratePercent());
        var riley = rates.stream().filter(r -> r.studentId().equals("123456789")).findFirst().orElseThrow();
        assertEquals(2, riley.attendedEvents());
        assertEquals(3, riley.heldEvents());
    }

    @Test
    void endTimeBeforeStartMeansNextDay() {
        MeetingTime t = EventService.meetingTime(LocalDate.of(2026, 11, 24), LocalTime.of(22, 0), LocalTime.of(1, 0));
        assertEquals(LocalDateTime.of(2026, 11, 24, 22, 0), t.start());
        assertEquals(LocalDateTime.of(2026, 11, 25, 1, 0), t.end());
    }

    @Test
    void peopleStillHereAreCheckedOutAtTheEndTime() {
        Event event = endedEvent();
        Attendance stayed = checkIn(event.getEventId(), "800001", event.getStartAt());
        Attendance late = checkIn(event.getEventId(), "800002", event.getEndAt().plusMinutes(30)); // after the end
        Attendance left = checkIn(event.getEventId(), "800003", event.getStartAt());
        attendanceService.checkOut(left); // left by QR before: not touched

        assertEquals(2, attendanceService.autoCheckOutIfOver(event));
        assertEquals(event.getEndAt(), stayed.getCheckOutTime());
        assertEquals("Auto", stayed.checkoutSourceLabel());
        assertEquals(120L, stayed.stayMinutes()); // not 1372 minutes any more
        assertEquals(0L, late.stayMinutes()); // checked in after the end: out at the check-in time
        assertEquals("QR", left.checkoutSourceLabel());
        assertEquals("Auto", attendanceService.participants(event.getEventId()).get(0).checkoutSource());

        assertEquals(0, attendanceService.autoCheckOutIfOver(event)); // nothing left to do
    }

    @Test
    void nothingHappensBeforeTheEndOrWithoutAnEndTime() {
        LocalDateTime now = attendanceService.currentTime();
        Event running = eventService.createEvent("Running", now.toLocalDate(), "", "GRIT", null,
                new MeetingTime(now.minusHours(1), now.plusHours(1)));
        checkIn(running.getEventId(), "800001", now.minusMinutes(30));
        assertEquals(0, attendanceService.autoCheckOutIfOver(running));

        Event noTime = eventService.createEvent("Old style", now.toLocalDate().minusDays(3), "", "GRIT");
        checkIn(noTime.getEventId(), "800002", now.minusDays(3));
        assertEquals(0, attendanceService.autoCheckOutIfOver(noTime)); // works as before
    }

    @Test
    void extendingTheEndUndoesAutoCheckOuts() {
        Event event = endedEvent();
        Attendance stayed = checkIn(event.getEventId(), "800001", event.getStartAt());
        Attendance byOwner = checkIn(event.getEventId(), "800002", event.getStartAt());
        attendanceService.updateTimes(byOwner, byOwner.getCheckInTime(), event.getStartAt().plusMinutes(10));
        attendanceService.autoCheckOutIfOver(event);
        assertEquals("Auto", stayed.checkoutSourceLabel());

        // Extended until 1 hour from now: the meeting is running again
        LocalDateTime now = attendanceService.currentTime();
        eventService.updateEvent(event.getEventId(), "GRIT", "Ended", event.getEventDate(), "",
                new MeetingTime(event.getStartAt(), now.plusHours(1)));
        assertNull(stayed.getCheckOutTime()); // still here again
        assertEquals("Owner", byOwner.checkoutSourceLabel()); // a check-out by the group is kept
    }
}
