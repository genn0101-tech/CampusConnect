package com.campusconnect.controller;

import com.campusconnect.service.GroupService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Top page: only the list of groups. Events are shown on each group's own page. */
@Controller
public class HomeController {

    private final GroupService groupService;

    public HomeController(GroupService groupService) {
        this.groupService = groupService;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("groups", groupService.allGroupsByName());
        return "index"; // -> templates/index.html
    }
}
