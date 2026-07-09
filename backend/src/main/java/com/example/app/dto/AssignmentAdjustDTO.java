package com.example.app.dto;

import jakarta.validation.constraints.NotNull;

public class AssignmentAdjustDTO {

    @NotNull(message = "器材ID不能为空")
    private Long equipmentId;

    @NotNull(message = "新班组ID不能为空")
    private Long newTeamId;

    private String operator;

    private String reason;

    public Long getEquipmentId() {
        return equipmentId;
    }

    public void setEquipmentId(Long equipmentId) {
        this.equipmentId = equipmentId;
    }

    public Long getNewTeamId() {
        return newTeamId;
    }

    public void setNewTeamId(Long newTeamId) {
        this.newTeamId = newTeamId;
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