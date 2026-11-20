package com.campusconnect;

import com.campusconnect.service.TimeFormat;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

// Plain unit test: no Spring, just the TimeFormat class.
class TimeFormatTests {

    private final TimeFormat timeFormat = new TimeFormat();
    private final LocalDateTime evening = LocalDateTime.of(2026, 11, 24, 22, 55);

    @Test
    void showsUsEnglishText() {
        assertEquals("10:55 PM", timeFormat.time(evening));
        assertEquals("11/24/2026 10:55 PM", timeFormat.dateTime(evening));
        assertEquals("11/24/2026", timeFormat.date(LocalDate.of(2026, 11, 24)));
        assertEquals("12:05 AM", timeFormat.time(LocalDateTime.of(2026, 11, 24, 0, 5)));
        assertEquals("", timeFormat.time(null));
    }

    @Test
    void sameTextWhateverTheComputerLanguage() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.JAPAN); // a Japanese server would print 午後 without Locale.US
            assertEquals("10:55 PM", timeFormat.time(evening));
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    void readsTypedTimes() {
        assertEquals(evening, timeFormat.parse("11/24/2026", "10:55 PM"));
        assertEquals(evening, timeFormat.parse("11/24/2026", "10:55pm"));
        assertEquals(evening, timeFormat.parse("11/24/2026", "10:55 p.m."));
        assertEquals(evening, timeFormat.parse(" 11/24/2026 ", "22:55")); // 24-hour also works
        assertEquals(LocalDateTime.of(2026, 11, 24, 0, 30), timeFormat.parse("11/24/2026", "12:30 AM"));
        assertEquals(LocalDateTime.of(2026, 11, 24, 12, 30), timeFormat.parse("11/24/2026", "12:30 PM"));
        assertNull(timeFormat.parse("", "")); // both empty = no time
    }

    @Test
    void rejectsWrongInput() {
        assertThrows(DateTimeParseException.class, () -> timeFormat.parse("2/30/2026", "10:00 AM"));
        assertThrows(DateTimeParseException.class, () -> timeFormat.parse("11/24/2026", "13:00 PM"));
        assertThrows(DateTimeParseException.class, () -> timeFormat.parse("11/24/2026", "10:75"));
        assertThrows(DateTimeParseException.class, () -> timeFormat.parse("11/24/2026", "10:55 M"));
        assertThrows(DateTimeParseException.class, () -> timeFormat.parse("11/24/2026", ""));
        assertThrows(DateTimeParseException.class, () -> timeFormat.parse("2026-11-24", "10:55 PM"));
    }
}
