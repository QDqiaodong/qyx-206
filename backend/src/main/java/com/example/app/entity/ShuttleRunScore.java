package com.example.app.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 折返跑测验成绩：同一班组 + 同一测验日在库里只允许一行。
 * 教员纸上先记一版、当天又改人数时，走的是这同一行的更新，而不是再写一行。
 *
 * 班组卡片与训练基地总览上的合格率都不另存，一律按这一行的
 * 合格人数 / 应测人数实时重算，因此两处永远是同一本账。
 *
 * version 乐观锁：两个教员几乎同时改同一班组同一日的合格人数时，
 * 更新语句带 version 条件，先落库者 version+1，后落库者条件匹配 0 行被整单拒绝（409），
 * 不会把先写成功的人数盖掉。
 */
@Entity
@Table(name = "shuttle_run_score", uniqueConstraints = {
        @UniqueConstraint(name = "uk_shuttle_team_date", columnNames = {"team_id", "test_date"})
}, indexes = {
        @Index(name = "idx_shuttle_team_date", columnList = "teamId,testDate"),
        @Index(name = "idx_shuttle_date", columnList = "testDate")
})
public class ShuttleRunScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "team_id", nullable = false)
    private Long teamId;

    @Column(name = "test_date", nullable = false)
    private LocalDate testDate;

    /** 应测人数 */
    @Column(name = "expected_count", nullable = false)
    private Integer expectedCount;

    /** 合格人数：不得大于应测人数 */
    @Column(name = "passed_count", nullable = false)
    private Integer passedCount;

    @Column(name = "operator", length = 50)
    private String operator;

    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @Version
    @Column(name = "version")
    private Long version = 0L;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }

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

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
