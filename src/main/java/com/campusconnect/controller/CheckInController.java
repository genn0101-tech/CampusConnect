package com.campusconnect.controller;

import com.campusconnect.model.Attendance;
import com.campusconnect.model.Event;
import com.campusconnect.model.User;
import com.campusconnect.service.AttendanceService;
import com.campusconnect.service.EventService;
import com.campusconnect.service.GroupService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Optional;

/** The pages students open on their phone after scanning the QR code. */
@Controller
public class CheckInController {

    private static final String STUDENT_ID_PATTERN = User.STUDENT_ID_PATTERN;
    // Suggestions start after this many typed characters (name) or digits (Student ID)
    private static final int MIN_NAME_QUERY = 2;
    private static final int MIN_ID_QUERY = 4;
    // Session key: the check-in this browser just made, which "Not you?" may undo
    private static final String UNDO_KEY = "undoableCheckIn";

    private final EventService eventService;
    private final AttendanceService attendanceService;
    private final GroupService groupService;

    public CheckInController(EventService eventService, AttendanceService attendanceService,
                             GroupService groupService) {
        this.eventService = eventService;
        this.attendanceService = attendanceService;
        this.groupService = groupService;
    }

    /** Puts the event and its group on the page (the group name is shown in the privacy note). */
    private Event addEvent(String eventId, Model model) {
        Event event = eventService.getEvent(eventId);
        model.addAttribute("event", event);
        model.addAttribute("group", groupService.getGroup(event.getGroupId()));
        return event;
    }

    // ---------- Check-in ----------

    @GetMapping("/events/{eventId}/checkin")
    public String checkInForm(@PathVariable String eventId, Model model) {
        addEvent(eventId, model);
        return "checkin";
    }

    @PostMapping("/events/{eventId}/checkin")
    public String checkIn(@PathVariable String eventId, @RequestParam String studentId,
                          HttpSession session, Model model) {
        Event event = addEvent(eventId, model);
        studentId = studentId.trim();

        if (!studentId.matches(STUDENT_ID_PATTERN)) {
            model.addAttribute("error", "Please enter a valid Student ID (letters and numbers only).");
            return "checkin";
        }

        Optional<User> user = attendanceService.findUser(studentId);
        if (user.isEmpty()) {
            // First time: ask for name and email, then register as a Guest
            model.addAttribute("studentId", studentId);
            return "checkin-guest";
        }
        // A known Student ID: the result page asks "Not you?" in case of a typo
        return completeCheckIn(event, user.get(), true, session, model);
    }

    @PostMapping("/events/{eventId}/checkin/guest")
    public String registerGuest(@PathVariable String eventId,
                                @RequestParam String studentId,
                                @RequestParam String name,
                                @RequestParam String email,
                                HttpSession session, Model model) {
        Event event = addEvent(eventId, model);
        studentId = studentId.trim();
        name = name.trim();
        email = email.trim();

        if (!studentId.matches(STUDENT_ID_PATTERN)) {
            model.addAttribute("error", "Please enter a valid Student ID (letters and numbers only).");
            return "checkin";
        }
        if (name.isEmpty() || !email.contains("@")) {
            model.addAttribute("error", "Please enter your name and a valid email.");
            model.addAttribute("studentId", studentId);
            model.addAttribute("name", name);
            model.addAttribute("email", email);
            return "checkin-guest";
        }

        // If the ID was registered in the meantime, use the existing user instead of overwriting it
        User user = attendanceService.findUser(studentId).orElse(null);
        if (user == null) {
            user = attendanceService.registerGuest(studentId, name, email, event.getGroupId());
        }
        return completeCheckIn(event, user, false, session, model);
    }

    /**
     * Shared last step of both check-in paths.
     * knownId = the Student ID was already registered, so "Not you?" is shown.
     */
    private String completeCheckIn(Event event, User user, boolean knownId, HttpSession session, Model model) {
        Optional<Attendance> existing = attendanceService.findAttendance(event.getEventId(), user.getStudentId());
        model.addAttribute("user", user);
        if (existing.isPresent()) {
            model.addAttribute("success", false);
            model.addAttribute("message", "You already checked in.");
            model.addAttribute("attendance", existing.get());
        } else {
            Attendance attendance = attendanceService.checkIn(event.getEventId(), user.getStudentId());
            model.addAttribute("success", true);
            model.addAttribute("message", "Checked in.");
            model.addAttribute("attendance", attendance);
            if (knownId) {
                // Remember it so "Not you?" can undo exactly this one check-in, and nobody else's
                session.setAttribute(UNDO_KEY, attendance.getId());
                model.addAttribute("canUndo", true);
            }
        }
        model.addAttribute("knownId", knownId);
        return "result";
    }

    /** "Not you?": undo the check-in this browser just made, then ask for the Student ID again. */
    @PostMapping("/events/{eventId}/checkin/not-me")
    public String notMe(@PathVariable String eventId, HttpSession session, RedirectAttributes redirect) {
        eventService.getEvent(eventId); // 404 if the event does not exist
        Object undoable = session.getAttribute(UNDO_KEY);
        if (undoable instanceof Long attendanceId) {
            attendanceService.undoCheckIn(attendanceId, eventId);
            session.removeAttribute(UNDO_KEY);
        }
        redirect.addFlashAttribute("error", "Please enter your Student ID again.");
        return "redirect:/events/" + eventId + "/checkin";
    }

    // ---------- Check-out ----------

    @GetMapping("/events/{eventId}/checkout")
    public String checkOutForm(@PathVariable String eventId, Model model) {
        addCheckoutPage(eventId, model);
        return "checkout";
    }

    /**
     * Like addEvent, plus the automatic check-out: after the end time everyone is checked out,
     * so an ended event suggests nobody (this replaced the old "event day only" rule).
     */
    private Event addCheckoutPage(String eventId, Model model) {
        Event event = addEvent(eventId, model);
        attendanceService.autoCheckOutIfOver(event); // the event may have ended while the server slept
        return event;
    }

    /**
     * Suggestions for the checkout page: a JSON list of names, nothing else.
     * q = part of a name (2+ letters), id = start of a Student ID (4+ characters).
     * Only people still at the event (not checked out), at most 5.
     */
    @GetMapping("/events/{eventId}/checkout/candidates")
    @ResponseBody
    public List<String> checkoutCandidates(@PathVariable String eventId,
                                           @RequestParam(defaultValue = "") String q,
                                           @RequestParam(defaultValue = "") String id) {
        Event event = eventService.getEvent(eventId);
        attendanceService.autoCheckOutIfOver(event); // people of an ended event are not suggested
        if (!id.isBlank()) {
            String typedId = id.trim();
            if (typedId.length() < MIN_ID_QUERY || !typedId.matches(STUDENT_ID_PATTERN)) {
                return List.of();
            }
            return attendanceService.checkoutCandidatesById(eventId, typedId);
        }
        if (q.trim().length() < MIN_NAME_QUERY) {
            return List.of();
        }
        return attendanceService.checkoutCandidates(eventId, q);
    }

    @PostMapping("/events/{eventId}/checkout/name")
    public String checkOutByName(@PathVariable String eventId,
                                 @RequestParam String name,
                                 @RequestParam(required = false) String studentId,
                                 @RequestParam(defaultValue = "") String idPrefix,
                                 Model model) {
        addCheckoutPage(eventId, model);

        List<Attendance> matches = attendanceService.stillHereWithName(eventId, name);
        // Picked from the Student ID suggestions: keep only people whose ID starts with the typed digits,
        // so two people with the same name are told apart without asking again
        if (!idPrefix.isBlank()) {
            matches.removeIf(match -> !match.getStudentId().startsWith(idPrefix.trim()));
        }
        if (matches.isEmpty()) {
            model.addAttribute("error", "Nobody with this name is checked in. Please pick a name from the list or use your Student ID.");
            return "checkout";
        }
        if (matches.size() > 1) {
            // Same name twice: the Student ID decides who is checking out
            for (Attendance match : matches) {
                if (studentId != null && match.getStudentId().equals(studentId.trim())) {
                    return finishCheckOut(match, model);
                }
            }
            model.addAttribute("sameName", name.trim());
            if (studentId != null) {
                model.addAttribute("error", "That Student ID does not match anyone with this name.");
            }
            return "checkout";
        }
        return finishCheckOut(matches.get(0), model);
    }

    @PostMapping("/events/{eventId}/checkout")
    public String checkOut(@PathVariable String eventId, @RequestParam String studentId, Model model) {
        addCheckoutPage(eventId, model);
        studentId = studentId.trim();

        Optional<Attendance> attendance = attendanceService.findAttendance(eventId, studentId);
        if (attendance.isEmpty()) {
            model.addAttribute("error", "No check-in found for this Student ID at this event.");
            return "checkout";
        }
        return finishCheckOut(attendance.get(), model);
    }

    /** Shared last step of both check-out paths (Student ID and name). */
    private String finishCheckOut(Attendance attendance, Model model) {
        model.addAttribute("user", attendanceService.findUser(attendance.getStudentId()).orElse(null));
        if (attendance.isCheckedOut()) {
            model.addAttribute("success", false);
            model.addAttribute("message", "You already checked out.");
            model.addAttribute("attendance", attendance);
        } else {
            model.addAttribute("success", true);
            model.addAttribute("message", "Checked out. Thank you for coming!");
            model.addAttribute("attendance", attendanceService.checkOut(attendance));
        }
        return "result";
    }
}
