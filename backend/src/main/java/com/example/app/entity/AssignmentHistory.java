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

    @Column(name = "operator", length = 50)
    private String operator;

    @Column(name = "reason", length = 200)
    private String reason;

    @PrePersist
    protected void onCreate() {
        changeTime = LocalDateTime.now();
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