package com.campusconnect.controller;

import com.campusconnect.model.Event;
import com.campusconnect.model.Group;
import com.campusconnect.service.EventService;
import com.campusconnect.service.GroupAccess;
import com.campusconnect.service.GroupService;
import com.campusconnect.service.TimeFormat;
import jakarta.servlet.http.HttpSession;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/** Owner fixes an event: name, date, location, and meeting time (e.g. extend the end). */
@Controller
public class EventEditController {

    private final GroupService groupService;
    private final EventService eventService;
    private final GroupAccess groupAccess;
    private final TimeFormat timeFormat;

    public EventEditController(GroupService groupService, EventService eventService,
                               GroupAccess groupAccess, TimeFormat timeFormat) {
        this.groupService = groupService;
        this.eventService = eventService;
        this.groupAccess = groupAccess;
        this.timeFormat = timeFormat;
    }

    @GetMapping("/g/{groupId}/events/{eventId}/edit")
    public String editForm(@PathVariable String groupId, @PathVariable String eventId,
                           HttpSession session, Model model) {
        Group group = groupService.getGroup(groupId);
        if (!groupAccess.isUnlocked(session, groupId)) {
            return "redirect:/g/" + groupId;
        }
        Event event = eventService.getEventInGroup(eventId, groupId);
        model.addAttribute("group", group);
        model.addAttribute("event", event);
        model.addAttribute("eventName", event.getEventName());
        model.addAttribute("eventDate", event.getEventDate());
        model.addAttribute("location", event.getLocation());
        model.addAttribute("startTime", timeFormat.time(event.getStartAt()));
        model.addAttribute("endTime", timeFormat.time(event.getEndAt()));
        return "event-edit";
    }

    @PostMapping("/g/{groupId}/events/{eventId}/edit")
    public String edit(@PathVariable String groupId, @PathVariable String eventId,
                       @RequestParam String eventName,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate eventDate,
                       @RequestParam(defaultValue = "") String location,
                       @RequestParam(defaultValue = "") String startTime,
                       @RequestParam(defaultValue = "") String endTime,
                       HttpSession session, Model model, RedirectAttributes redirect) {
        Group group = groupService.getGroup(groupId);
        if (!groupAccess.isUnlocked(session, groupId)) {
            return "redirect:/g/" + groupId;
        }
        Event event = eventService.getEventInGroup(eventId, groupId);

        String error = null;
        EventService.MeetingTime time = null;
        if (eventName.isBlank()) {
            error = "Event name is required.";
        } else if (eventDate == null) {
            error = "Date is required.";
        } else if (startTime.isBlank() && endTime.isBlank()) {
            time = null; // no meeting time: no automatic check-out (like old events)
        } else {
            try {
                time = EventService.meetingTime(eventDate,
                        timeFormat.parseTimeOfDay(startTime), timeFormat.parseTimeOfDay(endTime));
            } catch (DateTimeParseException e) {
                error = "Please enter both the start and end time like 7:00 PM.";
            }
        }
        if (error != null) {
            model.addAttribute("group", group);
            model.addAttribute("event", event);
            model.addAttribute("error", error);
            model.addAttribute("eventName", eventName);
            model.addAttribute("eventDate", eventDate);
            model.addAttribute("location", location);
            model.addAttribute("startTime", startTime);
            model.addAttribute("endTime", endTime);
            return "event-edit";
        }

        eventService.updateEvent(eventId, groupId, eventName.trim(), eventDate, location.trim(), time);
        redirect.addFlashAttribute("message", "Event updated.");
        return "redirect:/g/" + groupId + "/events/" + eventId;
    }
}
