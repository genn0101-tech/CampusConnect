package com.campusconnect.service;

import com.campusconnect.model.Attendance;
import com.campusconnect.model.Event;
import com.campusconnect.model.User;
import com.campusconnect.repository.AttendanceRepository;
import com.campusconnect.repository.EventRepository;
import com.campusconnect.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** Check-in / check-out, guest registration, time fixes, stay duration and attendance rate. */
@Service
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final Clock clock;

    private static final int MAX_CANDIDATES = 5;

    /** Result of "check out everyone": how many were checked out, how many were skipped. */
    public record BulkCheckoutResult(int checkedOut, int skipped) {
    }

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

    /**
     * "Not you?" on the check-in result page: removes a check-in that was just made by mistake.
     * Only if it belongs to this event and the person has not checked out yet.
     */
    public void undoCheckIn(Long attendanceId, String eventId) {
        attendanceRepository.findById(attendanceId).ifPresent(attendance -> {
            if (attendance.getEventId().equals(eventId) && !attendance.isCheckedOut()) {
                attendanceRepository.delete(attendance);
            }
        });
    }

    // ---------- Check-out by name ----------

    /**
     * Names (only names) of people at this event who have not checked out and whose name
     * contains the typed text. At most 5, so the list cannot be used to read everyone at once.
     */
    public List<String> checkoutCandidates(String eventId, String typed) {
        String query = typed.trim().toLowerCase(Locale.ROOT);
        List<String> names = new ArrayList<>();
        for (ParticipantRow row : participants(eventId)) {
            if (row.isStillHere() && row.name().toLowerCase(Locale.ROOT).contains(query)
                    && !names.contains(row.name())) {
                names.add(row.name());
            }
        }
        names.sort(String::compareToIgnoreCase);
        return names.size() > MAX_CANDIDATES ? names.subList(0, MAX_CANDIDATES) : names;
    }

    /**
     * Names (never Student IDs) of people still at this event whose Student ID starts with the typed digits.
     * Only names are returned, so nobody can collect other people's Student IDs from this list.
     */
    public List<String> checkoutCandidatesById(String eventId, String typedId) {
        String prefix = typedId.trim();
        List<String> names = new ArrayList<>();
        for (ParticipantRow row : participants(eventId)) {
            if (row.isStillHere() && row.studentId().startsWith(prefix) && !names.contains(row.name())) {
                names.add(row.name());
            }
        }
        names.sort(String::compareToIgnoreCase);
        return names.size() > MAX_CANDIDATES ? names.subList(0, MAX_CANDIDATES) : names;
    }

    /** People still at the event with exactly this name (ignoring upper/lower case). */
    public List<Attendance> stillHereWithName(String eventId, String name) {
        List<Attendance> matches = new ArrayList<>();
        for (Attendance a : attendanceRepository.findByEventId(eventId)) {
            Optional<User> user = userRepository.findById(a.getStudentId());
            if (!a.isCheckedOut() && user.isPresent() && user.get().getName().equalsIgnoreCase(name.trim())) {
                matches.add(a);
            }
        }
        return matches;
    }

    // ---------- Changes by the group (owner) ----------

    /** Finds one attendance row, or "404 Not Found" if it does not belong to this event. */
    public Attendance getAttendanceInEvent(Long attendanceId, String eventId) {
        Attendance attendance = attendanceRepository.findById(attendanceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Attendance not found"));
        if (!attendance.getEventId().equals(eventId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Attendance not found");
        }
        return attendance;
    }

    /**
     * Gives everyone who has not checked out the same check-out time.
     * People who checked in after that time are skipped (their stay would be negative).
     * @Transactional: all rows are saved together, or none if something fails.
     */
    @Transactional
    public BulkCheckoutResult checkOutEveryone(String eventId, LocalDateTime time) {
        int checkedOut = 0;
        int skipped = 0;
        for (Attendance a : attendanceRepository.findByEventId(eventId)) {
            if (a.isCheckedOut()) {
                continue;
            }
            if (time.isBefore(a.getCheckInTime())) {
                skipped++;
            } else {
                a.setCheckOutByOwner(time);
                attendanceRepository.save(a);
                checkedOut++;
            }
        }
        return new BulkCheckoutResult(checkedOut, skipped);
    }

    /**
     * Fixes one row's times. checkOut may be null ("still here").
     * Returns an error message, or null if the change was saved.
     */
    public String updateTimes(Attendance attendance, LocalDateTime checkIn, LocalDateTime checkOut) {
        if (checkIn == null) {
            return "Check-in time is required.";
        }
        if (checkOut != null && checkOut.isBefore(checkIn)) {
            return "Check-out cannot be before check-in.";
        }
        attendance.setCheckInTime(checkIn);
        // Only a changed check-out counts as "entered by the owner" (a check-in fix keeps QR)
        boolean checkOutChanged = (checkOut == null)
                ? attendance.getCheckOutTime() != null
                : !checkOut.equals(attendance.getCheckOutTime());
        if (checkOutChanged) {
            attendance.setCheckOutByOwner(checkOut);
        }
        attendanceRepository.save(attendance);
        return null;
    }

    /** Current time in Pacific time, without seconds. Used as the default in forms. */
    public LocalDateTime currentTime() {
        return now();
    }

    // ---------- Guest registration ----------

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
            String role = user.map(User::displayRole).orElse("");
            rows.add(new ParticipantRow(a.getId(), a.getStudentId(), name, email, role,
                    a.getCheckInTime(), a.getCheckOutTime(), a.stayMinutes(), a.checkoutSourceLabel()));
        }
        rows.sort(Comparator.comparing(ParticipantRow::checkInTime));
        return rows;
    }

    /** For the Live page: people still here first (by check-in), then people who left (latest first). */
    public List<ParticipantRow> liveOrder(List<ParticipantRow> rows) {
        List<ParticipantRow> here = new ArrayList<>();
        List<ParticipantRow> left = new ArrayList<>();
        for (ParticipantRow row : rows) {
            if (row.isStillHere()) {
                here.add(row);
            } else {
                left.add(row);
            }
        }
        left.sort(Comparator.comparing(ParticipantRow::checkOutTime).reversed());
        here.addAll(left);
        return here;
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
