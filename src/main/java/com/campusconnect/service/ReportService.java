package com.campusconnect.service;

import com.campusconnect.model.Event;
import com.campusconnect.model.Group;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Builds the funding report: one CSV file per event, made to open cleanly in Excel. */
@Service
public class ReportService {

    // Dates and times use the same US-English text as every page (e.g. 11/24/2026 8:30 PM)
    private final TimeFormat timeFormat;

    public ReportService(TimeFormat timeFormat) {
        this.timeFormat = timeFormat;
    }

    public String buildCsv(Event event, List<ParticipantRow> rows, Double averageStayMinutes) {
        StringBuilder csv = new StringBuilder();
        csv.append("EventID,EventName,Date,Location,StudentID,Name,Email,Role,"
                + "CheckInTime,CheckOutTime,StayMinutes,CheckOutBy\n");
        for (ParticipantRow row : rows) {
            csv.append(field(event.getEventId())).append(',')
                    .append(field(event.getEventName())).append(',')
                    .append(text(timeFormat.date(event.getEventDate()))).append(',')
                    .append(field(event.getLocation())).append(',')
                    .append(text(row.studentId())).append(',')
                    .append(field(row.name())).append(',')
                    .append(field(row.email())).append(',')
                    .append(field(row.role())).append(',')
                    .append(text(timeFormat.dateTime(row.checkInTime()))).append(',')
                    .append(text(timeFormat.dateTime(row.checkOutTime()))).append(',')
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

    /**
     * File name like ISA_Weekly-Meeting_2026-11-25_attendance.csv.
     * Spaces become "-", and characters Windows does not allow in file names are removed.
     */
    public String fileName(Group group, Event event) {
        return cleanForFileName(group.getGroupName(), "group") + "_"
                + cleanForFileName(event.getEventName(), "event") + "_"
                + event.getEventDate() + "_attendance.csv"; // LocalDate prints as yyyy-MM-dd
    }

    /**
     * The Content-Disposition header that makes the browser download the file with this name.
     * filename*=UTF-8''... carries the real name (also Japanese) in UTF-8 (RFC 6266),
     * filename="..." is an English-only fallback for very old browsers.
     */
    public String contentDisposition(String fileName) {
        String asciiFallback = fileName.replaceAll("[^\\x20-\\x7E]", "_"); // non-English letters -> _
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return "attachment; filename=\"" + asciiFallback + "\"; filename*=UTF-8''" + encoded;
    }

    private String cleanForFileName(String text, String fallback) {
        String cleaned = text.trim()
                .replaceAll("[/\\\\:*?\"<>|]", "") // remove / \ : * ? " < > |
                .replaceAll("\\p{Cntrl}", "")      // remove invisible control characters
                .replaceAll("\\s+", "-");          // spaces -> "-"
        return cleaned.isEmpty() ? fallback : cleaned;
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
