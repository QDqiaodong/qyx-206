package com.example.app.repository;

import com.example.app.entity.Equipment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EquipmentRepository extends JpaRepository<Equipment, Long> {

    Optional<Equipment> findByEquipmentCode(String equipmentCode);

    Page<Equipment> findByEquipmentCodeContainingOrTrainingPurposeContaining(String code, String purpose, Pageable pageable);

    @Query("SELECT e FROM Equipment e WHERE e.id NOT IN (SELECT a.equipmentId FROM Assignment a)")
    List<Equipment> findUnassigned();

    @Query("SELECT e FROM Equipment e WHERE e.id IN (SELECT a.equipmentId FROM Assignment a WHERE a.teamId = :teamId)")
    List<Equipment> findByTeamId(@Param("teamId") Long teamId);

    boolean existsByEquipmentCode(String equipmentCode);
}