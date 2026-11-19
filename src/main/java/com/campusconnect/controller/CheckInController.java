package com.campusconnect.controller;

import com.campusconnect.model.Attendance;
import com.campusconnect.model.Event;
import com.campusconnect.model.User;
import com.campusconnect.service.AttendanceService;
import com.campusconnect.service.EventService;
import com.campusconnect.service.GroupService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Optional;

/** The pages students open on their phone after scanning the QR code. */
@Controller
public class CheckInController {

    // StudentID: 1-20 letters or digits (kept loose on purpose so nobody is blocked at the door)
    private static final String STUDENT_ID_PATTERN = "[A-Za-z0-9]{1,20}";

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
    public String checkIn(@PathVariable String eventId, @RequestParam String studentId, Model model) {
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
        return completeCheckIn(event, user.get(), model);
    }

    @PostMapping("/events/{eventId}/checkin/guest")
    public String registerGuest(@PathVariable String eventId,
                                @RequestParam String studentId,
                                @RequestParam String name,
                                @RequestParam String email,
                                Model model) {
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
        return completeCheckIn(event, user, model);
    }

    /** Shared last step of both check-in paths. */
    private String completeCheckIn(Event event, User user, Model model) {
        Optional<Attendance> existing = attendanceService.findAttendance(event.getEventId(), user.getStudentId());
        model.addAttribute("user", user);
        if (existing.isPresent()) {
            model.addAttribute("success", false);
            model.addAttribute("message", "You already checked in.");
            model.addAttribute("attendance", existing.get());
        } else {
            Attendance attendance = attendanceService.checkIn(event.getEventId(), user.getStudentId());
            model.addAttribute("success", true);
            model.addAttribute("message", "Checked in. Welcome, " + user.getName() + "!");
            model.addAttribute("attendance", attendance);
        }
        return "result";
    }

    // ---------- Check-out ----------

    @GetMapping("/events/{eventId}/checkout")
    public String checkOutForm(@PathVariable String eventId, Model model) {
        addEvent(eventId, model);
        return "checkout";
    }

    @PostMapping("/events/{eventId}/checkout")
    public String checkOut(@PathVariable String eventId, @RequestParam String studentId, Model model) {
        Event event = addEvent(eventId, model);
        studentId = studentId.trim();

        Optional<Attendance> attendance = attendanceService.findAttendance(eventId, studentId);
        if (attendance.isEmpty()) {
            model.addAttribute("error", "No check-in found for this Student ID at this event.");
            return "checkout";
        }

        model.addAttribute("user", attendanceService.findUser(studentId).orElse(null));
        if (attendance.get().isCheckedOut()) {
            model.addAttribute("success", false);
            model.addAttribute("message", "You already checked out.");
            model.addAttribute("attendance", attendance.get());
        } else {
            model.addAttribute("success", true);
            model.addAttribute("message", "Checked out. Thank you for coming!");
            model.addAttribute("attendance", attendanceService.checkOut(attendance.get()));
        }
        return "result";
    }
}
