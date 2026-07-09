package com.example.app.repository;

import com.example.app.entity.AssignmentHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AssignmentHistoryRepository extends JpaRepository<AssignmentHistory, Long> {

    List<AssignmentHistory> findByEquipmentId(Long equipmentId);

    Page<AssignmentHistory> findAllByOrderByChangeTimeDesc(Pageable pageable);
}