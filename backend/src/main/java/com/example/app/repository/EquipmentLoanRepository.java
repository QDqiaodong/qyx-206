package com.example.app.repository;

import com.example.app.entity.EquipmentLoan;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface EquipmentLoanRepository extends JpaRepository<EquipmentLoan, Long> {

    /**
     * 出门判定：在途（OUT/OVERDUE）未还的离场单，加锁读取。
     * 配合器材行锁，保证两个班长并发点同一件器材出门时串行判定，
     * 后落地者读到先落地者的在途单，被挡回去。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM EquipmentLoan l WHERE l.equipmentId = :equipmentId " +
            "AND l.status IN ('OUT', 'OVERDUE')")
    List<EquipmentLoan> findOpenByEquipmentForUpdate(@Param("equipmentId") Long equipmentId);

    Optional<EquipmentLoan> findFirstByEquipmentIdAndStatusInOrderByCheckoutTimeDesc(
            Long equipmentId, List<String> statuses);

    /**
     * 定时扫描：所有已到预计归还时刻仍 OUT 的在途单。
     */
    @Query("SELECT l FROM EquipmentLoan l WHERE l.status = 'OUT' " +
            "AND l.expectedReturnTime <= :now")
    List<EquipmentLoan> findDueForOverdue(@Param("now") LocalDateTime now);

    long countByStatus(String status);

    /**
     * 读时口径下“真正超期未还”的条数：已翻 OVERDUE，或 OUT 但已过点（扫描尚未跑到）。
     */
    @Query("SELECT COUNT(l) FROM EquipmentLoan l WHERE l.status = 'OVERDUE' " +
            "OR (l.status = 'OUT' AND l.expectedReturnTime <= :now)")
    long countEffectivelyOverdue(@Param("now") LocalDateTime now);

    /**
     * 列表查询。
     * @param openOnly true=只看在外（OUT/OVERDUE），false=全部历史
     * @param overdueOnly true=只看读时口径超期件（OVERDUE 或 OUT 已过点）
     */
    @Query("SELECT l FROM EquipmentLoan l WHERE " +
            "(:teamId IS NULL OR l.teamId = :teamId) AND " +
            "(:equipmentId IS NULL OR l.equipmentId = :equipmentId) AND " +
            "(:openOnly = false OR l.status IN ('OUT', 'OVERDUE')) AND " +
            "(:overdueOnly = false OR l.status = 'OVERDUE' " +
            "  OR (l.status = 'OUT' AND l.expectedReturnTime <= :now)) AND " +
            "(:returnedOnly = false OR l.status = 'RETURNED') " +
            "ORDER BY l.checkoutTime DESC, l.id DESC")
    Page<EquipmentLoan> findLoans(@Param("teamId") Long teamId,
                                  @Param("equipmentId") Long equipmentId,
                                  @Param("openOnly") boolean openOnly,
                                  @Param("overdueOnly") boolean overdueOnly,
                                  @Param("returnedOnly") boolean returnedOnly,
                                  @Param("now") LocalDateTime now,
                                  Pageable pageable);
}
