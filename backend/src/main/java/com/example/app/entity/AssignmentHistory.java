package com.example.app.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "assignment_history", indexes = {
        @Index(name = "idx_equipment_id", columnList = "equipmentId"),
        @Index(name = "idx_change_time", columnList = "changeTime")
})
public class AssignmentHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "equipment_id", nullable = false)
    private Long equipmentId;

    @Column(name = "old_team_id")
    private Long oldTeamId;

    @Column(name = "new_team_id", nullable = false)
    private Long newTeamId;

    @Column(name = "change_time", updatable = false)
    private LocalDateTime changeTime;

    /**
     * 本段归属区间的开始时刻（含）。与同一器材上一段的 valid_to 严格相等，不留空档。
     */
    @Column(name = "valid_from")
    private LocalDateTime validFrom;

    /**
     * 本段归属区间的结束时刻（含边界交给下一段）。仍在用的最后一段为 NULL。
     */
    @Column(name = "valid_to")
    private LocalDateTime validTo;

    @Column(name = "operator", length = 50)
    private String operator;

    @Column(name = "reason", length = 200)
    private String reason;

    @PrePersist
    protected void onCreate() {
        // 老流水迁移会显式指定变更时刻，只在未指定时补当前时刻
        if (changeTime == null) {
            changeTime = LocalDateTime.now();
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

    public Long getOldTeamId() {
        return oldTeamId;
    }

    public void setOldTeamId(Long oldTeamId) {
        this.oldTeamId = oldTeamId;
    }

    public Long getNewTeamId() {
        return newTeamId;
    }

    public void setNewTeamId(Long newTeamId) {
        this.newTeamId = newTeamId;
    }

    public LocalDateTime getChangeTime() {
        return changeTime;
    }

    public void setChangeTime(LocalDateTime changeTime) {
        this.changeTime = changeTime;
    }

    public LocalDateTime getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(LocalDateTime validFrom) {
        this.validFrom = validFrom;
    }

    public LocalDateTime getValidTo() {
        return validTo;
    }

    public void setValidTo(LocalDateTime validTo) {
        this.validTo = validTo;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}