package com.campusconnect.config;

import com.campusconnect.model.Attendance;
import com.campusconnect.model.Event;
import com.campusconnect.model.Group;
import com.campusconnect.model.Invitation;
import com.campusconnect.model.User;
import com.campusconnect.repository.AttendanceRepository;
import com.campusconnect.repository.EventRepository;
import com.campusconnect.repository.GroupRepository;
import com.campusconnect.repository.InvitationRepository;
import com.campusconnect.repository.UserRepository;
import com.campusconnect.service.GroupPassphrase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Runs once when the app starts and copies the Phase 2 CSV files into the database.
 * It only loads a table when that table is still empty, so restarting does not create duplicates.
 * In production (app.load-sample-data=false) nothing is loaded: groups are created on the website.
 */
@Component
public class CsvDataLoader implements CommandLineRunner {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final AttendanceRepository attendanceRepository;
    private final InvitationRepository invitationRepository;
    private final GroupPassphrase groupPassphrase;
    private final Environment environment;
    private final boolean loadSampleData;

    // Spring passes the repositories in automatically (constructor injection)
    public CsvDataLoader(GroupRepository groupRepository, UserRepository userRepository,
                         EventRepository eventRepository, AttendanceRepository attendanceRepository,
                         InvitationRepository invitationRepository,
                         GroupPassphrase groupPassphrase, Environment environment,
                         @Value("${app.load-sample-data}") boolean loadSampleData) {
        this.groupRepository = groupRepository;
        this.userRepository = userRepository;
        this.eventRepository = eventRepository;
        this.attendanceRepository = attendanceRepository;
        this.invitationRepository = invitationRepository;
        this.groupPassphrase = groupPassphrase;
        this.environment = environment;
        this.loadSampleData = loadSampleData;
    }

    @Override
    public void run(String... args) throws IOException {
        // All sample data (groups too) is dummy data for development only
        if (!loadSampleData) {
            return;
        }
        if (groupRepository.count() == 0) {
            for (String[] v : readCsv("groups.csv")) {
                groupRepository.save(new Group(v[0], v[1], v[2]));
            }
        }
        setDevPassphrases();
        if (userRepository.count() == 0) {
            for (String[] v : readCsv("users.csv")) {
                userRepository.save(new User(v[0], v[1], v[2], v[3], v[4]));
            }
        }
        if (eventRepository.count() == 0) {
            for (String[] v : readCsv("events.csv")) {
                eventRepository.save(new Event(v[0], v[1], LocalDate.parse(v[2]), v[3], v[4]));
            }
        }
        if (attendanceRepository.count() == 0) {
            for (String[] v : readCsv("attendance.csv")) {
                LocalDateTime checkIn = LocalDateTime.parse(v[2], DATE_TIME);
                LocalDateTime checkOut = v[3].isBlank() ? null : LocalDateTime.parse(v[3], DATE_TIME);
                attendanceRepository.save(new Attendance(v[0], v[1], checkIn, checkOut));
            }
        }
        if (invitationRepository.count() == 0) {
            for (String[] v : readCsv("invitations.csv")) {
                invitationRepository.save(new Invitation(v[0], v[1], v[2]));
            }
        }
    }

    /**
     * Gives each sample group without a passphrase its development passphrase,
     * e.g. app.passphrase.grit=dev-grit in application-dev.properties (stored as a BCrypt hash).
     */
    private void setDevPassphrases() {
        for (Group group : groupRepository.findAll()) {
            String key = "app.passphrase." + group.getGroupId().toLowerCase(Locale.ROOT);
            String devPassphrase = environment.getProperty(key);
            if (group.getPassphraseHash() == null && devPassphrase != null && !devPassphrase.isBlank()) {
                group.setPassphraseHash(groupPassphrase.hash(devPassphrase));
                groupRepository.save(group);
            }
        }
    }

    /** Reads src/main/resources/data/{fileName}, skips the header, and splits each line by commas. */
    private List<String[]> readCsv(String fileName) throws IOException {
        List<String[]> rows = new ArrayList<>();
        ClassPathResource file = new ClassPathResource("data/" + fileName);
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            reader.readLine(); // skip header line
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    // -1 keeps empty columns at the end (e.g. a missing CheckOutTime)
                    rows.add(line.split(",", -1));
                }
            }
        }
        return rows;
    }
}
