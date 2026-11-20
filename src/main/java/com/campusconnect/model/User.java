package com.campusconnect.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A student. One row of users.csv. Role is "Owner", "Member" or "Guest". */
@Entity
@Table(name = "users")
public class User {

    public static final String ROLE_GUEST = "Guest";
    // StudentID: 1-20 letters or digits (kept loose on purpose so nobody is blocked at the door)
    public static final String STUDENT_ID_PATTERN = "[A-Za-z0-9]{1,20}";

    @Id
    private String studentId;
    private String name;
    private String email;
    private String groupId;
    private String role;

    protected User() {
    }

    public User(String studentId, String name, String email, String groupId, String role) {
        this.studentId = studentId;
        this.name = name;
        this.email = email;
        this.groupId = groupId;
        this.role = role;
    }

    /**
     * Role shown on screens and in the CSV. "Guest" is shown as "Participant",
     * but the stored value stays "Guest" so attendance-rate rules do not change.
     */
    public String displayRole() {
        return displayRole(role);
    }

    public static String displayRole(String role) {
        return ROLE_GUEST.equals(role) ? "Participant" : role;
    }

    /** Fixes a typo in the name or email (the group's People page). */
    public void changeNameAndEmail(String name, String email) {
        this.name = name;
        this.email = email;
    }

    public boolean isGuest() {
        return ROLE_GUEST.equals(role);
    }

    public String getStudentId() {
        return studentId;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getRole() {
        return role;
    }
}
