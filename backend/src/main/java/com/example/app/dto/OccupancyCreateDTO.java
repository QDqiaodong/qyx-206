package com.example.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

public class OccupancyCreateDTO {

    @NotNull(message = "器材ID不能为空")
    private Long equipmentId;

    /**
     * 班长所在班组；后端会校验该器材当前确在该班组名下
     */
    @NotNull(message = "班组ID不能为空")
    private Long teamId;

    @NotNull(message = "训练日期不能为空")
    private LocalDate trainingDate;

    @NotNull(message = "开始时间不能为空")
    private LocalTime startTime;

    @NotNull(message = "结束时间不能为空")
    private LocalTime endTime;

    @NotBlank(message = "课目名称不能为空")
    private String courseName;

    private String operator;

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
}
