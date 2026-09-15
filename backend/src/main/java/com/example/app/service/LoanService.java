package com.example.app.service;

import com.example.app.dto.LoanCheckoutDTO;
import com.example.app.dto.LoanReturnDTO;
import com.example.app.dto.LoanVO;
import com.example.app.entity.Assignment;
import com.example.app.entity.Equipment;
import com.example.app.entity.EquipmentLoan;
import com.example.app.entity.Team;
import com.example.app.exception.BusinessException;
import com.example.app.repository.AssignmentRepository;
import com.example.app.repository.EquipmentLoanRepository;
import com.example.app.repository.EquipmentRepository;
import com.example.app.repository.TeamRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class LoanService {

    private final EquipmentLoanRepository loanRepository;
    private final EquipmentRepository equipmentRepository;
    private final TeamRepository teamRepository;
    private final AssignmentRepository assignmentRepository;
    private final OccupancyService occupancyService;

    public LoanService(EquipmentLoanRepository loanRepository,
                       EquipmentRepository equipmentRepository,
                       TeamRepository teamRepository,
                       AssignmentRepository assignmentRepository,
                       OccupancyService occupancyService) {
        this.loanRepository = loanRepository;
        this.equipmentRepository = equipmentRepository;
        this.teamRepository = teamRepository;
        this.assignmentRepository = assignmentRepository;
        this.occupancyService = occupancyService;
    }

    /**
     * 班长登记器材外借离场。
     *
     * 并发控制：先对器材行加 FOR UPDATE 行锁（与课目占用挂载、归属调整、
     * 其它出门登记共用同一把锁），同一件器材的并发出门在此排队，
     * 后落地者拿到锁后能读到先落地者已提交的在途单 ——
     * 既不会两条都算出门在外，也不会把先落地的挤掉。
     */
    @Transactional
    public EquipmentLoan checkout(LoanCheckoutDTO dto) {
        // 0) 三项硬门槛：预计归还时刻 / 同行人 / 离场事由，缺一项不能出门
        //    （注解已拦一层，服务内再兜底，防止绕过 Controller 直接调用）
        if (dto.getExpectedReturnTime() == null) {
            throw new BusinessException("预计归还时刻不能为空");
        }
        if (dto.getCompanions() == null || dto.getCompanions().isBlank()) {
            throw new BusinessException("同行人不能为空");
        }
        if (dto.getReason() == null || dto.getReason().isBlank()) {
            throw new BusinessException("离场事由不能为空");
        }
        if (!dto.getExpectedReturnTime().isAfter(LocalDateTime.now())) {
            throw new BusinessException("预计归还时刻必须晚于当前时刻");
        }

        // 1) 锁器材行：与占用挂载、归属调整、其它出门/归还互斥排队
        Equipment equipment = equipmentRepository.findByIdForUpdate(dto.getEquipmentId())
                .orElseThrow(() -> new BusinessException("器材不存在"));

        if (dto.getTeamId() == null || teamRepository.findById(dto.getTeamId()).isEmpty()) {
            throw new BusinessException("班组不存在");
        }

        // 2) 只能把自己班组名下、已经归属的器材登记离场
        Assignment ownership = assignmentRepository.findByEquipmentId(equipment.getId())
                .orElseThrow(() -> new BusinessException("器材尚未归属班组，无法登记离场"));
        if (!ownership.getTeamId().equals(dto.getTeamId())) {
            throw new BusinessException("只能将本班组名下已归属的器材登记离场");
        }

        // 3) 同一件器材在外（含超期）未还，不能再开第二条离场
        List<EquipmentLoan> openLoans = loanRepository.findOpenByEquipmentForUpdate(equipment.getId());
        if (!openLoans.isEmpty()) {
            EquipmentLoan open = openLoans.get(0);
            if (EquipmentLoan.STATUS_OVERDUE.equals(open.getStatus())
                    || !open.getExpectedReturnTime().isAfter(LocalDateTime.now())) {
                throw new BusinessException("该器材已有超期未还的离场记录，归还前不能再次外借");
            }
            throw new BusinessException("该器材已出门在外且尚未归还，不能重复登记离场");
        }

        // 4) 已挂“尚未结束”的有效课目占用：直接拒绝。
        //    只读取占用账做判定，不改占用、不作废时段，两边账互不回写。
        if (occupancyService.hasActiveNotEndedOccupancy(equipment.getId())) {
            throw new BusinessException("该器材挂有尚未结束的课目占用，本次外借直接拒绝，请先与课目方协调");
        }

        EquipmentLoan loan = new EquipmentLoan();
        loan.setEquipmentId(equipment.getId());
        loan.setTeamId(dto.getTeamId());
        loan.setExpectedReturnTime(dto.getExpectedReturnTime());
        loan.setCompanions(dto.getCompanions().trim());
        loan.setReason(dto.getReason().trim());
        loan.setOperator(dto.getOperator() == null || dto.getOperator().isBlank()
                ? null : dto.getOperator().trim());
        loan.setStatus(EquipmentLoan.STATUS_OUT);
        EquipmentLoan saved = loanRepository.save(loan);
        log.info("器材 {} 由班组 {} 登记出门，离场单 {}，预计 {} 归还",
                equipment.getId(), dto.getTeamId(), saved.getId(), saved.getExpectedReturnTime());
        return saved;
    }

    /**
     * 器材回场归还：只有在外（OUT / OVERDUE）的离场单可归还。
     * 超期件同样走这里归还，归还闭环后同一件器材才能再被外借。
     */
    @Transactional
    public EquipmentLoan returnLoan(Long loanId, LoanReturnDTO dto) {
        EquipmentLoan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new BusinessException("离场记录不存在"));
        if (EquipmentLoan.STATUS_RETURNED.equals(loan.getStatus())) {
            throw new BusinessException("该离场记录已归还，不能重复归还");
        }
        boolean wasOverdue = isOverdue(loan);

        loan.setStatus(EquipmentLoan.STATUS_RETURNED);
        loan.setReturnTime(LocalDateTime.now());
        String operator = dto == null || dto.getOperator() == null || dto.getOperator().isBlank()
                ? "系统" : dto.getOperator().trim();
        loan.setReturnOperator(operator);
        if (dto != null && dto.getRemark() != null && !dto.getRemark().isBlank()) {
            loan.setReturnRemark(dto.getRemark().trim());
        }
        EquipmentLoan saved = loanRepository.save(loan);
        log.info("离场单 {} 归还闭环（器材 {}，经手人 {}，{}）",
                loanId, loan.getEquipmentId(), operator, wasOverdue ? "超期归还" : "按期归还");
        return saved;
    }

    /**
     * 超期扫描：已到预计归还时刻仍 OUT 的在途单翻成 OVERDUE 并留超期时刻。
     * 每分钟跑一次；测试可直接调用。
     */
    @Scheduled(fixedRate = 60_000L, initialDelay = 30_000L)
    @Transactional
    public int markOverdueLoans() {
        LocalDateTime now = LocalDateTime.now();
        List<EquipmentLoan> due = loanRepository.findDueForOverdue(now);
        for (EquipmentLoan loan : due) {
            loan.setStatus(EquipmentLoan.STATUS_OVERDUE);
            loan.setOverdueTime(now);
        }
        loanRepository.saveAll(due);
        if (!due.isEmpty()) {
            log.warn("超期未还器材 {} 件，已置为超期状态", due.size());
        }
        return due.size();
    }

    /**
     * 离场列表。
     * @param tab ALL 全部 / OPEN 在外（含超期）/ OVERDUE 仅超期 / RETURNED 已归还
     */
    public Page<LoanVO> list(String tab, Long teamId, Long equipmentId, Pageable pageable) {
        String t = tab == null || tab.isBlank() ? "OPEN" : tab.trim().toUpperCase();
        boolean openOnly = "OPEN".equals(t);
        boolean overdueOnly = "OVERDUE".equals(t);
        boolean returnedOnly = "RETURNED".equals(t);

        Page<EquipmentLoan> page = loanRepository.findLoans(
                teamId, equipmentId, openOnly, overdueOnly, returnedOnly, LocalDateTime.now(), pageable);
        return page.map(this::toVO);
    }

    /**
     * 对账用统计：在外件数、超期件数（读时口径，不依赖扫描是否刚跑过）、已归还件数。
     */
    public Map<String, Object> summary() {
        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> result = new HashMap<>();
        result.put("openCount",
                loanRepository.countByStatus(EquipmentLoan.STATUS_OUT)
                        + loanRepository.countByStatus(EquipmentLoan.STATUS_OVERDUE));
        result.put("overdueCount", loanRepository.countEffectivelyOverdue(now));
        result.put("returnedCount", loanRepository.countByStatus(EquipmentLoan.STATUS_RETURNED));
        return result;
    }

    /**
     * 读时口径：落库状态 + 当前时刻 vs 预计归还时刻。
     * 扫描还没跑到的窗口期内，OUT 已过点也如实呈现为超期。
     */
    private boolean isOverdue(EquipmentLoan loan) {
        if (EquipmentLoan.STATUS_RETURNED.equals(loan.getStatus())) {
            return false;
        }
        return EquipmentLoan.STATUS_OVERDUE.equals(loan.getStatus())
                || (EquipmentLoan.STATUS_OUT.equals(loan.getStatus())
                        && !loan.getExpectedReturnTime().isAfter(LocalDateTime.now()));
    }

    private LoanVO toVO(EquipmentLoan l) {
        LoanVO vo = new LoanVO();
        vo.setId(l.getId());
        vo.setEquipmentId(l.getEquipmentId());
        vo.setTeamId(l.getTeamId());
        vo.setCheckoutTime(l.getCheckoutTime());
        vo.setExpectedReturnTime(l.getExpectedReturnTime());
        vo.setCompanions(l.getCompanions());
        vo.setReason(l.getReason());
        vo.setOperator(l.getOperator());
        vo.setStatus(l.getStatus());
        vo.setOverdueTime(l.getOverdueTime());
        vo.setReturnTime(l.getReturnTime());
        vo.setReturnOperator(l.getReturnOperator());
        vo.setReturnRemark(l.getReturnRemark());

        boolean overdue = isOverdue(l);
        vo.setOverdue(overdue);
        if (EquipmentLoan.STATUS_RETURNED.equals(l.getStatus())) {
            vo.setEffectiveStatus(EquipmentLoan.STATUS_RETURNED);
        } else {
            vo.setEffectiveStatus(overdue ? EquipmentLoan.STATUS_OVERDUE : EquipmentLoan.STATUS_OUT);
        }

        try {
            Equipment equipment = equipmentRepository.findById(l.getEquipmentId())
                    .orElseThrow(() -> new RuntimeException("器材已删除"));
            vo.setEquipmentCode(equipment.getEquipmentCode());
            vo.setTrainingPurpose(equipment.getTrainingPurpose());
            vo.setSizeSpec(equipment.getSizeSpec());
        } catch (Exception e) {
            vo.setEquipmentCode("已删除");
        }

        try {
            Team team = teamRepository.findById(l.getTeamId())
                    .orElseThrow(() -> new RuntimeException("班组已删除"));
            vo.setTeamName(team.getTeamName());
        } catch (Exception e) {
            vo.setTeamName("已删除");
        }
        return vo;
    }
}
