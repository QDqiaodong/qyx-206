package com.example.app.repository;

import com.example.app.entity.TrainingOccupancy;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TrainingOccupancyRepository extends JpaRepository<TrainingOccupancy, Long> {

    /**
     * 挂占用时的冲突判定查询：仅有效占用参与，作废记录不挡人。
     * 加锁读取，配合器材行锁保证并发下交叉判定可靠。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM TrainingOccupancy o WHERE o.equipmentId = :equipmentId " +
            "AND o.trainingDate = :trainingDate AND o.status = 'ACTIVE'")
    List<TrainingOccupancy> findActiveForConflict(@Param("equipmentId") Long equipmentId,
                                                  @Param("trainingDate") LocalDate trainingDate);

    List<TrainingOccupancy> findByEquipmentIdAndTrainingDateAndStatusOrderByStartTimeAsc(
            Long equipmentId, LocalDate trainingDate, String status);

    Optional<TrainingOccupancy> findByIdAndStatus(Long id, String status);

    @Query("SELECT o FROM TrainingOccupancy o WHERE o.equipmentId = :equipmentId " +
            "AND o.trainingDate >= :fromDate AND o.status = 'ACTIVE' ORDER BY o.trainingDate, o.startTime")
    List<TrainingOccupancy> findActiveByEquipmentFrom(@Param("equipmentId") Long equipmentId,
                                                      @Param("fromDate") LocalDate fromDate);

    long countByTeamIdAndTrainingDateAndStatus(Long teamId, LocalDate trainingDate, String status);

    /**
     * 首页统计口径：被“尚未结束”的有效课目占用占住的器材。
     * “尚未结束”与改班作废、外借拦截同一条线：训练日在今天之后，
     * 或今天但结束时刻晚于当前时刻。已作废记录不占数。
     */
    @Query("SELECT DISTINCT o.equipmentId FROM TrainingOccupancy o WHERE o.status = 'ACTIVE' " +
            "AND (o.trainingDate > :today OR (o.trainingDate = :today AND o.endTime > :nowTime))")
    List<Long> findEquipmentIdsWithNotEndedOccupancy(@Param("today") LocalDate today,
                                                     @Param("nowTime") LocalTime nowTime);

    @Query("SELECT o.teamId, COUNT(o) FROM TrainingOccupancy o " +
            "WHERE o.trainingDate = :trainingDate AND o.status = 'ACTIVE' GROUP BY o.teamId")
    List<Object[]> countActiveByTeamForDate(@Param("trainingDate") LocalDate trainingDate);

    @Query("SELECT o FROM TrainingOccupancy o WHERE o.trainingDate = :trainingDate " +
            "AND (:teamId IS NULL OR o.teamId = :teamId) " +
            "AND (:equipmentId IS NULL OR o.equipmentId = :equipmentId) " +
            "AND (:includeCancelled = true OR o.status = 'ACTIVE') " +
            "ORDER BY CASE o.status WHEN 'CANCELLED' THEN 1 ELSE 0 END, o.startTime, o.id")
    Page<TrainingOccupancy> findForDay(@Param("trainingDate") LocalDate trainingDate,
                                       @Param("teamId") Long teamId,
                                       @Param("equipmentId") Long equipmentId,
                                       @Param("includeCancelled") boolean includeCancelled,
                                       Pageable pageable);
}
