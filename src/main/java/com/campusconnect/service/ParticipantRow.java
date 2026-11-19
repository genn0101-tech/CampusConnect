package com.campusconnect.service;

import java.time.LocalDateTime;

/**
 * One line of the participant list: attendance data joined with the user's name and role.
 * A record is a small class that only holds values (Java 16+).
 */
public record ParticipantRow(
        String studentId,
        String name,
        String email,
        String role,
        LocalDateTime checkInTime,
        LocalDateTime checkOutTime,
        Long stayMinutes) { // null = still at the event
}
