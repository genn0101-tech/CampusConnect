package com.campusconnect.controller;

import com.campusconnect.model.Folder;
import com.campusconnect.model.Group;
import com.campusconnect.service.FolderService;
import com.campusconnect.service.GroupAccess;
import com.campusconnect.service.GroupService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Folders of a group's events: create, view, archive, restore, delete, move an event (unlocked only). */
@Controller
public class FolderController {

    private final GroupService groupService;
    private final FolderService folderService;
    private final GroupAccess groupAccess;

    public FolderController(GroupService groupService, FolderService folderService, GroupAccess groupAccess) {
        this.groupService = groupService;
        this.folderService = folderService;
        this.groupAccess = groupAccess;
    }

    // ---------- Create ----------

    @GetMapping("/g/{groupId}/folders/new")
    public String newFolderForm(@PathVariable String groupId, HttpSession session, Model model) {
        Group group = groupService.getGroup(groupId);
        if (!groupAccess.isUnlocked(session, groupId)) {
            return "redirect:/g/" + groupId;
        }
        model.addAttribute("group", group);
        return "folder-new";
    }

    @PostMapping("/g/{groupId}/folders")
    public String createFolder(@PathVariable String groupId, @RequestParam String name,
                               HttpSession session, Model model, RedirectAttributes redirect) {
        Group group = groupService.getGroup(groupId);
        if (!groupAccess.isUnlocked(session, groupId)) {
            return "redirect:/g/" + groupId;
        }
        name = name.trim();
        String error = folderService.validateName(groupId, name);
        if (error != null) {
            model.addAttribute("group", group);
            model.addAttribute("error", error);
            model.addAttribute("name", name);
            return "folder-new";
        }
        folderService.createFolder(groupId, name);
        redirect.addFlashAttribute("message", "Created folder " + name + ".");
        return "redirect:/g/" + groupId;
    }

    // ---------- One folder ----------

    @GetMapping("/g/{groupId}/folders/{folderId}")
    public String folder(@PathVariable String groupId, @PathVariable Long folderId,
                         HttpSession session, Model model) {
        Group group = groupService.getGroup(groupId);
        if (!groupAccess.isUnlocked(session, groupId)) {
            return "redirect:/g/" + groupId;
        }
        Folder folder = folderService.getFolderInGroup(folderId, groupId);
        model.addAttribute("group", group);
        model.addAttribute("folder", folder);
        model.addAttribute("section", folderService.sectionOf(folder));
        return "folder";
    }

    @PostMapping("/g/{groupId}/folders/{folderId}/archive")
    public String archive(@PathVariable String groupId, @PathVariable Long folderId,
                          HttpSession session, RedirectAttributes redirect) {
        return setArchived(groupId, folderId, true, session, redirect);
    }

    @PostMapping("/g/{groupId}/folders/{folderId}/restore")
    public String restore(@PathVariable String groupId, @PathVariable Long folderId,
                          HttpSession session, RedirectAttributes redirect) {
        return setArchived(groupId, folderId, false, session, redirect);
    }

    private String setArchived(String groupId, Long folderId, boolean archived,
                               HttpSession session, RedirectAttributes redirect) {
        groupService.getGroup(groupId);
        if (!groupAccess.isUnlocked(session, groupId)) {
            return "redirect:/g/" + groupId;
        }
        folderService.setArchived(folderId, groupId, archived);
        redirect.addFlashAttribute("message", archived
                ? "Folder archived. Its events are hidden from the lists but still count in attendance rates."
                : "Folder restored.");
        return "redirect:/g/" + groupId + "/folders/" + folderId;
    }

    /** "Are you sure?" page. Only for archived folders. */
    @GetMapping("/g/{groupId}/folders/{folderId}/delete")
    public String deleteForm(@PathVariable String groupId, @PathVariable Long folderId,
                             HttpSession session, Model model) {
        Group group = groupService.getGroup(groupId);
        if (!groupAccess.isUnlocked(session, groupId)) {
            return "redirect:/g/" + groupId;
        }
        Folder folder = folderService.getFolderInGroup(folderId, groupId);
        if (!folder.isArchived()) {
            return "redirect:/g/" + groupId + "/folders/" + folderId; // archive first
        }
        model.addAttribute("group", group);
        model.addAttribute("folder", folder);
        model.addAttribute("eventCount", folderService.eventCount(folderId));
        return "folder-delete";
    }

    @PostMapping("/g/{groupId}/folders/{folderId}/delete")
    public String delete(@PathVariable String groupId, @PathVariable Long folderId,
                         HttpSession session, RedirectAttributes redirect) {
        groupService.getGroup(groupId);
        if (!groupAccess.isUnlocked(session, groupId)) {
            return "redirect:/g/" + groupId;
        }
        String name = folderService.getFolderInGroup(folderId, groupId).getName();
        folderService.deleteArchivedFolder(folderId, groupId); // refuses folders that are not archived
        redirect.addFlashAttribute("message", "Deleted folder " + name + ". Its events are now without a folder.");
        return "redirect:/g/" + groupId;
    }

    // ---------- Move an event ----------

    /** folderId empty = take the event out of its folder. */
    @PostMapping("/g/{groupId}/events/{eventId}/folder")
    public String moveEvent(@PathVariable String groupId, @PathVariable String eventId,
                            @RequestParam(required = false) Long folderId,
                            HttpSession session, RedirectAttributes redirect) {
        groupService.getGroup(groupId);
        if (!groupAccess.isUnlocked(session, groupId)) {
            return "redirect:/g/" + groupId;
        }
        folderService.moveEvent(eventId, groupId, folderId);
        redirect.addFlashAttribute("message", "Folder changed.");
        return "redirect:/g/" + groupId + "/events/" + eventId;
    }
}
