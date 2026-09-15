package com.example.app.service;

import com.example.app.dto.AssignmentDTO;
import com.example.app.dto.LoanCheckoutDTO;
import com.example.app.dto.LoanReturnDTO;
import com.example.app.dto.LoanVO;
import com.example.app.dto.OccupancyCreateDTO;
import com.example.app.entity.Equipment;
import com.example.app.entity.EquipmentLoan;
import com.example.app.entity.Team;
import com.example.app.exception.BusinessException;
import com.example.app.repository.EquipmentLoanRepository;
import com.example.app.repository.EquipmentRepository;
import com.example.app.repository.TeamRepository;
import com.example.app.repository.TrainingOccupancyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class LoanServiceIntegrationTest {

    @Autowired private LoanService loanService;
    @Autowired private OccupancyService occupancyService;
    @Autowired private AssignmentService assignmentService;
    @Autowired private EquipmentLoanRepository loanRepository;
    @Autowired private TrainingOccupancyRepository occupancyRepository;
    @Autowired private EquipmentRepository equipmentRepository;
    @Autowired private TeamRepository teamRepository;

    private Long teamA;
    private Long teamB;
    private Long equipmentId;

    @BeforeEach
    void setUp() {
        loanRepository.deleteAll();
        occupancyRepository.deleteAll();

        Team a = new Team();
        a.setTeamName("外借一班" + System.nanoTime());
        a.setMemberCount(10);
        teamA = teamRepository.save(a).getId();

        Team b = new Team();
        b.setTeamName("外借二班" + System.nanoTime());
        b.setMemberCount(8);
        teamB = teamRepository.save(b).getId();

        Equipment eq = new Equipment();
        eq.setEquipmentCode("EQ-LOAN-" + System.nanoTime());
        eq.setTrainingPurpose("灭火训练");
        eq.setSizeSpec("水带");
        equipmentId = equipmentRepository.save(eq).getId();

        AssignmentDTO bind = new AssignmentDTO();
        bind.setEquipmentId(equipmentId);
        bind.setTeamId(teamA);
        bind.setOperator("一班班长");
        assignmentService.bind(bind);
    }

    private LoanCheckoutDTO dto(LocalDateTime expectedReturn, String companions, String reason) {
        LoanCheckoutDTO d = new LoanCheckoutDTO();
        d.setEquipmentId(equipmentId);
        d.setTeamId(teamA);
        d.setExpectedReturnTime(expectedReturn);
        d.setCompanions(companions);
        d.setReason(reason);
        d.setOperator("一班班长");
        return d;
    }

    private LoanCheckoutDTO validDto() {
        return dto(LocalDateTime.now().plusHours(4), "张三、李四", "外场水带铺设演练");
    }

    // ---------- 1. 出门基础规则 ----------

    @Test
    void checkout_success_whenOwnedAndAllFieldsPresent() {
        EquipmentLoan loan = loanService.checkout(validDto());
        assertEquals(EquipmentLoan.STATUS_OUT, loan.getStatus());
        assertNotNull(loan.getCheckoutTime());
        assertEquals(1L, loanService.summary().get("openCount"));
    }

    @Test
    void checkout_rejects_whenExpectedReturnMissing() {
        LoanCheckoutDTO d = validDto();
        d.setExpectedReturnTime(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> loanService.checkout(d));
        assertTrue(ex.getMessage().contains("预计归还时刻"));
    }

    @Test
    void checkout_rejects_whenCompanionsMissing() {
        LoanCheckoutDTO d = validDto();
        d.setCompanions("   ");
        BusinessException ex = assertThrows(BusinessException.class, () -> loanService.checkout(d));
        assertTrue(ex.getMessage().contains("同行人"));
    }

    @Test
    void checkout_rejects_whenReasonMissing() {
        LoanCheckoutDTO d = validDto();
        d.setReason(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> loanService.checkout(d));
        assertTrue(ex.getMessage().contains("离场事由"));
    }

    @Test
    void checkout_rejects_whenExpectedReturnInPast() {
        LoanCheckoutDTO d = validDto();
        d.setExpectedReturnTime(LocalDateTime.now().minusMinutes(1));
        BusinessException ex = assertThrows(BusinessException.class, () -> loanService.checkout(d));
        assertTrue(ex.getMessage().contains("预计归还时刻必须晚于当前时刻"));
    }

    @Test
    void checkout_rejects_nonOwnedTeam() {
        LoanCheckoutDTO d = validDto();
        d.setTeamId(teamB);
        BusinessException ex = assertThrows(BusinessException.class, () -> loanService.checkout(d));
        assertTrue(ex.getMessage().contains("本班组"));
    }

    // ---------- 2. 在途未还不能开第二条 ----------

    @Test
    void checkout_rejects_secondLoanWhileStillOut() {
        loanService.checkout(validDto());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> loanService.checkout(validDto()));
        assertTrue(ex.getMessage().contains("尚未归还"));
    }

    @Test
    void checkout_allowed_again_afterReturn() {
        EquipmentLoan first = loanService.checkout(validDto());
        loanService.returnLoan(first.getId(), new LoanReturnDTO());
        EquipmentLoan second = loanService.checkout(validDto());
        assertEquals(EquipmentLoan.STATUS_OUT, second.getStatus());

        var page = loanService.list("ALL", null, null, PageRequest.of(0, 50));
        assertEquals(2, page.getTotalElements());
        assertEquals(1, page.getContent().stream()
                .filter(v -> EquipmentLoan.STATUS_RETURNED.equals(v.getEffectiveStatus())).count());
        assertEquals(1, page.getContent().stream()
                .filter(v -> EquipmentLoan.STATUS_OUT.equals(v.getEffectiveStatus())).count());
    }

    // ---------- 3. 并发：两个班长同时点同一件器材出门 ----------

    @Test
    void concurrentCheckout_exactlyOneWins() throws InterruptedException {
        int threads = 2;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    loanService.checkout(validDto());
                    success.incrementAndGet();
                } catch (BusinessException e) {
                    rejected.incrementAndGet();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }
        ready.await();
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS));

        assertEquals(1, success.get(), "先落地一条生效");
        assertEquals(1, rejected.get(), "后落地一条被挡");
        assertEquals(1L, loanRepository.findAll().size(), "不能两条都算出门在外");
    }

    // ---------- 4. 未结束课目占用：直接拒绝，且两本账互不改动 ----------

    @Test
    void checkout_rejected_whenActiveNotEndedOccupancy_andNeitherSideTouched() {
        // 挂明天的课目：任何墙钟时刻都属于“尚未结束”，用例不受当天时段窗口影响
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        OccupancyCreateDTO occ = new OccupancyCreateDTO();
        occ.setEquipmentId(equipmentId);
        occ.setTeamId(teamA);
        occ.setTrainingDate(tomorrow);
        occ.setStartTime(LocalTime.of(9, 0));
        occ.setEndTime(LocalTime.of(11, 0));
        occ.setCourseName("明日外场供水课目");
        occ.setOperator("一班班长");
        occupancyService.create(occ);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> loanService.checkout(validDto()));
        assertTrue(ex.getMessage().contains("课目占用"));

        // 占用账没被动过：仍是 ACTIVE
        var active = occupancyService.listActiveForEquipment(equipmentId, tomorrow);
        assertEquals(1, active.size());
        assertEquals("ACTIVE", active.get(0).getStatus());

        // 离场账没留下任何单据
        assertEquals(0L, loanRepository.findAll().size());
    }

    @Test
    void checkout_allowed_whenOccupancyAlreadyEnded_andDoesNotCancelIt() {
        LocalDate today = LocalDate.now();

        // 今天凌晨已结束的占用：不算“尚未结束”，不应拦截
        OccupancyCreateDTO past = new OccupancyCreateDTO();
        past.setEquipmentId(equipmentId);
        past.setTeamId(teamA);
        past.setTrainingDate(today);
        past.setStartTime(LocalTime.of(0, 0));
        past.setEndTime(LocalTime.of(0, 15));
        past.setCourseName("凌晨已结束课目");
        past.setOperator("一班班长");
        occupancyService.create(past);

        EquipmentLoan loan = loanService.checkout(validDto());
        assertEquals(EquipmentLoan.STATUS_OUT, loan.getStatus());

        // 已结束的历史占用原样保留，离场没有顺手作废它
        var active = occupancyService.listActiveForEquipment(equipmentId, today);
        assertEquals(1, active.size());
        assertEquals("ACTIVE", active.get(0).getStatus());
    }

    // ---------- 5. 超期：读时口径、扫描翻状态、超期件归还前锁死、归还后可再借 ----------

    @Test
    void overdueLifecycle_blockedWhileOverdue_thenFreesAfterReturn() {
        // 直接落一条预计归还时刻已过、仍在 OUT 的在途单
        EquipmentLoan loan = new EquipmentLoan();
        loan.setEquipmentId(equipmentId);
        loan.setTeamId(teamA);
        loan.setExpectedReturnTime(LocalDateTime.now().minusHours(2));
        loan.setCompanions("王五");
        loan.setReason("外场演练延时");
        loan.setOperator("一班班长");
        loan.setStatus(EquipmentLoan.STATUS_OUT);
        loan = loanRepository.saveAndFlush(loan);

        // 读时口径：扫描还没跑，列表/统计已如实呈现超期
        var openPage = loanService.list("OPEN", null, null, PageRequest.of(0, 50));
        LoanVO vo = openPage.getContent().get(0);
        assertTrue(vo.isOverdue());
        assertEquals(EquipmentLoan.STATUS_OVERDUE, vo.getEffectiveStatus());
        assertEquals(1L, loanService.summary().get("overdueCount"));

        // 超期件回来之前不能再被别人外借
        BusinessException ex = assertThrows(BusinessException.class,
                () -> loanService.checkout(validDto()));
        assertTrue(ex.getMessage().contains("超期未还"));

        // 定时扫描把落库状态翻成 OVERDUE 并留超期时刻
        int marked = loanService.markOverdueLoans();
        assertEquals(1, marked);
        EquipmentLoan scanned = loanRepository.findById(loan.getId()).orElseThrow();
        assertEquals(EquipmentLoan.STATUS_OVERDUE, scanned.getStatus());
        assertNotNull(scanned.getOverdueTime());

        // 超期归还
        LoanReturnDTO ret = new LoanReturnDTO();
        ret.setOperator("门卫老赵");
        ret.setRemark("器材完好");
        EquipmentLoan returned = loanService.returnLoan(loan.getId(), ret);
        assertEquals(EquipmentLoan.STATUS_RETURNED, returned.getStatus());
        assertNotNull(returned.getReturnTime());

        // 归还闭环后可以再开新的离场
        EquipmentLoan second = loanService.checkout(validDto());
        assertEquals(EquipmentLoan.STATUS_OUT, second.getStatus());
    }

    @Test
    void returnLoan_rejects_doubleReturn() {
        EquipmentLoan loan = loanService.checkout(validDto());
        loanService.returnLoan(loan.getId(), new LoanReturnDTO());
        assertThrows(BusinessException.class,
                () -> loanService.returnLoan(loan.getId(), new LoanReturnDTO()));
    }
}
