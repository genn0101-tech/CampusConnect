package com.campusconnect.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Remembers the highest number ever given out, e.g. name "event" -> 12 means EVT012 was the last event ID.
 * Without it, deleting the newest event would let the next event reuse its ID,
 * and a QR code printed for the deleted event would open the new event.
 */
@Entity
@Table(name = "id_counters")
public class IdCounter {

    @Id
    private String name;
    private int lastNumber;

    protected IdCounter() {
    }

    public IdCounter(String name, int lastNumber) {
        this.name = name;
        this.lastNumber = lastNumber;
    }

    public int getLastNumber() {
        return lastNumber;
    }

    public void setLastNumber(int lastNumber) {
        this.lastNumber = lastNumber;
    }
}
