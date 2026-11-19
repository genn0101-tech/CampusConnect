package com.campusconnect.controller;

import com.campusconnect.model.Group;
import com.campusconnect.service.GroupPassphrase;
import com.campusconnect.service.GroupService;
import com.campusconnect.service.SiteSecrets;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Developer page: see every group and reset a forgotten group passphrase.
 * Opened with ADMIN_PASSPHRASE. "Logged in" is remembered in the HTTP session.
 */
@Controller
public class AdminController {

    private static final String SESSION_KEY = "isAdmin";

    private final GroupService groupService;
    private final GroupPassphrase groupPassphrase;
    private final SiteSecrets siteSecrets;

    public AdminController(GroupService groupService, GroupPassphrase groupPassphrase, SiteSecrets siteSecrets) {
        this.groupService = groupService;
        this.groupPassphrase = groupPassphrase;
        this.siteSecrets = siteSecrets;
    }

    @GetMapping("/admin")
    public String adminHome(HttpSession session, Model model) {
        if (!isAdmin(session)) {
            return "admin-login";
        }
        model.addAttribute("groups", groupService.allGroupsByName());
        return "admin";
    }

    @PostMapping("/admin/login")
    public String login(@RequestParam String passphrase, HttpServletRequest request, Model model) {
        if (!siteSecrets.isAdminPassphrase(passphrase)) {
            model.addAttribute("error", "Wrong passphrase.");
            return "admin-login";
        }
        request.getSession();
        request.changeSessionId(); // new session ID after login (session fixation protection)
        request.getSession().setAttribute(SESSION_KEY, true);
        return "redirect:/admin";
    }

    @PostMapping("/admin/logout")
    public String logout(HttpSession session) {
        session.removeAttribute(SESSION_KEY);
        return "redirect:/admin";
    }

    @PostMapping("/admin/groups/{groupId}/reset-passphrase")
    public String resetPassphrase(@PathVariable String groupId, @RequestParam String newPassphrase,
                                  HttpSession session, RedirectAttributes redirect) {
        if (!isAdmin(session)) {
            return "redirect:/admin";
        }
        Group group = groupService.getGroup(groupId);
        String error = groupPassphrase.validate(newPassphrase);
        if (error != null) {
            redirect.addFlashAttribute("error", group.getGroupName() + ": " + error);
        } else {
            groupService.resetPassphrase(groupId, newPassphrase);
            redirect.addFlashAttribute("message", "Passphrase reset for " + group.getGroupName() + ".");
        }
        // Flash attributes survive exactly one redirect, then disappear
        return "redirect:/admin";
    }

    private boolean isAdmin(HttpSession session) {
        return Boolean.TRUE.equals(session.getAttribute(SESSION_KEY));
    }
}
