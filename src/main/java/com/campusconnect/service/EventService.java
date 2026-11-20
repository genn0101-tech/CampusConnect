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
    private final Clock clock;

    public EventService(EventRepository eventRepository, AttendanceRepository attendanceRepository,
                        InvitationRepository invitationRepository, IdCounterRepository idCounterRepository,
                        Clock clock) {
        this.eventRepository = eventRepository;
        this.attendanceRepository = attendanceRepository;
        this.invitationRepository = invitationRepository;
        this.idCounterRepository = idCounterRepository;
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
        LocalDate today = LocalDate.now(clock); // Pacific time
        List<Event> upcoming = new ArrayList<>();
        for (Event event : eventRepository.findByGroupId(groupId)) {
            if (!event.isArchived() && !event.getEventDate().isBefore(today)) {
                upcoming.add(event);
            }
        }
        upcoming.sort(Comparator.comparing(Event::getEventDate));
        return upcoming;
    }

    /** Not archived, before today, newest date first. */
    public List<Event> pastEvents(String groupId) {
        LocalDate today = LocalDate.now(clock);
        List<Event> past = new ArrayList<>();
        for (Event event : eventsOfGroupNewestFirst(groupId)) {
            if (!event.isArchived() && event.getEventDate().isBefore(today)) {
                past.add(event);
            }
        }
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

    @Transactional
    public Event createEvent(String eventName, LocalDate eventDate, String location, String groupId) {
        Event event = new Event(nextEventId(), eventName, eventDate, location, groupId);
        return eventRepository.save(event);
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
