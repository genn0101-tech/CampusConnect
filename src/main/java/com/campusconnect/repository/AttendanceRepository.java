package com.campusconnect.repository;

import com.campusconnect.model.Attendance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    List<Attendance> findByEventId(String eventId);

    List<Attendance> findByStudentId(String studentId);

    // Optional = "maybe there is a record, maybe not"
    Optional<Attendance> findByEventIdAndStudentId(String eventId, String studentId);
}
