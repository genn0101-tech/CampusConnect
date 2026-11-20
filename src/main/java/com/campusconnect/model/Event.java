package com.campusconnect.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

/** One event, e.g. "GRIT Weekly Meeting". One row of events.csv. */
@Entity
@Table(name = "events")
public class Event {

    @Id
    private String eventId;
    private String eventName;
    private LocalDate eventDate; // not "date": that is a reserved word in SQL
    private String location;
    private String groupId;
    // true = hidden from the normal lists (still counted in attendance rates and reports).
    // Nullable so it can be added to a table that already has rows; null means "not archived".
    private Boolean archived;
    // The folder this event is in, or null for "no folder"
    private Long folderId;

    protected Event() {
    }

    public Event(String eventId, String eventName, LocalDate eventDate, String location, String groupId) {
        this.eventId = eventId;
        this.eventName = eventName;
        this.eventDate = eventDate;
        this.location = location;
        this.groupId = groupId;
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

    /** Puts the event in a folder, or takes it out with null. */
    public void moveToFolder(Long folderId) {
        this.folderId = folderId;
    }

    public String getEventId() {
        return eventId;
    }

    public String getEventName() {
        return eventName;
    }

    public LocalDate getEventDate() {
        return eventDate;
    }

    public String getLocation() {
        return location;
    }

    public String getGroupId() {
        return groupId;
    }
}
