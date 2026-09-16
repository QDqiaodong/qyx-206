package com.example.app.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.LocalDate;

/**
 * 折返跑测验成绩保存：按 班组 + 测验日 定位唯一一行。
 * 第一次写是新增，当天改人数是带上 version 的整单更新。
 */
public class ShuttleRunScoreDTO {

    @NotNull(message = "测验日期不能为空")
    private LocalDate testDate;

    /** 应测人数：改小到低于已写下的合格人数时，服务端拒绝保存 */
    @NotNull(message = "应测人数不能为空")
    @PositiveOrZero(message = "应测人数不能为负")
    private Integer expectedCount;

    @NotNull(message = "合格人数不能为空")
    @PositiveOrZero(message = "合格人数不能为负")
    private Integer passedCount;

    private String operator;

    /** 打开成绩时读到的版本号，更新时原样回传；不传则按服务端当前版本兜底 */
    private Long version;

    public LocalDate getTestDate() {
        return testDate;
    }

    public void setTestDate(LocalDate testDate) {
        this.testDate = testDate;
    }

    public Integer getExpectedCount() {
        return expectedCount;
    }

    public void setExpectedCount(Integer expectedCount) {
        this.expectedCount = expectedCount;
    }

    public Integer getPassedCount() {
        return passedCount;
    }

    public void setPassedCount(Integer passedCount) {
        this.passedCount = passedCount;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
