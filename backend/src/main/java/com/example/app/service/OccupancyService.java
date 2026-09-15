package com.example.app.service;

import com.example.app.dto.OccupancyCreateDTO;
import com.example.app.dto.OccupancyVO;
import com.example.app.entity.Assignment;
import com.example.app.entity.Equipment;
import com.example.app.entity.Team;
import com.example.app.entity.TrainingOccupancy;
import com.example.app.exception.BusinessException;
import com.example.app.repository.AssignmentRepository;
import com.example.app.repository.EquipmentRepository;
import com.example.app.repository.TeamRepository;
import com.example.app.repository.TrainingOccupancyRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class OccupancyService {

    private final TrainingOccupancyRepository occupancyRepository;
    private final AssignmentRepository assignmentRepository;
    private final EquipmentRepository equipmentRepository;
    private final TeamRepository teamRepository;

    public OccupancyService(TrainingOccupancyRepository occupancyRepository,
                            AssignmentRepository assignmentRepository,
                            EquipmentRepository equipmentRepository,
                            TeamRepository teamRepository) {
        this.occupancyRepository = occupancyRepository;
        this.assignmentRepository = assignmentRepository;
        this.equipmentRepository = equipmentRepository;
        this.teamRepository = teamRepository;
    }

    /**
     * 班长挂课目占用。
     * 并发控制：先对器材行加 FOR UPDATE 行锁，同一件器材的并发挂载在此排队，
     * 后落地者拿到锁后能读到先落地者已提交的占用，再做交叉判定 ——
     * 既不会两条都生效，也不会把先落地的挤掉。
     */
    @Transactional
    public TrainingOccupancy create(OccupancyCreateDTO dto) {
        if (dto.getStartTime() == null || dto.getEndTime() == null
                || !dto.getEndTime().isAfter(dto.getStartTime())) {
            throw new BusinessException("结束时间必须晚于开始时间");
        }

        // 1) 锁器材行：与归属调整、其它挂载请求互斥
        Equipment equipment = equipmentRepository.findByIdForUpdate(dto.getEquipmentId())
                .orElseThrow(() -> new BusinessException("器材不存在"));

        if (teamRepository.findById(dto.getTeamId()).isEmpty()) {
            throw new BusinessException("班组不存在");
        }

        // 2) 只能挂自己班组名下已经归属的器材
        Assignment ownership = assignmentRepository.findByEquipmentId(equipment.getId())
                .orElseThrow(() -> new BusinessException("器材尚未归属班组，无法挂课目占用"));
        if (!ownership.getTeamId().equals(dto.getTeamId())) {
            throw new BusinessException("只能将本班组名下的器材挂到课目时段");
        }

        // 3) 交叉判定：只有 ACTIVE 占用参与，作废记录不挡人
        List<TrainingOccupancy> activeOnDay =
                occupancyRepository.findActiveForConflict(equipment.getId(), dto.getTrainingDate());
        for (TrainingOccupancy existing : activeOnDay) {
            if (overlaps(existing.getStartTime(), existing.getEndTime(),
                    dto.getStartTime(), dto.getEndTime())) {
                throw new BusinessException("该器材当天已有时间交叉的有效占用，后挂的占用不生效");
            }
        }

        TrainingOccupancy occupancy = new TrainingOccupancy();
        occupancy.setEquipmentId(equipment.getId());
        occupancy.setTeamId(dto.getTeamId());
        occupancy.setTrainingDate(dto.getTrainingDate());
        occupancy.setStartTime(dto.getStartTime());
        occupancy.setEndTime(dto.getEndTime());
        occupancy.setCourseName(dto.getCourseName());
        occupancy.setOperator(dto.getOperator());
        occupancy.setStatus(TrainingOccupancy.STATUS_ACTIVE);
        return occupancyRepository.save(occupancy);
    }

    /**
     * 器材改班组时调用（与归属调整同一事务）：当场作废该件所有“尚未结束”的有效占用，
     * 并留下谁在何时因改班作废的痕迹。已经结束的历史占用不动。
     */
    @Transactional
    public void cancelOnTeamTransfer(Long equipmentId, String operator, String transferReason) {
        LocalDate today = LocalDate.now();
        LocalTime nowTime = LocalTime.now();
        LocalDateTime now = LocalDateTime.now();

        List<TrainingOccupancy> activeFromToday =
                occupancyRepository.findActiveByEquipmentFrom(equipmentId, today);

        String cancelOperator = (operator == null || operator.isBlank()) ? "系统" : operator;
        String cancelReason = "器材改归属班组，未结束占用当场作废"
                + (transferReason != null && !transferReason.isBlank() ? "（改班原因：" + transferReason + "）" : "");

        int cancelled = 0;
        for (TrainingOccupancy occupancy : activeFromToday) {
            boolean notEnded = occupancy.getTrainingDate().isAfter(today)
                    || (occupancy.getTrainingDate().isEqual(today) && occupancy.getEndTime().isAfter(nowTime));
            if (!notEnded) {
                continue;
            }
            occupancy.setStatus(TrainingOccupancy.STATUS_CANCELLED);
            occupancy.setCancelTime(now);
            occupancy.setCancelOperator(cancelOperator);
            occupancy.setCancelReason(cancelReason);
            cancelled++;
        }
        occupancyRepository.saveAll(activeFromToday);
        log.info("器材 {} 改班组，作废未结束课目占用 {} 条，操作人 {}", equipmentId, cancelled, cancelOperator);
    }

    public Page<OccupancyVO> listForDay(LocalDate trainingDate, Long teamId, Long equipmentId,
                                        boolean includeCancelled, Pageable pageable) {
        Page<TrainingOccupancy> page = occupancyRepository.findForDay(
                trainingDate, teamId, equipmentId, includeCancelled, pageable);
        return page.map(this::toVO);
    }

    public List<OccupancyVO> listActiveForEquipment(Long equipmentId, LocalDate trainingDate) {
        return occupancyRepository
                .findByEquipmentIdAndTrainingDateAndStatusOrderByStartTimeAsc(
                        equipmentId, trainingDate, TrainingOccupancy.STATUS_ACTIVE)
                .stream()
                .map(this::toVO)
                .toList();
    }

    /**
     * 班组当天有效占用条数（作废不计入）。
     */
    public long countActiveForTeam(Long teamId, LocalDate trainingDate) {
        return occupancyRepository.countByTeamIdAndTrainingDateAndStatus(
                teamId, trainingDate, TrainingOccupancy.STATUS_ACTIVE);
    }

    /**
     * 只读判定：该件器材是否存在“尚未结束”的有效课目占用。
     * 供器材外借离场做前置拦截 —— 占用归占用、出门归出门，
     * 离场侧只读这本账，不作废、不改时段、不回写任何占用数据。
     * “尚未结束”口径与改班作废一致：训练日在今天之后，或今天但结束时刻晚于当前时刻。
     */
    public boolean hasActiveNotEndedOccupancy(Long equipmentId) {
        LocalDate today = LocalDate.now();
        LocalTime nowTime = LocalTime.now();
        List<TrainingOccupancy> activeFromToday =
                occupancyRepository.findActiveByEquipmentFrom(equipmentId, today);
        return activeFromToday.stream().anyMatch(o ->
                o.getTrainingDate().isAfter(today)
                        || (o.getTrainingDate().isEqual(today) && o.getEndTime().isAfter(nowTime)));
    }

    public List<Map<String, Object>> countActiveByTeam(LocalDate trainingDate) {
        return occupancyRepository.countActiveByTeamForDate(trainingDate).stream()
                .map(row -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("teamId", row[0]);
                    item.put("activeCount", row[1]);
                    return item;
                })
                .toList();
    }

    /**
     * 半开区间交叉判定：[s1,e1) 与 [s2,e2) 首尾相接不算交叉。
     */
    private boolean overlaps(LocalTime s1, LocalTime e1, LocalTime s2, LocalTime e2) {
        return s2.isBefore(e1) && s1.isBefore(e2);
    }

    private OccupancyVO toVO(TrainingOccupancy o) {
        OccupancyVO vo = new OccupancyVO();
        vo.setId(o.getId());
        vo.setEquipmentId(o.getEquipmentId());
        vo.setTeamId(o.getTeamId());
        vo.setTrainingDate(o.getTrainingDate());
        vo.setStartTime(o.getStartTime());
        vo.setEndTime(o.getEndTime());
        vo.setCourseName(o.getCourseName());
        vo.setOperator(o.getOperator());
        vo.setStatus(o.getStatus());
        vo.setCreateTime(o.getCreateTime());
        vo.setCancelTime(o.getCancelTime());
        vo.setCancelOperator(o.getCancelOperator());
        vo.setCancelReason(o.getCancelReason());

        try {
            Equipment equipment = equipmentRepository.findById(o.getEquipmentId())
                    .orElseThrow(() -> new RuntimeException("器材已删除"));
            vo.setEquipmentCode(equipment.getEquipmentCode());
            vo.setTrainingPurpose(equipment.getTrainingPurpose());
            vo.setSizeSpec(equipment.getSizeSpec());
        } catch (Exception e) {
            vo.setEquipmentCode("已删除");
        }

        try {
            Team team = teamRepository.findById(o.getTeamId())
                    .orElseThrow(() -> new RuntimeException("班组已删除"));
            vo.setTeamName(team.getTeamName());
        } catch (Exception e) {
            vo.setTeamName("已删除");
        }
        return vo;
    }
}
