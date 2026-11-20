package com.campusconnect.service;

import com.campusconnect.model.Event;
import com.campusconnect.model.Folder;
import com.campusconnect.repository.EventRepository;
import com.campusconnect.repository.FolderRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Folders of a group's events (e.g. "Weekly Meeting"), and the folder sections of the group page. */
@Service
public class FolderService {

    private static final int MAX_NAME_LENGTH = 40;

    /** One block of the group page. folder = null means "events without a folder". */
    public record FolderSection(Folder folder, List<Event> upcoming, List<Event> past) {
    }

    private final FolderRepository folderRepository;
    private final EventRepository eventRepository;
    private final EventService eventService;

    public FolderService(FolderRepository folderRepository, EventRepository eventRepository,
                         EventService eventService) {
        this.folderRepository = folderRepository;
        this.eventRepository = eventRepository;
        this.eventService = eventService;
    }

    // ---------- Lists ----------

    /** Folders that are not archived, by name. */
    public List<Folder> activeFolders(String groupId) {
        List<Folder> folders = new ArrayList<>();
        for (Folder folder : folderRepository.findByGroupId(groupId)) {
            if (!folder.isArchived()) {
                folders.add(folder);
            }
        }
        folders.sort(Comparator.comparing(Folder::getName, String.CASE_INSENSITIVE_ORDER));
        return folders;
    }

    /** Archived folders, by name. */
    public List<Folder> archivedFolders(String groupId) {
        List<Folder> folders = new ArrayList<>();
        for (Folder folder : folderRepository.findByGroupId(groupId)) {
            if (folder.isArchived()) {
                folders.add(folder);
            }
        }
        folders.sort(Comparator.comparing(Folder::getName, String.CASE_INSENSITIVE_ORDER));
        return folders;
    }

    /**
     * The group page: one section per active folder, then the events without a folder.
     * Events inside an archived folder are not shown here (they are under Archived).
     */
    public List<FolderSection> groupSections(String groupId) {
        List<FolderSection> sections = new ArrayList<>();
        for (Folder folder : activeFolders(groupId)) {
            sections.add(sectionOf(folder));
        }
        List<Event> noFolder = new ArrayList<>();
        for (Event event : eventRepository.findByGroupId(groupId)) {
            if (event.getFolderId() == null) {
                noFolder.add(event);
            }
        }
        sections.add(new FolderSection(null, eventService.upcomingOf(noFolder), eventService.pastOf(noFolder)));
        return sections;
    }

    /** Upcoming and past (not archived) events of one folder. */
    public FolderSection sectionOf(Folder folder) {
        List<Event> events = eventRepository.findByFolderId(folder.getFolderId());
        return new FolderSection(folder, eventService.upcomingOf(events), eventService.pastOf(events));
    }

    /** Number of events in a folder (archived ones too), for the delete page. */
    public int eventCount(Long folderId) {
        return eventRepository.findByFolderId(folderId).size();
    }

    // ---------- One folder ----------

    /** Finds a folder, or "404 Not Found" if it does not exist or belongs to another group. */
    public Folder getFolderInGroup(Long folderId, String groupId) {
        Folder folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Folder not found"));
        if (!folder.getGroupId().equals(groupId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Folder not found");
        }
        return folder;
    }

    /** Returns an error message, or null if the name is fine. */
    public String validateName(String groupId, String name) {
        if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
            return "Folder name is required (up to " + MAX_NAME_LENGTH + " characters).";
        }
        for (Folder folder : folderRepository.findByGroupId(groupId)) {
            if (folder.getName().equalsIgnoreCase(name)) {
                return "A folder named " + folder.getName() + " already exists.";
            }
        }
        return null;
    }

    public Folder createFolder(String groupId, String name) {
        return folderRepository.save(new Folder(groupId, name));
    }

    /** Archive (true) or restore (false). The events inside are hidden or shown with it. */
    public void setArchived(Long folderId, String groupId, boolean archived) {
        Folder folder = getFolderInGroup(folderId, groupId);
        if (archived) {
            folder.archive();
        } else {
            folder.restore();
        }
        folderRepository.save(folder);
    }

    /**
     * Deletes an archived folder. The events inside are NOT deleted: they go back to "no folder".
     * @Transactional: the events are moved and the folder is removed together.
     */
    @Transactional
    public void deleteArchivedFolder(Long folderId, String groupId) {
        Folder folder = getFolderInGroup(folderId, groupId);
        if (!folder.isArchived()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Archive the folder before deleting it");
        }
        for (Event event : eventRepository.findByFolderId(folderId)) {
            event.moveToFolder(null);
            eventRepository.save(event);
        }
        folderRepository.delete(folder);
    }

    /**
     * Checks a folder chosen in a form. null = no folder.
     * Returns the folder ID to save; 404 if it is not this group's folder, 400 if it is archived.
     */
    public Long checkChosenFolder(String groupId, Long folderId) {
        if (folderId == null) {
            return null;
        }
        Folder folder = getFolderInGroup(folderId, groupId);
        if (folder.isArchived()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This folder is archived");
        }
        return folder.getFolderId();
    }

    /** Moves an event into a folder, or out of any folder with null. */
    public void moveEvent(String eventId, String groupId, Long folderId) {
        Event event = eventService.getEventInGroup(eventId, groupId);
        event.moveToFolder(checkChosenFolder(groupId, folderId));
        eventRepository.save(event);
    }
}
