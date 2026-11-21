package com.campusconnect.service;

import com.campusconnect.model.Event;
import com.campusconnect.model.IdCounter;
import com.campusconnect.repository.AttendanceRepository;
import com.campusconnect.repository.EventRepository;
import com.campusconnect.repository.IdCounterRepository;
import com.campusconnect.repository.InvitationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class EventService {

    private static final String EVENT_COUNTER = "event";

    private final EventRepository eventRepository;
    private final AttendanceRepository attendanceRepository;
    private final InvitationRepository invitationRepository;
    private final IdCounterRepository idCounterRepository;
    private final AttendanceService attendanceService;
    private final Clock clock;

    /** Start and end of a meeting. */
    public record MeetingTime(LocalDateTime start, LocalDateTime end) {
    }

    public EventService(EventRepository eventRepository, AttendanceRepository attendanceRepository,
                        InvitationRepository invitationRepository, IdCounterRepository idCounterRepository,
                        AttendanceService attendanceService, Clock clock) {
        this.eventRepository = eventRepository;
        this.attendanceRepository = attendanceRepository;
        this.invitationRepository = invitationRepository;
        this.idCounterRepository = idCounterRepository;
        this.attendanceService = attendanceService;
        this.clock = clock;
    }

    // ---------- Lists (sorted here at display time, not in storage) ----------

    /** All events of one group (archived too), newest first. */
    public List<Event> eventsOfGroupNewestFirst(String groupId) {
        List<Event> events = eventRepository.findByGroupId(groupId);
        events.sort(Comparator.comparing(Event::getEventDate).reversed());
        return events;
    }

    /** Not archived, from today on (today counts as upcoming), nearest date first: today, tomorrow, ... */
    public List<Event> upcomingEvents(String groupId) {
        return upcomingOf(eventRepository.findByGroupId(groupId));
    }

    /** Not archived, before today, newest date first. */
    public List<Event> pastEvents(String groupId) {
        return pastOf(eventRepository.findByGroupId(groupId));
    }

    /** From a list of events: the upcoming ones that are not archived, nearest date first. */
    public List<Event> upcomingOf(List<Event> events) {
        LocalDate today = LocalDate.now(clock); // Pacific time
        List<Event> upcoming = new ArrayList<>();
        for (Event event : events) {
            if (!event.isArchived() && !event.getEventDate().isBefore(today)) {
                upcoming.add(event);
            }
        }
        upcoming.sort(Comparator.comparing(Event::getEventDate));
        return upcoming;
    }

    /** From a list of events: the past ones that are not archived, newest date first. */
    public List<Event> pastOf(List<Event> events) {
        LocalDate today = LocalDate.now(clock);
        List<Event> past = new ArrayList<>();
        for (Event event : events) {
            if (!event.isArchived() && event.getEventDate().isBefore(today)) {
                past.add(event);
            }
        }
        past.sort(Comparator.comparing(Event::getEventDate).reversed());
        return past;
    }

    /** Archived events, newest first. */
    public List<Event> archivedEvents(String groupId) {
        List<Event> archived = new ArrayList<>();
        for (Event event : eventsOfGroupNewestFirst(groupId)) {
            if (event.isArchived()) {
                archived.add(event);
            }
        }
        return archived;
    }

    // ---------- One event ----------

    /** Finds an event or shows a "404 Not Found" page if the ID does not exist. */
    public Event getEvent(String eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));
    }

    /** Like getEvent, but also 404 if the event belongs to a different group. */
    public Event getEventInGroup(String eventId, String groupId) {
        Event event = getEvent(eventId);
        if (!event.getGroupId().equals(groupId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found");
        }
        return event;
    }

    /**
     * Start and end on the event date. An end at or before the start means the next day
     * (e.g. 10:00 PM - 1:00 AM).
     */
    public static MeetingTime meetingTime(LocalDate date, LocalTime start, LocalTime end) {
        LocalDateTime startAt = date.atTime(start);
        LocalDateTime endAt = date.atTime(end);
        if (!endAt.isAfter(startAt)) {
            endAt = endAt.plusDays(1);
        }
        return new MeetingTime(startAt, endAt);
    }

    /** Without a meeting time (used by tests and old code paths). */
    public Event createEvent(String eventName, LocalDate eventDate, String location, String groupId) {
        return createEvent(eventName, eventDate, location, groupId, null, null);
    }

    public Event createEvent(String eventName, LocalDate eventDate, String location, String groupId,
                             Long folderId) {
        return createEvent(eventName, eventDate, location, groupId, folderId, null);
    }

    /**
     * folderId may be null (no folder); the caller checks that the folder belongs to the group.
     * time may be null (no meeting time).
     */
    @Transactional
    public Event createEvent(String eventName, LocalDate eventDate, String location, String groupId,
                             Long folderId, MeetingTime time) {
        Event event = new Event(nextEventId(), eventName, eventDate, location, groupId);
        event.moveToFolder(folderId);
        if (time != null) {
            event.setTimes(time.start(), time.end());
        }
        return eventRepository.save(event);
    }

    /**
     * Owner fixes the name, date, location, or meeting time (e.g. extends the end).
     * time = null removes the meeting time. If the end changes, automatic check-outs are redone.
     */
    @Transactional
    public void updateEvent(String eventId, String groupId, String eventName, LocalDate eventDate,
                            String location, MeetingTime time) {
        Event event = getEventInGroup(eventId, groupId);
        LocalDateTime oldEnd = event.getEndAt();
        event.changeDetails(eventName, eventDate, location);
        event.setTimes(time == null ? null : time.start(), time == null ? null : time.end());
        eventRepository.save(event);
        LocalDateTime newEnd = event.getEndAt();
        boolean endChanged = (oldEnd == null) ? newEnd != null : !oldEnd.equals(newEnd);
        if (endChanged) {
            attendanceService.redoAutoCheckOuts(event);
        }
    }

    // ---------- Archive and delete ----------

    /** Archive (true) or restore (false). Archived events still count in attendance rates and reports. */
    public void setArchived(String eventId, String groupId, boolean archived) {
        Event event = getEventInGroup(eventId, groupId);
        if (archived) {
            event.archive();
        } else {
            event.restore();
        }
        eventRepository.save(event);
    }

    /** Number of attendance records, shown on the delete confirmation page. */
    public int attendanceCount(String eventId) {
        return attendanceRepository.findByEventId(eventId).size();
    }

    /**
     * Deletes an archived event with all its attendance records and invitations. Cannot be undone.
     * Only archived events can be deleted, so an event in use is never deleted by one wrong tap.
     * People (users) are kept: they may have attended other events.
     */
    @Transactional
    public void deleteArchivedEvent(String eventId, String groupId) {
        Event event = getEventInGroup(eventId, groupId);
        if (!event.isArchived()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Archive the event before deleting it");
        }
        attendanceRepository.deleteAll(attendanceRepository.findByEventId(eventId));
        invitationRepository.deleteAll(invitationRepository.findByEventId(eventId));
        eventRepository.delete(event);
    }

    /**
     * Makes the next ID in the same style as events.csv: EVT001, EVT002, ...
     * The counter remembers the highest number ever used, so a deleted event's ID is never reused
     * (a QR code printed for the deleted event must not open a new event).
     */
    private String nextEventId() {
        int max = 0;
        for (Event event : eventRepository.findAll()) {
            String digits = event.getEventId().replaceAll("\\D", ""); // keep only the numbers
            if (!digits.isEmpty()) {
                max = Math.max(max, Integer.parseInt(digits));
            }
        }
        IdCounter counter = idCounterRepository.findById(EVENT_COUNTER)
                .orElse(new IdCounter(EVENT_COUNTER, 0));
        int next = Math.max(max, counter.getLastNumber()) + 1;
        counter.setLastNumber(next);
        idCounterRepository.save(counter);
        return String.format("EVT%03d", next);
    }
}
