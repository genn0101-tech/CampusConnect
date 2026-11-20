package com.campusconnect.controller;

import com.campusconnect.model.Group;
import com.campusconnect.model.User;
import com.campusconnect.service.EventService;
import com.campusconnect.service.GroupAccess;
import com.campusconnect.service.GroupService;
import com.campusconnect.service.PersonService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** The group's People page and fixing one person (only when the group is unlocked). */
@Controller
public class PeopleController {

    private final GroupService groupService;
    private final EventService eventService;
    private final PersonService personService;
    private final GroupAccess groupAccess;

    public PeopleController(GroupService groupService, EventService eventService,
                            PersonService personService, GroupAccess groupAccess) {
        this.groupService = groupService;
        this.eventService = eventService;
        this.personService = personService;
        this.groupAccess = groupAccess;
    }

    @GetMapping("/g/{groupId}/people")
    public String people(@PathVariable String groupId, HttpSession session, Model model) {
        Group group = groupService.getGroup(groupId);
        if (!groupAccess.isUnlocked(session, groupId)) {
            return "redirect:/g/" + groupId;
        }
        model.addAttribute("group", group);
        model.addAttribute("people", personService.peopleOfGroup(groupId));
        return "people";
    }

    /** from = where to go back: "people" (default), or "detail" / "live" of eventId. */
    @GetMapping("/g/{groupId}/people/{studentId}/edit")
    public String editForm(@PathVariable String groupId, @PathVariable String studentId,
                           @RequestParam(defaultValue = "people") String from,
                           @RequestParam(defaultValue = "") String eventId,
                           HttpSession session, Model model) {
        Group group = groupService.getGroup(groupId);
        if (!groupAccess.isUnlocked(session, groupId)) {
            return "redirect:/g/" + groupId;
        }
        User person = personService.getPersonOfGroup(groupId, studentId); // 404 if not this group's person
        model.addAttribute("group", group);
        model.addAttribute("person", person);
        model.addAttribute("name", person.getName());
        model.addAttribute("email", person.getEmail());
        model.addAttribute("newStudentId", person.getStudentId());
        model.addAttribute("from", from);
        model.addAttribute("eventId", eventId);
        model.addAttribute("backUrl", backUrl(groupId, from, eventId));
        return "person-edit";
    }

    @PostMapping("/g/{groupId}/people/{studentId}/edit")
    public String edit(@PathVariable String groupId, @PathVariable String studentId,
                       @RequestParam String name, @RequestParam String email,
                       @RequestParam String newStudentId,
                       @RequestParam(defaultValue = "people") String from,
                       @RequestParam(defaultValue = "") String eventId,
                       HttpSession session, Model model, RedirectAttributes redirect) {
        Group group = groupService.getGroup(groupId);
        if (!groupAccess.isUnlocked(session, groupId)) {
            return "redirect:/g/" + groupId;
        }
        String error = personService.updatePerson(groupId, studentId, name, email, newStudentId);
        if (error != null) {
            // Show the form again with what was typed
            model.addAttribute("group", group);
            model.addAttribute("person", personService.getPersonOfGroup(groupId, studentId));
            model.addAttribute("error", error);
            model.addAttribute("name", name);
            model.addAttribute("email", email);
            model.addAttribute("newStudentId", newStudentId);
            model.addAttribute("from", from);
            model.addAttribute("eventId", eventId);
            model.addAttribute("backUrl", backUrl(groupId, from, eventId));
            return "person-edit";
        }
        redirect.addFlashAttribute("message", "Saved " + name.trim() + ".");
        return "redirect:" + backUrl(groupId, from, eventId);
    }

    /**
     * The page to return to. Only this group's own pages are allowed,
     * so the "from" and "eventId" parameters cannot send people anywhere else.
     */
    private String backUrl(String groupId, String from, String eventId) {
        if (("detail".equals(from) || "live".equals(from)) && !eventId.isBlank()) {
            eventService.getEventInGroup(eventId, groupId); // 404 if the event is not this group's
            String eventUrl = "/g/" + groupId + "/events/" + eventId;
            return "live".equals(from) ? eventUrl + "/live" : eventUrl;
        }
        return "/g/" + groupId + "/people";
    }
}
