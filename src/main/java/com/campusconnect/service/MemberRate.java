package com.campusconnect.service;

import java.time.LocalDate;

/**
 * Attendance rate of one person in one group:
 * attended events / events of the group held since their first visit.
 */
public record MemberRate(
        String studentId,
        String name,
        String role,          // display name ("Participant" for Guest)
        LocalDate firstVisit, // date of the first event they came to
        int attendedEvents,
        int heldEvents,       // held since the first visit (including it)
        Double ratePercent) {
}
