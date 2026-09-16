package com.example.app.dto;

import java.time.LocalDate;

/**
 * 折返跑测验成绩展示：应测、合格与按当前人数实时算出的合格率。
 * 班组卡片与训练基地总览共用同一口径（合格 / 应测），两处数字一致。
 * passRate 为百分比数值（0-100，保留 1 位小数）；当天无成绩时各计数字段为 null。
 */
public class ShuttleRunScoreVO {

    private Long id;
    private Long teamId;
    private String teamName;
    private LocalDate testDate;
    private Integer expectedCount;
    private Integer passedCount;
    /** 合格率（百分比，保留 1 位小数）；当天未记成绩时为 null */
    private Double passRate;
    private String operator;
    private Long version;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public Double getPassRate() {
        return passRate;
    }

    public void setPassRate(Double passRate) {
        this.passRate = passRate;
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
