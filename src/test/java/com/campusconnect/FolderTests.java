package com.campusconnect;

import com.campusconnect.model.Event;
import com.campusconnect.model.Folder;
import com.campusconnect.repository.EventRepository;
import com.campusconnect.repository.FolderRepository;
import com.campusconnect.service.AttendanceService;
import com.campusconnect.service.EventService;
import com.campusconnect.service.FolderService;
import com.campusconnect.service.FolderService.FolderSection;
import com.campusconnect.service.MemberRate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Folders, using the sample data (EVT001 = GRIT's one past event, no folder).
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:foldertest")
@Transactional
class FolderTests {

    @Autowired
    private FolderService folderService;
    @Autowired
    private EventService eventService;
    @Autowired
    private AttendanceService attendanceService;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private FolderRepository folderRepository;

    private LocalDate today() {
        return attendanceService.currentTime().toLocalDate(); // Pacific time
    }

    @Test
    void groupPageHasOneSectionPerFolderThenNoFolder() {
        Folder weekly = folderService.createFolder("GRIT", "Weekly Meeting");
        Folder shortOne = folderService.createFolder("GRIT", "Short Meeting");
        eventService.createEvent("Week 1", today(), "", "GRIT", weekly.getFolderId());
        eventService.createEvent("Quick sync", today().plusDays(1), "", "GRIT", shortOne.getFolderId());

        List<FolderSection> sections = folderService.groupSections("GRIT");
        assertEquals(3, sections.size());
        assertEquals("Short Meeting", sections.get(0).folder().getName()); // folders by name
        assertEquals("Weekly Meeting", sections.get(1).folder().getName());
        assertEquals("Week 1", sections.get(1).upcoming().get(0).getEventName());
        assertNull(sections.get(2).folder()); // last: events without a folder
        assertEquals("EVT001", sections.get(2).past().get(0).getEventId());
    }

    @Test
    void folderNamesMustBeUniqueInTheGroup() {
        folderService.createFolder("GRIT", "Weekly Meeting");
        assertNotNull(folderService.validateName("GRIT", "weekly meeting"));
        assertNotNull(folderService.validateName("GRIT", ""));
        assertNull(folderService.validateName("JapaneseClub", "Weekly Meeting")); // other group: fine
    }

    @Test
    void eventCanMoveIntoAndOutOfAFolder() {
        Folder weekly = folderService.createFolder("GRIT", "Weekly Meeting");
        folderService.moveEvent("EVT001", "GRIT", weekly.getFolderId());
        assertEquals(weekly.getFolderId(), eventRepository.findById("EVT001").get().getFolderId());

        folderService.moveEvent("EVT001", "GRIT", null);
        assertNull(eventRepository.findById("EVT001").get().getFolderId());

        // Another group's folder cannot be used
        Folder other = folderService.createFolder("JapaneseClub", "Meetups");
        assertThrows(ResponseStatusException.class,
                () -> folderService.moveEvent("EVT001", "GRIT", other.getFolderId()));
    }

    @Test
    void archivedFolderHidesItsEventsButTheyStillCount() {
        Folder weekly = folderService.createFolder("GRIT", "Weekly Meeting");
        folderService.moveEvent("EVT001", "GRIT", weekly.getFolderId());
        folderService.setArchived(weekly.getFolderId(), "GRIT", true);

        List<FolderSection> sections = folderService.groupSections("GRIT");
        assertEquals(1, sections.size()); // only "no folder" is left, and it is empty
        assertTrue(sections.get(0).past().isEmpty());
        assertEquals(1, folderService.archivedFolders("GRIT").size());
        for (MemberRate rate : attendanceService.attendanceRates("GRIT")) {
            assertEquals(1, rate.heldEvents()); // still counted
        }
        // New events cannot be put into an archived folder
        assertThrows(ResponseStatusException.class,
                () -> folderService.checkChosenFolder("GRIT", weekly.getFolderId()));
    }

    @Test
    void deletingAFolderKeepsItsEvents() {
        Folder weekly = folderService.createFolder("GRIT", "Weekly Meeting");
        folderService.moveEvent("EVT001", "GRIT", weekly.getFolderId());

        // Only an archived folder can be deleted
        assertThrows(ResponseStatusException.class,
                () -> folderService.deleteArchivedFolder(weekly.getFolderId(), "GRIT"));

        folderService.setArchived(weekly.getFolderId(), "GRIT", true);
        folderService.deleteArchivedFolder(weekly.getFolderId(), "GRIT");

        assertFalse(folderRepository.existsById(weekly.getFolderId()));
        Event kept = eventRepository.findById("EVT001").orElseThrow();
        assertNull(kept.getFolderId()); // back to "no folder"
        assertEquals(5, attendanceService.participants("EVT001").size()); // attendance untouched
    }
}
