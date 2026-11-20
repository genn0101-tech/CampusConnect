package com.campusconnect.controller;

import com.campusconnect.model.Event;
import com.campusconnect.model.Group;
import com.campusconnect.service.EventService;
import com.campusconnect.service.GroupAccess;
import com.campusconnect.service.GroupService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Archive, restore, and delete events (only when the group is unlocked). */
@Controller
public class EventArchiveController {

    private final GroupService groupService;
    private final EventService eventService;
    private final GroupAccess groupAccess;

    public EventArchiveController(GroupService groupService, EventService eventService, GroupAccess groupAccess) {
        this.groupService = groupService;
        this.eventService = eventService;
        this.groupAccess = groupAccess;
    }

    @PostMapping("/g/{groupId}/events/{eventId}/archive")
    public String archive(@PathVariable String groupId, @PathVariable String eventId,
                          HttpSession session, RedirectAttributes redirect) {
        return setArchived(groupId, eventId, true, session, redirect);
    }

    @PostMapping("/g/{groupId}/events/{eventId}/restore")
    public String restore(@PathVariable String groupId, @PathVariable String eventId,
                          HttpSession session, RedirectAttributes redirect) {
        return setArchived(groupId, eventId, false, session, redirect);
    }

    private String setArchived(String groupId, String eventId, boolean archived,
                               HttpSession session, RedirectAttributes redirect) {
        groupService.getGroup(groupId); // 404 if the group does not exist
        if (!groupAccess.isUnlocked(session, groupId)) {
            return "redirect:/g/" + groupId;
        }
        eventService.setArchived(eventId, groupId, archived);
        redirect.addFlashAttribute("message", archived
                ? "Archived. It is hidden from the event lists but still counts in attendance rates."
                : "Restored to the event lists.");
        return "redirect:/g/" + groupId + "/events/" + eventId;
    }

    /** "Are you sure?" page. Only for archived events. */
    @GetMapping("/g/{groupId}/events/{eventId}/delete")
    public String deleteForm(@PathVariable String groupId, @PathVariable String eventId,
                             HttpSession session, Model model) {
        Group group = groupService.getGroup(groupId);
        if (!groupAccess.isUnlocked(session, groupId)) {
            return "redirect:/g/" + groupId;
        }
        Event event = eventService.getEventInGroup(eventId, groupId);
        if (!event.isArchived()) {
            return "redirect:/g/" + groupId + "/events/" + eventId; // archive first
        }
        model.addAttribute("group", group);
        model.addAttribute("event", event);
        model.addAttribute("attendanceCount", eventService.attendanceCount(eventId));
        return "event-delete";
    }

    @PostMapping("/g/{groupId}/events/{eventId}/delete")
    public String delete(@PathVariable String groupId, @PathVariable String eventId,
                         HttpSession session, RedirectAttributes redirect) {
        groupService.getGroup(groupId);
        if (!groupAccess.isUnlocked(session, groupId)) {
            return "redirect:/g/" + groupId;
        }
        String name = eventService.getEventInGroup(eventId, groupId).getEventName();
        eventService.deleteArchivedEvent(eventId, groupId); // refuses events that are not archived
        redirect.addFlashAttribute("message", "Deleted " + name + " and its attendance records.");
        return "redirect:/g/" + groupId;
    }
}
