package com.example.app.service;

import com.example.app.dto.AssignmentAdjustDTO;
import com.example.app.dto.AssignmentDTO;
import com.example.app.dto.LoanCheckoutDTO;
import com.example.app.dto.LoanReturnDTO;
import com.example.app.dto.OccupancyCreateDTO;
import com.example.app.dto.TeamStatisticsDTO;
import com.example.app.entity.Equipment;
import com.example.app.entity.EquipmentLoan;
import com.example.app.entity.Team;
import com.example.app.repository.EquipmentLoanRepository;
import com.example.app.repository.EquipmentRepository;
import com.example.app.repository.TeamRepository;
import com.example.app.repository.TrainingOccupancyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 首页统计口径：只数真正在库、没有未结束占用、也没有在外未还的器材。
 * 占用刚挂上 / 外借刚出门提交后，下一次读取必须立刻掉数；
 * 同一件被占用账和外借账同时占住时只剔一次。
 */
@SpringBootTest
class StatisticsServiceIntegrationTest {

    @Autowired private StatisticsService statisticsService;
    @Autowired private OccupancyService occupancyService;
    @Autowired private LoanService loanService;
    @Autowired private AssignmentService assignmentService;
    @Autowired private EquipmentLoanRepository loanRepository;
    @Autowired private TrainingOccupancyRepository occupancyRepository;
    @Autowired private EquipmentRepository equipmentRepository;
    @Autowired private TeamRepository teamRepository;

    private Long teamA;
    private Long teamB;
    private Long equipment1; // 归班组A
    private Long equipment2; // 归班组A
    private Long equipment3; // 归班组B

    @BeforeEach
    void setUp() {
        loanRepository.deleteAll();
        occupancyRepository.deleteAll();

        Team a = new Team();
        a.setTeamName("统计一班" + System.nanoTime());
        a.setMemberCount(10);
        teamA = teamRepository.save(a).getId();

        Team b = new Team();
        b.setTeamName("统计二班" + System.nanoTime());
        b.setMemberCount(8);
        teamB = teamRepository.save(b).getId();

        equipment1 = newEquipment("EQ-STAT-1-");
        equipment2 = newEquipment("EQ-STAT-2-");
        equipment3 = newEquipment("EQ-STAT-3-");

        bind(equipment1, teamA);
        bind(equipment2, teamA);
        bind(equipment3, teamB);
    }

    private Long newEquipment(String codePrefix) {
        Equipment eq = new Equipment();
        eq.setEquipmentCode(codePrefix + System.nanoTime());
        eq.setTrainingPurpose("灭火训练");
        eq.setSizeSpec("水带");
        return equipmentRepository.save(eq).getId();
    }

    private void bind(Long equipmentId, Long teamId) {
        AssignmentDTO dto = new AssignmentDTO();
        dto.setEquipmentId(equipmentId);
        dto.setTeamId(teamId);
        dto.setOperator("班长");
        assignmentService.bind(dto);
    }

    private void occupy(Long equipmentId, Long teamId, LocalDate date, int startHour, int endHour) {
        OccupancyCreateDTO dto = new OccupancyCreateDTO();
        dto.setEquipmentId(equipmentId);
        dto.setTeamId(teamId);
        dto.setTrainingDate(date);
        dto.setStartTime(LocalTime.of(startHour, 0));
        dto.setEndTime(LocalTime.of(endHour, 0));
        dto.setCourseName("测试课目");
        dto.setOperator("班长");
        occupancyService.create(dto);
    }

    private LoanCheckoutDTO loanDto(Long equipmentId, Long teamId) {
        LoanCheckoutDTO dto = new LoanCheckoutDTO();
        dto.setEquipmentId(equipmentId);
        dto.setTeamId(teamId);
        dto.setExpectedReturnTime(LocalDateTime.now().plusHours(4));
        dto.setCompanions("张三、李四");
        dto.setReason("外场演练");
        dto.setOperator("班长");
        return dto;
    }

    private long available(Map<String, Object> overview) {
        return ((Number) overview.get("totalEquipment")).longValue();
    }

    private TeamStatisticsDTO teamStat(Long teamId) {
        return statisticsService.getTeamStatistics().stream()
                .filter(s -> s.getTeamId().equals(teamId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("班组统计里找不到班组 " + teamId));
    }

    // ---------- 1. 占用刚挂上，首页立刻掉数 ----------

    @Test
    void overview_dropsImmediately_whenNotEndedOccupancyAttached() {
        Map<String, Object> before = statisticsService.getOverview();
        long availableBefore = available(before);
        long registeredBefore = ((Number) before.get("registeredEquipment")).longValue();
        long teams = ((Number) before.get("totalTeam")).longValue();

        // 挂明天的课目：任何墙钟时刻都属于“尚未结束”
        occupy(equipment1, teamA, LocalDate.now().plusDays(1), 9, 11);

        Map<String, Object> after = statisticsService.getOverview();
        assertEquals(availableBefore - 1, available(after),
                "占用刚挂上，首页在库可训件数必须立刻掉，不能停在挂占用前");
        assertEquals(registeredBefore, ((Number) after.get("registeredEquipment")).longValue(),
                "登记总件数不变，变的是可训口径");
        assertEquals(1L, ((Number) after.get("unavailableEquipment")).longValue()
                - ((Number) before.get("unavailableEquipment")).longValue());

        double expectedAvg = teams > 0
                ? Math.round(((double) (availableBefore - 1) / teams) * 10) / 10.0 : 0;
        assertEquals(expectedAvg, ((Number) after.get("averageEquipmentPerTeam")).doubleValue(),
                "班均必须按掉数后的可训件数重算");

        // 班组侧：一班可训 2 -> 1，名下归属仍是 2
        TeamStatisticsDTO a = teamStat(teamA);
        assertEquals(1L, a.getEquipmentCount());
        assertEquals(2L, a.getAssignedCount());
        assertEquals(1L, teamStat(teamB).getEquipmentCount());
    }

    // ---------- 2. 外借刚出门，首页立刻掉数；归还闭环后回来 ----------

    @Test
    void overview_dropsImmediately_whenLoanCheckedOut_andRecoversAfterReturn() {
        long availableBefore = available(statisticsService.getOverview());

        EquipmentLoan loan = loanService.checkout(loanDto(equipment2, teamA));
        assertEquals(availableBefore - 1, available(statisticsService.getOverview()),
                "外借刚出门，首页必须立刻掉数");
        assertEquals(1L, teamStat(teamA).getEquipmentCount());

        loanService.returnLoan(loan.getId(), new LoanReturnDTO());
        assertEquals(availableBefore, available(statisticsService.getOverview()),
                "归还闭环后该件重新计入可训");
        assertEquals(2L, teamStat(teamA).getEquipmentCount());
    }

    // ---------- 3. 占用账和外借账同时占住同一件：只剔一次 ----------

    @Test
    void overview_countsOnce_whenSameItemOccupiedAndLoaned() {
        long availableBefore = available(statisticsService.getOverview());

        // 先出门（此刻无占用，能出去），再挂明天占用 —— 两本账同时占住同一件
        loanService.checkout(loanDto(equipment2, teamA));
        long afterLoan = available(statisticsService.getOverview());
        assertEquals(availableBefore - 1, afterLoan);

        occupy(equipment2, teamA, LocalDate.now().plusDays(1), 14, 16);
        assertEquals(afterLoan, available(statisticsService.getOverview()),
                "同一件被占用和外借同时占住，只能剔一次，不能重复扣");
        assertEquals(1L, teamStat(teamA).getEquipmentCount(),
                "班组侧同样只剔一次：一班两件名下器材，一件被两本账占住，可训仍是一件");
    }

    // ---------- 4. 已结束的占用、已归还的离场不占数 ----------

    @Test
    void overview_ignoresEndedOccupancy() {
        long availableBefore = available(statisticsService.getOverview());

        // 昨天 09:00-10:00 的占用：已结束，任何墙钟时刻都不占数
        occupy(equipment1, teamA, LocalDate.now().minusDays(1), 9, 10);

        assertEquals(availableBefore, available(statisticsService.getOverview()),
                "已结束的历史占用不掉数");
        assertEquals(2L, teamStat(teamA).getEquipmentCount());
    }

    // ---------- 5. 改班作废占用后，件数回到可训并跟着新班组走 ----------

    @Test
    void teamStats_followTransferAndCancelledOccupancy() {
        occupy(equipment1, teamA, LocalDate.now().plusDays(1), 9, 11);
        assertEquals(1L, teamStat(teamA).getEquipmentCount());

        AssignmentAdjustDTO adjust = new AssignmentAdjustDTO();
        adjust.setEquipmentId(equipment1);
        adjust.setNewTeamId(teamB);
        adjust.setOperator("管理员");
        adjust.setReason("统一调配");
        assignmentService.adjust(adjust);

        // 未结束占用当场作废：器材恢复可训，且算到新班组头上
        assertEquals(1L, teamStat(teamA).getEquipmentCount());
        assertEquals(1L, teamStat(teamA).getAssignedCount());
        assertEquals(2L, teamStat(teamB).getEquipmentCount());
        assertEquals(2L, teamStat(teamB).getAssignedCount());
    }

    // ---------- 6. 两个班长几乎同时打开首页：一个刚出门成功，另一个刷新立刻看到新均数 ----------

    @Test
    void overview_refreshAfterCommittedCheckout_seesDroppedCount() throws Exception {
        long availableBefore = available(statisticsService.getOverview());

        // 一个班长的出门登记在独立线程里提交成功
        Thread checkout = new Thread(() -> loanService.checkout(loanDto(equipment2, teamA)));
        checkout.start();
        checkout.join();

        // 另一个班长此刻刷新首页：读到的是掉数后的结果，不是出门前的旧均数
        Map<String, Object> after = statisticsService.getOverview();
        assertEquals(availableBefore - 1, available(after));
        long teams = ((Number) after.get("totalTeam")).longValue();
        double expectedAvg = teams > 0
                ? Math.round(((double) (availableBefore - 1) / teams) * 10) / 10.0 : 0;
        assertEquals(expectedAvg, ((Number) after.get("averageEquipmentPerTeam")).doubleValue());
    }
}
