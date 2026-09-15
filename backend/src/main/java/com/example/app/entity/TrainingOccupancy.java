package com.example.app.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 课目占用：班长把本班组名下器材挂到某个训练日的课目时段。
 * status=ACTIVE 有效；CANCELLED 表示已作废（改班组时由系统当场作废），
 * 作废记录保留痕迹，但不再参与有效占用展示与交叉判定。
 */
@Entity
@Table(name = "training_occupancy", indexes = {
        @Index(name = "idx_occupancy_equipment_day", columnList = "equipmentId,trainingDate"),
        @Index(name = "idx_occupancy_team_day", columnList = "teamId,trainingDate"),
        @Index(name = "idx_occupancy_status", columnList = "status")
})
public class TrainingOccupancy {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_CANCELLED = "CANCELLED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "equipment_id", nullable = false)
    private Long equipmentId;

    @Column(name = "team_id", nullable = false)
    private Long teamId;

    @Column(name = "training_date", nullable = false)
    private LocalDate trainingDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "course_name", length = 100)
    private String courseName;

    @Column(name = "operator", length = 50)
    private String operator;

    @Column(name = "status", nullable = false, length = 20)
    private String status = STATUS_ACTIVE;

    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    @Column(name = "cancel_time")
    private LocalDateTime cancelTime;

    @Column(name = "cancel_operator", length = 50)
    private String cancelOperator;

    @Column(name = "cancel_reason", length = 200)
    private String cancelReason;

    @Version
    @Column(name = "version")
    private Long version;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        if (status == null) {
            status = STATUS_ACTIVE;
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

    public LocalDate getTrainingDate() {
        return trainingDate;
    }

    public void setTrainingDate(LocalDate trainingDate) {
        this.trainingDate = trainingDate;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }

    public String getCourseName() {
        return courseName;
    }

    public void setCourseName(String courseName) {
        this.courseName = courseName;
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

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getCancelTime() {
        return cancelTime;
    }

    public void setCancelTime(LocalDateTime cancelTime) {
        this.cancelTime = cancelTime;
    }

    public String getCancelOperator() {
        return cancelOperator;
    }

    public void setCancelOperator(String cancelOperator) {
        this.cancelOperator = cancelOperator;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public void setCancelReason(String cancelReason) {
        this.cancelReason = cancelReason;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
