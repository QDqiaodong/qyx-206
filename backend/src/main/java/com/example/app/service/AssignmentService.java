package com.example.app.service;

import com.example.app.dto.AssignmentAdjustDTO;
import com.example.app.dto.AssignmentDTO;
import com.example.app.dto.AssignmentIntervalVO;
import com.example.app.dto.IntervalMigrationResult;
import com.example.app.dto.OwnershipMismatchVO;
import com.example.app.entity.Assignment;
import com.example.app.entity.AssignmentHistory;
import com.example.app.entity.Equipment;
import com.example.app.entity.Team;
import com.example.app.exception.BusinessException;
import com.example.app.repository.AssignmentHistoryRepository;
import com.example.app.repository.AssignmentRepository;
import com.example.app.repository.EquipmentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Slf4j
public class AssignmentService {

    private final AssignmentRepository assignmentRepository;
    private final AssignmentHistoryRepository historyRepository;
    private final EquipmentRepository equipmentRepository;
    private final EquipmentService equipmentService;
    private final TeamService teamService;
    private final OccupancyService occupancyService;

    public AssignmentService(AssignmentRepository assignmentRepository, AssignmentHistoryRepository historyRepository,
                             EquipmentRepository equipmentRepository,
                             EquipmentService equipmentService, TeamService teamService,
                             OccupancyService occupancyService) {
        this.assignmentRepository = assignmentRepository;
        this.historyRepository = historyRepository;
        this.equipmentRepository = equipmentRepository;
        this.equipmentService = equipmentService;
        this.teamService = teamService;
        this.occupancyService = occupancyService;
    }

    @Transactional
    public Assignment bind(AssignmentDTO dto) {
        // 锁器材行，与课目占用挂载互斥
        equipmentRepository.findByIdForUpdate(dto.getEquipmentId())
                .orElseThrow(() -> new BusinessException("器材不存在"));
        if (assignmentRepository.existsByEquipmentId(dto.getEquipmentId())) {
            throw new BusinessException("器材已绑定班组");
        }
        Assignment assignment = new Assignment();
        assignment.setEquipmentId(dto.getEquipmentId());
        assignment.setTeamId(dto.getTeamId());
        assignment.setOperator(dto.getOperator());
        assignment.setRemark(dto.getRemark());

        Assignment saved = assignmentRepository.save(assignment);

        // 首段归属区间：从绑定时刻起，开口（valid_to 空）表示在用
        LocalDateTime now = LocalDateTime.now();
        AssignmentHistory history = new AssignmentHistory();
        history.setEquipmentId(dto.getEquipmentId());
        history.setOldTeamId(null);
        history.setNewTeamId(dto.getTeamId());
        history.setChangeTime(now);
        history.setValidFrom(now);
        history.setValidTo(null);
        history.setOperator(dto.getOperator());
        history.setReason("初始绑定");
        historyRepository.save(history);

        verifyChain(dto.getEquipmentId());
        return saved;
    }

    @Transactional
    public Assignment adjust(AssignmentAdjustDTO dto) {
        // 锁器材行：与课目占用挂载互斥，保证“改班当场作废”与新挂载不会交错；
        // 两人同时改同一件器材在此排队，拿到锁时读到的是对方已提交的最新链，
        // 因此同一件器材一次只能落一段，段与段不会压在一起。
        equipmentRepository.findByIdForUpdate(dto.getEquipmentId())
                .orElseThrow(() -> new BusinessException("器材不存在"));

        Assignment existing = assignmentRepository.findByEquipmentId(dto.getEquipmentId())
                .orElseThrow(() -> new BusinessException("器材未绑定班组"));

        if (existing.getTeamId().equals(dto.getNewTeamId())) {
            throw new BusinessException("新班组与原班组相同");
        }

        // 当前在用段必须唯一，且末段班组必须就是归属单上的班组 ——
        // 对不上说明账已经乱了，拒绝在错账上继续记，去对账清单里挑出来给人看。
        List<AssignmentHistory> openSegments =
                historyRepository.findByEquipmentIdAndValidToIsNullAndValidFromIsNotNull(dto.getEquipmentId());
        if (openSegments.isEmpty()) {
            throw new BusinessException("归属区间缺少在用段（老流水可能尚未推区间），请先执行老流水迁移");
        }
        if (openSegments.size() > 1) {
            throw new BusinessException("归属区间链已损坏：存在多段在用区间，已列入对账清单，请先人工核对");
        }
        AssignmentHistory openSegment = openSegments.get(0);
        if (!openSegment.getNewTeamId().equals(existing.getTeamId())) {
            throw new BusinessException("当前归属与流水末段对不上，已列入对账清单，请先人工核对");
        }

        // 前一段结束的那一刻就是后一段开始的那一刻：两段共用同一个时间戳
        LocalDateTime now = LocalDateTime.now();
        Long oldTeamId = existing.getTeamId();

        openSegment.setValidTo(now);
        historyRepository.save(openSegment);

        AssignmentHistory history = new AssignmentHistory();
        history.setEquipmentId(dto.getEquipmentId());
        history.setOldTeamId(oldTeamId);
        history.setNewTeamId(dto.getNewTeamId());
        history.setChangeTime(now);
        history.setValidFrom(now);
        history.setValidTo(null);
        history.setOperator(dto.getOperator());
        history.setReason(dto.getReason());
        historyRepository.save(history);

        existing.setTeamId(dto.getNewTeamId());
        existing.setOperator(dto.getOperator());
        Assignment saved = assignmentRepository.save(existing);

        // 落库前整体校一遍链：相邻段首尾相接、不留空档、不叠压，只有末段开口
        verifyChain(dto.getEquipmentId());

        // 归属调整照常通过，不拦截；但该件尚未结束的课目占用必须当场作废并留痕
        occupancyService.cancelOnTeamTransfer(dto.getEquipmentId(), dto.getOperator(), dto.getReason());

        return saved;
    }

    /**
     * 链式校验：一件器材的全部归属段按起点排序后，
     * 前一段的 valid_to 必须等于后一段的 valid_from（不留空档也不叠压），
     * 且只有最后一段开口（valid_to 空）。任何一处接不上直接抛错，
     * 写路径靠事务回滚保证断链不落库。
     */
    public void verifyChain(Long equipmentId) {
        List<AssignmentHistory> segments =
                historyRepository.findByEquipmentIdOrderByValidFromAscIdAsc(equipmentId);
        List<String> problems = new ArrayList<>();

        if (segments.isEmpty()) {
            throw new BusinessException("器材 " + equipmentId + " 没有任何归属区间");
        }
        for (int i = 0; i < segments.size(); i++) {
            AssignmentHistory seg = segments.get(i);
            String label = "流水#" + seg.getId();
            if (seg.getValidFrom() == null) {
                problems.add(label + " 还没有区间起点（老流水未迁移），请先执行老流水迁移");
                continue;
            }
            if (seg.getValidTo() != null && !seg.getValidTo().isAfter(seg.getValidFrom())) {
                problems.add(label + " 区间结束不晚于开始");
            }
            if (i < segments.size() - 1 && seg.getValidTo() == null) {
                problems.add(label + " 不是末段却开口（valid_to 为空），与后段叠压");
            }
            if (i > 0) {
                AssignmentHistory prev = segments.get(i - 1);
                if (prev.getValidTo() != null && !prev.getValidTo().equals(seg.getValidFrom())) {
                    problems.add(label + " 与上一段接不上（上一段终于 " + prev.getValidTo()
                            + "，本段始于 " + seg.getValidFrom() + "）");
                }
            }
        }
        AssignmentHistory last = segments.get(segments.size() - 1);
        if (last.getValidTo() != null) {
            problems.add("末段流水#" + last.getId() + " 已闭口，当前归属落不到任何一段上");
        }
        if (!problems.isEmpty()) {
            throw new BusinessException("器材 " + equipmentId + " 归属区间链断裂："
                    + String.join("；", problems));
        }
    }

    /**
     * 一件器材的归属区间时间线：每段归哪个班组、从何时到何时、待了多久。
     */
    public List<AssignmentIntervalVO> getEquipmentIntervals(Long equipmentId) {
        Equipment equipment;
        try {
            equipment = equipmentService.findById(equipmentId);
        } catch (Exception e) {
            throw new BusinessException("器材不存在");
        }
        List<AssignmentHistory> segments =
                historyRepository.findByEquipmentIdOrderByValidFromAscIdAsc(equipmentId);
        LocalDateTime now = LocalDateTime.now();
        List<AssignmentIntervalVO> result = new ArrayList<>();
        for (int i = 0; i < segments.size(); i++) {
            AssignmentHistory seg = segments.get(i);
            if (seg.getValidFrom() == null) {
                // 老流水还没推区间，不是一段归属区间，不混进时间线（去「对账迁移」处理）
                continue;
            }
            AssignmentIntervalVO vo = new AssignmentIntervalVO();
            vo.setHistoryId(seg.getId());
            vo.setEquipmentId(equipmentId);
            vo.setEquipmentCode(equipment.getEquipmentCode());
            vo.setTeamId(seg.getNewTeamId());
            vo.setTeamName(resolveTeamName(seg.getNewTeamId()));
            vo.setValidFrom(seg.getValidFrom());
            vo.setValidTo(seg.getValidTo());
            boolean current = seg.getValidTo() == null && i == segments.size() - 1;
            vo.setCurrent(current);
            vo.setDurationText(durationText(seg.getValidFrom(),
                    seg.getValidTo() != null ? seg.getValidTo() : now));
            vo.setOperator(seg.getOperator());
            vo.setReason(seg.getReason());
            result.add(vo);
        }
        return result;
    }

    /**
     * 对账：逐件核对“归属单上的当前班组”与“流水最后一段（在用段）的班组”。
     * 只把对不上的挑出来列明，绝不替人把归属改过去。
     */
    public List<OwnershipMismatchVO> findOwnershipMismatches() {
        List<Assignment> assignments = assignmentRepository.findAll();
        List<AssignmentHistory> allHistory = historyRepository.findAll();

        Map<Long, List<AssignmentHistory>> openByEquipment = new HashMap<>();
        Set<Long> hasAnySegment = new HashSet<>();
        for (AssignmentHistory h : allHistory) {
            if (h.getValidFrom() != null) {
                hasAnySegment.add(h.getEquipmentId());
            }
            if (h.getValidTo() == null && h.getValidFrom() != null) {
                openByEquipment.computeIfAbsent(h.getEquipmentId(), k -> new ArrayList<>()).add(h);
            }
        }

        List<OwnershipMismatchVO> result = new ArrayList<>();
        Set<Long> assignedEquipmentIds = new HashSet<>();
        for (Assignment a : assignments) {
            assignedEquipmentIds.add(a.getEquipmentId());
            List<AssignmentHistory> opens = openByEquipment.get(a.getEquipmentId());
            if (opens == null || opens.isEmpty()) {
                OwnershipMismatchVO vo = baseMismatch(a.getEquipmentId());
                vo.setType("NO_INTERVAL");
                vo.setAssignmentTeamId(a.getTeamId());
                vo.setAssignmentTeamName(resolveTeamName(a.getTeamId()));
                vo.setDetail(hasAnySegment.contains(a.getEquipmentId())
                        ? "归属单在册，但区间链没有在用段（末段已闭口或链已损坏）"
                        : "归属单在册，但流水里还没有任何归属区间（老流水可能未迁移）");
                result.add(vo);
            } else if (opens.size() > 1) {
                OwnershipMismatchVO vo = baseMismatch(a.getEquipmentId());
                vo.setType("MULTIPLE_OPEN");
                vo.setAssignmentTeamId(a.getTeamId());
                vo.setAssignmentTeamName(resolveTeamName(a.getTeamId()));
                vo.setDetail("存在 " + opens.size() + " 段在用区间，当前归属落点不唯一");
                result.add(vo);
            } else {
                AssignmentHistory open = opens.get(0);
                if (!open.getNewTeamId().equals(a.getTeamId())) {
                    OwnershipMismatchVO vo = baseMismatch(a.getEquipmentId());
                    vo.setType("TEAM_MISMATCH");
                    vo.setAssignmentTeamId(a.getTeamId());
                    vo.setAssignmentTeamName(resolveTeamName(a.getTeamId()));
                    vo.setSegmentTeamId(open.getNewTeamId());
                    vo.setSegmentTeamName(resolveTeamName(open.getNewTeamId()));
                    vo.setDetail("归属单记在「" + vo.getAssignmentTeamName() + "」，流水末段却是「"
                            + vo.getSegmentTeamName() + "」");
                    result.add(vo);
                }
            }
        }
        // 有在用段、归属单却不存在的孤儿区间
        for (Map.Entry<Long, List<AssignmentHistory>> entry : openByEquipment.entrySet()) {
            if (assignedEquipmentIds.contains(entry.getKey())) {
                continue;
            }
            for (AssignmentHistory open : entry.getValue()) {
                OwnershipMismatchVO vo = baseMismatch(entry.getKey());
                vo.setType("ORPHAN_INTERVAL");
                vo.setSegmentTeamId(open.getNewTeamId());
                vo.setSegmentTeamName(resolveTeamName(open.getNewTeamId()));
                vo.setDetail("流水存在在用段（「" + vo.getSegmentTeamName() + "」），但归属单不存在");
                result.add(vo);
            }
        }
        return result;
    }

    /**
     * 老流水一次性推区间：把没有起止列的变更记录按时间顺序推成首尾相接的区间链。
     * 时间戳重复（先后顺序推不出）、或最早一段推不出起点的器材，逐件列入 problems，
     * 保持老格式不动，绝不按猜的补。可重复执行：已推过的器材自动跳过。
     */
    @Transactional
    public IntervalMigrationResult migrateLegacyIntervals() {
        IntervalMigrationResult result = new IntervalMigrationResult();
        List<Long> equipmentIds = historyRepository.findEquipmentIdsWithUnmigratedHistory();
        result.setLegacyEquipmentCount(equipmentIds.size());

        for (Long equipmentId : equipmentIds) {
            List<AssignmentHistory> rows =
                    historyRepository.findByEquipmentIdOrderByChangeTimeAscIdAsc(equipmentId);
            if (rows.isEmpty()) {
                continue;
            }
            // 已推过区间的器材不该再有老流水；混态说明数据被手工动过，交人核对
            boolean partiallyMigrated = rows.stream().anyMatch(r -> r.getValidFrom() != null);
            if (partiallyMigrated) {
                result.addProblem(equipmentId, resolveEquipmentCode(equipmentId),
                        "同一件器材的新老流水混在一起，请人工核对");
                continue;
            }
            if (rows.stream().anyMatch(r -> r.getChangeTime() == null)) {
                result.addProblem(equipmentId, resolveEquipmentCode(equipmentId),
                        "存在没有变更时刻的流水，先后顺序推不出");
                continue;
            }
            // 时间戳重复：并列的两条谁先谁后推不出，不许猜
            Set<LocalDateTime> seen = new HashSet<>();
            boolean duplicated = rows.stream().anyMatch(r -> !seen.add(r.getChangeTime()));
            if (duplicated) {
                result.addProblem(equipmentId, resolveEquipmentCode(equipmentId),
                        "变更时间戳重复，先后顺序推不出");
                continue;
            }

            List<AssignmentHistory> chain = new ArrayList<>();
            AssignmentHistory first = rows.get(0);
            if (first.getOldTeamId() == null) {
                // 首条即初始绑定：第一段起点就是首条变更时刻
                chain.addAll(rows);
            } else {
                // 首条是调整：它前面还有一段初始归属，起点只能取自归属单登记的绑定时刻
                Assignment assignment = assignmentRepository.findByEquipmentId(equipmentId).orElse(null);
                LocalDateTime bindTime = assignment != null ? assignment.getBindTime() : null;
                if (bindTime == null) {
                    result.addProblem(equipmentId, resolveEquipmentCode(equipmentId),
                            "最早一段推不出起点：首条流水不是初始绑定，且归属单没有绑定时刻");
                    continue;
                }
                if (bindTime.isAfter(first.getChangeTime())) {
                    result.addProblem(equipmentId, resolveEquipmentCode(equipmentId),
                            "最早一段推不出起点：归属单绑定时刻晚于首次变更时刻，前后对不上");
                    continue;
                }
                AssignmentHistory initial = new AssignmentHistory();
                initial.setEquipmentId(equipmentId);
                initial.setOldTeamId(null);
                initial.setNewTeamId(first.getOldTeamId());
                initial.setChangeTime(bindTime);
                initial.setValidFrom(bindTime);
                initial.setOperator("系统迁移");
                initial.setReason("老流水迁移补登：初始绑定归属段");
                chain.add(initial);
                chain.addAll(rows);
            }

            // 每段起点 = 本条变更时刻；每段终点 = 下一段起点；末段开口
            for (AssignmentHistory seg : chain) {
                if (seg.getValidFrom() == null) {
                    seg.setValidFrom(seg.getChangeTime());
                }
            }
            for (int i = 0; i < chain.size() - 1; i++) {
                chain.get(i).setValidTo(chain.get(i + 1).getValidFrom());
            }
            chain.get(chain.size() - 1).setValidTo(null);

            historyRepository.saveAll(chain);
            result.setMigratedEquipmentCount(result.getMigratedEquipmentCount() + 1);
            result.setMigratedSegmentCount(result.getMigratedSegmentCount() + chain.size());
        }
        log.info("老流水推区间完成：{} 件待迁移，成功 {} 件，推不动 {} 件",
                result.getLegacyEquipmentCount(), result.getMigratedEquipmentCount(),
                result.getProblems().size());
        return result;
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
                result.put("oldTeamId", history.getOldTeamId());
                result.put("oldTeamName", resolveTeamName(history.getOldTeamId()));
            }

            result.put("newTeamId", history.getNewTeamId());
            result.put("newTeamName", resolveTeamName(history.getNewTeamId()));

            result.put("changeTime", history.getChangeTime());
            result.put("validFrom", history.getValidFrom());
            result.put("validTo", history.getValidTo());
            result.put("operator", history.getOperator());
            result.put("reason", history.getReason());

            return result;
        });
    }

    public List<Equipment> getTeamEquipments(Long teamId) {
        return equipmentService.findByTeamId(teamId);
    }

    /**
     * 全部归属单：首页统计逐单核对“名下器材是否真在库可训”用。
     */
    public List<Assignment> findAllAssignments() {
        return assignmentRepository.findAll();
    }

    public long countByTeamId(Long teamId) {
        return assignmentRepository.countByTeamId(teamId);
    }

    public List<Object[]> getTeamEquipmentCounts() {
        return assignmentRepository.countByTeamId();
    }

    private OwnershipMismatchVO baseMismatch(Long equipmentId) {
        OwnershipMismatchVO vo = new OwnershipMismatchVO();
        vo.setEquipmentId(equipmentId);
        vo.setEquipmentCode(resolveEquipmentCode(equipmentId));
        return vo;
    }

    private String resolveTeamName(Long teamId) {
        try {
            Team team = teamService.findById(teamId);
            return team.getTeamName();
        } catch (Exception e) {
            return "已删除";
        }
    }

    private String resolveEquipmentCode(Long equipmentId) {
        try {
            return equipmentService.findById(equipmentId).getEquipmentCode();
        } catch (Exception e) {
            return "已删除";
        }
    }

    /**
     * 这一段待了多久：如 "3天4小时"、"5小时12分"、"8分"。
     */
    private String durationText(LocalDateTime from, LocalDateTime to) {
        Duration d = Duration.between(from, to);
        if (d.isNegative()) {
            return "0分";
        }
        long days = d.toDays();
        long hours = d.toHoursPart();
        long minutes = d.toMinutesPart();
        if (days > 0) {
            return days + "天" + hours + "小时";
        }
        if (d.toHours() > 0) {
            return d.toHours() + "小时" + minutes + "分";
        }
        if (minutes > 0) {
            return minutes + "分";
        }
        return "不足1分";
    }
}
