package com.campusconnect.service;

/** Attendance rate of one member: attended events / held events of their group. */
public record MemberRate(
        String studentId,
        String name,
        String role,
        int attendedEvents,
        int heldEvents,
        Double ratePercent) { // null when the group has held no events yet
}
