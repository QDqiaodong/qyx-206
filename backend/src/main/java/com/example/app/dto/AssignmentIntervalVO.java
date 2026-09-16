package com.example.app.dto;

import java.time.LocalDateTime;

/**
 * 一段归属区间：某件器材从 validFrom 到 validTo 归 teamName 手下。
 * validTo 为空表示这一段仍在用（当前归属所在段）。
 */
public class AssignmentIntervalVO {

    private Long historyId;
    private Long equipmentId;
    private String equipmentCode;
    private Long teamId;
    private String teamName;
    private LocalDateTime validFrom;
    private LocalDateTime validTo;
    /** 是否当前仍在用的最后一段 */
    private boolean current;
    /** 这一段待了多久，如 "3天4小时"；在用段算到当前时刻并标注“至今” */
    private String durationText;
    private String operator;
    private String reason;

    public Long getHistoryId() {
        return historyId;
    }

    public void setHistoryId(Long historyId) {
        this.historyId = historyId;
    }

    public Long getEquipmentId() {
        return equipmentId;
    }

    public void setEquipmentId(Long equipmentId) {
        this.equipmentId = equipmentId;
    }

    public String getEquipmentCode() {
        return equipmentCode;
    }

    public void setEquipmentCode(String equipmentCode) {
        this.equipmentCode = equipmentCode;
    }

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

    public boolean isCurrent() {
        return current;
    }

    public void setCurrent(boolean current) {
        this.current = current;
    }

    public String getDurationText() {
        return durationText;
    }

    public void setDurationText(String durationText) {
        this.durationText = durationText;
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
