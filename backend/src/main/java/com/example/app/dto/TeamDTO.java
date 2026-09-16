package com.example.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class TeamDTO {

    private Long id;

    @NotBlank(message = "班组名称不能为空")
    private String teamName;

    @NotNull(message = "成员数量不能为空")
    private Integer memberCount;

    private String description;

    /**
     * 打开编辑页时读到的档案版本；提交时若库里版本已被别人顶新，
     * 本次整单保存拒绝（409），要求刷新后基于最新一版再改。
     * 为空时按服务端事务内读到的版本兜底校验。
     */
    private Long version;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTeamName() {
        return teamName;
    }

    public void setTeamName(String teamName) {
        this.teamName = teamName;
    }

    public Integer getMemberCount() {
        return memberCount;
    }

    public void setMemberCount(Integer memberCount) {
        this.memberCount = memberCount;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}