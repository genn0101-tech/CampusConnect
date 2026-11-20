package com.campusconnect;

import com.campusconnect.model.Attendance;
import com.campusconnect.repository.AttendanceRepository;
import com.campusconnect.repository.UserRepository;
import com.campusconnect.service.AttendanceService;
import com.campusconnect.service.PersonService;
import com.campusconnect.service.PersonService.PersonRow;
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

// The People page and person fixes, using the sample CSV data (GRIT has 5 people at EVT001).
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:persontest")
@Transactional
class PersonTests {

    @Autowired
    private PersonService personService;
    @Autowired
    private AttendanceService attendanceService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AttendanceRepository attendanceRepository;

    @Test
    void peopleListShowsVisits() {
        List<PersonRow> people = personService.peopleOfGroup("GRIT");
        assertEquals(5, people.size());
        assertEquals("Bob Lee", people.get(0).name()); // sorted by name
        assertEquals("Participant", people.get(0).role());
        for (PersonRow row : people) {
            assertEquals(1, row.visits()); // everyone came to the one GRIT event
        }
    }

    @Test
    void nameAndEmailCanBeFixed() {
        assertNull(personService.updatePerson("GRIT", "987654321", " Jon Doe ", "jon@example.com", "987654321"));
        assertEquals("Jon Doe", userRepository.findById("987654321").get().getName());
        assertEquals("jon@example.com", userRepository.findById("987654321").get().getEmail());
        assertNotNull(personService.updatePerson("GRIT", "987654321", "", "jon@example.com", "987654321"));
        assertNotNull(personService.updatePerson("GRIT", "987654321", "Jon", "not-an-email", "987654321"));
    }

    @Test
    void newStudentIdMovesAttendance() {
        assertNull(personService.updatePerson("GRIT", "987654321", "John Doe", "john@email.com", "987650000"));

        assertFalse(userRepository.existsById("987654321")); // the old ID is gone
        assertEquals("John Doe", userRepository.findById("987650000").get().getName());
        assertTrue(attendanceRepository.findByStudentId("987654321").isEmpty());
        Attendance moved = attendanceService.findAttendance("EVT001", "987650000").orElseThrow();
        assertEquals(115L, moved.stayMinutes()); // 15:05-17:00, unchanged by the move
    }

    @Test
    void studentIdOfSomeoneElseIsRefused() {
        String error = personService.updatePerson("GRIT", "987654321", "John Doe", "john@email.com", "123456789");
        assertTrue(error.contains("already registered as Riley Morgan"));
        // Nothing changed
        assertEquals("John Doe", userRepository.findById("987654321").get().getName());
        assertEquals(1, attendanceRepository.findByStudentId("987654321").size());
    }

    @Test
    void onlyPeopleOfThisGroupCanBeEdited() {
        // Jane Smith (112233445) belongs to Japanese Club and never came to a GRIT event
        assertThrows(ResponseStatusException.class, () -> personService.getPersonOfGroup("GRIT", "112233445"));
    }

    @Test
    void notYouUndoesOnlyAnOpenCheckInOfThisEvent() {
        Attendance mistake = attendanceService.checkIn("EVT002", "123456789");
        attendanceService.undoCheckIn(mistake.getId(), "EVT003"); // wrong event: nothing happens
        assertTrue(attendanceService.findAttendance("EVT002", "123456789").isPresent());

        attendanceService.undoCheckIn(mistake.getId(), "EVT002");
        assertTrue(attendanceService.findAttendance("EVT002", "123456789").isEmpty());

        // A finished visit (already checked out) is never removed
        Attendance finished = attendanceService.findAttendance("EVT001", "123456789").orElseThrow();
        attendanceService.undoCheckIn(finished.getId(), "EVT001");
        assertTrue(attendanceService.findAttendance("EVT001", "123456789").isPresent());
    }
}
