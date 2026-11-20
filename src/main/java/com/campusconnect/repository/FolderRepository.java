package com.campusconnect.repository;

import com.campusconnect.model.Folder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FolderRepository extends JpaRepository<Folder, Long> {

    List<Folder> findByGroupId(String groupId);
}
