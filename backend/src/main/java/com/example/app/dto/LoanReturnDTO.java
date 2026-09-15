package com.example.app.dto;

/**
 * 器材回场归还入参。只有在外（OUT / OVERDUE）的离场单可归还。
 */
public class LoanReturnDTO {

    /** 归还经手人（门卫/班长），可空，默认“系统” */
    private String operator;

    /** 归还备注，可空，如“外观完好” */
    private String remark;

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
