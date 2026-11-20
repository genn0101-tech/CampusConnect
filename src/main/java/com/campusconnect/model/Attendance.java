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

    public static final String SOURCE_SELF = "SELF";
    public static final String SOURCE_OWNER = "OWNER";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // the database assigns 1, 2, 3, ...
    private Long id;
    private String eventId;
    private String studentId;
    private LocalDateTime checkInTime;
    private LocalDateTime checkOutTime; // null until the student checks out
    // Who recorded the check-out: SOURCE_SELF (the student, by QR) or SOURCE_OWNER (typed or fixed by the group).
    // Nullable on purpose: adding a NOT NULL column to a table that already has rows would fail.
    // Rows from before this column existed have null; back then only QR check-out existed.
    private String checkoutSource;

    protected Attendance() {
    }

    public Attendance(String eventId, String studentId, LocalDateTime checkInTime, LocalDateTime checkOutTime) {
        this.eventId = eventId;
        this.studentId = studentId;
        this.checkInTime = checkInTime;
        this.checkOutTime = checkOutTime;
    }

    /** Check-out by the student (QR page). */
    public void checkOut(LocalDateTime time) {
        this.checkOutTime = time;
        this.checkoutSource = SOURCE_SELF;
    }

    /** Check-out time typed or fixed by the group. null = "still here" again. */
    public void setCheckOutByOwner(LocalDateTime time) {
        this.checkOutTime = time;
        this.checkoutSource = (time == null) ? null : SOURCE_OWNER;
    }

    /** Moves this record to a corrected Student ID (the group's People page). */
    public void changeStudentId(String newStudentId) {
        this.studentId = newStudentId;
    }

    /** Check-in time fixed by the group. Does not change who recorded the check-out. */
    public void setCheckInTime(LocalDateTime time) {
        this.checkInTime = time;
    }

    /** Label for the participant list and CSV: "QR", "Owner", or "" if not checked out. */
    public String checkoutSourceLabel() {
        if (checkOutTime == null) {
            return "";
        }
        return SOURCE_OWNER.equals(checkoutSource) ? "Owner" : "QR"; // null = old QR check-out
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

    public String getCheckoutSource() {
        return checkoutSource;
    }
}
