package com.campusconnect.service;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The one place that turns dates and times into text, and text back into times.
 * Always US English (11/24/2026, 10:55 PM), whatever language or time zone the browser uses.
 * Stored times are already Pacific time (see ClockConfig), so no time zone conversion is needed.
 * Templates call it as ${@timeFormat.time(...)}.
 */
@Component("timeFormat")
public class TimeFormat {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("M/d/uuuu", Locale.US)
            .withResolverStyle(ResolverStyle.STRICT); // STRICT: 2/30/2026 is an error, not March 2
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.US);

    // "10:55 PM", "10:55pm", "10:55 p.m." or 24-hour "22:55"
    private static final Pattern TIME_INPUT = Pattern.compile("(\\d{1,2}):(\\d{2})(?:\\s*([AaPp])\\.?\\s*[Mm]?\\.?)?");

    /** 11/24/2026 */
    public String date(LocalDate date) {
        return date == null ? "" : date.format(DATE);
    }

    /** 10:55 PM */
    public String time(LocalDateTime time) {
        return time == null ? "" : time.format(TIME);
    }

    /** 11/24/2026 10:55 PM */
    public String dateTime(LocalDateTime time) {
        return time == null ? "" : date(time.toLocalDate()) + " " + time(time);
    }

    /** Date part for a form field: 11/24/2026 */
    public String dateOf(LocalDateTime time) {
        return time == null ? "" : date(time.toLocalDate());
    }

    /**
     * Reads a date field (11/24/2026) and a time field (10:55 PM) typed in a form.
     * Both empty = no time (null). Throws DateTimeParseException if the text is not valid.
     */
    public LocalDateTime parse(String dateText, String timeText) {
        String dateValue = dateText == null ? "" : dateText.trim();
        String timeValue = timeText == null ? "" : timeText.trim();
        if (dateValue.isEmpty() && timeValue.isEmpty()) {
            return null;
        }
        LocalDate date = LocalDate.parse(dateValue, DATE);
        return date.atTime(parseTime(timeValue));
    }

    private LocalTime parseTime(String text) {
        Matcher m = TIME_INPUT.matcher(text);
        if (!m.matches()) {
            throw new DateTimeParseException("Time must look like 10:55 PM", text, 0);
        }
        int hour = Integer.parseInt(m.group(1));
        int minute = Integer.parseInt(m.group(2));
        String amPm = m.group(3);
        if (minute > 59) {
            throw new DateTimeParseException("Minutes must be 00-59", text, 0);
        }
        if (amPm == null) {
            // 24-hour time, e.g. 22:55
            if (hour > 23) {
                throw new DateTimeParseException("Hour must be 0-23", text, 0);
            }
        } else {
            // 12-hour time: 12:xx AM is just after midnight, 12:xx PM is just after noon
            if (hour < 1 || hour > 12) {
                throw new DateTimeParseException("Hour must be 1-12", text, 0);
            }
            boolean pm = amPm.equalsIgnoreCase("p");
            hour = hour % 12 + (pm ? 12 : 0);
        }
        return LocalTime.of(hour, minute);
    }
}
