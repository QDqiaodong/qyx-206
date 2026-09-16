package com.example.app.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "assignment", indexes = {
        @Index(name = "idx_assignment_equipment", columnList = "equipmentId"),
        @Index(name = "idx_assignment_team", columnList = "teamId")
})
public class Assignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "equipment_id", unique = true, nullable = false)
    private Long equipmentId;

    @Column(name = "team_id", nullable = false)
    private Long teamId;

    @Column(name = "bind_time", updatable = false)
    private LocalDateTime bindTime;

    @Column(name = "operator", length = 50)
    private String operator;

    @Column(name = "remark", length = 200)
    private String remark;

    @PrePersist
    protected void onCreate() {
        // 老流水迁移核对起点时允许显式指定，只在未指定时补当前时刻
        if (bindTime == null) {
            bindTime = LocalDateTime.now();
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

    public LocalDateTime getBindTime() {
        return bindTime;
    }

    public void setBindTime(LocalDateTime bindTime) {
        this.bindTime = bindTime;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}