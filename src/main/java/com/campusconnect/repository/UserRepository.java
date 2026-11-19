package com.campusconnect.repository;

import com.campusconnect.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserRepository extends JpaRepository<User, String> {

    // Spring Data builds the query from the method name: WHERE group_id = ?
    List<User> findByGroupId(String groupId);
}
