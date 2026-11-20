package com.campusconnect.controller;

import com.campusconnect.model.Event;
import com.campusconnect.model.Group;
import com.campusconnect.service.AttendanceService;
import com.campusconnect.service.EventService;
import com.campusconnect.service.GroupAccess;
import com.campusconnect.service.GroupService;
import com.campusconnect.service.ParticipantRow;
import com.campusconnect.service.QrCodeService;
import com.campusconnect.service.ReportService;
import com.google.zxing.WriterException;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Event detail page, QR code images, and CSV report. Only for the group that owns the event. */
@Controller
public class EventController {

    private final EventService eventService;
    private final GroupService groupService;
    private final AttendanceService attendanceService;
    private final ReportService reportService;
    private final QrCodeService qrCodeService;
    private final GroupAccess groupAccess;
    private final String baseUrl;

    public EventController(EventService eventService, GroupService groupService,
                           AttendanceService attendanceService, ReportService reportService,
                           QrCodeService qrCodeService, GroupAccess groupAccess,
                           @Value("${app.base-url}") String baseUrl) {
        this.eventService = eventService;
        this.groupService = groupService;
        this.attendanceService = attendanceService;
        this.reportService = reportService;
        this.qrCodeService = qrCodeService;
        this.groupAccess = groupAccess;
        this.baseUrl = baseUrl;
    }

    // ---------- Event detail ----------

    @GetMapping("/g/{groupId}/events/{eventId}")
    public String eventDetail(@PathVariable String groupId, @PathVariable String eventId,
                              HttpSession session, Model model) {
        Group group = groupService.getGroup(groupId);
        if (!groupAccess.isUnlocked(session, groupId)) {
            return "redirect:/g/" + groupId;
        }
        Event event = eventService.getEventInGroup(eventId, groupId);
        List<ParticipantRow> participants = attendanceService.participants(eventId);

        model.addAttribute("group", group);
        model.addAttribute("event", event);
        model.addAttribute("participants", participants);
        model.addAttribute("averageStay", attendanceService.averageStayMinutes(participants));
        model.addAttribute("checkInUrl", pageUrl(eventId, "checkin"));
        model.addAttribute("checkOutUrl", pageUrl(eventId, "checkout"));
        return "event-detail";
    }

    // ---------- QR codes ----------
    // Two QR codes per event: one for the entrance (check-in), one for the exit (check-out).
    // The images are only for the group, but the pages inside the QR codes are public.

    @GetMapping(value = "/g/{groupId}/events/{eventId}/qr-checkin.png", produces = MediaType.IMAGE_PNG_VALUE)
    @ResponseBody
    public byte[] checkInQrCode(@PathVariable String groupId, @PathVariable String eventId, HttpSession session)
            throws WriterException, IOException {
        requireUnlocked(session, groupId);
        eventService.getEventInGroup(eventId, groupId);
        return qrCodeService.toPng(pageUrl(eventId, "checkin"));
    }

    @GetMapping(value = "/g/{groupId}/events/{eventId}/qr-checkout.png", produces = MediaType.IMAGE_PNG_VALUE)
    @ResponseBody
    public byte[] checkOutQrCode(@PathVariable String groupId, @PathVariable String eventId, HttpSession session)
            throws WriterException, IOException {
        requireUnlocked(session, groupId);
        eventService.getEventInGroup(eventId, groupId);
        return qrCodeService.toPng(pageUrl(eventId, "checkout"));
    }

    // ---------- CSV report ----------

    @GetMapping("/g/{groupId}/events/{eventId}/report.csv")
    public ResponseEntity<byte[]> downloadReport(@PathVariable String groupId, @PathVariable String eventId,
                                                 HttpSession session) throws IOException {
        requireUnlocked(session, groupId);
        Group group = groupService.getGroup(groupId);
        Event event = eventService.getEventInGroup(eventId, groupId);

        List<ParticipantRow> participants = attendanceService.participants(eventId);
        String csv = reportService.buildCsv(event, participants,
                attendanceService.averageStayMinutes(participants));

        // The BOM (3 special bytes at the start) tells Excel the file is UTF-8,
        // so non-English names (e.g. Japanese) are shown correctly
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
        body.write(csv.getBytes(StandardCharsets.UTF_8));

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        reportService.contentDisposition(reportService.fileName(group, event)))
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body.toByteArray());
    }

    /** For non-HTML responses (image, CSV): answer "403 Forbidden" instead of redirecting. */
    private void requireUnlocked(HttpSession session, String groupId) {
        groupService.getGroup(groupId); // 404 if the group does not exist
        if (!groupAccess.isUnlocked(session, groupId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Group is locked");
        }
    }

    /** Full URL of a public event page, e.g. https://example.com/events/EVT001/checkin */
    private String pageUrl(String eventId, String page) {
        String base = baseUrl.isBlank()
                ? ServletUriComponentsBuilder.fromCurrentContextPath().toUriString() // URL the browser used
                : baseUrl;
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/events/" + eventId + "/" + page;
    }
}
