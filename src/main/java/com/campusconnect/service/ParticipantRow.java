package com.campusconnect.service;

import java.time.LocalDateTime;

/**
 * One line of the participant list: attendance data joined with the user's name and role.
 * A record is a small class that only holds values (Java 16+).
 */
public record ParticipantRow(
        Long attendanceId,     // used by the "Edit" link of each row
        String studentId,
        String name,
        String email,
        String role,           // display name: "Guest" is shown as "Participant"
        LocalDateTime checkInTime,
        LocalDateTime checkOutTime,
        Long stayMinutes,      // null = still at the event
        String checkoutSource) { // "QR", "Owner", or "" if not checked out

    public boolean isStillHere() {
        return checkOutTime == null;
    }
}
