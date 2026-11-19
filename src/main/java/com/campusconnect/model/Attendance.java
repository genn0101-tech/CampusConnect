package com.campusconnect.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * One check-in (and later check-out) of one student at one event. One row of attendance.csv.
 * Name and role are NOT stored here; they are looked up from User by studentId (normalization).
 */
@Entity
@Table(name = "attendance",
        uniqueConstraints = @UniqueConstraint(columnNames = {"eventId", "studentId"})) // one record per student per event
public class Attendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // the database assigns 1, 2, 3, ...
    private Long id;
    private String eventId;
    private String studentId;
    private LocalDateTime checkInTime;
    private LocalDateTime checkOutTime; // null until the student checks out

    protected Attendance() {
    }

    public Attendance(String eventId, String studentId, LocalDateTime checkInTime, LocalDateTime checkOutTime) {
        this.eventId = eventId;
        this.studentId = studentId;
        this.checkInTime = checkInTime;
        this.checkOutTime = checkOutTime;
    }

    public void checkOut(LocalDateTime time) {
        this.checkOutTime = time;
    }

    public boolean isCheckedOut() {
        return checkOutTime != null;
    }

    /** Stay duration in minutes (same idea as Phase 1). Returns null if the student has not checked out yet. */
    public Long stayMinutes() {
        if (checkOutTime == null) {
            return null;
        }
        return Duration.between(checkInTime, checkOutTime).toMinutes();
    }

    public Long getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public String getStudentId() {
        return studentId;
    }

    public LocalDateTime getCheckInTime() {
        return checkInTime;
    }

    public LocalDateTime getCheckOutTime() {
        return checkOutTime;
    }
}
