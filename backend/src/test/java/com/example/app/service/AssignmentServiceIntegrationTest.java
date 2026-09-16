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
import com.example.app.repository.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AssignmentServiceIntegrationTest {

    @Autowired private AssignmentService assignmentService;
    @Autowired private AssignmentRepository assignmentRepository;
    @Autowired private AssignmentHistoryRepository historyRepository;
    @Autowired private EquipmentRepository equipmentRepository;
    @Autowired private TeamRepository teamRepository;

    private Long teamA;
    private Long teamB;
    private Long teamC;
    private Long equipmentId;

    @BeforeEach
    void setUp() {
        historyRepository.deleteAll();
        assignmentRepository.deleteAll();

        teamA = newTeam("归属一班");
        teamB = newTeam("归属二班");
        teamC = newTeam("归属三班");

        equipmentId = newEquipment();
    }

    private Long newTeam(String prefix) {
        Team t = new Team();
        t.setTeamName(prefix + System.nanoTime());
        t.setMemberCount(10);
        return teamRepository.save(t).getId();
    }

    private Long newEquipment() {
        Equipment eq = new Equipment();
        eq.setEquipmentCode("EQ-ASSIGN-" + System.nanoTime());
        eq.setTrainingPurpose("破拆训练");
        eq.setSizeSpec("液压钳");
        return equipmentRepository.save(eq).getId();
    }

    private AssignmentDTO bindDto(Long eqId, Long teamId) {
        AssignmentDTO d = new AssignmentDTO();
        d.setEquipmentId(eqId);
        d.setTeamId(teamId);
        d.setOperator("管理员");
        return d;
    }

    private AssignmentAdjustDTO adjustDto(Long eqId, Long newTeamId) {
        AssignmentAdjustDTO d = new AssignmentAdjustDTO();
        d.setEquipmentId(eqId);
        d.setNewTeamId(newTeamId);
        d.setOperator("管理员");
        d.setReason("轮换");
        return d;
    }

    /** 老格式流水：只有变更时刻，没有区间起止 */
    private AssignmentHistory legacyRow(Long eqId, Long oldTeam, Long newTeam, LocalDateTime changeTime) {
        AssignmentHistory h = new AssignmentHistory();
        h.setEquipmentId(eqId);
        h.setOldTeamId(oldTeam);
        h.setNewTeamId(newTeam);
        h.setChangeTime(changeTime);
        h.setOperator("历史操作人");
        return historyRepository.save(h);
    }

    private Assignment assignmentRow(Long eqId, Long teamId, LocalDateTime bindTime) {
        Assignment a = new Assignment();
        a.setEquipmentId(eqId);
        a.setTeamId(teamId);
        a.setBindTime(bindTime);
        a.setOperator("历史操作人");
        return assignmentRepository.save(a);
    }

    private List<AssignmentHistory> chain(Long eqId) {
        return historyRepository.findByEquipmentIdOrderByValidFromAscIdAsc(eqId);
    }

    // ---------- 1. 绑定/调整落区间 ----------

    @Test
    void bind_createsSingleOpenSegment() {
        assignmentService.bind(bindDto(equipmentId, teamA));

        List<AssignmentHistory> segs = chain(equipmentId);
        assertEquals(1, segs.size());
        assertEquals(teamA, segs.get(0).getNewTeamId());
        assertNotNull(segs.get(0).getValidFrom());
        assertNull(segs.get(0).getValidTo(), "在用段必须开口");
        assertEquals(segs.get(0).getChangeTime(), segs.get(0).getValidFrom());
        assertDoesNotThrow(() -> assignmentService.verifyChain(equipmentId));
    }

    @Test
    void adjust_segmentsChainExactly_andIntervalsQueryable() {
        assignmentService.bind(bindDto(equipmentId, teamA));
        assignmentService.adjust(adjustDto(equipmentId, teamB));

        List<AssignmentHistory> segs = chain(equipmentId);
        assertEquals(2, segs.size());
        assertEquals(teamA, segs.get(0).getNewTeamId());
        assertEquals(teamB, segs.get(1).getNewTeamId());
        assertEquals(segs.get(0).getValidTo(), segs.get(1).getValidFrom(),
                "前一段结束的那一刻就是后一段开始的那一刻");
        assertTrue(segs.get(0).getValidTo().isAfter(segs.get(0).getValidFrom()));
        assertNull(segs.get(1).getValidTo(), "还在用的这一段结束时间先空着");

        // 区间能查：每段归谁、从何时到何时、待了多久
        List<AssignmentIntervalVO> intervals = assignmentService.getEquipmentIntervals(equipmentId);
        assertEquals(2, intervals.size());
        assertFalse(intervals.get(0).isCurrent());
        assertTrue(intervals.get(1).isCurrent());
        assertNotNull(intervals.get(0).getDurationText());
        assertNotNull(intervals.get(1).getDurationText());
    }

    @Test
    void adjust_rejects_sameTeam() {
        assignmentService.bind(bindDto(equipmentId, teamA));
        assertThrows(BusinessException.class, () -> assignmentService.adjust(adjustDto(equipmentId, teamA)));
        assertEquals(1, chain(equipmentId).size());
    }

    // ---------- 2. 链式校验：空档、叠压、末段闭口都要被挑出 ----------

    @Test
    void verifyChain_detectsGap() {
        LocalDateTime t1 = LocalDateTime.of(2026, 1, 1, 8, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 1, 3, 8, 0);
        LocalDateTime t3 = LocalDateTime.of(2026, 1, 5, 8, 0);
        AssignmentHistory s1 = legacyRow(equipmentId, null, teamA, t1);
        s1.setValidFrom(t1);
        s1.setValidTo(t2);
        historyRepository.save(s1);
        AssignmentHistory s2 = legacyRow(equipmentId, teamA, teamB, t3);
        s2.setValidFrom(t3); // 与上一段之间空了一天多
        historyRepository.save(s2);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> assignmentService.verifyChain(equipmentId));
        assertTrue(ex.getMessage().contains("接不上"), ex.getMessage());
    }

    @Test
    void verifyChain_detectsOverlapAndPrematureOpen() {
        LocalDateTime t1 = LocalDateTime.of(2026, 1, 1, 8, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 1, 3, 8, 0);
        AssignmentHistory s1 = legacyRow(equipmentId, null, teamA, t1);
        s1.setValidFrom(t1); // 前段不闭口：与后段压在一起
        historyRepository.save(s1);
        AssignmentHistory s2 = legacyRow(equipmentId, teamA, teamB, t2);
        s2.setValidFrom(t2);
        historyRepository.save(s2);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> assignmentService.verifyChain(equipmentId));
        assertTrue(ex.getMessage().contains("开口"), ex.getMessage());
    }

    @Test
    void verifyChain_detectsClosedTail() {
        LocalDateTime t1 = LocalDateTime.of(2026, 1, 1, 8, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 1, 3, 8, 0);
        AssignmentHistory s1 = legacyRow(equipmentId, null, teamA, t1);
        s1.setValidFrom(t1);
        s1.setValidTo(t2); // 末段闭口：当前归属落不到任何一段上
        historyRepository.save(s1);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> assignmentService.verifyChain(equipmentId));
        assertTrue(ex.getMessage().contains("末段"), ex.getMessage());
    }

    // ---------- 3. 并发：两人同时改同一件器材，只能落一段 ----------

    @Test
    void concurrentAdjust_exactlyOneSegmentLands() throws InterruptedException {
        assignmentService.bind(bindDto(equipmentId, teamA));

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
                    assignmentService.adjust(adjustDto(equipmentId, teamB));
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
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));

        assertEquals(1, success.get(), "同时改同一件器材，只能落一段");
        assertEquals(1, rejected.get());
        List<AssignmentHistory> segs = chain(equipmentId);
        assertEquals(2, segs.size(), "初始段 + 唯一落下的调整段");
        assertEquals(segs.get(0).getValidTo(), segs.get(1).getValidFrom(),
                "两段不许压在一起，也不许留空档");
        assertNull(segs.get(1).getValidTo());
        assertDoesNotThrow(() -> assignmentService.verifyChain(equipmentId));
    }

    // ---------- 4. 老流水一次性推区间 ----------

    @Test
    void migrate_buildsChainedIntervals_fromLegacyRows() {
        LocalDateTime t0 = LocalDateTime.of(2026, 1, 1, 8, 0);
        LocalDateTime t1 = LocalDateTime.of(2026, 1, 3, 8, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 1, 6, 8, 0);
        LocalDateTime t3 = LocalDateTime.of(2026, 1, 9, 8, 0);
        assignmentRow(equipmentId, teamC, t0);
        legacyRow(equipmentId, null, teamA, t1);
        legacyRow(equipmentId, teamA, teamB, t2);
        legacyRow(equipmentId, teamB, teamC, t3);

        IntervalMigrationResult result = assignmentService.migrateLegacyIntervals();
        assertEquals(1, result.getLegacyEquipmentCount());
        assertEquals(1, result.getMigratedEquipmentCount());
        assertEquals(3, result.getMigratedSegmentCount());
        assertTrue(result.getProblems().isEmpty());

        List<AssignmentHistory> segs = chain(equipmentId);
        assertEquals(3, segs.size());
        assertEquals(t1, segs.get(0).getValidFrom());
        assertEquals(t2, segs.get(0).getValidTo());
        assertEquals(t2, segs.get(1).getValidFrom());
        assertEquals(t3, segs.get(1).getValidTo());
        assertEquals(t3, segs.get(2).getValidFrom());
        assertNull(segs.get(2).getValidTo());
        assertEquals(teamC, segs.get(2).getNewTeamId(), "末段必须落在当前归属上");
        assertDoesNotThrow(() -> assignmentService.verifyChain(equipmentId));
        assertTrue(assignmentService.findOwnershipMismatches().isEmpty());

        // 幂等：再跑一次不再动老数据
        IntervalMigrationResult again = assignmentService.migrateLegacyIntervals();
        assertEquals(0, again.getMigratedEquipmentCount());

        // 推完区间后，新的调整照常往后接段
        assignmentService.adjust(adjustDto(equipmentId, teamA));
        assertEquals(4, chain(equipmentId).size());
        assertDoesNotThrow(() -> assignmentService.verifyChain(equipmentId));
    }

    @Test
    void migrate_firstRecordIsAdjust_derivesEarliestSegmentFromBindTime() {
        LocalDateTime t0 = LocalDateTime.of(2026, 1, 1, 8, 0);
        LocalDateTime t1 = LocalDateTime.of(2026, 1, 5, 8, 0);
        assignmentRow(equipmentId, teamB, t0);
        // 老流水丢了初始绑定那一条，首条就是调整：前面那段起点取自归属单登记的绑定时刻
        legacyRow(equipmentId, teamA, teamB, t1);

        IntervalMigrationResult result = assignmentService.migrateLegacyIntervals();
        assertEquals(1, result.getMigratedEquipmentCount());
        assertTrue(result.getProblems().isEmpty());

        List<AssignmentHistory> segs = chain(equipmentId);
        assertEquals(2, segs.size());
        assertEquals(t0, segs.get(0).getValidFrom());
        assertEquals(t1, segs.get(0).getValidTo());
        assertEquals(teamA, segs.get(0).getNewTeamId());
        assertEquals("系统迁移", segs.get(0).getOperator());
        assertEquals(t1, segs.get(1).getValidFrom());
        assertNull(segs.get(1).getValidTo());
        assertDoesNotThrow(() -> assignmentService.verifyChain(equipmentId));
    }

    @Test
    void migrate_flagsDuplicateTimestamps_andLeavesRowsUntouched() {
        LocalDateTime t0 = LocalDateTime.of(2026, 1, 1, 8, 0);
        LocalDateTime t1 = LocalDateTime.of(2026, 1, 3, 8, 0);
        assignmentRow(equipmentId, teamB, t0);
        legacyRow(equipmentId, null, teamA, t1);
        legacyRow(equipmentId, teamA, teamB, t1); // 时间戳重复：谁先谁后推不出

        IntervalMigrationResult result = assignmentService.migrateLegacyIntervals();
        assertEquals(0, result.getMigratedEquipmentCount());
        assertEquals(1, result.getProblems().size());
        assertEquals(equipmentId, result.getProblems().get(0).getEquipmentId());
        assertTrue(result.getProblems().get(0).getReason().contains("重复"));

        // 不许按猜的补：老流水原样留在那里
        assertTrue(chain(equipmentId).stream().allMatch(h -> h.getValidFrom() == null));
    }

    @Test
    void migrate_flagsUnderivableEarliestStart() {
        LocalDateTime t1 = LocalDateTime.of(2026, 1, 3, 8, 0);
        LocalDateTime t5 = LocalDateTime.of(2026, 1, 8, 8, 0);

        // 情况一：首条是调整，归属单绑定时刻却晚于首次变更 —— 起点对不上
        assignmentRow(equipmentId, teamB, t5);
        legacyRow(equipmentId, teamA, teamB, t1);

        // 情况二：首条是调整，且连归属单都没有 —— 没有起点来源
        Long orphanEq = newEquipment();
        legacyRow(orphanEq, teamA, teamB, t1);

        IntervalMigrationResult result = assignmentService.migrateLegacyIntervals();
        assertEquals(0, result.getMigratedEquipmentCount());
        assertEquals(2, result.getProblems().size());
        assertTrue(result.getProblems().stream()
                .anyMatch(p -> p.getEquipmentId().equals(equipmentId)
                        && p.getReason().contains("最早一段推不出起点")));
        assertTrue(result.getProblems().stream()
                .anyMatch(p -> p.getEquipmentId().equals(orphanEq)
                        && p.getReason().contains("最早一段推不出起点")));

        // 两件都原样不动
        assertTrue(chain(equipmentId).stream().allMatch(h -> h.getValidFrom() == null));
        assertTrue(chain(orphanEq).stream().allMatch(h -> h.getValidFrom() == null));
    }

    // ---------- 5. 对账：当前归属与流水末段对不上，只挑出来不改账 ----------

    @Test
    void mismatch_listedWithoutTouchingAssignment_andAdjustRefused() {
        assignmentService.bind(bindDto(equipmentId, teamA));
        // 手工把归属单改成二班，流水末段仍是一班 —— 模拟账实不符
        Assignment a = assignmentRepository.findByEquipmentId(equipmentId).orElseThrow();
        a.setTeamId(teamB);
        assignmentRepository.save(a);

        List<OwnershipMismatchVO> mismatches = assignmentService.findOwnershipMismatches();
        assertEquals(1, mismatches.size());
        OwnershipMismatchVO vo = mismatches.get(0);
        assertEquals("TEAM_MISMATCH", vo.getType());
        assertEquals(equipmentId, vo.getEquipmentId());
        assertEquals(teamB, vo.getAssignmentTeamId());
        assertEquals(teamA, vo.getSegmentTeamId());

        // 只挑出来列明，不许直接把归属改过去蒙掉
        assertEquals(teamB, assignmentRepository.findByEquipmentId(equipmentId).orElseThrow().getTeamId());

        // 错账上不许继续往后记段
        BusinessException ex = assertThrows(BusinessException.class,
                () -> assignmentService.adjust(adjustDto(equipmentId, teamC)));
        assertTrue(ex.getMessage().contains("对不上"), ex.getMessage());
        assertEquals(1, chain(equipmentId).size());
    }

    @Test
    void mismatch_flagsAssignmentWithoutAnyInterval() {
        // 只有归属单、流水里没有任何区间（老流水未迁移的极端情形）
        assignmentRow(equipmentId, teamA, LocalDateTime.of(2026, 1, 1, 8, 0));

        List<OwnershipMismatchVO> mismatches = assignmentService.findOwnershipMismatches();
        assertEquals(1, mismatches.size());
        assertEquals("NO_INTERVAL", mismatches.get(0).getType());
        assertEquals(equipmentId, mismatches.get(0).getEquipmentId());
    }

    @Test
    void adjust_rejects_whenLegacyRowsNotMigrated() {
        LocalDateTime t0 = LocalDateTime.of(2026, 1, 1, 8, 0);
        LocalDateTime t1 = LocalDateTime.of(2026, 1, 3, 8, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 1, 5, 8, 0);
        assignmentRow(equipmentId, teamB, t0);
        legacyRow(equipmentId, null, teamA, t1);
        legacyRow(equipmentId, teamA, teamB, t2);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> assignmentService.adjust(adjustDto(equipmentId, teamC)));
        assertTrue(ex.getMessage().contains("迁移"), ex.getMessage());
    }
}
