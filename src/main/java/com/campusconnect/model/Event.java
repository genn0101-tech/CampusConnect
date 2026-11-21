package com.campusconnect.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;

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
    // Meeting start and end (Pacific time). Nullable: events made before this feature have none,
    // and work as before (no automatic check-out). End may be on the next day (e.g. 10 PM - 1 AM).
    private LocalDateTime startAt;
    private LocalDateTime endAt;

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

    public LocalDateTime getStartAt() {
        return startAt;
    }

    public LocalDateTime getEndAt() {
        return endAt;
    }

    /** Sets the meeting time. Both null = no meeting time (old events). */
    public void setTimes(LocalDateTime startAt, LocalDateTime endAt) {
        this.startAt = startAt;
        this.endAt = endAt;
    }

    /** Fixes the name, date, and location (owner). */
    public void changeDetails(String eventName, LocalDate eventDate, String location) {
        this.eventName = eventName;
        this.eventDate = eventDate;
        this.location = location;
    }

    /** True when the event has an end time and it has passed. */
    public boolean isOver(LocalDateTime now) {
        return endAt != null && now.isAfter(endAt);
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
