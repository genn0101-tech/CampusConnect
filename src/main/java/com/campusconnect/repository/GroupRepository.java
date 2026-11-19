package com.campusconnect.repository;

import com.campusconnect.model.Group;
import org.springframework.data.jpa.repository.JpaRepository;

// Spring Data writes the SQL for us: findAll(), findById(), save(), count(), ...
public interface GroupRepository extends JpaRepository<Group, String> {
}
