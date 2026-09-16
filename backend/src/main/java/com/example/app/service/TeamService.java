package com.example.app.service;

import com.example.app.dto.TeamDTO;
import com.example.app.entity.Team;
import com.example.app.repository.TeamRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@Slf4j
public class TeamService {

    private final TeamRepository teamRepository;

    public TeamService(TeamRepository teamRepository) {
        this.teamRepository = teamRepository;
    }

    public Page<Team> findAll(String keyword, Pageable pageable) {
        if (keyword != null && !keyword.isEmpty()) {
            return teamRepository.findByTeamNameContaining(keyword, pageable);
        }
        return teamRepository.findAll(pageable);
    }

    public Team findById(Long id) {
        return teamRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("班组不存在"));
    }

    @Transactional
    public Team create(TeamDTO dto) {
        if (teamRepository.existsByTeamName(dto.getTeamName())) {
            throw new RuntimeException("班组名称已存在");
        }
        Team team = new Team();
        team.setTeamName(dto.getTeamName());
        team.setMemberCount(dto.getMemberCount());
        team.setDescription(dto.getDescription());
        return teamRepository.save(team);
    }

    /**
     * 更新班组档案。
     *
     * 整单落库：名称、在编制、职责说明在同一条带版本条件的 UPDATE 里一起写，
     * 要么三个字段全是这一版、要么整单不进库，绝不允许编制来自一次保存、
     * 说明来自另一次保存的拼版。
     *
     * 两人几乎同时改同一个班：先提交者把 version 顶到下一号并拿到行写锁；
     * 后者的 UPDATE 在锁上排队，拿到锁后按当前已提交版本重判条件，
     * WHERE version 匹配 0 行，整单拒绝（409），库里留下的是先落库的完整一版。
     */
    @Transactional
    public Team update(Long id, TeamDTO dto) {
        Team current = findById(id);
        if (!Objects.equals(current.getTeamName(), dto.getTeamName()) &&
                teamRepository.existsByTeamName(dto.getTeamName())) {
            throw new RuntimeException("班组名称已存在");
        }

        // 优先按前端打开档案时读到的版本号比对；老客户端不带版本时，
        // 用本事务内读到的版本兜底 —— 两个并发事务同样读到旧号，
        // 后到的 UPDATE 条件仍会匹配 0 行。
        Long expectedVersion = dto.getVersion() != null ? dto.getVersion() : current.getVersion();

        String description = dto.getDescription();
        int updated = teamRepository.updateVersioned(
                id, dto.getTeamName(), dto.getMemberCount(), description, expectedVersion);
        if (updated == 0) {
            throw new ObjectOptimisticLockingFailureException(Team.class, id);
        }
        return findById(id);
    }

    @Transactional
    public void delete(Long id) {
        teamRepository.deleteById(id);
    }

    /**
     * 下拉/卡片读路径：始终实时查库，不落任何服务端缓存。
     * 班组档案那一版提交完成后，下一个请求（总览卡片、归属绑定下拉、
     * 课目占用、外借离场）拿到的就是同一版编制与说明，不存在“各页各记”。
     */
    @Transactional(readOnly = true)
    public List<Team> findAll() {
        return teamRepository.findAll();
    }

    public long count() {
        return teamRepository.count();
    }
}