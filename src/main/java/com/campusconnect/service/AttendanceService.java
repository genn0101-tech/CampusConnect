package com.campusconnect.service;

import com.campusconnect.model.Attendance;
import com.campusconnect.model.Event;
import com.campusconnect.model.User;
import com.campusconnect.repository.AttendanceRepository;
import com.campusconnect.repository.EventRepository;
import com.campusconnect.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Check-in / check-out, guest registration, stay duration and attendance rate. */
@Service
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final Clock clock;

    public AttendanceService(AttendanceRepository attendanceRepository, UserRepository userRepository,
                             EventRepository eventRepository, Clock clock) {
        this.attendanceRepository = attendanceRepository;
        this.userRepository = userRepository;
        this.eventRepository = eventRepository;
        this.clock = clock;
    }

    // ---------- Check-in / check-out ----------

    public Optional<User> findUser(String studentId) {
        return userRepository.findById(studentId);
    }

    public Optional<Attendance> findAttendance(String eventId, String studentId) {
        return attendanceRepository.findByEventIdAndStudentId(eventId, studentId);
    }

    /** Creates a new check-in record with the current time. Call only if the student has no record yet. */
    public Attendance checkIn(String eventId, String studentId) {
        Attendance attendance = new Attendance(eventId, studentId, now(), null);
        return attendanceRepository.save(attendance);
    }

    public Attendance checkOut(Attendance attendance) {
        attendance.checkOut(now());
        return attendanceRepository.save(attendance);
    }

    /** Registers a first-time visitor as a Guest of the event's group. */
    public User registerGuest(String studentId, String name, String email, String groupId) {
        User guest = new User(studentId, name, email, groupId, User.ROLE_GUEST);
        return userRepository.save(guest);
    }

    // ---------- Stay duration ----------

    /** Participant list of one event, in check-in order. */
    public List<ParticipantRow> participants(String eventId) {
        List<ParticipantRow> rows = new ArrayList<>();
        for (Attendance a : attendanceRepository.findByEventId(eventId)) {
            // Look up name and role from users (they are not stored in attendance)
            Optional<User> user = userRepository.findById(a.getStudentId());
            String name = user.map(User::getName).orElse("(unknown)");
            String email = user.map(User::getEmail).orElse("");
            String role = user.map(User::getRole).orElse("");
            rows.add(new ParticipantRow(a.getStudentId(), name, email, role,
                    a.getCheckInTime(), a.getCheckOutTime(), a.stayMinutes()));
        }
        rows.sort(Comparator.comparing(ParticipantRow::checkInTime));
        return rows;
    }

    /** Average stay in minutes of people who already checked out. Returns null if nobody has. */
    public Double averageStayMinutes(List<ParticipantRow> rows) {
        long total = 0;
        int count = 0;
        for (ParticipantRow row : rows) {
            if (row.stayMinutes() != null) {
                total += row.stayMinutes();
                count++;
            }
        }
        if (count == 0) {
            return null;
        }
        return (double) total / count;
    }

    // ---------- Attendance rate ----------

    /**
     * For each non-Guest user of the group:
     * (events of the group they attended) / (events of the group held so far).
     * "Held" = event date is today or earlier. Guests are not included.
     */
    public List<MemberRate> attendanceRates(String groupId) {
        LocalDate today = LocalDate.now(clock);

        Set<String> heldEventIds = new HashSet<>();
        for (Event event : eventRepository.findByGroupId(groupId)) {
            if (!event.getEventDate().isAfter(today)) {
                heldEventIds.add(event.getEventId());
            }
        }

        List<MemberRate> rates = new ArrayList<>();
        for (User user : userRepository.findByGroupId(groupId)) {
            if (user.isGuest()) {
                continue;
            }
            int attended = 0;
            for (Attendance a : attendanceRepository.findByStudentId(user.getStudentId())) {
                if (heldEventIds.contains(a.getEventId())) {
                    attended++; // at most one record per event, so no double counting
                }
            }
            int held = heldEventIds.size();
            Double rate = (held == 0) ? null : attended * 100.0 / held;
            rates.add(new MemberRate(user.getStudentId(), user.getName(), user.getRole(), attended, held, rate));
        }
        rates.sort(Comparator.comparing(MemberRate::name));
        return rates;
    }

    private LocalDateTime now() {
        // Cut seconds so times match the yyyy-MM-dd HH:mm format used everywhere
        return LocalDateTime.now(clock).withSecond(0).withNano(0);
    }
}
