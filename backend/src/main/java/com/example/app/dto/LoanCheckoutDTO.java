package com.example.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * 器材外借离场登记入参。
 * 预计归还时刻、同行人、离场事由缺一项都不能出门（注解 + 服务内双重校验）。
 */
public class LoanCheckoutDTO {

    @NotNull(message = "器材ID不能为空")
    private Long equipmentId;

    /**
     * 班长所在班组；后端会校验该器材当前确在该班组名下、且已归属。
     */
    @NotNull(message = "班组ID不能为空")
    private Long teamId;

    @NotNull(message = "预计归还时刻不能为空")
    private LocalDateTime expectedReturnTime;

    @NotBlank(message = "同行人不能为空")
    private String companions;

    @NotBlank(message = "离场事由不能为空")
    private String reason;

    /** 登记出门的班长 */
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
}
