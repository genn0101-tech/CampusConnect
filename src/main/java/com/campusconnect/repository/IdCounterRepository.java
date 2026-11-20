package com.campusconnect.repository;

import com.campusconnect.model.IdCounter;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdCounterRepository extends JpaRepository<IdCounter, String> {
}
