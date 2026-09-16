package com.example.app.dto;

public class TeamStatisticsDTO {

    private Long teamId;
    private String teamName;
    /** 名下真正在库可训的器材件数（无未结束占用、无在外未还） */
    private Long equipmentCount;
    /** 名下归属器材总件数（含被占用/在外件，仅作对照） */
    private Long assignedCount;

    public Long getTeamId() {
        return teamId;
    }

    public void setTeamId(Long teamId) {
        this.teamId = teamId;
    }

    public String getTeamName() {
        return teamName;
    }

    public void setTeamName(String teamName) {
        this.teamName = teamName;
    }

    public Long getEquipmentCount() {
        return equipmentCount;
    }

    public void setEquipmentCount(Long equipmentCount) {
        this.equipmentCount = equipmentCount;
    }

    public Long getAssignedCount() {
        return assignedCount;
    }

    public void setAssignedCount(Long assignedCount) {
        this.assignedCount = assignedCount;
    }
}