package com.campusconnect.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A student organization, e.g. GRIT or Japanese Club. One row of groups.csv. */
@Entity
@Table(name = "student_groups") // "groups" is a reserved word in some databases
public class Group {

    @Id
    private String groupId;
    private String groupName;
    private String description;
    // BCrypt hash of the group passphrase. The plain passphrase is never stored.
    private String passphraseHash;

    // JPA needs an empty constructor to create objects from database rows
    protected Group() {
    }

    public Group(String groupId, String groupName, String description) {
        this.groupId = groupId;
        this.groupName = groupName;
        this.description = description;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getGroupName() {
        return groupName;
    }

    public String getDescription() {
        return description;
    }

    public String getPassphraseHash() {
        return passphraseHash;
    }

    public void setPassphraseHash(String passphraseHash) {
        this.passphraseHash = passphraseHash;
    }
}
