package com.campusconnect.controller;

import com.campusconnect.model.Attendance;
import com.campusconnect.model.Event;
import com.campusconnect.model.Group;
import com.campusconnect.service.AttendanceService;
import com.campusconnect.service.AttendanceService.BulkCheckoutResult;
import com.campusconnect.service.EventService;
import com.campusconnect.service.GroupAccess;
import com.campusconnect.service.GroupService;
import com.campusconnect.service.ParticipantRow;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Pages the group uses during and after an event (only when the group is unlocked):
 * the Live page, "check out everyone", and fixing one person's times.
 */
@Controller
public class OwnerAttendanceController {

    private final GroupService groupService;
    private final EventService eventService;
    private final AttendanceService attendanceService;
    private final GroupAccess groupAccess;

    public OwnerAttendanceController(GroupService groupService, EventService eventService,
                                     AttendanceService attendanceService, GroupAccess groupAccess) {
        this.groupService = groupService;
        this.eventService = eventService;
        this.attendanceService = attendanceService;
        this.groupAccess = groupAccess;
    }

    // ---------- Live page ----------

    @GetMapping("/g/{groupId}/events/{eventId}/live")
    public String live(@PathVariable String groupId, @PathVariable String eventId,
                       HttpSession session, Model model) {
        if (!addGroupAndEvent(groupId, eventId, session, model)) {
            return "redirect:/g/" + groupId;
        }
        List<ParticipantRow> rows = attendanceService.participants(eventId);
        long hereNow = rows.stream().filter(ParticipantRow::isStillHere).count();
        model.addAttribute("hereNow", hereNow);
        model.addAttribute("totalCame", rows.size());
        model.addAttribute("participants", attendanceService.liveOrder(rows));
        // The page reloads itself every 30 seconds (<meta http-equiv="refresh">, no JavaScript)
        model.addAttribute("refreshSeconds", 30);
        return "event-live";
    }

    // ---------- Check out everyone ----------

    /** Confirmation page with the time (default: now). Prevents checking out everyone by one wrong tap. */
    @GetMapping("/g/{groupId}/events/{eventId}/checkout-all")
    public String checkoutAllForm(@PathVariable String groupId, @PathVariable String eventId,
                                  @RequestParam(defaultValue = "detail") String from,
                                  HttpSession session, Model model) {
        if (!addGroupAndEvent(groupId, eventId, session, model)) {
            return "redirect:/g/" + groupId;
        }
        long stillHere = attendanceService.participants(eventId).stream()
                .filter(ParticipantRow::isStillHere).count();
        model.addAttribute("stillHere", stillHere);
        model.addAttribute("time", attendanceService.currentTime());
        model.addAttribute("from", from);
        return "checkout-all";
    }

    @PostMapping("/g/{groupId}/events/{eventId}/checkout-all")
    public String checkoutAll(@PathVariable String groupId, @PathVariable String eventId,
                              @RequestParam String time, @RequestParam(defaultValue = "detail") String from,
                              HttpSession session, Model model, RedirectAttributes redirect) {
        if (!addGroupAndEvent(groupId, eventId, session, model)) {
            return "redirect:/g/" + groupId;
        }
        LocalDateTime checkOutTime;
        try {
            checkOutTime = parseTime(time);
        } catch (DateTimeParseException e) {
            checkOutTime = null;
        }
        if (checkOutTime == null) {
            redirect.addFlashAttribute("error", "Please enter a valid check-out time.");
            return "redirect:/g/" + groupId + "/events/" + eventId + "/checkout-all?from=" + safeFrom(from);
        }

        BulkCheckoutResult result = attendanceService.checkOutEveryone(eventId, checkOutTime);
        String message = "Checked out " + result.checkedOut() + " people.";
        if (result.skipped() > 0) {
            message += " Skipped " + result.skipped() + " who checked in after that time.";
        }
        redirect.addFlashAttribute("message", message);
        return backTo(groupId, eventId, from);
    }

    // ---------- Fix one person's times ----------

    @GetMapping("/g/{groupId}/events/{eventId}/attendance/{attendanceId}/edit")
    public String editForm(@PathVariable String groupId, @PathVariable String eventId,
                           @PathVariable Long attendanceId, @RequestParam(defaultValue = "detail") String from,
                           HttpSession session, Model model) {
        if (!addGroupAndEvent(groupId, eventId, session, model)) {
            return "redirect:/g/" + groupId;
        }
        Attendance attendance = attendanceService.getAttendanceInEvent(attendanceId, eventId);
        model.addAttribute("attendance", attendance);
        model.addAttribute("user", attendanceService.findUser(attendance.getStudentId()).orElse(null));
        model.addAttribute("checkIn", attendance.getCheckInTime());
        model.addAttribute("checkOut", attendance.getCheckOutTime());
        model.addAttribute("from", from);
        return "attendance-edit";
    }

    @PostMapping("/g/{groupId}/events/{eventId}/attendance/{attendanceId}/edit")
    public String edit(@PathVariable String groupId, @PathVariable String eventId,
                       @PathVariable Long attendanceId,
                       @RequestParam String checkIn,
                       @RequestParam(defaultValue = "") String checkOut, // empty = still here
                       @RequestParam(defaultValue = "detail") String from,
                       HttpSession session, Model model, RedirectAttributes redirect) {
        if (!addGroupAndEvent(groupId, eventId, session, model)) {
            return "redirect:/g/" + groupId;
        }
        Attendance attendance = attendanceService.getAttendanceInEvent(attendanceId, eventId);

        String error;
        try {
            error = attendanceService.updateTimes(attendance, parseTime(checkIn), parseTime(checkOut));
        } catch (DateTimeParseException e) {
            error = "Please enter valid times.";
        }
        if (error != null) {
            // Show the form again with the error
            model.addAttribute("error", error);
            model.addAttribute("attendance", attendance);
            model.addAttribute("user", attendanceService.findUser(attendance.getStudentId()).orElse(null));
            model.addAttribute("checkIn", attendance.getCheckInTime());
            model.addAttribute("checkOut", attendance.getCheckOutTime());
            model.addAttribute("from", from);
            return "attendance-edit";
        }
        redirect.addFlashAttribute("message", "Times updated.");
        return backTo(groupId, eventId, from);
    }

    // ---------- Helpers ----------

    /** Adds the group and event to the page. Returns false if the group is locked. */
    private boolean addGroupAndEvent(String groupId, String eventId, HttpSession session, Model model) {
        Group group = groupService.getGroup(groupId); // 404 if the group does not exist
        if (!groupAccess.isUnlocked(session, groupId)) {
            return false;
        }
        Event event = eventService.getEventInGroup(eventId, groupId); // 404 if not this group's event
        model.addAttribute("group", group);
        model.addAttribute("event", event);
        return true;
    }

    /**
     * Reads the value of an <input type="datetime-local">, e.g. "2026-11-24T20:30".
     * Empty means "no time". Seconds are cut, like everywhere else in the app.
     */
    private LocalDateTime parseTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDateTime.parse(value.trim()).withSecond(0).withNano(0);
    }

    /** Only two pages are allowed as "go back to", so the parameter cannot send people elsewhere. */
    private String safeFrom(String from) {
        return "live".equals(from) ? "live" : "detail";
    }

    private String backTo(String groupId, String eventId, String from) {
        String eventUrl = "/g/" + groupId + "/events/" + eventId;
        return "redirect:" + ("live".equals(safeFrom(from)) ? eventUrl + "/live" : eventUrl);
    }
}
