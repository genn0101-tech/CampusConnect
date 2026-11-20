package com.campusconnect.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * An invitation of a student to an event. One row of invitations.csv.
 * Stored now so the table exists; invitation management comes after the applications.
 */
@Entity
@Table(name = "invitations")
public class Invitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String eventId;
    private String studentId;
    private String status;

    protected Invitation() {
    }

    public Invitation(String eventId, String studentId, String status) {
        this.eventId = eventId;
        this.studentId = studentId;
        this.status = status;
    }

    /** Moves this invitation to a corrected Student ID. */
    public void changeStudentId(String newStudentId) {
        this.studentId = newStudentId;
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

    public String getStatus() {
        return status;
    }
}
