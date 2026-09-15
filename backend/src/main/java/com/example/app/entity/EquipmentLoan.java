package com.example.app.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 器材外借离场：班长把本班组名下已归属器材拉出营区演练时的离场登记。
 *
 * status 流转：
 *   OUT     已出门、在外未还（含尚未到预计归还时刻的）
 *   OVERDUE 已超过预计归还时刻仍未还（由定时扫描从 OUT 翻成，留痕）
 *   RETURNED 已回场归还，闭环；同一件器材之后才能再开下一条离场
 *
 * 与课目占用两本账互不改动：离场只“读”占用做前置拦截，
 * 不去作废任何有效占用；归还/超期也不触碰占用表。
 */
@Entity
@Table(name = "equipment_loan", indexes = {
        @Index(name = "idx_loan_equipment", columnList = "equipmentId"),
        @Index(name = "idx_loan_team", columnList = "teamId"),
        @Index(name = "idx_loan_status", columnList = "status"),
        @Index(name = "idx_loan_expected_return", columnList = "expectedReturnTime")
})
public class EquipmentLoan {

    public static final String STATUS_OUT = "OUT";
    public static final String STATUS_OVERDUE = "OVERDUE";
    public static final String STATUS_RETURNED = "RETURNED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "equipment_id", nullable = false)
    private Long equipmentId;

    /**
     * 出门时器材所属班组的快照。出门后归属调整不改变这条离场账的班组。
     */
    @Column(name = "team_id", nullable = false)
    private Long teamId;

    @Column(name = "expected_return_time", nullable = false)
    private LocalDateTime expectedReturnTime;

    @Column(name = "companions", nullable = false, length = 500)
    private String companions;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Column(name = "operator", length = 50)
    private String operator;

    @Column(name = "status", nullable = false, length = 20)
    private String status = STATUS_OUT;

    @Column(name = "checkout_time", updatable = false)
    private LocalDateTime checkoutTime;

    @Column(name = "return_time")
    private LocalDateTime returnTime;

    @Column(name = "return_operator", length = 50)
    private String returnOperator;

    @Column(name = "return_remark", length = 200)
    private String returnRemark;

    /** 翻成超期的时刻，留痕用 */
    @Column(name = "overdue_time")
    private LocalDateTime overdueTime;

    @Version
    @Column(name = "version")
    private Long version;

    @PrePersist
    protected void onCreate() {
        checkoutTime = LocalDateTime.now();
        if (status == null) {
            status = STATUS_OUT;
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getEquipmentId() {
        return equipmentId;
    }

    public void setEquipmentId(Long equipmentId) {
        this.equipmentId = equipmentId;
    }

    public Long getTeamId() {
        return teamId;
    }

    public void setTeamId(Long teamId) {
        this.teamId = teamId;
    }

    public LocalDateTime getExpectedReturnTime() {
        return expectedReturnTime;
    }

    public void setExpectedReturnTime(LocalDateTime expectedReturnTime) {
        this.expectedReturnTime = expectedReturnTime;
    }

    public String getCompanions() {
        return companions;
    }

    public void setCompanions(String companions) {
        this.companions = companions;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCheckoutTime() {
        return checkoutTime;
    }

    public void setCheckoutTime(LocalDateTime checkoutTime) {
        this.checkoutTime = checkoutTime;
    }

    public LocalDateTime getReturnTime() {
        return returnTime;
    }

    public void setReturnTime(LocalDateTime returnTime) {
        this.returnTime = returnTime;
    }

    public String getReturnOperator() {
        return returnOperator;
    }

    public void setReturnOperator(String returnOperator) {
        this.returnOperator = returnOperator;
    }

    public String getReturnRemark() {
        return returnRemark;
    }

    public void setReturnRemark(String returnRemark) {
        this.returnRemark = returnRemark;
    }

    public LocalDateTime getOverdueTime() {
        return overdueTime;
    }

    public void setOverdueTime(LocalDateTime overdueTime) {
        this.overdueTime = overdueTime;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
