package com.campusconnect.repository;

import com.campusconnect.model.Invitation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InvitationRepository extends JpaRepository<Invitation, Long> {

    List<Invitation> findByStudentId(String studentId);

    List<Invitation> findByEventId(String eventId);
}
