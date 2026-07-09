package com.example.app.service;

import com.example.app.dto.TeamStatisticsDTO;
import com.example.app.entity.Team;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class StatisticsService {

    private final EquipmentService equipmentService;
    private final TeamService teamService;
    private final AssignmentService assignmentService;

    public StatisticsService(EquipmentService equipmentService, TeamService teamService, AssignmentService assignmentService) {
        this.equipmentService = equipmentService;
        this.teamService = teamService;
        this.assignmentService = assignmentService;
    }

    public Map<String, Object> getOverview() {
        Map<String, Object> overview = new HashMap<>();
        
        long totalEquipment = equipmentService.count();
        long totalTeam = teamService.count();
        
        double average = totalTeam > 0 ? (double) totalEquipment / totalTeam : 0;
        
        overview.put("totalEquipment", totalEquipment);
        overview.put("totalTeam", totalTeam);
        overview.put("averageEquipmentPerTeam", Math.round(average * 10) / 10.0);
        
        return overview;
    }

    public List<TeamStatisticsDTO> getTeamStatistics() {
        List<TeamStatisticsDTO> statistics = new ArrayList<>();
        
        List<Object[]> counts = assignmentService.getTeamEquipmentCounts();
        Map<Long, Long> countMap = new HashMap<>();
        
        for (Object[] row : counts) {
            Long teamId = (Long) row[0];
            Long count = (Long) row[1];
            countMap.put(teamId, count);
        }
        
        List<Team> teams = teamService.findAll();
        
        for (Team team : teams) {
            TeamStatisticsDTO dto = new TeamStatisticsDTO();
            dto.setTeamId(team.getId());
            dto.setTeamName(team.getTeamName());
            dto.setEquipmentCount(countMap.getOrDefault(team.getId(), 0L));
            statistics.add(dto);
        }
        
        return statistics;
    }
}