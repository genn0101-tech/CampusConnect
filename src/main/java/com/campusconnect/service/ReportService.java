package com.campusconnect.service;

import com.campusconnect.model.Event;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Builds the funding report: one CSV file per event. */
@Service
public class ReportService {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public String buildCsv(Event event, List<ParticipantRow> rows, Double averageStayMinutes) {
        StringBuilder csv = new StringBuilder();
        csv.append("EventID,EventName,Date,Location,StudentID,Name,Email,Role,CheckInTime,CheckOutTime,StayMinutes\n");
        for (ParticipantRow row : rows) {
            csv.append(field(event.getEventId())).append(',')
                    .append(field(event.getEventName())).append(',')
                    .append(event.getEventDate()).append(',')
                    .append(field(event.getLocation())).append(',')
                    .append(field(row.studentId())).append(',')
                    .append(field(row.name())).append(',')
                    .append(field(row.email())).append(',')
                    .append(field(row.role())).append(',')
                    .append(format(row.checkInTime())).append(',')
                    .append(format(row.checkOutTime())).append(',')
                    .append(row.stayMinutes() == null ? "" : row.stayMinutes())
                    .append('\n');
        }
        // Summary lines at the bottom for the funding application
        csv.append('\n');
        csv.append("Total participants,").append(rows.size()).append('\n');
        csv.append("Average stay minutes,")
                .append(averageStayMinutes == null ? "" : String.format("%.1f", averageStayMinutes))
                .append('\n');
        return csv.toString();
    }

    private String format(LocalDateTime time) {
        return time == null ? "" : time.format(DATE_TIME);
    }

    /** Puts a value in quotes if it contains a comma, quote or newline (standard CSV rule). */
    private String field(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
