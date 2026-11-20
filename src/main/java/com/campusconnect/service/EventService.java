package com.campusconnect.service;

import com.campusconnect.model.Event;
import com.campusconnect.repository.EventRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class EventService {

    private final EventRepository eventRepository;
    private final Clock clock;

    public EventService(EventRepository eventRepository, Clock clock) {
        this.eventRepository = eventRepository;
        this.clock = clock;
    }

    /** Events of one group, newest first. Sorted here at display time, not in storage. */
    public List<Event> eventsOfGroupNewestFirst(String groupId) {
        List<Event> events = eventRepository.findByGroupId(groupId);
        events.sort(Comparator.comparing(Event::getEventDate).reversed());
        return events;
    }

    /** Events from today on (today counts as upcoming), nearest date first: today, tomorrow, ... */
    public List<Event> upcomingEvents(String groupId) {
        LocalDate today = LocalDate.now(clock); // Pacific time
        List<Event> upcoming = new ArrayList<>();
        for (Event event : eventRepository.findByGroupId(groupId)) {
            if (!event.getEventDate().isBefore(today)) {
                upcoming.add(event);
            }
        }
        upcoming.sort(Comparator.comparing(Event::getEventDate));
        return upcoming;
    }

    /** Events before today, newest date first. */
    public List<Event> pastEvents(String groupId) {
        LocalDate today = LocalDate.now(clock);
        List<Event> past = new ArrayList<>();
        for (Event event : eventsOfGroupNewestFirst(groupId)) {
            if (event.getEventDate().isBefore(today)) {
                past.add(event);
            }
        }
        return past;
    }

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

    public Event createEvent(String eventName, LocalDate eventDate, String location, String groupId) {
        Event event = new Event(nextEventId(), eventName, eventDate, location, groupId);
        return eventRepository.save(event);
    }

    /** Makes the next ID in the same style as events.csv: EVT001, EVT002, ... */
    private String nextEventId() {
        int max = 0;
        for (Event event : eventRepository.findAll()) {
            String digits = event.getEventId().replaceAll("\\D", ""); // keep only the numbers
            if (!digits.isEmpty()) {
                max = Math.max(max, Integer.parseInt(digits));
            }
        }
        return String.format("EVT%03d", max + 1);
    }
}
