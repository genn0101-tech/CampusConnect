package com.campusconnect.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A folder of one group's events, e.g. "Weekly Meeting". An event is in one folder or in none. */
@Entity
@Table(name = "folders")
public class Folder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // the database assigns 1, 2, 3, ...
    private Long folderId;
    private String groupId;
    private String name;
    // Nullable like events.archived; null means "not archived"
    private Boolean archived;

    protected Folder() {
    }

    public Folder(String groupId, String name) {
        this.groupId = groupId;
        this.name = name;
    }

    public boolean isArchived() {
        return Boolean.TRUE.equals(archived);
    }

    public void archive() {
        this.archived = true;
    }

    public void restore() {
        this.archived = false;
    }

    public Long getFolderId() {
        return folderId;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getName() {
        return name;
    }
}
