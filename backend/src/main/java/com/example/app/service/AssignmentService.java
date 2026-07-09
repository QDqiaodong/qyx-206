package com.example.app.service;

import com.example.app.dto.AssignmentAdjustDTO;
import com.example.app.dto.AssignmentDTO;
import com.example.app.entity.Assignment;
import com.example.app.entity.AssignmentHistory;
import com.example.app.entity.Equipment;
import com.example.app.entity.Team;
import com.example.app.repository.AssignmentHistoryRepository;
import com.example.app.repository.AssignmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class AssignmentService {

    private final AssignmentRepository assignmentRepository;
    private final AssignmentHistoryRepository historyRepository;
    private final EquipmentService equipmentService;
    private final TeamService teamService;

    public AssignmentService(AssignmentRepository assignmentRepository, AssignmentHistoryRepository historyRepository, 
                             EquipmentService equipmentService, TeamService teamService) {
        this.assignmentRepository = assignmentRepository;
        this.historyRepository = historyRepository;
        this.equipmentService = equipmentService;
        this.teamService = teamService;
    }

    @Transactional
    public Assignment bind(AssignmentDTO dto) {
        if (assignmentRepository.existsByEquipmentId(dto.getEquipmentId())) {
            throw new RuntimeException("器材已绑定班组");
        }
        Assignment assignment = new Assignment();
        assignment.setEquipmentId(dto.getEquipmentId());
        assignment.setTeamId(dto.getTeamId());
        assignment.setOperator(dto.getOperator());
        assignment.setRemark(dto.getRemark());
        
        Assignment saved = assignmentRepository.save(assignment);
        
        AssignmentHistory history = new AssignmentHistory();
        history.setEquipmentId(dto.getEquipmentId());
        history.setOldTeamId(null);
        history.setNewTeamId(dto.getTeamId());
        history.setOperator(dto.getOperator());
        history.setReason("初始绑定");
        historyRepository.save(history);
        
        return saved;
    }

    @Transactional
    public Assignment adjust(AssignmentAdjustDTO dto) {
        Assignment existing = assignmentRepository.findByEquipmentId(dto.getEquipmentId())
                .orElseThrow(() -> new RuntimeException("器材未绑定班组"));
        
        if (existing.getTeamId().equals(dto.getNewTeamId())) {
            throw new RuntimeException("新班组与原班组相同");
        }
        
        Long oldTeamId = existing.getTeamId();
        existing.setTeamId(dto.getNewTeamId());
        existing.setOperator(dto.getOperator());
        
        Assignment saved = assignmentRepository.save(existing);
        
        AssignmentHistory history = new AssignmentHistory();
        history.setEquipmentId(dto.getEquipmentId());
        history.setOldTeamId(oldTeamId);
        history.setNewTeamId(dto.getNewTeamId());
        history.setOperator(dto.getOperator());
        history.setReason(dto.getReason());
        historyRepository.save(history);
        
        return saved;
    }

    public Page<Map<String, Object>> getHistory(Pageable pageable) {
        Page<AssignmentHistory> historyPage = historyRepository.findAllByOrderByChangeTimeDesc(pageable);
        
        return historyPage.map(history -> {
            Map<String, Object> result = new HashMap<>();
            result.put("id", history.getId());
            result.put("equipmentId", history.getEquipmentId());
            
            try {
                Equipment equipment = equipmentService.findById(history.getEquipmentId());
                result.put("equipmentCode", equipment.getEquipmentCode());
            } catch (Exception e) {
                result.put("equipmentCode", "已删除");
            }
            
            if (history.getOldTeamId() != null) {
                try {
                    Team oldTeam = teamService.findById(history.getOldTeamId());
                    result.put("oldTeamId", history.getOldTeamId());
                    result.put("oldTeamName", oldTeam.getTeamName());
                } catch (Exception e) {
                    result.put("oldTeamId", history.getOldTeamId());
                    result.put("oldTeamName", "已删除");
                }
            }
            
            try {
                Team newTeam = teamService.findById(history.getNewTeamId());
                result.put("newTeamId", history.getNewTeamId());
                result.put("newTeamName", newTeam.getTeamName());
            } catch (Exception e) {
                result.put("newTeamId", history.getNewTeamId());
                result.put("newTeamName", "已删除");
            }
            
            result.put("changeTime", history.getChangeTime());
            result.put("operator", history.getOperator());
            result.put("reason", history.getReason());
            
            return result;
        });
    }

    public List<Equipment> getTeamEquipments(Long teamId) {
        return equipmentService.findByTeamId(teamId);
    }

    public long countByTeamId(Long teamId) {
        return assignmentRepository.countByTeamId(teamId);
    }

    public List<Object[]> getTeamEquipmentCounts() {
        return assignmentRepository.countByTeamId();
    }
}