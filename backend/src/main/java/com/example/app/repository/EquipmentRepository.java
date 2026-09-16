package com.example.app.repository;

import com.example.app.entity.Equipment;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EquipmentRepository extends JpaRepository<Equipment, Long> {

    Optional<Equipment> findByEquipmentCode(String equipmentCode);

    /**
     * 器材行锁：课目占用挂载与归属调整共用同一把锁，串行化同一器材的并发操作。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Equipment e WHERE e.id = :id")
    Optional<Equipment> findByIdForUpdate(@Param("id") Long id);

    Page<Equipment> findByEquipmentCodeContainingOrTrainingPurposeContaining(String code, String purpose, Pageable pageable);

    @Query("SELECT e FROM Equipment e WHERE e.id NOT IN (SELECT a.equipmentId FROM Assignment a)")
    List<Equipment> findUnassigned();

    @Query("SELECT e FROM Equipment e WHERE e.id IN (SELECT a.equipmentId FROM Assignment a WHERE a.teamId = :teamId)")
    List<Equipment> findByTeamId(@Param("teamId") Long teamId);

    boolean existsByEquipmentCode(String equipmentCode);

    /**
     * 全部登记器材的主键：首页统计逐件对照在库可训口径用。
     */
    @Query("SELECT e.id FROM Equipment e")
    List<Long> findAllIds();
}