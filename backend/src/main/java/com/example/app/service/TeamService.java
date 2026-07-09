package com.example.app.service;

import com.example.app.dto.TeamDTO;
import com.example.app.entity.Team;
import com.example.app.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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

    @Transactional
    public Team update(Long id, TeamDTO dto) {
        Team team = findById(id);
        if (!team.getTeamName().equals(dto.getTeamName()) &&
                teamRepository.existsByTeamName(dto.getTeamName())) {
            throw new RuntimeException("班组名称已存在");
        }
        team.setTeamName(dto.getTeamName());
        team.setMemberCount(dto.getMemberCount());
        team.setDescription(dto.getDescription());
        return teamRepository.save(team);
    }

    @Transactional
    public void delete(Long id) {
        teamRepository.deleteById(id);
    }

    public List<Team> findAll() {
        return teamRepository.findAll();
    }

    public long count() {
        return teamRepository.count();
    }
}