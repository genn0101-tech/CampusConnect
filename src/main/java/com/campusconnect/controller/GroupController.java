package com.campusconnect.controller;

import com.campusconnect.model.Event;
import com.campusconnect.model.Group;
import com.campusconnect.service.AttendanceService;
import com.campusconnect.service.EventService;
import com.campusconnect.service.GroupAccess;
import com.campusconnect.service.GroupPassphrase;
import com.campusconnect.service.GroupService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * A group's own pages under /g/{groupId}: unlock with the group passphrase,
 * then see and create only this group's events, and see this group's attendance rate.
 */
@Controller
public class GroupController {

    private final GroupService groupService;
    private final EventService eventService;
    private final AttendanceService attendanceService;
    private final GroupPassphrase groupPassphrase;
    private final GroupAccess groupAccess;

    public GroupController(GroupService groupService, EventService eventService,
                           AttendanceService attendanceService, GroupPassphrase groupPassphrase,
                           GroupAccess groupAccess) {
        this.groupService = groupService;
        this.eventService = eventService;
        this.attendanceService = attendanceService;
        this.groupPassphrase = groupPassphrase;
        this.groupAccess = groupAccess;
    }

    // ---------- Group home: passphrase form or event list ----------

    @GetMapping("/g/{groupId}")
    public String groupHome(@PathVariable String groupId, HttpSession session, Model model) {
        Group group = groupService.getGroup(groupId);
        model.addAttribute("group", group);
        if (!groupAccess.isUnlocked(session, groupId)) {
            return "group-locked";
        }
        model.addAttribute("upcoming", eventService.upcomingEvents(groupId));
        model.addAttribute("past", eventService.pastEvents(groupId));
        model.addAttribute("archived", eventService.archivedEvents(groupId));
        return "group";
    }

    @PostMapping("/g/{groupId}/unlock")
    public String unlock(@PathVariable String groupId, @RequestParam String passphrase,
                         HttpServletRequest request, Model model) {
        Group group = groupService.getGroup(groupId);
        if (!groupPassphrase.matches(group, passphrase)) {
            model.addAttribute("group", group);
            model.addAttribute("error", "Wrong passphrase.");
            return "group-locked";
        }
        groupAccess.unlock(request, groupId);
        return "redirect:/g/" + groupId;
    }

    @PostMapping("/g/{groupId}/lock")
    public String lock(@PathVariable String groupId, HttpSession session) {
        groupAccess.lock(session, groupId);
        return "redirect:/g/" + groupId;
    }

    // ---------- Event creation (unlocked groups only) ----------

    @GetMapping("/g/{groupId}/events/new")
    public String newEventForm(@PathVariable String groupId, HttpSession session, Model model) {
        Group group = groupService.getGroup(groupId);
        if (!groupAccess.isUnlocked(session, groupId)) {
            return "redirect:/g/" + groupId;
        }
        model.addAttribute("group", group);
        return "event-new";
    }

    @PostMapping("/g/{groupId}/events")
    public String createEvent(@PathVariable String groupId,
                              @RequestParam String eventName,
                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate eventDate,
                              @RequestParam String location,
                              HttpSession session, Model model) {
        Group group = groupService.getGroup(groupId);
        if (!groupAccess.isUnlocked(session, groupId)) {
            return "redirect:/g/" + groupId;
        }

        List<String> errors = new ArrayList<>();
        if (eventName.isBlank()) {
            errors.add("Event name is required.");
        }
        if (eventDate == null) {
            errors.add("Date is required.");
        }
        if (!errors.isEmpty()) {
            // Show the form again with the values already typed
            model.addAttribute("group", group);
            model.addAttribute("errors", errors);
            model.addAttribute("eventName", eventName);
            model.addAttribute("eventDate", eventDate);
            model.addAttribute("location", location);
            return "event-new";
        }

        // The group comes from the URL, so a group can only create events for itself
        Event event = eventService.createEvent(eventName.trim(), eventDate, location.trim(), groupId);
        // Redirect after POST so refreshing the page does not create the event twice
        return "redirect:/g/" + groupId + "/events/" + event.getEventId();
    }

    // ---------- Attendance rate (unlocked groups only) ----------

    @GetMapping("/g/{groupId}/attendance-rate")
    public String attendanceRate(@PathVariable String groupId, HttpSession session, Model model) {
        Group group = groupService.getGroup(groupId);
        if (!groupAccess.isUnlocked(session, groupId)) {
            return "redirect:/g/" + groupId;
        }
        model.addAttribute("group", group);
        model.addAttribute("rates", attendanceService.attendanceRates(groupId));
        return "attendance-rate";
    }
}
