package com.example.app.service;

import com.example.app.dto.TeamStatisticsDTO;
import com.example.app.entity.Assignment;
import com.example.app.entity.Team;
import com.example.app.repository.EquipmentLoanRepository;
import com.example.app.repository.TrainingOccupancyRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Slf4j
public class StatisticsService {

    private final EquipmentService equipmentService;
    private final TeamService teamService;
    private final AssignmentService assignmentService;
    private final TrainingOccupancyRepository occupancyRepository;
    private final EquipmentLoanRepository loanRepository;

    public StatisticsService(EquipmentService equipmentService, TeamService teamService,
                             AssignmentService assignmentService,
                             TrainingOccupancyRepository occupancyRepository,
                             EquipmentLoanRepository loanRepository) {
        this.equipmentService = equipmentService;
        this.teamService = teamService;
        this.assignmentService = assignmentService;
        this.occupancyRepository = occupancyRepository;
        this.loanRepository = loanRepository;
    }

    /**
     * 首页统计概览。
     *
     * 口径：器材数只数“真正在库、能拉出去训练”的件 —— 库里登记在册，
     * 且没有尚未结束的有效课目占用，也没有在外未还（OUT/OVERDUE）的离场单。
     * 同一件器材被占用账和外借账同时占住时只剔一次（两本账取并集）。
     *
     * 新鲜度：整个方法包在同一个只读事务里取数（一致快照、不落任何缓存），
     * 占用刚挂上 / 外借刚出门提交后，下一个请求立刻读到掉数后的结果；
     * 两个班长几乎同时打开首页，先出门成功的一方提交后，另一方刷新看到的就是新均数。
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getOverview() {
        Set<Long> unavailableIds = findUnavailableEquipmentIds();

        long registeredEquipment = equipmentService.count();
        long availableEquipment = countAvailableEquipment(unavailableIds);
        long totalTeam = teamService.count();

        double average = totalTeam > 0 ? (double) availableEquipment / totalTeam : 0;

        Map<String, Object> overview = new HashMap<>();
        // 在库可训器材数（首页“器材总数”卡片与班均都按这个口径算）
        overview.put("totalEquipment", availableEquipment);
        // 库里登记总件数，仅作对照展示，不参与均数
        overview.put("registeredEquipment", registeredEquipment);
        // 被未结束占用 / 在外未还占住、暂不可训的件数
        overview.put("unavailableEquipment", registeredEquipment - availableEquipment);
        overview.put("totalTeam", totalTeam);
        overview.put("averageEquipmentPerTeam", Math.round(average * 10) / 10.0);
        return overview;
    }

    /**
     * 班组侧统计。
     * equipmentCount 只数该班组名下“真正在库可训”的器材：归属在本班组，
     * 且没有尚未结束的有效占用、也没有在外未还；assignedCount 是名下归属总件数，仅作对照。
     */
    @Transactional(readOnly = true)
    public List<TeamStatisticsDTO> getTeamStatistics() {
        Set<Long> unavailableIds = findUnavailableEquipmentIds();

        // 逐归属单过一遍：同一件被占用和外借同时占住时，unavailableIds 是并集，只剔一次
        Map<Long, Long> assignedCountMap = new HashMap<>();
        Map<Long, Long> availableCountMap = new HashMap<>();
        for (Assignment assignment : assignmentService.findAllAssignments()) {
            assignedCountMap.merge(assignment.getTeamId(), 1L, Long::sum);
            if (!unavailableIds.contains(assignment.getEquipmentId())) {
                availableCountMap.merge(assignment.getTeamId(), 1L, Long::sum);
            }
        }

        List<TeamStatisticsDTO> statistics = new ArrayList<>();
        for (Team team : teamService.findAll()) {
            TeamStatisticsDTO dto = new TeamStatisticsDTO();
            dto.setTeamId(team.getId());
            dto.setTeamName(team.getTeamName());
            dto.setEquipmentCount(availableCountMap.getOrDefault(team.getId(), 0L));
            dto.setAssignedCount(assignedCountMap.getOrDefault(team.getId(), 0L));
            statistics.add(dto);
        }
        return statistics;
    }

    /**
     * 暂不可训器材并集：挂了尚未结束的有效课目占用，或在外未还（OUT/OVERDUE）。
     * “尚未结束”与改班作废、外借拦截同一条线：训练日在今天之后，或今天但结束时刻晚于当前时刻。
     */
    private Set<Long> findUnavailableEquipmentIds() {
        LocalDate today = LocalDate.now();
        LocalTime nowTime = LocalTime.now();
        Set<Long> ids = new HashSet<>(
                occupancyRepository.findEquipmentIdsWithNotEndedOccupancy(today, nowTime));
        ids.addAll(loanRepository.findEquipmentIdsWithOpenLoan());
        return ids;
    }

    /**
     * 在库可训件数：逐件对照登记器材，剔除被占用/在外占住的。
     * 按器材主表逐件数，被两本账同时占住的同一件只剔一次，也不会把已删除器材的残留账算进来。
     */
    private long countAvailableEquipment(Set<Long> unavailableIds) {
        if (unavailableIds.isEmpty()) {
            return equipmentService.count();
        }
        long available = 0;
        for (Long equipmentId : equipmentService.findAllIds()) {
            if (!unavailableIds.contains(equipmentId)) {
                available++;
            }
        }
        return available;
    }
}
