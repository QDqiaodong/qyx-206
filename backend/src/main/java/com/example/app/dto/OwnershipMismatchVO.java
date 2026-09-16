package com.example.app.dto;

/**
 * 一条“当前归属 vs 流水末段”对不上的账。
 * 只挑出来列明是哪几件、两边各是什么，由人核对处置 —— 系统不替人把归属改过去。
 */
public class OwnershipMismatchVO {

    /** NO_INTERVAL=有归属单但没有任何区间；TEAM_MISMATCH=归属单与末段班组不一致；ORPHAN_INTERVAL=有在用段但无归属单 */
    private String type;
    private Long equipmentId;
    private String equipmentCode;
    private Long assignmentTeamId;
    private String assignmentTeamName;
    private Long segmentTeamId;
    private String segmentTeamName;
    private String detail;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
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

    public Long getAssignmentTeamId() {
        return assignmentTeamId;
    }

    public void setAssignmentTeamId(Long assignmentTeamId) {
        this.assignmentTeamId = assignmentTeamId;
    }

    public String getAssignmentTeamName() {
        return assignmentTeamName;
    }

    public void setAssignmentTeamName(String assignmentTeamName) {
        this.assignmentTeamName = assignmentTeamName;
    }

    public Long getSegmentTeamId() {
        return segmentTeamId;
    }

    public void setSegmentTeamId(Long segmentTeamId) {
        this.segmentTeamId = segmentTeamId;
    }

    public String getSegmentTeamName() {
        return segmentTeamName;
    }

    public void setSegmentTeamName(String segmentTeamName) {
        this.segmentTeamName = segmentTeamName;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }
}
