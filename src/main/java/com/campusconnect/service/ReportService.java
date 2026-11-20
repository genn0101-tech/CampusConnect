package com.campusconnect.service;

import com.campusconnect.model.Event;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Builds the funding report: one CSV file per event, made to open cleanly in Excel. */
@Service
public class ReportService {

    // US style, e.g. 11/24/2026 and 11/24/2026 8:30 PM (times are already Pacific time)
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("M/d/yyyy", Locale.US);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("M/d/yyyy h:mm a", Locale.US);

    public String buildCsv(Event event, List<ParticipantRow> rows, Double averageStayMinutes) {
        StringBuilder csv = new StringBuilder();
        csv.append("EventID,EventName,Date,Location,StudentID,Name,Email,Role,"
                + "CheckInTime,CheckOutTime,StayMinutes,CheckOutBy\n");
        for (ParticipantRow row : rows) {
            csv.append(field(event.getEventId())).append(',')
                    .append(field(event.getEventName())).append(',')
                    .append(text(format(event.getEventDate()))).append(',')
                    .append(field(event.getLocation())).append(',')
                    .append(text(row.studentId())).append(',')
                    .append(field(row.name())).append(',')
                    .append(field(row.email())).append(',')
                    .append(field(row.role())).append(',')
                    .append(text(format(row.checkInTime()))).append(',')
                    .append(text(format(row.checkOutTime()))).append(',')
                    .append(row.stayMinutes() == null ? "" : row.stayMinutes()).append(',')
                    .append(field(row.checkoutSource()))
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

    private String format(LocalDate date) {
        return date == null ? "" : date.format(DATE);
    }

    private String format(LocalDateTime time) {
        return time == null ? "" : time.format(DATE_TIME);
    }

    /**
     * A cell Excel must keep as text, written as ="value".
     * Without this, Excel turns dates into numbers (shown as ####),
     * long IDs into 2.22E+08, and removes leading zeros from Student IDs.
     */
    private String text(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String formula = "=\"" + value.replace("\"", "\"\"") + "\""; // ="value"
        return quote(formula);
    }

    /** A normal cell, quoted if it contains a comma, quote or newline (standard CSV rule). */
    private String field(String value) {
        if (value == null) {
            return "";
        }
        // Anyone can type their own name, so a name like "=HYPERLINK(...)" must not run
        // as a formula in Excel. A leading ' makes Excel show it as plain text.
        if (!value.isEmpty() && "=+-@\t\r".indexOf(value.charAt(0)) >= 0) {
            value = "'" + value;
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return quote(value);
        }
        return value;
    }

    /** Wraps a cell in quotes and doubles the quotes inside it (standard CSV rule). */
    private String quote(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
