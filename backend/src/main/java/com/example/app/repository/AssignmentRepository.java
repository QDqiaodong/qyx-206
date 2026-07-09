package com.example.app.repository;

import com.example.app.entity.Assignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AssignmentRepository extends JpaRepository<Assignment, Long> {

    Optional<Assignment> findByEquipmentId(Long equipmentId);

    List<Assignment> findByTeamId(Long teamId);

    void deleteByEquipmentId(Long equipmentId);

    boolean existsByEquipmentId(Long equipmentId);

    @Query("SELECT a.teamId, COUNT(a) FROM Assignment a GROUP BY a.teamId")
    List<Object[]> countByTeamId();

    long countByTeamId(Long teamId);
}