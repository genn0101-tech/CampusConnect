package com.campusconnect.service;

import com.campusconnect.model.Group;
import com.campusconnect.repository.GroupRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;

@Service
public class GroupService {

    private static final int MAX_ID_LENGTH = 30;

    private final GroupRepository groupRepository;
    private final GroupPassphrase groupPassphrase;

    public GroupService(GroupRepository groupRepository, GroupPassphrase groupPassphrase) {
        this.groupRepository = groupRepository;
        this.groupPassphrase = groupPassphrase;
    }

    public List<Group> allGroupsByName() {
        List<Group> groups = groupRepository.findAll();
        groups.sort(Comparator.comparing(Group::getGroupName));
        return groups;
    }

    /** Finds a group or shows a "404 Not Found" page. */
    public Group getGroup(String groupId) {
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found"));
    }

    /** Creates a group. The passphrase is stored only as a BCrypt hash. */
    public Group createGroup(String groupName, String description, String passphrase) {
        Group group = new Group(newGroupId(groupName), groupName, description);
        group.setPassphraseHash(groupPassphrase.hash(passphrase));
        return groupRepository.save(group);
    }

    /** For the developer page: replaces a forgotten passphrase with a new one. */
    public void resetPassphrase(String groupId, String newPassphrase) {
        Group group = getGroup(groupId);
        group.setPassphraseHash(groupPassphrase.hash(newPassphrase));
        groupRepository.save(group);
    }

    /**
     * Makes a URL-friendly ID from the name: "Japanese Club" -> "JapaneseClub".
     * If that ID is taken, adds a number: "JapaneseClub2", "JapaneseClub3", ...
     */
    private String newGroupId(String groupName) {
        String base = groupName.replaceAll("[^A-Za-z0-9]", ""); // keep only letters and digits
        if (base.length() > MAX_ID_LENGTH) {
            base = base.substring(0, MAX_ID_LENGTH);
        }
        if (base.isEmpty()) {
            base = "Group"; // e.g. a name written only in Japanese
        }
        String id = base;
        int number = 2;
        while (groupRepository.existsById(id)) {
            id = base + number;
            number++;
        }
        return id;
    }
}
