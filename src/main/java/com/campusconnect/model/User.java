package com.campusconnect.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A student. One row of users.csv. Role is "Owner", "Member" or "Guest". */
@Entity
@Table(name = "users")
public class User {

    public static final String ROLE_GUEST = "Guest";

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
