package com.campusconnect.service;

import com.campusconnect.model.Attendance;
import com.campusconnect.model.Event;
import com.campusconnect.model.Invitation;
import com.campusconnect.model.User;
import com.campusconnect.repository.AttendanceRepository;
import com.campusconnect.repository.EventRepository;
import com.campusconnect.repository.InvitationRepository;
import com.campusconnect.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The group's People page: everyone who came to this group's events (or was registered by this group),
 * and fixing a person's name, email, or Student ID.
 * One Student ID = one person in the whole app, so a fix is seen by every group.
 */
@Service
public class PersonService {

    private static final int MAX_NAME_LENGTH = 60;

    /** One line of the People list. */
    public record PersonRow(String studentId, String name, String email, String role, int visits) {
    }

    private final UserRepository userRepository;
    private final AttendanceRepository attendanceRepository;
    private final InvitationRepository invitationRepository;
    private final EventRepository eventRepository;

    public PersonService(UserRepository userRepository, AttendanceRepository attendanceRepository,
                         InvitationRepository invitationRepository, EventRepository eventRepository) {
        this.userRepository = userRepository;
        this.attendanceRepository = attendanceRepository;
        this.invitationRepository = invitationRepository;
        this.eventRepository = eventRepository;
    }

    /** People of this group with how many of its events they came to, sorted by name. */
    public List<PersonRow> peopleOfGroup(String groupId) {
        // Count visits: Student ID -> number of this group's events attended
        Map<String, Integer> visits = new HashMap<>();
        for (Event event : eventRepository.findByGroupId(groupId)) {
            for (Attendance a : attendanceRepository.findByEventId(event.getEventId())) {
                visits.put(a.getStudentId(), visits.getOrDefault(a.getStudentId(), 0) + 1);
            }
        }
        // People registered by this group count too, even with 0 visits
        for (User user : userRepository.findByGroupId(groupId)) {
            visits.putIfAbsent(user.getStudentId(), 0);
        }

        List<PersonRow> rows = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : visits.entrySet()) {
            User user = userRepository.findById(entry.getKey()).orElse(null);
            if (user != null) {
                rows.add(new PersonRow(user.getStudentId(), user.getName(), user.getEmail(),
                        user.displayRole(), entry.getValue()));
            }
        }
        rows.sort(Comparator.comparing(PersonRow::name, String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    /** Finds a person this group may edit, or "404 Not Found". */
    public User getPersonOfGroup(String groupId, String studentId) {
        for (PersonRow row : peopleOfGroup(groupId)) {
            if (row.studentId().equals(studentId)) {
                return userRepository.findById(studentId).orElseThrow();
            }
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Person not found");
    }

    /**
     * Saves a fixed name, email and Student ID. Returns an error message, or null if saved.
     * A new Student ID moves ALL of this person's attendance (every group) to the new ID.
     * @Transactional: everything is saved together, or nothing if something fails.
     */
    @Transactional
    public String updatePerson(String groupId, String oldStudentId,
                               String name, String email, String newStudentId) {
        User person = getPersonOfGroup(groupId, oldStudentId);
        name = name.trim();
        email = email.trim();
        newStudentId = newStudentId.trim();

        if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
            return "Name is required (up to " + MAX_NAME_LENGTH + " characters).";
        }
        if (!email.contains("@")) {
            return "Please enter a valid email.";
        }
        if (!newStudentId.matches(User.STUDENT_ID_PATTERN)) {
            return "Student ID must be 1-20 letters or numbers.";
        }

        if (newStudentId.equals(oldStudentId)) {
            person.changeNameAndEmail(name, email);
            userRepository.save(person);
            return null;
        }

        // The new ID must not belong to anyone else, so two people are never mixed up
        User other = userRepository.findById(newStudentId).orElse(null);
        if (other != null) {
            return "Student ID " + newStudentId + " is already registered as " + other.getName() + ".";
        }
        if (!attendanceRepository.findByStudentId(newStudentId).isEmpty()) {
            return "Student ID " + newStudentId + " already has attendance records.";
        }

        // The Student ID is the primary key and cannot be changed in place:
        // save the person under the new ID, move their records, then remove the old ID.
        User moved = new User(newStudentId, name, email, person.getGroupId(), person.getRole());
        userRepository.save(moved);
        for (Attendance a : attendanceRepository.findByStudentId(oldStudentId)) {
            a.changeStudentId(newStudentId);
            attendanceRepository.save(a);
        }
        for (Invitation invitation : invitationRepository.findByStudentId(oldStudentId)) {
            invitation.changeStudentId(newStudentId);
            invitationRepository.save(invitation);
        }
        userRepository.delete(person);
        return null;
    }
}
