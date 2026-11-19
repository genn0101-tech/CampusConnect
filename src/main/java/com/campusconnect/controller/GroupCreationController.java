package com.campusconnect.controller;

import com.campusconnect.model.Group;
import com.campusconnect.service.GroupAccess;
import com.campusconnect.service.GroupPassphrase;
import com.campusconnect.service.GroupService;
import com.campusconnect.service.SiteSecrets;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.List;

/** Creating a new group. Only people who know GROUP_CREATION_CODE can do this. */
@Controller
public class GroupCreationController {

    private static final int MAX_NAME_LENGTH = 60;
    private static final int MAX_DESCRIPTION_LENGTH = 200;

    private final GroupService groupService;
    private final GroupPassphrase groupPassphrase;
    private final GroupAccess groupAccess;
    private final SiteSecrets siteSecrets;

    public GroupCreationController(GroupService groupService, GroupPassphrase groupPassphrase,
                                   GroupAccess groupAccess, SiteSecrets siteSecrets) {
        this.groupService = groupService;
        this.groupPassphrase = groupPassphrase;
        this.groupAccess = groupAccess;
        this.siteSecrets = siteSecrets;
    }

    @GetMapping("/groups/new")
    public String newGroupForm() {
        return "group-new";
    }

    @PostMapping("/groups")
    public String createGroup(@RequestParam String creationCode,
                              @RequestParam String groupName,
                              @RequestParam(defaultValue = "") String description,
                              @RequestParam String passphrase,
                              @RequestParam String passphraseConfirm,
                              HttpServletRequest request, Model model) {
        groupName = groupName.trim();
        description = description.trim();

        List<String> errors = new ArrayList<>();
        if (!siteSecrets.isGroupCreationCode(creationCode)) {
            errors.add("Wrong group creation code.");
        }
        if (groupName.isEmpty() || groupName.length() > MAX_NAME_LENGTH) {
            errors.add("Group name is required (up to " + MAX_NAME_LENGTH + " characters).");
        }
        if (description.length() > MAX_DESCRIPTION_LENGTH) {
            errors.add("Description is too long (up to " + MAX_DESCRIPTION_LENGTH + " characters).");
        }
        String passphraseError = groupPassphrase.validate(passphrase);
        if (passphraseError != null) {
            errors.add(passphraseError);
        } else if (!passphrase.equals(passphraseConfirm)) {
            errors.add("The two passphrases do not match.");
        }

        if (!errors.isEmpty()) {
            // Show the form again. Secrets (code, passphrase) are not sent back to the page.
            model.addAttribute("errors", errors);
            model.addAttribute("groupName", groupName);
            model.addAttribute("description", description);
            return "group-new";
        }

        Group group = groupService.createGroup(groupName, description, passphrase);
        // The creator already knows the passphrase, so open the group for them right away
        groupAccess.unlock(request, group.getGroupId());
        return "redirect:/g/" + group.getGroupId();
    }
}
