package com.example.app.repository;

import com.example.app.entity.AssignmentHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AssignmentHistoryRepository extends JpaRepository<AssignmentHistory, Long> {

    List<AssignmentHistory> findByEquipmentId(Long equipmentId);

    Page<AssignmentHistory> findAllByOrderByChangeTimeDesc(Pageable pageable);

    /**
     * 一件器材的全部归属段，按区间开始时刻升序 —— 链式校验与区间查询共用。
     */
    List<AssignmentHistory> findByEquipmentIdOrderByValidFromAscIdAsc(Long equipmentId);

    /**
     * 老流水迁移按变更时刻升序重放。
     */
    List<AssignmentHistory> findByEquipmentIdOrderByChangeTimeAscIdAsc(Long equipmentId);

    /**
     * 一件器材当前仍在用的归属段（已推区间、且 valid_to 为空）。正常至多一条；
     * 多于一条即链已损坏，由链式校验挑出。老流水（valid_from 为空）不算段。
     */
    List<AssignmentHistory> findByEquipmentIdAndValidToIsNullAndValidFromIsNotNull(Long equipmentId);

    /**
     * 还有老流水（未推区间）的器材主键。
     */
    @Query("SELECT DISTINCT h.equipmentId FROM AssignmentHistory h WHERE h.validFrom IS NULL")
    List<Long> findEquipmentIdsWithUnmigratedHistory();
}
